package cn.minims.who.service;

import cn.minims.who.dto.MediaDtos.AvatarShapeRequest;
import cn.minims.who.dto.MediaDtos.AvatarVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.util.Json;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 证件照形状三档的往返：auto（跟随模板）/ rect（方形）/ circle（圆形）。
 *
 * <p>曾经 shapeOf() 把 auto 压成 rect，用户在编辑器里点「跟随模板」存进去，
 * 接口读回来还是 rect，界面看着像那个按钮点不动。这里盯住三档都能原样读回。
 */
class AvatarShapeServiceTest {

    private static final long USER_ID = 9L;
    private static final long RESUME_ID = 11L;

    @TempDir
    Path root;

    private Resume resume;
    private AvatarService service;

    /** 服务层用 LambdaUpdateWrapper 写 avatar_shape，没有表信息缓存就连 lambda 都取不到。 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Resume.class);
    }

    @BeforeEach
    void setUp() {
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);
        resume.setAvatarShape("auto");
        when(resumeMapper.selectById(RESUME_ID)).thenReturn(resume);
        when(resumeMapper.update(any(), any())).thenReturn(1);
        ResumeAccessService accessService = new ResumeAccessService(resumeMapper);
        service = new AvatarService(resumeMapper, accessService,
                new AttachmentStore(root.toString()),
                new MediaTokenService("test-secret"),
                new Json(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    void autoRoundTripsAsAuto() {
        AvatarVo vo = service.updateShape(USER_ID, RESUME_ID, new AvatarShapeRequest("auto"));

        assertThat(vo.shape()).isEqualTo("auto");
        assertThat(resume.getAvatarShape()).isEqualTo("auto");
        // 读路径也要一致：简历详情、分享页都走 toVo
        assertThat(service.toVo(USER_ID, RESUME_ID).shape()).isEqualTo("auto");
    }

    @Test
    void rectAndCircleRoundTrip() {
        assertThat(service.updateShape(USER_ID, RESUME_ID, new AvatarShapeRequest("rect")).shape())
                .isEqualTo("rect");
        assertThat(service.updateShape(USER_ID, RESUME_ID, new AvatarShapeRequest("circle")).shape())
                .isEqualTo("circle");
    }

    @Test
    void blankShapeFallsBackToAuto() {
        resume.setAvatarShape(null);

        assertThat(service.toVo(USER_ID, RESUME_ID).shape()).isEqualTo("auto");
    }
}
