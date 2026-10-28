package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeAttachment;
import cn.minims.who.entity.Template;
import cn.minims.who.entity.User;
import cn.minims.who.entity.UserIdentity;
import cn.minims.who.mapper.ResumeAttachmentMapper;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserIdentityMapper;
import cn.minims.who.mapper.UserMapper;
import cn.minims.who.util.Json;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 账号全量数据导出：把「属于这个账号的东西」一次打包成一个 zip。
 *
 * <p>包里的东西按可人读的方式分层（{@code 说明.txt} / {@code manifest.json} /
 * {@code account.json} / {@code resumes/} / {@code templates/} / {@code files/}），
 * 目的是让用户能自己核对完整性，而不是只能信任一个黑盒归档。
 */
@Component
public class AccountExportService {

    /** 导出包格式版本：以后调整目录结构或字段时，用户可以靠这个字段判断手上那份是哪一版。 */
    public static final int EXPORT_VERSION = 1;

    private static final int NAME_LIMIT = 100;
    private static final String GUIDE_FILE = "说明.txt";
    private static final String GUIDE_TEXT = """
            极简简历 MiniWho · 账号数据导出

            account.json   账号本身的信息（不含密码），含账号头像的存放位置
            resumes/       每一份简历的完整内容、版式设置与头像信息
            templates/     你自己导入或保存的模板包（官方模板不需要备份，站内随时可取）
            files/         原文件：附件、简历照片、公司/院校 logo，以及账号头像
            manifest.json  本次导出的清单，可用来核对包内文件是否齐全

            JSON 都能用文本编辑器直接打开；图片与附件保留原格式，可自行留档或迁移。
            这份包只用于备份与迁移，站内暂不支持把它导回来。
            """;

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeAttachmentMapper attachmentMapper;
    private final TemplateMapper templateMapper;
    private final UserIdentityMapper identityMapper;
    private final AttachmentStore store;
    private final TemplateService templateService;
    private final Json json;

    public AccountExportService(UserMapper userMapper, ResumeMapper resumeMapper,
                                ResumeAttachmentMapper attachmentMapper, TemplateMapper templateMapper,
                                UserIdentityMapper identityMapper, AttachmentStore store,
                                TemplateService templateService, Json json) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.attachmentMapper = attachmentMapper;
        this.templateMapper = templateMapper;
        this.identityMapper = identityMapper;
        this.store = store;
        this.templateService = templateService;
        this.json = json;
    }

    /** 包内的一条文本条目，内容在规划阶段就已算好。 */
    public record ExportText(String entryName, String content) {
    }

    /** 包内的一条二进制条目：只记存储 key 与目标路径，字节留到真正写包时才取。 */
    public record ExportFile(String key, String entryName) {
    }

    /**
     * 导出计划：文件名 + 文本条目 + 待打包的文件。
     *
     * <p>之所以分成「计划」和「写包」两步，是因为写包发生在响应流已经开始之后，
     * 那时再抛异常只能给用户一个截断的 zip；所有查库与鉴权都必须在计划阶段完成。
     */
    public record ExportPlan(String fileName, List<ExportText> texts, List<ExportFile> files) {
    }

    public ExportPlan plan(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByAsc(Resume::getId));
        List<ResumeAttachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<ResumeAttachment>()
                .eq(ResumeAttachment::getUserId, userId)
                .orderByAsc(ResumeAttachment::getId));
        List<Template> templates = templateMapper.selectList(new LambdaQueryWrapper<Template>()
                .eq(Template::getOwnerUserId, userId)
                .eq(Template::getSource, "imported")
                .orderByAsc(Template::getCode));
        List<UserIdentity> identities = identityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getUserId, userId)
                .orderByAsc(UserIdentity::getId));

        List<ExportText> texts = new ArrayList<>();
        List<ExportFile> files = new ArrayList<>();

        texts.add(new ExportText(GUIDE_FILE, GUIDE_TEXT));
        for (Resume resume : resumes) {
            texts.add(new ExportText("resumes/" + resumeEntryName(resume), resumeJson(resume).toString()));
        }
        for (Template template : templates) {
            TemplateService.TemplateExport export =
                    templateService.download(template.getCode(), () -> userId, false);
            texts.add(new ExportText("templates/" + safeName(export.fileName()), export.content()));
        }
        texts.add(new ExportText("account.json", accountJson(user, identities).toString()));

        for (ResumeAttachment attachment : attachments) {
            files.add(new ExportFile(attachment.getStorageKey(), "files/attachments/"
                    + attachment.getResumeId() + "/" + attachment.getId() + "-" + safeName(attachment.getFileName())));
        }
        for (Resume resume : resumes) {
            if (resume.getAvatarKey() != null && !resume.getAvatarKey().isBlank()) {
                files.add(new ExportFile(resume.getAvatarKey(), "files/" + resume.getAvatarKey()));
            }
            for (String key : store.listKeys("media/" + resume.getId())) {
                files.add(new ExportFile(key, "files/" + key));
            }
        }
        // 账号头像不在任何一份简历目录下，单独收一条。
        if (user.getAvatarKey() != null && !user.getAvatarKey().isBlank()) {
            files.add(new ExportFile(user.getAvatarKey(), "files/" + user.getAvatarKey()));
        }

        // 清单排最后生成：它是包内其余条目的索引，早一步就少算几条，用户核对时会以为包坏了。
        texts.add(new ExportText("manifest.json",
                manifest(user, resumes, templates, attachments, identities, texts, files)));

        return new ExportPlan(archiveName(user), texts, files);
    }

    public void write(ExportPlan plan, OutputStream output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (ExportText text : plan.texts()) {
                zip.putNextEntry(new ZipEntry(text.entryName()));
                zip.write(text.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (ExportFile file : plan.files()) {
                Path path = store.resolve(file.key());
                if (!Files.isReadable(path)) {
                    // 库里还挂着行、盘上文件已经没了：这是别处清理失败的存量问题，
                    // 不该让它把整次导出顶失败，少一个文件在 manifest 里也对不上。
                    continue;
                }
                zip.putNextEntry(new ZipEntry(file.entryName()));
                try (InputStream input = Files.newInputStream(path)) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private String archiveName(User user) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now());
        return "miniwho-" + safeName(user.getUsername()) + "-" + stamp + ".zip";
    }

    ObjectNode resumeJson(Resume resume) {
        ObjectNode node = json.object();
        node.put("id", resume.getId());
        node.put("title", resume.getTitle());
        node.put("language", resume.getLanguage());
        node.put("templateCode", resume.getTemplateCode());
        node.put("source", resume.getSource());
        node.put("lifecycleStatus", resume.getLifecycleStatus());
        node.put("completeness", resume.getCompleteness());
        node.put("score", resume.getScore());
        node.put("contentVersion", resume.getContentVersion());
        node.put("createdAt", resume.getCreatedAt());
        node.put("updatedAt", resume.getUpdatedAt());
        ObjectNode avatar = node.putObject("avatar");
        avatar.put("key", resume.getAvatarKey());
        avatar.put("shape", resume.getAvatarShape());
        avatar.put("file", resume.getAvatarKey() == null || resume.getAvatarKey().isBlank()
                ? null : "files/" + resume.getAvatarKey());
        node.set("content", json.readOrDefault(resume.getContent(), "{}"));
        node.set("modulePrefs", json.readOrDefault(resume.getModulePrefs(), "[]"));
        node.set("style", json.readOrDefault(resume.getStyle(), "{}"));
        return node;
    }

    ObjectNode accountJson(User user, List<UserIdentity> identities) {
        ObjectNode node = json.object();
        node.put("id", user.getId());
        node.put("username", user.getUsername());
        node.put("nickname", user.getNickname());
        node.put("email", user.getEmail());
        node.put("emailVerified", Integer.valueOf(1).equals(user.getEmailVerified()));
        node.put("hasPassword", Integer.valueOf(1).equals(user.getHasPassword()));
        node.put("role", user.getRole());
        node.put("status", user.getStatus());
        node.put("createdAt", user.getCreatedAt());
        node.put("updatedAt", user.getUpdatedAt());
        ObjectNode avatar = node.putObject("avatar");
        avatar.put("key", user.getAvatarKey());
        avatar.put("file", user.getAvatarKey() == null || user.getAvatarKey().isBlank()
                ? null : "files/" + user.getAvatarKey());
        ArrayNode rows = node.putArray("identities");
        for (UserIdentity identity : identities) {
            ObjectNode row = rows.addObject();
            row.put("provider", identity.getProvider());
            row.put("providerUserId", identity.getProviderUserId());
            row.put("providerLogin", identity.getProviderLogin());
            row.put("email", identity.getEmail());
            row.put("createdAt", identity.getCreatedAt());
        }
        // 密码哈希不进导出包：这份 zip 是给用户自己留档的，可能被转发出去。
        return node;
    }

    String manifest(User user, List<Resume> resumes, List<Template> templates,
                            List<ResumeAttachment> attachments, List<UserIdentity> identities,
                            List<ExportText> texts, List<ExportFile> files) {
        ObjectNode node = json.object();
        node.put("app", "极简简历 MiniWho");
        node.put("exportVersion", EXPORT_VERSION);
        node.put("exportedAt", System.currentTimeMillis());
        node.put("exportedAtText", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
        ObjectNode account = node.putObject("account");
        account.put("username", user.getUsername());
        account.put("nickname", user.getNickname());
        ArrayNode counts = node.putArray("counts");
        counts.addObject().put("item", "简历").put("count", resumes.size());
        counts.addObject().put("item", "自有模板包").put("count", templates.size());
        counts.addObject().put("item", "附件").put("count", attachments.size());
        counts.addObject().put("item", "登录渠道").put("count", identities.size());
        ArrayNode entries = node.putArray("entries");
        texts.forEach(text -> entries.add(text.entryName()));
        files.forEach(file -> entries.add(file.entryName()));
        return json.writePretty(node);
    }

    private String resumeEntryName(Resume resume) {
        String title = safeName(resume.getTitle());
        return resume.getId() + (title.isBlank() ? "" : "-" + title) + ".json";
    }

    /**
     * 把用户可控的名字压成一个能安全放进 zip 的路径段。
     *
     * <p>这里防的是「解包的人被自己备份的文件伤到」：附件原名是上传时用户随手写的，
     * 留着 {@code ../} 或反斜杠就会让解压工具把文件写到目标目录之外去。
     */
    static String safeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "未命名";
        }
        String slashed = raw.replace('\\', '/');
        String base = slashed.substring(slashed.lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                continue;
            }
            if (c == '/' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                c = '_';
            }
            name.append(c);
        }
        String cleaned = name.toString().trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isBlank()) {
            return "未命名";
        }
        if (cleaned.length() <= NAME_LIMIT) {
            return cleaned;
        }
        int dot = cleaned.lastIndexOf('.');
        // 截断要保住扩展名，否则备份回来的 pdf/docx 在解压目录里认不出来。
        if (dot > 0 && dot < cleaned.length() - 1 && cleaned.length() - dot <= 12) {
            String ext = cleaned.substring(dot);
            return cleaned.substring(0, NAME_LIMIT - ext.length()) + ext;
        }
        return cleaned.substring(0, NAME_LIMIT);
    }
}
