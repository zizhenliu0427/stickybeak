package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.ProductQuery;
import au.com.stickybeak.product.entity.*;
import au.com.stickybeak.product.mapper.*;
import au.com.stickybeak.product.service.ProductSearchService;
import au.com.stickybeak.product.service.ProductService;
import au.com.stickybeak.product.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final TagMapper tagMapper;
    private final ProductTagRelMapper productTagRelMapper;
    private final CategoryMapper categoryMapper;
    private final ProductSearchService productSearchService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "product:";
    private static final long CACHE_TTL = 60; // seconds

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductImageMapper productImageMapper,
                              TagMapper tagMapper,
                              ProductTagRelMapper productTagRelMapper,
                              CategoryMapper categoryMapper,
                              ProductSearchService productSearchService,
                              StringRedisTemplate stringRedisTemplate,
                              ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.tagMapper = tagMapper;
        this.productTagRelMapper = productTagRelMapper;
        this.categoryMapper = categoryMapper;
        this.productSearchService = productSearchService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 稳定的缓存 key（修复：原先直接用 query.hashCode() 是对象身份哈希，每次都不同，缓存从未命中） */
    private String listCacheKey(ProductQuery query) {
        return CACHE_PREFIX + "list:" + String.join("|",
                String.valueOf(query.getCategory()),
                String.valueOf(query.getTags()),
                String.valueOf(query.getMinPrice()),
                String.valueOf(query.getMaxPrice()),
                String.valueOf(query.getSort()),
                String.valueOf(query.getQ()),
                String.valueOf(query.getPage()),
                String.valueOf(query.getSize()));
    }

    @Override
    public ProductListVO listProducts(ProductQuery query) {
        boolean debugEngine = StringUtils.hasText(query.getEngine());
        // 显式 engine 参数（压测/调试）跳过缓存，避免污染线上缓存
        if (!debugEngine) {
            try {
                String cachedData = stringRedisTemplate.opsForValue().get(listCacheKey(query));
                if (StringUtils.hasText(cachedData)) {
                    return objectMapper.readValue(cachedData, ProductListVO.class);
                }
            } catch (Exception e) {
                // Redis error or JSON error, ignore cache
            }
        }

        // 分类 slug → id；标签名 → tagIds（供 ES 过滤与 MySQL 兜底共用）
        Long categoryId = null;
        if (StringUtils.hasText(query.getCategory())) {
            Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getSlug, query.getCategory()));
            categoryId = category != null ? category.getId() : -1L;
        }
        List<Long> tagIds = null;
        if (StringUtils.hasText(query.getTags())) {
            tagIds = new ArrayList<>();
            for (String tagName : query.getTags().split(",")) {
                Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getName, tagName.trim()));
                if (tag != null) {
                    tagIds.add(tag.getId());
                }
            }
        }

        // 有关键词且未强制 mysql → 先走 ES（Sentinel 熔断/异常时返回 null 自动降级）
        if (StringUtils.hasText(query.getQ()) && !"mysql".equalsIgnoreCase(query.getEngine())
                && !Long.valueOf(-1L).equals(categoryId) && (tagIds == null || !tagIds.isEmpty())) {
            Integer minCents = query.getMinPrice() == null ? null
                    : query.getMinPrice().multiply(BigDecimal.valueOf(100)).intValue();
            Integer maxCents = query.getMaxPrice() == null ? null
                    : query.getMaxPrice().multiply(BigDecimal.valueOf(100)).intValue();
            int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
            int pageSize = query.getSize() != null && query.getSize() > 0 ? query.getSize() : 12;

            ProductSearchService.EsSearchResult esResult = productSearchService.search(
                    query.getQ(), categoryId, tagIds, minCents, maxCents,
                    query.getSort(), pageNum, pageSize);
            if (esResult != null) {
                ProductListVO result = buildFromEsHits(esResult, pageNum, pageSize);
                if (!debugEngine) {
                    writeCache(listCacheKey(query), result);
                }
                return result;
            }
            log.info("ES unavailable, falling back to MySQL LIKE for q={}", query.getQ());
        }

        ProductListVO result = listProductsViaMysql(query, categoryId, tagIds);
        if (!debugEngine) {
            writeCache(listCacheKey(query), result);
        }
        return result;
    }

    /** ES 命中 id 列表 → MySQL 取实体并保留 ES 相关性/排序顺序 */
    private ProductListVO buildFromEsHits(ProductSearchService.EsSearchResult esResult, int pageNum, int pageSize) {
        if (esResult.ids().isEmpty()) {
            return ProductListVO.of(new ArrayList<>(), 0, pageNum, pageSize);
        }
        Map<Long, Product> byId = productMapper.selectBatchIds(esResult.ids()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<ProductVO> items = esResult.ids().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::enrichProduct)
                .collect(Collectors.toList());
        return ProductListVO.of(items, esResult.total(), pageNum, pageSize);
    }

    private void writeCache(String key, ProductListVO result) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result),
                    CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore cache error
        }
    }

    private ProductListVO listProductsViaMysql(ProductQuery query, Long categoryId, List<Long> tagIds) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 0);

        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        if (query.getMinPrice() != null) {
            wrapper.ge(Product::getPrice, query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            wrapper.le(Product::getPrice, query.getMaxPrice());
        }

        if (StringUtils.hasText(query.getQ())) {
            wrapper.and(w -> w.like(Product::getName, query.getQ())
                              .or()
                              .like(Product::getDescription, query.getQ()));
        }

        if (tagIds != null) {
            if (tagIds.isEmpty()) {
                wrapper.eq(Product::getId, -1L); // won't match
            } else {
                LambdaQueryWrapper<ProductTagRel> relWrapper = new LambdaQueryWrapper<>();
                relWrapper.in(ProductTagRel::getTagId, tagIds);
                List<ProductTagRel> rels = productTagRelMapper.selectList(relWrapper);

                List<Long> productIds = rels.stream()
                        .map(ProductTagRel::getProductId)
                        .distinct()
                        .collect(Collectors.toList());

                if (productIds.isEmpty()) {
                    wrapper.eq(Product::getId, -1L); // won't match
                } else {
                    wrapper.in(Product::getId, productIds);
                }
            }
        }

        String sort = query.getSort() != null ? query.getSort() : "new";
        switch (sort) {
            case "sales":
                wrapper.orderByDesc(Product::getSales);
                break;
            case "price-asc":
                wrapper.orderByAsc(Product::getPrice);
                break;
            case "price-desc":
                wrapper.orderByDesc(Product::getPrice);
                break;
            case "new":
            default:
                wrapper.orderByDesc(Product::getCreateTime);
                break;
        }

        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getSize() != null && query.getSize() > 0 ? query.getSize() : 12;
        
        Page<Product> page = new Page<>(pageNum, pageSize);
        productMapper.selectPage(page, wrapper);

        List<ProductVO> items = page.getRecords().stream()
                .map(this::enrichProduct)
                .collect(Collectors.toList());

        ProductListVO result = ProductListVO.of(items, page.getTotal(), pageNum, pageSize);
        return result;
    }

    private ProductVO enrichProduct(Product p) {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setSlug(p.getSlug());
        vo.setDescription(p.getDescription());
        if (p.getPrice() != null) {
            vo.setPriceCents(p.getPrice().multiply(BigDecimal.valueOf(100)).intValue());
        }
        vo.setStock(p.getStock());
        vo.setSales(p.getSales());
        vo.setFeatured(p.getFeatured() != null && p.getFeatured() == 1);
        vo.setSourceNoteId(p.getSourceNoteId());

        Category category = categoryMapper.selectById(p.getCategoryId());
        if (category != null) {
            vo.setCategory(category.getSlug());
            vo.setCategoryName(category.getName());
        }

        LambdaQueryWrapper<ProductImage> imgWrapper = new LambdaQueryWrapper<>();
        imgWrapper.eq(ProductImage::getProductId, p.getId()).orderByAsc(ProductImage::getSortOrder);
        List<ProductImage> images = productImageMapper.selectList(imgWrapper);
        if (images != null && !images.isEmpty()) {
            List<ProductVO.MediaItem> media = images.stream()
                    .map(i -> new ProductVO.MediaItem(i.getUrl(),
                            i.getMediaType() == null ? "image" : i.getMediaType()))
                    .collect(Collectors.toList());
            vo.setMedia(media);
            // images 只含图片（卡片/封面安全），视频走 media
            vo.setImages(media.stream()
                    .filter(m -> "image".equals(m.mediaType()))
                    .map(ProductVO.MediaItem::url)
                    .collect(Collectors.toList()));
        } else {
            vo.setImages(new ArrayList<>());
            vo.setMedia(new ArrayList<>());
        }

        LambdaQueryWrapper<ProductTagRel> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(ProductTagRel::getProductId, p.getId());
        List<ProductTagRel> rels = productTagRelMapper.selectList(relWrapper);
        if (rels != null && !rels.isEmpty()) {
            List<Long> tagIds = rels.stream().map(ProductTagRel::getTagId).collect(Collectors.toList());
            if (!tagIds.isEmpty()) {
                List<Tag> tags = tagMapper.selectBatchIds(tagIds);
                if (tags != null) {
                    List<String> tagNames = tags.stream().map(Tag::getName).collect(Collectors.toList());
                    vo.setTags(tagNames);
                }
            }
        }
        if (vo.getTags() == null) {
            vo.setTags(new ArrayList<>());
        }

        return vo;
    }

    @Override
    public ProductVO getBySlug(String slug) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getSlug, slug);
        Product product = productMapper.selectOne(wrapper);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Product not found");
        }
        return enrichProduct(product);
    }

    @Override
    public ProductVO getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Product not found");
        }
        return enrichProduct(product);
    }

    @Override
    public List<ProductVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Product> products = productMapper.selectBatchIds(ids);
        if (products == null) {
            return new ArrayList<>();
        }
        return products.stream().map(this::enrichProduct).collect(Collectors.toList());
    }

    @Override
    public List<ProductVO> listFeatured() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getFeatured, 1);
        wrapper.eq(Product::getStatus, 0);
        wrapper.last("LIMIT 8");
        List<Product> products = productMapper.selectList(wrapper);
        return products.stream().map(this::enrichProduct).collect(Collectors.toList());
    }

    @Override
    public List<ProductVO> listRelated(String categorySlug, String excludeSlug, int limit) {
        LambdaQueryWrapper<Category> catWrapper = new LambdaQueryWrapper<>();
        catWrapper.eq(Category::getSlug, categorySlug);
        Category category = categoryMapper.selectOne(catWrapper);
        if (category == null) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getCategoryId, category.getId());
        wrapper.ne(Product::getSlug, excludeSlug);
        wrapper.eq(Product::getStatus, 0);
        wrapper.last("LIMIT " + limit);

        List<Product> products = productMapper.selectList(wrapper);
        return products.stream().map(this::enrichProduct).collect(Collectors.toList());
    }
}
