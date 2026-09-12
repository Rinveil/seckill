package com.seckill.order.dto;

public record OrderView(String orderNo, long activityId, String status, int amountFen) {
}
