package com.seckill.common.mq;

/** 订单支付超时关闭消息。 */
public record OrderExpireMessage(String orderNo) {
}
