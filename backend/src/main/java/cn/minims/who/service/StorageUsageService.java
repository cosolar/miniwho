package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.entity.Resume;
import cn.minims.who.mapper.ResumeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 账号维度的磁盘用量与配额。
 *
 * <p>用量只认盘上的文件，不认库里的 {@code size_bytes}：头像与条目 logo 根本没有大小列，
 * 而附件的历史行也可能和实际文件对不上，扫目录一次就能同时避开这两个坑。
 *
 * <p>上传目录的层级是写死的 {@code {attachments,avatars,media}/{resumeId}/文件}，
 * 所以「谁的用量」等于「他名下所有简历目录的字节数之和」，回收站里的简历也算——
 * 那些文件同样占着部署者的盘。
 */
@Service
public class StorageUsageService {

    private static final List<String> CHANNELS = List.of("attachments", "avatars", "media");
    private static final long MB = 1024 * 1024L;

    private final AttachmentStore store;
    private final ResumeMapper resumeMapper;
    private final long quotaBytes;

    public StorageUsageService(AttachmentStore store, ResumeMapper resumeMapper,
                               @Value("${app.storage-quota-mb:512}") long quotaMb) {
        this.store = store;
        this.resumeMapper = resumeMapper;
        // 0 或负数表示不设限：给需要完全放开磁盘的部署留一个开关。
        this.quotaBytes = quotaMb <= 0 ? 0L : quotaMb * MB;
    }

    public long quotaBytes() {
        return quotaBytes;
    }

    public long usageBytes(long userId) {
        long total = 0L;
        for (Long resumeId : resumeMapper.selectIdsByUser(userId)) {
            total += resumeBytes(resumeId);
        }
        return total;
    }

    /**
     * 整站一次扫描：每个账号占了多少字节，外加找不到属主的孤儿目录字节数。
     * 后台要看的是全部账号的占用，逐个账号扫盘会变成 N 次遍历。
     */
    public SiteUsage scanSite() {
        Map<Long, Long> ownerOf = new HashMap<>();
        for (Resume resume : resumeMapper.selectOwners()) {
            if (resume.getId() != null && resume.getUserId() != null) {
                ownerOf.put(resume.getId(), resume.getUserId());
            }
        }
        Map<Long, Long> byUser = new HashMap<>();
        long orphan = 0L;
        long total = 0L;
        for (String channel : CHANNELS) {
            for (String name : store.subDirectories(channel)) {
                long bytes = store.sizeOfDir(channel + "/" + name);
                Long resumeId = parseId(name);
                Long owner = resumeId == null ? null : ownerOf.get(resumeId);
                if (owner == null) {
                    // 简历早就删了但目录还在：说明某条删除链路漏了清理，值得让管理员看见。
                    orphan += bytes;
                } else {
                    byUser.merge(owner, bytes, Long::sum);
                }
                total += bytes;
            }
        }
        return new SiteUsage(byUser, orphan, total);
    }

    /** 上传前的一道闸：已经超额就继续拒绝，未设配额时直接放过。 */
    public void assertRoom(long userId, long incomingBytes) {
        if (quotaBytes <= 0) {
            return;
        }
        long used = usageBytes(userId);
        if (used + incomingBytes > quotaBytes) {
            throw BizException.storageFull(String.format(Locale.ROOT,
                    "账号已用 %s，本次要存 %s，上限 %s。请先删除旧附件或不再需要的图片。",
                    readable(used), readable(incomingBytes), readable(quotaBytes)));
        }
    }

    private long resumeBytes(long resumeId) {
        long total = 0L;
        for (String channel : CHANNELS) {
            total += store.sizeOfDir(channel + "/" + resumeId);
        }
        return total;
    }

    private static Long parseId(String name) {
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readable(long bytes) {
        if (bytes >= MB) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (double) MB);
        }
        return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
    }

    public record SiteUsage(Map<Long, Long> byUser, long orphanBytes, long totalBytes) {

        public long userBytes(long userId) {
            return byUser.getOrDefault(userId, 0L);
        }
    }
}
