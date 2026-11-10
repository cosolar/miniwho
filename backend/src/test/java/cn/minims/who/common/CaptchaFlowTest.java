package cn.minims.who.common;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.TACBuilder;
import cloud.tianai.captcha.cache.impl.LocalCacheStore;
import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.generator.common.model.dto.ImageCaptchaInfo;
import cloud.tianai.captcha.generator.impl.transform.Base64ImageTransform;
import cloud.tianai.captcha.interceptor.CaptchaInterceptor;
import cloud.tianai.captcha.interceptor.Context;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cn.minims.who.config.CaptchaConfig;
import cn.minims.who.dto.AuthDtos.CaptchaTrack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 不经过 Spring，直接用项目自己的图片与判定规则跑一遍真实链路：
 * 出题 → 按缺口位置回放一条「先快后慢」的轨迹 → 换票据。
 * <p>顺带守住两件事：图片确实是从 resources/captcha/bg 里出来的，
 * 以及一条只有两点的直线轨迹过不了行为校验。
 */
class CaptchaFlowTest {

    private static CaptchaService captchaService;
    private static CapturingInterceptor interceptor;

    @BeforeAll
    static void setUp() throws Exception {
        interceptor = new CapturingInterceptor();
        ImageCaptchaApplication application = TACBuilder.builder()
                .setResourceStore(new CaptchaConfig().captchaResourceStore())
                .setValidator(new CaptchaConfig().imageCaptchaValidator())
                .setInterceptor(interceptor)
                .setCacheStore(new LocalCacheStore())
                .setTransform(new Base64ImageTransform())
                .build();
        captchaService = new CaptchaService(application, true);
    }

    @Test
    void humanTrackPassesAndBuysOneTicket() {
        CaptchaService.Challenge challenge = captchaService.generate();
        assertThat(challenge.id()).startsWith("SLIDER_");
        assertThat(challenge.backgroundImage()).startsWith("data:image/jpeg;base64,");
        assertThat(challenge.templateImage()).startsWith("data:image/png;base64,");
        assertThat(challenge.backgroundImageWidth()).isEqualTo(600);
        assertThat(challenge.backgroundImageHeight()).isEqualTo(360);
        // 拼图是一整条和底图等高的透明长条，高度必须等于底图，前端才能靠 top:0 对齐。
        assertThat(challenge.templateImageHeight()).isEqualTo(challenge.backgroundImageHeight());

        float percentage = interceptor.percentage();
        String ticket = captchaService.verify(challenge.id(), curvedTrack(percentage, 24, 700));

        assertThat(ticket).isNotBlank();
        assertThat(captchaService.consumeTicket(ticket)).isTrue();
    }

    @Test
    void straightTwoPointTrackIsRejected() {
        CaptchaService.Challenge challenge = captchaService.generate();
        float percentage = interceptor.percentage();
        float end = percentage * challenge.backgroundImageWidth();

        CaptchaTrack robot = new CaptchaTrack(600, 360, 110, 360, 0L, 40L, List.of(
                new CaptchaTrack.Point(0f, 0f, 0f, "MOVE"),
                new CaptchaTrack.Point(end, 0f, 40f, "MOVE")));

        assertThatThrownBy(() -> captchaService.verify(challenge.id(), robot))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * 回放一次人手拖动：终点落在缺口上，纵向带一点抖动，采样点越到后面间隔越长（松手前的减速）。
     */
    private static CaptchaTrack curvedTrack(float percentage, int points, long durationMs) {
        double end = percentage * 600d;
        List<CaptchaTrack.Point> list = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double progress = i / (double) (points - 1);
            // 位移走 ease-out：起步快、临近缺口慢下来
            double x = end * (1 - Math.pow(1 - progress, 2.1));
            double t = durationMs * Math.pow(progress, 1.55);
            double y = Math.sin(progress * Math.PI) * 6;
            list.add(new CaptchaTrack.Point((float) x, (float) y, (float) t, "MOVE"));
        }
        list.set(0, new CaptchaTrack.Point(0f, 0f, 0f, "MOVE"));
        return new CaptchaTrack(600, 360, 110, 360, 0L, durationMs, list);
    }

    /** 把生成时算好的缺口百分比截下来，测试才能「作弊」滑到正确位置。 */
    private static final class CapturingInterceptor implements CaptchaInterceptor {
        private volatile AnyMap validData;

        @Override
        public String getName() {
            return "captcha-test-capture";
        }

        @Override
        public void afterGenerateImageCaptchaValidData(Context context, String type,
                                                       ImageCaptchaInfo imageCaptchaInfo, AnyMap validData) {
            this.validData = validData;
        }

        float percentage() {
            AnyMap data = validData;
            if (data == null) {
                throw new IllegalStateException("没有截获到校验数据");
            }
            Float value = data.getFloat("percentage");
            if (value == null) {
                throw new IllegalStateException("校验数据里没有 percentage");
            }
            return value;
        }
    }
}
