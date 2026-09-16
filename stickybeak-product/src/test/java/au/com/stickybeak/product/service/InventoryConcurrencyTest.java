package au.com.stickybeak.product.service;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.product.dto.StockDeductRequest;
import au.com.stickybeak.product.dto.StockItemDTO;
import au.com.stickybeak.product.entity.StockHold;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.StockHoldMapper;
import au.com.stickybeak.product.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryConcurrencyTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private StockHoldMapper stockHoldMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private DefaultRedisScript<Long> deductStockScript;

    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);

        inventoryService = new InventoryServiceImpl(
                productMapper,
                stockHoldMapper,
                stringRedisTemplate,
                redissonClient,
                deductStockScript
        );
    }

    @Test
    void testConcurrencyAntiOverselling_exactlyInitialStockSucceeds() throws Exception {
        final int initialStock = 5;
        final int concurrentRequests = 20;
        final AtomicInteger remainingStock = new AtomicInteger(initialStock);

        // Simulate atomic Redis Lua script deduction
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenAnswer(invocation -> {
            int current = remainingStock.get();
            while (current > 0) {
                if (remainingStock.compareAndSet(current, current - 1)) {
                    return 1L; // Successfully deducted in Redis
                }
                current = remainingStock.get();
            }
            return 0L; // Out of stock
        });

        // Simulate MySQL optimistic update
        when(productMapper.deductStockOptimistic(eq(1000001L), eq(1))).thenAnswer(invocation -> 1);

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start simultaneously
                    StockDeductRequest req = new StockDeductRequest(
                            "SO_CONC_" + index,
                            List.of(new StockItemDTO(1000001L, 1))
                    );
                    inventoryService.deductStock(req);
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 20 threads at once
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(true, completed, "All concurrent tasks should complete within timeout");
        // Strict anti-overselling assertions
        assertEquals(5, successCount.get(), "Exactly 5 requests should succeed (equal to initial stock)");
        assertEquals(15, failCount.get(), "Exactly 15 requests should be rejected with insufficient stock");
        assertEquals(0, remainingStock.get(), "Remaining stock should be strictly 0, zero oversell");

        // Verify MySQL was only called 5 times
        verify(productMapper, times(5)).deductStockOptimistic(eq(1000001L), eq(1));
        // Verify 5 stock hold records inserted
        verify(stockHoldMapper, times(5)).insert(any(StockHold.class));
    }
}
