package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.MediaDtos.AvatarShapeRequest;
import cn.minims.who.dto.MediaDtos.AvatarVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.util.ImageCodec;
import cn.minims.who.util.Json;
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
 * 头像走独立通道：文件存 uploads/avatars/{resumeId}/，resumes 表只记 storage key 与形状。
 */
@Service
public class AvatarService {

    public static final long MAX_SIZE = 3 * 1024 * 1024;
    public static final int MAX_EDGE = 600;
    public static final int MIN_EDGE = 80;
    public static final String MEDIA_SCOPE = "avatar";

    private final ResumeMapper resumeMapper;
    private final ResumeAccessService accessService;
    private final AttachmentStore store;
    private final MediaTokenService mediaTokenService;
    private final Json json;

    public AvatarService(ResumeMapper resumeMapper, ResumeAccessService accessService, AttachmentStore store,
                         MediaTokenService mediaTokenService, Json json) {
        this.resumeMapper = resumeMapper;
        this.accessService = accessService;
        this.store = store;
        this.mediaTokenService = mediaTokenService;
        this.json = json;
    }

    public AvatarVo upload(long userId, long resumeId, MultipartFile file) {
        return upload(accessService.findOwned(userId, resumeId), file);
    }

    private AvatarVo upload(Resume resume, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的头像图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("头像图片不能超过 3MB");
        }
        byte[] raw = readAll(file);
        if (!ImageCodec.isJpeg(raw) && !ImageCodec.isPng(raw)) {
            throw BizException.badRequest("头像仅支持 JPG 或 PNG 图片");
        }
        BufferedImage decoded = ImageCodec.decode(raw, MIN_EDGE, "头像尺寸太小，建议不低于 400×500 像素");
        ImageCodec.Encoded encoded = ImageCodec.normalize(decoded, MAX_EDGE);
        String extension = encoded.alpha() ? "png" : "jpg";
        String key = "avatars/" + resume.getId() + "/avatar." + extension;
        dropStoredFiles(resume.getId());
        try (InputStream input = new ByteArrayInputStream(encoded.bytes())) {
            store.write(key, input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getAvatarKey, key)
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        resume.setAvatarKey(key);
        resume.setUpdatedAt(System.currentTimeMillis());
        return toVo(resume, (long) encoded.bytes().length);
    }

    public AvatarVo updateShape(long userId, long resumeId, AvatarShapeRequest request) {
        return updateShape(accessService.findOwned(userId, resumeId), request);
    }

    private AvatarVo updateShape(Resume resume, AvatarShapeRequest request) {
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getAvatarShape, request.shape())
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        resume.setAvatarShape(request.shape());
        return toVo(resume, null);
    }

    /** 删除后返回剩余的头像信息，前端可直接拿它刷新。 */
    public AvatarVo delete(long userId, long resumeId) {
        Resume resume = accessService.findOwned(userId, resumeId);
        delete(resume);
        return toVo(resume, null);
    }

    private void delete(Resume resume) {
        dropStoredFiles(resume.getId());
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getAvatarKey, null)
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        resume.setAvatarKey(null);
    }

    public ResponseEntity<Resource> file(long userId, long resumeId) {
        return file(accessService.findOwned(userId, resumeId));
    }

    /** 签名媒体通道：<img> 带不了登录头，靠短时签名 token 放行，不做登录校验。 */
    public ResponseEntity<Resource> publicFile(long resumeId, String token) {
        mediaTokenService.verify(token, MEDIA_SCOPE, resumeId);
        return file(accessService.requireExisting(resumeId));
    }

    private ResponseEntity<Resource> file(Resume resume) {
        Path path = currentPath(resume);
        if (path == null) {
            throw BizException.notFound("简历还没有头像");
        }
        String name = path.getFileName().toString();
        boolean png = name.endsWith(".png");
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

    public AvatarVo toVo(long userId, long resumeId) {
        return toVo(accessService.findOwned(userId, resumeId), null);
    }

    private AvatarVo toVo(Resume resume, Long knownSize) {
        Path path = currentPath(resume);
        if (path == null) {
            return new AvatarVo(false, shapeOf(resume), null, null, resume.getUpdatedAt());
        }
        Long size = knownSize;
        if (size == null) {
            try {
                size = Files.size(path);
            } catch (IOException e) {
                size = null;
            }
        }
        String url = "/api/public/avatars/" + resume.getId() + "?token=" + mediaTokenService.issue(MEDIA_SCOPE, resume.getId());
        return new AvatarVo(true, shapeOf(resume), url, size, resume.getUpdatedAt());
    }

    private String shapeOf(Resume resume) {
        return "circle".equals(resume.getAvatarShape()) ? "circle" : "rect";
    }

    /** 简历详情与分享页里内嵌的头像描述，无头像时返回 null（Jackson 会省略字段）。 */
    public ObjectNode describe(Resume resume) {
        AvatarVo vo = toVo(resume, null);
        if (!vo.present()) {
            return null;
        }
        ObjectNode node = json.object();
        node.put("shape", vo.shape());
        node.put("url", vo.url());
        node.put("sizeBytes", vo.sizeBytes());
        node.put("updatedAt", vo.updatedAt());
        return node;
    }

    private Path currentPath(Resume resume) {
        String key = resume.getAvatarKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        Path path = store.resolve(key);
        return Files.exists(path) ? path : null;
    }

    /** 复制简历时把头像一并带过去；失败只影响副本，不阻断复制。 */
    public void copy(Resume source, Resume target) {
        Path from = currentPath(source);
        if (from == null) {
            return;
        }
        String key = "avatars/" + target.getId() + "/" + from.getFileName().toString();
        try (InputStream input = Files.newInputStream(from)) {
            store.write(key, input);
        } catch (IOException e) {
            return;
        }
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, target.getId())
                .set(Resume::getAvatarKey, key)
                .set(Resume::getAvatarShape, shapeOf(source)));
        target.setAvatarKey(key);
        target.setAvatarShape(shapeOf(source));
    }

    public void purge(Long resumeId, String key) {
        if (key != null && !key.isBlank()) {
            store.deleteQuietly(key);
        }
        dropStoredFiles(resumeId);
    }

    private void dropStoredFiles(Long resumeId) {
        store.deleteQuietly("avatars/" + resumeId + "/avatar.jpg");
        store.deleteQuietly("avatars/" + resumeId + "/avatar.png");
        store.deleteDirectoryQuietly("avatars/" + resumeId);
    }

    private byte[] readAll(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw BizException.badRequest("头像读取失败，请重新选择");
        }
    }
}
