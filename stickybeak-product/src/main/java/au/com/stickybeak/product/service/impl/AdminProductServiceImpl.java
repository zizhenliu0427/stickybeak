package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.dto.AdminProductPageQuery;
import au.com.stickybeak.product.dto.ProductPriceUpdateRequest;
import au.com.stickybeak.product.dto.ProductStatusUpdateRequest;
import au.com.stickybeak.product.dto.ProductStockUpdateRequest;
import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.entity.ProductImage;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.mapper.ProductImageMapper;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.service.AdminProductService;
import au.com.stickybeak.product.vo.AdminProductVO;
import au.com.stickybeak.product.vo.StockAlertSummaryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final StringRedisTemplate stringRedisTemplate;

    public AdminProductServiceImpl(ProductMapper productMapper,
                                   CategoryMapper categoryMapper,
                                   ProductImageMapper productImageMapper,
                                   StringRedisTemplate stringRedisTemplate) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.productImageMapper = productImageMapper;
        this.stringRedisTemplate = stringRedisTemplate;
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
        log.info("Admin updated product {} status to {}", id, req.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStock(Long id, ProductStockUpdateRequest req) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Product not found: " + id);
        }

        int targetStock;
        if (req.getStock() != null) {
            targetStock = Math.max(0, req.getStock());
        } else if (req.getDelta() != null) {
            targetStock = Math.max(0, (product.getStock() != null ? product.getStock() : 0) + req.getDelta());
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Either stock or delta must be provided");
        }

        product.setStock(targetStock);
        productMapper.updateById(product);

        // 同步更新 Redis 预扣减库存缓存
        try {
            stringRedisTemplate.opsForValue().set("product:stock:" + id, String.valueOf(targetStock));
        } catch (Exception e) {
            log.warn("Failed to sync Redis stock cache for product {}: {}", id, e.getMessage());
        }

        clearProductCaches(product);
        log.info("Admin updated product {} stock to {}", id, targetStock);
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
