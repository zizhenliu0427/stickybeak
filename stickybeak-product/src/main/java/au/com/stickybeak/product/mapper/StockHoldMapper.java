package au.com.stickybeak.product.mapper;

import au.com.stickybeak.product.entity.StockHold;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockHoldMapper extends BaseMapper<StockHold> {

    /**
     * 条件流转预占状态，返回影响行数。并发回滚 / 确认时只有一个能把 HELD 改走，
     * 避免同一笔预占被重复归还库存。
     */
    @Update("UPDATE t_stock_hold SET status = #{toStatus} " +
            "WHERE id = #{id} AND status = #{fromStatus} AND is_deleted = 0")
    int transitStatus(@Param("id") Long id, @Param("fromStatus") Integer fromStatus, @Param("toStatus") Integer toStatus);
}
