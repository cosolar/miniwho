package cn.minims.who.config;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.dto.ResumeDtos.CreateResumeRequest;
import cn.minims.who.entity.Feedback;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.Template;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.FeedbackMapper;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserMapper;
import cn.minims.who.service.ResumeService;
import cn.minims.who.template.BuiltinTemplatePackages;
import cn.minims.who.template.TemplateCssSanitizer;
import cn.minims.who.template.TemplatePackage;
import cn.minims.who.template.TemplatePackageCodec;
import cn.minims.who.template.TemplatePackageException;
import cn.minims.who.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(20)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** 社区模板转正前的作者命名空间：这批包曾以 cosolar--<code> 的导入编码存在，升级后要迁到官方裸编码。 */
    private static final String LEGACY_AUTHOR_NAMESPACE = "cosolar";

    private final TemplateMapper templateMapper;
    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final FeedbackMapper feedbackMapper;
    private final ResumeService resumeService;
    private final BuiltinTemplatePackages builtinTemplatePackages;
    private final TemplatePackageCodec codec;
    private final Json json;

    @Value("${app.seed-demo:true}")
    private boolean seedDemo;

    @Value("${app.demo-password:demo123}")
    private String demoPassword;

    /** 无默认值：首次创建 admin 时必须由 MINIWHO_ADMIN_PASSWORD 显式提供，缺失则拒绝启动。 */
    @Value("${app.admin-password:}")
    private String adminPassword;

    public DataInitializer(TemplateMapper templateMapper, UserMapper userMapper, ResumeMapper resumeMapper,
                           FeedbackMapper feedbackMapper, ResumeService resumeService,
                           BuiltinTemplatePackages builtinTemplatePackages, TemplatePackageCodec codec, Json json) {
        this.templateMapper = templateMapper;
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.feedbackMapper = feedbackMapper;
        this.resumeService = resumeService;
        this.builtinTemplatePackages = builtinTemplatePackages;
        this.codec = codec;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<TemplatePackage> packages = builtinTemplatePackages.load();
        migrateLegacyCommunityCodes(packages);
        seedTemplates(packages);
        if (seedDemo) {
            User demo = ensureUser("demo", demoPassword, "郝简历", "demo@miniwho.local", "user");
            ensureAdmin();
            seedDemoResume(demo);
            seedFeedback(demo);
        }
        syncUsageCounts();
    }

    /**
     * 管理员是公网入口上权限最高的账号，不允许默认口令：账号还不存在且未提供
     * MINIWHO_ADMIN_PASSWORD 时直接拒绝启动，而不是静默建出 admin/admin123。
     */
    private void ensureAdmin() {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "admin").last("limit 1"));
        if (existing != null) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "首次启动需要创建管理员账号，请通过环境变量 MINIWHO_ADMIN_PASSWORD 设置强密码"
                            + "（Docker Compose 部署请在 .env 里填写后再 up）");
        }
        if ("admin123".equalsIgnoreCase(adminPassword.trim())) {
            log.warn("MINIWHO_ADMIN_PASSWORD 仍是示例值 admin123，公网部署前请务必更换");
        }
        ensureUser("admin", adminPassword.trim(), "管理员", "admin@miniwho.local", "admin");
    }

    /**
     * 使用数从只加不减的计数器改成按简历实数之后，存量库里那些已经跑偏的数字要一次性拉回来。
     * 之后由 ResumeService 在增删改模板后重算，这里只是兜住历史数据。
     */
    private void syncUsageCounts() {
        templateMapper.update(null, new LambdaUpdateWrapper<Template>().setSql(
                "usage_count = (select count(*) from resumes where resumes.template_code = templates.code"
                        + " and resumes.deleted = 0)"));
    }

    /**
     * 转正前这批包以「cosolar--<code>」的导入编码存在：库里可能已有该编码的模板行和正在用它的简历。
     * 把它们迁到官方裸编码后，随后的种子流程会把升级出来的行接管为官方模板；样式作用域类 tw-cosolar--x
     * 也随之换成 tw-x（config 由种子按新包重算，PDF 缓存键跟着变化）。
     */
    private void migrateLegacyCommunityCodes(List<TemplatePackage> packages) {
        for (TemplatePackage pkg : packages) {
            if (!"official".equals(pkg.namespace())) {
                continue;
            }
            String legacy = LEGACY_AUTHOR_NAMESPACE + "--" + pkg.code();
            Template legacyRow = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                    .eq(Template::getCode, legacy).last("limit 1"));
            if (legacyRow == null) {
                continue;
            }
            for (Resume resume : resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getTemplateCode, legacy))) {
                ObjectNode style = parseObject(resume.getStyle());
                ObjectNode styleUpdate = null;
                if (style != null && !pkg.code().equals(style.path("templateCode").asText(null))) {
                    style.put("templateCode", pkg.code());
                    styleUpdate = style;
                }
                resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                        .eq(Resume::getId, resume.getId())
                        .set(Resume::getTemplateCode, pkg.code())
                        .set(styleUpdate != null, Resume::getStyle, styleUpdate == null ? null : json.write(styleUpdate)));
            }
            Template officialRow = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                    .eq(Template::getCode, pkg.code()).last("limit 1"));
            if (officialRow == null) {
                legacyRow.setCode(pkg.code());
                templateMapper.updateById(legacyRow);
                log.info("模板 {} 已随发版迁移为官方编码 {}", legacy, pkg.code());
            } else {
                log.info("官方编码 {} 已存在，旧导入模板 {} 保留原行", pkg.code(), legacy);
            }
        }
    }

    private ObjectNode parseObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return (ObjectNode) json.mapper().readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private void seedTemplates(List<TemplatePackage> packages) {
        for (TemplatePackage pkg : packages) {
            String code = builtinCode(pkg);
            String config = json.write(builtinConfig(pkg, code));
            Template existing = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                    .eq(Template::getCode, code).last("limit 1"));
            if (existing == null) {
                Template template = new Template();
                applyPackage(template, pkg, config);
                template.setCode(code);
                template.setStatus(1);
                template.setUsageCount(0);
                templateMapper.insert(template);
                continue;
            }
            String source = existing.getSource() == null ? "builtin" : existing.getSource();
            if (!"builtin".equals(source)) {
                // 作者把社区模板转正随发版下发：同编码原地升级为官方模板，简历的 template_code 不用动。
                log.info("编码 {} 原为导入模板，已随发版升级为官方模板", code);
                existing.setStatus(1);
                existing.setVisibility("public");
                existing.setExportable(1);
                existing.setOwnerUserId(null);
                applyPackage(existing, pkg, config);
                existing.setCode(code);
                templateMapper.updateById(existing);
                continue;
            }
            // 官方区的版式只由发版里的包文件决定；status 是管理员改的，usageCount 是用户用出来的，都不动。
            if (needsRefresh(existing, pkg, config)) {
                applyPackage(existing, pkg, config);
                existing.setCode(code);
                templateMapper.updateById(existing);
                log.info("已刷新内置模板：{}", code);
            }
        }
    }

    /** 官方命名空间沿用裸编码（与历史官方模板一致）；作者自带命名空间的包按导入规则合成编码。 */
    private String builtinCode(TemplatePackage pkg) {
        if ("official".equals(pkg.namespace())) {
            return pkg.code();
        }
        return TemplatePackageCodec.composedCode(pkg.namespace(), pkg.code());
    }

    /** 内置包允许带 style：与导入包走同一条净化链路，样式跟着 JSON 走，改样式不用动骨架代码。 */
    private ObjectNode builtinConfig(TemplatePackage pkg, String code) {
        if (pkg.style() == null || pkg.style().isBlank()) {
            return codec.renderConfig(pkg);
        }
        try {
            String styleCss = TemplateCssSanitizer.sanitize(pkg.style(), TemplatePackageCodec.scopeClassOf(code));
            return codec.renderConfig(pkg, TemplatePackageCodec.scopeClassOf(code), styleCss);
        } catch (TemplatePackageException e) {
            throw new IllegalStateException("内置模板包 " + code + " 的 style 不合法：" + e.getMessage(), e);
        }
    }

    private boolean needsRefresh(Template existing, TemplatePackage pkg, String config) {
        return !config.equals(existing.getConfig())
                || !pkg.canonical().equals(existing.getPkg())
                || !pkg.name().equals(existing.getName())
                || !equalsOrEmpty(pkg.description(), existing.getDescription())
                || !equalsOrEmpty(pkg.category(), existing.getCategory())
                || !String.join(",", pkg.tags()).equals(emptyToBlank(existing.getTags()))
                || !pkg.version().equals(existing.getVersion())
                || !equalsOrEmpty(pkg.author(), existing.getAuthor())
                || !Integer.valueOf(pkg.sortWeight()).equals(existing.getSortWeight());
    }

    private void applyPackage(Template target, TemplatePackage pkg, String config) {
        target.setName(pkg.name());
        target.setDescription(pkg.description());
        target.setCategory(pkg.category());
        target.setTags(String.join(",", pkg.tags()));
        target.setConfig(config);
        target.setPkg(pkg.canonical());
        target.setNamespace(pkg.namespace());
        target.setVersion(pkg.version());
        target.setAuthor(pkg.author());
        target.setSource("builtin");
        target.setApi(pkg.api());
        target.setChecksum(pkg.checksum());
        target.setSortWeight(pkg.sortWeight());
    }

    private boolean equalsOrEmpty(String expected, String actual) {
        return expected == null ? actual == null || actual.isBlank() : expected.equals(actual);
    }

    private String emptyToBlank(String value) {
        return value == null ? "" : value;
    }

    private User ensureUser(String username, String password, String nickname, String email, String role) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        log.info("已创建{}账号：{}", "admin".equals(role) ? "管理员" : "演示", username);
        return user;
    }

    private void seedDemoResume(User demo) {
        long count = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, demo.getId()));
        if (count > 0) {
            return;
        }
        var resume = resumeService.create(demo.getId(),
                new CreateResumeRequest("郝简历 · AI Agent Harness 研究员", "zh", "qinghe", true));
        resumeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.id())
                .set(Resume::getIsSample, 1)
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        log.info("已创建演示简历并设为首页示例：{}", resume.title());
    }

    private void seedFeedback(User demo) {
        long count = feedbackMapper.selectCount(null);
        if (count > 0) {
            return;
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(demo.getId());
        feedback.setContent("模板排版很干净，希望后续能支持导出 Word 和版本历史。");
        feedback.setContact("demo@miniwho.local");
        feedback.setStatus(0);
        feedbackMapper.insert(feedback);
    }
}
