package au.com.stickybeak.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class AddCartItemRequest {

    @NotNull(message = "productId cannot be null")
    private Long productId;

    @NotNull(message = "qty cannot be null")
    @Min(value = 1, message = "qty must be at least 1")
    private Integer qty;

    public AddCartItemRequest() {
    }

    public AddCartItemRequest(Long productId, Integer qty) {
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
