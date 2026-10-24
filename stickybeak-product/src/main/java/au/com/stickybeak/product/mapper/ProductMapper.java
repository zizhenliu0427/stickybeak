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

    /** 管理员按增量调整库存：数据库内原子计算，不会覆盖并发扣减 */
    @Update("UPDATE t_product SET stock = GREATEST(0, stock + #{delta}), update_time = NOW() " +
            "WHERE id = #{productId} AND is_deleted = 0")
    int adjustStock(@Param("productId") Long productId, @Param("delta") Integer delta);

    /** 管理员直接设置库存（盘点场景），只改 stock 列 */
    @Update("UPDATE t_product SET stock = #{stock}, update_time = NOW() " +
            "WHERE id = #{productId} AND is_deleted = 0")
    int setStock(@Param("productId") Long productId, @Param("stock") Integer stock);
}
