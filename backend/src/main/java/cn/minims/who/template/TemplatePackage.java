package cn.minims.who.template;

import java.util.List;

/**
 * 一份可交换、可导出的模板包。
 * canonical 是规范化后的作者原文，只用于导出；style 在这里仍然可能是脏的。
 * 渲染一律读展开出来的 config，其中 styleCss 是净化后的产物，两条通道不能混用。
 */
public record TemplatePackage(int api, String namespace, String code, String version,
                              String name, String description, String category, List<String> tags,
                              String author, String license,
                              String layout, String accent, String font, String sectionStyle, String density,
                              boolean photo, boolean free, int sortWeight, String style,
                              String canonical, String checksum) {

    public TemplatePackage {
        tags = List.copyOf(tags);
    }
}
