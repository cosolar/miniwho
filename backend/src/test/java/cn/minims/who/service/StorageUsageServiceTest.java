package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.entity.Resume;
import cn.minims.who.mapper.ResumeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 存储用量的口径：以盘上的文件为准，按「简历目录 → 属主」归堆。
 * 头像与 logo 没有大小列，所以只要有一次是按库里的字段算的，统计就会漏掉它们。
 */
class StorageUsageServiceTest {

    private static final long MB = 1024 * 1024L;

    @TempDir
    Path root;

    private ResumeMapper resumeMapper;
    private AttachmentStore store;

    @BeforeEach
    void setUp() {
        resumeMapper = mock(ResumeMapper.class);
        store = new AttachmentStore(root.toString());
    }

    @Test
    void countsEveryChannelOfTheUsersOwnResumes() throws IOException {
        write("attachments/7/a.pdf", 300);
        write("avatars/7/avatar.jpg", 200);
        write("media/7/logo.png", 100);
        write("attachments/8/b.pdf", 50);
        write("attachments/9/c.pdf", 999);
        when(resumeMapper.selectIdsByUser(3L)).thenReturn(List.of(7L, 8L));
        when(resumeMapper.selectOwners()).thenReturn(List.of(resume(7L, 3L), resume(8L, 3L), resume(9L, 4L)));

        assertThat(service(0).usageBytes(3L)).isEqualTo(650L);
        assertThat(service(0).scanSite().byUser()).containsEntry(3L, 650L).containsEntry(4L, 999L);
    }

    @Test
    void reportsDirectoriesWithoutOwnerAsOrphans() throws IOException {
        write("attachments/11/a.pdf", 400);
        when(resumeMapper.selectOwners()).thenReturn(List.of());
        // 回收站之外的目录名不是数字也要能兜住，不能让整站统计因为一个脏目录抛异常。
        write("media/not-a-number/logo.png", 600);

        StorageUsageService.SiteUsage scan = service(0).scanSite();
        assertThat(scan.orphanBytes()).isEqualTo(1000L);
        assertThat(scan.totalBytes()).isEqualTo(1000L);
        assertThat(scan.byUser()).isEmpty();
    }

    @Test
    void refusesUploadOnceQuotaIsUsedUp() throws IOException {
        write("attachments/7/a.pdf", MB);
        when(resumeMapper.selectIdsByUser(3L)).thenReturn(List.of(7L));

        // 已用 1 MB、上限 2 MB：刚好还能放 1 MB，再多一个字节就要先清理。
        assertThatCode(() -> service(2).assertRoom(3L, MB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service(2).assertRoom(3L, MB + 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限 2.0 MB");
    }

    @Test
    void zeroQuotaMeansUnlimited() throws IOException {
        write("attachments/7/a.pdf", 9 * MB);
        when(resumeMapper.selectIdsByUser(3L)).thenReturn(List.of(7L));

        assertThatCode(() -> service(0).assertRoom(3L, 8 * MB)).doesNotThrowAnyException();
    }

    private StorageUsageService service(long quotaMb) {
        return new StorageUsageService(store, resumeMapper, quotaMb);
    }

    private void write(String key, long bytes) throws IOException {
        Path target = root.resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[(int) bytes]);
        assertThat(Files.size(target)).isEqualTo(bytes);
    }

    private static Resume resume(long id, long userId) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(userId);
        return resume;
    }
}
