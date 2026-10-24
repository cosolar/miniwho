package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.Dtos;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.minims.who.dto.Dtos.ChangePasswordRequest;
import static cn.minims.who.dto.Dtos.CreateResumeRequest;
import static cn.minims.who.dto.Dtos.LoginRequest;
import static cn.minims.who.dto.Dtos.LoginResponse;
import static cn.minims.who.dto.Dtos.RegisterRequest;
import static cn.minims.who.dto.Dtos.UpdateProfileRequest;
import static cn.minims.who.dto.Dtos.UserVo;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeService resumeService;

    public AuthService(UserMapper userMapper, ResumeMapper resumeMapper, ResumeService resumeService) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.resumeService = resumeService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("limit 1"));
        if (existing != null) {
            throw BizException.badRequest("该用户名已被使用");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(request.password()));
        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        user.setNickname(nickname.isBlank() ? username : nickname);
        user.setRole("user");
        user.setStatus(1);
        userMapper.insert(user);

        var firstResume = resumeService.create(user.getId(),
                new CreateResumeRequest("我的第一份简历", "zh", "qinghe", false));

        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), StpUtil.getTokenName(), toVo(user), firstResume.id());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username().trim()).last("limit 1"));
        if (user == null || !BCrypt.checkpw(request.password(), user.getPasswordHash())) {
            throw new BizException("LOGIN_FAILED", "用户名或密码不正确", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw BizException.forbidden("账号已被冻结，请联系管理员");
        }
        StpUtil.login(user.getId());
        Resume latest = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, user.getId())
                .orderByDesc(Resume::getUpdatedAt)
                .last("limit 1"));
        long resumeId = latest == null ? 0L : latest.getId();
        return new LoginResponse(StpUtil.getTokenValue(), StpUtil.getTokenName(), toVo(user), resumeId);
    }

    public void logout() {
        StpUtil.logout();
    }

    public UserVo currentUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        return toVo(user);
    }

    public UserVo updateProfile(long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("账号不存在");
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (request.nickname() != null) {
            String nickname = request.nickname().trim();
            if (nickname.isBlank()) throw BizException.badRequest("昵称不能为空");
            update.set(User::getNickname, nickname);
        }
        if (request.email() != null) {
            update.set(User::getEmail, request.email().trim());
        }
        update.set(User::getUpdatedAt, System.currentTimeMillis());
        userMapper.update(null, update);
        return currentUser(userId);
    }

    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("账号不存在");
        if (!BCrypt.checkpw(request.oldPassword(), user.getPasswordHash())) {
            throw BizException.badRequest("原密码不正确");
        }
        if (BCrypt.checkpw(request.newPassword(), user.getPasswordHash())) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, BCrypt.hashpw(request.newPassword()))
                .set(User::getUpdatedAt, System.currentTimeMillis()));
    }

    public UserVo toVo(User user) {
        return new UserVo(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
