package cn.minims.who.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 文档：访问 /swagger-ui.html 查看，原始 JSON 在 /v3/api-docs。
 */
@Configuration
public class OpenApiConfig {

    private final String tokenName;

    public OpenApiConfig(@Value("${sa-token.token-name:satoken}") String tokenName) {
        this.tokenName = tokenName;
    }

    @Bean
    public OpenAPI miniwhoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MiniWho API")
                        .version("1.0.0")
                        .description("""
                                极简简历 MiniWho 的后端接口。
                                统一返回 { code, message, data }；除 /api/auth/**、/api/templates/**、/api/public/** 外都需要登录。
                                """)
                        .license(new License().name("Apache-2.0")))
                .components(new Components().addSecuritySchemes(tokenName, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(tokenName)
                        .description("登录接口返回的 token，放在请求头 " + tokenName + " 中")))
                .tags(List.of(
                        new Tag().name("认证与账号").description("注册、登录、当前用户与资料修改"),
                        new Tag().name("简历").description("简历增删改查、头像与附件"),
                        new Tag().name("模板").description("官方模板、导入模板包与站内分享"),
                        new Tag().name("分享与公开页").description("无鉴权的分享页、示例简历与媒体资源"),
                        new Tag().name("诊断报告").description("基于固定规则的简历诊断"),
                        new Tag().name("反馈").description("用户反馈提交"),
                        new Tag().name("管理后台").description("管理员用户、模板与反馈管理"),
                        new Tag().name("导出").description("PDF 导出与渲染能力状态"),
                        new Tag().name("站点统计").description("页脚展示的访问次数与在线人数"),
                        new Tag().name("系统").description("健康检查")
                ));
    }
}
