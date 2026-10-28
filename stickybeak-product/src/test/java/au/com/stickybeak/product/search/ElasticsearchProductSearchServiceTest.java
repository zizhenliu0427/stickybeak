package au.com.stickybeak.product.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchProductSearchServiceTest {

    @Mock
    private ElasticsearchOperations operations;

    private static ProductSearchService.Criteria criteria(String sort) {
        return new ProductSearchService.Criteria("公交 路牌", null, List.of(), null, null, sort, 1, 12);
    }

    @Test
    void buildQuery_keywordOnly_matchesWeightedFieldsAndOnlyOnSaleProducts() {
        NativeQuery query = ElasticsearchProductSearchService.buildQuery(criteria(null));

        BoolQuery bool = query.getQuery().bool();
        assertEquals("公交 路牌", bool.must().get(0).multiMatch().query());
        assertEquals(ElasticsearchProductSearchService.SEARCH_FIELDS, bool.must().get(0).multiMatch().fields());
        assertEquals(1, bool.filter().size());
        assertEquals("status", bool.filter().get(0).term().field());
        assertEquals(0L, bool.filter().get(0).term().value().longValue());

        assertEquals(0, query.getPageable().getPageNumber());
        assertEquals(12, query.getPageable().getPageSize());
        assertTrue(query.getHighlightQuery().isPresent());
        assertTrue(query.getTrackTotalHits());
    }

    @Test
    void buildQuery_withFilters_addsCategoryTagsAndPriceRange() {
        ProductSearchService.Criteria c = new ProductSearchService.Criteria("magnet", 7L, List.of("UNSW", "Sydney"),
                new BigDecimal("5.00"), new BigDecimal("20.50"), "price-asc", 3, 10);

        NativeQuery query = ElasticsearchProductSearchService.buildQuery(c);

        List<Query> filters = query.getQuery().bool().filter();
        assertEquals(4, filters.size());
        assertEquals("categoryId", filters.get(1).term().field());
        assertEquals(7L, filters.get(1).term().value().longValue());
        assertEquals("tags", filters.get(2).terms().field());
        assertEquals(List.of("UNSW", "Sydney"),
                filters.get(2).terms().terms().value().stream().map(FieldValue::stringValue).toList());
        assertEquals("price", filters.get(3).range().field());
        assertEquals(5.0, filters.get(3).range().gte().to(Double.class));
        assertEquals(20.5, filters.get(3).range().lte().to(Double.class));

        // 第 3 页、每页 10 条 → ES from = 20
        assertEquals(2, query.getPageable().getPageNumber());
        assertEquals(Sort.by(Sort.Direction.ASC, "price"), query.getPageable().getSort());
    }

    @Test
    void buildQuery_onlyMaxPrice_leavesLowerBoundOpen() {
        ProductSearchService.Criteria c = new ProductSearchService.Criteria("magnet", null, null,
                null, new BigDecimal("9.99"), null, 1, 12);

        Query range = ElasticsearchProductSearchService.buildQuery(c).getQuery().bool().filter().get(1);

        assertEquals(null, range.range().gte());
        assertEquals(9.99, range.range().lte().to(Double.class));
    }

    @Test
    void toSort_mapsListSortParametersAndDefaultsToRelevance() {
        assertTrue(ElasticsearchProductSearchService.toSort(null).isUnsorted());
        assertTrue(ElasticsearchProductSearchService.toSort("relevance").isUnsorted());
        assertTrue(ElasticsearchProductSearchService.toSort("unknown").isUnsorted());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), ElasticsearchProductSearchService.toSort("new"));
        assertEquals(Sort.by(Sort.Direction.DESC, "sales"), ElasticsearchProductSearchService.toSort("sales"));
        assertEquals(Sort.by(Sort.Direction.ASC, "price"), ElasticsearchProductSearchService.toSort("price-asc"));
        assertEquals(Sort.by(Sort.Direction.DESC, "price"), ElasticsearchProductSearchService.toSort("price-desc"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returnsIdsInHitOrderWithHighlights() {
        SearchHit<ProductDocument> first = mock(SearchHit.class);
        when(first.getId()).thenReturn("42");
        when(first.getHighlightField("name")).thenReturn(List.of("UNSW <em>公交</em>路牌"));
        SearchHit<ProductDocument> second = mock(SearchHit.class);
        when(second.getId()).thenReturn("7");
        when(second.getHighlightField("name")).thenReturn(List.of());
        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(first, second));
        when(hits.getTotalHits()).thenReturn(25L);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(hits);

        Optional<ProductSearchService.Result> result =
                new ElasticsearchProductSearchService(operations).search(criteria(null));

        assertTrue(result.isPresent());
        assertEquals(List.of(42L, 7L), result.get().ids());
        assertEquals(25L, result.get().total());
        assertEquals("UNSW <em>公交</em>路牌", result.get().highlightedNames().get(42L));
        assertFalse(result.get().highlightedNames().containsKey(7L));
    }

    @Test
    void search_whenElasticsearchFails_returnsEmptySoCallerFallsBack() {
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class)))
                .thenThrow(new IllegalStateException("connection refused"));

        Optional<ProductSearchService.Result> result =
                new ElasticsearchProductSearchService(operations).search(criteria(null));

        assertTrue(result.isEmpty());
    }
}
