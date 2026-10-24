package cn.minims.who.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.util.Json;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplatePackageCodecTest {

    private final Json json = new Json(new ObjectMapper());
    private final TemplatePackageCodec codec = new TemplatePackageCodec(json);

    @Test
    void 内置包都能解出并且展开成与历史一致的七个键() {
        List<TemplatePackage> packages = new BuiltinTemplatePackages(codec).load();
        assertThat(packages).hasSizeGreaterThanOrEqualTo(12);
        for (TemplatePackage pkg : packages) {
            ObjectNode config = codec.renderConfig(pkg);
            List<String> keys = new ArrayList<>();
            config.fieldNames().forEachRemaining(keys::add);
            // 键序变了就是字符串变了，会把按样式缓存的服务端 PDF 整批作废。
            assertThat(keys).containsExactly(
                    "layout", "accent", "font", "sectionStyle", "density", "free", "photo");
            assertThat(pkg.namespace()).isEqualTo("official");
            assertThat(pkg.canonical()).startsWith("{");
            assertThat(pkg.checksum()).hasSize(64);
        }
    }

    @Test
    void 非法包一次给出全部问题而不是只报第一条() {
        String broken = """
                {
                  "api": 2,
                  "id": { "namespace": "official", "code": "Bad Code", "version": "1.0" },
                  "meta": { "name": "", "category": "商务", "tags": ["含,逗号"] },
                  "theme": { "layout": "nope", "accent": "red", "font": "comic", "sectionStyle": "flag" }
                }
                """;
        assertThatThrownBy(() -> codec.decode(broken))
                .isInstanceOf(TemplatePackageException.class)
                .satisfies(error -> {
                    List<String> problems = ((TemplatePackageException) error).problems();
                    assertThat(problems).hasSizeGreaterThanOrEqualTo(6);
                    assertThat(problems).anyMatch(problem -> problem.contains("api"));
                    assertThat(problems).anyMatch(problem -> problem.contains("theme.layout"));
                    assertThat(problems).anyMatch(problem -> problem.contains("meta.name"));
                    assertThat(problems).anyMatch(problem -> problem.contains("meta.description"));
                });
    }
}
