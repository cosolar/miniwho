package cn.minims.who.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 简历本体、模块编辑、版面偏好与诊断报告的请求 / 响应模型。 */
public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record CreateResumeRequest(String title, String language, String templateCode, Boolean useSample) {
    }

    public record RenameRequest(@NotBlank(message = "请输入简历名称") @Size(max = 50, message = "简历名称不能超过 50 个字符") String title) {
    }

    public record ResumeSummary(Long id, String title, String language, String templateCode, String templateName,
                                Integer score, Integer completeness, Long updatedAt, String targetJob) {
    }

    public record ResumeDetail(Long id, String title, String language, String templateCode, String templateName,
                               String source, JsonNode content, JsonNode modulePrefs, JsonNode style,
                               Integer contentVersion, Integer completeness, Integer score,
                               Long scoreUpdatedAt, Long createdAt, Long updatedAt, JsonNode avatar) {
    }

    public record WorkbenchResponse(ResumeDetail resume, List<MediaDtos.AttachmentVo> attachments) {
    }

    public record SectionSaveRequest(
            @NotNull(message = "模块内容不能为空") JsonNode section,
            @NotNull(message = "缺少内容版本") Integer contentVersion) {
    }

    public record PrefsSaveRequest(
            @NotNull(message = "缺少模块顺序") List<String> order,
            @NotNull(message = "缺少隐藏模块信息") List<String> hidden,
            JsonNode customTitles,
            @NotNull(message = "缺少内容版本") Integer contentVersion) {
    }

    public record ModuleAddRequest(
            @NotNull(message = "模块内容不能为空") JsonNode section,
            @NotNull(message = "缺少内容版本") Integer contentVersion) {
    }

    public record StyleSaveRequest(String templateCode, String filter,
                                   @Min(-2) @Max(2) Integer fontSizeStep,
                                   @Min(-2) @Max(2) Integer spacingStep,
                                   Boolean showBrandFooter,
                                   PageMarginRequest pageMargin) {
    }

    /**
     * 页边距是纸面内边距，不是浏览器的打印边距：@page 已经是 0，纸外的白边只能由这里调。
     * custom=false 表示「跟随模板默认」，此时四边值不起作用——各版式自带的留白不一样。
     */
    public record PageMarginRequest(Boolean custom,
                                    @Min(0) @Max(40) Integer top,
                                    @Min(0) @Max(40) Integer right,
                                    @Min(0) @Max(40) Integer bottom,
                                    @Min(0) @Max(40) Integer left) {
    }

    public record SaveResult(int contentVersion, long savedAt, int completeness, int score) {
    }

    public record ReportVo(Long id, Long resumeId, Integer contentVersion, int totalScore, String grade,
                           JsonNode dimensions, JsonNode issues, Long createdAt, boolean current) {
    }

    /**
     * 全站示例简历。首页与模板中心的预览都读这一份，接口无鉴权，
     * 所以只放渲染版面需要的两个字段，不带用户标识、版面偏好、头像和分享配置。
     */
    public record SampleResume(String templateCode, JsonNode content) {
    }
}
