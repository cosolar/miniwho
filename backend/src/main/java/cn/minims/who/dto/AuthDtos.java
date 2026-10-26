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
            @NotBlank(message = "请输入用户名") @Size(max = 50) String username,
            @NotBlank(message = "请输入密码") @Size(max = 100) String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名需为 3-20 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String password,
            @Size(max = 50) String nickname) {
    }

    public record UserVo(Long id, String username, String nickname, String email,
                         String role, Integer status, Long createdAt) {
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
