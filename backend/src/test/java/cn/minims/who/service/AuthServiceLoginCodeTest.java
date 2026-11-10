package cn.minims.who.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.minims.who.common.BizException;
import cn.minims.who.common.CaptchaService;
import cn.minims.who.common.LoginGuard;
import cn.minims.who.dto.AuthDtos.LoginByCodeRequest;
import cn.minims.who.dto.AuthDtos.LoginCodeRequest;
import cn.minims.who.dto.AuthDtos.LoginRequest;
import cn.minims.who.dto.AuthDtos.LoginResponse;
import cn.minims.who.dto.AuthDtos.PasswordAfterCodeRequest;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.UserIdentityMapper;
import cn.minims.who.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 连错 3 次后改走邮箱验证码：密码入口关闭、验证码登录直接进入、免原密码改密只认本次登录的票据。
 */
class AuthServiceLoginCodeTest {

    private static final String PASSWORD = "secret";

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthSettingsService authSettings = mock(AuthSettingsService.class);
    private final EmailCodeService emailCodeService = mock(EmailCodeService.class);
    private final CaptchaService captchaService = mock(CaptchaService.class);
    private LoginGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LoginGuard();
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(1, 1));
        when(userMapper.selectById(7L)).thenReturn(user(1, 1));
        when(authSettings.loginCodeEnabled()).thenReturn(true);
        when(captchaService.consumeTicket(any())).thenReturn(true);
    }

    @Test
    void rejectsLoginWhenCaptchaIsWrong() {
        when(captchaService.consumeTicket(any())).thenReturn(false);
        BizException error = catchThrowableOfType(
                () -> service().login(login("bob", PASSWORD)), BizException.class);
        assertThat(error.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(error.getMessage()).contains("安全验证");
        verify(userMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void thirdWrongPasswordSwitchesToCodeLoginImmediately() {
        AuthService service = service();
        for (int i = 1; i < LoginGuard.MAX_FAILURES; i++) {
            BizException failure = catchThrowableOfType(
                    () -> service.login(login("bob", "wrong")), BizException.class);
            assertThat(failure.getCode()).isEqualTo("LOGIN_FAILED");
        }

        BizException stepUp = catchThrowableOfType(
                () -> service.login(login("bob", "wrong")), BizException.class);
        assertThat(stepUp.getCode()).isEqualTo("LOGIN_CODE_REQUIRED");
        assertThat(stepUp.getStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(stepUp.getDetails()).containsEntry("email", "bo***@example.com");

        // 门槛之后密码正确也不再放行。
        BizException afterThreshold = catchThrowableOfType(
                () -> service.login(login("bob", PASSWORD)), BizException.class);
        assertThat(afterThreshold.getCode()).isEqualTo("LOGIN_CODE_REQUIRED");
    }

    @Test
    void passwordLoginStaysUsableWhenThereIsNoMailChannel() {
        when(authSettings.loginCodeEnabled()).thenReturn(false);
        AuthService service = service();
        for (int i = 0; i < LoginGuard.MAX_FAILURES; i++) {
            BizException failure = catchThrowableOfType(
                    () -> service.login(login("bob", "wrong")), BizException.class);
            assertThat(failure.getCode()).isEqualTo("LOGIN_FAILED");
        }
    }

    @Test
    void sendCodeIsRefusedBeforeTheThreshold() {
        BizException error = catchThrowableOfType(
                () -> service().sendLoginCode(new LoginCodeRequest("bob")), BizException.class);
        assertThat(error.getCode()).isEqualTo("BAD_REQUEST");
        verify(emailCodeService, never()).issueLoginCode(any());
    }

    @Test
    void sendCodeGoesToTheVerifiedEmailOnceTriggered() {
        AuthService service = service();
        failThresholdTimes(service);

        service.sendLoginCode(new LoginCodeRequest("bob"));
        verify(emailCodeService).issueLoginCode("bob@example.com");
    }

    @Test
    void sendCodeRefusesAccountsWithoutAVerifiedEmail() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(0, 1));
        AuthService service = service();
        failThresholdTimes(service);

        BizException error = catchThrowableOfType(
                () -> service.sendLoginCode(new LoginCodeRequest("bob")), BizException.class);
        assertThat(error.getCode()).isEqualTo("BAD_REQUEST");
        verify(emailCodeService, never()).issueLoginCode(any());
    }

    @Test
    void codeLoginIssuesThePasswordChangeTicket() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getTokenSession).thenReturn(session);
            stp.when(StpUtil::getTokenValue).thenReturn("token-value");
            stp.when(StpUtil::getTokenName).thenReturn("satoken");

            LoginResponse response = service().loginByCode(new LoginByCodeRequest("bob", "123456"));

            assertThat(response.passwordChangeSuggested()).isTrue();
            assertThat(response.token()).isEqualTo("token-value");
            verify(emailCodeService).verifyLoginCode("bob@example.com", "123456");
            verify(session).set(anyString(), any());
            stp.verify(() -> StpUtil.login(7L));
        }
    }

    @Test
    void passwordChangeWithoutOldPasswordNeedsTheTicket() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getTokenSession).thenReturn(session);
            when(session.get(anyString())).thenReturn(null);

            BizException error = catchThrowableOfType(
                    () -> service().changePasswordAfterLoginCode(7L,
                            new PasswordAfterCodeRequest("new-secret")), BizException.class);
            assertThat(error.getCode()).isEqualTo("FORBIDDEN");

            when(session.get(anyString())).thenReturn(System.currentTimeMillis() - 31 * 60_000L);
            BizException expired = catchThrowableOfType(
                    () -> service().changePasswordAfterLoginCode(7L,
                            new PasswordAfterCodeRequest("new-secret")), BizException.class);
            assertThat(expired.getCode()).isEqualTo("FORBIDDEN");
        }
    }

    @Test
    void passwordChangeWithTicketUpdatesTheHashAndConsumesIt() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getTokenSession).thenReturn(session);
            when(session.get(anyString())).thenReturn(System.currentTimeMillis());

            service().changePasswordAfterLoginCode(7L, new PasswordAfterCodeRequest("new-secret"));

            verify(userMapper).update(any(), any());
            verify(session).delete(anyString());
        }
    }

    private void failThresholdTimes(AuthService service) {
        for (int i = 0; i < LoginGuard.MAX_FAILURES; i++) {
            catchThrowableOfType(() -> service.login(login("bob", "wrong")), BizException.class);
        }
        assertThat(guard.requiresCode(7L)).isTrue();
        verify(captchaService, atLeast(LoginGuard.MAX_FAILURES)).consumeTicket(any());
    }

    private static LoginRequest login(String username, String password) {
        return new LoginRequest(username, password, "captcha-ticket");
    }

    private AuthService service() {
        return new AuthService(userMapper, mock(UserIdentityMapper.class), mock(ResumeMapper.class),
                mock(ResumeService.class), authSettings, emailCodeService, guard, captchaService);
    }

    private static User user(int emailVerified, int hasPassword) {
        User user = new User();
        user.setId(7L);
        user.setUsername("bob");
        user.setPasswordHash(BCrypt.hashpw(PASSWORD));
        user.setHasPassword(hasPassword);
        user.setEmail("bob@example.com");
        user.setEmailVerified(emailVerified);
        user.setStatus(1);
        return user;
    }
}
