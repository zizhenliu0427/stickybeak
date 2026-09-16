package au.com.stickybeak.cart.controller;

import au.com.stickybeak.cart.dto.AddCartItemRequest;
import au.com.stickybeak.cart.dto.MergeCartRequest;
import au.com.stickybeak.cart.dto.UpdateCartItemRequest;
import au.com.stickybeak.cart.service.CartService;
import au.com.stickybeak.cart.vo.CartVO;
import au.com.stickybeak.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/cart")
public class CartController {

    public static final String COOKIE_GUEST_SESSION = "sb_guest";
    public static final String HEADER_USER_ID = "X-User-Id";

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Result<CartVO> getCart(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @CookieValue(value = COOKIE_GUEST_SESSION, required = false) String sessionId,
            HttpServletResponse response) {

        Long userId = parseUserId(userIdHeader);
        sessionId = ensureSessionId(userId, sessionId, response);

        CartVO cart = cartService.getCart(userId, sessionId);
        return Result.ok(cart);
    }

    @PostMapping("/items")
    public Result<CartVO> addItem(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @CookieValue(value = COOKIE_GUEST_SESSION, required = false) String sessionId,
            @Valid @RequestBody AddCartItemRequest req,
            HttpServletResponse response) {

        Long userId = parseUserId(userIdHeader);
        sessionId = ensureSessionId(userId, sessionId, response);

        CartVO cart = cartService.addItem(userId, sessionId, req);
        return Result.ok(cart);
    }

    @PutMapping("/items/{itemId}")
    public Result<CartVO> updateItem(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @CookieValue(value = COOKIE_GUEST_SESSION, required = false) String sessionId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest req,
            HttpServletResponse response) {

        Long userId = parseUserId(userIdHeader);
        sessionId = ensureSessionId(userId, sessionId, response);

        CartVO cart = cartService.updateItem(userId, sessionId, itemId, req);
        return Result.ok(cart);
    }

    @DeleteMapping("/items/{itemId}")
    public Result<CartVO> removeItem(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @CookieValue(value = COOKIE_GUEST_SESSION, required = false) String sessionId,
            @PathVariable Long itemId,
            HttpServletResponse response) {

        Long userId = parseUserId(userIdHeader);
        sessionId = ensureSessionId(userId, sessionId, response);

        CartVO cart = cartService.removeItem(userId, sessionId, itemId);
        return Result.ok(cart);
    }

    @DeleteMapping
    public Result<Void> clearCart(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @CookieValue(value = COOKIE_GUEST_SESSION, required = false) String sessionId,
            HttpServletResponse response) {

        Long userId = parseUserId(userIdHeader);
        sessionId = ensureSessionId(userId, sessionId, response);

        cartService.clearCart(userId, sessionId);
        return Result.ok();
    }

    @PostMapping("/merge")
    public Result<CartVO> mergeCart(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @CookieValue(value = COOKIE_GUEST_SESSION, required = false) String sessionId,
            @RequestBody(required = false) MergeCartRequest req) {

        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            return Result.fail(401, "Sign in required to merge cart");
        }

        CartVO cart = cartService.mergeCart(userId, sessionId, req);
        return Result.ok(cart);
    }

    private Long parseUserId(String userIdHeader) {
        if (StringUtils.hasText(userIdHeader)) {
            try {
                return Long.valueOf(userIdHeader.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String ensureSessionId(Long userId, String sessionId, HttpServletResponse response) {
        if (userId == null && !StringUtils.hasText(sessionId)) {
            String newSessionId = UUID.randomUUID().toString().replace("-", "");
            ResponseCookie cookie = ResponseCookie.from(COOKIE_GUEST_SESSION, newSessionId)
                    .httpOnly(true)
                    .path("/")
                    .maxAge(Duration.ofDays(30))
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            return newSessionId;
        }
        return sessionId;
    }
}
