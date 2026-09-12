package com.seckill.order.mq;

import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreateListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateListener.class);

    private final OrderService orderService;

    public OrderCreateListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = OrderMqConstants.QUEUE_CREATE)
    public void onMessage(OrderCreateMessage message) {
        log.info("recv order.create token={} userId={} activityId={}",
                message == null ? null : message.orderToken(),
                message == null ? null : message.userId(),
                message == null ? null : message.activityId());
        orderService.createFromMessage(message);
    }
}
