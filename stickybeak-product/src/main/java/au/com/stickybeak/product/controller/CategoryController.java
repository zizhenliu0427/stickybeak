package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.product.service.CategoryService;
import au.com.stickybeak.product.vo.CategoryVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<CategoryVO>> listAll() {
        return Result.ok(categoryService.listAll());
    }

    @GetMapping("/{slug}")
    public Result<CategoryVO> getBySlug(@PathVariable String slug) {
        return Result.ok(categoryService.getBySlug(slug));
    }
}
