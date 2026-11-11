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

    /**
     * 系统规范：每个版式都必须能被 A4 分页算法计算，不允许存在「预览不标页」的例外。
     * 新增版式除了同步本文件、ResumePaper.vue、PaperSection.vue 三处，还要保证正文是普通块流：
     * CSS 多栏（column-count）、break-inside / page-break-inside 都会让实测 top 与打印分页错位。
     */
    public static final Set<String> LAYOUTS = Set.of(
            "minimal", "academic", "sidebar", "band", "center", "timeline",
            "profile", "classic", "rail", "photoheader", "formal", "ribbon", "framed",
            "qieying", "biaoge", "kapi", "editorial", "blueprint", "fold");

    public static final Set<String> SECTION_STYLES = Set.of(
            "line", "caps", "bar", "block", "dot", "rule", "icon", "flag", "number",
            "side", "thead", "chip", "folio", "tick", "plain");

    public static final Set<String> FONTS = Set.of("sans", "serif");

    /**
     * density 是模板自带的整体松紧档位：compact 由渲染器折算进行距与模块间距的默认值里，
     * 用户在预览页自己调 spacingStep 时仍会叠加生效。目前两档：standard（默认）与 compact。
     */
    public static final Set<String> DENSITIES = Set.of("standard", "compact");

    private RendererCapabilities() {
    }
}
