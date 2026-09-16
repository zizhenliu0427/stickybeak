package au.com.stickybeak.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class StockDeductRequest {

    @NotBlank
    private String orderNo;

    @NotEmpty
    @Valid
    private List<StockItemDTO> items;

    public StockDeductRequest() {
    }

    public StockDeductRequest(String orderNo, List<StockItemDTO> items) {
        this.orderNo = orderNo;
        this.items = items;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public List<StockItemDTO> getItems() {
        return items;
    }

    public void setItems(List<StockItemDTO> items) {
        this.items = items;
    }
}
