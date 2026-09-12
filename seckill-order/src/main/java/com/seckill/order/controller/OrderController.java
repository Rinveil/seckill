package com.seckill.order.controller;

import com.seckill.common.result.Result;
import com.seckill.order.dto.OrderView;
import com.seckill.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/list")
    public Result<List<OrderView>> list(
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return Result.ok(orderService.list(userId, role));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderView> detail(
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String orderNo
    ) {
        return Result.ok(orderService.detail(userId, role, orderNo));
    }

    @PostMapping("/{orderNo}/pay")
    public Result<OrderView> pay(
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String orderNo
    ) {
        return Result.ok(orderService.pay(userId, role, orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<OrderView> cancel(
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String orderNo
    ) {
        return Result.ok(orderService.cancel(userId, role, orderNo));
    }
}
