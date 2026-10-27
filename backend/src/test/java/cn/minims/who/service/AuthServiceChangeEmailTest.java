package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.dto.AuthDtos.ChangeEmailCodeRequest;
import cn.minims.who.dto.AuthDtos.UpdateProfileRequest;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.UserIdentityMapper;
import cn.minims.who.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 换绑邮箱守门：新地址必须交验证码，旧地址没动过就不能要求；发码口把明显的坑先挡掉。 */
class AuthServiceChangeEmailTest {

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 的 set() 要靠实体列缓存解析字段名，单测里没有 MyBatis 启动流程，手工挂一次。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthSettingsService authSettings = mock(AuthSettingsService.class);
    private final EmailCodeService emailCodeService = mock(EmailCodeService.class);

    private AuthService service() {
        return new AuthService(userMapper, mock(UserIdentityMapper.class), mock(ResumeMapper.class),
                mock(ResumeService.class), authSettings, emailCodeService);
    }

    private static User user(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setEmail(email);
        user.setEmailVerified(email == null ? 0 : 1);
        return user;
    }

    @Test
    void 邮箱没变时不要求验证码也不碰验证状态() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old@x.com"));
        service().updateProfile(7L, new UpdateProfileRequest("新昵称", "OLD@X.com", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("nickname").doesNotContain("email_verified");
        verify(emailCodeService, never()).verifyChangeEmailCode(any(), any());
    }

    @Test
    void 邮箱变了但没有验证码时拒绝保存() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old@x.com"));

        assertThatThrownBy(() -> service().updateProfile(7L, new UpdateProfileRequest(null, "new@x.com", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("验证码");
        verify(userMapper, never()).update(any(), any());
        verify(emailCodeService, never()).verifyChangeEmailCode(any(), any());
    }

    @Test
    void 换绑成功后新邮箱直接算已验证() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old@x.com"));

        service().updateProfile(7L, new UpdateProfileRequest(null, "  New@X.COM  ", "123456"));

        verify(emailCodeService).verifyChangeEmailCode("new@x.com", "123456");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("email_verified");
    }

    @Test
    void 发码拒绝与当前相同的邮箱() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old@x.com"));
        when(authSettings.changeEmailEnabled()).thenReturn(true);

        assertThatThrownBy(() -> service().issueChangeEmailCode(7L, new ChangeEmailCodeRequest("OLD@x.com")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("相同");
        verify(emailCodeService, never()).issueChangeEmailCode(any());
    }

    @Test
    void 发码拒绝已被占用的邮箱() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old@x.com"));
        when(authSettings.changeEmailEnabled()).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(user(99L, "taken@x.com"));

        assertThatThrownBy(() -> service().issueChangeEmailCode(7L, new ChangeEmailCodeRequest("taken@x.com")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已被其他账号使用");
        verify(emailCodeService, never()).issueChangeEmailCode(any());
    }

    @Test
    void 邮件通道未配置时换绑整体不可用() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old@x.com"));
        when(authSettings.changeEmailEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service().issueChangeEmailCode(7L, new ChangeEmailCodeRequest("new@x.com")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮件服务");
        verify(emailCodeService, never()).issueChangeEmailCode(any());
    }

    @Test
    void 通道就绪且地址可用时把码发给规范化后的新地址() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old@x.com"));
        when(authSettings.changeEmailEnabled()).thenReturn(true);

        service().issueChangeEmailCode(7L, new ChangeEmailCodeRequest("  New@X.COM "));

        verify(emailCodeService).issueChangeEmailCode(eq("new@x.com"));
    }
}
