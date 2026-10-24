package au.com.stickybeak.order.mapper;

import au.com.stickybeak.order.entity.Order;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 条件更新：仅当数据库中的状态仍为 expectedStatus 时才写入，返回影响行数。
     * 并发流转（支付 / 超时 / 取消同时到达）时只有一个能成功，其余返回 0。
     */
    default int updateIfStatus(Order order, String expectedStatus) {
        return update(order, new UpdateWrapper<Order>()
                .eq("order_no", order.getOrderNo())
                .eq("status", expectedStatus));
    }

    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM t_order " +
            "WHERE status IN ('paid', 'processing', 'shipped', 'completed') AND is_deleted = 0")
    BigDecimal sumTotalGmv();

    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM t_order " +
            "WHERE status IN ('paid', 'processing', 'shipped', 'completed') " +
            "AND create_time >= CURDATE() AND is_deleted = 0")
    BigDecimal sumTodayGmv();

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS order_date, " +
            "COUNT(*) AS order_count, " +
            "COALESCE(SUM(CASE WHEN status IN ('paid', 'processing', 'shipped', 'completed') THEN pay_amount ELSE 0 END), 0) AS daily_gmv " +
            "FROM t_order " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) AND is_deleted = 0 " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY order_date ASC")
    List<Map<String, Object>> selectDailySalesTrend(@Param("days") int days);
}
