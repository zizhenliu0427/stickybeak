package au.com.stickybeak.cart.service.impl;

import au.com.stickybeak.cart.client.ProductClient;
import au.com.stickybeak.cart.client.ProductDTO;
import au.com.stickybeak.cart.dto.AddCartItemRequest;
import au.com.stickybeak.cart.dto.MergeCartItemDTO;
import au.com.stickybeak.cart.dto.MergeCartRequest;
import au.com.stickybeak.cart.dto.UpdateCartItemRequest;
import au.com.stickybeak.cart.entity.Cart;
import au.com.stickybeak.cart.entity.CartItem;
import au.com.stickybeak.cart.mapper.CartItemMapper;
import au.com.stickybeak.cart.mapper.CartMapper;
import au.com.stickybeak.cart.service.CartService;
import au.com.stickybeak.cart.vo.CartItemVO;
import au.com.stickybeak.cart.vo.CartVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private static final String CACHE_USER_PREFIX = "cart:user:";
    private static final String CACHE_SESSION_PREFIX = "cart:session:";
    private static final long CACHE_TTL_SECONDS = 300;

    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductClient productClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CartServiceImpl(CartMapper cartMapper,
                           CartItemMapper cartItemMapper,
                           ProductClient productClient,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper) {
        this.cartMapper = cartMapper;
        this.cartItemMapper = cartItemMapper;
        this.productClient = productClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public CartVO getCart(Long userId, String sessionId) {
        if (userId == null && !StringUtils.hasText(sessionId)) {
            return new CartVO(null, Collections.emptyList());
        }

        // 1. Try Redis cache
        CartVO cached = getFromCache(userId, sessionId);
        if (cached != null) {
            return cached;
        }

        // 2. Query or create Cart
        Cart cart = findCart(userId, sessionId);
        if (cart == null) {
            return new CartVO(null, Collections.emptyList());
        }

        // 3. Build VO and cache
        CartVO vo = buildCartVO(cart);
        putToCache(userId, sessionId, vo);
        return vo;
    }

    @Override
    @Transactional
    public CartVO addItem(Long userId, String sessionId, AddCartItemRequest req) {
        ProductDTO product = productClient.getById(req.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Product not found");
        }
        int availableStock = product.getStock() != null ? product.getStock() : 0;
        if (availableStock <= 0) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "Product is currently out of stock");
        }

        Cart cart = getOrCreateCart(userId, sessionId);

        LambdaQueryWrapper<CartItem> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.eq(CartItem::getCartId, cart.getId())
                .eq(CartItem::getProductId, req.getProductId());
        CartItem existingItem = cartItemMapper.selectOne(itemQuery);

        if (existingItem != null) {
            if (existingItem.getQty() >= availableStock) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                        "Maximum available stock reached (" + availableStock + ")");
            }
            int newQty = Math.min(availableStock, existingItem.getQty() + req.getQty());
            existingItem.setQty(newQty);
            cartItemMapper.updateById(existingItem);
        } else {
            int newQty = Math.min(availableStock, req.getQty());
            CartItem newItem = new CartItem();
            newItem.setCartId(cart.getId());
            newItem.setProductId(req.getProductId());
            newItem.setQty(newQty);
            BigDecimal price = product.getPriceCents() != null
                    ? BigDecimal.valueOf(product.getPriceCents()).divide(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            newItem.setPriceAtAdd(price);
            cartItemMapper.insert(newItem);
        }

        evictCache(userId, sessionId);
        return buildCartVO(cart);
    }

    @Override
    @Transactional
    public CartVO updateItem(Long userId, String sessionId, Long itemId, UpdateCartItemRequest req) {
        Cart cart = findCart(userId, sessionId);
        if (cart == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Cart not found");
        }

        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !cart.getId().equals(item.getCartId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Cart item not found");
        }

        ProductDTO product = productClient.getById(item.getProductId());
        int availableStock = (product != null && product.getStock() != null) ? product.getStock() : 0;
        if (req.getQty() > availableStock) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                    "Requested quantity (" + req.getQty() + ") exceeds available stock (" + availableStock + ")");
        }

        item.setQty(req.getQty());
        cartItemMapper.updateById(item);

        evictCache(userId, sessionId);
        return buildCartVO(cart);
    }

    @Override
    @Transactional
    public CartVO removeItem(Long userId, String sessionId, Long itemId) {
        Cart cart = findCart(userId, sessionId);
        if (cart == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Cart not found");
        }

        CartItem item = cartItemMapper.selectById(itemId);
        if (item != null && cart.getId().equals(item.getCartId())) {
            cartItemMapper.deleteById(itemId);
        }

        evictCache(userId, sessionId);
        return buildCartVO(cart);
    }

    @Override
    @Transactional
    public void clearCart(Long userId, String sessionId) {
        Cart cart = findCart(userId, sessionId);
        if (cart != null) {
            LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CartItem::getCartId, cart.getId());
            cartItemMapper.delete(wrapper);
            evictCache(userId, sessionId);
        }
    }

    @Override
    @Transactional
    public CartVO mergeCart(Long userId, String sessionId, MergeCartRequest req) {
        if (userId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "User must be authenticated to merge cart");
        }

        Cart userCart = getOrCreateCart(userId, null);

        // Map to hold incoming guest items: productId -> requested total qty
        Map<Long, Integer> guestItemQuantities = new HashMap<>();

        // 1. Collect from MergeCartRequest (localStorage items sent from browser)
        if (req != null && req.getItems() != null) {
            for (MergeCartItemDTO item : req.getItems()) {
                if (item.getProductId() != null && item.getQty() != null && item.getQty() > 0) {
                    guestItemQuantities.merge(item.getProductId(), item.getQty(), Integer::sum);
                }
            }
        }

        // 2. Collect from DB guest cart if sessionId is present
        Cart guestCart = null;
        if (StringUtils.hasText(sessionId)) {
            guestCart = findCart(null, sessionId);
            if (guestCart != null) {
                LambdaQueryWrapper<CartItem> guestWrapper = new LambdaQueryWrapper<>();
                guestWrapper.eq(CartItem::getCartId, guestCart.getId());
                List<CartItem> dbGuestItems = cartItemMapper.selectList(guestWrapper);
                for (CartItem item : dbGuestItems) {
                    guestItemQuantities.merge(item.getProductId(), item.getQty(), Integer::sum);
                }
            }
        }

        // If there are no guest items to merge, simply return current user cart
        if (guestItemQuantities.isEmpty()) {
            return buildCartVO(userCart);
        }

        // 3. Batch query product information for all incoming products
        Map<Long, ProductDTO> productMap = productClient.getByIds(guestItemQuantities.keySet());

        // 4. Query existing user cart items
        LambdaQueryWrapper<CartItem> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(CartItem::getCartId, userCart.getId());
        List<CartItem> existingUserItems = cartItemMapper.selectList(userWrapper);
        Map<Long, CartItem> existingUserItemMap = existingUserItems.stream()
                .collect(Collectors.toMap(CartItem::getProductId, i -> i, (a, b) -> a));

        // 5. Merge logic: add or update with stock capping
        for (Map.Entry<Long, Integer> entry : guestItemQuantities.entrySet()) {
            Long productId = entry.getKey();
            int guestQty = entry.getValue();

            ProductDTO prod = productMap.get(productId);
            if (prod == null) {
                // Fetch individually if batch missed it
                prod = productClient.getById(productId);
            }
            int stock = (prod != null && prod.getStock() != null) ? prod.getStock() : 999;
            if (stock <= 0) {
                continue; // Skip out-of-stock items
            }

            CartItem existing = existingUserItemMap.get(productId);
            if (existing != null) {
                int mergedQty = Math.min(stock, existing.getQty() + guestQty);
                existing.setQty(mergedQty);
                cartItemMapper.updateById(existing);
            } else {
                int initialQty = Math.min(stock, guestQty);
                CartItem newItem = new CartItem();
                newItem.setCartId(userCart.getId());
                newItem.setProductId(productId);
                newItem.setQty(initialQty);
                BigDecimal price = (prod != null && prod.getPriceCents() != null)
                        ? BigDecimal.valueOf(prod.getPriceCents()).divide(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
                newItem.setPriceAtAdd(price);
                cartItemMapper.insert(newItem);
            }
        }

        // 6. Delete DB guest cart if it existed
        if (guestCart != null) {
            LambdaQueryWrapper<CartItem> delWrapper = new LambdaQueryWrapper<>();
            delWrapper.eq(CartItem::getCartId, guestCart.getId());
            cartItemMapper.delete(delWrapper);
            cartMapper.deleteById(guestCart.getId());
        }

        // 7. Evict caches
        evictCache(userId, null);
        if (StringUtils.hasText(sessionId)) {
            evictCache(null, sessionId);
        }

        return buildCartVO(userCart);
    }

    private Cart findCart(Long userId, String sessionId) {
        LambdaQueryWrapper<Cart> query = new LambdaQueryWrapper<>();
        if (userId != null) {
            query.eq(Cart::getUserId, userId);
        } else if (StringUtils.hasText(sessionId)) {
            query.eq(Cart::getSessionId, sessionId);
        } else {
            return null;
        }
        return cartMapper.selectOne(query);
    }

    private Cart getOrCreateCart(Long userId, String sessionId) {
        Cart cart = findCart(userId, sessionId);
        if (cart != null) {
            return cart;
        }

        Cart newCart = new Cart();
        if (userId != null) {
            newCart.setUserId(userId);
        } else {
            newCart.setSessionId(sessionId);
        }
        cartMapper.insert(newCart);
        return newCart;
    }

    private CartVO buildCartVO(Cart cart) {
        LambdaQueryWrapper<CartItem> query = new LambdaQueryWrapper<>();
        query.eq(CartItem::getCartId, cart.getId())
                .orderByDesc(CartItem::getCreateTime);
        List<CartItem> items = cartItemMapper.selectList(query);

        if (items == null || items.isEmpty()) {
            return new CartVO(cart.getId(), Collections.emptyList());
        }

        Set<Long> productIds = items.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<Long, ProductDTO> productMap = productClient.getByIds(productIds);

        List<CartItemVO> voList = new ArrayList<>();
        for (CartItem item : items) {
            ProductDTO p = productMap.get(item.getProductId());
            if (p == null) {
                p = productClient.getById(item.getProductId());
            }

            CartItemVO vo = new CartItemVO();
            vo.setId(item.getId());
            vo.setProductId(item.getProductId());
            vo.setQty(item.getQty());
            if (item.getPriceAtAdd() != null) {
                vo.setPriceAtAddCents(item.getPriceAtAdd().multiply(BigDecimal.valueOf(100)).intValue());
            }

            if (p != null) {
                vo.setName(p.getName());
                vo.setSlug(p.getSlug());
                vo.setImageUrl(p.getCoverImage());
                vo.setPriceCents(p.getPriceCents());
                vo.setStock(p.getStock());
            } else {
                vo.setName("Product #" + item.getProductId());
                vo.setSlug(String.valueOf(item.getProductId()));
                vo.setPriceCents(vo.getPriceAtAddCents() != null ? vo.getPriceAtAddCents() : 0);
                vo.setStock(0);
            }
            voList.add(vo);
        }

        return new CartVO(cart.getId(), voList);
    }

    private String getCacheKey(Long userId, String sessionId) {
        if (userId != null) {
            return CACHE_USER_PREFIX + userId;
        } else if (StringUtils.hasText(sessionId)) {
            return CACHE_SESSION_PREFIX + sessionId;
        }
        return null;
    }

    private CartVO getFromCache(Long userId, String sessionId) {
        String key = getCacheKey(userId, sessionId);
        if (key == null) return null;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (StringUtils.hasText(json)) {
                return objectMapper.readValue(json, CartVO.class);
            }
        } catch (Exception e) {
            log.debug("Redis cache read skipped: {}", e.getMessage());
        }
        return null;
    }

    private void putToCache(Long userId, String sessionId, CartVO vo) {
        String key = getCacheKey(userId, sessionId);
        if (key == null || vo == null) return;
        try {
            String json = objectMapper.writeValueAsString(vo);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis cache write skipped: {}", e.getMessage());
        }
    }

    private void evictCache(Long userId, String sessionId) {
        String key = getCacheKey(userId, sessionId);
        if (key != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.debug("Redis cache delete skipped: {}", e.getMessage());
            }
        }
    }
}
