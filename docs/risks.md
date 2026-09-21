# 并发与鲁棒性（对照当前代码）

> 对照 `main` 实现。压测数据见 [test-report.md](./test-report.md)（已归档）。  
> 热路径：`POST /api/seckill/{id}` → 布隆 → Lua 预扣 → MQ/HTTP 建单 → 支付/取消/过期。

## 1. 已经做对的

| 点 | 实现 |
|---|---|
| 库存原子 | Lua：`open` / `bought>=limit` / `DECR stock + INCR bought`；失败码 `-3/-1/-2` |
| 回滚原子 | `StockRollbackHelper`：`bought>0` 才 `DECR bought + INCR stock` |
| 建单幂等 | `orderNo` 先查再插，吃 `DuplicateKey` |
| 支付 / 取消 / 过期 | 均 `WHERE status=CREATED` 条件更新；支付成功不再回滚库存 |
| 内部建单 | 网关拦截 `/api/order/internal/**`；order 若见到 `X-User-Id` 也拒绝。core 走 Service DNS |
| 伪造登录头 | 网关先剥 `X-User-*` 再注入 JWT claims |
| 关抢竞态 | `expireIfOpen` 仅当仍 OPEN；MQ 延迟 + 扫表；MQ 关时本机 `TaskScheduler` |
| 布隆误杀 | `ready` 缺失 fail-open；重建 tmp+RENAME |
| 禁用账号 | Redis `seckill:user:disabled:{id}`，网关验签后拒绝；Redis 异常 fail-open |
| 商城不漏草稿 | `listForMall` / `detailForMall` 排除 DRAFT |
| 限流 IP | nginx 写 `X-Real-IP=$remote_addr`，不信任客户端 XFF 首段；body `code=1002` |

## 2. 热路径竞态（现状）

| 级别 | 场景 | 现状 |
|---|---|---|
| — | 经网关打内部建单 | **已修**：403 |
| — | 支付 vs 过期/取消 | **已修**：`pay` 同样 CAS |
| 中 | 预扣成功、MQ `syncSend` 失败 | 回滚库存，用户重试。符合预期 |
| 中 | 投递成功、消费者失败后再投 | 幂等不二次扣库存，可能少卖。对账能看出 |
| — | PREHEATED 改限购 | **已修**：同步写 `seckill:limit:{id}` |
| — | MQ 关且扫表关 | **已修**：建单/开抢时本机延迟关单/关抢 |
| 低 | 布隆假阳性 | 仍进 Lua，可接受 |
| 低 | 限流单机内存 | 单副本演示可接受；多副本不共享桶 |
| 低 | Redis 禁用名单异常 | 网关 fail-open，短暂仍可能放行 |

## 3. 未做（有意后置或演示范围）

| 点 | 说明 |
|---|---|
| 真实支付 | Mock 固定成功，无资金流 |
| 克隆 API | 前端表单拷贝即可 |
| Java 单测套件 | 仍以本机 UI / `e2e-smoke.py` 为主 |
| 分布式限流 | 单副本内存令牌桶 |

## 4. 配置事实

| 项 | 事实 |
|---|---|
| `application.yml` 默认 | `seckill.mq/schedule.enabled=false`（裸起 Java） |
| **当前 K8s** | 两开关均为 **true** |
| 关单 | RocketMQ 延迟（3 分钟 delayLevel=7）+ 扫表；MQ 关则本机定时 |
| JWT | Bearer + localStorage；禁用账号另写 Redis 标记 |
| 限流 | order `-110`，早于 JWT；登录/注册另桶 |
| 布隆 | Redis bitmap，无 RedisBloom 模块 |
| Broker limit | `2Gi` |
