package cn.minims.who.content;

import cn.minims.who.util.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 求职意向的展示方式存放在模块的 meta 里，是前端的三档取值：
 * title_only（只在姓名下方）、full（独立模块）、both（都显示）。
 * 清洗器不能把它当未知字段抹掉，否则用户选了下拉框里的那一档，保存后又悄悄变回默认。
 */
class ContentSanitizerIntentionModeTest {

    private final ContentSanitizer sanitizer = new ContentSanitizer(new Json(new com.fasterxml.jackson.databind.ObjectMapper()));

    private ObjectNode section(String mode) {
        String json = """
                {
                  "id": "sec_job_intention",
                  "type": "job_intention",
                  "title": "求职意向",
                  "meta": { "displayMode": "%s" },
                  "items": [ { "id": "itm_1", "fields": { "jobTitle": "AI 开发工程师" } } ]
                }
                """.formatted(mode);
        return sanitizer.sanitizeSection(new Json(new com.fasterxml.jackson.databind.ObjectMapper()).read(json), "job_intention");
    }

    @Test
    void keepsEveryIntentionDisplayMode() {
        for (String mode : new String[] { "title_only", "full", "both" }) {
            ObjectNode result = section(mode);
            assertThat(result.path("meta").path("displayMode").asText()).isEqualTo(mode);
        }
    }

    @Test
    void keepsIntentionFieldsAlongsideTheMode() {
        ObjectNode result = section("both");

        assertThat(result.path("items").get(0).path("fields").path("jobTitle").asText()).isEqualTo("AI 开发工程师");
    }
}
