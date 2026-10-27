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

    /** 申请邮箱验证码（注册 / 找回密码共用同一个入参）：是否已注册、是否太频繁都由服务层回答统一文案，不额外暴露账号存在性以外的信息。 */
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

    /**
     * 账号视图。
     * <p>注销时间两个字段一起给：宽限期天数只在服务层有一处定义，前端自己算一遍就会有两套口径。
     */
    public record UserVo(Long id, String username, String nickname, String email,
                         String role, Integer status, Boolean hasPassword, Boolean emailVerified,
                         Long deletionRequestedAt, Long deletionScheduledAt,
                         Long createdAt) {
    }

    /** 登录页需要的最小信息：当前这个部署实际开放了哪几种注册 / 登录方式，以及能不能自助找回密码。 */
    public record AuthConfigVo(boolean usernameRegister, boolean emailRegister, boolean githubLogin,
                               boolean passwordReset) {
    }

    public record LoginResponse(String token, String tokenName, UserVo user, Long resumeId) {
    }

    /**
     * 换绑邮箱：码是发给新地址的 change_email 验证码，邮箱真的变了就必须带；
     * 没变（或只改昵称）时前端不会收集它，所以不加 @NotBlank，由服务层判。
     */
    public record UpdateProfileRequest(
            @Size(max = 50) String nickname,
            @Email(message = "邮箱格式不正确") @Size(max = 100) String email,
            @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字") String code) {
    }

    /** 发送换绑邮箱验证码：发给「新」地址，收件人是待验证的归属者。 */
    public record ChangeEmailCodeRequest(
            @NotBlank(message = "请输入新邮箱地址")
            @Email(message = "邮箱格式不正确") @Size(max = 100) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String newPassword) {
    }

    /** 找回密码：邮箱 + 验证码 + 新密码一次提交，没有中间态需要在前端记住。 */
    public record PasswordResetRequest(
            @NotBlank(message = "请输入邮箱")
            @Email(message = "邮箱格式不正确") @Size(max = 100) String email,
            @NotBlank(message = "请输入验证码") @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字") String code,
            @NotBlank(message = "请输入新密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String newPassword) {
    }

    /**
     * 申请注销：设过密码的账号交密码，第三方登录的账号交邮箱验证码，两者取其一。
     * 字段都不加 @NotBlank，因为「该交哪个」取决于账号本身，由服务层判。
     */
    public record DeletionRequest(
            @Size(max = 100, message = "密码过长") String password,
            @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字") String code) {
    }

    /** 注销状态：requestedAt 为空表示没有在注销中，scheduledAt 是实际清库时间。 */
    public record DeletionVo(Long requestedAt, Long scheduledAt, int graceDays) {
    }
}
