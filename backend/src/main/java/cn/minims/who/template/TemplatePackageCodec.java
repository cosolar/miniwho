package cn.minims.who.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.util.Json;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 模板包的编解码：读包时一次收集全部问题，让作者一轮就能改完。
 */
@Component
public class TemplatePackageCodec {

    /** 命名空间与 code 都会拼进合成编码和 CSS 作用域类，只收小写字母、数字与连字符。 */
    private static final Pattern NAMESPACE = Pattern.compile("^[a-z][a-z0-9-]{1,59}$");
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]{1,49}$");
    private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final int MAX_TAGS = 8;
    private static final int MAX_SAMPLE_CHARS = 200_000;

    private final Json json;

    public TemplatePackageCodec(Json json) {
        this.json = json;
    }

    public TemplatePackage decode(String text) {
        JsonNode root;
        try {
            root = json.mapper().readTree(text);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new TemplatePackageException(List.of("模板包不是合法的 JSON：" + e.getMessage()));
        }
        if (!root.isObject()) {
            throw new TemplatePackageException(List.of("模板包必须是一个 JSON 对象"));
        }
        List<String> problems = new ArrayList<>();

        int api = integer(root, "/api", 0, problems);
        if (api != RendererCapabilities.API) {
            problems.add("模板包 api=" + api + "，当前渲染器只支持 api=" + RendererCapabilities.API);
        }
        String namespace = text(root, "/id/namespace", problems, true, 60, NAMESPACE);
        String code = text(root, "/id/code", problems, true, 50, CODE);
        String version = text(root, "/id/version", problems, true, 30, VERSION);
        String name = text(root, "/meta/name", problems, true, 100, null);
        String description = text(root, "/meta/description", problems, true, 500, null);
        String category = text(root, "/meta/category", problems, true, 30, null);
        String author = text(root, "/meta/author", problems, false, 100, null);
        String license = text(root, "/meta/license", problems, false, 60, null);
        List<String> tags = tags(root, problems);

        String layout = text(root, "/theme/layout", problems, true, 40, null);
        String accent = text(root, "/theme/accent", problems, true, 20, COLOR);
        String font = text(root, "/theme/font", problems, true, 20, null);
        String sectionStyle = text(root, "/theme/sectionStyle", problems, true, 30, null);
        String density = text(root, "/theme/density", problems, false, 20, null);
        if (density == null) {
            density = "standard";
        }
        boolean photo = bool(root, "/capability/photo", false);
        boolean free = bool(root, "/capability/free", true);
        int sortWeight = integer(root, "/sortWeight", 0, problems);
        if (sortWeight < 0 || sortWeight > 9999) {
            problems.add("sortWeight 只能在 0 到 9999 之间");
        }
        // 原文先照单收下，是否可用由净化器判定；这里只挡体量，避免把超大文本塞进解析器。
        String style = text(root, "/style", problems, false, TemplateCssSanitizer.MAX_CSS_CHARS, null);
        String sample = sample(root, problems);

        oneOf(layout, RendererCapabilities.LAYOUTS, "theme.layout", problems);
        oneOf(font, RendererCapabilities.FONTS, "theme.font", problems);
        oneOf(sectionStyle, RendererCapabilities.SECTION_STYLES, "theme.sectionStyle", problems);
        oneOf(density, RendererCapabilities.DENSITIES, "theme.density", problems);
        if (!problems.isEmpty()) {
            throw new TemplatePackageException(problems);
        }

        String canonical = json.write(root);
        return new TemplatePackage(api, namespace, code, version, name, description, category, tags,
                author, license, layout, accent, font, sectionStyle, density, photo, free, sortWeight, style,
                sample, canonical, sha256(canonical));
    }

    /**
     * 可选的模板预制数据：一份完整的简历 content。新建「示例数据」简历时优先于全局示例，
     * 让财务、法务等垂直模板可以带自己的演示文案。只挡体量与形状，字段级校验交给渲染端的兜底。
     */
    private String sample(JsonNode root, List<String> problems) {
        JsonNode node = root.at("/sample");
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            problems.add("sample 必须是一个 JSON 对象（完整的简历 content）");
            return null;
        }
        String canonical = json.write(node);
        if (canonical.length() > MAX_SAMPLE_CHARS) {
            problems.add("sample 超过 " + MAX_SAMPLE_CHARS + " 字符上限");
            return null;
        }
        return canonical;
    }

    /**
     * 展开成前端渲染用的 config。前七个键的顺序必须与历史数据一致，否则每次启动都会写成新字符串、
     * 把按样式缓存的服务端 PDF 全部作废。
     * scopeClass 与 styleCss 只在带 style 的包上出现（内置或导入），且只能放净化后的 CSS——渲染端永远只读 config，不读 pkg。
     */
    public ObjectNode renderConfig(TemplatePackage pkg) {
        return renderConfig(pkg, null, null);
    }

    public ObjectNode renderConfig(TemplatePackage pkg, String scopeClass, String styleCss) {
        ObjectNode config = json.object();
        config.put("layout", pkg.layout());
        config.put("accent", pkg.accent());
        config.put("font", pkg.font());
        config.put("sectionStyle", pkg.sectionStyle());
        config.put("density", pkg.density());
        config.put("free", pkg.free());
        config.put("photo", pkg.photo());
        if (styleCss != null && !styleCss.isBlank()) {
            config.put("scopeClass", scopeClass);
            config.put("styleCss", styleCss);
        }
        return config;
    }

    /** 带 style 的包（内置或导入）共用的 CSS 作用域类。 */
    public static String scopeClassOf(String code) {
        return "tw-" + code;
    }

    /** 导入包的编码 = 命名空间 + "--" + 包内 code：两个人都叫 jinshi 也能共存，URL 里又不会出现斜杠。 */
    public static String composedCode(String namespace, String code) {
        String composed = namespace + "--" + code;
        if (!CODE.matcher(composed).matches()) {
            throw new TemplatePackageException(List.of(
                    "命名空间与编码合成后的「" + composed + "」不合法或超过 50 字符，请把 id.namespace 与 id.code 都改短"));
        }
        return composed;
    }

    public String pretty(String canonical) {
        try {
            return pretty(json.mapper().readTree(canonical));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("模板包无法还原为可交换格式", e);
        }
    }

    /** 交换给人看的排版；checksum 用的是紧凑形式，两者重新解析后是同一棵树。 */
    public String pretty(JsonNode node) {
        try {
            return json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("模板包无法还原为可交换格式", e);
        }
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }

    private String text(JsonNode root, String pointer, List<String> problems,
                        boolean required, int maxLen, Pattern pattern) {
        String field = pointer.substring(1).replace('/', '.');
        JsonNode node = root.at(pointer);
        if (node.isMissingNode() || node.isNull()) {
            if (required) {
                problems.add("缺少字段 " + field);
            }
            return null;
        }
        if (!node.isTextual()) {
            problems.add(field + " 必须是字符串");
            return null;
        }
        String value = node.asText().trim();
        if (value.isEmpty()) {
            if (required) {
                problems.add(field + " 不能为空");
            }
            return null;
        }
        if (value.length() > maxLen) {
            problems.add(field + " 超过 " + maxLen + " 字上限");
        }
        if (pattern != null && !pattern.matcher(value).matches()) {
            problems.add(field + " 格式不正确（" + pointerHint(pointer) + "）");
        }
        return value;
    }

    private String pointerHint(String pointer) {
        if (pointer.endsWith("code")) return "小写字母开头，可用小写字母、数字与连字符";
        if (pointer.endsWith("namespace")) return "小写字母开头，可用小写字母、数字与连字符";
        if (pointer.endsWith("version")) return "x.y.z 三段数字";
        if (pointer.endsWith("accent")) return "#rrggbb";
        return "格式受限";
    }

    private int integer(JsonNode root, String pointer, int fallback, List<String> problems) {
        JsonNode node = root.at(pointer);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (!node.isInt() && !node.isLong()) {
            problems.add(pointer + " 必须是整数");
            return fallback;
        }
        return node.asInt();
    }

    private boolean bool(JsonNode root, String pointer, boolean fallback) {
        JsonNode node = root.at(pointer);
        return node.isBoolean() ? node.asBoolean() : fallback;
    }

    private List<String> tags(JsonNode root, List<String> problems) {
        JsonNode node = root.at("/meta/tags");
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            problems.add("meta.tags 必须是字符串数组");
            return List.of();
        }
        if (node.size() > MAX_TAGS) {
            problems.add("meta.tags 最多 " + MAX_TAGS + " 个");
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : node) {
            if (!tag.isTextual() || tag.asText().isBlank()) {
                problems.add("meta.tags 里每一项都必须是非空字符串");
                continue;
            }
            String value = tag.asText().trim();
            if (value.length() > 20) {
                problems.add("标签「" + value + "」超过 20 字");
            }
            if (value.contains(",")) {
                problems.add("标签「" + value + "」不能含逗号");
            }
            tags.add(value);
        }
        return tags;
    }

    private void oneOf(String value, Set<String> allowed, String field, List<String> problems) {
        if (value == null || allowed.contains(value)) {
            return;
        }
        problems.add(field + " 的值「" + value + "」不被当前渲染器支持，可选值：" + String.join("/", new TreeSet<>(allowed)));
    }
}
