package cn.minims.who.common;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.generator.common.model.dto.GenerateParam;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.common.model.dto.MatchParam;
import cn.minims.who.dto.AuthDtos.CaptchaTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录前的滑块验证码：底图与拼图由 tianai-captcha 现画，落点与轨迹都由服务端判定。
 *
 * <p>校验通过后不再回传「答对了」这种一次性结论，而是发一张票据：登录接口只认票据，
 * 票据用一次就作废。这样即使有人绕过前端直接 POST 校验接口，也拿不到能反复使用的凭据；
 * 反过来，票据必须由这一次真实的滑动换出来，脚本没法自己拼轨迹换票。
 *
 * <p>答案（缺口位置）从头到尾不进响应体，前后端协议里也没有它的字段。
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    /** 票据有效期：从滑完到点下登录，正常不会超过两分钟。 */
    static final long TICKET_TTL_MS = 5 * 60_000L;
    private static final int MAX_TICKETS = 20_000;
    /** 轨迹上限：一张 600 宽的图滑到底也就几百个采样点，再多就是有人在灌数据。 */
    private static final int MAX_TRACK_POINTS = 1_200;
    private static final int ID_MAX_LENGTH = 64;

    private final ImageCaptchaApplication application;
    private final boolean enabled;
    private final long ticketTtlMs;
    private final Map<String, Long> tickets = new ConcurrentHashMap<>();

    @Autowired
    public CaptchaService(ImageCaptchaApplication application,
                          @Value("${app.captcha.enabled:true}") boolean enabled) {
        this(application, enabled, TICKET_TTL_MS);
    }

    CaptchaService(ImageCaptchaApplication application, boolean enabled, long ticketTtlMs) {
        this.application = application;
        this.enabled = enabled;
        this.ticketTtlMs = ticketTtlMs;
    }

    /** 关掉开关时登录不再要求票据，留给自动化测试与内网调试用。 */
    public boolean enabled() {
        return enabled;
    }

    /** 出一张新题：底图、拼图、尺寸都给前端，缺口位置（x/y）不给。 */
    public Challenge generate() {
        GenerateParam param = new GenerateParam();
        param.setType(CaptchaTypeConstant.SLIDER);
        ApiResponse<ImageCaptchaVO> response = application.generateCaptcha(param);
        ImageCaptchaVO vo = response == null ? null : response.getData();
        if (vo == null) {
            throw new BizException("CAPTCHA_UNAVAILABLE", "验证码暂时不可用，请稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new Challenge(vo.getId(), vo.getType(), vo.getBackgroundImage(), vo.getTemplateImage(),
                value(vo.getBackgroundImageWidth()), value(vo.getBackgroundImageHeight()),
                value(vo.getTemplateImageWidth()), value(vo.getTemplateImageHeight()));
    }

    /**
     * 校验轨迹并换票据。任何一项不合格都按「请重新拖动」处理：
     * tianai 那边同一个 id 只取一次缓存，校验失败后这张题就作废了，不存在「同一张图反复试」。
     */
    public String verify(String id, CaptchaTrack track) {
        if (id == null || id.isBlank() || id.length() > ID_MAX_LENGTH || track == null) {
            throw BizException.badRequest("验证码已失效，请重新拖动滑块");
        }
        ImageCaptchaTrack imageTrack = toImageTrack(track);
        ApiResponse<?> response;
        try {
            response = application.matching(id.trim(), new MatchParam(imageTrack));
        } catch (IllegalArgumentException e) {
            // 缺字段、点数为空这类畸形提交，一律当作验证码失效，不把内部异常抛给前端。
            log.debug("滑块验证参数非法: {}", e.getMessage());
            throw BizException.badRequest("验证码数据不完整，请重新拖动滑块");
        }
        if (response == null || !response.isSuccess()) {
            throw BizException.badRequest("验证未通过，请重新拖动滑块");
        }
        return grantTicket();
    }

    /** 登录时核销票据：用掉即删，答对一次只能登录一次。 */
    public boolean consumeTicket(String ticket) {
        if (!enabled) {
            return true;
        }
        if (ticket == null || ticket.isBlank() || ticket.length() > ID_MAX_LENGTH) {
            return false;
        }
        Long expiresAt = tickets.remove(ticket.trim());
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    /** 公开记录：图片是 data URI，前端直接塞进 img，不用再发第二次请求。 */
    public record Challenge(String id, String type, String backgroundImage, String templateImage,
                            int backgroundImageWidth, int backgroundImageHeight,
                            int templateImageWidth, int templateImageHeight) {
    }

    private String grantTicket() {
        evictIfNeeded();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, System.currentTimeMillis() + ticketTtlMs);
        return ticket;
    }

    private ImageCaptchaTrack toImageTrack(CaptchaTrack source) {
        if (source.trackList() == null || source.trackList().isEmpty()
                || source.trackList().size() > MAX_TRACK_POINTS) {
            throw BizException.badRequest("验证码数据不完整，请重新拖动滑块");
        }
        ImageCaptchaTrack track = new ImageCaptchaTrack();
        track.setBgImageWidth(source.bgImageWidth());
        track.setBgImageHeight(source.bgImageHeight());
        track.setTemplateImageWidth(source.templateImageWidth());
        track.setTemplateImageHeight(source.templateImageHeight());
        track.setStartTime(source.startTime());
        track.setStopTime(source.stopTime());
        List<ImageCaptchaTrack.Track> points = new ArrayList<>(source.trackList().size());
        for (CaptchaTrack.Point point : source.trackList()) {
            if (point == null || point.x() == null || point.y() == null || point.t() == null) {
                throw BizException.badRequest("验证码数据不完整，请重新拖动滑块");
            }
            points.add(new ImageCaptchaTrack.Track(point.x(), point.y(), point.t(),
                    point.type() == null || point.type().isBlank() ? "MOVE" : point.type()));
        }
        track.setTrackList(points);
        return track;
    }

    /** 没人来核销的票会一直占内存：超过上限时顺手清掉过期项。 */
    private void evictIfNeeded() {
        if (tickets.size() < MAX_TICKETS) {
            return;
        }
        long now = System.currentTimeMillis();
        tickets.forEach((key, expiresAt) -> {
            if (expiresAt <= now) {
                tickets.remove(key, expiresAt);
            }
        });
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }
}
