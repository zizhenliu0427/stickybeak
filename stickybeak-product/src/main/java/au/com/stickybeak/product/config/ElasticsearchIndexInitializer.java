package au.com.stickybeak.product.config;

import au.com.stickybeak.product.document.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 products 索引存在（mapping 由 ProductDocument 注解生成，IK 分析器按名引用）。
 * ES 不可用仅告警不阻断启动——搜索会自动降级 MySQL。
 */
@Component
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchIndexInitializer(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping();
                log.info("Created ES index 'products' with IK analyzer mapping");
            }
        } catch (Exception e) {
            log.warn("ES unavailable at startup, search will degrade to MySQL: {}", e.getMessage());
        }
    }
}
