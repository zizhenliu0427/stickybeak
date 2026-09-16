package au.com.stickybeak.product.service;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.StockDeductRequest;
import au.com.stickybeak.product.dto.StockItemDTO;
import au.com.stickybeak.product.entity.StockHold;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.StockHoldMapper;
import au.com.stickybeak.product.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

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

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @Test
    void deductStock_success() throws Exception {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(1L);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(productMapper.deductStockOptimistic(1001L, 2)).thenReturn(1);

        StockDeductRequest req = new StockDeductRequest("SO20260916001", List.of(new StockItemDTO(1001L, 2)));
        assertDoesNotThrow(() -> inventoryService.deductStock(req));

        verify(productMapper, times(1)).deductStockOptimistic(1001L, 2);
        verify(stockHoldMapper, times(1)).insert(any(StockHold.class));
        verify(rLock, times(1)).unlock();
    }

    @Test
    void deductStock_insufficientInRedis_throwsException() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(0L);

        StockDeductRequest req = new StockDeductRequest("SO20260916002", List.of(new StockItemDTO(1001L, 5)));
        BusinessException ex = assertThrows(BusinessException.class, () -> inventoryService.deductStock(req));

        assertEquals(ResultCode.INSUFFICIENT_STOCK.getCode(), ex.getCode());
        verify(productMapper, never()).deductStockOptimistic(anyLong(), anyInt());
    }

    @Test
    void deductStock_insufficientInDb_rollsBack() throws Exception {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(1L);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        // DB update returns 0 affected rows (stock condition failed)
        when(productMapper.deductStockOptimistic(1001L, 10)).thenReturn(0);

        StockDeductRequest req = new StockDeductRequest("SO20260916003", List.of(new StockItemDTO(1001L, 10)));
        BusinessException ex = assertThrows(BusinessException.class, () -> inventoryService.deductStock(req));

        assertEquals(ResultCode.INSUFFICIENT_STOCK.getCode(), ex.getCode());
        verify(valueOperations, times(1)).increment("product:stock:1001", 10);
        verify(rLock, times(1)).unlock();
    }

    @Test
    void rollbackStock_success() {
        StockHold hold = new StockHold(1001L, "SO20260916004", 3, StockHold.STATUS_HELD, null);
        when(stockHoldMapper.selectList(any())).thenReturn(List.of(hold));
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        inventoryService.rollbackStock("SO20260916004");

        verify(productMapper, times(1)).rollbackStockOptimistic(1001L, 3);
        verify(valueOperations, times(1)).increment("product:stock:1001", 3);
        verify(stockHoldMapper, times(1)).updateById(hold);
        assertEquals(StockHold.STATUS_RELEASED, hold.getStatus());
        verify(rLock, times(1)).unlock();
    }

    @Test
    void confirmStock_success() {
        StockHold hold = new StockHold(1001L, "SO20260916005", 2, StockHold.STATUS_HELD, null);
        when(stockHoldMapper.selectList(any())).thenReturn(List.of(hold));

        inventoryService.confirmStock("SO20260916005");

        verify(stockHoldMapper, times(1)).updateById(hold);
        assertEquals(StockHold.STATUS_CONFIRMED, hold.getStatus());
    }
}
