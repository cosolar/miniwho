package cn.minims.who.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.util.Json;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ContentSanitizer {

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Set<String> URL_KEYS = Set.of("link", "website", "github");
    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li");

    private final Json json;

    public ContentSanitizer(Json json) {
        this.json = json;
    }

    public ObjectNode sanitizeSection(JsonNode raw, String expectedType) {
        if (raw == null || !raw.isObject()) {
            throw cn.minims.who.common.BizException.badRequest("模块内容格式不正确");
        }
        ObjectNode section = raw.deepCopy();
        String type = section.path("type").asText("");
        if (!SectionTypes.isValid(type)) {
            throw cn.minims.who.common.BizException.badRequest("不支持的模块类型");
        }
        if (expectedType != null && !expectedType.equals(type)) {
            throw cn.minims.who.common.BizException.badRequest("模块类型不匹配");
        }
        if (section.path("id").asText("").isBlank()) {
            section.put("id", "sec_" + type);
        }
        String title = section.path("title").asText(SectionTypes.titleOf(type));
        int titleLimit = SectionTypes.isCustom(type) ? 20 : 30;
        if (title.length() > titleLimit) {
            title = title.substring(0, titleLimit);
        }
        section.put("title", title);

        JsonNode meta = section.get("meta");
        if (meta == null || !meta.isObject()) {
            section.set("meta", json.object());
        } else {
            section.set("meta", sanitizeValue(meta, 0, "meta"));
        }

        JsonNode itemsNode = section.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            section.set("items", json.array());
        } else {
            if (itemsNode.size() > 20) {
                throw cn.minims.who.common.BizException.badRequest("单个模块的条目不能超过 20 条");
            }
            ArrayNode items = json.array();
            for (JsonNode itemNode : itemsNode) {
                if (!itemNode.isObject()) {
                    continue;
                }
                ObjectNode item = itemNode.deepCopy();
                String itemId = item.path("id").asText("");
                if (itemId.isBlank()) {
                    itemId = "itm_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                }
                item.put("id", itemId.length() > 64 ? itemId.substring(0, 64) : itemId);
                JsonNode fields = item.get("fields");
                item.set("fields", fields != null && fields.isObject()
                        ? sanitizeValue(fields, 0, "fields")
                        : json.object());
                items.add(item);
            }
            section.set("items", items);
        }
        return section;
    }

    public JsonNode sanitizeValue(JsonNode node, int depth, String key) {
        if (depth > 8) {
            throw cn.minims.who.common.BizException.badRequest("内容层级过深");
        }
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (value.length() > 20000) {
                throw cn.minims.who.common.BizException.badRequest("单个字段内容过长");
            }
            value = value.trim();
            if (SectionTypes.RICH_KEYS.contains(key)) {
                value = Jsoup.clean(value, "", SAFELIST);
            }
            if (URL_KEYS.contains(key) && !value.isBlank()) {
                if (!(value.startsWith("https://") || value.startsWith("http://")) || value.length() > 500) {
                    throw cn.minims.who.common.BizException.badRequest("链接必须是 http/https 地址");
                }
            }
            return json.mapper().getNodeFactory().textNode(value);
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.deepCopy();
        }
        if (node.isArray()) {
            if (node.size() > 50) {
                throw cn.minims.who.common.BizException.badRequest("列表内容过多");
            }
            ArrayNode array = json.array();
            for (JsonNode child : node) {
                array.add(sanitizeValue(child, depth + 1, key));
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = json.object();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            int count = 0;
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (count++ > 60) {
                    throw cn.minims.who.common.BizException.badRequest("字段数量过多");
                }
                String childKey = entry.getKey();
                if (childKey == null || childKey.length() > 50) {
                    continue;
                }
                JsonNode child = entry.getValue();
                if (childKey.equals("period") && child != null && child.isObject()) {
                    object.set(childKey, sanitizePeriod(child));
                } else {
                    object.set(childKey, sanitizeValue(child, depth + 1, childKey));
                }
            }
            return object;
        }
        return node.deepCopy();
    }

    private JsonNode sanitizePeriod(JsonNode raw) {
        ObjectNode period = json.object();
        String start = raw.path("start").asText("");
        String end = raw.path("end").asText("");
        boolean current = raw.path("current").asBoolean(false);
        if (!start.isBlank()) {
            if (!MONTH.matcher(start).matches()) {
                throw cn.minims.who.common.BizException.badRequest("开始时间格式应为 YYYY-MM");
            }
            period.put("start", start);
        }
        if (!end.isBlank()) {
            if (!MONTH.matcher(end).matches()) {
                throw cn.minims.who.common.BizException.badRequest("结束时间格式应为 YYYY-MM");
            }
            if (!start.isBlank() && end.compareTo(start) < 0) {
                throw cn.minims.who.common.BizException.badRequest("结束时间不能早于开始时间");
            }
            period.put("end", end);
        }
        period.put("current", current);
        return period;
    }

    public void validateContent(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw cn.minims.who.common.BizException.badRequest("简历内容格式不正确");
        }
        JsonNode sections = content.get("sections");
        if (sections == null || !sections.isArray()) {
            throw cn.minims.who.common.BizException.badRequest("简历缺少模块内容");
        }
        if (sections.size() > 30) {
            throw cn.minims.who.common.BizException.badRequest("模块数量过多");
        }
        Set<String> types = new HashSet<>();
        for (JsonNode section : sections) {
            if (!section.isObject()) {
                throw cn.minims.who.common.BizException.badRequest("模块内容格式不正确");
            }
            String type = section.path("type").asText("");
            if (!SectionTypes.isValid(type)) {
                throw cn.minims.who.common.BizException.badRequest("不支持的模块类型：" + type);
            }
            if (!types.add(type)) {
                throw cn.minims.who.common.BizException.badRequest("模块重复：" + type);
            }
            sanitizeSection(section, null);
        }
    }

    public static String plain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    public static List<String> allowedOptional() {
        return SectionTypes.OPTIONAL_TYPES;
    }
}
