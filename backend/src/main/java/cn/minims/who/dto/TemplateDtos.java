package cn.minims.who.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** 模板目录、导入与站内分享的请求 / 响应模型。 */
public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record TemplateVo(String code, String name, String description, String category,
                             List<String> tags, JsonNode config, Integer usageCount,
                             String version, String author, String source, String visibility,
                             Boolean exportable) {
    }

    /** 模板中心的滚动分页：facets 是当前关键词下各分类的计数，供筛选 Tab 显示。 */
    public record TemplatePageVo(List<TemplateVo> items, long total, int page, int size,
                                 boolean hasMore, Map<String, Long> facets,
                                 long officialCount, long communityCount) {
    }

    /** 站内分享：shared 决定是否进模板中心，exportable 决定别人能否把包下载走。官方模板不参与。 */
    public record TemplateShareRequest(@NotNull(message = "请指定是否分享到模板中心") Boolean shared,
                                       Boolean exportable) {
    }
}
