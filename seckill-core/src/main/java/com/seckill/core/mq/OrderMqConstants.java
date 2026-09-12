package com.seckill.core.mq;

/** 与 order 消费端约定：direct 交换机 + 建单队列。 */
public final class OrderMqConstants {

    public static final String EXCHANGE = "seckill.order";
    public static final String ROUTING_KEY_CREATE = "order.create";
    public static final String QUEUE_CREATE = "seckill.order.create";

    private OrderMqConstants() {
    }
}
