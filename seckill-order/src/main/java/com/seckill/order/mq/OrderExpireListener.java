package com.seckill.order.mq;

import com.seckill.common.mq.OrderExpireMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderExpireListener {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireListener.class);

    private final OrderService orderService;

    public OrderExpireListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = OrderMqConstants.QUEUE_EXPIRE)
    public void onMessage(OrderExpireMessage message) {
        log.info("recv order.expire orderNo={}", message == null ? null : message.orderNo());
        orderService.expireFromMessage(message);
    }
}
