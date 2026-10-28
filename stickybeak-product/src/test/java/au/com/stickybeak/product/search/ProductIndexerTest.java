package au.com.stickybeak.product.search;

import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.entity.Product;
import au.com.stickybeak.product.entity.ProductTagRel;
import au.com.stickybeak.product.entity.Tag;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.mapper.ProductMapper;
import au.com.stickybeak.product.mapper.ProductTagRelMapper;
import au.com.stickybeak.product.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductIndexerTest {

    @Mock
    private ElasticsearchOperations operations;
    @Mock
    private IndexOperations indexOperations;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ProductTagRelMapper productTagRelMapper;
    @Captor
    private ArgumentCaptor<Iterable<ProductDocument>> documentsCaptor;
    @Captor
    private ArgumentCaptor<ProductDocument> documentCaptor;

    private ProductIndexer indexer;

    @BeforeEach
    void setUp() {
        lenient().when(operations.indexOps(ProductDocument.class)).thenReturn(indexOperations);
        indexer = new ProductIndexer(operations, productMapper, categoryMapper, tagMapper, productTagRelMapper);
    }

    private static Product product(Long id, Long categoryId) {
        Product p = new Product();
        p.setId(id);
        p.setName("Magnet " + id);
        p.setDescription("desc " + id);
        p.setSlug("magnet-" + id);
        p.setCategoryId(categoryId);
        p.setPrice(new BigDecimal("12.50"));
        p.setSales(3);
        p.setStock(40);
        p.setStatus(0);
        return p;
    }

    private static Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static Tag tag(Long id, String name) {
        Tag t = new Tag();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static ProductTagRel rel(Long productId, Long tagId) {
        ProductTagRel r = new ProductTagRel();
        r.setProductId(productId);
        r.setTagId(tagId);
        return r;
    }

    @Test
    void toDocument_copiesSearchFields() {
        Product p = product(1L, 9L);
        LocalDateTime created = LocalDateTime.of(2026, 5, 1, 10, 0);
        p.setCreateTime(created);

        ProductDocument doc = ProductIndexer.toDocument(p, "Bus Signs", List.of("UNSW"));

        assertEquals(1L, doc.getId());
        assertEquals("Magnet 1", doc.getName());
        assertEquals("Bus Signs", doc.getCategoryName());
        assertEquals(List.of("UNSW"), doc.getTags());
        assertEquals(12.5, doc.getPrice());
        assertEquals(0, doc.getStatus());
        assertEquals(created.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), doc.getCreatedAt());
    }

    @Test
    void toDocument_toleratesMissingPriceAndCreateTime() {
        Product p = product(1L, 9L);
        p.setPrice(null);

        ProductDocument doc = ProductIndexer.toDocument(p, null, List.of());

        assertNull(doc.getPrice());
        assertNull(doc.getCreatedAt());
    }

    @Test
    void reindexAll_createsMissingIndexRemovesStaleDocsAndBulkSaves() {
        when(indexOperations.exists()).thenReturn(false);
        when(productMapper.selectList(any())).thenReturn(List.of(product(1L, 9L), product(2L, 8L)));
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(9L, "Bus Signs")));
        when(tagMapper.selectList(any())).thenReturn(List.of(tag(100L, "UNSW"), tag(101L, "Sydney")));
        when(productTagRelMapper.selectList(any())).thenReturn(List.of(rel(1L, 100L), rel(1L, 101L), rel(2L, 999L)));

        int count = indexer.reindexAll();

        assertEquals(2, count);
        verify(indexOperations).createWithMapping();
        verify(operations).delete(any(DeleteQuery.class), eq(ProductDocument.class));
        verify(operations).save(documentsCaptor.capture());
        List<ProductDocument> docs = new ArrayList<>();
        documentsCaptor.getValue().forEach(docs::add);
        assertEquals(2, docs.size());
        assertEquals("Bus Signs", docs.get(0).getCategoryName());
        assertEquals(List.of("UNSW", "Sydney"), docs.get(0).getTags());
        // 分类缺失、标签已被删除时不报错，只是字段为空
        assertNull(docs.get(1).getCategoryName());
        assertEquals(List.of(), docs.get(1).getTags());
    }

    @Test
    void reindexAll_withNoProducts_clearsIndexWithoutSaving() {
        when(indexOperations.exists()).thenReturn(true);
        when(productMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, indexer.reindexAll());

        verify(indexOperations, never()).createWithMapping();
        verify(operations).delete(any(DeleteQuery.class), eq(ProductDocument.class));
        verify(operations, never()).save(any(Iterable.class));
    }

    @Test
    void indexProduct_savesDocumentWithCategoryAndTags() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 9L));
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, "Bus Signs"));
        when(productTagRelMapper.selectList(any())).thenReturn(List.of(rel(1L, 100L)));
        when(tagMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(tag(100L, "UNSW")));

        indexer.indexProduct(1L);

        verify(operations).save(documentCaptor.capture());
        assertEquals("Bus Signs", documentCaptor.getValue().getCategoryName());
        assertEquals(List.of("UNSW"), documentCaptor.getValue().getTags());
    }

    @Test
    void indexProduct_withoutTags_skipsTagLookup() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, null));
        when(productTagRelMapper.selectList(any())).thenReturn(List.of());

        indexer.indexProduct(1L);

        verify(tagMapper, never()).selectBatchIds(any());
        verify(operations).save(documentCaptor.capture());
        assertNull(documentCaptor.getValue().getCategoryName());
    }

    @Test
    void indexProduct_whenProductDeleted_removesDocument() {
        when(productMapper.selectById(5L)).thenReturn(null);

        indexer.indexProduct(5L);

        verify(operations).delete("5", ProductDocument.class);
        verify(operations, never()).save(any(ProductDocument.class));
    }

    @Test
    void onProductChanged_whenElasticsearchDown_doesNotPropagate() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, null));
        when(productTagRelMapper.selectList(any())).thenReturn(List.of());
        when(operations.save(any(ProductDocument.class))).thenThrow(new IllegalStateException("down"));

        indexer.onProductChanged(new ProductChangedEvent(1L));
    }

    @Test
    void scheduledAndStartupReindex_swallowFailures() {
        when(operations.indexOps(ProductDocument.class)).thenThrow(new IllegalStateException("down"));

        indexer.reindexOnStartup();
        indexer.scheduledReindex();
    }
}
