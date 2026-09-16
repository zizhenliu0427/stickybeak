package au.com.stickybeak.product.mapper;

import au.com.stickybeak.product.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE t_product SET stock = stock - #{qty}, sales = sales + #{qty}, update_time = NOW() " +
            "WHERE id = #{productId} AND stock >= #{qty} AND is_deleted = 0")
    int deductStockOptimistic(@Param("productId") Long productId, @Param("qty") Integer qty);

    @Update("UPDATE t_product SET stock = stock + #{qty}, sales = GREATEST(0, sales - #{qty}), update_time = NOW() " +
            "WHERE id = #{productId} AND is_deleted = 0")
    int rollbackStockOptimistic(@Param("productId") Long productId, @Param("qty") Integer qty);
}
