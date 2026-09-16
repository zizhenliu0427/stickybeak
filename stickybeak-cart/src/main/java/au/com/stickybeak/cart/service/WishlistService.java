package au.com.stickybeak.cart.service;

import au.com.stickybeak.cart.vo.WishlistVO;

import java.util.List;

public interface WishlistService {

    List<WishlistVO> listWishlist(Long userId);

    boolean toggleWishlist(Long userId, Long productId);

    void moveToCart(Long userId, String sessionId, Long productId);
}
