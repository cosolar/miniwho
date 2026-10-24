package cn.minims.who.service;

import cn.minims.who.common.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
