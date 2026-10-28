package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.dto.ShareDtos.ShareCreateRequest;
import cn.minims.who.dto.ShareDtos.ShareVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeShare;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeShareMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserMapper;
import cn.minims.who.util.Json;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 分享链接名的守门：格式、全库唯一、以及「同名重发」为什么必须复用原来那一行。 */
class ShareServiceTest {

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 的 set() 要靠实体列缓存解析字段名，单测里没有 MyBatis 启动流程，手工挂一次。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ResumeShare.class);
    }

    private final ResumeShareMapper shareMapper = mock(ResumeShareMapper.class);
    private final ResumeService resumeService = mock(ResumeService.class);

    private ShareService service() {
        Resume resume = new Resume();
        resume.setId(7L);
        resume.setUserId(1L);
        resume.setTitle("后端工程师简历");
        when(resumeService.findOwned(1L, 7L)).thenReturn(resume);
        return new ShareService(resumeService, mock(AvatarService.class), mock(ResumeMapper.class),
                shareMapper, mock(TemplateMapper.class), mock(UserMapper.class), new Json(new ObjectMapper()));
    }

    private static ShareCreateRequest request(String token) {
        return new ShareCreateRequest(token, null, 30, true, List.of("phone"));
    }

    private static ResumeShare row(long id, long userId, long resumeId, String token) {
        ResumeShare share = new ResumeShare();
        share.setId(id);
        share.setUserId(userId);
        share.setResumeId(resumeId);
        share.setToken(token);
        share.setViewCount(12L);
        share.setAllowDownload(1);
        share.setHiddenFields("[]");
        return share;
    }

    @Test
    void 留空时使用随机令牌() {
        ShareVo vo = service().create(1L, 7L, request("  "));

        assertThat(vo.token()).matches("[A-Za-z0-9_-]{40,}");
        verify(shareMapper).insert(any(ResumeShare.class));
    }

    @Test
    void 自定义链接名原样落库() {
        ShareVo vo = service().create(1L, 7L, request("haojianli"));

        assertThat(vo.token()).isEqualTo("haojianli");
        ArgumentCaptor<ResumeShare> captor = ArgumentCaptor.forClass(ResumeShare.class);
        verify(shareMapper).insert(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("haojianli");
    }

    @Test
    void 链接名字符集不合法时拒绝() {
        for (String bad : List.of("abc", "a".repeat(41), "hao 简历", "hao-jianli!", "share/../../")) {
            assertThatThrownBy(() -> service().create(1L, 7L, request(bad)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("链接名只能使用");
        }
        verify(shareMapper, never()).insert(any(ResumeShare.class));
    }

    @Test
    void 名字已被别人的链接占用时报冲突() {
        when(shareMapper.selectOne(any())).thenReturn(row(99L, 2L, 33L, "haojianli"));

        assertThatThrownBy(() -> service().create(1L, 7L, request("haojianli")))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("SHARE_TOKEN_TAKEN");
                    assertThat(error.getMessage()).contains("已被别人占用");
                });
        verify(shareMapper, never()).insert(any(ResumeShare.class));
    }

    @Test
    void 名字用在自己的另一份简历上时也报冲突() {
        when(shareMapper.selectOne(any())).thenReturn(row(99L, 1L, 88L, "haojianli"));

        assertThatThrownBy(() -> service().create(1L, 7L, request("haojianli")))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.getMessage()).contains("另一份简历"));
    }

    @Test
    void 重发同名链接时复用原记录而不是新插一条() {
        when(shareMapper.selectOne(any())).thenReturn(row(5L, 1L, 7L, "haojianli"));

        ShareVo vo = service().create(1L, 7L, request("haojianli"));

        assertThat(vo.id()).isEqualTo(5L);
        assertThat(vo.token()).isEqualTo("haojianli");
        assertThat(vo.viewCount()).isZero();
        assertThat(vo.hasPassword()).isFalse();
        verify(shareMapper, never()).insert(any(ResumeShare.class));
    }
}
