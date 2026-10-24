package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.internal.InternalApi;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.product.dto.StockDeductRequest;
import au.com.stickybeak.product.dto.StockRollbackRequest;
import au.com.stickybeak.product.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products/stock")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 扣减 / 回滚 / 确认只允许订单服务调用；对外暴露会被用来锁死库存或绕过防超卖 */
    @InternalApi
    @PostMapping("/deduct")
    public Result<Void> deductStock(@Valid @RequestBody StockDeductRequest req) {
        inventoryService.deductStock(req);
        return Result.ok();
    }

    @InternalApi
    @PostMapping("/rollback")
    public Result<Void> rollbackStock(@Valid @RequestBody StockRollbackRequest req) {
        inventoryService.rollbackStock(req.getOrderNo());
        return Result.ok();
    }

    /** 已支付订单退款后回补库存 */
    @InternalApi
    @PostMapping("/restock")
    public Result<Void> restockStock(@Valid @RequestBody StockRollbackRequest req) {
        inventoryService.restockConfirmed(req.getOrderNo());
        return Result.ok();
    }

    @InternalApi
    @PostMapping("/confirm")
    public Result<Void> confirmStock(@Valid @RequestBody StockRollbackRequest req) {
        inventoryService.confirmStock(req.getOrderNo());
        return Result.ok();
    }

    @GetMapping("/{productId}")
    public Result<Integer> getStock(@PathVariable Long productId) {
        return Result.ok(inventoryService.getOrInitStock(productId));
    }
}
