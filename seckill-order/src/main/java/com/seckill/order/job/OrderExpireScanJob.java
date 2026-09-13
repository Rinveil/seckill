package com.seckill.order.job;

import com.seckill.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 延迟队列兜底：扫过期未支付订单。 */
@Component
public class OrderExpireScanJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireScanJob.class);

    private final OrderService orderService;

    public OrderExpireScanJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${seckill.order.expire-scan-ms:30000}")
    public void scan() {
        int n = orderService.expireOverdueBatch(100);
        if (n > 0) {
            log.info("order expire scan closed {} orders", n);
        }
    }
}
