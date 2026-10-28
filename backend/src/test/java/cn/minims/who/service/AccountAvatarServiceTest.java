package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.dto.AuthDtos.UserAvatarVo;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 账号头像通道：闸门按上传原图的字节数判，落盘的只有当前这一张。
 *
 * <p>这里刻意让「换一次图」和「换个扩展名」都走真实文件目录：残留的第二张图不会显示，
 * 却会一直计入账号用量，靠单测盯比靠人记靠谱。库里只有一行 users，用同一个实体实例
 * 假装 mapper 与服务的读写，断言就直接看它身上的 avatar_key。
 */
class AccountAvatarServiceTest {

    private static final long USER_ID = 7L;

    @TempDir
    Path root;

    private User row;
    private AttachmentStore store;
    private StorageUsageService storageUsage;
    private AccountAvatarService service;

    /** 服务层用 LambdaUpdateWrapper 写 avatar_key，没有表信息缓存就连 lambda 都取不到。 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        UserMapper userMapper = mock(UserMapper.class);
        row = new User();
        row.setId(USER_ID);
        row.setUsername("tester");
        store = new AttachmentStore(root.toString());
        storageUsage = mock(StorageUsageService.class);
        when(userMapper.selectById(USER_ID)).thenReturn(row);
        when(userMapper.update(any(), any())).thenReturn(1);
        service = new AccountAvatarService(userMapper, store,
                new MediaTokenService("test-secret", 6 * 60 * 60_000L), storageUsage);
    }

    @Test
    void rejectsUploadsOverOneMegabyte() {
        // 字节闸门在解码之前，所以一个略超上限的假 JPEG 头就够触发它。
        byte[] oversized = new byte[(int) AccountAvatarService.MAX_SIZE + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;

        assertThatThrownBy(() -> service.upload(USER_ID, new MockMultipartFile("file", oversized)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("1MB");
        assertThat(row.getAvatarKey()).isNull();
    }

    @Test
    void rejectsSomethingThatIsNotJpegOrPng() {
        MockMultipartFile gif = new MockMultipartFile("file", "avatar.gif", "image/gif", "GIF89a".getBytes());

        assertThatThrownBy(() -> service.upload(USER_ID, gif))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("JPG 或 PNG");
    }

    @Test
    void rejectsAnImageSmallerThanTheAvatarSlotNeeds() {
        BufferedImage tiny = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> service.upload(USER_ID, multipart(tiny)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尺寸太小");
    }

    @Test
    void storesAnOpaqueImageAsJpegUnderTheAccountDirectory() throws IOException {
        UserAvatarVo vo = service.upload(USER_ID, square(false));

        assertThat(vo.present()).isTrue();
        assertThat(vo.url()).startsWith("/api/public/user-avatars/" + USER_ID + "?token=");
        assertThat(row.getAvatarKey()).isEqualTo("user-avatars/" + USER_ID + "/avatar.jpg");
        assertThat(root.resolve("user-avatars/" + USER_ID + "/avatar.jpg")).exists();
    }

    @Test
    void keepsTransparencyAsPngAndDropsThePreviousJpeg() throws IOException {
        service.upload(USER_ID, square(false));
        UserAvatarVo vo = service.upload(USER_ID, square(true));

        assertThat(vo.present()).isTrue();
        assertThat(row.getAvatarKey()).isEqualTo("user-avatars/" + USER_ID + "/avatar.png");
        assertThat(root.resolve("user-avatars/" + USER_ID + "/avatar.jpg")).doesNotExist();
        assertThat(names("user-avatars/" + USER_ID)).hasSize(1);
    }

    @Test
    void removingClearsTheKeyAndTheFile() throws IOException {
        service.upload(USER_ID, square(false));

        UserAvatarVo vo = service.remove(USER_ID);

        assertThat(vo.present()).isFalse();
        assertThat(vo.url()).isNull();
        assertThat(row.getAvatarKey()).isNull();
        assertThat(root.resolve("user-avatars/" + USER_ID + "/avatar.jpg")).doesNotExist();
    }

    @Test
    void describeWithoutAnAvatarStaysQuiet() {
        UserAvatarVo vo = service.describe(USER_ID);

        assertThat(vo.present()).isFalse();
        assertThat(vo.url()).isNull();
    }

    /** 库里挂着 key、盘上文件没了（清理失败或手工挪走）时，不能还给前端一个必 404 的链接。 */
    @Test
    void describeDropsTheUrlWhenTheFileIsGone() throws IOException {
        service.upload(USER_ID, square(false));
        Files.delete(root.resolve("user-avatars/" + USER_ID + "/avatar.jpg"));

        assertThat(service.describe(USER_ID).present()).isFalse();
    }

    @Test
    void refusesWhenTheAccountIsAlreadyOverQuota() {
        doAnswer(invocation -> {
            throw BizException.storageFull("账号已用满");
        }).when(storageUsage).assertRoom(anyLong(), anyLong());

        assertThatThrownBy(() -> service.upload(USER_ID, square(false)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已用满");
        assertThat(store.subDirectories("user-avatars")).isEmpty();
    }

    @Test
    void publicFileNeedsAMatchingSignature() {
        assertThatThrownBy(() -> service.publicFile(USER_ID, "not-a-token"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("签名链接无效");
    }

    private List<String> names(String dirKey) throws IOException {
        try (Stream<Path> files = Files.list(root.resolve(dirKey))) {
            return files.map(path -> path.getFileName().toString()).toList();
        }
    }

    private MockMultipartFile square(boolean alpha) throws IOException {
        BufferedImage image = new BufferedImage(160, 160,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(alpha ? new Color(20, 130, 120, 90) : new Color(20, 130, 120));
        graphics.fillRect(0, 0, 160, 160);
        graphics.dispose();
        return multipart(image);
    }

    private MockMultipartFile multipart(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "avatar.png", "image/png", out.toByteArray());
    }
}
