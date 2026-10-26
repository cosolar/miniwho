package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.ResumeDtos.ReportVo;
import cn.minims.who.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "诊断报告")
@RestController
@RequestMapping("/api/resumes/{id}/report")
public class ReportController {

    private final ReportService reportService;
    private final AuthUtil authUtil;

    public ReportController(ReportService reportService, AuthUtil authUtil) {
        this.reportService = reportService;
        this.authUtil = authUtil;
    }

    @PostMapping
    public ApiResponse<ReportVo> generate(@PathVariable long id) {
        return ApiResponse.ok(reportService.generate(authUtil.userId(), id));
    }

    @GetMapping("/latest")
    public ApiResponse<ReportVo> latest(@PathVariable long id) {
        return ApiResponse.ok(reportService.latest(authUtil.userId(), id));
    }
}
