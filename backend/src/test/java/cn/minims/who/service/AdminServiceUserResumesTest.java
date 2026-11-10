package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.dto.AdminDtos.AdminUserResumeVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.Template;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminServiceUserResumesTest {

    private UserMapper userMapper;
    private ResumeMapper resumeMapper;
    private TemplateMapper templateMapper;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        resumeMapper = mock(ResumeMapper.class);
        templateMapper = mock(TemplateMapper.class);
        adminService = new AdminService(userMapper, resumeMapper, templateMapper, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void returnsResumeOverviewWithTemplateName() {
        User user = new User();
        user.setId(42L);
        when(userMapper.selectById(42L)).thenReturn(user);

        Resume resume = new Resume();
        resume.setId(7L);
        resume.setUserId(42L);
        resume.setTitle("前端工程师简历");
        resume.setTemplateCode("qinghe");
        resume.setCompleteness(86);
        resume.setUpdatedAt(1790000000000L);
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume));

        Template template = new Template();
        template.setCode("qinghe");
        template.setName("青禾");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));

        List<AdminUserResumeVo> result = adminService.userResumes(42L);

        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.id()).isEqualTo(7L);
            assertThat(vo.title()).isEqualTo("前端工程师简历");
            assertThat(vo.templateCode()).isEqualTo("qinghe");
            assertThat(vo.templateName()).isEqualTo("青禾");
            assertThat(vo.completeness()).isEqualTo(86);
            assertThat(vo.updatedAt()).isEqualTo(1790000000000L);
        });
    }

    @Test
    void fallsBackToTemplateCodeWhenPackageWasDeleted() {
        User user = new User();
        user.setId(42L);
        when(userMapper.selectById(42L)).thenReturn(user);

        Resume resume = new Resume();
        resume.setId(8L);
        resume.setTitle("旧简历");
        resume.setTemplateCode("cosolar--custom");
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume));
        when(templateMapper.selectList(any())).thenReturn(List.of());

        assertThat(adminService.userResumes(42L))
                .singleElement()
                .extracting(AdminUserResumeVo::templateName)
                .isEqualTo("cosolar--custom");
    }

    @Test
    void rejectsUnknownUser() {
        when(userMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> adminService.userResumes(404L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
        verifyNoInteractions(resumeMapper, templateMapper);
    }
}
