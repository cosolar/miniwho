package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.AdminDtos.AdminPasswordResetVo;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceResetPasswordTest {

    private UserMapper userMapper;
    private MailService mailService;
    private AdminService adminService;
    private MockedStatic<StpUtil> stp;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        mailService = mock(MailService.class);
        adminService = new AdminService(userMapper, null, null, null, null, null, null,
                null, null, null, mailService);
        stp = mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stp.close();
    }

    @Test
    void 已验证邮箱会发信并写入新密码() {
        User user = user(42L, "reader@example.com", 1);
        when(userMapper.selectById(42L)).thenReturn(user);
        when(userMapper.update(nullable(User.class), any())).thenReturn(1);

        AdminPasswordResetVo result = adminService.resetUserPassword(1L, 42L);

        assertThat(result.delivery()).isEqualTo("email");
        assertThat(result.password()).isNull();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("reader@example.com"), eq(AdminPasswordNotice.subject()),
                text.capture(), html.capture());
        String password = passwordFrom(text.getValue());
        assertThat(password).hasSize(AdminPasswordNotice.PASSWORD_LENGTH);
        assertThat(html.getValue()).contains(password);
        assertThat(bcryptHashInUpdate()).satisfies(hash -> assertThat(BCrypt.checkpw(password, hash)).isTrue());
        stp.verify(() -> StpUtil.logout(42L));
    }

    @Test
    void 没有已验证邮箱时返回一次性密码且不发信() {
        User user = user(42L, "unverified@example.com", 0);
        when(userMapper.selectById(42L)).thenReturn(user);
        when(userMapper.update(nullable(User.class), any())).thenReturn(1);

        AdminPasswordResetVo result = adminService.resetUserPassword(1L, 42L);

        assertThat(result.delivery()).isEqualTo("manual");
        assertThat(result.password()).hasSize(AdminPasswordNotice.PASSWORD_LENGTH);
        verify(mailService, never()).send(anyString(), anyString(), anyString(), anyString());
        assertThat(bcryptHashInUpdate()).satisfies(hash -> assertThat(BCrypt.checkpw(result.password(), hash)).isTrue());
        stp.verify(() -> StpUtil.logout(42L));
    }

    @Test
    void 发信失败不改动原密码() {
        User user = user(42L, "reader@example.com", 1);
        when(userMapper.selectById(42L)).thenReturn(user);
        doThrow(new BizException("MAIL_SEND_FAILED", "邮件发送失败", HttpStatus.BAD_GATEWAY))
                .when(mailService).send(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> adminService.resetUserPassword(1L, 42L))
                .isInstanceOf(BizException.class);

        verify(userMapper, never()).update(nullable(User.class), any());
        stp.verify(() -> StpUtil.logout(42L), never());
    }

    private User user(long id, String email, int verified) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setEmailVerified(verified);
        return user;
    }

    private String passwordFrom(String textBody) {
        return java.util.regex.Pattern.compile("新密码：([A-Za-z2-9]{8})")
                .matcher(textBody)
                .results()
                .findFirst()
                .map(match -> match.group(1))
                .orElseThrow(() -> new AssertionError("邮件正文里没有 8 位密码：" + textBody));
    }

    @SuppressWarnings("unchecked")
    private String bcryptHashInUpdate() {
        ArgumentCaptor<Wrapper<User>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).update(nullable(User.class), wrapper.capture());
        Map<String, Object> params = ((LambdaUpdateWrapper<User>) wrapper.getValue()).getParamNameValuePairs();
        return params.values().stream()
                .filter(value -> value instanceof String text && text.startsWith("$2"))
                .map(String.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("更新条件里没有 BCrypt 哈希"));
    }
}
