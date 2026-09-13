package com.seckill.common.mq;

/** 活动到达 endAt 后自动关抢。 */
public record ActivityExpireMessage(long activityId) {
}
