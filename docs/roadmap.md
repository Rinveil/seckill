# 后续实现顺序

1. Redis 预热库存 + Lua 原子预扣，替换 `SeckillService` 内存计数
2. 网关限流、登录校验、活动令牌
3. 秒杀成功后投递 MQ，订单服务异步落库
4. 对账：Redis 库存 vs MySQL 订单
