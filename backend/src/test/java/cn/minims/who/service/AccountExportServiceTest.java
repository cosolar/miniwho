package cn.minims.who.service;

import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeAttachment;
import cn.minims.who.entity.User;
import cn.minims.who.entity.UserIdentity;
import cn.minims.who.util.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导出包的名字与清单：这三处错了，用户拿到的就分别是「解坏自己磁盘的包」「少一半内容」
 * 和「看不出门的空包」，都不会在服务端留下报错。
 */
class AccountExportServiceTest {

    private final Json json = new Json(new ObjectMapper());

    /** 只测纯构造方法，五个 Mapper 与存储层用不上，留空即可。 */
    private final AccountExportService service =
            new AccountExportService(null, null, null, null, null, null, null, json);

    @Test
    void 附件原名里的路径段不会带进包里() {
        assertThat(AccountExportService.safeName("../../../../etc/passwd")).isEqualTo("passwd");
        assertThat(AccountExportService.safeName("C:\\简历\\第一版.pdf")).isEqualTo("第一版.pdf");
        assertThat(AccountExportService.safeName("...")).isEqualTo("未命名");
        assertThat(AccountExportService.safeName("")).isEqualTo("未命名");
    }

    @Test
    void 非法文件名字符换成下划线() {
        assertThat(AccountExportService.safeName("a<b>:c\"d|e?.pdf")).isEqualTo("a_b__c_d_e_.pdf");
        assertThat(AccountExportService.safeName("尾部点空格  ")).isEqualTo("尾部点空格");
    }

    @Test
    void 超长原名截断后仍保住扩展名() {
        String longName = "名".repeat(180) + ".docx";
        String safe = AccountExportService.safeName(longName);

        assertThat(safe).hasSizeLessThanOrEqualTo(100);
        assertThat(safe).endsWith(".docx");
    }

    @Test
    void 账号信息里没有密码哈希() {
        User user = new User();
        user.setId(7L);
        user.setUsername("demo");
        user.setPasswordHash("$2a$10$abcdefghijklmnopabcdefghijklmnopabcdefghijklmnopab");
        user.setEmailVerified(1);
        UserIdentity identity = new UserIdentity();
        identity.setProvider("github");
        identity.setProviderLogin("demo");

        String rendered = service.accountJson(user, List.of(identity)).toString();

        assertThat(rendered).doesNotContain("$2a$").doesNotContain("passwordHash");
        assertThat(rendered).contains("github");
    }

    @Test
    void 简历内容导出成节点而不是转义字符串() {
        Resume resume = new Resume();
        resume.setId(3L);
        resume.setTitle("后端工程师简历");
        resume.setContent("{\"sections\":[{\"type\":\"work\"}]}");
        resume.setModulePrefs("[]");
        resume.setStyle("{\"accent\":\"#0a7d76\"}");
        resume.setAvatarKey("avatars/3/avatar.jpg");

        var node = service.resumeJson(resume);

        assertThat(node.get("content").isObject()).isTrue();
        assertThat(node.get("content").get("sections").isArray()).isTrue();
        assertThat(node.get("style").isObject()).isTrue();
        assertThat(node.get("avatar").get("file").asText()).isEqualTo("files/avatars/3/avatar.jpg");
    }

    @Test
    void 没设头像时不编出文件路径() {
        Resume resume = new Resume();
        resume.setId(4L);
        resume.setTitle("空白简历");

        assertThat(service.resumeJson(resume).get("avatar").get("file").isNull()).isTrue();
    }

    @Test
    void 清单条目与包内文件一一对应() {
        User user = new User();
        user.setUsername("demo");
        user.setNickname("郝简历");
        Resume resume = new Resume();
        resume.setId(5L);
        resume.setTitle("简历");
        ResumeAttachment attachment = new ResumeAttachment();
        attachment.setFileName("作品集.pdf");

        List<AccountExportService.ExportText> texts = List.of(
                new AccountExportService.ExportText("说明.txt", "x"),
                new AccountExportService.ExportText("account.json", "{}"),
                new AccountExportService.ExportText("resumes/5-简历.json", "{}"));
        List<AccountExportService.ExportFile> files = List.of(
                new AccountExportService.ExportFile("attachments/5/abc.pdf", "files/attachments/5/9-作品集.pdf"));

        String manifest = service.manifest(user, List.of(resume), List.of(), List.of(attachment),
                List.of(), texts, files);

        assertThat(manifest).contains("\"exportVersion\" : " + AccountExportService.EXPORT_VERSION);
        assertThat(manifest).contains("resumes/5-简历.json", "files/attachments/5/9-作品集.pdf");
        texts.forEach(text -> assertThat(manifest).contains(text.entryName()));
        assertThat(manifest).contains("account.json");
        assertThat(manifest).doesNotContain("manifest.json");
    }
}
