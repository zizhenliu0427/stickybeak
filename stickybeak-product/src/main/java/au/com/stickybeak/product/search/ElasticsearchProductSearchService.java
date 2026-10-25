package au.com.stickybeak.product.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.json.JsonData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "stickybeak.search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchProductSearchService implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchProductSearchService.class);

    /** 与 MySQL 列表一致：0 = 在售 */
    static final int ON_SALE = 0;
    /** 名称权重最高，分类名与标签其次，描述兜底 */
    static final List<String> SEARCH_FIELDS = List.of("name^3", "categoryName^2", "tags.text^2", "description");

    private final ElasticsearchOperations operations;

    public ElasticsearchProductSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public Optional<Result> search(Criteria criteria) {
        try {
            SearchHits<ProductDocument> hits = operations.search(buildQuery(criteria), ProductDocument.class);
            List<Long> ids = new ArrayList<>();
            Map<Long, String> highlightedNames = new HashMap<>();
            for (SearchHit<ProductDocument> hit : hits.getSearchHits()) {
                Long id = Long.valueOf(hit.getId());
                ids.add(id);
                List<String> fragments = hit.getHighlightField("name");
                if (!fragments.isEmpty()) {
                    highlightedNames.put(id, fragments.get(0));
                }
            }
            return Optional.of(new Result(ids, hits.getTotalHits(), highlightedNames));
        } catch (Exception e) {
            // ES 宕机或索引缺失时不影响商城：回退到 MySQL LIKE
            log.warn("Elasticsearch search failed, falling back to MySQL: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static NativeQuery buildQuery(Criteria c) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        bool.must(m -> m.multiMatch(mm -> mm.query(c.keyword()).fields(SEARCH_FIELDS)));
        bool.filter(f -> f.term(t -> t.field("status").value(ON_SALE)));
        if (c.categoryId() != null) {
            bool.filter(f -> f.term(t -> t.field("categoryId").value(c.categoryId())));
        }
        if (c.tags() != null && !c.tags().isEmpty()) {
            List<FieldValue> values = c.tags().stream().map(FieldValue::of).toList();
            bool.filter(f -> f.terms(t -> t.field("tags").terms(v -> v.value(values))));
        }
        if (c.minPrice() != null || c.maxPrice() != null) {
            bool.filter(f -> f.range(r -> {
                r.field("price");
                if (c.minPrice() != null) {
                    r.gte(JsonData.of(c.minPrice().doubleValue()));
                }
                if (c.maxPrice() != null) {
                    r.lte(JsonData.of(c.maxPrice().doubleValue()));
                }
                return r;
            }));
        }

        // encoder=html：先转义原文再插入 <em>，前端可以安全地渲染高亮
        HighlightParameters params = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withEncoder("html")
                .withNumberOfFragments(0)
                .build();
        Highlight highlight = new Highlight(params, List.of(new HighlightField("name")));

        return NativeQuery.builder()
                .withQuery(q -> q.bool(bool.build()))
                .withPageable(PageRequest.of(Math.max(c.page(), 1) - 1, c.size(), toSort(c.sort())))
                .withHighlightQuery(new HighlightQuery(highlight, ProductDocument.class))
                .withTrackTotalHits(true)
                .build();
    }

    /** relevance（默认）按相关度；其余与 MySQL 列表的排序参数保持一致 */
    static Sort toSort(String sort) {
        if (sort == null) {
            return Sort.unsorted();
        }
        return switch (sort) {
            case "new" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "sales" -> Sort.by(Sort.Direction.DESC, "sales");
            case "price-asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price-desc" -> Sort.by(Sort.Direction.DESC, "price");
            default -> Sort.unsorted();
        };
    }
}
