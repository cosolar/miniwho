package cn.minims.who.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.BizException;
import cn.minims.who.content.SectionTypes;
import cn.minims.who.dto.Dtos.PublicShareVo;
import cn.minims.who.dto.Dtos.ShareCreateRequest;
import cn.minims.who.dto.Dtos.ShareVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeShare;
import cn.minims.who.entity.Template;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeShareMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserMapper;
import cn.minims.who.util.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ShareService {

    private static final Set<String> ALLOWED_HIDDEN = Set.of("phone", "email", "wechat", "qq", "city", "website");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ResumeService resumeService;
    private final AvatarService avatarService;
    private final ResumeMapper resumeMapper;
    private final ResumeShareMapper shareMapper;
    private final TemplateMapper templateMapper;
    private final UserMapper userMapper;
    private final Json json;

    public ShareService(ResumeService resumeService, AvatarService avatarService, ResumeMapper resumeMapper,
                        ResumeShareMapper shareMapper, TemplateMapper templateMapper, UserMapper userMapper,
                        Json json) {
        this.resumeService = resumeService;
        this.avatarService = avatarService;
        this.resumeMapper = resumeMapper;
        this.shareMapper = shareMapper;
        this.templateMapper = templateMapper;
        this.userMapper = userMapper;
        this.json = json;
    }

    @Transactional
    public ShareVo create(long userId, long resumeId, ShareCreateRequest request) {
        Resume resume = resumeService.findOwned(userId, resumeId);
        String password = request.password() == null ? "" : request.password().trim();
        if (!password.isBlank() && (password.length() < 4 || password.length() > 20)) {
            throw BizException.badRequest("访问密码长度需为 4-20 个字符");
        }
        int days = request.expiresInDays() == null ? 30 : request.expiresInDays();
        if (days != 0 && (days < 1 || days > 365)) {
            throw BizException.badRequest("有效期设置不正确");
        }
        LinkedHashSet<String> hidden = new LinkedHashSet<>();
        if (request.hiddenFields() != null) {
            for (String field : request.hiddenFields()) {
                if (ALLOWED_HIDDEN.contains(field)) hidden.add(field);
            }
        } else {
            hidden.addAll(List.of("phone", "email"));
        }

        long now = System.currentTimeMillis();
        shareMapper.update(null, new LambdaUpdateWrapper<ResumeShare>()
                .eq(ResumeShare::getResumeId, resumeId)
                .eq(ResumeShare::getUserId, userId)
                .isNull(ResumeShare::getRevokedAt)
                .set(ResumeShare::getRevokedAt, now));

        ResumeShare share = new ResumeShare();
        share.setResumeId(resumeId);
        share.setUserId(userId);
        share.setToken(randomToken());
        share.setPasswordHash(password.isBlank() ? null : BCrypt.hashpw(password));
        share.setExpiresAt(days == 0 ? null : now + days * 24L * 60 * 60 * 1000);
        share.setAllowDownload(Boolean.TRUE.equals(request.allowDownload()) ? 1 : 0);
        ArrayNode hiddenNode = json.array();
        hidden.forEach(hiddenNode::add);
        share.setHiddenFields(json.write(hiddenNode));
        share.setViewCount(0L);
        shareMapper.insert(share);
        return toVo(share, resume);
    }

    public List<ShareVo> active(long userId, long resumeId) {
        Resume resume = resumeService.findOwned(userId, resumeId);
        long now = System.currentTimeMillis();
        return shareMapper.selectList(new LambdaQueryWrapper<ResumeShare>()
                        .eq(ResumeShare::getResumeId, resumeId)
                        .eq(ResumeShare::getUserId, userId)
                        .isNull(ResumeShare::getRevokedAt)
                        .and(w -> w.isNull(ResumeShare::getExpiresAt).or().gt(ResumeShare::getExpiresAt, now))
                        .orderByDesc(ResumeShare::getCreatedAt))
                .stream().map(s -> toVo(s, resume)).toList();
    }

    @Transactional
    public void revoke(long userId, long shareId) {
        ResumeShare share = shareMapper.selectById(shareId);
        if (share == null || !Objects.equals(share.getUserId(), userId)) {
            throw BizException.notFound("分享链接不存在");
        }
        shareMapper.update(null, new LambdaUpdateWrapper<ResumeShare>()
                .eq(ResumeShare::getId, shareId)
                .isNull(ResumeShare::getRevokedAt)
                .set(ResumeShare::getRevokedAt, System.currentTimeMillis()));
    }

    public PublicShareVo publicShare(String token, String password) {
        ResumeShare share = shareMapper.selectOne(new LambdaQueryWrapper<ResumeShare>()
                .eq(ResumeShare::getToken, token).last("limit 1"));
        if (share == null || share.getRevokedAt() != null) {
            throw new BizException("SHARE_REVOKED", "该分享链接已失效", HttpStatus.GONE);
        }
        if (share.getExpiresAt() != null && share.getExpiresAt() < System.currentTimeMillis()) {
            throw new BizException("SHARE_EXPIRED", "该分享链接已过期", HttpStatus.GONE);
        }
        if (share.getPasswordHash() != null) {
            if (password == null || password.isBlank()) {
                throw new BizException("SHARE_PASSWORD_REQUIRED", "请输入访问密码", HttpStatus.UNAUTHORIZED);
            }
            if (!BCrypt.checkpw(password, share.getPasswordHash())) {
                throw new BizException("SHARE_PASSWORD_INVALID", "访问密码不正确", HttpStatus.UNAUTHORIZED);
            }
        }
        Resume resume = resumeMapper.selectById(share.getResumeId());
        if (resume == null) {
            throw new BizException("SHARE_REVOKED", "简历已被删除，分享链接已失效", HttpStatus.GONE);
        }
        User owner = userMapper.selectById(share.getUserId());
        List<String> hidden = json.readOrDefault(share.getHiddenFields(), "[]")
                instanceof ArrayNode array
                ? json.mapper().convertValue(array, json.mapper().getTypeFactory().constructCollectionType(List.class, String.class))
                : List.of();
        ObjectNode content = (ObjectNode) json.read(resume.getContent()).deepCopy();
        trimHiddenFields(content, hidden);
        JsonNode style = json.read(resume.getStyle());
        Template template = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, resume.getTemplateCode()).last("limit 1"));
        ObjectNode templateInfo = json.object();
        templateInfo.put("code", resume.getTemplateCode());
        templateInfo.put("name", template == null ? resume.getTemplateCode() : template.getName());
        templateInfo.put("category", template == null ? "" : template.getCategory());
        JsonNode config = template == null ? json.object() : json.read(template.getConfig());

        shareMapper.update(null, new LambdaUpdateWrapper<ResumeShare>()
                .eq(ResumeShare::getId, share.getId())
                .setSql("view_count = view_count + 1"));
        long views = share.getViewCount() == null ? 1 : share.getViewCount() + 1;
        return new PublicShareVo(share.getToken(), resume.getTitle(), resume.getLanguage(), resume.getTemplateCode(),
                content, style, config, templateInfo, Integer.valueOf(1).equals(share.getAllowDownload()),
                share.getExpiresAt(), resume.getUpdatedAt(), owner == null ? "一位求职者" : owner.getNickname(),
                style.path("showBrandFooter").asBoolean(true), avatarService.describe(resume));
    }

    private void trimHiddenFields(ObjectNode content, List<String> hidden) {
        for (JsonNode sectionNode : content.path("sections")) {
            if (!SectionTypes.BASIC.equals(sectionNode.path("type").asText())) continue;
            for (JsonNode itemNode : sectionNode.path("items")) {
                if (itemNode instanceof ObjectNode item) {
                    JsonNode fieldsNode = item.get("fields");
                    if (fieldsNode instanceof ObjectNode fields) {
                        for (String field : hidden) {
                            fields.remove(field);
                        }
                        fields.remove("idNumber");
                    }
                }
            }
        }
    }

    private ShareVo toVo(ResumeShare share, Resume resume) {
        List<String> hidden = List.of();
        try {
            hidden = json.mapper().convertValue(json.readOrDefault(share.getHiddenFields(), "[]"),
                    json.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
        }
        return new ShareVo(share.getId(), share.getToken(), resume.getTitle(), share.getExpiresAt(),
                Integer.valueOf(1).equals(share.getAllowDownload()), hidden,
                share.getPasswordHash() != null, share.getViewCount(), share.getCreatedAt());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
