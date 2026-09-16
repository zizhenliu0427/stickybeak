package au.com.stickybeak.cart.vo;

import java.io.Serializable;

public class CartItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private String slug;
    private String name;
    private String imageUrl;
    private Integer priceCents;
    private Integer priceAtAddCents;
    private Integer qty;
    private Integer stock;

    public CartItemVO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getPriceAtAddCents() {
        return priceAtAddCents;
    }

    public void setPriceAtAddCents(Integer priceAtAddCents) {
        this.priceAtAddCents = priceAtAddCents;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
