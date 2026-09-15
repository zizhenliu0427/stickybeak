package au.com.stickybeak.product.service;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
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
        when(categoryMapper.selectById(1L)).thenReturn(cat);

        ProductImage img = new ProductImage();
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
}
