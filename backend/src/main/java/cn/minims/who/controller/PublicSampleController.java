package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.ResumeDtos.SampleResume;
import cn.minims.who.service.ResumeService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Duration;

/**
 * 首页与模板中心共用的示例简历。接口无鉴权，因此服务端只放行被管理员显式标为示例的那一份，
 * 且只返回版面渲染需要的字段；没有设置示例时返回 null，由前端回落到打包的默认示例。
 */
@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public")
public class PublicSampleController {

    private final ResumeService resumeService;

    public PublicSampleController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping("/sample-resume")
    public ResponseEntity<ApiResponse<SampleResume>> sampleResume() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(ApiResponse.ok(resumeService.sampleResume()));
    }
}
