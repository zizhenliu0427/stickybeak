package au.com.stickybeak.common.event;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderItemEventDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal price;
    private Integer qty;

    public OrderItemEventDTO() {
    }

    public OrderItemEventDTO(Long productId, String productName, String productImage, BigDecimal price, Integer qty) {
        this.productId = productId;
        this.productName = productName;
        this.productImage = productImage;
        this.price = price;
        this.qty = qty;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductImage() {
        return productImage;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
}
