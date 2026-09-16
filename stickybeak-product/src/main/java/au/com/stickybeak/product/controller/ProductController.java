package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.product.dto.ProductQuery;
import au.com.stickybeak.product.service.ProductService;
import au.com.stickybeak.product.vo.ProductListVO;
import au.com.stickybeak.product.vo.ProductVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Result<ProductListVO> listProducts(@ModelAttribute ProductQuery query) {
        return Result.ok(productService.listProducts(query));
    }

    @GetMapping("/featured")
    public Result<List<ProductVO>> listFeatured() {
        return Result.ok(productService.listFeatured());
    }

    @GetMapping("/{slug}")
    public Result<ProductVO> getBySlug(@PathVariable String slug) {
        return Result.ok(productService.getBySlug(slug));
    }

    @GetMapping("/id/{id}")
    public Result<ProductVO> getById(@PathVariable Long id) {
        return Result.ok(productService.getById(id));
    }

    @PostMapping("/batch")
    public Result<List<ProductVO>> listByIds(@RequestBody List<Long> ids) {
        return Result.ok(productService.listByIds(ids));
    }

    @GetMapping("/{slug}/related")
    public Result<List<ProductVO>> listRelated(@PathVariable String slug,
                                               @RequestParam(defaultValue = "4") int limit) {
        ProductVO product = productService.getBySlug(slug);
        return Result.ok(productService.listRelated(product.getCategory(), slug, limit));
    }
}
