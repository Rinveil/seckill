package com.seckill.order.mq;

import com.seckill.common.mq.OrderExpireMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.order.service.OrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 关单消费：关单用 DB 条件更新幂等；失败不重试，依赖扫表兜底。
 */
@Component
@RocketMQMessageListener(
        topic = OrderMqConstants.TOPIC_EXPIRE,
        consumerGroup = OrderMqConstants.CG_EXPIRE
)
public class OrderExpireListener implements RocketMQListener<OrderExpireMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireListener.class);

    private final OrderService orderService;

    public OrderExpireListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(OrderExpireMessage message) {
        log.info("recv order.expire orderNo={}", message == null ? null : message.orderNo());
        try {
            orderService.expireFromMessage(message);
        } catch (RuntimeException ex) {
            log.error("order.expire failed (scan job will cover). orderNo={}",
                    message == null ? null : message.orderNo(), ex);
        }
    }
}
