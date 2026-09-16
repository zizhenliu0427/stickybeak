package au.com.stickybeak.cart.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CartVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private List<CartItemVO> items = new ArrayList<>();
    private Integer totalCents = 0;
    private Integer itemCount = 0;

    public CartVO() {
    }

    public CartVO(Long id, List<CartItemVO> items) {
        this.id = id;
        this.items = items != null ? items : new ArrayList<>();
        calculateTotals();
    }

    public void calculateTotals() {
        int total = 0;
        int count = 0;
        if (items != null) {
            for (CartItemVO item : items) {
                int price = item.getPriceCents() != null ? item.getPriceCents() : 0;
                int q = item.getQty() != null ? item.getQty() : 0;
                total += price * q;
                count += q;
            }
        }
        this.totalCents = total;
        this.itemCount = count;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<CartItemVO> getItems() {
        return items;
    }

    public void setItems(List<CartItemVO> items) {
        this.items = items;
        calculateTotals();
    }

    public Integer getTotalCents() {
        return totalCents;
    }

    public void setTotalCents(Integer totalCents) {
        this.totalCents = totalCents;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }
}
