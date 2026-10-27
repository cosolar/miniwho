package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.AuthDtos.ChangePasswordRequest;
import cn.minims.who.dto.AuthDtos.EmailCodeRequest;
import cn.minims.who.dto.AuthDtos.EmailRegisterRequest;
import cn.minims.who.dto.AuthDtos.LoginRequest;
import cn.minims.who.dto.AuthDtos.LoginResponse;
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

    public AuthController(AuthService authService, AuthUtil authUtil) {
        this.authService = authService;
        this.authUtil = authUtil;
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

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
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
