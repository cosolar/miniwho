package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.SiteDtos.HealthCheckVo;
import cn.minims.who.dto.SiteDtos.HealthVo;
import cn.minims.who.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "系统")
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * 容器健康检查。
     *
     * <p>只有数据库或上传目录不可用才回 503，让编排系统真的把容器标成不健康；
     * 缺 PDF 内核这类降级项保持 200，否则关掉服务端导出的部署会永远显示故障。
     * 免登录，所以细节字段一概不给，路径与容量在后台「系统状态」里看。
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthVo>> health() {
        HealthService.Report report = healthService.report();
        List<HealthCheckVo> checks = report.checks().stream()
                .map(check -> new HealthCheckVo(check.name(), check.ok()))
                .toList();
        HealthVo vo = new HealthVo("miniwho", report.status(), System.currentTimeMillis(), checks);
        return ResponseEntity.status(report.coreOk() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.ok(vo));
    }
}
