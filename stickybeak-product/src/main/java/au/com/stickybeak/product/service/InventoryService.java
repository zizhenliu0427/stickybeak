package au.com.stickybeak.product.service;

import au.com.stickybeak.product.dto.StockDeductRequest;

public interface InventoryService {

    /**
     * Deduct stock using the 3-layer anti-overselling defense.
     * Layer 1: Redis Lua atomic script
     * Layer 2: Redisson distributed lock (ordered by productId)
     * Layer 3: MySQL optimistic conditional update & t_stock_hold logging
     * Runs as one local transaction (one Seata AT branch when called inside the checkout global transaction).
     */
    void deductStock(StockDeductRequest req);

    /**
     * Return the order's Redis pre-deductions recorded in its reservation ledger, then drop the ledger.
     * Called when the stock transaction did not commit, locally or via a Seata global rollback. Idempotent.
     *
     * @return number of products whose Redis stock was restored
     */
    int restoreRedisReservation(String orderNo);

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
     * Idempotent restock after a paid order is refunded.
     * Moves CONFIRMED holds to RELEASED and returns their quantity to MySQL and Redis.
     */
    void restockConfirmed(String orderNo);

    /**
     * Warm up or query stock in Redis for a product.
     */
    Integer getOrInitStock(Long productId);
}
