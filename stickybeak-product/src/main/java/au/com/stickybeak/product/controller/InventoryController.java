package au.com.stickybeak.product.controller;

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

    @PostMapping("/deduct")
    public Result<Void> deductStock(@Valid @RequestBody StockDeductRequest req) {
        inventoryService.deductStock(req);
        return Result.ok();
    }

    @PostMapping("/rollback")
    public Result<Void> rollbackStock(@Valid @RequestBody StockRollbackRequest req) {
        inventoryService.rollbackStock(req.getOrderNo());
        return Result.ok();
    }

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
