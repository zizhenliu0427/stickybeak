package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.product.document.ProductDocument;
import au.com.stickybeak.product.service.ProductSearchService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.json.JsonData;

import java.util.List;

/**
 * ES 检索实现。Sentinel 资源 productEsSearch：
 * 异常比例超阈值熔断（SentinelConfig 注册规则），熔断/异常一律 fallback 返回 null → 调用方走 MySQL。
 */
@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);

    public static final String RESOURCE = "productEsSearch";

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchServiceImpl(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    @SentinelResource(value = RESOURCE, fallback = "searchFallback")
    public EsSearchResult search(String keyword, Long categoryId, List<Long> tagIds,
                                 Integer minPriceCents, Integer maxPriceCents,
                                 String sort, int page, int size) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                // IK 分词检索：name 权重 3 倍于 description
                .must(QueryBuilders.multiMatch(m -> m
                        .query(keyword)
                        .fields("name^3", "description")
                        .analyzer("ik_smart")))
                // 只搜上架商品
                .filter(QueryBuilders.term(t -> t.field("status").value(0)));

        if (categoryId != null) {
            bool.filter(QueryBuilders.term(t -> t.field("categoryId").value(categoryId)));
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            bool.filter(QueryBuilders.terms(t -> t.field("tagIds")
                    .terms(TermsQueryField.of(v -> v.value(
                            tagIds.stream().map(FieldValue::of).toList())))));
        }
        if (minPriceCents != null || maxPriceCents != null) {
            bool.filter(QueryBuilders.range(r -> {
                var range = r.field("priceCents");
                if (minPriceCents != null) {
                    range = range.gte(JsonData.of(minPriceCents));
                }
                if (maxPriceCents != null) {
                    range = range.lte(JsonData.of(maxPriceCents));
                }
                return range;
            }));
        }

        Sort springSort = switch (sort == null ? "new" : sort) {
            case "sales" -> Sort.by(Sort.Direction.DESC, "sales");
            case "price-asc" -> Sort.by(Sort.Direction.ASC, "priceCents");
            case "price-desc" -> Sort.by(Sort.Direction.DESC, "priceCents");
            default -> Sort.by(Sort.Direction.DESC, "createTime"); // new
        };

        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(bool.build())))
                .withPageable(PageRequest.of(page - 1, size))
                .withSort(springSort)
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);
        List<Long> ids = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::getId)
                .toList();
        return new EsSearchResult(ids, hits.getTotalHits());
    }

    /** Sentinel fallback：ES 异常或熔断时返回 null，调用方降级 MySQL */
    @SuppressWarnings("unused")
    public EsSearchResult searchFallback(String keyword, Long categoryId, List<Long> tagIds,
                                         Integer minPriceCents, Integer maxPriceCents,
                                         String sort, int page, int size, Throwable t) {
        log.warn("ES search degraded to MySQL (keyword={}): {}", keyword, t == null ? "circuit open" : t.getMessage());
        return null;
    }
}
