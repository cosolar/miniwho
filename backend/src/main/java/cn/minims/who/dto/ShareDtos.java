package cn.minims.who.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 公开链接分享（含密码、有效期与字段隐藏）的请求 / 响应模型。 */
public final class ShareDtos {

    private ShareDtos() {
    }

    /**
     * {@code token} 是访客地址里的链接名，留空则用随机令牌。
     * 它在全库唯一，所以同名可能撞上别人的链接，此时直接拒绝而不是自动加后缀——
     * 用户要的是一条能念出来的固定地址，被悄悄改过的地址等于没给。
     */
    public record ShareCreateRequest(String token, String password, Integer expiresInDays,
                                     Boolean allowDownload, List<String> hiddenFields) {
    }

    public record ShareVo(Long id, String token, String resumeTitle, Long expiresAt, boolean allowDownload,
                          List<String> hiddenFields, boolean hasPassword, Long viewCount, Long createdAt) {
    }

    public record SharePasswordRequest(@NotBlank(message = "请输入访问密码") @Size(max = 32) String password) {
    }

    public record PublicShareVo(String token, String title, String language, String templateCode,
                                JsonNode content, JsonNode style, JsonNode templateConfig,
                                JsonNode template, boolean allowDownload, Long expiresAt, Long updatedAt,
                                String ownerName, boolean brandFooter, JsonNode avatar) {
    }
}
