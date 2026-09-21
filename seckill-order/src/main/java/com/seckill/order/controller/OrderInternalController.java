package com.seckill.order.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.result.Result;
import com.seckill.common.result.ResultCode;
import com.seckill.order.service.OrderService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 集群内同步建单（MQ 关闭时由 core 直连调用，禁止经网关）。 */
@RestController
@RequestMapping("/api/order/internal")
public class OrderInternalController {

    private final OrderService orderService;

    public OrderInternalController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create")
    public Result<Void> create(
            @RequestHeader(value = "X-User-Id", required = false) String gatewayUserId,
            @RequestBody OrderCreateMessage message
    ) {
        if (StringUtils.hasText(gatewayUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "内部接口不可经网关调用");
        }
        orderService.createFromMessage(message);
        return Result.ok(null);
    }
}
