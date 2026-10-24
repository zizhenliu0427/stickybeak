package au.com.stickybeak.product.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 商品关键词检索。返回排好序的商品 ID 页与高亮片段；ES 不可用时返回 empty，由调用方回退到 MySQL。
 */
public interface ProductSearchService {

    Optional<Result> search(Criteria criteria);

    /** 检索条件；categoryId / tags / 价格为空表示不筛选 */
    record Criteria(String keyword, Long categoryId, List<String> tags,
                    BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int size) {
    }

    /** highlightedNames：商品 ID → 已做 HTML 转义、命中词用 &lt;em&gt; 包裹的名称 */
    record Result(List<Long> ids, long total, Map<Long, String> highlightedNames) {
    }
}
