package cn.minims.who.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 开启本地缓存（Caffeine），具体缓存名、容量与过期时间在 application.yml 的 spring.cache 下配置。
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
