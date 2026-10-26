package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.FeedbackDtos.FeedbackRequest;
import cn.minims.who.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "反馈")
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AuthUtil authUtil;

    public FeedbackController(FeedbackService feedbackService, AuthUtil authUtil) {
        this.feedbackService = feedbackService;
        this.authUtil = authUtil;
    }

    @PostMapping
    public ApiResponse<Void> submit(@Valid @RequestBody FeedbackRequest request) {
        feedbackService.submit(authUtil.userId(), request);
        return ApiResponse.ok();
    }
}
