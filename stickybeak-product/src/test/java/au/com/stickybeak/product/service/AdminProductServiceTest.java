package au.com.stickybeak.product.service;

import au.com.stickybeak.product.dto.AdminProductPageQuery;
import au.com.stickybeak.product.dto.ProductPriceUpdateRequest;
import au.com.stickybeak.product.dto.ProductStatusUpdateRequest;
import au.com.stickybeak.product.dto.ProductStockUpdateRequest;
import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.mapper.ProductImageMapper;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.search.ProductChangedEvent;
import au.com.stickybeak.product.service.impl.AdminProductServiceImpl;
import au.com.stickybeak.product.vo.AdminProductVO;
import au.com.stickybeak.product.vo.StockAlertSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ProductImageMapper productImageMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminProductServiceImpl adminProductService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        adminProductService = new AdminProductServiceImpl(
                productMapper, categoryMapper, productImageMapper, stringRedisTemplate, eventPublisher);
    }

    @Test
    void testUpdateStatus() {
        Product p = new Product();
        p.setId(1001L);
        p.setStatus(0);
        when(productMapper.selectById(1001L)).thenReturn(p);

        ProductStatusUpdateRequest req = new ProductStatusUpdateRequest();
        req.setStatus(1);

        adminProductService.updateStatus(1001L, req);

        assertEquals(1, p.getStatus());
        verify(productMapper).updateById(p);
        verify(stringRedisTemplate).delete("product:id:1001");
        // 下架后通知搜索索引同步
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1001L));
    }

    @Test
    void testUpdateStock_Absolute() {
        Product p = new Product();
        p.setId(1001L);
        p.setStock(10);
        when(productMapper.selectById(1001L)).thenReturn(p);

        ProductStockUpdateRequest req = new ProductStockUpdateRequest();
        req.setStock(88);

        adminProductService.updateStock(1001L, req);

        verify(productMapper).setStock(1001L, 88);
        verify(productMapper, never()).updateById(any(Product.class));
        // Redis 缓存删除，下次扣减从 MySQL 预热
        verify(stringRedisTemplate).delete("product:stock:1001");
    }

    @Test
    void testUpdateStock_Delta() {
        Product p = new Product();
        p.setId(1001L);
        p.setStock(15);
        when(productMapper.selectById(1001L)).thenReturn(p);

        ProductStockUpdateRequest req = new ProductStockUpdateRequest();
        req.setDelta(25);

        adminProductService.updateStock(1001L, req);

        // 增量在 SQL 里原子计算，不再先读后写
        verify(productMapper).adjustStock(1001L, 25);
        verify(productMapper, never()).updateById(any(Product.class));
        verify(stringRedisTemplate).delete("product:stock:1001");
    }

    @Test
    void testUpdatePrice() {
        Product p = new Product();
        p.setId(1001L);
        p.setPrice(new BigDecimal("29.90"));
        when(productMapper.selectById(1001L)).thenReturn(p);

        ProductPriceUpdateRequest req = new ProductPriceUpdateRequest();
        req.setPrice(new BigDecimal("35.00"));

        adminProductService.updatePrice(1001L, req);

        assertEquals(new BigDecimal("35.00"), p.getPrice());
        verify(productMapper).updateById(p);
    }

    @Test
    void testGetStockAlerts() {
        Product p1 = new Product();
        p1.setId(1L);
        p1.setStock(0);

        Product p2 = new Product();
        p2.setId(2L);
        p2.setStock(2);

        Product p3 = new Product();
        p3.setId(3L);
        p3.setStock(8);

        when(productMapper.selectList(any())).thenReturn(List.of(p1, p2, p3));

        StockAlertSummaryVO summary = adminProductService.getStockAlerts(10);

        assertEquals(3, summary.getTotalAlerts());
        assertEquals(1, summary.getOutOfStockCount());
        assertEquals(1, summary.getCriticalCount());
        assertEquals(1, summary.getLowStockCount());
    }
}
