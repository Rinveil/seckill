package com.seckill.common.mq;

/** 活动到期关抢。 */
public final class ActivityMqConstants {

    public static final String EXCHANGE = "seckill.activity";
    public static final String ROUTING_KEY_DELAY = "activity.delay";
    public static final String QUEUE_DELAY = "seckill.activity.delay";
    public static final String ROUTING_KEY_EXPIRE = "activity.expire";
    public static final String QUEUE_EXPIRE = "seckill.activity.expire";

    private ActivityMqConstants() {
    }
}
