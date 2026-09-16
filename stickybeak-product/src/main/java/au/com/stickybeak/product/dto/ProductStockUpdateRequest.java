package au.com.stickybeak.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public class ProductStockUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 新的目标库存值（若设置此项，则直接覆盖为该值）
     */
    @Min(value = 0, message = "Stock must not be negative")
    private Integer stock;

    /**
     * 增量变化量（例如 +10, -5，若 stock 为空则使用此 delta 累加）
     */
    private Integer delta;

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }
}
