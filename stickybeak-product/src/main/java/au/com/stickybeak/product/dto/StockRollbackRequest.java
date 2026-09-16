package au.com.stickybeak.product.dto;

import jakarta.validation.constraints.NotBlank;

public class StockRollbackRequest {

    @NotBlank
    private String orderNo;

    public StockRollbackRequest() {
    }

    public StockRollbackRequest(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
