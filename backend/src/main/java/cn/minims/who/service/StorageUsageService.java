package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 账号维度的磁盘用量与配额。
 *
 * <p>用量只认盘上的文件，不认库里的 {@code size_bytes}：头像与条目 logo 根本没有大小列，
 * 而附件的历史行也可能和实际文件对不上，扫目录一次就能同时避开这两个坑。
 *
 * <p>简历文件的层级是写死的 {@code {attachments,avatars,media}/{resumeId}/文件}，所以「谁的用量」
 * 等于「他名下所有简历目录的字节数之和」，回收站里的简历也算——那些文件同样占着部署者的盘。
 * 账号头像是另一条通道 {@code user-avatars/{userId}}：目录名直接就是属主，不需要查简历。
 */
@Service
public class StorageUsageService {

    private static final List<String> CHANNELS = List.of("attachments", "avatars", "media");
    /** 账号自己的文件：按 userId 分目录，和简历通道分开归堆。 */
    private static final String ACCOUNT_CHANNEL = "user-avatars";
    private static final long MB = 1024 * 1024L;

    private final AttachmentStore store;
    private final ResumeMapper resumeMapper;
    private final UserMapper userMapper;
    private final long quotaBytes;

    public StorageUsageService(AttachmentStore store, ResumeMapper resumeMapper, UserMapper userMapper,
                               @Value("${app.storage-quota-mb:512}") long quotaMb) {
        this.store = store;
        this.resumeMapper = resumeMapper;
        this.userMapper = userMapper;
        // 0 或负数表示不设限：给需要完全放开磁盘的部署留一个开关。
        this.quotaBytes = quotaMb <= 0 ? 0L : quotaMb * MB;
    }

    public long quotaBytes() {
        return quotaBytes;
    }

    public long usageBytes(long userId) {
        long total = store.sizeOfDir(ACCOUNT_CHANNEL + "/" + userId);
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
        Map<Long, Long> accountBytes = new HashMap<>();
        for (String name : store.subDirectories(ACCOUNT_CHANNEL)) {
            long bytes = store.sizeOfDir(ACCOUNT_CHANNEL + "/" + name);
            total += bytes;
            Long userId = parseId(name);
            if (userId == null) {
                orphan += bytes;
                continue;
            }
            accountBytes.merge(userId, bytes, Long::sum);
        }
        if (!accountBytes.isEmpty()) {
            // 一次批量查存在性，别按目录各发一条查询：后台的整站扫描本来就扫所有账号。
            Set<Long> alive = new HashSet<>();
            userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .in(User::getId, accountBytes.keySet()))
                    .forEach(user -> alive.add(user.getId()));
            for (Map.Entry<Long, Long> entry : accountBytes.entrySet()) {
                if (alive.contains(entry.getKey())) {
                    byUser.merge(entry.getKey(), entry.getValue(), Long::sum);
                } else {
                    // 注销会把账号目录一起带走；还在盘上说明某条清理链路漏了，报给管理员。
                    orphan += entry.getValue();
                }
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
