package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.ProductQuery;
import au.com.stickybeak.product.entity.*;
import au.com.stickybeak.product.mapper.*;
import au.com.stickybeak.product.search.ProductSearchService;
import au.com.stickybeak.product.service.ProductService;
import au.com.stickybeak.product.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final TagMapper tagMapper;
    private final ProductTagRelMapper productTagRelMapper;
    private final CategoryMapper categoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    /** 关闭 ES（stickybeak.search.elasticsearch.enabled=false）时为空，搜索只走 MySQL */
    private final Optional<ProductSearchService> productSearchService;

    private static final String CACHE_PREFIX = "product:";
    private static final long CACHE_TTL = 60; // seconds

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductImageMapper productImageMapper,
                              TagMapper tagMapper,
                              ProductTagRelMapper productTagRelMapper,
                              CategoryMapper categoryMapper,
                              StringRedisTemplate stringRedisTemplate,
                              ObjectMapper objectMapper,
                              Optional<ProductSearchService> productSearchService) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.tagMapper = tagMapper;
        this.productTagRelMapper = productTagRelMapper;
        this.categoryMapper = categoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.productSearchService = productSearchService;
    }

    @Override
    public ProductListVO listProducts(ProductQuery query) {
        // 显式 engine=es|mysql（压测 / 对比基准用）跳过缓存，免得测到的是 Redis
        boolean debugEngine = StringUtils.hasText(query.getEngine());
        String cacheKey = debugEngine ? null : listCacheKey(query);
        if (!debugEngine) {
            try {
                String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);
                if (StringUtils.hasText(cachedData)) {
                    return objectMapper.readValue(cachedData, ProductListVO.class);
                }
            } catch (Exception e) {
                // Redis error or JSON error, ignore cache
            }
        }

        // 关键词搜索优先走 ES（IK 分词 + 相关度排序 + 高亮）；ES 不可用时继续走下面的 MySQL LIKE
        if (StringUtils.hasText(query.getQ()) && productSearchService.isPresent()
                && !"mysql".equalsIgnoreCase(query.getEngine())) {
            ProductListVO searched = searchWithElasticsearch(query, productSearchService.get());
            if (searched != null) {
                cacheList(cacheKey, searched);
                return searched;
            }
        }

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 0);

        if (StringUtils.hasText(query.getCategory())) {
            LambdaQueryWrapper<Category> catWrapper = new LambdaQueryWrapper<>();
            catWrapper.eq(Category::getSlug, query.getCategory());
            Category category = categoryMapper.selectOne(catWrapper);
            if (category != null) {
                wrapper.eq(Product::getCategoryId, category.getId());
            } else {
                wrapper.eq(Product::getCategoryId, -1L); // won't match
            }
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

        if (StringUtils.hasText(query.getTags())) {
            String[] tags = query.getTags().split(",");
            List<Long> tagIds = new ArrayList<>();
            for (String tagName : tags) {
                LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
                tagWrapper.eq(Tag::getName, tagName.trim());
                Tag tag = tagMapper.selectOne(tagWrapper);
                if (tag != null) {
                    tagIds.add(tag.getId());
                }
            }

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
            case "relevance": // MySQL 回退路径没有相关度评分，按最新排序
            default:
                wrapper.orderByDesc(Product::getCreateTime);
                break;
        }
        // 唯一键兜底：批量导入后 create_time 常常相同，只按它排序时分页会重复 / 漏行
        wrapper.orderByDesc(Product::getId);

        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getSize() != null && query.getSize() > 0 ? query.getSize() : 12;
        
        Page<Product> page = new Page<>(pageNum, pageSize);
        productMapper.selectPage(page, wrapper);

        List<ProductVO> items = enrichProducts(page.getRecords());

        ProductListVO result = ProductListVO.of(items, page.getTotal(), pageNum, pageSize);
        cacheList(cacheKey, result);
        return result;
    }

    /**
     * 列表缓存 key 由全部查询参数决定。ProductQuery 没有重写 hashCode，原先的 query.hashCode()
     * 是对象身份哈希，导致缓存永远不命中、每次请求都写一个新 key。取摘要是为了限制关键词带来的 key 长度。
     */
    public static String listCacheKey(ProductQuery query) {
        String canonical = String.join("|",
                String.valueOf(query.getCategory()), String.valueOf(query.getTags()),
                query.getMinPrice() == null ? "null" : query.getMinPrice().stripTrailingZeros().toPlainString(),
                query.getMaxPrice() == null ? "null" : query.getMaxPrice().stripTrailingZeros().toPlainString(),
                String.valueOf(query.getSort()), String.valueOf(query.getQ()),
                String.valueOf(query.getPage()), String.valueOf(query.getSize()));
        return CACHE_PREFIX + "list:" + DigestUtils.md5DigestAsHex(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private void cacheList(String cacheKey, ProductListVO result) {
        if (cacheKey == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore cache error
        }
    }

    /**
     * ES 只负责检索与排序，返回商品 ID；详情（图片、实时库存）仍从 MySQL 读取，保持与列表页一致。
     * ES 不可用时返回 null，由调用方回退。
     */
    private ProductListVO searchWithElasticsearch(ProductQuery query, ProductSearchService searchService) {
        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getSize() != null && query.getSize() > 0 ? query.getSize() : 12;

        Long categoryId = null;
        if (StringUtils.hasText(query.getCategory())) {
            Category category = categoryMapper.selectOne(
                    new LambdaQueryWrapper<Category>().eq(Category::getSlug, query.getCategory()));
            if (category == null) {
                return ProductListVO.of(new ArrayList<>(), 0L, pageNum, pageSize);
            }
            categoryId = category.getId();
        }
        List<String> tags = StringUtils.hasText(query.getTags())
                ? Arrays.stream(query.getTags().split(",")).map(String::trim).filter(StringUtils::hasText).toList()
                : List.of();

        Optional<ProductSearchService.Result> found = searchService.search(new ProductSearchService.Criteria(
                query.getQ().trim(), categoryId, tags, query.getMinPrice(), query.getMaxPrice(),
                query.getSort(), pageNum, pageSize));
        if (found.isEmpty()) {
            return null;
        }

        ProductSearchService.Result result = found.get();
        if (result.ids().isEmpty()) {
            return ProductListVO.of(new ArrayList<>(), result.total(), pageNum, pageSize);
        }
        Map<Long, Product> productsById = productMapper.selectBatchIds(result.ids()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        // 按 ES 的排序输出；索引可能比数据库略旧，已下架的商品在这里过滤掉
        List<Product> onSale = result.ids().stream()
                .map(productsById::get)
                .filter(p -> p != null && Integer.valueOf(0).equals(p.getStatus()))
                .toList();
        List<ProductVO> items = enrichProducts(onSale);
        items.forEach(vo -> vo.setHighlightedName(result.highlightedNames().get(vo.getId())));
        return ProductListVO.of(items, result.total(), pageNum, pageSize);
    }

    private ProductVO enrichProduct(Product p) {
        return enrichProducts(List.of(p)).get(0);
    }

    /**
     * 批量补全分类、图片、标签：不管一页有多少个商品，都只发 4 条 SQL，输出顺序与输入一致。
     * 之前按商品逐个查询（N+1），一页 12 个商品约 50 条 SQL、每条单独借还一次连接；
     * SkyWalking 链路显示并发时大部分时间耗在等数据库连接上（优化前后数据见 docs/PERF_PRODUCT_LIST.md）。
     */
    private List<ProductVO> enrichProducts(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toSet());
        Set<Long> categoryIds = products.stream().map(Product::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Category> categories = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));

        // 封面排第一：前台卡片直接取 images[0]
        Map<Long, List<ProductImage>> mediaByProduct = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                        .in(ProductImage::getProductId, productIds)
                        .orderByAsc(ProductImage::getProductId)
                        .orderByDesc(ProductImage::getIsCover)
                        .orderByAsc(ProductImage::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        List<ProductTagRel> rels = productTagRelMapper.selectList(new LambdaQueryWrapper<ProductTagRel>()
                .in(ProductTagRel::getProductId, productIds)
                .orderByAsc(ProductTagRel::getId));
        Set<Long> tagIds = rels.stream().map(ProductTagRel::getTagId).collect(Collectors.toSet());
        Map<Long, String> tagNames = tagIds.isEmpty() ? Map.of()
                : tagMapper.selectBatchIds(tagIds).stream()
                        .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        Map<Long, List<String>> tagsByProduct = rels.stream()
                .filter(rel -> tagNames.containsKey(rel.getTagId()))
                .collect(Collectors.groupingBy(ProductTagRel::getProductId,
                        Collectors.mapping(rel -> tagNames.get(rel.getTagId()), Collectors.toList())));

        return products.stream()
                .map(p -> toVO(p, categories.get(p.getCategoryId()),
                        mediaByProduct.getOrDefault(p.getId(), new ArrayList<>()),
                        tagsByProduct.getOrDefault(p.getId(), new ArrayList<>())))
                .collect(Collectors.toList());
    }

    private static ProductVO toVO(Product p, Category category, List<ProductImage> mediaRows, List<String> tags) {
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
        if (category != null) {
            vo.setCategory(category.getSlug());
            vo.setCategoryName(category.getName());
        }
        List<ProductVO.MediaItem> media = mediaRows.stream()
                .map(i -> new ProductVO.MediaItem(i.getUrl(), i.getMediaType() == null ? "image" : i.getMediaType()))
                .collect(Collectors.toList());
        vo.setMedia(media);
        // images 只含图片（卡片 / 封面安全），视频走 media
        vo.setImages(media.stream()
                .filter(m -> "image".equals(m.mediaType()))
                .map(ProductVO.MediaItem::url)
                .collect(Collectors.toList()));
        vo.setTags(new ArrayList<>(tags));
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
        return enrichProducts(products);
    }

    @Override
    public List<ProductVO> listFeatured() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getFeatured, 1);
        wrapper.eq(Product::getStatus, 0);
        wrapper.last("LIMIT 8");
        return enrichProducts(productMapper.selectList(wrapper));
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

        return enrichProducts(productMapper.selectList(wrapper));
    }
}
