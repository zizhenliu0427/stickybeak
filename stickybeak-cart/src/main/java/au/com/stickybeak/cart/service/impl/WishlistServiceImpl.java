package au.com.stickybeak.cart.service.impl;

import au.com.stickybeak.cart.client.ProductClient;
import au.com.stickybeak.cart.client.ProductDTO;
import au.com.stickybeak.cart.dto.AddCartItemRequest;
import au.com.stickybeak.cart.entity.Wishlist;
import au.com.stickybeak.cart.mapper.WishlistMapper;
import au.com.stickybeak.cart.service.CartService;
import au.com.stickybeak.cart.service.WishlistService;
import au.com.stickybeak.cart.vo.WishlistVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WishlistServiceImpl implements WishlistService {

    private final WishlistMapper wishlistMapper;
    private final ProductClient productClient;
    private final CartService cartService;

    public WishlistServiceImpl(WishlistMapper wishlistMapper,
                               ProductClient productClient,
                               CartService cartService) {
        this.wishlistMapper = wishlistMapper;
        this.productClient = productClient;
        this.cartService = cartService;
    }

    @Override
    public List<WishlistVO> listWishlist(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Wishlist> query = new LambdaQueryWrapper<>();
        query.eq(Wishlist::getUserId, userId).orderByDesc(Wishlist::getCreateTime);
        List<Wishlist> list = wishlistMapper.selectList(query);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> productIds = list.stream().map(Wishlist::getProductId).collect(Collectors.toSet());
        Map<Long, ProductDTO> productMap = productClient.getByIds(productIds);

        List<WishlistVO> result = new ArrayList<>();
        for (Wishlist w : list) {
            ProductDTO p = productMap.get(w.getProductId());
            if (p == null) {
                p = productClient.getById(w.getProductId());
            }

            WishlistVO vo = new WishlistVO();
            vo.setId(w.getId());
            vo.setProductId(w.getProductId());
            if (p != null) {
                vo.setName(p.getName());
                vo.setSlug(p.getSlug());
                vo.setImageUrl(p.getCoverImage());
                vo.setPriceCents(p.getPriceCents());
                vo.setStock(p.getStock());
            } else {
                vo.setName("Product #" + w.getProductId());
                vo.setSlug(String.valueOf(w.getProductId()));
                vo.setPriceCents(0);
                vo.setStock(0);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean toggleWishlist(Long userId, Long productId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User must be logged in to modify wishlist");
        }
        if (productId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "productId is required");
        }

        LambdaQueryWrapper<Wishlist> query = new LambdaQueryWrapper<>();
        query.eq(Wishlist::getUserId, userId).eq(Wishlist::getProductId, productId);
        Wishlist existing = wishlistMapper.selectOne(query);

        if (existing != null) {
            wishlistMapper.deleteById(existing.getId());
            return false; // Removed
        } else {
            ProductDTO product = productClient.getById(productId);
            if (product == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "Product not found");
            }
            Wishlist item = new Wishlist();
            item.setUserId(userId);
            item.setProductId(productId);
            wishlistMapper.insert(item);
            return true; // Added
        }
    }

    @Override
    @Transactional
    public void moveToCart(Long userId, String sessionId, Long productId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User must be logged in");
        }

        // 1. Add to cart
        cartService.addItem(userId, sessionId, new AddCartItemRequest(productId, 1));

        // 2. Remove from wishlist
        LambdaQueryWrapper<Wishlist> query = new LambdaQueryWrapper<>();
        query.eq(Wishlist::getUserId, userId).eq(Wishlist::getProductId, productId);
        wishlistMapper.delete(query);
    }
}
