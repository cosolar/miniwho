package cn.minims.who.dto;

import jakarta.validation.constraints.Size;

/** 健康检查与站点统计这类无业务归属的轻量响应模型。 */
public final class SiteDtos {

    private SiteDtos() {
    }

    public record HealthVo(String app, String status, long time) {
    }

    /** 页脚统计：累计访问次数与最近 5 分钟内有心跳的在线人数。 */
    public record SiteStatsVo(long visits, int online) {
    }

    /** 访客 id 由前端本地生成，只用于去重与心跳，服务端不落库。 */
    public record SiteVisitRequest(@Size(max = 64, message = "访客标识过长") String visitorId) {
    }
}
