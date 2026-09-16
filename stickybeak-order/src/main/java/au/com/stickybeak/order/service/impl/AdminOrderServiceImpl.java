package au.com.stickybeak.order.service.impl;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.client.ProductClient;
import au.com.stickybeak.order.dto.AdminOrderPageQuery;
import au.com.stickybeak.order.dto.AdminOrderStatusUpdateRequest;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.entity.OrderItem;
import au.com.stickybeak.order.mapper.OrderItemMapper;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.service.AdminOrderService;
import au.com.stickybeak.order.statemachine.OrderStateMachine;
import au.com.stickybeak.order.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderServiceImpl.class);

    private static final String CACHE_SUMMARY = "order:analytics:summary";
    private static final String CACHE_TREND_PREFIX = "order:analytics:trend:";
    private static final long CACHE_TTL_SECONDS = 300; // 5 min

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStateMachine orderStateMachine;
    private final ProductClient productClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AdminOrderServiceImpl(OrderMapper orderMapper,
                                 OrderItemMapper orderItemMapper,
                                 OrderStateMachine orderStateMachine,
                                 ProductClient productClient,
                                 StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStateMachine = orderStateMachine;
        this.productClient = productClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalyticsSummaryVO getAnalyticsSummary() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(CACHE_SUMMARY);
            if (StringUtils.hasText(cached)) {
                return objectMapper.readValue(cached, AnalyticsSummaryVO.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read analytics summary from redis: {}", e.getMessage());
        }

        BigDecimal totalGmv = orderMapper.sumTotalGmv();
        BigDecimal todayGmv = orderMapper.sumTodayGmv();

        Long totalOrders = orderMapper.selectCount(new LambdaQueryWrapper<>());
        Long paidOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, "paid", "processing", "shipped", "completed"));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long todayOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, todayStart));

        Long pendingShipment = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, "paid", "processing"));
        Long pendingPayment = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "pending"));

        AnalyticsSummaryVO vo = new AnalyticsSummaryVO();
        vo.setTotalGmv(totalGmv != null ? totalGmv : BigDecimal.ZERO);
        vo.setTodayGmv(todayGmv != null ? todayGmv : BigDecimal.ZERO);
        vo.setTotalOrders(totalOrders != null ? totalOrders : 0L);
        vo.setPaidOrders(paidOrders != null ? paidOrders : 0L);
        vo.setTodayOrders(todayOrders != null ? todayOrders : 0L);
        vo.setPendingShipmentOrders(pendingShipment != null ? pendingShipment : 0L);
        vo.setPendingPaymentOrders(pendingPayment != null ? pendingPayment : 0L);

        try {
            stringRedisTemplate.opsForValue().set(
                    CACHE_SUMMARY,
                    objectMapper.writeValueAsString(vo),
                    CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Failed to cache analytics summary: {}", e.getMessage());
        }

        return vo;
    }

    @Override
    public List<SalesTrendItemVO> getSalesTrend(int days) {
        if (days <= 0 || days > 180) {
            days = 7;
        }
        String cacheKey = CACHE_TREND_PREFIX + days;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return objectMapper.readValue(cached, new TypeReference<List<SalesTrendItemVO>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to read trend from redis: {}", e.getMessage());
        }

        List<Map<String, Object>> rawTrend = orderMapper.selectDailySalesTrend(days);
        Map<String, SalesTrendItemVO> map = new HashMap<>();
        if (rawTrend != null) {
            for (Map<String, Object> row : rawTrend) {
                String date = String.valueOf(row.get("order_date"));
                int count = row.get("order_count") != null ? ((Number) row.get("order_count")).intValue() : 0;
                BigDecimal gmv = row.get("daily_gmv") != null ? new BigDecimal(String.valueOf(row.get("daily_gmv"))) : BigDecimal.ZERO;
                map.put(date, new SalesTrendItemVO(date, count, gmv));
            }
        }

        // 补齐连续日期序列
        List<SalesTrendItemVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = days - 1; i >= 0; i--) {
            String targetDate = today.minusDays(i).format(dtf);
            if (map.containsKey(targetDate)) {
                result.add(map.get(targetDate));
            } else {
                result.add(new SalesTrendItemVO(targetDate, 0, BigDecimal.ZERO));
            }
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(result),
                    CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Failed to cache trend: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public List<TopProductVO> getTopProducts(int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }
        List<Map<String, Object>> rows = orderItemMapper.selectTopSellingProducts(limit);
        List<TopProductVO> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                TopProductVO vo = new TopProductVO();
                if (r.get("product_id") != null) {
                    vo.setProductId(((Number) r.get("product_id")).longValue());
                }
                vo.setProductName(String.valueOf(r.get("product_name")));
                vo.setProductImage(r.get("product_image") != null ? String.valueOf(r.get("product_image")) : null);
                vo.setTotalQuantity(r.get("total_quantity") != null ? ((Number) r.get("total_quantity")).intValue() : 0);
                vo.setTotalRevenue(r.get("total_revenue") != null ? new BigDecimal(String.valueOf(r.get("total_revenue"))) : BigDecimal.ZERO);
                list.add(vo);
            }
        }
        return list;
    }

    @Override
    public Page<AdminOrderVO> pageOrders(AdminOrderPageQuery query) {
        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.like(Order::getOrderNo, query.getOrderNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Order::getStatus, query.getStatus().trim().toLowerCase());
        }
        if (query.getUserId() != null) {
            wrapper.eq(Order::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getStartDate())) {
            wrapper.ge(Order::getCreateTime, query.getStartDate().trim() + " 00:00:00");
        }
        if (StringUtils.hasText(query.getEndDate())) {
            wrapper.le(Order::getCreateTime, query.getEndDate().trim() + " 23:59:59");
        }

        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> resultPage = orderMapper.selectPage(page, wrapper);

        Page<AdminOrderVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        if (resultPage.getRecords().isEmpty()) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        List<Long> orderIds = resultPage.getRecords().stream().map(Order::getId).toList();
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(OrderItem::getOrderId, orderIds);
        List<OrderItem> allItems = orderItemMapper.selectList(itemWrapper);

        Map<Long, List<OrderItemVO>> itemsByOrderId = new HashMap<>();
        for (OrderItem item : allItems) {
            OrderItemVO ivo = new OrderItemVO();
            ivo.setId(item.getId());
            ivo.setProductId(item.getProductId());
            ivo.setProductName(item.getProductName());
            ivo.setProductImage(item.getProductImage());
            ivo.setPrice(item.getPrice());
            ivo.setQty(item.getQty());
            itemsByOrderId.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(ivo);
        }

        List<AdminOrderVO> voList = resultPage.getRecords().stream().map(o -> {
            AdminOrderVO vo = new AdminOrderVO();
            vo.setId(o.getId());
            vo.setOrderNo(o.getOrderNo());
            vo.setUserId(o.getUserId());
            vo.setAddressSnapshot(o.getAddressSnapshot());
            vo.setTotalAmount(o.getTotalAmount());
            vo.setPayAmount(o.getPayAmount());
            vo.setCurrency(o.getCurrency());
            vo.setExchangeRateAtPay(o.getExchangeRateAtPay());
            vo.setStatus(o.getStatus());
            vo.setPayTime(o.getPayTime());
            vo.setShipTime(o.getShipTime());
            vo.setCompleteTime(o.getCompleteTime());
            vo.setRemark(o.getRemark());
            vo.setCreateTime(o.getCreateTime());
            vo.setUpdateTime(o.getUpdateTime());
            vo.setItems(itemsByOrderId.getOrDefault(o.getId(), Collections.emptyList()));
            vo.setAllowedNextStatuses(orderStateMachine.getAllowedTransitions(o.getStatus()));
            return vo;
        }).collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(String orderNo, AdminOrderStatusUpdateRequest req, Long adminId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Order not found: " + orderNo);
        }

        OrderStatus targetStatus = OrderStatus.fromCode(req.getStatus());
        if (targetStatus == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid target status: " + req.getStatus());
        }

        String currentStatus = order.getStatus();

        // 如果管理员取消订单且该订单原状态是 paid 或 processing，需要通知商品微服务释放库存
        if (targetStatus == OrderStatus.CANCELLED) {
            if ("paid".equalsIgnoreCase(currentStatus) || "processing".equalsIgnoreCase(currentStatus) || "pending".equalsIgnoreCase(currentStatus)) {
                log.info("Admin cancelling order {}, releasing stock", orderNo);
                try {
                    productClient.rollbackStock(orderNo);
                } catch (Exception e) {
                    log.error("Failed to release stock when admin cancelled order {}: {}", orderNo, e.getMessage());
                }
            }
        }

        StringBuilder remarkBuilder = new StringBuilder();
        if (StringUtils.hasText(order.getRemark())) {
            remarkBuilder.append(order.getRemark().trim());
        }
        if (targetStatus == OrderStatus.SHIPPED && StringUtils.hasText(req.getTrackingNo())) {
            if (remarkBuilder.length() > 0) {
                remarkBuilder.append(" | ");
            }
            remarkBuilder.append("Tracking: ").append(req.getTrackingNo().trim());
        }
        if (StringUtils.hasText(req.getRemark())) {
            if (remarkBuilder.length() > 0) {
                remarkBuilder.append(" | ");
            }
            remarkBuilder.append(req.getRemark().trim());
        }
        if (remarkBuilder.length() > 0) {
            order.setRemark(remarkBuilder.toString());
        }

        orderStateMachine.transition(order, targetStatus, adminId, "admin");
        orderMapper.updateById(order);

        // 清理分析统计缓存
        evictAnalyticsCaches();
        log.info("Admin {} updated order {} status from {} to {}", adminId, orderNo, currentStatus, targetStatus.getCode());
    }

    private void evictAnalyticsCaches() {
        try {
            stringRedisTemplate.delete(CACHE_SUMMARY);
            Set<String> trendKeys = stringRedisTemplate.keys(CACHE_TREND_PREFIX + "*");
            if (trendKeys != null && !trendKeys.isEmpty()) {
                stringRedisTemplate.delete(trendKeys);
            }
        } catch (Exception e) {
            log.warn("Failed to evict analytics cache: {}", e.getMessage());
        }
    }
}
