package cn.minims.who;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分层约束守门：控制器只碰 service / dto / common，实体与 Mapper 留在服务层；
 * 服务层不得反向依赖 Web 层。接口签名类型化之后，这两条是保证依赖方向不腐化的底线。
 */
class ArchitectureTest {

    private static final Path SOURCES = Path.of("src/main/java/cn/minims/who");

    @Test
    void 控制器不得直接依赖实体层与持久层() throws IOException {
        assertNoImports("controller", List.of("cn.minims.who.entity.", "cn.minims.who.mapper."));
    }

    @Test
    void 服务层不得反向依赖控制器与Web配置() throws IOException {
        assertNoImports("service", List.of("cn.minims.who.controller.", "cn.minims.who.config."));
    }

    private void assertNoImports(String layer, List<String> forbidden) throws IOException {
        List<String> violations = javaFiles(layer)
                .flatMap(file -> importsOf(file).stream()
                        .filter(name -> forbidden.stream().anyMatch(name::startsWith))
                        .map(name -> SOURCES.relativize(file).toString().replace('\\', '/') + " -> " + name))
                .toList();
        assertThat(violations).as("发现越层依赖").isEmpty();
    }

    private Stream<Path> javaFiles(String layer) throws IOException {
        Path dir = SOURCES.resolve(layer);
        assertThat(dir).as("源码目录不存在：%s", dir.toAbsolutePath()).isDirectory();
        return Files.walk(dir).filter(file -> file.toString().endsWith(".java"));
    }

    private List<String> importsOf(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .map(line -> line.substring("import ".length())
                            .replaceFirst("^static ", "")
                            .replace(";", "")
                            .trim())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
