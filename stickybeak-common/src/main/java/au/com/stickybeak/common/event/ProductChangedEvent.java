package au.com.stickybeak.common.event;

import java.io.Serializable;

/**
 * 商品变更事件：管理端改价/改库存/上下架后投递，product 服务消费后刷新 ES 索引。
 * 不做 DB + ES 双写，走 MQ 最终一致（ARCHITECTURE §4.5）。
 */
public class ProductChangedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String EXCHANGE = "product.topic";
    public static final String ROUTING_KEY = "product.changed";
    public static final String INDEX_QUEUE = "product.search.index";

    public enum ChangeType {
        UPSERT,
        DELETE
    }

    private Long productId;
    private ChangeType changeType;

    public ProductChangedEvent() {
    }

    public ProductChangedEvent(Long productId, ChangeType changeType) {
        this.productId = productId;
        this.changeType = changeType;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }
}
