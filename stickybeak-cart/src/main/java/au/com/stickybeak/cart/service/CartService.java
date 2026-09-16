package au.com.stickybeak.cart.service;

import au.com.stickybeak.cart.dto.AddCartItemRequest;
import au.com.stickybeak.cart.dto.MergeCartRequest;
import au.com.stickybeak.cart.dto.UpdateCartItemRequest;
import au.com.stickybeak.cart.vo.CartVO;

public interface CartService {

    CartVO getCart(Long userId, String sessionId);

    CartVO addItem(Long userId, String sessionId, AddCartItemRequest req);

    CartVO updateItem(Long userId, String sessionId, Long itemId, UpdateCartItemRequest req);

    CartVO removeItem(Long userId, String sessionId, Long itemId);

    void clearCart(Long userId, String sessionId);

    CartVO mergeCart(Long userId, String sessionId, MergeCartRequest req);
}
