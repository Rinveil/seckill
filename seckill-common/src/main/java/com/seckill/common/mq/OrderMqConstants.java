package com.seckill.common.mq;

/** core 生产 / order 消费；RocketMQ Topic。 */
public final class OrderMqConstants {

    public static final String TOPIC_CREATE = "seckill-order-create";
    public static final String TOPIC_EXPIRE = "seckill-order-expire";

    public static final String CG_CREATE = "seckill-cg-order-create";
    public static final String CG_EXPIRE = "seckill-cg-order-expire";

    /** RocketMQ 延迟等级：7 = 3 分钟（与默认支付时限对齐）。 */
    public static final int DELAY_LEVEL_3_MIN = 7;

    private OrderMqConstants() {
    }
}
