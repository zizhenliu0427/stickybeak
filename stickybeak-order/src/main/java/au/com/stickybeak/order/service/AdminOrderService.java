package au.com.stickybeak.order.service;

import au.com.stickybeak.order.dto.AdminOrderPageQuery;
import au.com.stickybeak.order.dto.AdminOrderStatusUpdateRequest;
import au.com.stickybeak.order.vo.AdminOrderVO;
import au.com.stickybeak.order.vo.AnalyticsSummaryVO;
import au.com.stickybeak.order.vo.SalesTrendItemVO;
import au.com.stickybeak.order.vo.TopProductVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface AdminOrderService {
    AnalyticsSummaryVO getAnalyticsSummary();
    List<SalesTrendItemVO> getSalesTrend(int days);
    List<TopProductVO> getTopProducts(int limit);
    Page<AdminOrderVO> pageOrders(AdminOrderPageQuery query);
    void updateOrderStatus(String orderNo, AdminOrderStatusUpdateRequest req, Long adminId);

    /** 对已支付 / 备货中的订单全额退款；对退款中的订单再次调用即重试 */
    void refundOrder(String orderNo, String reason, Long adminId);
}
