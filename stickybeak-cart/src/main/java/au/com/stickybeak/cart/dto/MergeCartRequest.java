package au.com.stickybeak.cart.dto;

import java.util.ArrayList;
import java.util.List;

public class MergeCartRequest {
    private List<MergeCartItemDTO> items = new ArrayList<>();

    public MergeCartRequest() {
    }

    public MergeCartRequest(List<MergeCartItemDTO> items) {
        this.items = items;
    }

    public List<MergeCartItemDTO> getItems() {
        return items;
    }

    public void setItems(List<MergeCartItemDTO> items) {
        this.items = items;
    }
}
