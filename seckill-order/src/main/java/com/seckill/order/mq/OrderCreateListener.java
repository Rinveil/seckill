package com.seckill.order.mq;

import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 建单消费：失败已在业务侧回滚库存，吞异常不重试，避免重复回滚；失败消息进 DLQ。
 */
@Component
@ConditionalOnProperty(prefix = "seckill.mq", name = "enabled", havingValue = "true")
public class OrderCreateListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateListener.class);

    private final OrderService orderService;
    private final RabbitTemplate rabbitTemplate;

    public OrderCreateListener(OrderService orderService, RabbitTemplate rabbitTemplate) {
        this.orderService = orderService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = OrderMqConstants.QUEUE_CREATE)
    public void onMessage(OrderCreateMessage message) {
        log.info("recv order.create token={} userId={} activityId={}",
                message == null ? null : message.orderToken(),
                message == null ? null : message.userId(),
                message == null ? null : message.activityId());
        try {
            orderService.createFromMessage(message);
        } catch (RuntimeException ex) {
            log.error("order.create failed, send DLQ. token={}",
                    message == null ? null : message.orderToken(), ex);
            if (message != null) {
                rabbitTemplate.convertAndSend(
                        OrderMqConstants.EXCHANGE,
                        OrderMqConstants.ROUTING_KEY_CREATE_DLQ,
                        message
                );
            }
        }
    }

    @RabbitListener(queues = OrderMqConstants.QUEUE_CREATE_DLQ)
    public void onDlq(OrderCreateMessage message) {
        log.error("DLQ order.create token={} userId={} activityId={}",
                message == null ? null : message.orderToken(),
                message == null ? null : message.userId(),
                message == null ? null : message.activityId());
    }
}
