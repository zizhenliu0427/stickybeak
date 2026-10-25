package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.AdminProductPageQuery;
import au.com.stickybeak.product.dto.ProductPriceUpdateRequest;
import au.com.stickybeak.product.dto.ProductStatusUpdateRequest;
import au.com.stickybeak.product.dto.ProductStockUpdateRequest;
import au.com.stickybeak.product.dto.ProductCreateRequest;
import au.com.stickybeak.product.dto.UploadUrlRequest;
import au.com.stickybeak.product.service.AdminProductService;
import au.com.stickybeak.product.service.StorageService;
import au.com.stickybeak.product.search.ProductIndexer;
import au.com.stickybeak.product.vo.AdminProductVO;
import au.com.stickybeak.product.vo.StockAlertSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/products/admin")
public class AdminProductController {

    private final AdminProductService adminProductService;
    /** 关闭 ES（stickybeak.search.elasticsearch.enabled=false）时为空 */
    private final Optional<ProductIndexer> productIndexer;
    private final StorageService storageService;

    public AdminProductController(AdminProductService adminProductService,
                                  Optional<ProductIndexer> productIndexer,
                                  StorageService storageService) {
        this.adminProductService = adminProductService;
        this.productIndexer = productIndexer;
        this.storageService = storageService;
    }

    @GetMapping("/page")
    public Result<Page<AdminProductVO>> pageProducts(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @ModelAttribute AdminProductPageQuery query) {
        checkAdminRole(roles);
        return Result.ok(adminProductService.pageProducts(query));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusUpdateRequest req) {
        checkAdminRole(roles);
        adminProductService.updateStatus(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/stock")
    public Result<Void> updateStock(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @PathVariable Long id,
            @Valid @RequestBody ProductStockUpdateRequest req) {
        checkAdminRole(roles);
        adminProductService.updateStock(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/price")
    public Result<Void> updatePrice(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @PathVariable Long id,
            @Valid @RequestBody ProductPriceUpdateRequest req) {
        checkAdminRole(roles);
        adminProductService.updatePrice(id, req);
        return Result.ok();
    }

    @GetMapping("/stock-alerts")
    public Result<StockAlertSummaryVO> getStockAlerts(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestParam(defaultValue = "10") int threshold) {
        checkAdminRole(roles);
        return Result.ok(adminProductService.getStockAlerts(threshold));
    }

    /** 全量重建 ES 索引（首次导入/索引漂移兜底） */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex(
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        checkAdminRole(roles);
        ProductIndexer indexer = productIndexer.orElseThrow(() ->
                new BusinessException(ResultCode.BAD_REQUEST, "Elasticsearch search is disabled"));
        return Result.ok(Map.of("indexed", indexer.reindexAll()));
    }

    /** 申请图片预签名上传 URL（浏览器直传 MinIO） */
    @PostMapping("/upload-url")
    public Result<StorageService.PresignedUpload> createUploadUrl(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @Valid @RequestBody UploadUrlRequest req) {
        checkAdminRole(roles);
        return Result.ok(storageService.createUploadUrl(req.filename(), req.contentType()));
    }

    /** 新建商品（含图片/标签），返回新商品 id */
    @PostMapping
    public Result<Map<String, Object>> createProduct(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @Valid @RequestBody ProductCreateRequest req) {
        checkAdminRole(roles);
        Long id = adminProductService.createProduct(req);
        return Result.ok(Map.of("id", id));
    }

    private void checkAdminRole(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Admin permission required");
        }
        String normalized = rolesHeader.toLowerCase();
        if (!normalized.contains("admin") && !normalized.contains("sysadmin")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Admin permission required");
        }
    }
}
