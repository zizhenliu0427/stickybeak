package au.com.stickybeak.product.vo;

import java.util.List;

public class ProductListVO {
    private List<ProductVO> items;
    private Long total;
    private Integer page;
    private Integer size;

    public static ProductListVO of(List<ProductVO> items, long total, int page, int size) {
        ProductListVO vo = new ProductListVO();
        vo.setItems(items);
        vo.setTotal(total);
        vo.setPage(page);
        vo.setSize(size);
        return vo;
    }

    public List<ProductVO> getItems() {
        return items;
    }

    public void setItems(List<ProductVO> items) {
        this.items = items;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
