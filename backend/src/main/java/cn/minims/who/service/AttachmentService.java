package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.Dtos.AttachmentVo;
import cn.minims.who.entity.ResumeAttachment;
import cn.minims.who.mapper.ResumeAttachmentMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    public static final long MAX_SIZE = 10 * 1024 * 1024;
    public static final int MAX_ATTACHMENTS = 3;
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "pdf", "doc", "docx", "txt");
    private static final Map<String, String> MIME = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "pdf", "application/pdf", "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain"
    );

    private final ResumeAttachmentMapper attachmentMapper;
    private final ResumeService resumeService;
    private final AttachmentStore store;

    public AttachmentService(ResumeAttachmentMapper attachmentMapper, ResumeService resumeService,
                             AttachmentStore store) {
        this.attachmentMapper = attachmentMapper;
        this.resumeService = resumeService;
        this.store = store;
    }

    public List<AttachmentVo> list(long userId, long resumeId) {
        resumeService.findOwned(userId, resumeId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<ResumeAttachment>()
                        .eq(ResumeAttachment::getResumeId, resumeId)
                        .orderByDesc(ResumeAttachment::getCreatedAt))
                .stream().map(this::toVo).toList();
    }

    public AttachmentVo upload(long userId, long resumeId, MultipartFile file) {
        resumeService.findOwned(userId, resumeId);
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的文件");
        }
        long count = attachmentMapper.selectCount(new LambdaQueryWrapper<ResumeAttachment>()
                .eq(ResumeAttachment::getResumeId, resumeId));
        if (count >= MAX_ATTACHMENTS) {
            throw BizException.badRequest("附件简历最多保留 " + MAX_ATTACHMENTS + " 个，请先删除旧文件");
        }
        String original = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        original = original.replace("\\", "/");
        original = original.substring(original.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\t\\u0000-\\u001f]", "").trim();
        if (original.isBlank()) original = "resume";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > -1 && dot < original.length() - 1) {
            ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED.contains(ext)) {
            throw BizException.badRequest("仅支持 jpg、jpeg、png、pdf、doc、docx、txt 格式");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("单个附件不能超过 10MB");
        }
        if (!magicMatches(file, ext)) {
            throw BizException.badRequest("文件内容与扩展名不一致，请重新选择");
        }
        if (original.length() > 120) {
            String name = original.substring(0, dot > 0 ? dot : original.length());
            if (name.length() > 100) name = name.substring(0, 100);
            original = name + "." + ext;
        }
        String key = "attachments/" + resumeId + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream input = file.getInputStream()) {
            store.write(key, input);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败", e);
        }
        ResumeAttachment attachment = new ResumeAttachment();
        attachment.setResumeId(resumeId);
        attachment.setUserId(userId);
        attachment.setFileName(original);
        attachment.setStorageKey(key);
        attachment.setMime(MIME.getOrDefault(ext, "application/octet-stream"));
        attachment.setSizeBytes(file.getSize());
        attachmentMapper.insert(attachment);
        return toVo(attachment);
    }

    public void delete(long userId, long attachmentId) {
        ResumeAttachment attachment = requireOwned(userId, attachmentId);
        attachmentMapper.deleteById(attachmentId);
        store.deleteQuietly(attachment.getStorageKey());
    }

    public ResponseEntity<Resource> preview(long userId, long attachmentId) {
        ResumeAttachment attachment = requireOwned(userId, attachmentId);
        Path path = store.resolve(attachment.getStorageKey());
        if (!Files.exists(path)) {
            throw BizException.notFound("附件文件不存在，请重新上传");
        }
        FileSystemResource resource = new FileSystemResource(path);
        boolean inline = List.of("application/pdf", "image/jpeg", "image/png", "text/plain")
                .contains(attachment.getMime());
        String encoded = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String ext = attachment.getFileName().contains(".")
                ? attachment.getFileName().substring(attachment.getFileName().lastIndexOf('.') + 1) : "dat";
        String disposition = (inline ? "inline" : "attachment")
                + "; filename=\"attachment." + ext + "\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .contentType(MediaType.parseMediaType(attachment.getMime() == null
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE : attachment.getMime()))
                .contentLength(attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes())
                .body(resource);
    }

    private ResumeAttachment requireOwned(long userId, long attachmentId) {
        ResumeAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !Long.valueOf(userId).equals(attachment.getUserId())) {
            throw BizException.notFound("附件不存在或无权访问");
        }
        resumeService.findOwned(userId, attachment.getResumeId());
        return attachment;
    }

    private AttachmentVo toVo(ResumeAttachment attachment) {
        return new AttachmentVo(attachment.getId(), attachment.getFileName(), attachment.getMime(),
                attachment.getSizeBytes(), attachment.getCreatedAt());
    }

    private boolean magicMatches(MultipartFile file, String ext) {
        if (!List.of("jpg", "jpeg", "png", "pdf").contains(ext)) {
            return true;
        }
        try (InputStream input = file.getInputStream()) {
            byte[] head = input.readNBytes(8);
            if (head.length < 4) return false;
            return switch (ext) {
                case "jpg", "jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
                case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
                case "pdf" -> head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46;
                default -> true;
            };
        } catch (IOException e) {
            return false;
        }
    }
}
