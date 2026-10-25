package cn.minims.who.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 单镜像部署时由 Spring Boot 自己托管前端：打包阶段把 frontend/dist 放进 classpath:/static，
 * 这里补上原本由 Nginx 负责的两件事——静态资源缓存策略与前端路由回落 index.html。
 * 本地开发仍走 Vite（5173），只有请求真正落到后端时才走这条链路。
 */
@Configuration
public class StaticSiteConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite 产物带内容 hash，可以放心长缓存；/assets/** 比 /** 更具体，会优先命中。
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable());

        // 其余静态文件与前端路由：有这个文件就给文件，没有扩展名的路径一律回 index.html。
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /** 把「找不到页面」变成「交给前端路由」，但不许吞掉 API 和确实缺失的静态资源。 */
    private static class SpaResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (resourcePath.equals("api") || resourcePath.startsWith("api/")) {
                return null;
            }
            Resource direct = location.createRelative(resourcePath);
            if (direct.exists() && direct.isReadable()) {
                return direct;
            }
            // 带扩展名说明在找文件（logo.png、index-abc.js），找不到就该 404，不能回一张网页。
            if (resourcePath.contains(".")) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }
    }

    /** 原来由 Nginx 统一加的三个响应头；单镜像后由应用补上，换部署方式不至于少一层防护。 */
    @Bean
    public FilterRegistrationBean<Filter> siteSecurityHeaders() {
        Filter filter = (request, response, chain) -> {
            HttpServletResponse res = (HttpServletResponse) response;
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            res.setHeader("X-Frame-Options", "SAMEORIGIN");
            chain.doFilter(request, response);
        };
        return new FilterRegistrationBean<>(filter);
    }
}
