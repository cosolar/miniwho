package cn.minims.who.service;

import cn.hutool.crypto.digest.BCrypt;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.AuthDtos.DeletionRequest;
import cn.minims.who.dto.AuthDtos.DeletionVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeAttachment;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.EmailCodeMapper;
import cn.minims.who.mapper.FeedbackMapper;
import cn.minims.who.mapper.ResumeAttachmentMapper;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeReportMapper;
import cn.minims.who.mapper.ResumeShareMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserIdentityMapper;
import cn.minims.who.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注销链路守门：宽限期算法、自助注销的准入条件，以及硬删范围到底圈住了哪些文件。 */
class AccountDeletionServiceTest {

    @TempDir
    Path uploadDir;

    private final UserMapper userMapper = mock(UserMapper.class);
    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeAttachmentMapper attachmentMapper = mock(ResumeAttachmentMapper.class);
    private final EmailCodeService emailCodeService = mock(EmailCodeService.class);
    private final MailService mailService = mock(MailService.class);

    private AccountDeletionService service() {
        AttachmentStore store = new AttachmentStore(uploadDir.toString());
        return new AccountDeletionService(userMapper, resumeMapper, attachmentMapper,
                mock(ResumeShareMapper.class), mock(ResumeReportMapper.class), mock(TemplateMapper.class),
                mock(FeedbackMapper.class), mock(UserIdentityMapper.class), mock(EmailCodeMapper.class),
                store, emailCodeService, mailService);
    }

    private static User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setStatus(1);
        user.setHasPassword(1);
        user.setPasswordHash(BCrypt.hashpw("correct-horse"));
        return user;
    }

    private static Resume resume(long id, long userId) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(userId);
        return resume;
    }

    @Test
    void 宽限期从申请那一刻算起且不随当前时间漂移() {
        assertThat(AccountDeletionService.deletionDeadline(null)).isNull();
        long requestedAt = 1_700_000_000_000L;
        assertThat(AccountDeletionService.deletionDeadline(requestedAt))
                .isEqualTo(requestedAt + AccountDeletionService.GRACE_DAYS * 24 * 60 * 60_000L);
    }

    @Test
    void 管理员账号不能自助注销() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "admin"));

        assertThatThrownBy(() -> service().request(7L, new DeletionRequest("correct-horse", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("管理员");
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void 密码不正确时不写下申请时间() {
        when(userMapper.selectById(3L)).thenReturn(user(3L, "user"));

        assertThatThrownBy(() -> service().request(3L, new DeletionRequest("wrong-horse", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码");
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void 重复申请不会把宽限期重新拉长() {
        User user = user(4L, "user");
        user.setDeletionRequestedAt(1_600_000_000_000L);
        when(userMapper.selectById(4L)).thenReturn(user);

        DeletionVo vo = service().request(4L, new DeletionRequest("correct-horse", null));

        assertThat(vo.requestedAt()).isEqualTo(1_600_000_000_000L);
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void 没有密码也没有已验证邮箱的账号只能找管理员() {
        User user = user(5L, "user");
        user.setHasPassword(0);
        when(userMapper.selectById(5L)).thenReturn(user);

        assertThatThrownBy(() -> service().request(5L, new DeletionRequest(null, "123456")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("管理员");
    }

    @Test
    void 清除范围只圈本账号的简历目录与附件行() throws IOException {
        Files.createDirectories(uploadDir.resolve("attachments/11"));
        Files.createDirectories(uploadDir.resolve("attachments/99"));
        Files.createDirectories(uploadDir.resolve("media/12"));
        Files.createDirectories(uploadDir.resolve("avatars/11"));
        Files.writeString(uploadDir.resolve("attachments/11/a.pdf"), "mine");
        Files.writeString(uploadDir.resolve("attachments/99/b.pdf"), "someone else");
        Files.writeString(uploadDir.resolve("media/12/logo.png"), "mine");
        Files.writeString(uploadDir.resolve("avatars/11/avatar.jpg"), "mine");

        when(userMapper.selectById(1L)).thenReturn(user(1L, "user"));
        when(resumeMapper.selectPurgeColumnsByUser(1L)).thenReturn(List.of(resume(11L, 1L), resume(12L, 1L)));
        ResumeAttachment attachment = new ResumeAttachment();
        attachment.setId(1L);
        attachment.setStorageKey("attachments/11/a.pdf");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment));

        AccountDeletionService.PurgePlan plan = service().plan(1L);

        assertThat(plan.fileKeys())
                .contains("attachments/11/a.pdf", "media/12/logo.png", "avatars/11/avatar.jpg")
                .doesNotContain("attachments/99/b.pdf");
        assertThat(plan.dirs()).contains("attachments/11", "avatars/11", "media/12");
    }
}
