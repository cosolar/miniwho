package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.Dtos.ExportPayloadVo;
import cn.minims.who.service.PdfExportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public/exports")
public class PublicExportController {

    private final PdfExportService pdfExportService;

    public PublicExportController(PdfExportService pdfExportService) {
        this.pdfExportService = pdfExportService;
    }

    @GetMapping("/{token}")
    public ApiResponse<ExportPayloadVo> payload(@PathVariable String token) {
        return ApiResponse.ok(pdfExportService.payload(token));
    }
}
