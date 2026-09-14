package com.seckill.common.mq;

/** core 生产 / order 消费；RabbitMQ Exchange / Queue / RoutingKey。 */
public final class OrderMqConstants {

    public static final String EXCHANGE = "seckill.order.exchange";

    public static final String QUEUE_CREATE = "seckill.order.create";
    public static final String QUEUE_CREATE_DLQ = "seckill.order.create.dlq";
    /** TTL 延迟队列，到期经 DLX 进入 QUEUE_EXPIRE。 */
    public static final String QUEUE_DELAY = "seckill.order.delay";
    public static final String QUEUE_EXPIRE = "seckill.order.expire";
    public static final String QUEUE_EXPIRE_DLQ = "seckill.order.expire.dlq";

    public static final String ROUTING_KEY_CREATE = "order.create";
    public static final String ROUTING_KEY_CREATE_DLQ = "order.create.dlq";
    public static final String ROUTING_KEY_DELAY = "order.delay";
    public static final String ROUTING_KEY_EXPIRE = "order.expire";
    public static final String ROUTING_KEY_EXPIRE_DLQ = "order.expire.dlq";

    private OrderMqConstants() {
    }
}
