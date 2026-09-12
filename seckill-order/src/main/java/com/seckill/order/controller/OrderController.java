package com.seckill.order.controller;

import com.seckill.common.result.Result;
import com.seckill.order.dto.OrderView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @GetMapping("/list")
    public Result<List<OrderView>> list() {
        return Result.ok(List.of(new OrderView("SK202609110001", 1L, "CREATED", 9900)));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderView> detail(@PathVariable String orderNo) {
        return Result.ok(new OrderView(orderNo, 1L, "CREATED", 9900));
    }
}
