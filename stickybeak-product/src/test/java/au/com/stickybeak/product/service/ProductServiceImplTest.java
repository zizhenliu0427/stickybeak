package au.com.stickybeak.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.ProductQuery;
import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.entity.ProductImage;
import au.com.stickybeak.product.entity.ProductTagRel;
import au.com.stickybeak.product.entity.Tag;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.mapper.ProductImageMapper;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.ProductTagRelMapper;
import au.com.stickybeak.product.mapper.TagMapper;
import au.com.stickybeak.product.service.impl.ProductServiceImpl;
import au.com.stickybeak.product.vo.ProductListVO;
import au.com.stickybeak.product.vo.ProductVO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

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

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 这里只测 MySQL 路径；ES 路径见 ProductServiceSearchTest
        productService = new ProductServiceImpl(productMapper, productImageMapper, tagMapper,
                productTagRelMapper, categoryMapper, stringRedisTemplate, objectMapper, Optional.empty());
    }

    private Product createSampleProduct(Long id, String slug, String name) {
        Product p = new Product();
        p.setId(id);
        p.setSlug(slug);
        p.setName(name);
        p.setDescription("Test description");
        p.setCategoryId(1L);
        p.setPrice(new BigDecimal("19.90"));
        p.setStock(50);
        p.setSales(10);
        p.setFeatured(1);
        p.setStatus(0);
        return p;
    }

    @Test
    void getBySlug_found() {
        Product p = createSampleProduct(1001L, "sample-slug", "Sample Product");
        when(productMapper.selectOne(any())).thenReturn(p);

        Category cat = new Category();
        cat.setId(1L);
        cat.setSlug("bus-sign");
        cat.setName("大学公交路牌");
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(cat));

        ProductImage img = new ProductImage();
        img.setProductId(1001L);
        img.setUrl("/products/test/cover.jpg");
        when(productImageMapper.selectList(any())).thenReturn(List.of(img));

        ProductTagRel rel = new ProductTagRel();
        rel.setProductId(1001L);
        rel.setTagId(10L);
        when(productTagRelMapper.selectList(any())).thenReturn(List.of(rel));

        Tag tag = new Tag();
        tag.setId(10L);
        tag.setName("UNSW");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag));

        ProductVO vo = productService.getBySlug("sample-slug");
        assertNotNull(vo);
        assertEquals("sample-slug", vo.getSlug());
        assertEquals("Sample Product", vo.getName());
        assertEquals(1990, vo.getPriceCents());
        assertEquals("bus-sign", vo.getCategory());
        assertEquals(1, vo.getImages().size());
        assertEquals(1, vo.getTags().size());
        assertEquals("UNSW", vo.getTags().get(0));
    }

    @Test
    void getBySlug_notFound_throwsException() {
        when(productMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> productService.getBySlug("non-existent"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void listFeatured_returnsFeaturedProducts() {
        Product p = createSampleProduct(1001L, "featured-slug", "Featured Product");
        when(productMapper.selectList(any())).thenReturn(List.of(p));

        List<ProductVO> list = productService.listFeatured();
        assertEquals(1, list.size());
        assertEquals("featured-slug", list.get(0).getSlug());
    }

    @Test
    void listProducts_pagination() {
        Product p = createSampleProduct(1001L, "prod-1", "Product 1");
        
        when(productMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Product> page = invocation.getArgument(0);
            page.setRecords(List.of(p));
            page.setTotal(1);
            return page;
        });

        ProductQuery query = new ProductQuery();
        query.setPage(1);
        query.setSize(10);

        ProductListVO result = productService.listProducts(query);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getItems().size());
        assertEquals("prod-1", result.getItems().get(0).getSlug());
    }

    private static ProductImage image(Long productId, String url, int sortOrder) {
        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setUrl(url);
        img.setSortOrder(sortOrder);
        return img;
    }

    private static ProductTagRel rel(Long productId, Long tagId) {
        ProductTagRel rel = new ProductTagRel();
        rel.setProductId(productId);
        rel.setTagId(tagId);
        return rel;
    }

    private static Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    @Test
    void listProducts_enrichesAWholePageWithFourQueries() {
        // 一页 12 个商品：分类 / 图片 / 标签关联 / 标签各查一次，而不是每个商品各查 4 次
        List<Product> page = new java.util.ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            Product p = createSampleProduct(id, "prod-" + id, "Product " + id);
            p.setCategoryId(id % 2 == 0 ? 2L : 1L);
            page.add(p);
        }
        when(productMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Product> p = invocation.getArgument(0);
            p.setRecords(page);
            p.setTotal(48);
            return p;
        });
        Category odd = new Category();
        odd.setId(1L);
        odd.setSlug("bus-sign");
        Category even = new Category();
        even.setId(2L);
        even.setSlug("train");
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(odd, even));
        when(productImageMapper.selectList(any())).thenReturn(List.of(
                image(1L, "/p1/cover.jpg", 0), image(1L, "/p1/2.jpg", 1), image(2L, "/p2/cover.jpg", 0)));
        when(productTagRelMapper.selectList(any())).thenReturn(List.of(rel(1L, 10L), rel(1L, 11L), rel(3L, 10L), rel(4L, 99L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(10L, "UNSW"), tag(11L, "Sydney")));

        ProductListVO result = productService.listProducts(new ProductQuery());

        assertEquals(12, result.getItems().size());
        assertEquals("prod-1", result.getItems().get(0).getSlug());
        assertEquals("prod-12", result.getItems().get(11).getSlug());
        assertEquals("bus-sign", result.getItems().get(0).getCategory());
        assertEquals("train", result.getItems().get(1).getCategory());
        assertEquals(List.of("/p1/cover.jpg", "/p1/2.jpg"), result.getItems().get(0).getImages());
        assertEquals(List.of("UNSW", "Sydney"), result.getItems().get(0).getTags());
        assertEquals(List.of("UNSW"), result.getItems().get(2).getTags());
        // 没有图片 / 标签、或标签已被删除的商品返回空列表
        assertEquals(List.of(), result.getItems().get(3).getTags());
        assertEquals(List.of(), result.getItems().get(4).getImages());

        verify(categoryMapper, times(1)).selectBatchIds(any());
        verify(productImageMapper, times(1)).selectList(any());
        verify(productTagRelMapper, times(1)).selectList(any());
        verify(tagMapper, times(1)).selectBatchIds(any());
        verify(categoryMapper, never()).selectById(any());
    }
}
