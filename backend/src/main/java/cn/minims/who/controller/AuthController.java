package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.common.CaptchaService;
import cn.minims.who.dto.AuthDtos.CaptchaCheckRequest;
import cn.minims.who.dto.AuthDtos.CaptchaTicketVo;
import cn.minims.who.dto.AuthDtos.CaptchaVo;
import cn.minims.who.dto.AuthDtos.ChangePasswordRequest;
import cn.minims.who.dto.AuthDtos.EmailCodeRequest;
import cn.minims.who.dto.AuthDtos.EmailRegisterRequest;
import cn.minims.who.dto.AuthDtos.LoginByCodeRequest;
import cn.minims.who.dto.AuthDtos.LoginCodeRequest;
import cn.minims.who.dto.AuthDtos.LoginRequest;
import cn.minims.who.dto.AuthDtos.LoginResponse;
import cn.minims.who.dto.AuthDtos.PasswordAfterCodeRequest;
import cn.minims.who.dto.AuthDtos.PasswordResetRequest;
import cn.minims.who.dto.AuthDtos.RegisterRequest;
import cn.minims.who.dto.AuthDtos.UpdateProfileRequest;
import cn.minims.who.dto.AuthDtos.UserVo;
import cn.minims.who.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "认证与账号")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthUtil authUtil;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, AuthUtil authUtil, CaptchaService captchaService) {
        this.authService = authService;
        this.authUtil = authUtil;
        this.captchaService = captchaService;
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /** 发送注册验证码；管理员未开放邮箱注册或通道未就绪时直接拒绝。 */
    @PostMapping("/email-code")
    public ApiResponse<Void> emailCode(@Valid @RequestBody EmailCodeRequest request) {
        authService.sendRegisterCode(request);
        return ApiResponse.ok();
    }

    @PostMapping("/register-email")
    public ApiResponse<LoginResponse> registerEmail(@Valid @RequestBody EmailRegisterRequest request) {
        return ApiResponse.ok(authService.registerWithEmail(request));
    }

    /** 发送找回密码验证码；只给「邮箱已在本站验证过」的账号发信。 */
    @PostMapping("/password-code")
    public ApiResponse<Void> passwordCode(@Valid @RequestBody EmailCodeRequest request) {
        authService.sendResetCode(request);
        return ApiResponse.ok();
    }

    /** 用验证码重置密码：成功后该账号的全部登录态作废，需要重新登录。 */
    @PostMapping("/password-reset")
    public ApiResponse<Void> passwordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    /**
     * 滑块验证码出题：只给底图、拼图和尺寸，缺口位置留在服务端。
     * <p>拼图是「与底图等高的透明长条」，图块画在正确纵坐标上，前端横向平移即可对齐。
     */
    @GetMapping("/captcha")
    public ApiResponse<CaptchaVo> captcha() {
        CaptchaService.Challenge challenge = captchaService.generate();
        return ApiResponse.ok(new CaptchaVo(challenge.id(), challenge.type(),
                challenge.backgroundImage(), challenge.templateImage(),
                challenge.backgroundImageWidth(), challenge.backgroundImageHeight(),
                challenge.templateImageWidth(), challenge.templateImageHeight()));
    }

    /** 校验滑动轨迹：通过后发一张一次性票据，登录接口只认这张票。 */
    @PostMapping("/captcha/check")
    public ApiResponse<CaptchaTicketVo> checkCaptcha(@Valid @RequestBody CaptchaCheckRequest request) {
        return ApiResponse.ok(new CaptchaTicketVo(captchaService.verify(request.id(), request.data())));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 登录验证码：只有账号已因连续输错进入验证码登录状态时才会真的发信。 */
    @PostMapping("/login-code")
    public ApiResponse<Void> loginCode(@Valid @RequestBody LoginCodeRequest request) {
        authService.sendLoginCode(request);
        return ApiResponse.ok();
    }

    /** 邮箱验证码登录：不再校验密码，验证通过即进入平台，并允许直接设置新密码。 */
    @PostMapping("/login-by-code")
    public ApiResponse<LoginResponse> loginByCode(@Valid @RequestBody LoginByCodeRequest request) {
        return ApiResponse.ok(authService.loginByCode(request));
    }

    /** 验证码登录后的免原密码改密：只认本次登录令牌上的票据。 */
    @PutMapping("/password-after-code")
    public ApiResponse<Void> passwordAfterCode(@Valid @RequestBody PasswordAfterCodeRequest request) {
        authService.changePasswordAfterLoginCode(authUtil.userId(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserVo> me() {
        return ApiResponse.ok(authService.currentUser(authUtil.userId()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserVo> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(authUtil.userId(), request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authUtil.userId(), request);
        return ApiResponse.ok();
    }
}
