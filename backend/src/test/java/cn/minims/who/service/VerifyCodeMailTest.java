package cn.minims.who.service;

import cn.minims.who.mapper.SiteSettingMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证码邮件的四处对应关系：HTML 里引用的 cid 必须就是内联附件的 id、纯文本备选与 HTML 是同一个码、
 * 收件地址进 HTML 前得转义、注册与找回密码两套文案不能串台。
 * 任何一处错位，收件人看到的就是破图、两个不一样的验证码、一封信里的标签被地址带歪，
 * 或者本该改密码的人以为自己在注册。
 */
class VerifyCodeMailTest {

    private final Map<String, String> rows = new HashMap<>();

    private final SiteSettingMapper mapper = new SiteSettingMapper() {
        @Override
        public String selectValue(String key) {
            return rows.get(key);
        }

        @Override
        public int updateValue(String key, String value, long now) {
            if (!rows.containsKey(key)) {
                return 0;
            }
            rows.put(key, value);
            return 1;
        }

        @Override
        public int insertValue(String key, String value, long now) {
            rows.put(key, value);
            return 1;
        }
    };

    private final SettingsService settings = new SettingsService(mapper);
    private final MailService mail = new MailService(settings);

    @Test
    void 邮件带纯文本与HTML两份正文并把logo内联进来() throws Exception {
        settings.set(SettingsService.MAIL_FROM, "no-reply@example.com");
        String html = EmailCodeService.htmlBody("reader@example.com", "980135", EmailCodeService.PURPOSE_REGISTER);
        String text = EmailCodeService.textBody("reader@example.com", "980135", EmailCodeService.PURPOSE_REGISTER);

        MimeMessage message = mail.compose(new JavaMailSenderImpl(), "reader@example.com",
                "【极简简历】注册验证码", text, html);
        // 不发信的话 saveChanges 由 JavaMailSender 在 send 里做；这里补上，否则各 part 的头还没落定。
        message.saveChanges();
        List<Part> parts = flatten(message);

        assertThat(contentTypesOf(parts)).anyMatch(type -> type.startsWith("text/plain"));
        assertThat(contentTypesOf(parts)).anyMatch(type -> type.startsWith("text/html"));
        assertThat(html).contains("cid:" + MailService.LOGO_CID);

        Part logo = parts.stream()
                .filter(part -> contentTypeOf(part).startsWith("image/"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("邮件里没有内联图片"));
        assertThat(((MimeBodyPart) logo).getContentID()).isEqualTo("<" + MailService.LOGO_CID + ">");
        try (InputStream input = ((MimeBodyPart) logo).getInputStream()) {
            assertThat(input.readAllBytes()).isNotEmpty();
        }
        assertThat(text).contains("980135");
    }

    @Test
    void 有效期跟着TTL走且收件地址不会带歪HTML() {
        String html = EmailCodeService.htmlBody("evil\"><script>@example.com", "424242",
                EmailCodeService.PURPOSE_REGISTER);

        assertThat(html).contains("424242").contains("10 分钟").contains("&quot;&gt;&lt;script&gt;");
        assertThat(html).doesNotContain("<script>");
    }

    /** 找回密码的码能直接改密码，所以信里必须写得明明白白；写成注册文案就是给「以为在注册其实被改了密码」开门。 */
    @Test
    void 两种用途各自用自己的文案() {
        String register = EmailCodeService.htmlBody("me@example.com", "111111", EmailCodeService.PURPOSE_REGISTER);
        String reset = EmailCodeService.htmlBody("me@example.com", "222222", EmailCodeService.PURPOSE_RESET);
        String resetText = EmailCodeService.textBody("me@example.com", "222222", EmailCodeService.PURPOSE_RESET);

        assertThat(register).contains("注册「极简简历」账号").doesNotContain("重置");
        assertThat(reset).contains("重置「极简简历」的登录密码").doesNotContain("账号不会被创建");
        // HTML 与纯文本是同一个码渲染出来的两份正文，对不上就等于发出去一封收件人猜不透的信。
        assertThat(reset).contains("222222");
        assertThat(resetText).contains("重置").contains("222222");
    }

    /** 收件地址最后才进模板：早一步替换的话，本地部分里的 __CODE__ 会被当成占位符换成真验证码。 */
    @Test
    void 收件地址里的占位符原样保留且不会被顶包() {
        String html = EmailCodeService.htmlBody("__CODE__@example.com", "424242", EmailCodeService.PURPOSE_REGISTER);

        assertThat(html).contains("__CODE__@example.com");
        // 真验证码只该出现在码框里一次，出现两次说明地址文本把它替换进去了。
        assertThat(html.split("424242", -1)).hasSize(2);
    }

    private static String contentTypeOf(Part part) {
        try {
            return part.getContentType();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> contentTypesOf(List<Part> parts) {
        return parts.stream().map(VerifyCodeMailTest::contentTypeOf).toList();
    }

    private static List<Part> flatten(Part part) throws MessagingException, IOException {
        List<Part> parts = new ArrayList<>();
        collect(part, parts);
        return parts;
    }

    private static void collect(Part part, List<Part> sink) throws MessagingException, IOException {
        sink.add(part);
        if (part.getContent() instanceof MimeMultipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                collect(multipart.getBodyPart(index), sink);
            }
        }
    }
}
