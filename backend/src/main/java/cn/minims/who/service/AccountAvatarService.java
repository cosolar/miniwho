package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.AuthDtos.UserAvatarVo;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import cn.minims.who.util.ImageCodec;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 账号头像通道：文件存 uploads/user-avatars/{userId}/avatar.{jpg|png}，users 表只记存储 key。
 *
 * <p>不复用简历照片那条 {@code avatars/{resumeId}} 通道：账号头像不属于任何一份简历，
 * 混进去以后，用量归堆会按简历找属主、注销清库会按简历删目录，账号自己的文件既可能算到
 * 别人头上，也可能在别人删简历时被顺手带走。
 */
@Service
public class AccountAvatarService {

    /** 上限按上传的原图算，重编码后只会更小；1MB 够一张手机拍的头像，也挡得住随手拖进来的成片。 */
    public static final long MAX_SIZE = 1024 * 1024L;
    public static final int MAX_EDGE = 256;
    public static final int MIN_EDGE = 80;
    public static final String MEDIA_SCOPE = "user-avatar";
    public static final String DIR = "user-avatars";

    private final UserMapper userMapper;
    private final AttachmentStore store;
    private final MediaTokenService mediaTokenService;
    private final StorageUsageService storageUsage;

    public AccountAvatarService(UserMapper userMapper, AttachmentStore store,
                               MediaTokenService mediaTokenService, StorageUsageService storageUsage) {
        this.userMapper = userMapper;
        this.store = store;
        this.mediaTokenService = mediaTokenService;
        this.storageUsage = storageUsage;
    }

    public UserAvatarVo upload(long userId, MultipartFile file) {
        User user = requireUser(userId);
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的头像图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("账号头像不能超过 1MB");
        }
        byte[] raw = readAll(file);
        if (!ImageCodec.isJpeg(raw) && !ImageCodec.isPng(raw)) {
            throw BizException.badRequest("头像仅支持 JPG 或 PNG 图片");
        }
        BufferedImage decoded = ImageCodec.decode(raw, MIN_EDGE, "头像尺寸太小，建议不低于 400×400 像素");
        ImageCodec.Encoded encoded = ImageCodec.normalize(decoded, MAX_EDGE);
        storageUsage.assertRoom(userId, encoded.bytes().length);
        String key = path(userId, encoded.alpha() ? "png" : "jpg");
        // 先删旧图再落新图：换一次扩展名（透明与否变了）就会留下第二份，白占盘。
        dropStoredFiles(userId);
        try (InputStream input = new ByteArrayInputStream(encoded.bytes())) {
            store.write(key, input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        long now = System.currentTimeMillis();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatarKey, key)
                .set(User::getUpdatedAt, now));
        user.setAvatarKey(key);
        user.setUpdatedAt(now);
        return toVo(user, (long) encoded.bytes().length);
    }

    public UserAvatarVo remove(long userId) {
        User user = requireUser(userId);
        dropStoredFiles(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatarKey, null)
                .set(User::getUpdatedAt, System.currentTimeMillis()));
        user.setAvatarKey(null);
        return toVo(user, null);
    }

    public UserAvatarVo describe(long userId) {
        return toVo(requireUser(userId), null);
    }

    /** 签名媒体通道：<img> 带不了登录头，靠短时签名 token 放行，不做登录校验。 */
    public ResponseEntity<Resource> publicFile(long userId, String token) {
        mediaTokenService.verify(token, MEDIA_SCOPE, userId);
        User user = requireUser(userId);
        String key = user.getAvatarKey();
        if (key == null || key.isBlank()) {
            throw BizException.notFound("账号还没有头像");
        }
        Path path = store.resolve(key);
        boolean png = key.endsWith(".png");
        try {
            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"avatar." + (png ? "png" : "jpg") + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType(png ? "image/png" : "image/jpeg"))
                    .contentLength(Files.size(path))
                    .body(new FileSystemResource(path));
        } catch (IOException e) {
            throw BizException.notFound("头像文件不存在，请重新上传");
        }
    }

    private UserAvatarVo toVo(User user, Long knownSize) {
        String key = user.getAvatarKey();
        if (key == null || key.isBlank() || !Files.exists(store.resolve(key))) {
            return new UserAvatarVo(false, null, null, user.getUpdatedAt());
        }
        Long size = knownSize;
        if (size == null) {
            try {
                size = Files.size(store.resolve(key));
            } catch (IOException e) {
                size = null;
            }
        }
        String url = "/api/public/user-avatars/" + user.getId()
                + "?token=" + mediaTokenService.issue(MEDIA_SCOPE, user.getId());
        return new UserAvatarVo(true, url, size, user.getUpdatedAt());
    }

    private User requireUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        return user;
    }

    private byte[] readAll(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw BizException.badRequest("头像读取失败，请重新选择");
        }
    }

    private static String path(long userId, String extension) {
        return DIR + "/" + userId + "/avatar." + extension;
    }

    private void dropStoredFiles(long userId) {
        store.deleteQuietly(path(userId, "jpg"));
        store.deleteQuietly(path(userId, "png"));
        store.deleteDirectoryQuietly(DIR + "/" + userId);
    }
}
