package com.seckill.order.controller;

import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.result.Result;
import com.seckill.order.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 集群内同步建单（MQ 关闭时由 core 调用）。 */
@RestController
@RequestMapping("/api/order/internal")
public class OrderInternalController {

    private final OrderService orderService;

    public OrderInternalController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create")
    public Result<Void> create(@RequestBody OrderCreateMessage message) {
        orderService.createFromMessage(message);
        return Result.ok(null);
    }
}
