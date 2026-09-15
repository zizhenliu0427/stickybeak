package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.product.service.TagService;
import au.com.stickybeak.product.vo.TagVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public Result<List<TagVO>> listAll() {
        return Result.ok(tagService.listAll());
    }
}
