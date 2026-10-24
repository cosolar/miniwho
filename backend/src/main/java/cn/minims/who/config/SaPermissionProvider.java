package cn.minims.who.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaPermissionProvider implements StpInterface {

    private final UserMapper userMapper;

    public SaPermissionProvider(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.valueOf(String.valueOf(loginId)));
        if (user == null) {
            return List.of();
        }
        return List.of(user.getRole());
    }
}
