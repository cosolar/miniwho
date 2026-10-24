package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.Dtos.CreateResumeRequest;
import cn.minims.who.dto.Dtos.ModuleAddRequest;
import cn.minims.who.dto.Dtos.PrefsSaveRequest;
import cn.minims.who.dto.Dtos.RenameRequest;
import cn.minims.who.dto.Dtos.ResumeDetail;
import cn.minims.who.dto.Dtos.ResumeSummary;
import cn.minims.who.dto.Dtos.SaveResult;
import cn.minims.who.dto.Dtos.SectionSaveRequest;
import cn.minims.who.dto.Dtos.StyleSaveRequest;
import cn.minims.who.dto.Dtos.WorkbenchResponse;
import cn.minims.who.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "简历")
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final AuthUtil authUtil;

    public ResumeController(ResumeService resumeService, AuthUtil authUtil) {
        this.resumeService = resumeService;
        this.authUtil = authUtil;
    }

    @GetMapping
    public ApiResponse<List<ResumeSummary>> list() {
        return ApiResponse.ok(resumeService.list(authUtil.userId()));
    }

    @PostMapping
    public ApiResponse<ResumeDetail> create(@RequestBody CreateResumeRequest request) {
        return ApiResponse.ok(resumeService.create(authUtil.userId(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(resumeService.detail(authUtil.userId(), id));
    }

    @GetMapping("/{id}/workbench")
    public ApiResponse<WorkbenchResponse> workbench(@PathVariable long id) {
        return ApiResponse.ok(resumeService.workbench(authUtil.userId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResumeDetail> rename(@PathVariable long id, @Valid @RequestBody RenameRequest request) {
        return ApiResponse.ok(resumeService.rename(authUtil.userId(), id, request));
    }

    @PostMapping("/{id}/duplicate")
    public ApiResponse<ResumeDetail> duplicate(@PathVariable long id) {
        return ApiResponse.ok(resumeService.duplicate(authUtil.userId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        resumeService.delete(authUtil.userId(), id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/sections/{sectionType}")
    public ApiResponse<SaveResult> saveSection(@PathVariable long id, @PathVariable String sectionType,
                                               @Valid @RequestBody SectionSaveRequest request) {
        return ApiResponse.ok(resumeService.saveSection(authUtil.userId(), id, sectionType, request));
    }

    @PatchMapping("/{id}/module-prefs")
    public ApiResponse<SaveResult> savePrefs(@PathVariable long id, @Valid @RequestBody PrefsSaveRequest request) {
        return ApiResponse.ok(resumeService.savePrefs(authUtil.userId(), id, request));
    }

    @PostMapping("/{id}/modules")
    public ApiResponse<ResumeDetail> addModule(@PathVariable long id, @Valid @RequestBody ModuleAddRequest request) {
        return ApiResponse.ok(resumeService.addModule(authUtil.userId(), id, request));
    }

    @DeleteMapping("/{id}/modules/{sectionType}")
    public ApiResponse<ResumeDetail> removeModule(@PathVariable long id, @PathVariable String sectionType,
                                                  @RequestParam int version) {
        return ApiResponse.ok(resumeService.removeModule(authUtil.userId(), id, sectionType, version));
    }

    @PutMapping("/{id}/style")
    public ApiResponse<ResumeDetail> saveStyle(@PathVariable long id, @Valid @RequestBody StyleSaveRequest request) {
        return ApiResponse.ok(resumeService.saveStyle(authUtil.userId(), id, request));
    }
}
