package au.com.stickybeak.cart.service;

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
import au.com.stickybeak.cart.service.impl.CartServiceImpl;
import au.com.stickybeak.cart.vo.CartVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartMapper cartMapper;

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private ProductClient productClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private ProductDTO createSampleProduct(Long id, String name, int priceCents, int stock) {
        ProductDTO p = new ProductDTO();
        p.setId(id);
        p.setSlug("prod-" + id);
        p.setName(name);
        p.setPriceCents(priceCents);
        p.setStock(stock);
        p.setImages(List.of("/images/" + id + ".jpg"));
        return p;
    }

    @Test
    void addItem_newItem_insertsSuccessfully() {
        ProductDTO p = createSampleProduct(101L, "Sydney Bus Sign", 1500, 10);
        when(productClient.getById(101L)).thenReturn(p);

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(cart);
        when(cartItemMapper.selectByCartAndProductRaw(1L, 101L)).thenReturn(null);

        // When building CartVO after insert
        CartItem item = new CartItem();
        item.setId(10L);
        item.setCartId(1L);
        item.setProductId(101L);
        item.setQty(2);
        item.setPriceAtAdd(new BigDecimal("15.00"));
        when(cartItemMapper.selectList(any())).thenReturn(List.of(item));
        when(productClient.getByIds(any())).thenReturn(Map.of(101L, p));

        AddCartItemRequest req = new AddCartItemRequest(101L, 2);
        CartVO result = cartService.addItem(200L, null, req);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(2, result.getItems().get(0).getQty());
        assertEquals(3000, result.getTotalCents());
        verify(cartItemMapper).insert(any(CartItem.class));
    }

    @Test
    void addItem_outOfStock_throwsException() {
        ProductDTO p = createSampleProduct(101L, "Sydney Bus Sign", 1500, 0);
        when(productClient.getById(101L)).thenReturn(p);

        AddCartItemRequest req = new AddCartItemRequest(101L, 1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> cartService.addItem(200L, null, req));
        assertEquals(ResultCode.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
    }

    @Test
    void addItem_existingItem_cumulatesQuantityCappedAtStock() {
        ProductDTO p = createSampleProduct(101L, "Sydney Bus Sign", 1500, 5);
        when(productClient.getById(101L)).thenReturn(p);

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(cart);

        CartItem existing = new CartItem();
        existing.setId(10L);
        existing.setCartId(1L);
        existing.setProductId(101L);
        existing.setQty(3);
        existing.setPriceAtAdd(new BigDecimal("15.00"));
        when(cartItemMapper.selectByCartAndProductRaw(1L, 101L)).thenReturn(existing);

        when(cartItemMapper.selectList(any())).thenReturn(List.of(existing));
        when(productClient.getByIds(any())).thenReturn(Map.of(101L, p));

        // Adding 4 more when current is 3 and stock is 5 -> should cap at 5
        AddCartItemRequest req = new AddCartItemRequest(101L, 4);
        CartVO result = cartService.addItem(200L, null, req);

        assertNotNull(result);
        assertEquals(5, existing.getQty());
        verify(cartItemMapper).updateById(existing);
    }

    @Test
    void updateItem_exceedsStock_throwsException() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(cart);

        CartItem item = new CartItem();
        item.setId(10L);
        item.setCartId(1L);
        item.setProductId(101L);
        item.setQty(2);
        when(cartItemMapper.selectById(10L)).thenReturn(item);

        ProductDTO p = createSampleProduct(101L, "Sydney Bus Sign", 1500, 5);
        when(productClient.getById(101L)).thenReturn(p);

        UpdateCartItemRequest req = new UpdateCartItemRequest(10); // requests 10, stock is 5
        BusinessException ex = assertThrows(BusinessException.class,
                () -> cartService.updateItem(200L, null, 10L, req));
        assertEquals(ResultCode.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
    }

    @Test
    void removeItem_deletesItem() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(cart);

        CartItem item = new CartItem();
        item.setId(10L);
        item.setCartId(1L);
        item.setProductId(101L);
        when(cartItemMapper.selectById(10L)).thenReturn(item);
        when(cartItemMapper.selectList(any())).thenReturn(List.of());

        CartVO result = cartService.removeItem(200L, null, 10L);
        assertNotNull(result);
        verify(cartItemMapper).deleteByIdPhysical(10L);
    }

    // ==============================================================
    // ⭐ Merge Cart 4 Scenarios (Issue 3.3)
    // ==============================================================

    @Test
    void mergeCart_scenario1_onlyGuestItems() {
        // Scenario 1: User cart was empty, guest items are transferred to user cart
        Cart userCart = new Cart();
        userCart.setId(1L);
        userCart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(userCart);

        // User cart currently has no items
        when(cartItemMapper.selectList(any())).thenReturn(new ArrayList<>());

        ProductDTO p1 = createSampleProduct(101L, "Product A", 1000, 10);
        when(productClient.getByIds(any())).thenReturn(Map.of(101L, p1));

        MergeCartRequest req = new MergeCartRequest(List.of(
                new MergeCartItemDTO(101L, 2, 1000)
        ));

        CartVO result = cartService.mergeCart(200L, null, req);
        assertNotNull(result);
        verify(cartItemMapper).insert(any(CartItem.class));
    }

    @Test
    void mergeCart_scenario2_onlyUserItems() {
        // Scenario 2: No guest items in request or DB, user cart remains unchanged
        Cart userCart = new Cart();
        userCart.setId(1L);
        userCart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(userCart);

        CartItem userItem = new CartItem();
        userItem.setId(1L);
        userItem.setCartId(1L);
        userItem.setProductId(101L);
        userItem.setQty(2);
        userItem.setPriceAtAdd(new BigDecimal("10.00"));
        when(cartItemMapper.selectList(any())).thenReturn(List.of(userItem));

        ProductDTO p1 = createSampleProduct(101L, "Product A", 1000, 10);
        when(productClient.getByIds(any())).thenReturn(Map.of(101L, p1));

        MergeCartRequest req = new MergeCartRequest(List.of()); // empty guest cart

        CartVO result = cartService.mergeCart(200L, null, req);
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(2, result.getItems().get(0).getQty());
        verify(cartItemMapper, never()).insert(any(CartItem.class));
        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    @Test
    void mergeCart_scenario3_bothNonOverlapping() {
        // Scenario 3: Both user and guest have items, non-overlapping -> both kept
        Cart userCart = new Cart();
        userCart.setId(1L);
        userCart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(userCart);

        CartItem userItem = new CartItem();
        userItem.setId(1L);
        userItem.setCartId(1L);
        userItem.setProductId(101L);
        userItem.setQty(1);
        userItem.setPriceAtAdd(new BigDecimal("10.00"));

        when(cartItemMapper.selectList(any())).thenReturn(List.of(userItem));

        ProductDTO p2 = createSampleProduct(102L, "Product B", 2000, 10);
        when(productClient.getByIds(any())).thenReturn(Map.of(102L, p2));

        MergeCartRequest req = new MergeCartRequest(List.of(
                new MergeCartItemDTO(102L, 3, 2000)
        ));

        CartVO result = cartService.mergeCart(200L, null, req);
        assertNotNull(result);
        // Product 102 inserted as new item
        verify(cartItemMapper).insert(any(CartItem.class));
    }

    @Test
    void mergeCart_scenario4_bothOverlapping_sumsQtyCappedAtStock() {
        // Scenario 4: Overlapping items -> quantity summed and capped by stock
        Cart userCart = new Cart();
        userCart.setId(1L);
        userCart.setUserId(200L);
        when(cartMapper.selectOne(any())).thenReturn(userCart);

        CartItem userItem = new CartItem();
        userItem.setId(1L);
        userItem.setCartId(1L);
        userItem.setProductId(101L);
        userItem.setQty(3); // currently 3
        userItem.setPriceAtAdd(new BigDecimal("10.00"));

        when(cartItemMapper.selectList(any())).thenReturn(List.of(userItem));

        ProductDTO p1 = createSampleProduct(101L, "Product A", 1000, 5); // Stock is 5
        when(productClient.getByIds(any())).thenReturn(Map.of(101L, p1));

        // Guest had 4 of Product 101. Total 3 + 4 = 7, capped to stock 5.
        MergeCartRequest req = new MergeCartRequest(List.of(
                new MergeCartItemDTO(101L, 4, 1000)
        ));

        CartVO result = cartService.mergeCart(200L, null, req);
        assertNotNull(result);
        assertEquals(5, userItem.getQty());
        verify(cartItemMapper).updateById(userItem);
    }
}
