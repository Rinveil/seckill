package com.seckill.order.mq;

import com.seckill.common.mq.OrderExpireMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.mq", name = "enabled", havingValue = "true")
public class OrderExpireListener {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireListener.class);

    private final OrderService orderService;
    private final RabbitTemplate rabbitTemplate;

    public OrderExpireListener(OrderService orderService, RabbitTemplate rabbitTemplate) {
        this.orderService = orderService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = OrderMqConstants.QUEUE_EXPIRE)
    public void onMessage(OrderExpireMessage message) {
        log.info("recv order.expire orderNo={}", message == null ? null : message.orderNo());
        try {
            orderService.expireFromMessage(message);
        } catch (RuntimeException ex) {
            log.error("order.expire failed, send DLQ. orderNo={}",
                    message == null ? null : message.orderNo(), ex);
            if (message != null) {
                rabbitTemplate.convertAndSend(
                        OrderMqConstants.EXCHANGE,
                        OrderMqConstants.ROUTING_KEY_EXPIRE_DLQ,
                        message
                );
            }
        }
    }

    @RabbitListener(queues = OrderMqConstants.QUEUE_EXPIRE_DLQ)
    public void onDlq(OrderExpireMessage message) {
        log.error("DLQ order.expire orderNo={}", message == null ? null : message.orderNo());
    }
}
