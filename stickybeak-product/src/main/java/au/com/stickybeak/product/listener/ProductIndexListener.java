package au.com.stickybeak.product.listener;

import au.com.stickybeak.common.event.ProductChangedEvent;
import au.com.stickybeak.product.service.ProductIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费商品变更事件刷新 ES 索引（最终一致，主流程不阻塞）。
 */
@Component
public class ProductIndexListener {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexListener.class);

    private final ProductIndexService productIndexService;

    public ProductIndexListener(ProductIndexService productIndexService) {
        this.productIndexService = productIndexService;
    }

    @RabbitListener(queues = ProductChangedEvent.INDEX_QUEUE)
    public void onProductChanged(ProductChangedEvent event) {
        try {
            if (event.getChangeType() == ProductChangedEvent.ChangeType.DELETE) {
                productIndexService.delete(event.getProductId());
            } else {
                productIndexService.upsertFromDb(event.getProductId());
            }
        } catch (Exception e) {
            // ES 宕机不阻塞 MQ 消费；索引与 DB 的偏差由全量 reindex 兜底
            log.error("Failed to sync product {} to ES: {}", event.getProductId(), e.getMessage());
        }
    }
}
