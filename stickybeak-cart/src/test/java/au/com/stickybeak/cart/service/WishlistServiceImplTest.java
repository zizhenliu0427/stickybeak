package au.com.stickybeak.cart.service;

import au.com.stickybeak.cart.client.ProductClient;
import au.com.stickybeak.cart.client.ProductDTO;
import au.com.stickybeak.cart.dto.AddCartItemRequest;
import au.com.stickybeak.cart.entity.Wishlist;
import au.com.stickybeak.cart.mapper.WishlistMapper;
import au.com.stickybeak.cart.service.impl.WishlistServiceImpl;
import au.com.stickybeak.cart.vo.WishlistVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

    @Mock
    private WishlistMapper wishlistMapper;

    @Mock
    private ProductClient productClient;

    @Mock
    private CartService cartService;

    @InjectMocks
    private WishlistServiceImpl wishlistService;

    @Test
    void toggleWishlist_whenNotExists_addsToWishlist() {
        when(wishlistMapper.selectOne(any())).thenReturn(null);

        ProductDTO product = new ProductDTO();
        product.setId(101L);
        product.setName("Bus Sign");
        when(productClient.getById(101L)).thenReturn(product);

        boolean added = wishlistService.toggleWishlist(200L, 101L);
        assertTrue(added);
        verify(wishlistMapper).insert(any(Wishlist.class));
    }

    @Test
    void toggleWishlist_whenExists_removesFromWishlist() {
        Wishlist existing = new Wishlist();
        existing.setId(5L);
        existing.setUserId(200L);
        existing.setProductId(101L);
        when(wishlistMapper.selectOne(any())).thenReturn(existing);

        boolean added = wishlistService.toggleWishlist(200L, 101L);
        assertFalse(added);
        verify(wishlistMapper).deleteById(5L);
    }

    @Test
    void listWishlist_returnsEnrichedWishlist() {
        Wishlist w = new Wishlist();
        w.setId(1L);
        w.setUserId(200L);
        w.setProductId(101L);
        when(wishlistMapper.selectList(any())).thenReturn(List.of(w));

        ProductDTO p = new ProductDTO();
        p.setId(101L);
        p.setName("Bus Sign");
        p.setSlug("bus-sign");
        p.setPriceCents(1200);
        p.setStock(20);
        when(productClient.getByIds(any())).thenReturn(Map.of(101L, p));

        List<WishlistVO> list = wishlistService.listWishlist(200L);
        assertEquals(1, list.size());
        assertEquals("Bus Sign", list.get(0).getName());
        assertEquals(1200, list.get(0).getPriceCents());
    }

    @Test
    void moveToCart_addsToCartAndDeletesFromWishlist() {
        wishlistService.moveToCart(200L, null, 101L);

        verify(cartService).addItem(eq(200L), isNull(), any(AddCartItemRequest.class));
        verify(wishlistMapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
