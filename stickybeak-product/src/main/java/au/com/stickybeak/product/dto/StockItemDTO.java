package au.com.stickybeak.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class StockItemDTO {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer qty;

    public StockItemDTO() {
    }

    public StockItemDTO(Long productId, Integer qty) {
        this.productId = productId;
        this.qty = qty;
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
}
