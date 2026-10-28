package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.common.tx.AfterCommit;
import au.com.stickybeak.product.dto.AdminProductPageQuery;
import au.com.stickybeak.product.dto.ProductCreateRequest;
import au.com.stickybeak.product.dto.ProductPriceUpdateRequest;
import au.com.stickybeak.product.dto.ProductStatusUpdateRequest;
import au.com.stickybeak.product.dto.ProductStockUpdateRequest;
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
import au.com.stickybeak.product.search.ProductChangedEvent;
import au.com.stickybeak.product.service.AdminProductService;
import au.com.stickybeak.product.vo.AdminProductVO;
import au.com.stickybeak.product.vo.StockAlertSummaryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.seata.spring.annotation.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminProductServiceImpl implements AdminProductService {

    private static final Logger log = LoggerFactory.getLogger(AdminProductServiceImpl.class);

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageMapper productImageMapper;
    private final TagMapper tagMapper;
    private final ProductTagRelMapper productTagRelMapper;
    private final StringRedisTemplate stringRedisTemplate;
    /** 商品变更后通知 ES 索引同步（事务提交后执行，见 ProductIndexer） */
    private final ApplicationEventPublisher eventPublisher;

    public AdminProductServiceImpl(ProductMapper productMapper,
                                   CategoryMapper categoryMapper,
                                   ProductImageMapper productImageMapper,
                                   TagMapper tagMapper,
                                   ProductTagRelMapper productTagRelMapper,
                                   StringRedisTemplate stringRedisTemplate,
                                   ApplicationEventPublisher eventPublisher) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.productImageMapper = productImageMapper;
        this.tagMapper = tagMapper;
        this.productTagRelMapper = productTagRelMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Page<AdminProductVO> pageProducts(AdminProductPageQuery query) {
        Page<Product> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(Product::getName, query.getKeyword().trim())
                    .or().like(Product::getDescription, query.getKeyword().trim())
                    .or().like(Product::getSlug, query.getKeyword().trim()));
        }

        if (query.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, query.getCategoryId());
        }

        if (query.getStatus() != null) {
            wrapper.eq(Product::getStatus, query.getStatus());
        }

        if (query.getStockAlert() != null) {
            int th = query.getThreshold();
            switch (query.getStockAlert()) {
                case 1 -> wrapper.le(Product::getStock, th);
                case 2 -> wrapper.eq(Product::getStock, 0);
                case 3 -> wrapper.ge(Product::getStock, 1).le(Product::getStock, 3);
                case 4 -> wrapper.ge(Product::getStock, 4).le(Product::getStock, th);
                default -> {}
            }
        }

        wrapper.orderByDesc(Product::getUpdateTime);
        Page<Product> resultPage = productMapper.selectPage(page, wrapper);

        Page<AdminProductVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        if (resultPage.getRecords().isEmpty()) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        // 批量提取分类与图片
        Set<Long> categoryIds = resultPage.getRecords().stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<Category> categories = categoryMapper.selectBatchIds(categoryIds);
            for (Category c : categories) {
                categoryMap.put(c.getId(), c.getName());
            }
        }

        Set<Long> productIds = resultPage.getRecords().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        Map<Long, String> coverImageMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            LambdaQueryWrapper<ProductImage> imgWrapper = new LambdaQueryWrapper<>();
            imgWrapper.in(ProductImage::getProductId, productIds);
            List<ProductImage> images = productImageMapper.selectList(imgWrapper);
            for (ProductImage img : images) {
                if ("video".equalsIgnoreCase(img.getMediaType())) {
                    continue;
                }
                if (img.getIsCover() != null && img.getIsCover() == 1) {
                    coverImageMap.put(img.getProductId(), img.getUrl());
                } else if (!coverImageMap.containsKey(img.getProductId())) {
                    coverImageMap.put(img.getProductId(), img.getUrl());
                }
            }
        }

        List<AdminProductVO> voList = resultPage.getRecords().stream().map(p -> {
            AdminProductVO vo = new AdminProductVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setSlug(p.getSlug());
            vo.setDescription(p.getDescription());
            vo.setCategoryId(p.getCategoryId());
            vo.setCategoryName(categoryMap.get(p.getCategoryId()));
            vo.setPrice(p.getPrice());
            if (p.getPrice() != null) {
                vo.setPriceCents(p.getPrice().multiply(new BigDecimal("100")).intValue());
            }
            vo.setStock(p.getStock());
            vo.setSales(p.getSales());
            vo.setStatus(p.getStatus());
            vo.setFeatured(p.getFeatured());
            vo.setCoverImage(coverImageMap.get(p.getId()));
            vo.setCreateTime(p.getCreateTime());
            vo.setUpdateTime(p.getUpdateTime());
            vo.setStockStatus(computeStockStatus(p.getStock()));
            return vo;
        }).collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, ProductStatusUpdateRequest req) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Product not found: " + id);
        }
        product.setStatus(req.getStatus());
        productMapper.updateById(product);
        clearProductCaches(product);
        eventPublisher.publishEvent(new ProductChangedEvent(id));
        log.info("Admin updated product {} status to {}", id, req.getStatus());
    }

    /** 与 InventoryServiceImpl#rollbackStock 同理：改库存行要先确认它不在未完成的结账全局事务里 */
    @Override
    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public void updateStock(Long id, ProductStockUpdateRequest req) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Product not found: " + id);
        }

        // 在数据库内原子更新：先读后写（updateById）会覆盖这期间下单产生的扣减，导致超卖
        if (req.getStock() != null) {
            productMapper.setStock(id, Math.max(0, req.getStock()));
        } else if (req.getDelta() != null) {
            productMapper.adjustStock(id, req.getDelta());
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Either stock or delta must be provided");
        }

        // Redis 预扣缓存在提交后删除，下一次扣减按 MySQL 最新值重新预热（避免覆盖并发扣减的结果）
        String stockKey = "product:stock:" + id;
        AfterCommit.run(() -> {
            try {
                stringRedisTemplate.delete(stockKey);
            } catch (Exception e) {
                log.warn("Failed to evict Redis stock cache for product {}: {}", id, e.getMessage());
            }
        });

        clearProductCaches(product);
        eventPublisher.publishEvent(new ProductChangedEvent(id));
        log.info("Admin updated product {} stock (set={}, delta={})", id, req.getStock(), req.getDelta());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePrice(Long id, ProductPriceUpdateRequest req) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Product not found: " + id);
        }
        product.setPrice(req.getPrice());
        productMapper.updateById(product);
        clearProductCaches(product);
        eventPublisher.publishEvent(new ProductChangedEvent(id));
        log.info("Admin updated product {} price to {}", id, req.getPrice());
    }

    @Override
    public StockAlertSummaryVO getStockAlerts(int threshold) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(Product::getStock, threshold);
        wrapper.orderByAsc(Product::getStock);
        List<Product> alertList = productMapper.selectList(wrapper);

        StockAlertSummaryVO summary = new StockAlertSummaryVO();
        summary.setTotalAlerts(alertList.size());

        int outOfStock = 0;
        int critical = 0;
        int low = 0;

        List<AdminProductVO> voList = new ArrayList<>();
        for (Product p : alertList) {
            int stock = p.getStock() != null ? p.getStock() : 0;
            if (stock == 0) {
                outOfStock++;
            } else if (stock <= 3) {
                critical++;
            } else {
                low++;
            }

            AdminProductVO vo = new AdminProductVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setSlug(p.getSlug());
            vo.setPrice(p.getPrice());
            vo.setStock(p.getStock());
            vo.setSales(p.getSales());
            vo.setStatus(p.getStatus());
            vo.setStockStatus(computeStockStatus(stock));
            voList.add(vo);
        }

        summary.setOutOfStockCount(outOfStock);
        summary.setCriticalCount(critical);
        summary.setLowStockCount(low);
        summary.setAlertProducts(voList);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(ProductCreateRequest req) {
        Category category = categoryMapper.selectById(req.categoryId());
        if (category == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "category not found: " + req.categoryId());
        }

        Product product = new Product();
        product.setName(req.name().trim());
        // slug：未提供则先占位，插入后更新为 p-{id}
        product.setSlug(StringUtils.hasText(req.slug()) ? req.slug().trim()
                : "tmp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        product.setDescription(req.description());
        product.setCategoryId(req.categoryId());
        product.setPrice(req.price());
        product.setStock(req.stock());
        product.setSales(0);
        product.setFeatured(Boolean.TRUE.equals(req.featured()) ? 1 : 0);
        product.setStatus(Product.STATUS_ACTIVE);
        productMapper.insert(product);

        if (!StringUtils.hasText(req.slug())) {
            Product slugUpdate = new Product();
            slugUpdate.setId(product.getId());
            slugUpdate.setSlug("p-" + product.getId());
            productMapper.updateById(slugUpdate);
            product.setSlug("p-" + product.getId());
        }

        // 媒体：优先 media（含类型），兼容旧 images 字段（全按 image）；第一张图片为封面
        List<ProductCreateRequest.MediaInput> mediaInputs = req.media() != null && !req.media().isEmpty()
                ? req.media()
                : (req.images() == null ? List.of()
                        : req.images().stream()
                                .filter(StringUtils::hasText)
                                .map(u -> new ProductCreateRequest.MediaInput(u.trim(), "image"))
                                .toList());
        int sort = 0;
        boolean coverAssigned = false;
        for (ProductCreateRequest.MediaInput m : mediaInputs) {
            if (m == null || !StringUtils.hasText(m.url())) {
                continue;
            }
            String mediaType = "video".equalsIgnoreCase(m.mediaType()) ? "video" : "image";
            ProductImage image = new ProductImage();
            image.setProductId(product.getId());
            image.setUrl(m.url().trim());
            image.setMediaType(mediaType);
            image.setSortOrder(sort);
            // 封面只能是图片（视频不做封面）
            boolean isCover = !coverAssigned && "image".equals(mediaType);
            image.setIsCover(isCover ? 1 : 0);
            if (isCover) {
                coverAssigned = true;
            }
            productImageMapper.insert(image);
            sort++;
        }

        // 标签：按名 upsert 后关联
        if (req.tags() != null) {
            for (String raw : req.tags()) {
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                String tagName = raw.trim();
                Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName));
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(tagName);
                    tagMapper.insert(tag);
                }
                ProductTagRel rel = new ProductTagRel();
                rel.setProductId(product.getId());
                rel.setTagId(tag.getId());
                productTagRelMapper.insert(rel);
            }
        }

        eventPublisher.publishEvent(new ProductChangedEvent(product.getId()));
        log.info("Admin created product {} ({}), category={}", product.getId(), product.getName(), req.categoryId());
        return product.getId();
    }

    private String computeStockStatus(Integer stock) {
        if (stock == null || stock <= 0) {
            return "out_of_stock";
        } else if (stock <= 3) {
            return "critical";
        } else if (stock <= 10) {
            return "low";
        }
        return "normal";
    }

    private void clearProductCaches(Product product) {
        try {
            stringRedisTemplate.delete("product:id:" + product.getId());
            if (StringUtils.hasText(product.getSlug())) {
                stringRedisTemplate.delete("product:slug:" + product.getSlug());
            }
            Set<String> listKeys = stringRedisTemplate.keys("product:list:*");
            if (listKeys != null && !listKeys.isEmpty()) {
                stringRedisTemplate.delete(listKeys);
            }
        } catch (Exception e) {
            log.warn("Failed to evict redis cache for product {}: {}", product.getId(), e.getMessage());
        }
    }
}
