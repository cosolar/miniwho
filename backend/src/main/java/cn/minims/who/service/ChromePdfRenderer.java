package cn.minims.who.service;

import cn.minims.who.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 用系统 Chrome / Edge 的无头模式把渲染页打印成 PDF，避免引入 PDF 库与字体依赖。
 */
@Component
public class ChromePdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(ChromePdfRenderer.class);
    private static final List<String> CANDIDATES = List.of(
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
            "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
            "/usr/bin/microsoft-edge"
    );

    private final boolean enabled;
    private final String configuredPath;
    private final String executable;
    private final int timeoutSeconds;

    public ChromePdfRenderer(@Value("${app.pdf.enabled:true}") boolean enabled,
                             @Value("${app.pdf.browser-path:}") String configuredPath,
                             @Value("${app.pdf.timeout-seconds:45}") int timeoutSeconds) {
        this.enabled = enabled;
        this.configuredPath = configuredPath == null ? "" : configuredPath.trim();
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.executable = resolveExecutable();
    }

    public boolean available() {
        return enabled && executable != null;
    }

    public String engine() {
        if (!enabled) {
            return "disabled";
        }
        if (executable == null) {
            return "not-found";
        }
        String name = Path.of(executable).getFileName().toString().toLowerCase(Locale.ROOT);
        return name.contains("edge") ? "edge-headless" : "chrome-headless";
    }

    public byte[] render(String url) {
        if (!available()) {
            throw BizException.badRequest("当前环境没有可用的 PDF 渲染引擎，请使用浏览器打印导出");
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("miniwho-pdf-");
            tempDir.resolve("profile").toFile().mkdirs();
            Path output = tempDir.resolve("resume.pdf");
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add("--headless=new");
            command.add("--disable-gpu");
            command.add("--no-sandbox");
            command.add("--no-first-run");
            command.add("--hide-scrollbars");
            command.add("--mute-audio");
            command.add("--user-data-dir=" + tempDir.resolve("profile"));
            command.add("--virtual-time-budget=12000");
            command.add("--run-all-compositor-stages-before-draw");
            command.add("--no-pdf-header-footer");
            command.add("--print-to-pdf-no-header");
            command.add("--print-to-pdf=" + output);
            command.add(url);

            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw BizException.tooMany("PDF 生成超时，请稍后重试或改用浏览器打印");
            }
            int exit = process.exitValue();
            if (!Files.exists(output) || Files.size(output) == 0) {
                log.warn("无头浏览器未产出 PDF，exit={} url={}", exit, url);
                throw BizException.badRequest("PDF 生成失败，请检查渲染地址是否可被后端访问");
            }
            return Files.readAllBytes(output);
        } catch (IOException e) {
            throw new IllegalStateException("PDF 渲染失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.tooMany("PDF 生成被中断，请重试");
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private String resolveExecutable() {
        if (!enabled) {
            return null;
        }
        if (!configuredPath.isBlank()) {
            Path path = Path.of(configuredPath);
            return Files.isExecutable(path) ? path.toString() : null;
        }
        for (String candidate : CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return path.toString();
            }
        }
        for (String onPath : List.of("chrome", "chromium", "chromium-browser", "google-chrome", "microsoft-edge")) {
            Path found = searchPath(onPath);
            if (found != null) {
                return found.toString();
            }
        }
        return null;
    }

    private Path searchPath(String executable) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, windows ? executable + ".exe" : executable);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响导出结果。
                }
            });
        } catch (IOException ignored) {
        }
    }
}
