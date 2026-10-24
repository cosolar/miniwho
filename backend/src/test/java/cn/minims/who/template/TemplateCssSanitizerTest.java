package cn.minims.who.template;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 净化器是模板包里唯一的外部代码面，这些用例按攻击面写，不按功能写。
 */
class TemplateCssSanitizerTest {

    private static final String SCOPE = "tw-jinshi";

    private static List<String> problems(String css) {
        try {
            TemplateCssSanitizer.sanitize(css, SCOPE);
            return List.of();
        } catch (TemplatePackageException e) {
            return e.problems();
        }
    }

    @Test
    void 合规规则被重新生成并保留作用域内声明() {
        String css = """
                .tw-jinshi .paper-section__title {
                  color: #fff;
                  background: var(--paper-accent);
                  clip-path: polygon(0 0, 100% 0, calc(100% - 1.8mm) 100%, 0 100%);
                }
                .tw-jinshi > .paper-header { margin-bottom: 4mm }
                @media print {
                  .tw-jinshi .paper-contacts { font-size: 9pt }
                }
                """;
        assertThatCode(() -> TemplateCssSanitizer.sanitize(css, SCOPE)).doesNotThrowAnyException();
        String cleaned = TemplateCssSanitizer.sanitize(css, SCOPE);
        assertThat(cleaned)
                .contains(".tw-jinshi .paper-section__title")
                .contains("clip-path: polygon(0 0, 100% 0, calc(100% - 1.8mm) 100%, 0 100%)")
                .contains(".tw-jinshi > .paper-header")
                .contains("@media print");
    }

    @Test
    void 越出作用域的选择器全部被拒() {
        assertThat(problems("body { color: red }")).isNotEmpty();
        assertThat(problems("html .tw-jinshi { color: red }")).isNotEmpty();
        assertThat(problems(":is(.tw-jinshi) { color: red }")).isNotEmpty();
        // .tw-jinshi-x 是另一个类，前缀相同不算命中。
        assertThat(problems(".tw-jinshi-x .paper-header { color: red }")).isNotEmpty();
        assertThat(problems(".tw-jinshi { color: red } .paper-header { color: blue }")).isNotEmpty();
        assertThat(problems("")).isEmpty();
    }

    @Test
    void 注释不能用来夹带被禁内容() {
        // 注释剥离后 url(/*x*/http://evil) 变成 url(http://evil)，必须被发现。
        assertThat(problems(".tw-jinshi { background: url(/*x*/http://evil.example/track.png) }")).isNotEmpty();
        assertThat(problems("/* 未闭合 .tw-jinshi { color: red }")).isNotEmpty();
    }

    @Test
    void 破坏注入标签与脚本语义的写法被拒() {
        assertThat(problems(".tw-jinshi { color: red }</style><script>alert(1)</script>")).isNotEmpty();
        assertThat(problems(".tw-jinshi { color: red !important }")).isNotEmpty();
        assertThat(problems("@import url(data:text/css,x);")).isNotEmpty();
        assertThat(problems(".tw-jinshi { width: expression(alert(1)) }")).isNotEmpty();
        assertThat(problems(".tw-jinshi { behavior: url(#default#userdata) }")).isNotEmpty();
        assertThat(problems(".tw-jinshi .paper-header { color: red")).isNotEmpty();
    }

    @Test
    void 外链资源被拒但data内联与十六进制色值可用() {
        assertThat(problems(".tw-jinshi .logo { background-image: url(https://cdn.example/a.png) }")).isNotEmpty();
        assertThat(problems(".tw-jinshi .logo { background-image: url(//cdn.example/a.png) }")).isNotEmpty();
        assertThat(problems(".tw-jinshi .logo { background-image: url(\"data:image/svg+xml,<svg/>\") }")).isNotEmpty();
        assertThat(problems(".tw-jinshi .paper-header { color: #1f3a5c }")).isEmpty();
    }

    @Test
    void 一层之外的atRule被拒() {
        assertThat(problems("@media print { @media screen { .tw-jinshi .a { color: red } } }")).isNotEmpty();
        assertThat(problems("@keyframes spin { .tw-jinshi .a { color: red } }")).isNotEmpty();
    }
}
