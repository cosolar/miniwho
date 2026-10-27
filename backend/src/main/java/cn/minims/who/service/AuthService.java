package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.User;
import cn.minims.who.entity.UserIdentity;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.UserIdentityMapper;
import cn.minims.who.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static cn.minims.who.dto.AuthDtos.ChangePasswordRequest;
import static cn.minims.who.dto.ResumeDtos.CreateResumeRequest;
import static cn.minims.who.dto.AuthDtos.EmailCodeRequest;
import static cn.minims.who.dto.AuthDtos.EmailRegisterRequest;
import static cn.minims.who.dto.AuthDtos.LoginRequest;
import static cn.minims.who.dto.AuthDtos.LoginResponse;
import static cn.minims.who.dto.AuthDtos.RegisterRequest;
import static cn.minims.who.dto.AuthDtos.UpdateProfileRequest;
import static cn.minims.who.dto.AuthDtos.UserVo;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final UserIdentityMapper identityMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeService resumeService;
    private final AuthSettingsService authSettings;
    private final EmailCodeService emailCodeService;

    public AuthService(UserMapper userMapper, UserIdentityMapper identityMapper, ResumeMapper resumeMapper,
                       ResumeService resumeService, AuthSettingsService authSettings,
                       EmailCodeService emailCodeService) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.resumeMapper = resumeMapper;
        this.resumeService = resumeService;
        this.authSettings = authSettings;
        this.emailCodeService = emailCodeService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (!authSettings.usernameRegisterEnabled()) {
            throw BizException.forbidden("管理员未开放用户名注册，请改用邮箱注册或联系管理员");
        }
        String username = request.username().trim();
        User existing = findByUsername(username);
        if (existing != null) {
            throw BizException.badRequest("该用户名已被使用");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(request.password()));
        user.setHasPassword(1);
        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        user.setNickname(nickname.isBlank() ? username : nickname);
        user.setRole("user");
        user.setStatus(1);
        insertUser(user, "该用户名已被使用");

        return afterSignup(user);
    }

    /** 发送注册验证码；是否开放由「开关 + SMTP 通道」共同决定，未开放时给统一文案。 */
    public void sendRegisterCode(EmailCodeRequest request) {
        if (!authSettings.emailRegisterEnabled()) {
            throw BizException.forbidden("管理员未开放邮箱注册");
        }
        String email = normalizeEmail(request.email());
        if (findByEmail(email) != null) {
            throw BizException.badRequest("该邮箱已注册，请直接登录；忘记密码请联系管理员重置");
        }
        emailCodeService.issueRegisterCode(email);
    }

    @Transactional
    public LoginResponse registerWithEmail(EmailRegisterRequest request) {
        if (!authSettings.emailRegisterEnabled()) {
            throw BizException.forbidden("管理员未开放邮箱注册");
        }
        String email = normalizeEmail(request.email());
        emailCodeService.verifyRegisterCode(email, request.code());
        if (findByEmail(email) != null) {
            throw BizException.badRequest("该邮箱已注册，请直接登录");
        }
        User user = new User();
        user.setUsername(allocateUsername(baseNameFrom(email)));
        user.setPasswordHash(BCrypt.hashpw(request.password()));
        user.setHasPassword(1);
        user.setEmail(email);
        // 走过验证码的邮箱才算已验证，这是 GitHub 登录能否关联这个账号的前提。
        user.setEmailVerified(1);
        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        user.setNickname(nickname.isBlank() ? user.getUsername() : nickname);
        user.setRole("user");
        user.setStatus(1);
        insertUser(user, "该邮箱已注册，请直接登录");

        return afterSignup(user);
    }

    /**
     * 第三方首登：按渠道身份找账号，找不到再按「两边都验证过的邮箱」关联，最后才自动建号。
     * <p>返回 token 而不是 LoginResponse：第三方登录是从浏览器跳回来的 GET，
     * 只能把凭据带回前端地址，再由前端调 /auth/me 补全用户信息。
     */
    @Transactional
    public String loginByProvider(String provider, String providerUserId, String providerLogin,
                                  String displayName, String email) {
        UserIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getProvider, provider)
                .eq(UserIdentity::getProviderUserId, providerUserId)
                .last("limit 1"));
        User user = identity == null ? null : userMapper.selectById(identity.getUserId());
        boolean brandNew = false;
        if (user == null && email != null) {
            user = findVerifiedByEmail(email);
            if (user != null && !Integer.valueOf(1).equals(user.getStatus())) {
                throw BizException.forbidden("账号已被冻结，请联系管理员");
            }
        }
        if (user == null) {
            user = provisionFromProvider(provider, providerLogin, displayName, email);
            brandNew = true;
        }
        if (identity == null) {
            bindIdentity(user.getId(), provider, providerUserId, providerLogin, email);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw BizException.forbidden("账号已被冻结，请联系管理员");
        }
        if (brandNew) {
            resumeService.create(user.getId(),
                    new CreateResumeRequest("我的第一份简历", "zh", "qinghe", false));
        }
        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    public LoginResponse login(LoginRequest request) {
        String identifier = request.username().trim();
        User user = identifier.contains("@")
                ? findByEmail(normalizeEmail(identifier))
                : findByUsername(identifier);
        // 第三方账号的 password_hash 只是一枚随机占位值，不进密码比对。
        if (user == null || !Integer.valueOf(1).equals(user.getHasPassword())
                || !BCrypt.checkpw(request.password(), user.getPasswordHash())) {
            throw new BizException("LOGIN_FAILED", "账号或密码不正确", org.springframework.http.HttpStatus.UNAUTHORIZED);
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
            String email = normalizeEmail(request.email());
            if (!email.isEmpty()) {
                User owner = findByEmail(email);
                if (owner != null && !owner.getId().equals(userId)) {
                    throw BizException.badRequest("该邮箱已被其他账号使用");
                }
            }
            // 改地址等于重新绑定：旧地址的验证码证明不了新地址，验证状态必须归零。
            update.set(User::getEmail, email.isEmpty() ? null : email);
            update.set(User::getEmailVerified, 0);
        }
        update.set(User::getUpdatedAt, System.currentTimeMillis());
        userMapper.update(null, update);
        return currentUser(userId);
    }

    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("账号不存在");
        if (!Integer.valueOf(1).equals(user.getHasPassword())) {
            throw BizException.badRequest("该账号通过第三方登录，尚未设置密码");
        }
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
        // 旧密码可能已经泄露：改完立即作废该账号的全部登录态，包括当前这次请求的 token。
        StpUtil.logout(userId);
    }

    public UserVo toVo(User user) {
        return new UserVo(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getRole(), user.getStatus(),
                Integer.valueOf(1).equals(user.getHasPassword()),
                Integer.valueOf(1).equals(user.getEmailVerified()),
                user.getCreatedAt());
    }

    private LoginResponse afterSignup(User user) {
        var firstResume = resumeService.create(user.getId(),
                new CreateResumeRequest("我的第一份简历", "zh", "qinghe", false));
        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), StpUtil.getTokenName(), toVo(user), firstResume.id());
    }

    private User provisionFromProvider(String provider, String providerLogin, String displayName, String email) {
        User user = new User();
        user.setUsername(allocateUsername(provider + "_" + (providerLogin == null ? "" : providerLogin)));
        // password_hash 是非空列：给一枚没人知道的随机哈希，账号只能通过第三方渠道登录。
        user.setPasswordHash(BCrypt.hashpw(UUID.randomUUID().toString()));
        user.setHasPassword(0);
        user.setNickname(displayName == null || displayName.isBlank() ? providerLogin : displayName.trim());
        // 渠道侧已验证过的邮箱可以直接当已验证用；被占用了就不写，避免撞唯一索引。
        if (email != null && findByEmail(email) == null) {
            user.setEmail(email);
            user.setEmailVerified(1);
        }
        user.setRole("user");
        user.setStatus(1);
        insertUser(user, "账号创建失败，请重试");
        return user;
    }

    private void bindIdentity(long userId, String provider, String providerUserId,
                              String providerLogin, String email) {
        UserIdentity identity = new UserIdentity();
        identity.setUserId(userId);
        identity.setProvider(provider);
        identity.setProviderUserId(providerUserId);
        identity.setProviderLogin(providerLogin);
        identity.setEmail(email);
        identityMapper.insert(identity);
    }

    private void insertUser(User user, String conflictMessage) {
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 先查后插之间被并发注册抢注，唯一索引兜底。
            throw BizException.badRequest(conflictMessage);
        }
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("limit 1"));
    }

    private User findByEmail(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email).last("limit 1"));
    }

    /** 只有验证码确认过的邮箱才允许被第三方账号关联：别人乱填的邮箱不能变成接管凭据。 */
    private User findVerifiedByEmail(String email) {
        User user = findByEmail(email);
        return user != null && Integer.valueOf(1).equals(user.getEmailVerified()) ? user : null;
    }

    /** 邮箱一律小写存：MySQL 的排序规则本来就大小写不敏感，SQLite 靠这条规则对齐行为。 */
    private static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String baseNameFrom(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /**
     * 邮箱前缀和渠道用户名只是内部标识（界面展示昵称），要落到 users.username 的非空唯一约束上，
     * 所以先清洗成合法字符，撞了再加随机后缀。
     */
    private String allocateUsername(String base) {
        String normalized = (base == null ? "" : base.toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9_]", "_");
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20);
        }
        while (normalized.length() < 3) {
            normalized = normalized + "_";
        }
        String candidate = normalized;
        for (int i = 0; i < 12 && findByUsername(candidate) != null; i++) {
            String suffix = String.valueOf(ThreadLocalRandom.current().nextInt(100, 1000));
            candidate = normalized.substring(0, Math.min(normalized.length(), 17)) + suffix;
        }
        if (findByUsername(candidate) != null) {
            throw BizException.badRequest("账号标识已被占用，请稍后重试");
        }
        return candidate;
    }
}
