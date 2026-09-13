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

    /** 消费失败人工死信（不改建队队列参数，避免与已有队列冲突）。 */
    public static final String ROUTING_KEY_CREATE_DLQ = "order.create.dlq";
    public static final String QUEUE_CREATE_DLQ = "seckill.order.create.dlq";
    public static final String ROUTING_KEY_EXPIRE_DLQ = "order.expire.dlq";
    public static final String QUEUE_EXPIRE_DLQ = "seckill.order.expire.dlq";

    private OrderMqConstants() {
    }
}
