# 代码导读：从前端到完整订单链路

> 阅读顺序建议：先看本文总览与时序，再按「文件入口」跳进源码。  
> 当前默认：`seckill.mq.enabled=false`、`seckill.schedule.enabled=false`（同步建单、无定时关单/关抢）。  
> 架构总览见 [architecture.md](./architecture.md)。

---

## 1. 模块与入口对照

| 层 | 路径 | 端口（容器） | 职责 |
|---|---|---|---|
| B 端 | `apps/web` | 80 → NodePort 30080 | 登录、活动运营、会场抢购、订单支付/取消 |
| 网关 | `seckill-gateway` | 8080 | JWT 校验，注入 `X-User-*`，按路径转发 |
| 用户 | `seckill-user` | 8081 | 注册/登录/JWT、用户管理 |
| 活动 | `seckill-activity` | 8082 | 活动状态机、预热、开/关抢、Redis 库存基准 |
| 秒杀 | `seckill-core` | 8083 | Redis Lua 预扣 → 建单投递（MQ 或 HTTP 同步） |
| 订单 | `seckill-order` | 8084 | 幂等落库、支付、取消、过期 |
| 公共 | `seckill-common` | — | `Result`、MQ 消息体、Redis Key、功能开关 |

HTTP 统一经前端同源 `/api/**`（nginx 反代到 gateway）。

---

## 2. 完整链路鸟瞰（当前默认：MQ 关）

```text
[浏览器 Vue]
  Login → JWT 存 localStorage
  运营：建活动 → 预热 → 开抢
  会场：立即抢购
        │
        ▼
POST /api/seckill/{activityId}   (+ Authorization: Bearer)
        │
        ▼
[gateway JwtAuthGlobalFilter]
  验 JWT → 写入 X-User-Id / X-User-Role
  路由 /api/seckill/** → seckill-core:8083
        │
        ▼
[SeckillController.grab]
  → SeckillService.grab(activityId, userId)
        │
        ├─ StockLuaExecutor.deduct   # Redis 原子：开抢标记 / 限购 / DECR
        │     失败 → BusinessException（未开抢/重复/售罄）
        │
        ├─ 生成 orderToken + OrderCreateMessage
        │
        └─ mq=false → OrderCreateClient.createSync
              HTTP POST http://seckill-order:8084/api/order/internal/create
                    │
                    ▼
              [OrderInternalController] → OrderService.createFromMessage
                    查价 → insert t_order(CREATED) →（mq 开才投延迟关单）
        │
        ▼
返回 { activityId, orderToken, remainStock }
        │
        ▼
[订单页] POST /api/order/{orderNo}/pay → PAID
  或   POST /api/order/{orderNo}/cancel → CANCELLED + Redis 回滚
```

**MQ 打开时差异**：`SeckillService` 改为 `RabbitTemplate` 投递建单队列；`OrderCreateListener` 消费后同样进 `createFromMessage`。关单可走 TTL+DLX 延迟队列 + 扫表 Job（需 `schedule.enabled=true`）。

---

## 3. 前置：活动为何能抢（运营侧）

抢购前必须把活动推到 **OPEN**，并写好 Redis。读这些即可理解库存从哪来：

| 步骤 | API | 核心类 / 方法 | 说明 |
|---|---|---|---|
| 创建 | `POST /api/activity` | `ActivityService.create` | DB：`DRAFT`，库存只在 MySQL |
| 预热 | `POST /api/activity/{id}/preheat` | `ActivityService.preheat` | 写 `seckill:stock:{id}` + `seckill:stock:init:{id}`，状态 `PREHEATED` |
| 开抢 | `POST /api/activity/{id}/open` | `ActivityService.open` | 校验 Redis 库存存在；写 `seckill:open:{id}=1`；状态 `OPEN`；mq 开时投活动到期延迟消息 |
| 关抢 | `POST /api/activity/{id}/close` | `ActivityService.close` / `doClose` | `CLOSED` 终态；删 open 标记 |

状态机：`DRAFT → PREHEATED → OPEN → CLOSED`（CLOSED 不可再开，须新建活动）。

前端运营页：`apps/web/src/views/ActivityManage.vue`（调用 `api.js` 的 `createActivity` / `preheatActivity` / `openActivity`）。

Redis Key 约定（必读）：

```9:26:seckill-common/src/main/java/com/seckill/common/redis/SeckillRedisKeys.java
    public static String stock(long activityId) {
        return "seckill:stock:" + activityId;
    }
    // open / bought / stockInit ...
```

---

## 4. 前端：从点击「立即抢购」开始

### 4.1 路由

`apps/web/src/main.js`

- `/seckill` → 会场列表 `SeckillHome.vue`
- `/seckill/activity/:id` → 抢购页 `Activity.vue`
- `/seckill/result` → 结果页
- `/ops/orders` → 订单（支付/取消）

未登录跳转 `/login`；Token 在 `api.js` 的 `localStorage`。

### 4.2 抢购页

`apps/web/src/views/Activity.vue`

- `canGrab`：`status === 'OPEN'` 且在 `startAt`～`endAt` 之间（前端展示层判断，真正防超卖在 Redis Lua）
- `onGrab` → `grab(route.params.id)` → 跳转结果页

### 4.3 HTTP 封装

`apps/web/src/api.js`

```56:84:apps/web/src/api.js
async function request(path, options = {}) {
  // Content-Type + Authorization: Bearer <token>
  // 401 → clearAuth + toast
}
```

```138:155:apps/web/src/api.js
export function grab(activityId) {
  return request(`/api/seckill/${activityId}`, { method: 'POST' })
}
export function payOrder(orderNo) { ... }
export function cancelOrder(orderNo) { ... }
```

浏览器实际请求：`http://localhost:30080/api/seckill/{id}`（web 容器 nginx 反代到 gateway）。

---

## 5. 网关：鉴权与路由

### 5.1 路由表

`seckill-gateway/src/main/resources/application.yml`

| Path | 下游 |
|---|---|
| `/api/user/**` | `seckill-user:8081` |
| `/api/activity/**` | `seckill-activity:8082` |
| `/api/seckill/**` | `seckill-core:8083` |
| `/api/order/**` | `seckill-order:8084` |

白名单：`/api/user/login`、`/api/user/register`（无需 JWT）。

### 5.2 JWT Filter

`seckill-gateway/.../JwtAuthGlobalFilter.java`

1. 去掉客户端伪造的 `X-User-*`
2. 解析 `Authorization: Bearer`
3. 注入 `X-User-Id`（subject）、`X-User-Role`、`X-Username`
4. 失败返回 `{ code: 401, message: "未登录" }`

下游 **信任** 这些 Header（集群内网约定）；core 抢购只用 `X-User-Id`。

---

## 6. Core：预扣库存 + 触发建单

### 6.1 Controller

`seckill-core/.../SeckillController.java`

```23:29:seckill-core/src/main/java/com/seckill/core/controller/SeckillController.java
    @PostMapping("/{activityId}")
    public Result<Map<String, Object>> grab(
            @PathVariable long activityId,
            @RequestHeader("X-User-Id") long userId
    ) {
        return Result.ok(seckillService.grab(activityId, userId));
    }
```

### 6.2 Service 主流程

`seckill-core/.../SeckillService.java`（建议整文件通读）

1. `stockLuaExecutor.deduct(activityId, userId)`
2. 按返回码映射业务异常：`-3` 未开抢、`-1` 重复、`<0` 售罄
3. 生成 `orderToken`（UUID 去横线）与 `OrderCreateMessage`
4. `dispatchCreate`：
   - **`seckill.mq.enabled=false`（当前）**：`OrderCreateClient.createSync`
   - **`true`**：`RabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY_CREATE, message)`
5. 任一步失败：`stockLuaExecutor.rollback` 后抛「系统繁忙，库存已回滚」

### 6.3 Lua 预扣（防超卖 / 限购核心）

`seckill-core/.../StockLuaExecutor.java`

KEYS：`stock` / `bought:{user}` / `open`

逻辑摘要：

1. `open != 1` → `-3`
2. 已存在 bought → `-1`
3. stock &lt; 1 → `-2`
4. `DECR stock` + `SET bought=1` → 返回剩余库存

回滚与 order 侧共用 `StockRollbackHelper`（`seckill-common`）：库存 +1 并删 bought。

### 6.4 同步建单客户端

`seckill-core/.../OrderCreateClient.java`

- URL：`{seckill.order-uri}/api/order/internal/create`
- 默认 `http://seckill-order:8084`（**不经网关**，集群 Service DNS）
- 校验 HTTP 2xx 且 `Result.code == 0`

---

## 7. Order：落库与支付/取消

### 7.1 同步入口（MQ 关）

`seckill-order/.../OrderInternalController.java`

`POST /api/order/internal/create` → `OrderService.createFromMessage`

> 注意：该接口面向集群内 core；若误经网关暴露，需自行加鉴权（当前演示依赖内网）。

### 7.2 MQ 入口（MQ 开）

`seckill-order/.../OrderCreateListener.java`  
`@ConditionalOnProperty(seckill.mq.enabled=true)` → 同样调用 `createFromMessage`。

### 7.3 幂等建单

`OrderService.createFromMessage`（必读）

1. 按 `orderToken` 查重 → 已存在则直接返回  
2. `JdbcTemplate` 查 `t_activity.price_fen`；活动不存在则 **回滚 Redis** 并抛错  
3. `expireAt = createdAt + expireMinutes`（默认 3 分钟）  
4. `insert t_order`，状态 `CREATED`  
5. `scheduleExpire`：仅当 `mq.enabled=true` 时发延迟关单消息；否则跳过  
6. 插入失败（非重复键）→ 回滚 Redis  

订单状态常量见 `SeckillOrder`：`CREATED` / `PAID` / `CANCELLED` / `EXPIRED`。

### 7.4 用户侧 API

`OrderController`（经网关，需 JWT → `X-User-Id`）

| 方法 | 路径 | Service |
|---|---|---|
| 列表 | `GET /api/order/list` | `list`（非 ADMIN 只看自己） |
| 详情 | `GET /api/order/{orderNo}` | `detail` |
| 支付 | `POST /api/order/{orderNo}/pay` | `pay` → Mock 成功 → `PAID` |
| 取消 | `POST /api/order/{orderNo}/cancel` | `cancel` → `CANCELLED` + Redis 回滚 |

支付竞态：仅 `CREATED` 可付；若已过 `expireAt` 会先尝试关单再拒绝。  
关单/取消/过期互斥：`closeCreatedOrder` 用 DB 条件更新 `status=CREATED`，保证只成功一次，避免双重回滚。

前端：`OrderManage.vue` → `payOrder` / `cancelOrder`。

---

## 8. 开关与双路径对照

配置：`seckill-common/.../SeckillFeatureProperties`  
各模块 `application.yml`：`SECKILL_MQ_ENABLED` / `SECKILL_SCHEDULE_ENABLED`  
MQ 关时排除：`RabbitAutoConfiguration`（见 yml `spring.autoconfigure.exclude`）

| 能力 | mq=false, schedule=false（当前） | 均 true |
|---|---|---|
| 建单 | core → HTTP → order | core → RabbitMQ → Listener → order |
| 支付超时关单 | **不会自动**（无延迟消息、无扫表） | TTL+DLX + `OrderExpireScanJob` |
| 活动到期关抢 | **不会自动**（可手动 close） | TTL+DLX + `ActivityExpireScanJob` |
| RabbitMQ Pod | 可 `replicas=0` | `rabbitmq` Deployment 需要 |

相关 Job（均 `@ConditionalOnProperty(schedule.enabled=true)`）：

- `OrderExpireScanJob`
- `ActivityExpireScanJob`
- `StockReconcileScanJob`

---

## 9. 建议阅读顺序（半小时版）

1. `apps/web/src/api.js` + `Activity.vue` + `OrderManage.vue`  
2. `JwtAuthGlobalFilter` + gateway `application.yml` 路由  
3. `SeckillRedisKeys` + `StockLuaExecutor`  
4. `SeckillService` + `OrderCreateClient`  
5. `OrderService.createFromMessage` / `pay` / `cancel`  
6. （可选）`ActivityService.preheat` / `open` / `doClose`  
7. （可选）MQ 开时再看 `OrderCreateListener`、`OrderExpireListener`、`OrderMqConstants`

---

## 10. 并发与一致性要点（读代码时对照）

1. **库存真相**：开抢后以 Redis 为准；DB `stock` 是配置快照。对账公式：`init ≈ redis + CREATED + PAID`。  
2. **限购 1**：Lua `bought` 键；重复抢直接 `-1`。  
3. **预扣成功但建单失败**：core/order 都会尝试回滚 Redis，避免「钱扣了单没有」。  
4. **支付 vs 过期**：靠 `CREATED` 条件更新互斥。  
5. **当前关 MQ+扫表**：超时订单不会自动变 `EXPIRED`，演示时请手动支付或取消。

---

## 11. 本机环境备注

- K8s 工作负载已全部 **scale 到 0**（namespace `seckill` 与 PVC 仍在）。  
- 恢复：`./infra/scripts/deploy-local.sh` 或按需 `kubectl -n seckill scale deploy/<name> --replicas=1`。  
- 若还要省 Docker Desktop 内存：Settings → Kubernetes → 取消 Enable Kubernetes。
