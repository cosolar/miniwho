package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.TemplateDtos.TemplateVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.Template;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.template.TemplateCssSanitizer;
import cn.minims.who.template.TemplatePackage;
import cn.minims.who.template.TemplatePackageCodec;
import cn.minims.who.template.TemplatePackageException;
import cn.minims.who.util.Json;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class TemplateService {

    /** 官方包保留给随应用发版的模板，导入包不得冒用。 */
    private static final String OFFICIAL_NAMESPACE = "official";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String VISIBILITY_PRIVATE = "private";

    private final TemplateMapper templateMapper;
    private final ResumeMapper resumeMapper;
    private final TemplatePackageCodec codec;
    private final Json json;

    public TemplateService(TemplateMapper templateMapper, ResumeMapper resumeMapper,
                           TemplatePackageCodec codec, Json json) {
        this.templateMapper = templateMapper;
        this.resumeMapper = resumeMapper;
        this.codec = codec;
        this.json = json;
    }

    /**
     * 模板中心的数据源：官方区 + 别人已分享到站内的包。
     * 自己导入但未分享的模板只走 /mine，不出现在公开列表里。
     */
    @Cacheable(cacheNames = "template-active", key = "'list'")
    public List<TemplateVo> listActive() {
        return templateMapper.selectList(new LambdaQueryWrapper<Template>()
                        .eq(Template::getStatus, 1)
                        .and(scope -> scope
                                .eq(Template::getSource, "builtin").or().isNull(Template::getSource)
                                .or(community -> community
                                        .eq(Template::getSource, "imported")
                                        .eq(Template::getVisibility, VISIBILITY_PUBLIC)))
                        .orderByAsc(Template::getSortWeight)
                        .orderByAsc(Template::getId))
                .stream().map(this::toVo).toList();
    }

    public List<TemplateVo> mine(long userId) {
        return templateMapper.selectList(new LambdaQueryWrapper<Template>()
                        .eq(Template::getSource, "imported")
                        .eq(Template::getOwnerUserId, userId)
                        .orderByDesc(Template::getUpdatedAt))
                .stream().map(this::toVo).toList();
    }

    /**
     * 导入别人设计的模板包：解码 → 命名空间合成编码 → CSS 净化 → 落库为私有模板。
     * 同作者重复导入按版本升级处理，原地覆盖；编码已被别人或官方占用时直接冲突返回。
     */
    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public TemplateVo importPackage(long userId, JsonNode body) {
        TemplatePackage pkg = decodeOrThrow(body);
        if (OFFICIAL_NAMESPACE.equals(pkg.namespace())) {
            throw BizException.badRequest("official 命名空间保留给随应用发版的官方模板包");
        }
        String code;
        String styleCss;
        try {
            code = TemplatePackageCodec.composedCode(pkg.namespace(), pkg.code());
            styleCss = TemplateCssSanitizer.sanitize(pkg.style(), TemplatePackageCodec.scopeClassOf(code));
        } catch (TemplatePackageException e) {
            throw invalidPackage(e.problems());
        }
        String config = json.write(codec.renderConfig(pkg, TemplatePackageCodec.scopeClassOf(code), styleCss));

        Template existing = findByCode(code);
        if (existing != null && !isOwnedImport(existing, userId)) {
            throw new BizException("TEMPLATE_CODE_TAKEN",
                    "模板编码 " + code + " 已被占用，请修改包里的 id.code 或 id.namespace",
                    HttpStatus.CONFLICT, Map.of("code", code));
        }
        Template target = existing == null ? new Template() : existing;
        applyPackage(target, pkg, code, config, userId);
        if (existing == null) {
            target.setStatus(1);
            target.setUsageCount(0);
            target.setVisibility(VISIBILITY_PRIVATE);
            target.setExportable(1);
            try {
                templateMapper.insert(target);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 先查后插之间编码被并发导入抢注，唯一索引兜底。
                throw new BizException("TEMPLATE_CODE_TAKEN",
                        "模板编码 " + code + " 已被占用，请修改包里的 id.code 或 id.namespace",
                        HttpStatus.CONFLICT, Map.of("code", code));
            }
        } else {
            // 重新导入只换版式，分享状态与导出权限由作者上一次的选择决定，不在这里重置。
            templateMapper.updateById(target);
        }
        return toVo(target);
    }

    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public void remove(String code, long userId, boolean admin) {
        Template template = requireAny(code);
        if (!"imported".equals(template.getSource())) {
            throw BizException.forbidden("官方模板随应用发版，只能由管理员在后台下架，不能删除");
        }
        if (!admin && !Long.valueOf(userId).equals(template.getOwnerUserId())) {
            throw BizException.forbidden("只能删除自己导入的模板");
        }
        long inUse = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getTemplateCode, code));
        if (inUse > 0) {
            throw new BizException("TEMPLATE_IN_USE",
                    "还有 " + inUse + " 份简历正在使用这个模板，请先给它们换掉模板",
                    HttpStatus.CONFLICT, Map.of("resumeCount", inUse));
        }
        templateMapper.deleteById(template.getId());
    }

    /**
     * 站内分享：shared 决定进不进模板中心，exportable 决定别人能否把模板包下载走。
     * 只影响本站可见性，不上传到任何中央服务器；取消分享后别人已用的简历照常渲染，只是不再能被新简历选中。
     */
    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public TemplateVo share(String code, long userId, boolean admin, boolean shared, boolean exportable) {
        Template template = requireAny(code);
        if (!"imported".equals(template.getSource())) {
            throw BizException.badRequest("官方模板随应用发版，不参与站内分享");
        }
        if (!admin && !Long.valueOf(userId).equals(template.getOwnerUserId())) {
            throw BizException.forbidden("只能分享自己导入的模板");
        }
        template.setVisibility(shared ? VISIBILITY_PUBLIC : VISIBILITY_PRIVATE);
        template.setExportable(exportable ? 1 : 0);
        templateMapper.updateById(template);
        return toVo(template);
    }

    @Cacheable(cacheNames = "template-detail", key = "#code")
    public TemplateVo getActive(String code, LongSupplier viewerId, boolean admin) {
        Template template = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, code)
                .eq(Template::getStatus, 1)
                .last("limit 1"));
        if (template == null) {
            throw BizException.notFound("模板不存在或已下架");
        }
        // 私有导入包不能只凭编码就读到配置；但已被任何简历在用的模板除外——
        // 作者取消分享后，别人已有的简历仍要能渲染，与 requireUsable 的放行口径一致。
        if (!canViewConfig(template, viewerId, admin)) {
            throw BizException.notFound("模板不存在或已下架");
        }
        return toVo(template);
    }

    private boolean canViewConfig(Template template, LongSupplier viewerId, boolean admin) {
        String source = template.getSource() == null || template.getSource().isBlank()
                ? "builtin" : template.getSource();
        if ("builtin".equals(source) || VISIBILITY_PUBLIC.equals(template.getVisibility())) {
            return true;
        }
        if (admin) {
            return true;
        }
        if (template.getUsageCount() != null && template.getUsageCount() > 0) {
            return true;
        }
        try {
            return template.getOwnerUserId() != null
                    && viewerId.getAsLong() == template.getOwnerUserId();
        } catch (Exception e) {
            // 未登录时取不到身份；私有且无人在用的模板一律 404，不暴露存在性。
            return false;
        }
    }

    public Template requireAny(String code) {
        Template template = findByCode(code);
        if (template == null) {
            throw BizException.badRequest("模板不存在");
        }
        return template;
    }

    /** 模板包自带的预制示例数据；没带或包损坏时返回 null，由调用方回退到全局示例。 */
    public String sampleContentOf(Template template) {
        String pkg = template.getPkg();
        if (pkg == null || pkg.isBlank()) {
            return null;
        }
        try {
            JsonNode sample = json.read(pkg).at("/sample");
            return sample.isObject() ? json.write(sample) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 新建或换用模板时的可见性关卡：别人未分享的导入包即使知道编码也不能选用。 */
    public Template requireUsable(String code, long userId) {
        Template template = requireAny(code);
        if ("imported".equals(template.getSource())
                && !VISIBILITY_PUBLIC.equals(template.getVisibility())
                && !Long.valueOf(userId).equals(template.getOwnerUserId())) {
            throw BizException.forbidden("该模板还没有分享到模板中心，只有作者自己能选用");
        }
        return template;
    }

    public Template findByCode(String code) {
        return templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, code)
                .last("limit 1"));
    }

    /** 已分享且作者允许导出的包，本站所有用户都能取走原文；关掉开关后别人只能选用，不能导出。 */
    public boolean isPubliclyExportable(Template template) {
        return "imported".equals(template.getSource())
                && VISIBILITY_PUBLIC.equals(template.getVisibility())
                && Integer.valueOf(1).equals(template.getExportable());
    }

    /** 官方包与已分享且允许导出的包之外，只有作者本人或管理员能取走原文。 */
    public boolean canExport(Template template, long userId, boolean admin) {
        return isPubliclyExportable(template)
                || "builtin".equals(template.getSource())
                || admin
                || Long.valueOf(userId).equals(template.getOwnerUserId());
    }

    /**
     * 导出包原文：官方包与作者开放的社区包对所有人开放，其余只认作者与管理员。
     *
     * <p>viewerId 延迟到私有包分支才取值：公开包不需要登录，未登录时也不该提前抛 401；
     * 私有包在未登录时由取身份的一方抛出未登录异常，与改造前的行为保持一致。
     */
    public TemplateExport download(String code, LongSupplier viewerId, boolean admin) {
        Template template = findByCode(code);
        if (template == null) {
            throw BizException.notFound("模板不存在");
        }
        boolean official = template.getSource() == null || "builtin".equals(template.getSource());
        if (!official && !isPubliclyExportable(template)
                && !canExport(template, viewerId.getAsLong(), admin)) {
            throw BizException.forbidden("作者没有开放这个模板包的导出");
        }
        if (template.getPkg() == null || template.getPkg().isBlank()) {
            throw BizException.notFound("该模板没有可导出的包文件");
        }
        String version = template.getVersion() == null || template.getVersion().isBlank()
                ? "1.0.0" : template.getVersion();
        return new TemplateExport(template.getCode() + "-" + version + ".json", codec.pretty(template.getPkg()));
    }

    /** 模板包下载体：文件名与格式化后的包原文都由服务层给出。 */
    public record TemplateExport(String fileName, String content) {
    }

    /** 官方区备份：随发版进来的内置包原样打成一个数组，供部署迁移时一次导入。 */
    public ArrayNode officialPackages() {
        ArrayNode bundle = json.array();
        templateMapper.selectList(new LambdaQueryWrapper<Template>()
                        .eq(Template::getSource, "builtin")
                        .orderByAsc(Template::getSortWeight)
                        .orderByAsc(Template::getId))
                .stream()
                .filter(template -> template.getPkg() != null && !template.getPkg().isBlank())
                .forEach(template -> bundle.add(json.read(template.getPkg())));
        return bundle;
    }

    /**
     * 使用数按「当前还有几份简历在用」重算，而不是只加不减的累计。
     * 界面上这句话与删除前的 TEMPLATE_IN_USE 关卡必须同源，否则删掉一份简历后卡片还挂着旧数字，
     * 而模板其实已经能删了。调用方要在简历落库之后再打，否则数出来的是改模板前的状态。
     */
    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public void refreshUsage(String... codes) {
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            long inUse = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getTemplateCode, code));
            templateMapper.update(null, new LambdaUpdateWrapper<Template>()
                    .eq(Template::getCode, code)
                    .set(Template::getUsageCount, (int) inUse));
        }
    }

    public TemplateVo toVo(Template template) {
        List<String> tags = template.getTags() == null || template.getTags().isBlank()
                ? List.of()
                : Arrays.stream(template.getTags().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        String source = template.getSource() == null || template.getSource().isBlank()
                ? "builtin" : template.getSource();
        String visibility = VISIBILITY_PUBLIC.equals(template.getVisibility()) ? VISIBILITY_PUBLIC : VISIBILITY_PRIVATE;
        return new TemplateVo(template.getCode(), template.getName(), template.getDescription(),
                template.getCategory(), tags, json.read(template.getConfig()), template.getUsageCount(),
                template.getVersion(), template.getAuthor(), source, visibility,
                Integer.valueOf(1).equals(template.getExportable()));
    }

    public List<Template> all() {
        return templateMapper.selectList(new LambdaQueryWrapper<Template>()
                .orderByAsc(Template::getSortWeight).orderByAsc(Template::getId));
    }

    private void applyPackage(Template target, TemplatePackage pkg, String code, String config, long userId) {
        target.setCode(code);
        target.setName(pkg.name());
        target.setDescription(pkg.description());
        target.setCategory(pkg.category());
        target.setTags(String.join(",", pkg.tags()));
        target.setConfig(config);
        target.setPkg(pkg.canonical());
        target.setNamespace(pkg.namespace());
        target.setVersion(pkg.version());
        target.setAuthor(pkg.author());
        target.setSource("imported");
        target.setApi(pkg.api());
        target.setChecksum(pkg.checksum());
        target.setSortWeight(pkg.sortWeight());
        target.setOwnerUserId(userId);
    }

    private boolean isOwnedImport(Template template, long userId) {
        return "imported".equals(template.getSource()) && Long.valueOf(userId).equals(template.getOwnerUserId());
    }

    private TemplatePackage decodeOrThrow(JsonNode body) {
        try {
            return codec.decode(json.write(body));
        } catch (TemplatePackageException e) {
            throw invalidPackage(e.problems());
        }
    }

    private BizException invalidPackage(List<String> problems) {
        return new BizException("TEMPLATE_PACKAGE_INVALID",
                String.join("；", problems), HttpStatus.BAD_REQUEST, Map.of("problems", problems));
    }
}
