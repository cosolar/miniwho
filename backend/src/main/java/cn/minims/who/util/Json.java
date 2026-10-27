package cn.minims.who.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.BizException;
import org.springframework.stereotype.Component;

@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public JsonNode read(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("数据库中存储的 JSON 内容损坏", e);
        }
    }

    public JsonNode readOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return read(fallback);
        }
        return read(value);
    }

    public ObjectNode object() {
        return mapper.createObjectNode();
    }

    public ArrayNode array() {
        return mapper.createArrayNode();
    }

    public String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw BizException.badRequest("内容格式不正确");
        }
    }

    /** 给人看的导出件用缩进格式；接口出参仍然走 write()，不要拿这个当默认。 */
    public String writePretty(JsonNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("导出内容序列化失败", e);
        }
    }
}
