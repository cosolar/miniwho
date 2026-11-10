package cn.minims.who;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 命名守门：Java 标识符一律用 ASCII，中文只出现在注释、字符串与文本块里。
 * 把中文写成方法名虽然能编译，但 IDE、构建报告与跨语言工具链都容易出问题。
 */
class NamingConventionTest {

    private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");

    @Test
    void javaIdentifiersUseOnlyAsciiCharacters() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String root : SOURCE_ROOTS) {
            violations.addAll(scan(Path.of(root)));
        }
        assertThat(violations)
                .as("Java 标识符必须是 ASCII，中文只允许出现在注释与字符串字面量中")
                .isEmpty();
    }

    private List<String> scan(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                boolean inBlockComment = false;
                boolean inTextBlock = false;
                int lineNo = 0;
                for (String raw : Files.readAllLines(file)) {
                    lineNo++;
                    String code = raw;
                    if (inBlockComment) {
                        int end = code.indexOf("*/");
                        if (end < 0) continue;
                        code = code.substring(end + 2);
                        inBlockComment = false;
                    }
                    if (inTextBlock) {
                        int end = code.indexOf("\"\"\"");
                        if (end < 0) continue;
                        code = code.substring(end + 3);
                        inTextBlock = false;
                    }
                    code = code.replaceAll("/\\*.*?\\*/", "");
                    int commentStart = code.indexOf("/*");
                    if (commentStart >= 0) {
                        code = code.substring(0, commentStart);
                        inBlockComment = true;
                    }
                    int textStart = code.indexOf("\"\"\"");
                    if (textStart >= 0) {
                        code = code.substring(0, textStart);
                        inTextBlock = true;
                    }
                    code = code.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                            .replaceAll("'(?:\\\\.|[^'\\\\])*'", "''")
                            .replaceAll("//.*$", "");
                    if (code.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff)) {
                        violations.add(file + ":" + lineNo + ": " + raw.trim());
                    }
                }
            }
        }
        return violations;
    }
}
