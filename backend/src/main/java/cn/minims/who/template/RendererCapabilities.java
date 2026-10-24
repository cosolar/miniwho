package cn.minims.who.template;

import java.util.Set;

/**
 * 渲染器当前能表达的能力清单，是模板包取值的唯一权威。
 * 新增版式时必须同步三处：ResumePaper.vue 的 .layout-* 块、PaperSection.vue 的 .paper-style-* 块，以及这里。
 * 这里漏同步的后果是导入被误拒，前端多写一个类不会报错但包会被判为不支持。
 */
public final class RendererCapabilities {

    /** 模板包格式版本；渲染能力变化时递增，导入低版本包时走迁移。 */
    public static final int API = 1;

    public static final Set<String> LAYOUTS = Set.of(
            "minimal", "academic", "sidebar", "band", "center", "timeline",
            "profile", "classic", "rail", "photoheader", "formal", "ribbon");

    public static final Set<String> SECTION_STYLES = Set.of(
            "line", "caps", "bar", "block", "dot", "rule", "icon", "flag");

    public static final Set<String> FONTS = Set.of("sans", "serif");

    /**
     * config.density 目前只是占位：正文字号与行距由每份简历自己的 styleData（fontSizeStep / spacingStep）决定，
     * 渲染器没有 .density-* 类。放行别的值只会让用户以为模板生效了，所以只接受 standard。
     */
    public static final Set<String> DENSITIES = Set.of("standard");

    private RendererCapabilities() {
    }
}
