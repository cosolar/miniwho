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
    void builtinPackagesDecodeToLegacySevenKeys() {
        List<TemplatePackage> packages = new BuiltinTemplatePackages(codec).load();
        assertThat(packages).hasSizeGreaterThanOrEqualTo(12);
        for (TemplatePackage pkg : packages) {
            ObjectNode config = codec.renderConfig(pkg);
            List<String> keys = new ArrayList<>();
            config.fieldNames().forEachRemaining(keys::add);
            // 键序变了就是字符串变了，会把按样式缓存的服务端 PDF 整批作废。
            assertThat(keys).containsExactly(
                    "layout", "accent", "font", "sectionStyle", "density", "free", "photo");
            if ("official".equals(pkg.namespace())) {
                assertThat(pkg.code()).matches("^[a-z][a-z0-9-]{1,49}$");
            } else {
                // 作者自带命名空间的转正模板：合成编码必须与导入规则一致且合法。
                assertThat(TemplatePackageCodec.composedCode(pkg.namespace(), pkg.code()))
                        .matches("^[a-z][a-z0-9-]{1,49}$");
            }
            assertThat(pkg.canonical()).startsWith("{");
            assertThat(pkg.checksum()).hasSize(64);
        }
    }

    @Test
    void packageCarriesSampleDataAndRejectsInvalidShapes() {
        String withSample = """
                {
                  "api": 1,
                  "id": { "namespace": "demo", "code": "alpha", "version": "1.0.0" },
                  "meta": { "name": "演示", "description": "描述", "category": "通用", "tags": [] },
                  "theme": { "layout": "classic", "accent": "#123456", "font": "sans", "sectionStyle": "icon" },
                  "sample": { "schemaVersion": 1, "language": "zh" }
                }
                """;
        assertThat(codec.decode(withSample).sample()).isEqualTo("{\"schemaVersion\":1,\"language\":\"zh\"}");

        String badSample = withSample.replace("\"sample\": { \"schemaVersion\": 1, \"language\": \"zh\" }",
                "\"sample\": [1, 2]");
        assertThatThrownBy(() -> codec.decode(badSample))
                .isInstanceOf(TemplatePackageException.class)
                .satisfies(error -> assertThat(((TemplatePackageException) error).problems())
                        .anyMatch(problem -> problem.contains("sample")));
    }

    @Test
    void rejectsResumeDataMixedIntoAPackageEnvelope() {
        String resume = """
                {
                  "api": 1,
                  "id": { "namespace": "qliu1", "code": "resume-agent-v5", "version": "1.1.0" },
                  "meta": {
                    "name": "刘琦 AI开发工程师",
                    "description": "简历内容的结构化副本",
                    "category": "resume",
                    "contentOf": "resume_v4_agent.html",
                    "pdf": "resume_v4_agent.pdf"
                  },
                  "theme": { "layout": "photoheader", "accent": "#000000", "font": "sans", "sectionStyle": "icon" },
                  "sectionTitles": { "education": "教育背景" },
                  "basics": { "name": "刘琦", "phone": "15511775170" },
                  "education": [],
                  "skills": [],
                  "internships": [],
                  "projects": []
                }
                """;
        assertThatThrownBy(() -> codec.decode(resume))
                .isInstanceOf(TemplatePackageException.class)
                .satisfies(error -> {
                    List<String> problems = ((TemplatePackageException) error).problems();
                    assertThat(problems).hasSize(1);
                    assertThat(problems.get(0))
                            .contains("简历数据")
                            .contains("basics")
                            .contains("sectionTitles");
                });
    }

    @Test
    void rejectsFieldsOutsideThePackageSchema() {
        String extraFields = """
                {
                  "api": 1,
                  "id": { "namespace": "demo", "code": "alpha", "version": "1.0.0" },
                  "meta": { "name": "演示", "description": "描述", "category": "通用", "note": "简历备注" },
                  "theme": { "layout": "classic", "accent": "#123456", "font": "sans", "sectionStyle": "icon" },
                  "preview": "https://example.com/preview.png"
                }
                """;
        assertThatThrownBy(() -> codec.decode(extraFields))
                .isInstanceOf(TemplatePackageException.class)
                .satisfies(error -> {
                    List<String> problems = ((TemplatePackageException) error).problems();
                    assertThat(problems).anyMatch(problem -> problem.contains("模板包顶层")
                            && problem.contains("preview"));
                    assertThat(problems).anyMatch(problem -> problem.contains("meta 里")
                            && problem.contains("note"));
                });
    }

    @Test
    void invalidPackageReportsAllProblemsAtOnce() {
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
