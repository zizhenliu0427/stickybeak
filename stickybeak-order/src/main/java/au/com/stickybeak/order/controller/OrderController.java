package au.com.stickybeak.order.controller;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.order.dto.CreateOrderRequest;
import au.com.stickybeak.order.service.OrderService;
import au.com.stickybeak.order.vo.CheckoutResponseVO;
import au.com.stickybeak.order.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    public static final String HEADER_USER_ID = "X-User-Id";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public Result<CheckoutResponseVO> checkout(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader,
            @Valid @RequestBody CreateOrderRequest req) {
        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        CheckoutResponseVO vo = orderService.checkout(userId, req);
        return Result.ok(vo);
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> getByOrderNo(
            @PathVariable String orderNo,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader) {
        Long userId = parseUserId(userIdHeader);
        OrderVO vo = orderService.getByOrderNo(orderNo, userId);
        return Result.ok(vo);
    }

    @GetMapping("/my")
    public Result<List<OrderVO>> listMyOrders(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userIdHeader) {
        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        List<OrderVO> list = orderService.listUserOrders(userId);
        return Result.ok(list);
    }

    private Long parseUserId(String userIdHeader) {
        if (StringUtils.hasText(userIdHeader)) {
            try {
                return Long.valueOf(userIdHeader.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
