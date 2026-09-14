package com.seckill.order.mq;

import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.order.service.OrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 建单消费：失败已在业务侧回滚库存，吞掉异常避免 RocketMQ 重试导致重复回滚。
 * 异常仅打日志（等价于原 DLQ 人工观察）；扫表/对账兜底。
 */
@Component
@ConditionalOnProperty(prefix = "seckill.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = OrderMqConstants.TOPIC_CREATE,
        consumerGroup = OrderMqConstants.CG_CREATE
)
public class OrderCreateListener implements RocketMQListener<OrderCreateMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateListener.class);

    private final OrderService orderService;

    public OrderCreateListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(OrderCreateMessage message) {
        log.info("recv order.create token={} userId={} activityId={}",
                message == null ? null : message.orderToken(),
                message == null ? null : message.userId(),
                message == null ? null : message.activityId());
        try {
            orderService.createFromMessage(message);
        } catch (RuntimeException ex) {
            log.error("order.create failed (no retry, stock may already rolled back). token={}",
                    message == null ? null : message.orderToken(), ex);
        }
    }
}
