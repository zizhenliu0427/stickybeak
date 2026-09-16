package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.product.document.ProductDocument;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.entity.ProductTagRel;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.ProductTagRelMapper;
import au.com.stickybeak.product.service.ProductIndexService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

@Service
public class ProductIndexServiceImpl implements ProductIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexServiceImpl.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductMapper productMapper;
    private final ProductTagRelMapper productTagRelMapper;

    public ProductIndexServiceImpl(ElasticsearchOperations elasticsearchOperations,
                                   ProductMapper productMapper,
                                   ProductTagRelMapper productTagRelMapper) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.productMapper = productMapper;
        this.productTagRelMapper = productTagRelMapper;
    }

    @Override
    public void upsertFromDb(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            delete(productId);
            return;
        }
        elasticsearchOperations.save(toDocument(product));
        log.info("Indexed product {} to ES", productId);
    }

    @Override
    public void delete(Long productId) {
        elasticsearchOperations.delete(String.valueOf(productId), ProductDocument.class);
        log.info("Deleted product {} from ES", productId);
    }

    @Override
    public int reindexAll() {
        List<Product> products = productMapper.selectList(null);
        List<ProductDocument> docs = products.stream().map(this::toDocument).toList();
        elasticsearchOperations.save(docs);
        log.info("Reindexed {} products into ES", docs.size());
        return docs.size();
    }

    private ProductDocument toDocument(Product p) {
        ProductDocument doc = new ProductDocument();
        doc.setId(p.getId());
        doc.setName(p.getName());
        doc.setDescription(p.getDescription());
        doc.setSlug(p.getSlug());
        doc.setCategoryId(p.getCategoryId());
        doc.setPriceCents(p.getPrice() == null ? 0
                : p.getPrice().multiply(BigDecimal.valueOf(100)).intValue());
        doc.setSales(p.getSales());
        doc.setStatus(p.getStatus());
        if (p.getCreateTime() != null) {
            doc.setCreateTime(p.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        List<Long> tagIds = productTagRelMapper.selectList(new LambdaQueryWrapper<ProductTagRel>()
                        .eq(ProductTagRel::getProductId, p.getId()))
                .stream().map(ProductTagRel::getTagId).toList();
        doc.setTagIds(tagIds);
        return doc;
    }
}
