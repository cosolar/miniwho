package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.BizException;
import cn.minims.who.content.ContentDefaults;
import cn.minims.who.content.ContentSanitizer;
import cn.minims.who.content.ScoreEngine;
import cn.minims.who.content.SectionTypes;
import cn.minims.who.dto.Dtos;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeAttachment;
import cn.minims.who.entity.ResumeReport;
import cn.minims.who.entity.Template;
import cn.minims.who.mapper.ResumeAttachmentMapper;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeReportMapper;
import cn.minims.who.mapper.ResumeShareMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.util.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.minims.who.dto.Dtos.AttachmentVo;
import static cn.minims.who.dto.Dtos.CreateResumeRequest;
import static cn.minims.who.dto.Dtos.ModuleAddRequest;
import static cn.minims.who.dto.Dtos.PrefsSaveRequest;
import static cn.minims.who.dto.Dtos.RenameRequest;
import static cn.minims.who.dto.Dtos.ResumeDetail;
import static cn.minims.who.dto.Dtos.ResumeSummary;
import static cn.minims.who.dto.Dtos.SaveResult;
import static cn.minims.who.dto.Dtos.SectionSaveRequest;
import static cn.minims.who.dto.Dtos.StyleSaveRequest;
import static cn.minims.who.dto.Dtos.WorkbenchResponse;

@Service
public class ResumeService {

    public static final int MAX_RESUMES = 50;
    public static final int MAX_ATTACHMENTS = 3;

    private final ResumeMapper resumeMapper;
    private final TemplateMapper templateMapper;
    private final ResumeAttachmentMapper attachmentMapper;
    private final ResumeShareMapper shareMapper;
    private final ResumeReportMapper reportMapper;
    private final ContentDefaults defaults;
    private final ContentSanitizer sanitizer;
    private final ScoreEngine scoreEngine;
    private final TemplateService templateService;
    private final AttachmentStore store;
    private final AvatarService avatarService;
    private final ItemMediaService itemMediaService;
    private final Json json;

    public ResumeService(ResumeMapper resumeMapper, TemplateMapper templateMapper,
                         ResumeAttachmentMapper attachmentMapper, ResumeShareMapper shareMapper,
                         ResumeReportMapper reportMapper,
                         ContentDefaults defaults, ContentSanitizer sanitizer, ScoreEngine scoreEngine,
                         TemplateService templateService, AttachmentStore store, AvatarService avatarService,
                         ItemMediaService itemMediaService, Json json) {
        this.resumeMapper = resumeMapper;
        this.templateMapper = templateMapper;
        this.attachmentMapper = attachmentMapper;
        this.shareMapper = shareMapper;
        this.reportMapper = reportMapper;
        this.defaults = defaults;
        this.sanitizer = sanitizer;
        this.scoreEngine = scoreEngine;
        this.templateService = templateService;
        this.store = store;
        this.avatarService = avatarService;
        this.itemMediaService = itemMediaService;
        this.json = json;
    }

    public List<ResumeSummary> list(long userId) {
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt));
        Map<String, String> templateNames = templateNames();
        List<ResumeSummary> result = new ArrayList<>();
        for (Resume resume : resumes) {
            JsonNode content = json.read(resume.getContent());
            result.add(new ResumeSummary(resume.getId(), resume.getTitle(), resume.getLanguage(),
                    resume.getTemplateCode(), templateNames.getOrDefault(resume.getTemplateCode(), resume.getTemplateCode()),
                    resume.getScore(), resume.getCompleteness(), resume.getUpdatedAt(), targetJob(content)));
        }
        return result;
    }

    /** 全站唯一示例简历；没有设置时返回 null，由前端回落到打包的默认示例。 */
    public Dtos.SampleResume sampleResume() {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getIsSample, 1)
                .orderByDesc(Resume::getUpdatedAt)
                .last("limit 1"));
        if (resume == null) {
            return null;
        }
        return new Dtos.SampleResume(resume.getTemplateCode(), json.read(resume.getContent()));
    }

    @Transactional
    public ResumeDetail create(long userId, CreateResumeRequest request) {
        long count = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        if (count >= MAX_RESUMES) {
            throw BizException.badRequest("最多只能创建 " + MAX_RESUMES + " 份简历");
        }
        String language = "en".equalsIgnoreCase(request.language()) ? "en" : "zh";
        String templateCode = request.templateCode() == null || request.templateCode().isBlank()
                ? "qinghe" : request.templateCode().trim();
        Template template = templateService.requireUsable(templateCode, userId);
        if (!Objects.equals(template.getStatus(), 1)) {
            throw BizException.badRequest("所选模板已下架，请选择其他模板");
        }

        boolean useSample = Boolean.TRUE.equals(request.useSample()) && "zh".equals(language);
        ObjectNode content = useSample ? defaults.sampleContent() : defaults.blankContent(language);
        ObjectNode prefs = defaults.defaultPrefs(content);
        ObjectNode style = defaults.defaultStyle(templateCode);
        ScoreEngine.Analysis analysis = scoreEngine.analyze(content);

        Resume resume = new Resume();
        resume.setUserId(userId);
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isBlank()) {
            title = "en".equals(language) ? "Untitled Resume" : "未命名简历";
        }
        resume.setTitle(title.length() > 50 ? title.substring(0, 50) : title);
        resume.setLanguage(language);
        resume.setTemplateCode(templateCode);
        resume.setSource(useSample ? "sample" : "blank");
        resume.setContent(json.write(content));
        resume.setModulePrefs(json.write(prefs));
        resume.setStyle(json.write(style));
        resume.setContentVersion(0);
        resume.setCompleteness(analysis.completeness());
        resume.setScore(analysis.totalScore());
        resume.setScoreUpdatedAt(System.currentTimeMillis());
        resume.setLifecycleStatus(1);
        resume.setDeleted(0);
        resumeMapper.insert(resume);
        templateService.refreshUsage(templateCode);
        return detail(userId, resume.getId());
    }

    public ResumeDetail detail(long userId, long id) {
        Resume resume = findOwned(userId, id);
        return toDetail(resume);
    }

    public WorkbenchResponse workbench(long userId, long id) {
        Resume resume = findOwned(userId, id);
        List<AttachmentVo> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<ResumeAttachment>()
                        .eq(ResumeAttachment::getResumeId, id)
                        .orderByDesc(ResumeAttachment::getCreatedAt))
                .stream()
                .map(a -> new AttachmentVo(a.getId(), a.getFileName(), a.getMime(), a.getSizeBytes(), a.getCreatedAt()))
                .toList();
        return new WorkbenchResponse(toDetail(resume), attachments);
    }

    @Transactional
    public ResumeDetail rename(long userId, long id, RenameRequest request) {
        Resume resume = findOwned(userId, id);
        String title = request.title().trim();
        if (title.isBlank()) {
            throw BizException.badRequest("简历名称不能为空");
        }
        if (title.length() > 50) {
            throw BizException.badRequest("简历名称不能超过 50 个字符");
        }
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getTitle, title)
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        return detail(userId, id);
    }

    @Transactional
    public ResumeDetail duplicate(long userId, long id) {
        Resume source = findOwned(userId, id);
        long count = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        if (count >= MAX_RESUMES) {
            throw BizException.badRequest("最多只能创建 " + MAX_RESUMES + " 份简历");
        }
        ObjectNode content = parseObject(source.getContent());
        String title = source.getTitle() + " 副本";
        if (title.length() > 50) {
            title = title.substring(0, 50);
        }
        ScoreEngine.Analysis analysis = scoreEngine.analyze(content);
        Resume copy = new Resume();
        copy.setUserId(userId);
        copy.setTitle(title);
        copy.setLanguage(source.getLanguage());
        copy.setTemplateCode(source.getTemplateCode());
        copy.setSource("duplicate");
        copy.setContent(source.getContent());
        copy.setModulePrefs(source.getModulePrefs());
        copy.setStyle(source.getStyle());
        copy.setContentVersion(0);
        copy.setCompleteness(analysis.completeness());
        copy.setScore(analysis.totalScore());
        copy.setScoreUpdatedAt(System.currentTimeMillis());
        copy.setLifecycleStatus(1);
        copy.setDeleted(0);
        resumeMapper.insert(copy);
        avatarService.copy(source, copy);
        itemMediaService.copyInto(source, copy);
        return detail(userId, copy.getId());
    }

    @Transactional
    public void delete(long userId, long id) {
        Resume resume = findOwned(userId, id);
        shareMapper.update(null, new LambdaUpdateWrapper<cn.minims.who.entity.ResumeShare>()
                .eq(cn.minims.who.entity.ResumeShare::getResumeId, resume.getId())
                .isNull(cn.minims.who.entity.ResumeShare::getRevokedAt)
                .set(cn.minims.who.entity.ResumeShare::getRevokedAt, System.currentTimeMillis()));
        List<ResumeAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<ResumeAttachment>().eq(ResumeAttachment::getResumeId, resume.getId()));
        attachmentMapper.delete(new LambdaQueryWrapper<ResumeAttachment>()
                .eq(ResumeAttachment::getResumeId, resume.getId()));
        reportMapper.delete(new LambdaQueryWrapper<ResumeReport>()
                .eq(ResumeReport::getResumeId, resume.getId()));
        resumeMapper.deleteById(resume.getId());
        templateService.refreshUsage(resume.getTemplateCode());
        for (ResumeAttachment attachment : attachments) {
            store.deleteQuietly(attachment.getStorageKey());
        }
        store.deleteDirectoryQuietly("attachments/" + resume.getId());
        avatarService.purge(resume.getId(), resume.getAvatarKey());
        itemMediaService.purge(resume);
    }

    @Transactional
    public SaveResult saveSection(long userId, long id, String sectionType, SectionSaveRequest request) {
        if (!SectionTypes.isValid(sectionType)) {
            throw BizException.badRequest("不支持的模块类型");
        }
        Resume resume = findOwned(userId, id);
        ensureVersion(resume, request.contentVersion());
        ObjectNode content = parseObject(resume.getContent());
        ArrayNode sections = (ArrayNode) content.get("sections");
        int index = indexOf(sections, sectionType);
        if (index < 0) {
            throw BizException.badRequest("模块不存在，请先添加模块");
        }
        ObjectNode existing = (ObjectNode) sections.get(index);
        ObjectNode sanitized = sanitizer.sanitizeSection(request.section(), sectionType);
        sanitized.put("id", existing.path("id").asText("sec_" + sectionType));
        sanitized.put("title", existing.path("title").asText(SectionTypes.TITLES.get(sectionType)));
        sanitized.put("order", existing.path("order").asInt(index));
        sanitized.put("visible", existing.path("visible").asBoolean(true));
        if (SectionTypes.SINGLE_ITEM_TYPES.contains(sectionType)
                && sanitized.path("items").size() > 1) {
            ArrayNode one = json.array();
            one.add(sanitized.path("items").get(0));
            sanitized.set("items", one);
        }
        if (SectionTypes.isCustom(sectionType) && sanitized.path("items").size() > 10) {
            throw BizException.badRequest("自定义模块最多填写 10 条内容");
        }
        sections.set(index, sanitized);
        content.set("sections", sections);
        return persistContent(resume, content, parseObject(resume.getModulePrefs()), request.contentVersion());
    }

    @Transactional
    public SaveResult savePrefs(long userId, long id, PrefsSaveRequest request) {
        Resume resume = findOwned(userId, id);
        ensureVersion(resume, request.contentVersion());
        ObjectNode content = parseObject(resume.getContent());
        ObjectNode prefs = parseObject(resume.getModulePrefs());
        ArrayNode sections = (ArrayNode) content.get("sections");

        List<String> existingTypes = new ArrayList<>();
        for (JsonNode section : sections) {
            existingTypes.add(section.path("type").asText());
        }
        LinkedHashSet<String> orderSet = new LinkedHashSet<>(request.order());
        if (orderSet.size() != request.order().size()
                || orderSet.size() != existingTypes.size()
                || !orderSet.containsAll(existingTypes)) {
            throw BizException.badRequest("模块顺序与当前模块不一致");
        }
        Set<String> hiddenSet = new HashSet<>(request.hidden());
        if (hiddenSet.contains(SectionTypes.BASIC) || hiddenSet.contains(SectionTypes.EDUCATION)) {
            throw BizException.badRequest("基本信息与教育背景不能隐藏");
        }
        JsonNode customTitles = request.customTitles();
        if (customTitles == null || !customTitles.isObject()) {
            customTitles = prefs.path("customTitles");
            if (!customTitles.isObject()) customTitles = json.object();
        }
        customTitles = sanitizer.sanitizeValue(customTitles, 0, "customTitles");

        ArrayNode orderNode = json.array();
        for (String type : request.order()) orderNode.add(type);
        ArrayNode hiddenNode = json.array();
        for (String type : request.hidden()) {
            if (existingTypes.contains(type)) hiddenNode.add(type);
        }
        prefs.set("order", orderNode);
        prefs.set("hidden", hiddenNode);
        prefs.set("customTitles", customTitles);

        int orderIndex = 0;
        for (String type : request.order()) {
            int sectionIndex = indexOf(sections, type);
            if (sectionIndex < 0) continue;
            ObjectNode section = (ObjectNode) sections.get(sectionIndex);
            section.put("order", orderIndex++);
            section.put("visible", !hiddenSet.contains(type));
            JsonNode customTitle = customTitles.get(type);
            if (customTitle != null && customTitle.isTextual() && !customTitle.asText().isBlank()
                    && SectionTypes.isOptional(type)) {
                String t = customTitle.asText().trim();
                int limit = SectionTypes.isCustom(type) ? 20 : 30;
                section.put("title", t.length() > limit ? t.substring(0, limit) : t);
            }
        }
        content.set("sections", sections);
        return persistContent(resume, content, prefs, request.contentVersion());
    }

    @Transactional
    public ResumeDetail addModule(long userId, long id, ModuleAddRequest request) {
        Resume resume = findOwned(userId, id);
        ensureVersion(resume, request.contentVersion());
        JsonNode raw = request.section();
        String type = raw == null ? "" : raw.path("type").asText("");
        if (!SectionTypes.isOptional(type)) {
            throw BizException.notFound("该模块不可通过模块库添加");
        }
        ObjectNode content = parseObject(resume.getContent());
        ArrayNode sections = (ArrayNode) content.get("sections");
        if (indexOf(sections, type) >= 0) {
            throw BizException.badRequest("该模块已添加");
        }
        if (SectionTypes.isCustom(type)) {
            long customCount = 0;
            for (JsonNode section : sections) {
                if (SectionTypes.isCustom(section.path("type").asText())) customCount++;
            }
            if (customCount >= 5) {
                throw BizException.badRequest("自定义模块最多添加 5 个");
            }
        }
        ObjectNode sanitized = sanitizer.sanitizeSection(raw, type);
        sanitized.put("visible", true);
        sanitized.put("order", sections.size());
        if (SectionTypes.isCustom(type)) {
            String customTitle = sanitized.path("meta").path("customTitle").asText("");
            if (customTitle.isBlank()) customTitle = sanitized.path("title").asText("自定义模块");
            if (customTitle.isBlank()) customTitle = "自定义模块";
            sanitized.put("title", customTitle.length() > 20 ? customTitle.substring(0, 20) : customTitle);
        }
        sections.add(sanitized);
        content.set("sections", sections);

        ObjectNode prefs = parseObject(resume.getModulePrefs());
        ArrayNode order = (ArrayNode) prefs.path("order");
        ArrayNode newOrder = json.array();
        for (JsonNode node : order) {
            if (!type.equals(node.asText())) newOrder.add(node.asText());
        }
        newOrder.add(type);
        prefs.set("order", newOrder);
        ArrayNode hidden = (ArrayNode) prefs.path("hidden");
        ArrayNode newHidden = json.array();
        for (JsonNode node : hidden) {
            if (!type.equals(node.asText())) newHidden.add(node.asText());
        }
        prefs.set("hidden", newHidden);
        persistContent(resume, content, prefs, request.contentVersion());
        return detail(userId, id);
    }

    @Transactional
    public ResumeDetail removeModule(long userId, long id, String sectionType, int contentVersion) {
        if (!SectionTypes.isOptional(sectionType)) {
            throw BizException.badRequest("该模块不可删除");
        }
        Resume resume = findOwned(userId, id);
        ensureVersion(resume, contentVersion);
        ObjectNode content = parseObject(resume.getContent());
        ArrayNode sections = (ArrayNode) content.get("sections");
        int index = indexOf(sections, sectionType);
        if (index < 0) {
            throw BizException.notFound("模块不存在");
        }
        ArrayNode remaining = json.array();
        for (JsonNode section : sections) {
            if (!sectionType.equals(section.path("type").asText())) {
                remaining.add(section);
            }
        }
        content.set("sections", remaining);

        ObjectNode prefs = parseObject(resume.getModulePrefs());
        ArrayNode order = json.array();
        for (JsonNode node : prefs.path("order")) {
            if (!sectionType.equals(node.asText())) order.add(node.asText());
        }
        prefs.set("order", order);
        ArrayNode hidden = json.array();
        for (JsonNode node : prefs.path("hidden")) {
            if (!sectionType.equals(node.asText())) hidden.add(node.asText());
        }
        prefs.set("hidden", hidden);
        JsonNode customTitles = prefs.path("customTitles");
        if (customTitles instanceof ObjectNode objectNode) {
            objectNode.remove(sectionType);
        }
        for (int i = 0; i < remaining.size(); i++) {
            ((ObjectNode) remaining.get(i)).put("order", i);
        }
        persistContent(resume, content, prefs, contentVersion);
        return detail(userId, id);
    }

    @Transactional
    public ResumeDetail saveStyle(long userId, long id, StyleSaveRequest request) {
        Resume resume = findOwned(userId, id);
        ObjectNode style = parseObject(resume.getStyle());
        String oldTemplate = style.path("templateCode").asText(resume.getTemplateCode());
        String newTemplate = request.templateCode();
        boolean templateChanged = false;
        if (newTemplate != null && !newTemplate.isBlank()) {
            // 沿用当前模板时不做可见性关卡：作者事后取消分享，不该把别人已在用的简历锁住。
            templateChanged = !newTemplate.equals(oldTemplate);
            if (templateChanged) {
                templateService.requireUsable(newTemplate, userId);
            } else {
                templateService.requireAny(newTemplate);
            }
            style.put("templateCode", newTemplate);
            resume.setTemplateCode(newTemplate);
        }
        if (request.filter() != null) {
            if (!List.of("standard", "soft", "mono").contains(request.filter())) {
                throw BizException.badRequest("不支持的简历滤镜");
            }
            style.put("filter", request.filter());
        }
        if (request.fontSizeStep() != null) style.put("fontSizeStep", request.fontSizeStep());
        if (request.spacingStep() != null) style.put("spacingStep", request.spacingStep());
        if (request.showBrandFooter() != null) style.put("showBrandFooter", request.showBrandFooter());

        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getStyle, json.write(style))
                .set(Resume::getTemplateCode, resume.getTemplateCode())
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        if (templateChanged) {
            // 新旧两边都要重算：换走的那一套少一份，换进来的多一份。
            templateService.refreshUsage(oldTemplate, resume.getTemplateCode());
        }
        return detail(userId, id);
    }

    public Resume findOwned(long userId, long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null || !Objects.equals(resume.getUserId(), userId)) {
            throw BizException.notFound("简历不存在或无权访问");
        }
        return resume;
    }

    public ResumeDetail toDetail(Resume resume) {
        Template template = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, resume.getTemplateCode()).last("limit 1"));
        String templateName = template == null ? resume.getTemplateCode() : template.getName();
        return new ResumeDetail(
                resume.getId(), resume.getTitle(), resume.getLanguage(), resume.getTemplateCode(), templateName,
                resume.getSource(), json.read(resume.getContent()), json.read(resume.getModulePrefs()),
                json.read(resume.getStyle()), resume.getContentVersion(), resume.getCompleteness(),
                resume.getScore(), resume.getScoreUpdatedAt(), resume.getCreatedAt(), resume.getUpdatedAt(),
                avatarService.describe(resume)
        );
    }

    private SaveResult persistContent(Resume resume, ObjectNode content, ObjectNode prefs, int expectedVersion) {
        sanitizer.validateContent(content);
        String contentJson = json.write(content);
        if (contentJson.length() > 1024 * 1024) {
            throw BizException.badRequest("简历内容超过 1MB 限制，请精简后再保存");
        }
        ScoreEngine.Analysis analysis = scoreEngine.analyze(content);
        int newVersion = expectedVersion + 1;
        long now = System.currentTimeMillis();
        int rows = resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .eq(Resume::getContentVersion, expectedVersion)
                .set(Resume::getContent, contentJson)
                .set(Resume::getModulePrefs, json.write(prefs))
                .set(Resume::getContentVersion, newVersion)
                .set(Resume::getCompleteness, analysis.completeness())
                .set(Resume::getScore, analysis.totalScore())
                .set(Resume::getScoreUpdatedAt, now)
                .set(Resume::getUpdatedAt, now));
        if (rows == 0) {
            Resume latest = resumeMapper.selectById(resume.getId());
            throw BizException.conflict("RESUME_VERSION_CONFLICT", "简历已在其他设备更新，请刷新后再试",
                    Map.of("serverVersion", latest == null ? -1 : latest.getContentVersion()));
        }
        itemMediaService.reconcile(resume.getId(), contentJson);
        return new SaveResult(newVersion, now, analysis.completeness(), analysis.totalScore());
    }

    private void ensureVersion(Resume resume, Integer version) {
        if (version == null || !version.equals(resume.getContentVersion())) {
            throw BizException.conflict("RESUME_VERSION_CONFLICT", "简历已在其他设备更新，请刷新后再试",
                    Map.of("serverVersion", resume.getContentVersion()));
        }
    }

    private ObjectNode parseObject(String raw) {
        JsonNode node = json.read(raw);
        if (!node.isObject()) {
            throw new IllegalStateException("简历 JSON 数据结构损坏");
        }
        return (ObjectNode) node;
    }

    private int indexOf(ArrayNode sections, String type) {
        for (int i = 0; i < sections.size(); i++) {
            if (type.equals(sections.get(i).path("type").asText())) return i;
        }
        return -1;
    }

    private String targetJob(JsonNode content) {
        for (JsonNode section : content.path("sections")) {
            if (SectionTypes.INTENTION.equals(section.path("type").asText())) {
                return section.path("items").path(0).path("fields").path("jobTitle").asText("");
            }
        }
        return "";
    }

    private Map<String, String> templateNames() {
        Map<String, String> names = new HashMap<>();
        for (Template template : templateService.all()) {
            names.put(template.getCode(), template.getName());
        }
        return names;
    }
}
