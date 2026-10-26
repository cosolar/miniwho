package cn.minims.who.config;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import cn.minims.who.template.TemplatePackage;
import cn.minims.who.template.TemplatePackageCodec;
import cn.minims.who.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

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
        seedTemplates();
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

    private void seedTemplates() {
        for (TemplatePackage pkg : builtinTemplatePackages.load()) {
            String config = json.write(codec.renderConfig(pkg));
            Template existing = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                    .eq(Template::getCode, pkg.code()).last("limit 1"));
            if (existing == null) {
                Template template = new Template();
                applyPackage(template, pkg, config);
                template.setStatus(1);
                template.setUsageCount(0);
                templateMapper.insert(template);
                continue;
            }
            String source = existing.getSource() == null ? "builtin" : existing.getSource();
            if (!"builtin".equals(source)) {
                log.warn("编码 {} 已被导入模板占用，内置模板包不再覆盖它", pkg.code());
                continue;
            }
            // 官方区的版式只由发版里的包文件决定；status 是管理员改的，usageCount 是用户用出来的，都不动。
            if (needsRefresh(existing, pkg, config)) {
                applyPackage(existing, pkg, config);
                templateMapper.updateById(existing);
                log.info("已刷新内置模板：{}", pkg.code());
            }
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
        target.setCode(pkg.code());
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
