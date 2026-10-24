package cn.minims.who.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 随应用发版的内置模板包。它们是官方区的唯一来源，启动时由 DataInitializer 覆盖式写回库。
 */
@Component
public class BuiltinTemplatePackages {

    private static final Logger log = LoggerFactory.getLogger(BuiltinTemplatePackages.class);
    private static final String LOCATION = "classpath*:template-packages/*.json";

    private final TemplatePackageCodec codec;

    public BuiltinTemplatePackages(TemplatePackageCodec codec) {
        this.codec = codec;
    }

    public List<TemplatePackage> load() {
        List<TemplatePackage> packages = new ArrayList<>();
        for (Resource resource : resources()) {
            String code = resource.getFilename();
            try (InputStream in = resource.getInputStream()) {
                packages.add(codec.decode(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (TemplatePackageException e) {
                throw new IllegalStateException("内置模板包 " + code + " 不合法：" + e.getMessage(), e);
            } catch (IOException e) {
                throw new IllegalStateException("内置模板包 " + code + " 读取失败", e);
            }
        }
        packages.sort(Comparator.comparingInt(TemplatePackage::sortWeight).thenComparing(TemplatePackage::code));
        log.info("已载入内置模板包 {} 份", packages.size());
        return packages;
    }

    private Resource[] resources() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] found;
        try {
            found = resolver.getResources(LOCATION);
        } catch (IOException e) {
            throw new IllegalStateException("内置模板包扫描失败", e);
        }
        if (found.length == 0) {
            throw new IllegalStateException("没找到任何内置模板包，请确认 template-packages/ 已打进 jar");
        }
        return found;
    }
}
