package cn.minims.who.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 登录、注册与个人中心的请求 / 响应模型。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入账号") @Size(max = 100) String username,
            @NotBlank(message = "请输入密码") @Size(max = 100) String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名需为 3-20 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String password,
            @Size(max = 50) String nickname) {
    }

    /** 发送注册验证码：只收邮箱，是否已注册、是否太频繁都由服务层回答统一文案，不额外暴露账号存在性以外的信息。 */
    public record EmailCodeRequest(
            @NotBlank(message = "请输入邮箱")
            @Email(message = "邮箱格式不正确") @Size(max = 100) String email) {
    }

    public record EmailRegisterRequest(
            @NotBlank(message = "请输入邮箱")
            @Email(message = "邮箱格式不正确") @Size(max = 100) String email,
            @NotBlank(message = "请输入密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String password,
            @NotBlank(message = "请输入验证码") @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字") String code,
            @Size(max = 50) String nickname) {
    }

    public record UserVo(Long id, String username, String nickname, String email,
                         String role, Integer status, Boolean hasPassword, Boolean emailVerified,
                         Long createdAt) {
    }

    /** 登录页需要的最小信息：当前这个部署实际开放了哪几种注册 / 登录方式。 */
    public record AuthConfigVo(boolean usernameRegister, boolean emailRegister, boolean githubLogin) {
    }

    public record LoginResponse(String token, String tokenName, UserVo user, Long resumeId) {
    }

    public record UpdateProfileRequest(
            @Size(max = 50) String nickname,
            @Email(message = "邮箱格式不正确") @Size(max = 100) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String newPassword) {
    }
}
