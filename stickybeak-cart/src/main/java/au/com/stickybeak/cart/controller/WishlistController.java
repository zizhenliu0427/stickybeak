package au.com.stickybeak.cart.controller;

import au.com.stickybeak.cart.service.WishlistService;
import au.com.stickybeak.cart.vo.WishlistVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wishlist")
public class WishlistController {

    public static final String HEADER_USER_ID = "X-User-Id";

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public Result<List<WishlistVO>> list(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader) {
        Long userId = requireUserId(userIdHeader);
        return Result.ok(wishlistService.listWishlist(userId));
    }

    @PostMapping("/toggle/{productId}")
    public Result<Map<String, Object>> toggle(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @PathVariable Long productId) {
        Long userId = requireUserId(userIdHeader);
        boolean added = wishlistService.toggleWishlist(userId, productId);
        return Result.ok(Map.of("productId", productId, "inWishlist", added));
    }

    @PostMapping("/move-to-cart/{productId}")
    public Result<Void> moveToCart(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @CookieValue(value = CartController.COOKIE_GUEST_SESSION, required = false) String sessionId,
            @PathVariable Long productId) {
        Long userId = requireUserId(userIdHeader);
        wishlistService.moveToCart(userId, sessionId, productId);
        return Result.ok();
    }

    private Long requireUserId(String userIdHeader) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Sign in required for wishlist");
        }
        try {
            return Long.valueOf(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid user identifier");
        }
    }
}
