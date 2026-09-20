# 代码导读：从前端到完整订单链路

> 阅读顺序：先看总览与时序，再按「文件入口」跳进源码。
> 当前默认：`seckill.mq.enabled=true`、`seckill.schedule.enabled=true`（RocketMQ 异步建单 + 延迟关单/关抢 + 扫表兜底）。
> 架构总览见 [architecture.md](./architecture.md)。

---

## 1. 模块与入口对照

| 层 | 路径 | 端口 | 职责 |
|---|---|---|---|
| B 端 | `apps/web` | 80→NodePort 30080 | 登录、商城浏览、活动运营、抢购、订单支付/取消 |
| 网关 | `seckill-gateway` | 8080 | JWT 校验，注入 `X-User-*`，按路径转发 |
| 用户 | `seckill-user` | 8081 | 注册/登录/JWT、用户管理 |
| 活动 | `seckill-activity` | 8082 | 活动状态机、预热、开/关抢、Redis 库存、商城公开接口 |
| 秒杀 | `seckill-core` | 8083 | Redis Lua 预扣 → 建单投递 |
| 订单 | `seckill-order` | 8084 | 幂等落库、支付、取消、过期 |
| 公共 | `seckill-common` | — | `Result`、MQ 消息体、Redis Key、功能开关 |

HTTP 统一经前端同源 `/api/**`（nginx 反代到 gateway）。

---

## 2. 完整链路鸟瞰

```text
[浏览器 Vue] Login → JWT 存 localStorage
运营：建活动 → 预热 → 开抢
商城/会场：立即抢购
      |
      v
POST /api/seckill/{activityId} (+ Authorization: Bearer)
      |
      v
[gateway JwtAuthGlobalFilter] 验 JWT → 写 X-User-Id / X-User-Role
      |
      v
[SeckillController.grab] → SeckillService.grab(activityId, userId)
      |
      +- ActivityBloomFilter  # 无效 ID 直接 1003，不打 Lua
      |
      +- StockLuaExecutor.deduct  # Redis 原子：开抢标记/限购/DECR
      |    失败 → BusinessException（未开抢/重复/售罄）
      |
      +- 生成 orderToken + OrderCreateMessage
      |
      +- mq=true  → RocketMQTemplate.syncSend(TOPIC_CREATE)
      +- mq=false → OrderCreateClient.createSync (HTTP)
      |
      v
[order] OrderCreateListener (MQ) 或 OrderInternalController (同步)
  → OrderService.createFromMessage
     查价 → insert t_order(CREATED) → scheduleExpire (MQ 延迟)
      |
      v
返回 { activityId, orderToken, remainStock }
      |
      v
[订单页] pay → PAID | cancel → CANCELLED+回滚 | 3 分钟未付 → EXPIRED+回滚
```

---

## 3. 前置：活动为何能抢（运营侧）

| 步骤 | API | 核心方法 | 说明 |
|---|---|---|---|
| 创建 | `POST /api/activity` | `ActivityService.create` | DB `DRAFT` |
| 预热 | `POST /api/activity/{id}/preheat` | `preheat` | 写 `seckill:stock:{id}` + `stock:init`，`PREHEATED` |
| 开抢 | `POST /api/activity/{id}/open` | `open` | 写 `seckill:open:{id}=1`，重建布隆，`OPEN`，投到期延迟消息 |
| 关抢 | `POST /api/activity/{id}/close` | `doClose` | `CLOSED` 终态，删 open 标记并重建布隆 |

状态机：`DRAFT → PREHEATED → OPEN → CLOSED`（终态不复用）。

Redis Key（`SeckillRedisKeys.java`）：`stock` / `open` / `bought:{user}` / `limit` / `stock:init` / `bloom:activity` + `bloom:ready`。

---

## 4. 前端

### 4.1 路由（`apps/web/src/main.js`）

| 路径 | 页面 | 鉴权 |
|---|---|---|
| `/mall` | `Mall.vue` 商城卡片 | 公开 |
| `/mall/:id` | `MallDetail.vue` 商品详情 | 公开 |
| `/login` `/register` | 登录/注册 | 公开 |
| `/seckill` | `SeckillHome.vue` 会场表 | 登录 |
| `/seckill/activity/:id` | `Activity.vue` 抢购 | 登录 |
| `/seckill/result` | `Result.vue` 抢购结果 + 去支付 | 登录 |
| `/my/orders` | `MyOrders.vue` 我的订单 | 登录 |
| `/ops/dashboard` | `Dashboard.vue` 数据看板 | ADMIN |
| `/ops/*` | 运营管理 | ADMIN |

### 4.2 商城（`Mall.vue`）

卡片网格，共用静态 `/product.svg`；秒杀价 + 划线原价 + 折扣 + 倒计时 + 已抢进度；支持搜索/状态筛选/分页。未登录可逛；开抢中点「立即抢购」需登录后进会场，其它状态进公开详情 `/mall/:id`。数据来自公开 `GET /api/mall/list`（含 `soldCount`、`limitPerUser`）。

### 4.3 HTTP（`api.js`）

`request()` 自动带 `Authorization: Bearer`；401 清 token + toast。

---

## 5. 网关

路由（`application.yml`）：`/api/user/**`→user, `/api/activity/**`+`/api/mall/**`→activity, `/api/seckill/**`→core, `/api/order/**`→order。

白名单：`/api/user/login`、`/api/user/register`、`/api/mall/**`、`/actuator`。

`JwtAuthGlobalFilter`：去伪造头 → 验 JWT → 注入 `X-User-Id/Role/Username` → 失败返 401。

`RateLimitFilter`（order -110，早于 JWT）：仅 `/api/seckill/**`，按客户端 IP 令牌桶（默认 50 QPS、桶容量 10），超限 429。

---

## 6. Core：预扣 + 建单

`SeckillService.grab`：
1. `StockLuaExecutor.deduct` → `-3`未开抢/`-1`重复/`-2`售罄
2. 生成 `orderToken` + `OrderCreateMessage`
3. `dispatchCreate`：mq=true→`RocketMQTemplate.syncSend`；mq=false→`OrderCreateClient.createSync`
4. 失败→`rollback` + 抛「系统繁忙，库存已回滚」

Lua（`StockLuaExecutor.java`）：`open!=1`→-3；`bought >= limit`→-1；`stock<1`→-2；否则 DECR 库存 + INCR 已购计数。`limit` 来自 Redis `seckill:limit:{id}`（活动 `limitPerUser`，默认 1）。

---

## 7. Order：落库与支付/取消

建单入口：MQ 开→`OrderCreateListener`；MQ 关→`OrderInternalController`。两者调 `createFromMessage`。

`createFromMessage`：查重→查价→`insert t_order(CREATED)`→`scheduleExpire`（MQ 延迟）→失败回滚 Redis。

用户 API（`OrderController`，经网关）：
- `GET /api/order/list` `GET /api/order/{orderNo}`（列表带 `activityTitle`）
- `POST /api/order/{orderNo}/pay` → `PAID`（仅 CREATED 可付）
- `POST /api/order/{orderNo}/cancel` → `CANCELLED` + Redis 回滚（已购计数 -1）

关单/取消/过期互斥：`closeCreatedOrder` 用 DB 条件更新 `status=CREATED`，只成功一次，避免双重回滚。

---

## 8. 开关（`SeckillFeatureProperties`）

| 开关 | 关 | 开 |
|---|---|---|
| `seckill.mq.enabled` | core HTTP 同步建单；不注册 Listener；排除 RocketMQ 自动配置 | RocketMQ 异步建单 + 延迟关单/关抢 |
| `seckill.schedule.enabled` | 不注册扫表/对账 Job | 订单过期扫表 + 活动到期扫表 + 库存对账 |

环境变量：`SECKILL_MQ_ENABLED` / `SECKILL_SCHEDULE_ENABLED` / `SECKILL_MQ_AUTOCONFIG_EXCLUDE`。

---

## 9. 建议阅读顺序

1. `apps/web/src/api.js` + `Mall.vue` + `Activity.vue` + `OrderManage.vue`
2. `JwtAuthGlobalFilter` + gateway `application.yml`
3. `SeckillRedisKeys` + `StockLuaExecutor`
4. `SeckillService` + `OrderCreateClient`
5. `OrderService.createFromMessage` / `pay` / `cancel`
6. `ActivityService.preheat` / `open` / `doClose`
7. MQ 开时再看 `OrderCreateListener`、`OrderExpireListener`、`OrderMqConstants`

---

## 10. 并发与一致性要点

1. 库存真相在 Redis；DB `stock` 是配置快照。对账：`init ≈ redis + CREATED + PAID`
2. 限购：Lua `bought` 计数 vs `limit` 键；取消/超时 DECR 已购，同用户可再抢
3. 预扣成功但建单失败：core/order 都会回滚 Redis
4. 支付 vs 过期：靠 `CREATED` 条件更新互斥
5. 活动手动关 vs 到期关：到期时若已非 OPEN 则跳过；扫表 + 延迟消息双保险
