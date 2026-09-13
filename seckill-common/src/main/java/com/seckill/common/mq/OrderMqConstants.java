package com.seckill.common.mq;

/** core 生产 / order 消费共用；含订单延迟过期。 */
public final class OrderMqConstants {

    public static final String EXCHANGE = "seckill.order";
    public static final String ROUTING_KEY_CREATE = "order.create";
    public static final String QUEUE_CREATE = "seckill.order.create";

    public static final String ROUTING_KEY_DELAY = "order.delay";
    public static final String QUEUE_DELAY = "seckill.order.delay";
    public static final String ROUTING_KEY_EXPIRE = "order.expire";
    public static final String QUEUE_EXPIRE = "seckill.order.expire";

    private OrderMqConstants() {
    }
}
