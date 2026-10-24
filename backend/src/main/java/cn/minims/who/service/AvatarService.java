package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.Dtos.AvatarShapeRequest;
import cn.minims.who.dto.Dtos.AvatarVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.util.Json;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

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
    private final AttachmentStore store;
    private final MediaTokenService mediaTokenService;
    private final Json json;

    public AvatarService(ResumeMapper resumeMapper, AttachmentStore store, MediaTokenService mediaTokenService,
                         Json json) {
        this.resumeMapper = resumeMapper;
        this.store = store;
        this.mediaTokenService = mediaTokenService;
        this.json = json;
    }

    public AvatarVo upload(Resume resume, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的头像图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("头像图片不能超过 3MB");
        }
        byte[] raw = readAll(file);
        if (!isJpeg(raw) && !isPng(raw)) {
            throw BizException.badRequest("头像仅支持 JPG 或 PNG 图片");
        }
        BufferedImage decoded = decode(raw);
        boolean alpha = hasAlpha(decoded);
        BufferedImage prepared = prepare(decoded, alpha);
        String extension = alpha ? "png" : "jpg";
        byte[] encoded = encode(prepared, extension);
        String key = "avatars/" + resume.getId() + "/avatar." + extension;
        dropStoredFiles(resume.getId());
        try (InputStream input = new ByteArrayInputStream(encoded)) {
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
        return toVo(resume, (long) encoded.length);
    }

    public AvatarVo updateShape(Resume resume, AvatarShapeRequest request) {
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getAvatarShape, request.shape())
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        resume.setAvatarShape(request.shape());
        return toVo(resume, null);
    }

    public void delete(Resume resume) {
        dropStoredFiles(resume.getId());
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getAvatarKey, null)
                .set(Resume::getUpdatedAt, System.currentTimeMillis()));
        resume.setAvatarKey(null);
    }

    public ResponseEntity<Resource> file(Resume resume) {
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

    public AvatarVo toVo(Resume resume, Long knownSize) {
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

    public String shapeOf(Resume resume) {
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

    public Path currentPath(Resume resume) {
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

    private BufferedImage decode(byte[] raw) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            throw BizException.badRequest("图片无法解析，请换一张 JPG 或 PNG");
        }
        if (image.getWidth() < MIN_EDGE || image.getHeight() < MIN_EDGE) {
            throw BizException.badRequest("头像尺寸太小，建议不低于 400×500 像素");
        }
        return image;
    }

    private BufferedImage prepare(BufferedImage source, boolean alpha) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        int width = source.getWidth();
        int height = source.getHeight();
        if (longest > MAX_EDGE) {
            double ratio = (double) MAX_EDGE / longest;
            width = Math.max(1, (int) Math.round(width * ratio));
            height = Math.max(1, (int) Math.round(height * ratio));
        }
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (!alpha) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        }
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private boolean hasAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 24)) {
            for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 24)) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) < 250) {
                    return true;
                }
            }
        }
        return false;
    }

    private byte[] encode(BufferedImage image, String extension) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if ("png".equals(extension)) {
            try {
                ImageIO.write(image, "png", out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out.toByteArray();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前 JDK 缺少 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f);
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private boolean isJpeg(byte[] head) {
        return head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] head) {
        return head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
    }
}
