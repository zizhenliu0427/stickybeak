package au.com.stickybeak.order.mapper;

import au.com.stickybeak.order.entity.OrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT oi.product_id AS product_id, " +
            "oi.product_name AS product_name, " +
            "oi.product_image AS product_image, " +
            "SUM(oi.qty) AS total_quantity, " +
            "COALESCE(SUM(oi.price * oi.qty), 0) AS total_revenue " +
            "FROM t_order_item oi " +
            "JOIN t_order o ON oi.order_id = o.id " +
            "WHERE o.status IN ('paid', 'processing', 'shipped', 'completed') " +
            "AND o.is_deleted = 0 AND oi.is_deleted = 0 " +
            "GROUP BY oi.product_id, oi.product_name, oi.product_image " +
            "ORDER BY total_quantity DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectTopSellingProducts(@Param("limit") int limit);
}
