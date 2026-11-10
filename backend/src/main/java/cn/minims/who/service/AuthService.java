package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.common.CaptchaService;
import cn.minims.who.common.LoginGuard;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.User;
import cn.minims.who.entity.UserIdentity;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.UserIdentityMapper;
import cn.minims.who.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static cn.minims.who.dto.AuthDtos.ChangePasswordRequest;
import static cn.minims.who.dto.AuthDtos.ChangeEmailCodeRequest;
import static cn.minims.who.dto.ResumeDtos.CreateResumeRequest;
import static cn.minims.who.dto.AuthDtos.EmailCodeRequest;
import static cn.minims.who.dto.AuthDtos.EmailRegisterRequest;
import static cn.minims.who.dto.AuthDtos.LoginByCodeRequest;
import static cn.minims.who.dto.AuthDtos.LoginCodeRequest;
import static cn.minims.who.dto.AuthDtos.LoginRequest;
import static cn.minims.who.dto.AuthDtos.LoginResponse;
import static cn.minims.who.dto.AuthDtos.PasswordAfterCodeRequest;
import static cn.minims.who.dto.AuthDtos.PasswordResetRequest;
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
    private final LoginGuard loginGuard;
    private final CaptchaService captchaService;

    /** 验证码登录成功后留在这枚令牌上的改密票据：只对本次会话有效，用完即删。 */
    private static final String PASSWORD_TICKET = "password-change-after-code";
    private static final long PASSWORD_TICKET_MS = 30 * 60_000L;

    public AuthService(UserMapper userMapper, UserIdentityMapper identityMapper, ResumeMapper resumeMapper,
                       ResumeService resumeService, AuthSettingsService authSettings,
                       EmailCodeService emailCodeService, LoginGuard loginGuard, CaptchaService captchaService) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.resumeMapper = resumeMapper;
        this.resumeService = resumeService;
        this.authSettings = authSettings;
        this.emailCodeService = emailCodeService;
        this.loginGuard = loginGuard;
        this.captchaService = captchaService;
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
            throw BizException.badRequest("该邮箱已注册，请直接登录；忘记密码请点「忘记密码」自助重置");
        }
        emailCodeService.issueRegisterCode(email);
    }

    /**
     * 邮箱注册。刻意不加 {@code @Transactional}：验证码校验一旦卷进这个事务，错一次码的
     * attempts 记数就会随异常回滚，五次上限变成摆设（详见 {@link EmailCodeService#verifyRegisterCode}）。
     * <p>剩下的两次写各自成立：建号是单条 insert，首份简历由 {@code ResumeService.create} 自己的事务保证。
     * 极端情况下会留下「有账号但没简历」的用户，登录返回 resumeId=0，后台列表里手动新建即可，
     * 这个代价比把爆破上限弄没要小。
     */
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
     * 发送找回密码验证码。
     * <p>只认「验证码确认过的邮箱」：注册时没填邮箱、或用 GitHub 邮箱自动带出的地址都可能没被本站
     * 验证过，而一封能收到验证码的邮件就是这次改密码的唯一凭据，所以未验证一律拒绝。
     * <p>不区分「邮箱没注册」和「注册了但没验证」：前者会让脚本拿接口当账号探测器，后者本来就少见，
     * 用同一句文案的代价远小于泄露账号表。
     */
    public void sendResetCode(EmailCodeRequest request) {
        if (!authSettings.passwordResetEnabled()) {
            throw BizException.forbidden("管理员尚未配置邮件服务，找回密码请联系管理员");
        }
        String email = normalizeEmail(request.email());
        if (findVerifiedByEmail(email) == null) {
            throw BizException.badRequest("该邮箱未在本站完成验证，无法自助重置，请联系管理员");
        }
        emailCodeService.issueResetCode(email);
    }

    /**
     * 用验证码重置密码：验证通过即覆盖密码并作废旧凭据下的全部登录态。
     * <p>顺序是先查账号、再比对新旧密码、最后才消费验证码。「和旧密码相同」这类失败不该把码吃掉，
     * 否则用户改个字符就要去邮箱里再取一封。
     * <p>同样不加事务：这里只有一条 UPDATE，事务换不来什么，却会把验证码的错误计数一起回滚掉。
     */
    public void resetPassword(PasswordResetRequest request) {
        if (!authSettings.passwordResetEnabled()) {
            throw BizException.forbidden("管理员尚未配置邮件服务，找回密码请联系管理员");
        }
        String email = normalizeEmail(request.email());
        User user = findVerifiedByEmail(email);
        if (user == null) {
            throw BizException.badRequest("该邮箱未在本站完成验证，无法自助重置，请联系管理员");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw BizException.forbidden("账号已被冻结，请联系管理员");
        }
        if (Integer.valueOf(1).equals(user.getHasPassword())
                && BCrypt.checkpw(request.newPassword(), user.getPasswordHash())) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        emailCodeService.verifyResetCode(email, request.code());
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPasswordHash, BCrypt.hashpw(request.newPassword()))
                .set(User::getHasPassword, 1)
                .set(User::getUpdatedAt, System.currentTimeMillis()));
        // 找回密码本身就说明旧凭据（邮箱或密码）可能已经不在自己手里了：所有端一律踢下线。
        StpUtil.logout(user.getId());
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

    /**
     * 密码登录。每次提交先销一张滑块验证码换来的票据，专门挡「拿一批密码直接撞」的脚本。
     * <p>同一账号 30 分钟内连错 3 次后不再接受密码：第三次输错当场返回 428，
     * 前端切到邮箱验证码登录；密码正确也不再放行，否则前三次失败只是延迟了几秒。
     * <p>账号没有已验证邮箱、或站点没配 SMTP 时没有验证码通道，仍按密码登录（成功即清零），
     * 否则任何人都能靠输错三次把这类账号永久锁死。
     */
    public LoginResponse login(LoginRequest request) {
        if (!captchaService.consumeTicket(request.captchaTicket())) {
            throw BizException.badRequest("安全验证已失效，请重新拖动滑块");
        }
        User user = findLoginUser(request.username());
        if (user != null && Integer.valueOf(1).equals(user.getHasPassword()) && requiresLoginCode(user)) {
            throw loginCodeRequired(user);
        }
        // 第三方账号的 password_hash 只是一枚随机占位值，不进密码比对。
        if (user == null || !Integer.valueOf(1).equals(user.getHasPassword())
                || !BCrypt.checkpw(request.password(), user.getPasswordHash())) {
            if (user != null && Integer.valueOf(1).equals(user.getHasPassword())) {
                loginGuard.recordFailure(user.getId());
                if (requiresLoginCode(user)) {
                    throw loginCodeRequired(user);
                }
            }
            throw new BizException("LOGIN_FAILED", "账号或密码不正确", HttpStatus.UNAUTHORIZED);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw BizException.forbidden("账号已被冻结，请联系管理员");
        }
        loginGuard.recordSuccess(user.getId());
        StpUtil.login(user.getId());
        Resume latest = latestResume(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), StpUtil.getTokenName(), toVo(user),
                latest == null ? 0L : latest.getId(), false);
    }

    /**
     * 发送登录验证码：账号必须已经因连续输错密码进入验证码登录状态。
     * 没到门槛的一律不发，免得这个公开接口变成「给任意账号发信」的入口；
     * 失败原因统一文案，不额外暴露账号是否绑定邮箱。
     */
    public void sendLoginCode(LoginCodeRequest request) {
        User user = findLoginUser(request.username());
        if (user == null || !Integer.valueOf(1).equals(user.getHasPassword())
                || !Integer.valueOf(1).equals(user.getStatus())
                || !requiresLoginCode(user)) {
            throw BizException.badRequest("当前账号暂时不能使用邮箱验证码登录，请稍后用密码重试");
        }
        emailCodeService.issueLoginCode(user.getEmail());
    }

    /**
     * 邮箱验证码登录：密码已经不作数，验证码是唯一凭据。
     * 成功后在这枚令牌上留一张改密票据，允许用户不交原密码直接设置新密码。
     */
    public LoginResponse loginByCode(LoginByCodeRequest request) {
        User user = findLoginUser(request.username());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !Integer.valueOf(1).equals(user.getEmailVerified())
                || user.getEmail() == null || user.getEmail().isBlank()) {
            throw BizException.badRequest("验证码不正确或已过期，请重新获取");
        }
        emailCodeService.verifyLoginCode(user.getEmail(), request.code());
        loginGuard.recordSuccess(user.getId());
        StpUtil.login(user.getId());
        StpUtil.getTokenSession().set(PASSWORD_TICKET, System.currentTimeMillis());
        Resume latest = latestResume(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), StpUtil.getTokenName(), toVo(user),
                latest == null ? 0L : latest.getId(), true);
    }

    /**
     * 验证码登录后的免原密码改密：票据只对本次登录的令牌有效，30 分钟内可用一次。
     * 票据缺失或过期一律拒绝，避免普通登录态也能不交原密码改密。
     */
    public void changePasswordAfterLoginCode(long userId, PasswordAfterCodeRequest request) {
        Object issuedAt = StpUtil.getTokenSession().get(PASSWORD_TICKET);
        if (!(issuedAt instanceof Number ticket) || System.currentTimeMillis() - ticket.longValue() > PASSWORD_TICKET_MS) {
            throw BizException.forbidden("这次登录已超过免原密码修改的时限，请重新用邮箱验证码登录，或用原密码修改");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        if (Integer.valueOf(1).equals(user.getHasPassword())
                && BCrypt.checkpw(request.newPassword(), user.getPasswordHash())) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, BCrypt.hashpw(request.newPassword()))
                .set(User::getHasPassword, 1)
                .set(User::getUpdatedAt, System.currentTimeMillis()));
        StpUtil.getTokenSession().delete(PASSWORD_TICKET);
        // 本次是凭邮箱验证码进来的，不存在「旧密码泄露」的前提，当前会话保留；其他会话不受影响。
        loginGuard.recordSuccess(userId);
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
            String current = user.getEmail() == null ? "" : user.getEmail();
            if (!email.equalsIgnoreCase(current)) {
                if (email.isEmpty()) {
                    // 清空邮箱：没有可验证的地址，验证状态跟着作废。
                    update.set(User::getEmail, null);
                    update.set(User::getEmailVerified, 0);
                } else {
                    // 换绑要证明新地址归本人：验证码只发新地址，验过才算数，验证状态由此回到 1。
                    // 自助找回密码认的就是这个标记，绕过这一步等于把找回入口让给一个收不到信的地址。
                    String code = request.code();
                    if (code == null || code.isBlank()) {
                        throw BizException.badRequest("更换邮箱需要先用新邮箱获取验证码");
                    }
                    User owner = findByEmail(email);
                    if (owner != null && !owner.getId().equals(userId)) {
                        throw BizException.badRequest("该邮箱已被其他账号使用");
                    }
                    emailCodeService.verifyChangeEmailCode(email, code);
                    update.set(User::getEmail, email);
                    update.set(User::getEmailVerified, 1);
                }
            }
        }
        update.set(User::getUpdatedAt, System.currentTimeMillis());
        userMapper.update(null, update);
        return currentUser(userId);
    }

    /**
     * 发送换绑邮箱验证码：发给「新」地址。发码前先把明显的坑挡掉（与当前相同、已被占用），
     * 不让用户等一封注定作废的邮件；占用检查在保存时还会再做一遍，挡并发窗口。
     */
    public void issueChangeEmailCode(long userId, ChangeEmailCodeRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("账号不存在");
        if (!authSettings.changeEmailEnabled()) {
            throw BizException.forbidden("管理员尚未配置邮件服务，更换邮箱暂不可用，请联系管理员");
        }
        String email = normalizeEmail(request.email());
        if (email.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())) {
            throw BizException.badRequest("新邮箱与当前邮箱相同");
        }
        User owner = findByEmail(email);
        if (owner != null && !owner.getId().equals(userId)) {
            throw BizException.badRequest("该邮箱已被其他账号使用");
        }
        emailCodeService.issueChangeEmailCode(email);
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
                user.getDeletionRequestedAt(),
                AccountDeletionService.deletionDeadline(user.getDeletionRequestedAt()),
                user.getCreatedAt());
    }

    private LoginResponse afterSignup(User user) {
        var firstResume = resumeService.create(user.getId(),
                new CreateResumeRequest("我的第一份简历", "zh", "qinghe", false));
        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), StpUtil.getTokenName(), toVo(user), firstResume.id(), false);
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

    /** 登录标识既可以是用户名也可以是邮箱，两种登录页共用同一套判定。 */
    private User findLoginUser(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        return value.contains("@") ? findByEmail(normalizeEmail(value)) : findByUsername(value);
    }

    /** 验证码登录的三个前提：确实连错了 3 次、能收到信、站点也能发信。 */
    private boolean requiresLoginCode(User user) {
        return loginGuard.requiresCode(user.getId())
                && Integer.valueOf(1).equals(user.getEmailVerified())
                && user.getEmail() != null && !user.getEmail().isBlank()
                && authSettings.loginCodeEnabled();
    }

    private BizException loginCodeRequired(User user) {
        return new BizException("LOGIN_CODE_REQUIRED",
                "密码已连续输错 3 次，请改用邮箱验证码登录",
                HttpStatus.PRECONDITION_REQUIRED, Map.of("email", maskEmail(user.getEmail())));
    }

    private Resume latestResume(long userId) {
        return resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt)
                .last("limit 1"));
    }

    /** 只露前两位和域名：足够让用户认出收件地址，又不把完整地址递给可能在旁边看屏幕的人。 */
    private static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        return (local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2)) + "***" + email.substring(at);
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
