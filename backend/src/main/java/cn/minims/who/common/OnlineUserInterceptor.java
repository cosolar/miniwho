package cn.minims.who.common;

import cn.minims.who.service.OnlineUserService;
import cn.minims.who.service.UserActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 刷新登录用户的最近活跃时间。注册在登录拦截器之后：未登录请求已被 401 挡下，
 * 白名单接口（例如页脚心跳）带令牌时同样会走到这里。
 * <p>内存里的时间用于「是否在线」判定，落库的那份用于后台展示最后活跃时间，两者互不替代。
 */
@Component
public class OnlineUserInterceptor implements HandlerInterceptor {

    private final OnlineUserService onlineUsers;
    private final UserActivityService userActivity;

    public OnlineUserInterceptor(OnlineUserService onlineUsers, UserActivityService userActivity) {
        this.onlineUsers = onlineUsers;
        this.userActivity = userActivity;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        onlineUsers.touchCurrentUser();
        userActivity.touchCurrentUser();
        return true;
    }
}
