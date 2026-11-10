package cn.minims.who.common;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.generator.common.model.dto.GenerateParam;
import cloud.tianai.captcha.validator.common.model.dto.MatchParam;
import cn.minims.who.dto.AuthDtos.CaptchaTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CaptchaServiceTest {

    private final ImageCaptchaApplication application = mock(ImageCaptchaApplication.class);
    private final CaptchaService service = new CaptchaService(application, true, 5 * 60_000L);

    @Test
    void generateReturnsImagesOnly() {
        ImageCaptchaVO vo = new ImageCaptchaVO();
        vo.setId("SLIDER_abc");
        vo.setType("SLIDER");
        vo.setBackgroundImage("data:image/jpeg;base64,AAAA");
        vo.setTemplateImage("data:image/png;base64,BBBB");
        vo.setBackgroundImageWidth(600);
        vo.setBackgroundImageHeight(360);
        vo.setTemplateImageWidth(60);
        vo.setTemplateImageHeight(360);
        doReturn(ApiResponse.ofSuccess(vo)).when(application).generateCaptcha(any(GenerateParam.class));

        CaptchaService.Challenge challenge = service.generate();

        assertThat(challenge.id()).isEqualTo("SLIDER_abc");
        assertThat(challenge.backgroundImage()).startsWith("data:image/jpeg;base64,");
        assertThat(challenge.templateImage()).startsWith("data:image/png;base64,");
        assertThat(challenge.backgroundImageWidth()).isEqualTo(600);
        assertThat(challenge.templateImageHeight()).isEqualTo(360);
    }

    @Test
    void verifiedTrackBurnsOneTicketPerSlide() {
        doReturn(ApiResponse.ofSuccess()).when(application).matching(anyString(), any(MatchParam.class));

        String ticket = service.verify("SLIDER_abc", track(20));

        assertThat(ticket).isNotBlank();
        assertThat(service.consumeTicket(ticket)).isTrue();
        // 一次性：同一张票不能拿来登录第二次。
        assertThat(service.consumeTicket(ticket)).isFalse();
    }

    @Test
    void rejectedTrackYieldsNoTicket() {
        doReturn(ApiResponse.ofCheckError("位置不对")).when(application).matching(anyString(), any(MatchParam.class));

        assertThatThrownBy(() -> service.verify("SLIDER_abc", track(20)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("验证未通过");
    }

    @Test
    void malformedTrackIsRejectedBeforeTouchingTheLibrary() {
        CaptchaTrack empty = new CaptchaTrack(600, 360, 60, 360, 0L, 100L, List.of());

        assertThatThrownBy(() -> service.verify("SLIDER_abc", empty))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据不完整");
        verifyNoInteractions(application);
    }

    @Test
    void expiredTicketIsRejected() throws InterruptedException {
        CaptchaService shortLived = new CaptchaService(application, true, 20);
        doReturn(ApiResponse.ofSuccess()).when(application).matching(anyString(), any(MatchParam.class));

        String ticket = shortLived.verify("SLIDER_abc", track(20));
        Thread.sleep(60);

        assertThat(shortLived.consumeTicket(ticket)).isFalse();
    }

    @Test
    void disabledCaptchaAcceptsAnyLogin() {
        CaptchaService off = new CaptchaService(application, false, 5 * 60_000L);

        assertThat(off.enabled()).isFalse();
        assertThat(off.consumeTicket(null)).isTrue();
        assertThat(off.consumeTicket("whatever")).isTrue();
    }

    /** 一条形状正常的轨迹：起点在原点附近、x 单调右移、带时间戳。 */
    private static CaptchaTrack track(int points) {
        List<CaptchaTrack.Point> list = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            list.add(new CaptchaTrack.Point(i * 4f, (float) Math.sin(i / 3d) * 2, i * 18f, "MOVE"));
        }
        return new CaptchaTrack(600, 360, 60, 360, 0L, points * 18L, list);
    }
}
