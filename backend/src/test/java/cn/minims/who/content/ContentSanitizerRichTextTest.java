package cn.minims.who.content;

import cn.minims.who.util.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 富文本里的「（1）列表」靠 ol 上的 data-list="paren" 区分，清洗时不能连同其它属性一起抹掉；
 * 但这份放行只限这一个标记，class / style 之类仍需剥掉。
 */
class ContentSanitizerRichTextTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContentSanitizer sanitizer = new ContentSanitizer(new Json(mapper));

    private String sanitizedContent(String html) {
        String json = """
                {
                  "id": "sec_self_evaluation",
                  "type": "self_evaluation",
                  "items": [ { "id": "itm_1", "fields": { "content": "%s" } } ]
                }
                """.formatted(html.replace("\"", "\\\""));
        ObjectNode result = sanitizer.sanitizeSection(new Json(mapper).read(json), "self_evaluation");
        return result.path("items").get(0).path("fields").path("content").asText();
    }

    @Test
    void keepsParenListMarker() {
        String content = sanitizedContent("<ol data-list=\"paren\"><li>第一条</li><li>第二条</li></ol>");

        assertThat(content).contains("<ol data-list=\"paren\">");
        assertThat(content).contains("<li>第一条</li>");
    }

    @Test
    void keepsUnorderedListMarker() {
        // 无序列表的菱形 / 对勾等样式同样存在 ul 的 data-list 上，保存后不能被剥掉。
        String content = sanitizedContent("<ul data-list=\"diamond\"><li>第一条</li><li>第二条</li></ul>");

        assertThat(content).contains("<ul data-list=\"diamond\">");
        assertThat(content).contains("<li>第一条</li>");
    }

    @Test
    void keepsStrikeThrough() {
        // 浏览器 execCommand('strikeThrough') 生成的是 <strike>，不能只认 <s>。
        String content = sanitizedContent("<p><strike>删除线</strike></p>");

        assertThat(content).contains("<strike>删除线</strike>");
    }

    @Test
    void stillStripsOtherAttributesAndUnsafeTags() {
        String content = sanitizedContent(
                "<ol class=\"paren-list\" style=\"color:red\"><li onclick=\"alert(1)\">内容</li></ol><script>alert(1)</script>");

        assertThat(content).contains("<ol>");
        assertThat(content).doesNotContain("class=");
        assertThat(content).doesNotContain("style=");
        assertThat(content).doesNotContain("onclick");
        assertThat(content).doesNotContain("<script");
    }
}
