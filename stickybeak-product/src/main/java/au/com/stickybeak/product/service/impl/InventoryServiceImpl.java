package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.StockDeductRequest;
import au.com.stickybeak.product.dto.StockItemDTO;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.entity.StockHold;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.StockHoldMapper;
import au.com.stickybeak.product.service.InventoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);
    private static final String STOCK_KEY_PREFIX = "product:stock:";
    private static final String LOCK_KEY_PREFIX = "lock:product:stock:";

    private final ProductMapper productMapper;
    private final StockHoldMapper stockHoldMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final DefaultRedisScript<Long> deductStockScript;

    public InventoryServiceImpl(ProductMapper productMapper,
                                StockHoldMapper stockHoldMapper,
                                StringRedisTemplate stringRedisTemplate,
                                RedissonClient redissonClient,
                                DefaultRedisScript<Long> deductStockScript) {
        this.productMapper = productMapper;
        this.stockHoldMapper = stockHoldMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
        this.deductStockScript = deductStockScript;
    }

    @Override
    public void deductStock(StockDeductRequest req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Items cannot be empty");
        }

        // Sort items by productId in ascending order to prevent deadlocks across concurrent threads
        List<StockItemDTO> sortedItems = req.getItems().stream()
                .sorted(Comparator.comparing(StockItemDTO::getProductId))
                .collect(Collectors.toList());

        List<StockItemDTO> successfullyDeductedItems = new ArrayList<>();

        for (StockItemDTO item : sortedItems) {
            Long productId = item.getProductId();
            Integer qty = item.getQty();
            String stockKey = STOCK_KEY_PREFIX + productId;

            // -------------------------------------------------------------
            // 防线 1: Redis Lua 脚本原子预扣减 (抗超高并发冲击)
            // -------------------------------------------------------------
            Long luaResult = executeLuaDeduct(stockKey, productId, qty);
            if (luaResult == null || luaResult == 0) {
                log.warn("Stock pre-deduction failed in Redis for product: {}, requested: {}", productId, qty);
                compensate(req.getOrderNo(), successfullyDeductedItems);
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Product " + productId + " is out of stock");
            }

            // -------------------------------------------------------------
            // 防线 2: Redisson 分布式锁 (保障多实例节点并发安全)
            // -------------------------------------------------------------
            String lockKey = LOCK_KEY_PREFIX + productId;
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = false;
            try {
                acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while acquiring lock for product {}", productId);
            }

            if (!acquired) {
                log.warn("Failed to acquire Redisson lock for product: {}", productId);
                // Revert this item in Redis
                stringRedisTemplate.opsForValue().increment(stockKey, qty);
                compensate(req.getOrderNo(), successfullyDeductedItems);
                throw new BusinessException(ResultCode.CONFLICT, "Server is busy, please retry");
            }

            try {
                // ---------------------------------------------------------
                // 防线 3: MySQL 乐观条件更新 (stock >= qty, 数据库兜底)
                // ---------------------------------------------------------
                int affectedRows = productMapper.deductStockOptimistic(productId, qty);
                if (affectedRows <= 0) {
                    log.error("MySQL optimistic stock deduction failed for product {}, insufficient DB stock", productId);
                    // Revert this item in Redis
                    stringRedisTemplate.opsForValue().increment(stockKey, qty);
                    compensate(req.getOrderNo(), successfullyDeductedItems);
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Insufficient stock for product " + productId);
                }

                // Record stock hold
                StockHold hold = new StockHold(
                        productId,
                        req.getOrderNo(),
                        qty,
                        StockHold.STATUS_HELD,
                        LocalDateTime.now().plusMinutes(30)
                );
                stockHoldMapper.insert(hold);
                successfullyDeductedItems.add(item);
                log.info("Successfully deducted stock for order {}, product {}, qty {}", req.getOrderNo(), productId, qty);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    private Long executeLuaDeduct(String stockKey, Long productId, Integer qty) {
        Long result = stringRedisTemplate.execute(
                deductStockScript,
                Collections.singletonList(stockKey),
                String.valueOf(qty)
        );

        if (result != null && result == -1) {
            // Redis key does not exist yet; warm it up from MySQL and retry
            getOrInitStock(productId);
            result = stringRedisTemplate.execute(
                    deductStockScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(qty)
            );
        }
        return result;
    }

    private void compensate(String orderNo, List<StockItemDTO> items) {
        if (items.isEmpty()) {
            return;
        }
        log.warn("Compensating previously deducted items for order: {}", orderNo);
        for (StockItemDTO item : items) {
            Long productId = item.getProductId();
            Integer qty = item.getQty();
            try {
                productMapper.rollbackStockOptimistic(productId, qty);
                stringRedisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + productId, qty);
            } catch (Exception e) {
                log.error("Error compensating product {} for order {}: {}", productId, orderNo, e.getMessage());
            }
        }
        // Remove any holds created for this order
        stockHoldMapper.delete(new LambdaQueryWrapper<StockHold>().eq(StockHold::getOrderNo, orderNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackStock(String orderNo) {
        log.info("Executing stock rollback for order: {}", orderNo);
        List<StockHold> holds = stockHoldMapper.selectList(
                new LambdaQueryWrapper<StockHold>()
                        .eq(StockHold::getOrderNo, orderNo)
                        .eq(StockHold::getStatus, StockHold.STATUS_HELD)
        );

        if (holds.isEmpty()) {
            log.info("No held stock found for order {}, rollback skipped or already processed", orderNo);
            return;
        }

        for (StockHold hold : holds) {
            Long productId = hold.getProductId();
            Integer qty = hold.getQty();
            String lockKey = LOCK_KEY_PREFIX + productId;
            RLock lock = redissonClient.getLock(lockKey);
            try {
                lock.lock(10, TimeUnit.SECONDS);
                // 1. Rollback MySQL
                productMapper.rollbackStockOptimistic(productId, qty);

                // 2. Mark hold status as RELEASED
                hold.setStatus(StockHold.STATUS_RELEASED);
                stockHoldMapper.updateById(hold);

                // 3. Rollback Redis stock
                stringRedisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + productId, qty);
                log.info("Stock rolled back for order {}, product {}, qty {}", orderNo, productId, qty);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmStock(String orderNo) {
        log.info("Confirming stock deduction for order: {}", orderNo);
        List<StockHold> holds = stockHoldMapper.selectList(
                new LambdaQueryWrapper<StockHold>()
                        .eq(StockHold::getOrderNo, orderNo)
                        .eq(StockHold::getStatus, StockHold.STATUS_HELD)
        );

        for (StockHold hold : holds) {
            hold.setStatus(StockHold.STATUS_CONFIRMED);
            stockHoldMapper.updateById(hold);
        }
    }

    @Override
    public Integer getOrInitStock(Long productId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String cached = stringRedisTemplate.opsForValue().get(stockKey);
        if (cached != null) {
            try {
                return Integer.parseInt(cached.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            return 0;
        }

        Integer stock = product.getStock() != null ? product.getStock() : 0;
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(stock), Duration.ofDays(7));
        return stock;
    }
}
