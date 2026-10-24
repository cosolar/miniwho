package cn.minims.who.common;

import cn.dev33.satoken.stp.StpUtil;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    private final UserMapper userMapper;

    public AuthUtil(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public long userId() {
        return StpUtil.getLoginIdAsLong();
    }

    public User user() {
        User user = userMapper.selectById(userId());
        if (user == null) {
            throw BizException.forbidden("账号不存在或已被冻结");
        }
        return user;
    }

    public void requireAdmin() {
        StpUtil.checkRole("admin");
    }

    /** 公开接口上需要按角色放行时用这个：未登录一律 false，不抛异常。 */
    public boolean isAdmin() {
        return StpUtil.isLogin() && StpUtil.hasRole("admin");
    }
}
