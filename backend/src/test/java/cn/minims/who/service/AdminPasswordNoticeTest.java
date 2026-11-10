package cn.minims.who.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理员重置密码邮件的几个硬约束：密码固定 8 位、字符集不含易混字符、
 * HTML 与纯文本是同一个密码，收件地址进 HTML 前要转义。
 */
class AdminPasswordNoticeTest {

    @Test
    void generatedPasswordHasEightCharsAndNoAmbiguousOnes() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String password = AdminPasswordNotice.generatePassword();
            assertThat(password).hasSize(AdminPasswordNotice.PASSWORD_LENGTH);
            assertThat(password).matches("[A-Za-z2-9]+");
            assertThat(password).doesNotContain("0", "O", "1", "l", "I");
            seen.add(password);
        }
        assertThat(seen).hasSizeGreaterThan(490);
    }

    @Test
    void mailBodyContainsSamePasswordAndEscapesRecipient() {
        String email = "evil\"><script>@example.com";
        String password = "Ab3dE5gH";
        String html = AdminPasswordNotice.htmlBody(email, password);
        String text = AdminPasswordNotice.textBody(email, password);

        assertThat(html).contains(password).contains("cid:" + MailService.LOGO_CID);
        assertThat(html).contains("&quot;&gt;&lt;script&gt;");
        assertThat(html).doesNotContain("<script>");
        assertThat(text).contains(password).contains(email);
        assertThat(AdminPasswordNotice.subject()).contains("密码已重置");
    }
}
