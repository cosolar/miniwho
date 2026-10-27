package cn.minims.who.service;

import cn.minims.who.dto.AdminDtos;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行环境自检，给容器健康检查和后台概览用。
 *
 * <p>核心项（数据库、上传目录）失败才算 DOWN，管理项（PDF、邮件）不达标只算降级：
 * 关掉服务端导出的部署本来就是正常状态，不能因此让容器一直显示 unhealthy。
 */
@Service
public class HealthService {

    private static final int DB_VALID_SECONDS = 2;

    private final DataSource dataSource;
    private final AttachmentStore store;
    private final ChromePdfRenderer renderer;
    private final MailService mailService;
    private final StorageUsageService usage;

    public HealthService(DataSource dataSource, AttachmentStore store, ChromePdfRenderer renderer,
                         MailService mailService, StorageUsageService usage) {
        this.dataSource = dataSource;
        this.store = store;
        this.renderer = renderer;
        this.mailService = mailService;
        this.usage = usage;
    }

    public record Check(String name, boolean ok, String detail) {
    }

    /** 核心项全过是 UP，核心项有失败是 DOWN，其余失败是 DEGRADED。 */
    public record Report(String status, boolean coreOk, List<Check> checks) {
    }

    public Report report() {
        List<Check> checks = new ArrayList<>(4);
        Check database = database();
        Check storage = storage();
        checks.add(database);
        checks.add(storage);
        boolean coreOk = database.ok() && storage.ok();
        String status = !coreOk ? "DOWN" : (renderer.available() ? "UP" : "DEGRADED");
        return new Report(status, coreOk, List.copyOf(checks));
    }

    /** 后台用的全量视图：连降级项与磁盘数字一起给，反正这层已经要求管理员。 */
    public Report fullReport() {
        Report base = report();
        List<Check> checks = new ArrayList<>(base.checks());
        checks.add(new Check("pdf", renderer.available(), renderer.engine()));
        checks.add(new Check("mail", mailService.available(),
                mailService.available() ? "SMTP 已就绪" : "未配置 SMTP：邮箱注册、找回密码与注销验证码不可用"));
        long free = freeBytes();
        checks.add(new Check("disk", free > 0, "剩余 " + (free / (1024 * 1024)) + " MB"));
        return new Report(base.status(), base.coreOk(), List.copyOf(checks));
    }

    public StorageUsageService.SiteUsage siteUsage() {
        return usage.scanSite();
    }

    public long quotaBytes() {
        return usage.quotaBytes();
    }

    /** 后台「系统状态」视图：自检项连细节一起给，再补磁盘水位、用量与配额。 */
    public AdminDtos.AdminSystemVo adminView() {
        Report report = fullReport();
        StorageUsageService.SiteUsage scan = usage.scanSite();
        List<AdminDtos.AdminCheckVo> checks = report.checks().stream()
                .map(check -> new AdminDtos.AdminCheckVo(check.name(), check.ok(), check.detail()))
                .toList();
        return new AdminDtos.AdminSystemVo(report.status(), report.coreOk(), checks,
                store.root().toString(), freeBytes(), scan.totalBytes(), scan.orphanBytes(), usage.quotaBytes());
    }

    /** 取不到容量（比如目录根本没挂载）时按 0 计，交给前端显示成未知。 */
    public long freeBytes() {
        return Math.max(store.root().toFile().getUsableSpace(), 0L);
    }

    private Check database() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(DB_VALID_SECONDS);
            return new Check("database", valid, valid ? "连接可用" : "连接池取到的连接不可用");
        } catch (SQLException e) {
            return new Check("database", false, "数据库连接失败");
        }
    }

    /**
     * 真正写一个文件再删掉：挂载卷只读、目录被删、磁盘写满这三种故障，
     * 只看 {@code Files.isWritable} 都测不出来。
     */
    private Check storage() {
        Path probe = store.root().resolve(".health-probe");
        try {
            Files.createDirectories(probe.getParent());
            Files.writeString(probe, "probe");
            Files.deleteIfExists(probe);
            return new Check("storage", true, "上传目录可写");
        } catch (IOException | RuntimeException e) {
            return new Check("storage", false, "上传目录不可写");
        }
    }
}
