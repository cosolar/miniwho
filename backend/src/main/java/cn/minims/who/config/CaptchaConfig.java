package cn.minims.who.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.common.model.dto.ResourceMap;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import cloud.tianai.captcha.validator.ImageCaptchaValidator;
import cloud.tianai.captcha.validator.impl.BasicCaptchaTrackValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 滑块验证码的图片与判定规则。
 *
 * <p>图片全部自带，不依赖 tianai 包里那张唯一的示例图：背景图放在
 * {@code classpath:captcha/bg} 下，启动时按文件名扫一遍，新增一张就等于多一套底图，
 * 不用改代码。拼图凹槽用官方那两套 {@code active.png / fixed.png} 模板。
 *
 * <p>判定器换成 {@link BasicCaptchaTrackValidator}：默认的 Simple 版只比对缺口位置，
 * 这里额外看轨迹本身——按下到松手的时长、采样点数、起始点是否从原点开始、
 * y 轴是否纹丝不动、相邻采样点的跨度、以及「先快后慢」的减速曲线，
 * 脚本直接 POST 一个终点位置已经过不了。
 */
@Configuration
public class CaptchaConfig {

    private static final Logger log = LoggerFactory.getLogger(CaptchaConfig.class);

    /** 允许的落点误差：600 宽的底图上是 18px，再紧会让手抖的正常用户重试。 */
    private static final float TOLERANT = 0.03f;
    /** 拼图模板在 tianai 包里的固定位置。 */
    private static final String TEMPLATE_ROOT = "META-INF/cut-image/template/";
    private static final String BACKGROUND_ROOT = "captcha/bg/";
    private static final String ACTIVE_IMAGE = "active.png";
    private static final String FIXED_IMAGE = "fixed.png";

    @Bean
    public ResourceStore captchaResourceStore() throws IOException {
        LocalMemoryResourceStore store = new LocalMemoryResourceStore();
        store.addTemplate(CaptchaTypeConstant.SLIDER, template("slider_1"));
        store.addTemplate(CaptchaTypeConstant.SLIDER, template("slider_2"));

        for (String fileName : backgroundFileNames()) {
            store.addResource(CaptchaTypeConstant.SLIDER,
                    new Resource("classpath", BACKGROUND_ROOT + fileName, "default"));
        }
        return store;
    }

    /** 覆盖 starter 默认的 SimpleImageCaptchaValidator：滑块要看轨迹，不只看落点。 */
    @Bean
    public ImageCaptchaValidator imageCaptchaValidator() {
        return new BasicCaptchaTrackValidator(TOLERANT);
    }

    private static ResourceMap template(String dir) {
        ResourceMap map = new ResourceMap("default", 2);
        map.put(ACTIVE_IMAGE, new Resource("classpath", TEMPLATE_ROOT + dir + "/" + ACTIVE_IMAGE));
        map.put(FIXED_IMAGE, new Resource("classpath", TEMPLATE_ROOT + dir + "/" + FIXED_IMAGE));
        return map;
    }

    /**
     * 扫 classpath 下的底图。打成 jar 后目录遍历不可用，这里走 Spring 的资源匹配，
     * jar 内的 {@code BOOT-INF/classes/captcha/bg/*.jpg} 同样能被列出来。
     */
    private static String[] backgroundFileNames() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        org.springframework.core.io.Resource[] resources = resolver.getResources("classpath*:" + BACKGROUND_ROOT + "*.jpg");
        String[] names = Arrays.stream(resources)
                .map(org.springframework.core.io.Resource::getFilename)
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator.naturalOrder())
                .toArray(String[]::new);
        if (names.length == 0) {
            // 一张都没有时 tianai 会在生成阶段抛空指针，这里直接说清楚缺了什么。
            throw new IllegalStateException("缺少滑块验证码背景图：classpath:" + BACKGROUND_ROOT + "*.jpg");
        }
        log.info("滑块验证码已加载 {} 张背景图", names.length);
        return names;
    }
}
