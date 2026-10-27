package cn.minims.who.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.entity.EmailCode;
import cn.minims.who.mapper.EmailCodeMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * 一次性邮箱验证码：发信前先落库，验证通过立刻删除，明文只在这条链路里短暂存在。
 * <p>限流按「邮箱」和「IP」两个维度：IP 限在拦截器里，这里管同一个邮箱多久能收几封。
 */
@Service
public class EmailCodeService {

    public static final String PURPOSE_REGISTER = "register";

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
        long now = System.currentTimeMillis();
        codeMapper.delete(new LambdaQueryWrapper<EmailCode>().lt(EmailCode::getCreatedAt, now - RETENTION_MS));

        EmailCode latest = latest(email);
        if (latest != null && now - latest.getCreatedAt() < RESEND_INTERVAL_MS) {
            throw BizException.tooMany("验证码刚刚发送过，请一分钟后再试");
        }
        long recent = codeMapper.selectCount(new LambdaQueryWrapper<EmailCode>()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, PURPOSE_REGISTER)
                .ge(EmailCode::getCreatedAt, now - HOURLY_WINDOW_MS));
        if (recent >= MAX_PER_HOUR) {
            throw BizException.tooMany("这个邮箱申请验证码太频繁，请一小时后再试");
        }

        String code = nextCode();
        EmailCode record = new EmailCode();
        record.setEmail(email);
        record.setPurpose(PURPOSE_REGISTER);
        // 验证码只有 6 位，明文或普通哈希都扛不住库泄露后被爆破，按密码的强度存。
        record.setCodeHash(BCrypt.hashpw(code));
        record.setExpiresAt(now + TTL_MS);
        record.setAttempts(0);
        codeMapper.insert(record);

        mailService.send(email, "极简简历注册验证码", body(code));
    }

    /** 校验并消费验证码：成功即删除，失败累计次数，超过上限直接作废。 */
    public void verifyRegisterCode(String email, String code) {
        long now = System.currentTimeMillis();
        EmailCode latest = latest(email);
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
    private EmailCode latest(String email) {
        return codeMapper.selectOne(new LambdaQueryWrapper<EmailCode>()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, PURPOSE_REGISTER)
                .orderByDesc(EmailCode::getId)
                .last("limit 1"));
    }

    private static String nextCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String body(String code) {
        return """
                你正在注册极简简历。

                验证码：%s

                验证码 10 分钟内有效，填写后本邮件即失效。
                如果这不是你本人的操作，请忽略本邮件。
                """.formatted(code);
    }
}
