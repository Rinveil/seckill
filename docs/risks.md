# 并发与鲁棒性（对照当前代码）

> 对照 `main` 实现，不是目标态愿望清单。压测数据见 [test-report.md](./test-report.md)（已归档）。  
> 热路径：`POST /api/seckill/{id}` → 布隆 → Lua 预扣 → MQ/HTTP 建单 → 支付/取消/过期。

## 1. 已经做对的（不要再改语义）

| 点 | 实现 |
|---|---|
| 库存原子 | Lua：`open` / `bought>=limit` / `DECR stock + INCR bought`；失败码 `-3/-1/-2` |
| 回滚原子 | `StockRollbackHelper` 同一套 Lua：`bought>0` 才 `DECR bought + INCR stock` |
| 建单幂等 | `orderNo` 先查再插，吃 `DuplicateKey`；重复消费不二次扣库存 |
| 取消 vs 过期 | `closeCreatedOrder` 条件更新 `status=CREATED`，只回滚一次 |
| 伪造登录头 | 网关先剥 `X-User-*` 再注入 JWT claims |
| 关抢竞态 | `expireIfOpen` 仅当仍 OPEN；扫表 + 延迟消息双保险 |
| 布隆误杀 | `ready` 缺失或检查异常时 fail-open 到 Lua；重建先写 tmp 再 `RENAME` |
| 商城不漏草稿 | `listForMall` / `detailForMall` 排除 DRAFT |

## 2. 并发风险（热路径）

级别：**高** = 可能超卖、错付或绕过预扣；**中** = 库存漂/脏数据/体验差；**低** = 演示可接受。

| 级别 | 场景 | 代码 | 实际后果 | 现状 |
|---|---|---|---|---|
| **高** | 任意登录用户调内部建单 | `POST /api/order/internal/create` 经网关 `/api/order/**` 转发；`OrderInternalController` 无来源校验，body 自带 `userId/activityId` | 绕过布隆与 Lua，直接落 `CREATED`，不扣 Redis | **未修**。core 调 order 本走集群 DNS，不必经过网关 |
| **高** | 支付 vs 过期/取消 | `pay()`：读到 CREATED 后 `updateById(PAID)`，**不带状态条件**；`cancel`/`expire` 用 CAS | 时序：pay 读 CREATED → expire CAS 成功并回滚库存 → pay 仍写成 PAID。已付订单对应已释放库存 | **未修**。文档若写「支付也 CAS」与代码不符 |
| 中 | 预扣成功、MQ `syncSend` 失败 | `SeckillService` 回滚后对用户返回 400 | 不超卖；用户需重试。Broker busy 时常见（见压测） | 符合预期 |
| 中 | 投递成功、消费者失败后再投 | 消费侧失败会回滚 Redis；重试靠 `orderNo` 幂等，**不再扣库存** | 可能库存多 1（少卖），不超卖 | 可接受；对账能看出 |
| 中 | 开抢中改 Redis 库存 | 运营接口已禁止 OPEN 改库存 | 若绕过前端打 API，仍可能与 Lua 竞态 | API 已拦 OPEN |
| 中 | PREHEATED 改 `limitPerUser` 未再预热 | `update()` 只改 DB；Lua 读 `seckill:limit:{id}` | 开抢后限购仍是旧值 | 须再点预热才写入 Redis |
| 中 | MQ 关且扫表关 | 不投延迟关单、不跑过期 Job | 待支付订单**不会** 3 分钟自动过期 | 当前 K8s **两开关都是 true**；切 false 时要知道这条 |
| 低 | 布隆假阳性 | 标准 bitmap 布隆 | 无效 ID 仍进 Lua，多 4 个 GET | 可接受 |
| 低 | 布隆不能单点删除 | 关抢/启动按 OPEN 集合重建 | 关闭后的 ID 不再放行（重建后） | 已按集合重建 |
| 低 | 限流单机 + 可伪 IP | 网关内存令牌桶；`X-Forwarded-For` 首段 | 多副本不共享；客户端可换桶 | 单副本演示可接受 |
| 低 | 禁用账号 Token 未过期 | 网关只验签；`/me` 与登录才看 `status` | 禁用后 2 小时内仍可抢购/支付 | JWT `expire-hours` 默认 2 |

### 2.1 支付竞态（应修）

```text
pay:     SELECT → status==CREATED → updateById(PAID)      // 无 WHERE status=CREATED
expire:  UPDATE ... SET EXPIRED WHERE status=CREATED      // CAS，成功则回滚 Redis
```

正确做法：`pay` 同样 `CREATED → PAID` 条件更新，`rows!=1` 则按当前状态返回（已过期/已取消不可付）。

### 2.2 内部建单（应修）

`OrderCreateClient` 直连 `http://seckill-order:8084`，浏览器不应打到该接口。

可选手段（演示级即可）：网关去掉 `/api/order/internal/**`；或 order 校验共享密钥 / 只绑 ClusterIP 且拒绝外网头。

## 3. 其它实现缺口（非并发，但对照产品）

| 级别 | 点 | 说明 |
|---|---|---|
| 中 | 删活动 Redis 残留 | `delete` 清 `stock/open/init`，不清 `limit`、`bought:{user}`；AUTO_INCREMENT 一般不复用 ID，多为孤儿 key |
| 中 | 订单列表 N+1 | `toView` 每条 `SELECT title FROM t_activity`；运营订单多时放大 |
| 低 | 限流业务码 | HTTP 429，body `code=429`；`ResultCode.RATE_LIMITED=1002` 未使用 |
| 低 | 登录无限流 | 仅 `/api/seckill/**` 有桶；爆破密码演示可接受 |
| 低 | 无单测 | 功能靠本机 UI / `e2e-smoke.py`；无 Java 单测 |
| — | 真实支付 | roadmap 后置，Mock 固定成功。无资金流、无回调验签 |
| — | 克隆 | 前端表单拷贝，无后端 `clone` API，新活动新 ID |

## 4. 配置事实（避免文档写反）

| 项 | 事实 |
|---|---|
| `application.yml` 默认 | `seckill.mq/schedule.enabled=false`（本地裸起 Java） |
| **当前 K8s** | `SECKILL_MQ_ENABLED=true`、`SECKILL_SCHEDULE_ENABLED=true`（`infra/k8s/21-23-*.yaml`） |
| 关单 | RocketMQ 延迟（3 分钟用 delayLevel=7），**不是** Rabbit TTL+DLX |
| JWT | `Authorization: Bearer` + localStorage，**不用** Cookie Session |
| 限流 | 网关 order `-110`，早于 JWT `-100` |
| 布隆 | Redis bitmap `seckill:bloom:activity` + `seckill:bloom:ready`；无 RedisBloom 模块 |
| Broker limit | `2Gi`（曾 OOM 从 1Gi 调高） |

两开关都关时：抢购 HTTP 同步建单；**没有**延迟关单也**没有**扫表关单/关抢。只适合短时减压，不适合当正式演示配置。
