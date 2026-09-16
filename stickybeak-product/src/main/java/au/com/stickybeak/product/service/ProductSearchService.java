package au.com.stickybeak.product.service;

import java.util.List;

/**
 * 商品搜索（ES）。ES 只负责检索命中与排序，商品实体仍从 MySQL 读（ES 为检索层，非数据源）。
 */
public interface ProductSearchService {

    /**
     * ES 检索。返回 null 表示 ES 不可用/被熔断，调用方降级 MySQL。
     */
    EsSearchResult search(String keyword, Long categoryId, List<Long> tagIds,
                          Integer minPriceCents, Integer maxPriceCents,
                          String sort, int page, int size);

    /** ES 命中页：商品 id（按相关性/排序规则有序）+ 总命中数 */
    record EsSearchResult(List<Long> ids, long total) {
    }
}
