package com.seckill.core.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.ResultCode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SeckillService {

    private final AtomicInteger stock = new AtomicInteger(100);
    private final Map<Long, Boolean> orderedUsers = new ConcurrentHashMap<>();

    public Map<String, Object> grab(long activityId, long userId) {
        if (orderedUsers.putIfAbsent(userId, true) != null) {
            throw new BusinessException(ResultCode.DUPLICATE);
        }
        int left = stock.decrementAndGet();
        if (left < 0) {
            stock.incrementAndGet();
            orderedUsers.remove(userId);
            throw new BusinessException(ResultCode.SOLD_OUT);
        }
        return Map.of(
                "activityId", activityId,
                "orderToken", UUID.randomUUID().toString(),
                "remainStock", left
        );
    }
}
