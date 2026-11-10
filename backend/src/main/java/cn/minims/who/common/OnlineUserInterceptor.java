package cn.minims.who.common;

import cn.minims.who.service.OnlineUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 刷新登录用户的最近活跃时间。注册在登录拦截器之后：未登录请求已被 401 挡下，
 * 白名单接口（例如页脚心跳）带令牌时同样会走到这里。
 */
@Component
public class OnlineUserInterceptor implements HandlerInterceptor {

    private final OnlineUserService onlineUsers;

    public OnlineUserInterceptor(OnlineUserService onlineUsers) {
        this.onlineUsers = onlineUsers;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        onlineUsers.touchCurrentUser();
        return true;
    }
}
