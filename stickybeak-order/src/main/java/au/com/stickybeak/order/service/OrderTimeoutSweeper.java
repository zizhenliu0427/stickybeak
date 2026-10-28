package au.com.stickybeak.order.service;

import au.com.stickybeak.common.enums.OrderStatus;
import au.com.stickybeak.order.entity.Order;
import au.com.stickybeak.order.mapper.OrderMapper;
import au.com.stickybeak.order.service.OrderService.TimeoutOutcome;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时关单的兜底扫描：延迟消息发送失败（结账时只记日志）或丢失时，订单会一直停在 pending
 * 并占着库存。定期找出超过 TTL + 宽限期仍未支付的订单，按超时流程关闭并释放资源。
 * 多实例同时扫描是安全的：关单走条件更新，只有一个实例能成功。
 */
@Component
@ConditionalOnProperty(prefix = "stickybeak.order.sweep", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutSweeper.class);

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final long orderTtlMillis;
    private final long graceMillis;
    private final int batchSize;

    public OrderTimeoutSweeper(OrderMapper orderMapper,
                               OrderService orderService,
                               @Value("${stickybeak.order.ttl-millis:1800000}") long orderTtlMillis,
                               @Value("${stickybeak.order.sweep.grace-millis:300000}") long graceMillis,
                               @Value("${stickybeak.order.sweep.batch-size:100}") int batchSize) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.orderTtlMillis = orderTtlMillis;
        this.graceMillis = graceMillis;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${stickybeak.order.sweep.initial-delay-millis:60000}",
            fixedDelayString = "${stickybeak.order.sweep.interval-millis:300000}")
    public int sweep() {
        // create_time 由本服务按 JVM 时钟填充，这里用同一时钟计算截止时间
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(orderTtlMillis + graceMillis));
        List<Order> stale = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING.getCode())
                .lt(Order::getCreateTime, cutoff)
                .orderByAsc(Order::getCreateTime)
                .last("LIMIT " + batchSize));

        int closed = 0;
        for (Order order : stale) {
            String orderNo = order.getOrderNo();
            try {
                if (orderService.closeIfUnpaid(orderNo) == TimeoutOutcome.CLOSED_NOW) {
                    orderService.releaseOrderResources(orderNo);
                    closed++;
                    log.warn("Sweeper closed order {} whose timeout message never arrived", orderNo);
                }
            } catch (Exception e) {
                log.error("Sweeper failed to close order {}: {}", orderNo, e.getMessage());
            }
        }
        return closed;
    }
}
