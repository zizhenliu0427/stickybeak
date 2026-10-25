package au.com.stickybeak.product.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;

import java.util.List;

/**
 * 商品搜索文档。只放检索、筛选、排序用的字段；展示数据（图片、实时库存）仍从 MySQL 读取。
 * 索引由 {@link ProductIndexer} 显式创建（IK 分词，见 product-mapping.json），不随启动自动建。
 */
@Document(indexName = ProductDocument.INDEX, createIndex = false)
@Mapping(mappingPath = "/elasticsearch/product-mapping.json")
public class ProductDocument {

    public static final String INDEX = "stickybeak-products";

    @Id
    private Long id;
    private String name;
    private String description;
    private String slug;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private Double price;
    private Integer sales;
    private Integer stock;
    private Integer status;
    /** 上架时间（epoch millis），用于"最新"排序 */
    private Long createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getSales() {
        return sales;
    }

    public void setSales(Integer sales) {
        this.sales = sales;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
