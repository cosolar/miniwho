package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.ExportDtos.ExportPayloadVo;
import cn.minims.who.dto.ExportDtos.PdfStatusVo;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 服务端 PDF 导出：签发一次性渲染凭证 -> 无头浏览器打印前端渲染页 -> 按内容版本缓存结果。
 */
@Service
public class PdfExportService {

    private static final long TICKET_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L;
    private static final long INFLIGHT_WAIT_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> OVERRIDE_KEYS = List.of("filter", "fontSizeStep", "spacingStep", "showBrandFooter");

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    /** 同一份内容（缓存键相同）只让一个请求去起浏览器，其余请求等它的结果，避免刷新风暴重复渲染。 */
    private final Map<String, CompletableFuture<byte[]>> inflight = new ConcurrentHashMap<>();

    private final ResumeMapper resumeMapper;
    private final ResumeAccessService accessService;
    private final TemplateMapper templateMapper;
    private final TemplateService templateService;
    private final AvatarService avatarService;
    private final ChromePdfRenderer renderer;
    private final PdfToUnicodeNormalizer toUnicodeNormalizer;
    private final AttachmentStore store;
    private final Json json;
    private final String renderBaseUrl;

    public PdfExportService(ResumeMapper resumeMapper, ResumeAccessService accessService, TemplateMapper templateMapper,
                            TemplateService templateService, AvatarService avatarService, ChromePdfRenderer renderer,
                            PdfToUnicodeNormalizer toUnicodeNormalizer,
                            AttachmentStore store, Json json,
                            @Value("${app.pdf.render-base-url:}") String renderBaseUrl) {
        this.resumeMapper = resumeMapper;
        this.accessService = accessService;
        this.templateMapper = templateMapper;
        this.templateService = templateService;
        this.avatarService = avatarService;
        this.renderer = renderer;
        this.toUnicodeNormalizer = toUnicodeNormalizer;
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

    /** 归属校验、模板解析与文件名统一收在服务层，控制器只做 HTTP 头与字节流。 */
    public PdfFile exportFor(long userId, long resumeId, String templateCode, JsonNode styleOverrides) {
        Resume resume = accessService.findOwned(userId, resumeId);
        String resolved = templateCode == null || templateCode.isBlank()
                ? resume.getTemplateCode() : templateCode.trim();
        if (!resolved.equals(resume.getTemplateCode())) {
            // 查询参数里的模板编码同样要过可见性关卡，防止借导出渲染别人的私有模板配置。
            templateService.requireUsable(resolved, userId);
        }
        byte[] pdf = export(resume, resolved, styleOverrides);
        return new PdfFile(fileName(resume.getTitle()), pdf);
    }

    private String fileName(String title) {
        String cleaned = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "").trim();
        if (cleaned.isBlank()) {
            cleaned = "极简简历";
        }
        return (cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned) + ".pdf";
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
        String cacheKey = cacheKey(resume, resolvedTemplate, style);
        byte[] cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> running = inflight.putIfAbsent(cacheKey, mine);
        if (running != null) {
            return awaitInflight(running);
        }
        try {
            // 等锁期间别的请求可能刚写完缓存，再查一次，能省掉一次浏览器启动。
            cached = readCache(cacheKey);
            if (cached != null) {
                mine.complete(cached);
                return cached;
            }
            String token = issueTicket(resume.getId(), resolvedTemplate, style);
            String url = renderBaseUrl + "/export/" + token;
            // 浏览器打印出来的中文 PDF 里，复制文字会拿到康熙部首码点，进缓存前统一修正一次。
            byte[] pdf = toUnicodeNormalizer.normalize(renderer.render(url));
            writeCache(cacheKey, pdf);
            sweepCache();
            mine.complete(pdf);
            return pdf;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(cacheKey, mine);
        }
    }

    private byte[] awaitInflight(CompletableFuture<byte[]> running) {
        try {
            return running.get(INFLIGHT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.tooMany("PDF 生成被中断，请重试");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BizException biz) {
                throw biz;
            }
            throw BizException.tooMany("PDF 生成失败，请稍后重试");
        } catch (TimeoutException e) {
            throw BizException.tooMany("PDF 导出任务繁忙，请稍后重试");
        }
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
                // 整份样式都进键：滤镜、字号、行距、页脚开关，以及以后新增的样式项，
                // 避免切换其中任何一项后仍命中旧 PDF。
                json.write(style));
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

    /** 导出结果：文件名已清洗并带 .pdf 后缀，字节流由控制器直接写进响应。 */
    public record PdfFile(String fileName, byte[] bytes) {
    }
}
