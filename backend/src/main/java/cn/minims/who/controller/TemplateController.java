package cn.minims.who.controller;

import com.fasterxml.jackson.databind.JsonNode;
import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.Dtos.TemplateShareRequest;
import cn.minims.who.dto.Dtos.TemplateVo;
import cn.minims.who.entity.Template;
import cn.minims.who.service.TemplateService;
import cn.minims.who.template.TemplatePackageCodec;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "模板")
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private final TemplateService templateService;
    private final TemplatePackageCodec codec;
    private final AuthUtil authUtil;

    public TemplateController(TemplateService templateService, TemplatePackageCodec codec, AuthUtil authUtil) {
        this.templateService = templateService;
        this.codec = codec;
        this.authUtil = authUtil;
    }

    /** 官方区列表，未登录也能看。 */
    @GetMapping
    public ApiResponse<List<TemplateVo>> list() {
        return ApiResponse.ok(templateService.listActive());
    }

    /** 我导入的模板包；/api/templates/** 在拦截器里是公开路径，这里靠 userId 强制登录。 */
    @GetMapping("/mine")
    public ApiResponse<List<TemplateVo>> mine() {
        return ApiResponse.ok(templateService.mine(authUtil.userId()));
    }

    @PostMapping("/import")
    public ApiResponse<TemplateVo> importPackage(@RequestBody JsonNode body) {
        return ApiResponse.ok(templateService.importPackage(authUtil.userId(), body));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> remove(@PathVariable String code) {
        templateService.remove(code, authUtil.userId(), authUtil.isAdmin());
        return ApiResponse.ok();
    }

    /** 分享到模板中心 / 取消分享，并可同时设置别人能否导出这个包。 */
    @PutMapping("/{code}/share")
    public ApiResponse<TemplateVo> share(@PathVariable String code,
                                         @Valid @RequestBody TemplateShareRequest request) {
        return ApiResponse.ok(templateService.share(code, authUtil.userId(), authUtil.isAdmin(),
                Boolean.TRUE.equals(request.shared()), !Boolean.FALSE.equals(request.exportable())));
    }

    /**
     * 导出包原文：官方包与「已分享且作者允许导出」的包对所有人开放，
     * 其余导入包只有作者本人与管理员能取（这时才需要身份，未登录直接 401）。
     */
    @GetMapping("/{code}/export")
    public ResponseEntity<String> export(@PathVariable String code) {
        Template template = templateService.findByCode(code);
        if (template == null) {
            throw BizException.notFound("模板不存在");
        }
        boolean official = template.getSource() == null || "builtin".equals(template.getSource());
        if (!official && !templateService.isPubliclyExportable(template)
                && !templateService.canExport(template, authUtil.userId(), authUtil.isAdmin())) {
            throw BizException.forbidden("作者没有开放这个模板包的导出");
        }
        if (template.getPkg() == null || template.getPkg().isBlank()) {
            throw BizException.notFound("该模板没有可导出的包文件");
        }
        String version = template.getVersion() == null || template.getVersion().isBlank()
                ? "1.0.0" : template.getVersion();
        String fileName = template.getCode() + "-" + version + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(JSON_UTF8)
                .body(codec.pretty(template.getPkg()));
    }

    @GetMapping("/{code}")
    public ApiResponse<TemplateVo> get(@PathVariable String code) {
        return ApiResponse.ok(templateService.getActive(code));
    }
}
