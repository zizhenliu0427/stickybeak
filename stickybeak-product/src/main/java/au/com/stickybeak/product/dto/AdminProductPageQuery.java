package au.com.stickybeak.product.dto;

import java.io.Serializable;

public class AdminProductPageQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer page = 1;
    private Integer size = 10;
    private String keyword;
    private Long categoryId;
    private Integer status; // 0: 上架, 1: 下架, null: 全部
    private Integer stockAlert; // 1: 全部预警(<=threshold), 2: 缺货(0), 3: 紧缺(1-3), 4: 预警(4-10)
    private Integer threshold = 10;

    public Integer getPage() {
        return page != null && page > 0 ? page : 1;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size != null && size > 0 && size <= 100 ? size : 10;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getStockAlert() {
        return stockAlert;
    }

    public void setStockAlert(Integer stockAlert) {
        this.stockAlert = stockAlert;
    }

    public Integer getThreshold() {
        return threshold != null && threshold > 0 ? threshold : 10;
    }

    public void setThreshold(Integer threshold) {
        this.threshold = threshold;
    }
}
