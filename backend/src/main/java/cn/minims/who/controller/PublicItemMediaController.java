package cn.minims.who.controller;

import cn.minims.who.service.ItemMediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 条目图片（院校 / 公司 logo）的公开读取通道。
 *
 * <p>与头像不同，这里不校验签名：链接必须由前端从简历 content 里的 key 直接推导出来，
 * 否则编辑器保存时会把临时链接写回数据。随机 UUID 文件名本身就是取件凭证。
 */
@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public/media")
public class PublicItemMediaController {

    private final ItemMediaService itemMediaService;

    public PublicItemMediaController(ItemMediaService itemMediaService) {
        this.itemMediaService = itemMediaService;
    }

    @GetMapping("/{resumeId}/{name}")
    public ResponseEntity<Resource> itemMedia(@PathVariable long resumeId, @PathVariable String name) {
        return itemMediaService.serve("media/" + resumeId + "/" + name);
    }
}
