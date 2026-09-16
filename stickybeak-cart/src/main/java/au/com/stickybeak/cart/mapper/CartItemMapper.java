package au.com.stickybeak.cart.mapper;

import au.com.stickybeak.cart.entity.CartItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    @Delete("DELETE FROM t_cart_item WHERE cart_id = #{cartId}")
    int deleteByCartIdPhysical(@Param("cartId") Long cartId);

    @Delete("DELETE FROM t_cart_item WHERE id = #{id}")
    int deleteByIdPhysical(@Param("id") Long id);

    @Select("SELECT * FROM t_cart_item WHERE cart_id = #{cartId} AND product_id = #{productId} LIMIT 1")
    CartItem selectByCartAndProductRaw(@Param("cartId") Long cartId, @Param("productId") Long productId);
}
