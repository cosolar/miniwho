package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.Dtos.SiteStatsVo;
import cn.minims.who.dto.Dtos.SiteVisitRequest;
import cn.minims.who.service.SiteStatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开统计：页脚展示的累计访问次数与在线人数。
 * /api/public/** 在拦截器里是免登录路径，这里只暴露聚合数字，不回传任何访客标识。
 */
@Tag(name = "站点统计")
@RestController
@RequestMapping("/api/public/stats")
public class SiteStatsController {

    private final SiteStatsService siteStatsService;

    public SiteStatsController(SiteStatsService siteStatsService) {
        this.siteStatsService = siteStatsService;
    }

    @GetMapping
    public ApiResponse<SiteStatsVo> stats() {
        return ApiResponse.ok(siteStatsService.snapshot());
    }

    /** 一次页面会话只调一次：计访问 + 打心跳。 */
    @PostMapping("/visit")
    public ApiResponse<SiteStatsVo> visit(@RequestBody(required = false) SiteVisitRequest request) {
        siteStatsService.recordVisit(request == null ? null : request.visitorId());
        return ApiResponse.ok(siteStatsService.snapshot());
    }

    /** 轮询心跳，只更新在线状态，不累加访问数。 */
    @PostMapping("/ping")
    public ApiResponse<SiteStatsVo> ping(@RequestBody(required = false) SiteVisitRequest request) {
        siteStatsService.touch(request == null ? null : request.visitorId());
        return ApiResponse.ok(siteStatsService.snapshot());
    }
}
