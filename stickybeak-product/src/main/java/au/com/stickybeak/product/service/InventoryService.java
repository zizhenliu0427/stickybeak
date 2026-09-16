package au.com.stickybeak.product.service;

import au.com.stickybeak.product.dto.StockDeductRequest;

public interface InventoryService {

    /**
     * Deduct stock using the 3-layer anti-overselling defense.
     * Layer 1: Redis Lua atomic script
     * Layer 2: Redisson distributed lock (ordered by productId)
     * Layer 3: MySQL optimistic conditional update & t_stock_hold logging
     */
    void deductStock(StockDeductRequest req);

    /**
     * Idempotent stock rollback for an order (e.g. timeout or cancellation).
     * Reverts MySQL stock & sales, updates t_stock_hold to RELEASED, increments Redis stock.
     */
    void rollbackStock(String orderNo);

    /**
     * Confirm stock deduction upon successful payment.
     * Updates t_stock_hold to CONFIRMED.
     */
    void confirmStock(String orderNo);

    /**
     * Warm up or query stock in Redis for a product.
     */
    Integer getOrInitStock(Long productId);
}
