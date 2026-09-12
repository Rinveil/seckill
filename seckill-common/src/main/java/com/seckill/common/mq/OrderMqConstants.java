package com.seckill.common.mq;

/** core 生产 / order 消费共用。 */
public final class OrderMqConstants {

    public static final String EXCHANGE = "seckill.order";
    public static final String ROUTING_KEY_CREATE = "order.create";
    public static final String QUEUE_CREATE = "seckill.order.create";

    private OrderMqConstants() {
    }
}
