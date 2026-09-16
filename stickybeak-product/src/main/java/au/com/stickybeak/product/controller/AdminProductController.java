package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.AdminProductPageQuery;
import au.com.stickybeak.product.dto.ProductPriceUpdateRequest;
import au.com.stickybeak.product.dto.ProductStatusUpdateRequest;
import au.com.stickybeak.product.dto.ProductStockUpdateRequest;
import au.com.stickybeak.product.service.AdminProductService;
import au.com.stickybeak.product.vo.AdminProductVO;
import au.com.stickybeak.product.vo.StockAlertSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products/admin")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
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
