package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.Dtos.ExportPayloadVo;
import cn.minims.who.dto.Dtos.PdfStatusVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.Template;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.util.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 PDF 导出：签发一次性渲染凭证 -> 无头浏览器打印前端渲染页 -> 按内容版本缓存结果。
 */
@Service
public class PdfExportService {

    private static final long TICKET_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> OVERRIDE_KEYS = List.of("filter", "fontSizeStep", "spacingStep", "showBrandFooter");

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private final ResumeMapper resumeMapper;
    private final TemplateMapper templateMapper;
    private final AvatarService avatarService;
    private final ChromePdfRenderer renderer;
    private final AttachmentStore store;
    private final Json json;
    private final String renderBaseUrl;

    public PdfExportService(ResumeMapper resumeMapper, TemplateMapper templateMapper, AvatarService avatarService,
                            ChromePdfRenderer renderer, AttachmentStore store, Json json,
                            @Value("${app.pdf.render-base-url:}") String renderBaseUrl) {
        this.resumeMapper = resumeMapper;
        this.templateMapper = templateMapper;
        this.avatarService = avatarService;
        this.renderer = renderer;
        this.store = store;
        this.json = json;
        this.renderBaseUrl = renderBaseUrl == null ? "" : renderBaseUrl.trim();
    }

    public PdfStatusVo status() {
        if (renderBaseUrl.isBlank()) {
            return new PdfStatusVo(false, "not-configured",
                    "未配置 MINIWHO_PDF_RENDER_BASE_URL，服务端 PDF 暂不可用");
        }
        if (!renderer.available()) {
            return new PdfStatusVo(false, renderer.engine(), "未找到可用的无头浏览器，请使用浏览器打印导出");
        }
        return new PdfStatusVo(true, renderer.engine(), null);
    }

    public byte[] export(Resume resume, String templateCode, JsonNode styleOverrides) {
        if (renderBaseUrl.isBlank()) {
            throw new BizException("PDF_ENGINE_UNAVAILABLE",
                    "服务端未配置渲染地址，请改用浏览器打印导出", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!renderer.available()) {
            throw new BizException("PDF_ENGINE_UNAVAILABLE",
                    "当前环境没有可用的无头浏览器，请改用浏览器打印导出", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        String resolvedTemplate = templateCode == null || templateCode.isBlank() ? resume.getTemplateCode() : templateCode.trim();
        ObjectNode style = mergeStyle(resume.getStyle(), styleOverrides);
        String cacheKey = cacheKey(resume, resolvedTemplate, style);        byte[] cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        String token = issueTicket(resume.getId(), resolvedTemplate, style);
        String url = renderBaseUrl + "/export/" + token;
        byte[] pdf = renderer.render(url);
        writeCache(cacheKey, pdf);
        sweepCache();
        return pdf;
    }

    public ExportPayloadVo payload(String token) {
        Ticket ticket = tickets.get(token);
        if (ticket == null) {
            throw BizException.notFound("导出链接无效或已过期，请重新发起导出");
        }
        if (ticket.expiresAt() < System.currentTimeMillis()) {
            tickets.remove(token);
            throw BizException.notFound("导出链接无效或已过期，请重新发起导出");
        }
        Resume resume = resumeMapper.selectById(ticket.resumeId());
        if (resume == null) {
            throw BizException.notFound("简历不存在");
        }
        Template template = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, ticket.templateCode()).last("limit 1"));
        ObjectNode templateInfo = json.object();
        templateInfo.put("code", resume.getTemplateCode());
        templateInfo.put("name", template == null ? resume.getTemplateCode() : template.getName());
        templateInfo.put("category", template == null ? "" : template.getCategory());
        JsonNode config = template == null ? json.object() : json.read(template.getConfig());
        return new ExportPayloadVo(resume.getTitle(), resume.getLanguage(), ticket.templateCode(),
                template == null ? resume.getTemplateCode() : template.getName(),
                json.read(resume.getContent()), ticket.style(), config,
                avatarService.describe(resume), resume.getUpdatedAt());
    }

    private String issueTicket(long resumeId, String templateCode, ObjectNode style) {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        tickets.put(token, new Ticket(resumeId, templateCode, style, now + TICKET_TTL_MILLIS));
        return token;
    }

    private ObjectNode mergeStyle(String storedStyle, JsonNode overrides) {
        JsonNode node = json.read(storedStyle);
        if (!(node instanceof ObjectNode style)) {
            return json.object();
        }
        if (overrides != null && overrides.isObject()) {
            for (String key : OVERRIDE_KEYS) {
                if (overrides.has(key) && !overrides.get(key).isNull()) {
                    style.set(key, overrides.get(key));
                }
            }
        }
        return style;
    }

    private String cacheKey(Resume resume, String templateCode, ObjectNode style) {
        ObjectNode avatar = avatarService.describe(resume);
        Template template = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, templateCode).last("limit 1"));
        String raw = String.join("|",
                String.valueOf(resume.getId()),
                String.valueOf(resume.getContentVersion()),
                String.valueOf(resume.getUpdatedAt()),
                avatar == null ? "-" : String.valueOf(avatar.path("updatedAt").asLong(0)),
                templateCode,
                // 模板包可以被重新导入覆盖，编码不变但渲染配置变了；把配置放进键里，
                // 升级模板后不会继续命中上一版式样的缓存 PDF。
                template == null || template.getConfig() == null ? "-" : template.getConfig(),
                style.path("filter").asText("standard"),
                String.valueOf(style.path("fontSizeStep").asInt(0)),
                String.valueOf(style.path("spacingStep").asInt(0)));
        return "exports/" + sha1(raw) + ".pdf";
    }

    private byte[] readCache(String key) {
        Path path = store.resolve(key);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return bytes.length > 0 ? bytes : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeCache(String key, byte[] pdf) {
        try (InputStream input = new ByteArrayInputStream(pdf)) {
            store.write(key, input);
        } catch (IOException | RuntimeException e) {
            // 缓存写入失败不影响本次导出结果。
        }
    }

    private void sweepCache() {
        Path dir = store.resolve("exports");
        try (var paths = Files.list(dir)) {
            long now = System.currentTimeMillis();
            paths.filter(Files::isRegularFile).forEach(p -> {
                try {
                    if (now - Files.getLastModifiedTime(p).toMillis() > CACHE_TTL_MILLIS) {
                        Files.deleteIfExists(p);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private String sha1(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("缓存键生成失败", e);
        }
    }

    private record Ticket(long resumeId, String templateCode, ObjectNode style, long expiresAt) {
    }
}
