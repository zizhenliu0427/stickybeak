package au.com.stickybeak.product.service;

import au.com.stickybeak.product.dto.ProductQuery;
import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.mapper.ProductImageMapper;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.ProductTagRelMapper;
import au.com.stickybeak.product.mapper.TagMapper;
import au.com.stickybeak.product.search.ProductSearchService;
import au.com.stickybeak.product.service.impl.ProductServiceImpl;
import au.com.stickybeak.product.vo.ProductListVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关键词搜索走 ES 的路径：ES 给出排序后的 ID 与高亮，商品详情仍从 MySQL 读；ES 不可用时回退 MySQL。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceSearchTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductImageMapper productImageMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ProductTagRelMapper productTagRelMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ProductSearchService searchService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        productService = new ProductServiceImpl(productMapper, productImageMapper, tagMapper,
                productTagRelMapper, categoryMapper, stringRedisTemplate, objectMapper, Optional.of(searchService));
    }

    private static Product product(Long id, int status) {
        Product p = new Product();
        p.setId(id);
        p.setSlug("magnet-" + id);
        p.setName("Magnet " + id);
        p.setCategoryId(1L);
        p.setPrice(new BigDecimal("10.00"));
        p.setStatus(status);
        return p;
    }

    private static ProductQuery keywordQuery(String q) {
        ProductQuery query = new ProductQuery();
        query.setQ(q);
        return query;
    }

    @Test
    void listProducts_withKeyword_keepsEsOrderAndHighlights() {
        when(searchService.search(any())).thenReturn(Optional.of(new ProductSearchService.Result(
                List.of(2L, 1L, 3L), 3L, Map.of(2L, "<em>Magnet</em> 2"))));
        // 数据库返回顺序与 ES 不同；3 号已在 ES 同步前下架
        when(productMapper.selectBatchIds(List.of(2L, 1L, 3L)))
                .thenReturn(List.of(product(1L, 0), product(2L, 0), product(3L, 1)));

        ProductListVO result = productService.listProducts(keywordQuery("  magnet "));

        assertEquals(List.of("magnet-2", "magnet-1"), result.getItems().stream().map(v -> v.getSlug()).toList());
        assertEquals("<em>Magnet</em> 2", result.getItems().get(0).getHighlightedName());
        assertNull(result.getItems().get(1).getHighlightedName());
        assertEquals(3L, result.getTotal());
        verify(productMapper, never()).selectPage(any(), any());
    }

    @Test
    void listProducts_withKeyword_passesFiltersToSearch() {
        Category category = new Category();
        category.setId(7L);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(searchService.search(any())).thenReturn(Optional.of(
                new ProductSearchService.Result(List.of(), 0L, Map.of())));
        ProductQuery query = keywordQuery("路牌");
        query.setCategory("bus-sign");
        query.setTags("UNSW, ,Sydney");
        query.setMinPrice(new BigDecimal("5"));
        query.setSort("sales");
        query.setPage(2);
        query.setSize(6);

        ProductListVO result = productService.listProducts(query);

        ArgumentCaptor<ProductSearchService.Criteria> captor = ArgumentCaptor.forClass(ProductSearchService.Criteria.class);
        verify(searchService).search(captor.capture());
        ProductSearchService.Criteria c = captor.getValue();
        assertEquals("路牌", c.keyword());
        assertEquals(7L, c.categoryId());
        assertEquals(List.of("UNSW", "Sydney"), c.tags());
        assertEquals(new BigDecimal("5"), c.minPrice());
        assertEquals("sales", c.sort());
        assertEquals(2, c.page());
        assertEquals(6, c.size());
        assertTrue(result.getItems().isEmpty());
        verify(productMapper, never()).selectBatchIds(any());
    }

    @Test
    void listProducts_withUnknownCategory_returnsEmptyWithoutSearching() {
        when(categoryMapper.selectOne(any())).thenReturn(null);
        ProductQuery query = keywordQuery("magnet");
        query.setCategory("missing");

        ProductListVO result = productService.listProducts(query);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(searchService, never()).search(any());
    }

    @Test
    void listProducts_whenEsUnavailable_fallsBackToMysql() {
        when(searchService.search(any())).thenReturn(Optional.empty());
        when(productMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Product> page = invocation.getArgument(0);
            page.setRecords(List.of(product(1L, 0)));
            page.setTotal(1);
            return page;
        });

        ProductListVO result = productService.listProducts(keywordQuery("magnet"));

        assertEquals(1, result.getItems().size());
        assertNull(result.getItems().get(0).getHighlightedName());
    }

    @Test
    void listProducts_withoutKeyword_doesNotUseEs() {
        when(productMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        productService.listProducts(new ProductQuery());

        verify(searchService, never()).search(any());
    }

    @Test
    void listCacheKey_isStableForEqualQueriesAndDiffersByParameter() {
        ProductQuery a = keywordQuery("magnet");
        a.setMinPrice(new BigDecimal("5.00"));
        ProductQuery b = keywordQuery("magnet");
        b.setMinPrice(new BigDecimal("5"));
        ProductQuery c = keywordQuery("magnet");
        c.setPage(2);

        assertEquals(ProductServiceImpl.listCacheKey(a), ProductServiceImpl.listCacheKey(b));
        assertNotEquals(ProductServiceImpl.listCacheKey(a), ProductServiceImpl.listCacheKey(c));
        assertTrue(ProductServiceImpl.listCacheKey(a).startsWith("product:list:"));
    }
}
