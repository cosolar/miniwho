package cn.minims.who.service;

import cn.minims.who.common.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

@Component
public class AttachmentStore {

    private final Path root;

    public AttachmentStore(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(root.resolve("attachments"));
        Files.createDirectories(root.resolve("avatars"));
        Files.createDirectories(root.resolve("media"));
    }

    public Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw BizException.forbidden("非法的文件路径");
        }
        return path;
    }

    public Path root() {
        return root;
    }

    /** 一个目录里直接文件的总字节数；目录不存在按 0 计，单个文件取不到大小则跳过。 */
    public long sizeOfDir(String dirKey) {
        Path dir = resolve(dirKey);
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        long total = 0L;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                try {
                    total += Files.size(path);
                } catch (IOException ignored) {
                    // 正在被删除的文件不该让整个用量统计失败掉。
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传目录失败", e);
        }
        return total;
    }

    /** 列出一个顶层目录下的子目录名，用于把整站文件按简历归堆。 */
    public List<String> subDirectories(String dirKey) {
        Path dir = resolve(dirKey);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传目录失败", e);
        }
    }

    /**
     * 列出一个目录下的文件 key，只走一层不递归：上传目录的层级是写死的
     * {@code attachments/{resumeId}} 这种形态，递归扫描只会把别人的文件也翻出来。
     */
    public List<String> listKeys(String dirKey) {
        Path dir = resolve(dirKey);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> dirKey + "/" + path.getFileName())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传目录失败", e);
        }
    }

    public void write(String key, InputStream input) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败", e);
        }
    }

    public void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | RuntimeException ignored) {
            // 文件清理失败不阻断用户的删除操作，可由运维任务兜底清理。
        }
    }

    public void deleteDirectoryQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | RuntimeException ignored) {
            // 目录非空时保留，避免误删其他文件。
        }
    }
}
