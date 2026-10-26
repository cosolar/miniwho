package cn.minims.who.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户意见反馈的提交模型。 */
public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record FeedbackRequest(
            @NotBlank(message = "请填写反馈内容") @Size(max = 2000, message = "反馈内容不能超过 2000 字") String content,
            @Size(max = 200) String contact) {
    }
}
