package cn.minims.who.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 只允许管理员访问。标在方法或控制器类上，由 {@link AdminInterceptor} 统一校验，
 * 业务方法里不再逐个手写 requireAdmin。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
