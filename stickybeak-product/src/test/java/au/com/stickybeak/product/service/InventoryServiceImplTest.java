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

    @Mock
    private DefaultRedisScript<Long> incrementIfExistsScript;

    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        inventoryService = new InventoryServiceImpl(productMapper, stockHoldMapper, stringRedisTemplate,
                redissonClient, deductStockScript, incrementIfExistsScript);
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
        // 回补走"key 存在才加"的脚本，key 过期时不会凭空写出错误库存
        verify(stringRedisTemplate, times(1)).execute(incrementIfExistsScript, List.of("product:stock:1001"), "10");
        verify(valueOperations, never()).increment(anyString(), anyLong());
        verify(rLock, times(1)).unlock();
    }

    @Test
    void deductStock_productMissing_luaMinusOne_fails() {
        // key 不存在 → 预热时商品也不存在 → 重试仍返回 -1，必须视为失败
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(-1L);
        when(valueOperations.get("product:stock:9999")).thenReturn(null);
        when(productMapper.selectById(9999L)).thenReturn(null);

        StockDeductRequest req = new StockDeductRequest("SO20260916006", List.of(new StockItemDTO(9999L, 1)));
        BusinessException ex = assertThrows(BusinessException.class, () -> inventoryService.deductStock(req));

        assertEquals(ResultCode.INSUFFICIENT_STOCK.getCode(), ex.getCode());
        verify(productMapper, never()).deductStockOptimistic(anyLong(), anyInt());
        verify(stockHoldMapper, never()).insert(any(StockHold.class));
    }

    @Test
    void rollbackStock_success() {
        StockHold hold = new StockHold(1001L, "SO20260916004", 3, StockHold.STATUS_HELD, null);
        hold.setId(1L);
        when(stockHoldMapper.selectList(any())).thenReturn(List.of(hold));
        when(stockHoldMapper.transitStatus(1L, StockHold.STATUS_HELD, StockHold.STATUS_RELEASED)).thenReturn(1);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        inventoryService.rollbackStock("SO20260916004");

        verify(productMapper, times(1)).rollbackStockOptimistic(1001L, 3);
        verify(stringRedisTemplate, times(1)).execute(incrementIfExistsScript, List.of("product:stock:1001"), "3");
        verify(rLock, times(1)).unlock();
    }

    @Test
    void rollbackStock_holdAlreadyReleased_skipsStockChanges() {
        StockHold hold = new StockHold(1001L, "SO20260916007", 3, StockHold.STATUS_HELD, null);
        hold.setId(2L);
        when(stockHoldMapper.selectList(any())).thenReturn(List.of(hold));
        // 另一个回滚请求先一步把 HELD 改走了
        when(stockHoldMapper.transitStatus(2L, StockHold.STATUS_HELD, StockHold.STATUS_RELEASED)).thenReturn(0);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        inventoryService.rollbackStock("SO20260916007");

        verify(productMapper, never()).rollbackStockOptimistic(anyLong(), anyInt());
        verify(stringRedisTemplate, never()).execute(eq(incrementIfExistsScript), anyList(), any());
        verify(rLock, times(1)).unlock();
    }

    @Test
    void confirmStock_success() {
        StockHold hold = new StockHold(1001L, "SO20260916005", 2, StockHold.STATUS_HELD, null);
        hold.setId(3L);
        when(stockHoldMapper.selectList(any())).thenReturn(List.of(hold));

        inventoryService.confirmStock("SO20260916005");

        verify(stockHoldMapper, times(1)).transitStatus(3L, StockHold.STATUS_HELD, StockHold.STATUS_CONFIRMED);
        verify(stockHoldMapper, never()).updateById(any(StockHold.class));
    }

    @Test
    void restockConfirmed_releasesConfirmedHoldsOnly() {
        StockHold hold = new StockHold(1001L, "SO20260916008", 2, StockHold.STATUS_CONFIRMED, null);
        hold.setId(4L);
        when(stockHoldMapper.selectList(any())).thenReturn(List.of(hold));
        when(stockHoldMapper.transitStatus(4L, StockHold.STATUS_CONFIRMED, StockHold.STATUS_RELEASED)).thenReturn(1);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        inventoryService.restockConfirmed("SO20260916008");

        verify(stockHoldMapper).transitStatus(4L, StockHold.STATUS_CONFIRMED, StockHold.STATUS_RELEASED);
        verify(productMapper).rollbackStockOptimistic(1001L, 2);
        verify(stringRedisTemplate).execute(incrementIfExistsScript, List.of("product:stock:1001"), "2");
    }

    @Test
    void restockConfirmed_duplicateMessageDoesNothing() {
        when(stockHoldMapper.selectList(any())).thenReturn(List.of());

        inventoryService.restockConfirmed("SO20260916009");

        verify(productMapper, never()).rollbackStockOptimistic(anyLong(), anyInt());
    }
}
