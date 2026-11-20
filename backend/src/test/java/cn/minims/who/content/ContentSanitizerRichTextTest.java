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
    void keepsListMarkerColor() {
        // 列表标记的自定义色（#rrggbb）存在 data-list-color 上，保存后不能被剥掉，否则用户选的颜色会丢。
        String content = sanitizedContent("<ul data-list=\"disc\" data-list-color=\"#a33a2f\"><li>第一条</li></ul>");

        assertThat(content).contains("data-list-color=\"#a33a2f\"");
        assertThat(content).contains("<li>第一条</li>");
    }

    @Test
    void keepsOrderedListMarkerColor() {
        // 有序列表同样可以单独设色。
        String content = sanitizedContent("<ol data-list=\"paren\" data-list-color=\"#475569\"><li>第一条</li></ol>");

        assertThat(content).contains("data-list-color=\"#475569\"");
    }

    @Test
    void stripsInvalidListMarkerColor() {
        // 标记色只认 #rrggbb：枚举名、任意字符串、非 hex 一律当作没设置，避免正文混入脏值。
        String content = sanitizedContent(
                "<ul data-list=\"disc\" data-list-color=\"brick\"><li>a</li></ul>"
                        + "<ol data-list=\"paren\" data-list-color=\"red; background:url(x)\"><li>b</li></ol>");

        assertThat(content).doesNotContain("data-list-color");
        assertThat(content).contains("<li>a</li>");
        assertThat(content).contains("<li>b</li>");
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
