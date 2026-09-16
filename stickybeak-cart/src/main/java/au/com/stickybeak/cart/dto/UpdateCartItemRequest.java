package au.com.stickybeak.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateCartItemRequest {

    @NotNull(message = "qty cannot be null")
    @Min(value = 1, message = "qty must be at least 1")
    private Integer qty;

    public UpdateCartItemRequest() {
    }

    public UpdateCartItemRequest(Integer qty) {
        this.qty = qty;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
}
