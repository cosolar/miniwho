package cn.minims.who.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.entity.EmailCode;
import cn.minims.who.mapper.EmailCodeMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;

/**
 * 一次性邮箱验证码：发信前先落库，验证通过立刻删除，明文只在这条链路里短暂存在。
 * <p>限流按「邮箱」和「IP」两个维度：IP 限在拦截器里，这里管同一个邮箱多久能收几封。
 * <p>注册、找回密码、注销与换绑邮箱共用同一套存储与限流，靠 purpose 隔离：各条链路的验证码互不通用，
 * 否则拿到一个注册码就能顺手改密码或把账号邮箱换成自己的。
 */
@Service
public class EmailCodeService {

    public static final String PURPOSE_REGISTER = "register";
    public static final String PURPOSE_RESET = "reset";
    public static final String PURPOSE_DELETION = "deletion";
    public static final String PURPOSE_CHANGE_EMAIL = "change_email";

    /** 一种用途一套文案：收件人得能从邮件本身看出这码是拿来干什么的，才谈得上判断自己有没有被骗。 */
    private record Copy(String subject, String heading, String lead, String closing, String preheader) {
    }

    /** 主题里带上站点名：收件箱列表只显示发件人显示名时，这一串方括号是唯一能认出品牌的位。 */
    private static final Map<String, Copy> COPIES = Map.of(
            PURPOSE_REGISTER, new Copy("【极简简历】注册验证码", "注册验证码",
                    "你正在使用 __EMAIL__ 注册「极简简历」账号。把下面这 6 位数字填回注册页面，即可完成注册。",
                    "如果这不是你本人的操作，请忽略本邮件，账号不会被创建。",
                    "验证码 __MINUTES__ 分钟内有效，且只用于本次注册。"),
            PURPOSE_RESET, new Copy("【极简简历】找回密码验证码", "重置登录密码",
                    "你正在为 __EMAIL__ 重置「极简简历」的登录密码。把下面这 6 位数字填回页面，即可设置新密码。",
                    "如果这不是你本人的操作，请忽略本邮件，你的密码不会有任何变化。",
                    "验证码 __MINUTES__ 分钟内有效，且只用于本次密码重置。"),
            // 注销是这份数据唯一不可逆的入口，邮件文案要说清「码只用来确认身份，删不删还有宽限期」，
            // 否则拿到别人邮箱的一枚码就能替人做决定，收件人也没从邮件里看出后果。
            PURPOSE_DELETION, new Copy("【极简简历】注销验证码", "注销账号验证",
                    "你正在为 __EMAIL__ 申请注销「极简简历」账号。把下面这 6 位数字填回页面，即可提交注销申请。",
                    "如果这不是你本人的操作，请忽略本邮件，账号不会被注销。",
                    "验证码 __MINUTES__ 分钟内有效，且只用于本次注销申请；提交后不会立刻删除，宽限期内登录即可取消。"),
            // 换绑邮箱同样是「证明新地址归你」：别人拿到旧账号的登录态还不够，得能收到新邮箱的信才算数。
            PURPOSE_CHANGE_EMAIL, new Copy("【极简简历】换绑邮箱验证码", "更换邮箱验证",
                    "你正在把「极简简历」账号的邮箱更换为 __EMAIL__。把下面这 6 位数字填回页面，即可完成换绑。",
                    "如果这不是你本人的操作，请忽略本邮件，账号邮箱保持不变。",
                    "验证码 __MINUTES__ 分钟内有效，且只用于本次更换邮箱。"));

    /** 十分钟：短到脚本刷不动，长到用户从垃圾箱里翻出来还能用。 */
    private static final long TTL_MS = 10 * 60_000L;
    private static final long RESEND_INTERVAL_MS = 60_000L;
    private static final long HOURLY_WINDOW_MS = 60 * 60_000L;
    private static final int MAX_PER_HOUR = 5;
    private static final int MAX_ATTEMPTS = 5;
    /** 过期很久的验证码没有价值了，发信时顺手清掉，不让表只增不删。 */
    private static final long RETENTION_MS = 24 * 60 * 60_000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailCodeMapper codeMapper;
    private final MailService mailService;

    public EmailCodeService(EmailCodeMapper codeMapper, MailService mailService) {
        this.codeMapper = codeMapper;
        this.mailService = mailService;
    }

    /** 生成并发送注册验证码；同一邮箱一小时内最多 {@value #MAX_PER_HOUR} 封。 */
    public void issueRegisterCode(String email) {
        issue(email, PURPOSE_REGISTER);
    }

    public void issueResetCode(String email) {
        issue(email, PURPOSE_RESET);
    }

    public void issueDeletionCode(String email) {
        issue(email, PURPOSE_DELETION);
    }

    public void issueChangeEmailCode(String email) {
        issue(email, PURPOSE_CHANGE_EMAIL);
    }

    /**
     * 校验并消费注册验证码：成功即删除，失败累计次数，超过上限直接作废。
     * <p>调用方必须在没有事务的环境下调它。这不是风格问题：错一次码抛的是 RuntimeException，
     * 跟着调用方事务一起回滚的话 attempts 永远停在 0，五次上限形同虚设；而用
     * {@code NOT_SUPPORTED} 挂起调用方事务在 SQLite 上又会撞上 SQLITE_BUSY_SNAPSHOT
     * （外层先读后写，内层这条写已经提交，外层写就被顶死）。所以由调用方把事务去掉。
     */
    public void verifyRegisterCode(String email, String code) {
        verify(email, code, PURPOSE_REGISTER);
    }

    public void verifyResetCode(String email, String code) {
        verify(email, code, PURPOSE_RESET);
    }

    /**
     * 校验并消费注销验证码。
     * <p>没有密码的第三方账号只有这一条自助注销的路，所以它和其他两条一样必须在无事务环境下调用。
     */
    public void verifyDeletionCode(String email, String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("请输入邮箱验证码");
        }
        verify(email, code, PURPOSE_DELETION);
    }

    /**
     * 校验并消费换绑邮箱验证码：码是发给「新」地址的，验过才算证明新地址归本人。
     * <p>和注册 / 找回密码一样必须在无事务环境下调用，attempts 记数不能随外层事务回滚。
     */
    public void verifyChangeEmailCode(String email, String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("请输入新邮箱的验证码");
        }
        verify(email, code, PURPOSE_CHANGE_EMAIL);
    }

    private void issue(String email, String purpose) {
        Copy copy = COPIES.get(purpose);
        long now = System.currentTimeMillis();
        codeMapper.delete(new LambdaQueryWrapper<EmailCode>().lt(EmailCode::getCreatedAt, now - RETENTION_MS));

        EmailCode latest = latest(email, purpose);
        if (latest != null && now - latest.getCreatedAt() < RESEND_INTERVAL_MS) {
            throw BizException.tooMany("验证码刚刚发送过，请一分钟后再试");
        }
        long recent = codeMapper.selectCount(new LambdaQueryWrapper<EmailCode>()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, purpose)
                .ge(EmailCode::getCreatedAt, now - HOURLY_WINDOW_MS));
        if (recent >= MAX_PER_HOUR) {
            throw BizException.tooMany("这个邮箱申请验证码太频繁，请一小时后再试");
        }

        String code = nextCode();
        EmailCode record = new EmailCode();
        record.setEmail(email);
        record.setPurpose(purpose);
        // 验证码只有 6 位，明文或普通哈希都扛不住库泄露后被爆破，按密码的强度存。
        record.setCodeHash(BCrypt.hashpw(code));
        record.setExpiresAt(now + TTL_MS);
        record.setAttempts(0);
        codeMapper.insert(record);

        mailService.send(email, copy.subject(), textBody(email, code, purpose), htmlBody(email, code, purpose));
    }

    private void verify(String email, String code, String purpose) {
        long now = System.currentTimeMillis();
        EmailCode latest = latest(email, purpose);
        if (latest == null) {
            throw BizException.badRequest("请先获取邮箱验证码");
        }
        if (latest.getExpiresAt() <= now) {
            codeMapper.deleteById(latest.getId());
            throw BizException.badRequest("验证码已过期，请重新获取");
        }
        if (latest.getAttempts() >= MAX_ATTEMPTS) {
            codeMapper.deleteById(latest.getId());
            throw BizException.badRequest("验证码错误次数过多，请重新获取");
        }
        if (!BCrypt.checkpw(code == null ? "" : code.trim(), latest.getCodeHash())) {
            EmailCode patch = new EmailCode();
            patch.setId(latest.getId());
            patch.setAttempts(latest.getAttempts() + 1);
            codeMapper.updateById(patch);
            throw BizException.badRequest("验证码不正确");
        }
        codeMapper.deleteById(latest.getId());
    }

    /** 只取未被删除的最新一条：过期判断交给调用方，好把「已过期」和「没申请过」区分开。 */
    private EmailCode latest(String email, String purpose) {
        return codeMapper.selectOne(new LambdaQueryWrapper<EmailCode>()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, purpose)
                .orderByDesc(EmailCode::getId)
                .last("limit 1"));
    }

    private static String nextCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** 收件地址来自用户输入，虽然过了一遍 @Email，进 HTML 前还是把尖括号与 & 转义掉。 */
    static String htmlBody(String email, String code, String purpose) {
        return render(HTML_TEMPLATE, copy(purpose), styledEmail(escapeHtml(email)), code);
    }

    static String textBody(String email, String code, String purpose) {
        return render(TEXT_TEMPLATE, copy(purpose), email, code);
    }

    private static Copy copy(String purpose) {
        Copy copy = COPIES.get(purpose);
        if (copy == null) {
            throw new IllegalArgumentException("未知的验证码用途: " + purpose);
        }
        return copy;
    }

    /**
     * 有效期只有一个来源：TTL 常量；文案只有一个来源：{@link #COPIES}。
     * <p>替换顺序是刻意的：收件地址最后进。反过来做，一个本地部分里带 {@code __CODE__} 的
     * 合法地址就能把真实验证码顶进正文。
     */
    private static String render(String template, Copy copy, String emailValue, String code) {
        return template.replace("__HEADING__", copy.heading())
                .replace("__LEAD__", copy.lead())
                .replace("__CLOSING__", copy.closing())
                .replace("__PREHEADER__", copy.preheader())
                .replace("__CODE__", code)
                .replace("__MINUTES__", String.valueOf(TTL_MS / 60_000L))
                .replace("__LOGO__", MailService.LOGO_CID)
                .replace("__EMAIL__", emailValue);
    }

    private static String styledEmail(String escapedEmail) {
        return "<span style=\"color:#1f2a26;font-weight:600;\">" + escapedEmail + "</span>";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * 邮件正文按各家客户端的最保守写法来：只用 table 布局 + 内联样式，不用 class、外链字体与背景图，
     * 关键底色同时给一份 bgcolor 属性兜底 Outlook 的 Word 渲染引擎。
     */
    private static final String HTML_TEMPLATE = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>__HEADING__</title>
            </head>
            <body style="margin:0;padding:0;background-color:#f2f5f4;-webkit-text-size-adjust:100%;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                __PREHEADER__
              </div>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f2f5f4;">
                <tr>
                  <td align="center" style="padding:32px 16px;">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;background-color:#ffffff;border:1px solid #e4eae8;border-radius:14px;">
                      <tr>
                        <td style="padding:0;">
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                            <tr>
                              <td height="4" bgcolor="#0a7d76" style="height:4px;line-height:4px;font-size:4px;">&nbsp;</td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td align="left" style="padding:30px 40px 6px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;">
                          <div style="font-size:12px;letter-spacing:2px;color:#8b9a95;">MINIWHO · 账号验证</div>
                          <div style="padding-top:12px;font-size:20px;font-weight:600;color:#1f2a26;">__HEADING__</div>
                          <div style="padding-top:16px;font-size:14px;line-height:1.8;color:#5b6b66;">
                            __LEAD__
                          </div>
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:26px 0;">
                            <tr>
                              <td align="center" bgcolor="#f2f8f7" style="background-color:#f2f8f7;border:1px dashed #a9cdc8;border-radius:10px;padding:26px 8px;">
                                <span style="font-family:Consolas,'Courier New',Menlo,monospace;font-size:32px;font-weight:700;letter-spacing:10px;color:#0a7d76;">__CODE__</span>
                              </td>
                            </tr>
                          </table>
                          <div style="font-size:13px;line-height:2;color:#7c8b86;">
                            验证码 <span style="color:#0a7d76;font-weight:600;">__MINUTES__ 分钟</span>内有效，填写成功后立即失效。<br>
                            极简简历不会通过电话、聊天软件或邮件向你索要验证码。<br>
                            __CLOSING__
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding:26px 40px 32px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;">
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                            <tr>
                              <td align="center" style="border-top:1px solid #eef2f1;padding-top:24px;">
                                <img src="cid:__LOGO__" width="150" height="45" alt="极简简历 who.minims.cn" style="display:block;border:0;outline:none;text-decoration:none;">
                                <div style="padding-top:14px;font-size:12px;line-height:1.9;color:#a2b0ab;">
                                  开源自托管的简历工具，数据留在你自己手里<br>
                                  本邮件由系统自动发出，请勿直接回复。
                                </div>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

    private static final String TEXT_TEMPLATE = """
            __LEAD__

            验证码：__CODE__

            验证码 __MINUTES__ 分钟内有效，填写成功后立即失效。
            极简简历不会通过电话、聊天软件或邮件向你索要验证码。
            __CLOSING__

            —— 极简简历 who.minims.cn（本邮件由系统自动发出，请勿直接回复）
            """;
}
