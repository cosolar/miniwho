package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.MediaDtos.ItemMediaVo;
import cn.minims.who.service.ItemMediaService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

/**
 * 条目图片上传：教育背景与工作经历的院校 / 公司 logo。
 * 返回的 key 由编辑器写进对应条目的 fields.logo，读取走 /api/public/media，
 * 换图或移除只改 content，文件在内容落库后由 ItemMediaService.reconcile 回收。
 */
@Tag(name = "简历")
@RestController
@RequestMapping("/api/resumes")
public class ItemMediaController {

    private final ItemMediaService itemMediaService;
    private final AuthUtil authUtil;

    public ItemMediaController(ItemMediaService itemMediaService, AuthUtil authUtil) {
        this.itemMediaService = itemMediaService;
        this.authUtil = authUtil;
    }

    @PostMapping("/{id}/media")
    public ApiResponse<ItemMediaVo> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(itemMediaService.upload(authUtil.userId(), id, file));
    }
}
