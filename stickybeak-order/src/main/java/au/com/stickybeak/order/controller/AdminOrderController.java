package au.com.stickybeak.order.controller;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.dto.AdminOrderPageQuery;
import au.com.stickybeak.order.dto.AdminOrderStatusUpdateRequest;
import au.com.stickybeak.order.service.AdminOrderService;
import au.com.stickybeak.order.vo.AdminOrderVO;
import au.com.stickybeak.order.vo.AnalyticsSummaryVO;
import au.com.stickybeak.order.vo.SalesTrendItemVO;
import au.com.stickybeak.order.vo.TopProductVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders/admin")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping("/analytics/summary")
    public Result<AnalyticsSummaryVO> getAnalyticsSummary(
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        checkAdminRole(roles);
        return Result.ok(adminOrderService.getAnalyticsSummary());
    }

    @GetMapping("/analytics/trend")
    public Result<List<SalesTrendItemVO>> getSalesTrend(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestParam(defaultValue = "7") int days) {
        checkAdminRole(roles);
        return Result.ok(adminOrderService.getSalesTrend(days));
    }

    @GetMapping("/analytics/top-products")
    public Result<List<TopProductVO>> getTopProducts(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestParam(defaultValue = "10") int limit) {
        checkAdminRole(roles);
        return Result.ok(adminOrderService.getTopProducts(limit));
    }

    @GetMapping("/page")
    public Result<Page<AdminOrderVO>> pageOrders(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @ModelAttribute AdminOrderPageQuery query) {
        checkAdminRole(roles);
        return Result.ok(adminOrderService.pageOrders(query));
    }

    @PutMapping("/{orderNo}/status")
    public Result<Void> updateOrderStatus(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable String orderNo,
            @Valid @RequestBody AdminOrderStatusUpdateRequest req) {
        checkAdminRole(roles);
        Long adminId = parseUserId(userIdHeader);
        adminOrderService.updateOrderStatus(orderNo, req, adminId);
        return Result.ok();
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

    private Long parseUserId(String userIdHeader) {
        if (StringUtils.hasText(userIdHeader)) {
            try {
                return Long.valueOf(userIdHeader.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }
}
