package cn.minims.who.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** 健康检查与站点统计这类无业务归属的轻量响应模型。 */
public final class SiteDtos {

    private SiteDtos() {
    }

    /**
     * 对外健康检查：{@code checks} 只给通过与否，不给路径与容量数字——这个接口不需要登录。
     * {@code status} 为 DOWN 时 HTTP 会是 503，容器的 healthcheck 据此判定不健康。
     */
    public record HealthVo(String app, String status, long time, List<HealthCheckVo> checks) {
    }

    public record HealthCheckVo(String name, boolean ok) {
    }

    /**
     * 账号自己的磁盘用量。
     *
     * @param quotaBytes 上限，0 表示部署者没有设限
     */
    public record StorageVo(long usedBytes, long quotaBytes) {
    }

    /** 页脚统计：累计访问次数与最近 5 分钟内有心跳的在线人数。 */
    public record SiteStatsVo(long visits, int online) {
    }

    /** 访客 id 由前端本地生成，只用于去重与心跳，服务端不落库。 */
    public record SiteVisitRequest(@Size(max = 64, message = "访客标识过长") String visitorId) {
    }
}
