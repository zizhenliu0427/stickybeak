package au.com.stickybeak.product.search;

/**
 * 商品数据被修改（上下架、改价、改库存）。由 {@link ProductIndexer} 在事务提交后同步到 ES。
 */
public record ProductChangedEvent(Long productId) {
}
