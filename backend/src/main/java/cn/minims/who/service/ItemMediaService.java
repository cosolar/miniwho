package cn.minims.who.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.MediaDtos.ItemMediaVo;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 条目图片（院校 / 公司 logo）通道：文件存 uploads/media/{resumeId}/{uuid}.{ext}，
 * 简历 content 里只保存这个 key。
 *
 * <p>读取链接不带签名，靠 128 位随机文件名做能力凭证。头像之所以要走签名链接，是因为它
 * 直接挂在简历上、名字固定（avatar.jpg），可枚举；logo 的 key 本身就是不可猜的随机值，
 * 而且必须能被前端从 key 推导出来——编辑器会把 content 原样存回去，任何服务端注入的
 * 临时链接都会在下次保存时被写进数据里。
 */
@Service
public class ItemMediaService {

    public static final long MAX_SIZE = 2 * 1024 * 1024;
    public static final int MAX_EDGE = 256;
    public static final int MIN_EDGE = 64;
    private static final String DIR = "media";
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(jpg|png)$");
    private static final String LOGO_FIELD = "logo";

    private final AttachmentStore store;
    private final ResumeMapper resumeMapper;
    private final ResumeAccessService accessService;
    private final Json json;

    public ItemMediaService(AttachmentStore store, ResumeMapper resumeMapper, ResumeAccessService accessService,
                            Json json) {
        this.store = store;
        this.resumeMapper = resumeMapper;
        this.accessService = accessService;
        this.json = json;
    }

    public ItemMediaVo upload(long userId, long resumeId, MultipartFile file) {
        return upload(accessService.findOwned(userId, resumeId), file);
    }

    private ItemMediaVo upload(Resume resume, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("图片不能超过 2MB");
        }
        byte[] raw = readAll(file);
        if (!isJpeg(raw) && !isPng(raw)) {
            throw BizException.badRequest("只支持 JPG 或 PNG 图片");
        }
        BufferedImage decoded = decode(raw);
        boolean alpha = hasAlpha(decoded);
        byte[] encoded = encode(prepare(decoded, alpha), alpha);
        String extension = alpha ? "png" : "jpg";
        String key = path(resume.getId(), UUID.randomUUID() + "." + extension);
        try (InputStream input = new ByteArrayInputStream(encoded)) {
            store.write(key, input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ItemMediaVo(key, publicUrl(key), (long) encoded.length);
    }

    public ResponseEntity<Resource> serve(String key) {
        Path path = safeResolve(key);
        if (path == null || !Files.exists(path)) {
            throw BizException.notFound("图片不存在");
        }
        boolean png = key.endsWith(".png");
        try {
            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable")
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType(png ? "image/png" : "image/jpeg"))
                    .contentLength(Files.size(path))
                    .body(new FileSystemResource(path));
        } catch (IOException e) {
            throw BizException.notFound("图片不存在");
        }
    }

    /**
     * 垃圾回收：删掉这份简历目录下已经没有被 content 引用的图片。
     *
     * <p>放在内容落库之后，而不是让前端在换图或移除时立刻删除：编辑器里的修改要点「保存模块」才生效，
     * 用户完全可能上传后取消，这时库里存的还是旧 key，提前删文件就会把简历上的图变成 404。
     * 扫目录而不是 diff 旧 content，是因为条目复制会让同一个 key 出现多处，
     * 只有「整份 content 里还引用不到」才是安全删除的判据。
     *
     * <p>刚上传的图要先留十分钟缓冲：保存另一个模块也会走到这里，正在编辑、还没保存的 logo
     * 此刻还没进 content，立刻回收会把用户下一步要存的图删掉。
     */
    public void reconcile(long resumeId, String contentJson) {
        Set<String> referenced = new HashSet<>(logoKeys(json.read(contentJson)));
        long cutoff = System.currentTimeMillis() - GRACE.toMillis();
        for (String key : listKeys(resumeId)) {
            if (referenced.contains(key) || lastModified(key) > cutoff) {
                continue;
            }
            store.deleteQuietly(key);
        }
    }

    /** 复制简历时把 logo 一起带过去，并把副本 content 里的 key 换掉。 */
    public void copyInto(Resume source, Resume target) {
        if (!(json.read(target.getContent()) instanceof ObjectNode content)) {
            return;
        }
        List<String> keys = logoKeys(content);
        if (keys.isEmpty()) {
            return;
        }
        Map<String, String> renamed = new LinkedHashMap<>();
        for (String key : keys) {
            if (renamed.containsKey(key) || !key.startsWith(DIR + "/" + source.getId() + "/")) {
                continue;
            }
            Path from = safeResolve(key);
            if (from == null || !Files.exists(from)) {
                continue;
            }
            String next = path(target.getId(), UUID.randomUUID() + (key.endsWith(".png") ? ".png" : ".jpg"));
            try (InputStream input = Files.newInputStream(from)) {
                store.write(next, input);
                renamed.put(key, next);
            } catch (IOException e) {
                // 单个图片复制失败只影响那一条 logo，不阻断整份副本。
            }
        }
        if (renamed.isEmpty()) {
            return;
        }
        applyRenames(content, renamed);
        String rewritten = json.write(content);
        target.setContent(rewritten);
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, target.getId())
                .set(Resume::getContent, rewritten));
    }

    public void purge(Resume resume) {
        for (String key : listKeys(resume.getId())) {
            store.deleteQuietly(key);
        }
        store.deleteDirectoryQuietly(DIR + "/" + resume.getId());
    }

    private List<String> listKeys(long resumeId) {
        Path dir = store.resolve(DIR + "/" + resumeId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> FILE_NAME.matcher(name).matches())
                    .forEach(name -> keys.add(path(resumeId, name)));
        } catch (IOException e) {
            return keys;
        }
        return keys;
    }

    public static String publicUrl(String key) {
        return "/api/public/" + key;
    }

    public static List<String> logoKeys(JsonNode content) {
        List<String> keys = new ArrayList<>();
        for (JsonNode section : content.path("sections")) {
            for (JsonNode item : section.path("items")) {
                String value = item.path("fields").path(LOGO_FIELD).asText("");
                if (!value.isBlank()) {
                    keys.add(value);
                }
            }
        }
        return keys;
    }

    private void applyRenames(JsonNode content, Map<String, String> renamed) {
        for (JsonNode section : content.path("sections")) {
            for (JsonNode item : section.path("items")) {
                JsonNode fields = item.path("fields");
                if (!fields.isObject()) {
                    continue;
                }
                String value = fields.path(LOGO_FIELD).asText("");
                String next = renamed.get(value);
                if (next != null) {
                    ((ObjectNode) fields).put(LOGO_FIELD, next);
                }
            }
        }
    }

    private long lastModified(String key) {
        Path path = safeResolve(key);
        if (path == null) {
            return 0;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private Path safeResolve(String key) {
        if (key == null || !key.startsWith(DIR + "/") || !FILE_NAME.matcher(fileName(key)).matches()) {
            return null;
        }
        try {
            return store.resolve(key);
        } catch (BizException e) {
            return null;
        }
    }

    private static String path(long resumeId, String name) {
        return DIR + "/" + resumeId + "/" + name;
    }

    private static String fileName(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(slash + 1);
    }

    private byte[] readAll(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw BizException.badRequest("图片读取失败，请重新选择");
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
            throw BizException.badRequest("图片尺寸太小，建议不低于 128×128 像素");
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

    private byte[] encode(BufferedImage image, boolean png) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (png) {
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
