package au.com.stickybeak.product.vo;

import java.util.List;

public class ProductVO {

    /** 媒体项（图/视频），按 sort_order 有序 */
    public record MediaItem(String url, String mediaType) {
    }

    private Long id;
    private String slug;
    private String name;
    private String description;
    private String category;
    private String categoryName;
    private Integer priceCents;
    private Integer stock;
    private Integer sales;
    private List<String> tags;
    /** 仅图片 URL（卡片/封面安全） */
    private List<String> images;
    /** 全量媒体（含视频），详情页画廊用 */
    private List<MediaItem> media;
    private String sourceNoteId;
    private Boolean featured;
    /** 关键词搜索命中时的高亮名称：已 HTML 转义，命中词用 &lt;em&gt; 包裹；非搜索结果为 null */
    private String highlightedName;

    public Long getId() {
        return id;
    }

    public String getHighlightedName() {
        return highlightedName;
    }

    public void setHighlightedName(String highlightedName) {
        this.highlightedName = highlightedName;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getSales() {
        return sales;
    }

    public void setSales(Integer sales) {
        this.sales = sales;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<MediaItem> getMedia() {
        return media;
    }

    public void setMedia(List<MediaItem> media) {
        this.media = media;
    }

    public String getSourceNoteId() {
        return sourceNoteId;
    }

    public void setSourceNoteId(String sourceNoteId) {
        this.sourceNoteId = sourceNoteId;
    }

    public Boolean getFeatured() {
        return featured;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }
}
