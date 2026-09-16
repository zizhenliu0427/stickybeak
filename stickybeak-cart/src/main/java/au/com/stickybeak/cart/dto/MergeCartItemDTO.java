package au.com.stickybeak.cart.dto;

public class MergeCartItemDTO {
    private Long productId;
    private Integer qty;
    private Integer priceCents;

    public MergeCartItemDTO() {
    }

    public MergeCartItemDTO(Long productId, Integer qty, Integer priceCents) {
        this.productId = productId;
        this.qty = qty;
        this.priceCents = priceCents;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }
}
