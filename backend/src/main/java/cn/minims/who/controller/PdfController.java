package cn.minims.who.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.ExportDtos.PdfStatusVo;
import cn.minims.who.service.PdfExportService;
import cn.minims.who.service.PdfExportService.PdfFile;
import cn.minims.who.util.Json;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "导出")
@RestController
public class PdfController {

    private final PdfExportService pdfExportService;
    private final AuthUtil authUtil;
    private final Json json;

    public PdfController(PdfExportService pdfExportService, AuthUtil authUtil, Json json) {
        this.pdfExportService = pdfExportService;
        this.authUtil = authUtil;
        this.json = json;
    }

    @GetMapping("/api/pdf/status")
    public ApiResponse<PdfStatusVo> status() {
        return ApiResponse.ok(pdfExportService.status());
    }

    @GetMapping("/api/resumes/{id}/pdf")
    public ResponseEntity<Resource> export(@PathVariable long id,
                                           @RequestParam(required = false) String templateCode,
                                           @RequestParam(required = false) String filter,
                                           @RequestParam(required = false) Integer fontSizeStep,
                                           @RequestParam(required = false) Integer spacingStep,
                                           @RequestParam(required = false) Boolean showBrandFooter) {
        ObjectNode overrides = json.object();
        if (filter != null && !filter.isBlank()) {
            overrides.put("filter", filter);
        }
        if (fontSizeStep != null) {
            overrides.put("fontSizeStep", fontSizeStep);
        }
        if (spacingStep != null) {
            overrides.put("spacingStep", spacingStep);
        }
        if (showBrandFooter != null) {
            overrides.put("showBrandFooter", showBrandFooter);
        }
        PdfFile file = pdfExportService.exportFor(authUtil.userId(), id, templateCode, overrides);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resume.pdf\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.bytes().length)
                .body(new ByteArrayResource(file.bytes()));
    }
}
