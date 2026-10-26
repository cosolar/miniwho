package cn.minims.who.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** PDF 导出与公开预览所需的响应模型。 */
public final class ExportDtos {

    private ExportDtos() {
    }

    public record PdfStatusVo(boolean available, String engine, String message) {
    }

    public record ExportPayloadVo(String title, String language, String templateCode, String templateName,
                                  JsonNode content, JsonNode style, JsonNode templateConfig, JsonNode avatar,
                                  Long updatedAt) {
    }
}
