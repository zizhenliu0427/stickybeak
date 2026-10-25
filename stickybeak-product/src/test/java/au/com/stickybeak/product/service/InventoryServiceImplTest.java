package au.com.stickybeak.product.service;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.StockDeductRequest;
import au.com.stickybeak.product.dto.StockItemDTO;
import au.com.stickybeak.product.entity.StockHold;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.StockHoldMapper;
import au.com.stickybeak.product.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Mock
    private DefaultRedisScript<Long> restoreReservationScript;

    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        inventoryService = new InventoryServiceImpl(productMapper, stockHoldMapper, stringRedisTemplate,
                redissonClient, deductStockScript, incrementIfExistsScript, restoreReservationScript);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 扣减脚本：KEYS = [库存, 订单账本]，ARGV = [数量, 商品 ID, 账本 TTL] */
    private void stubDeduct(long result) {
        when(stringRedisTemplate.execute(eq(deductStockScript), anyList(), any(), any(), any())).thenReturn(result);
    }

    /** 模拟 @Transactional：执行后按是否抛异常触发提交 / 回滚回调 */
    private RuntimeException runInTransaction(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        RuntimeException failure = null;
        try {
            action.run();
        } catch (RuntimeException e) {
            failure = e;
        }
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        int status = failure == null ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK;
        synchronizations.forEach(sync -> sync.afterCompletion(status));
        return failure;
    }

    @Test
    void deductStock_success() throws Exception {
        stubDeduct(1L);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(productMapper.deductStockOptimistic(1001L, 2)).thenReturn(1);

        StockDeductRequest req = new StockDeductRequest("SO20260916001", List.of(new StockItemDTO(1001L, 2)));
        assertNull(runInTransaction(() -> inventoryService.deductStock(req)));

        // 预扣同时记入订单账本
        verify(stringRedisTemplate).execute(deductStockScript,
                List.of("product:stock:1001", "stock:reservation:SO20260916001"), "2", "1001", "3600");
        verify(productMapper, times(1)).deductStockOptimistic(1001L, 2);
        verify(stockHoldMapper, times(1)).insert(any(StockHold.class));
        verify(rLock, times(1)).unlock();
        // 提交成功不归还
        verify(stringRedisTemplate, never()).execute(eq(restoreReservationScript), anyList(), any());
    }

    @Test
    void deductStock_insufficientInRedis_throwsException() {
        stubDeduct(0L);

        StockDeductRequest req = new StockDeductRequest("SO20260916002", List.of(new StockItemDTO(1001L, 5)));
        BusinessException ex = assertThrows(BusinessException.class, () -> inventoryService.deductStock(req));

        assertEquals(ResultCode.INSUFFICIENT_STOCK.getCode(), ex.getCode());
        verify(productMapper, never()).deductStockOptimistic(anyLong(), anyInt());
    }

    @Test
    void deductStock_insufficientInDb_rollsBackAndRestoresRedisFromLedger() throws Exception {
        stubDeduct(1L);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        // DB update returns 0 affected rows (stock condition failed)
        when(productMapper.deductStockOptimistic(1001L, 10)).thenReturn(0);

        StockDeductRequest req = new StockDeductRequest("SO20260916003", List.of(new StockItemDTO(1001L, 10)));
        RuntimeException failure = runInTransaction(() -> inventoryService.deductStock(req));

        assertInstanceOf(BusinessException.class, failure);
        assertEquals(ResultCode.INSUFFICIENT_STOCK.getCode(), ((BusinessException) failure).getCode());
        // 事务回滚后按账本归还 Redis（MySQL 由事务回滚），不再逐件手工回补
        verify(stringRedisTemplate, times(1)).execute(restoreReservationScript,
                List.of("stock:reservation:SO20260916003"), "product:stock:");
        verify(stringRedisTemplate, never()).execute(eq(incrementIfExistsScript), anyList(), any());
        verify(productMapper, never()).rollbackStockOptimistic(anyLong(), anyInt());
        verify(rLock, times(1)).unlock();
    }

    @Test
    void deductStock_secondItemFails_wholeOrderRestoredOnce() throws Exception {
        // 第一件 Redis + MySQL 都扣了，第二件 Redis 不足：本地事务回滚撤销第一件的 MySQL，账本一次性归还 Redis
        when(stringRedisTemplate.execute(eq(deductStockScript), anyList(), any(), any(), any())).thenReturn(1L, 0L);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(productMapper.deductStockOptimistic(1001L, 1)).thenReturn(1);

        StockDeductRequest req = new StockDeductRequest("SO20260916010",
                List.of(new StockItemDTO(1002L, 1), new StockItemDTO(1001L, 1)));
        assertNotNull(runInTransaction(() -> inventoryService.deductStock(req)));

        verify(productMapper, never()).deductStockOptimistic(eq(1002L), anyInt());
        verify(stockHoldMapper, never()).delete(any());
        verify(stringRedisTemplate, times(1)).execute(eq(restoreReservationScript), anyList(), any());
    }

    @Test
    void deductStock_lockTimeout_failsAndRestoresOnRollback() throws Exception {
        stubDeduct(1L);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        StockDeductRequest req = new StockDeductRequest("SO20260916011", List.of(new StockItemDTO(1001L, 1)));
        RuntimeException failure = runInTransaction(() -> inventoryService.deductStock(req));

        assertEquals(ResultCode.CONFLICT.getCode(), ((BusinessException) failure).getCode());
        verify(productMapper, never()).deductStockOptimistic(anyLong(), anyInt());
        verify(stringRedisTemplate).execute(eq(restoreReservationScript), anyList(), any());
    }

    @Test
    void restoreRedisReservation_returnsRestoredProductCount() {
        when(stringRedisTemplate.execute(restoreReservationScript,
                List.of("stock:reservation:SO20260916012"), "product:stock:")).thenReturn(2L, 0L);

        assertEquals(2, inventoryService.restoreRedisReservation("SO20260916012"));
        // 第二次调用（全局回滚 hook 重复触发）账本已删除，不会重复归还
        assertEquals(0, inventoryService.restoreRedisReservation("SO20260916012"));
    }

    @Test
    void deductStock_productMissing_luaMinusOne_fails() {
        // key 不存在 → 预热时商品也不存在 → 重试仍返回 -1，必须视为失败
        stubDeduct(-1L);
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
