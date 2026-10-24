package au.com.stickybeak.product.search;

import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.entity.ProductTagRel;
import au.com.stickybeak.product.entity.Tag;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.ProductTagRelMapper;
import au.com.stickybeak.product.mapper.TagMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL → ES 同步：
 * - 增量：管理员修改商品后（事务提交后）重建该商品文档；
 * - 全量对账：启动时一次 + 每日定时一次，修正增量遗漏（ES 短暂不可用、下单引起的销量 / 库存变化）。
 * 商品量在千级以内，全量重建只需一次批量写入；规模变大后再换成 binlog（Canal）同步。
 */
@Component
@ConditionalOnProperty(prefix = "stickybeak.search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexer.class);

    private final ElasticsearchOperations operations;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ProductTagRelMapper productTagRelMapper;

    public ProductIndexer(ElasticsearchOperations operations,
                          ProductMapper productMapper,
                          CategoryMapper categoryMapper,
                          TagMapper tagMapper,
                          ProductTagRelMapper productTagRelMapper) {
        this.operations = operations;
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.productTagRelMapper = productTagRelMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductChanged(ProductChangedEvent event) {
        try {
            indexProduct(event.productId());
        } catch (Exception e) {
            log.warn("Failed to sync product {} to Elasticsearch, the next full reindex will fix it: {}",
                    event.productId(), e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reindexOnStartup() {
        reindexQuietly();
    }

    @Scheduled(cron = "${stickybeak.search.elasticsearch.reindex-cron:0 30 3 * * *}")
    public void scheduledReindex() {
        reindexQuietly();
    }

    private void reindexQuietly() {
        try {
            int count = reindexAll();
            log.info("Reindexed {} products into Elasticsearch", count);
        } catch (Exception e) {
            log.warn("Full reindex into Elasticsearch failed, search falls back to MySQL: {}", e.getMessage());
        }
    }

    /** 不存在时按 product-mapping.json 创建索引（需要 IK 分词插件） */
    public void ensureIndex() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
            log.info("Created Elasticsearch index {}", ProductDocument.INDEX);
        }
    }

    public int reindexAll() {
        ensureIndex();
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<>());
        // 先清理数据库里已不存在（逻辑删除）的文档，再批量覆盖写入
        List<String> liveIds = products.stream().map(p -> String.valueOf(p.getId())).toList();
        NativeQuery stale = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.mustNot(m -> m.ids(i -> i.values(liveIds)))))
                .build();
        operations.delete(DeleteQuery.builder(stale).build(), ProductDocument.class);
        if (products.isEmpty()) {
            return 0;
        }
        Map<Long, String> categoryNames = categoryMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        Map<Long, String> tagNames = tagMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        Map<Long, List<String>> tagsByProduct = productTagRelMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(rel -> tagNames.containsKey(rel.getTagId()))
                .collect(Collectors.groupingBy(ProductTagRel::getProductId,
                        Collectors.mapping(rel -> tagNames.get(rel.getTagId()), Collectors.toList())));

        List<ProductDocument> documents = products.stream()
                .map(p -> toDocument(p, categoryNames.get(p.getCategoryId()),
                        tagsByProduct.getOrDefault(p.getId(), Collections.emptyList())))
                .toList();
        operations.save(documents);
        return documents.size();
    }

    /** 商品被逻辑删除（selectById 查不到）时删除文档 */
    public void indexProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            operations.delete(String.valueOf(productId), ProductDocument.class);
            return;
        }
        Category category = product.getCategoryId() == null ? null : categoryMapper.selectById(product.getCategoryId());
        List<Long> tagIds = productTagRelMapper.selectList(new LambdaQueryWrapper<ProductTagRel>()
                        .eq(ProductTagRel::getProductId, productId)).stream()
                .map(ProductTagRel::getTagId)
                .toList();
        List<String> tags = tagIds.isEmpty() ? Collections.emptyList()
                : tagMapper.selectBatchIds(tagIds).stream().map(Tag::getName).toList();
        operations.save(toDocument(product, category == null ? null : category.getName(), tags));
    }

    static ProductDocument toDocument(Product p, String categoryName, List<String> tags) {
        ProductDocument doc = new ProductDocument();
        doc.setId(p.getId());
        doc.setName(p.getName());
        doc.setDescription(p.getDescription());
        doc.setSlug(p.getSlug());
        doc.setCategoryId(p.getCategoryId());
        doc.setCategoryName(categoryName);
        doc.setTags(new ArrayList<>(tags));
        doc.setPrice(p.getPrice() == null ? null : p.getPrice().doubleValue());
        doc.setSales(p.getSales());
        doc.setStock(p.getStock());
        doc.setStatus(p.getStatus());
        if (p.getCreateTime() != null) {
            doc.setCreatedAt(p.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return doc;
    }
}
