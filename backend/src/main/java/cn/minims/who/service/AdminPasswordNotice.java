package cn.minims.who.service;

import java.security.SecureRandom;

/**
 * 管理员重置密码后的通知邮件。
 * <p>密码由服务端生成、只出现在这封邮件里，接口不回传明文；因此模板与随机源放在一处，
 * 测试可以核对长度、字符集和 HTML 转义。
 */
public final class AdminPasswordNotice {

    public static final int PASSWORD_LENGTH = 8;

    /** 去掉 0/O、1/l/I 这类容易看错的字符，管理员不需要再替用户辨认。 */
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AdminPasswordNotice() {
    }

    public static String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }

    public static String subject() {
        return "【极简简历】登录密码已重置";
    }

    public static String textBody(String email, String password) {
        return """
                管理员已为 %s 对应的「极简简历」账号重置登录密码。

                新密码：%s

                请使用新密码登录，并在登录后尽快到个人中心修改密码。
                如果这不是你本人申请的操作，请立即联系站点管理员。

                —— 极简简历 who.minims.cn（本邮件由系统自动发出，请勿直接回复）
                """.formatted(email, password);
    }

    public static String htmlBody(String email, String password) {
        String lead = "管理员已为 " + styledEmail(escapeHtml(email))
                + " 对应的「极简简历」账号重置登录密码。请使用下面的新密码登录。";
        return HTML_TEMPLATE
                .replace("__LEAD__", lead)
                .replace("__PASSWORD__", escapeHtml(password))
                .replace("__LOGO__", MailService.LOGO_CID);
    }

    private static String styledEmail(String escapedEmail) {
        return "<span style=\"color:#1f2a26;font-weight:600;\">" + escapedEmail + "</span>";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String HTML_TEMPLATE = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>登录密码已重置</title>
            </head>
            <body style="margin:0;padding:0;background-color:#f2f5f4;-webkit-text-size-adjust:100%;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                管理员已重置你的「极简简历」登录密码，请查收新密码。
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
                          <div style="font-size:12px;letter-spacing:2px;color:#8b9a95;">MINIWHO · 密码重置</div>
                          <div style="padding-top:12px;font-size:20px;font-weight:600;color:#1f2a26;">登录密码已重置</div>
                          <div style="padding-top:16px;font-size:14px;line-height:1.8;color:#5b6b66;">
                            __LEAD__
                          </div>
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:26px 0;">
                            <tr>
                              <td align="center" bgcolor="#f2f8f7" style="background-color:#f2f8f7;border:1px dashed #a9cdc8;border-radius:10px;padding:26px 8px;">
                                <span style="font-family:Consolas,'Courier New',Menlo,monospace;font-size:30px;font-weight:700;letter-spacing:6px;color:#0a7d76;">__PASSWORD__</span>
                              </td>
                            </tr>
                          </table>
                          <div style="font-size:13px;line-height:2;color:#7c8b86;">
                            请使用新密码登录，并在登录后尽快到个人中心修改密码。<br>
                            如果这不是你本人申请的操作，请立即联系站点管理员。<br>
                            极简简历不会通过电话、聊天软件或邮件向你索要密码。
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
}
