package cn.minims.who.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 附件、头像与条目图片（院校 / 公司 logo）的请求 / 响应模型。 */
public final class MediaDtos {

    private MediaDtos() {
    }

    public record AttachmentVo(Long id, String fileName, String mime, Long sizeBytes, Long createdAt) {
    }

    public record AvatarVo(boolean present, String shape, String url, Long sizeBytes, Long updatedAt) {
    }

    public record AvatarShapeRequest(
            @NotBlank(message = "请选择头像形状")
            @Pattern(regexp = "circle|rect", message = "头像形状仅支持 circle 或 rect") String shape) {
    }

    /** 条目图片（院校 / 公司 logo）：key 存进简历 content，url 由 key 直接推导。 */
    public record ItemMediaVo(String key, String url, Long sizeBytes) {
    }
}
