# 爆款秒杀系统 — 目标架构（定稿）

> 演示目标：在 Mac（**Apple Silicon / arm64**）用 **Docker Desktop Kubernetes** 部署全栈（前端 + 全部 Java + 中间件均在容器内），通过 **唯一 B 端（Vue3 + Element Plus）** 自测。  
> 不以本机 IntelliJ 启动 `*Application` 为运行方式。密钥只走环境变量 / K8s Secret。  
> **优先把项目跑通**。网关限流已落地；压测已归档（见 [test-report.md](./test-report.md)），以后不再做。

## 1. 已确认决策

| 项 | 选择 |
|---|---|
| 前端 | 仅 B 端：Vue3 + Vite + Element Plus（运营 + 自测抢购） |
| 部署 | Docker Desktop K8s；镜像 **arm64**；Docker 内存约 **8GB**；5 服务先各 1 副本 |
| 入口 | **NodePort** + localhost |
| 数据 | 共享 **一个 MySQL**；中间件 **PVC 持久化**；业务库访问统一 **MyBatis-Plus** |
| 鉴权 | **JWT**（网关本地验签）；Redis **不做** Session |
| Redis | 库存预扣、已购标记；B 端改库存 **直接改 Redis** |
| 后置 | 真实支付态（限流已落地；压测已归档） |

## 2. 产品约定（账号 / 活动 / 订单）

### 2.1 账号

| 项 | 约定 |
|---|---|
| 注册 | 开放；角色固定为 **USER**，不可选 ADMIN |
| 管理员 | **种子账号** + **运营用户管理**可创建 ADMIN；公开注册不可选 ADMIN |
| 用户管理 | B 端运营：列表筛选、创建、改昵称/角色、启停、重置密码；禁用账号不可登录 |
| 自测方式 | **同一账号可做运营 + 自测抢购**（用种子 ADMIN 即可两条链路都测）；普通 USER 主要用于抢购 |
| 标识 | 仅 **用户名**（不用手机、邮箱） |
| 验证码 | **无** |
| 密码 | 登录用明文传输（本地 HTTP 演示可接受）；库内 **BCrypt 哈希存储**（不落库明文） |

### 2.2 活动 / 库存

| 项 | 约定 |
|---|---|
| 状态机 | **DRAFT → PREHEATED → OPEN → CLOSED**；CLOSED 为**终态**，同活动不可再开，须**新建活动** |
| 改库存 | 仅 **PREHEATED** 允许 B 端直接改 Redis；OPEN/CLOSED 禁止 |
| 限购 | 每用户每活动 **可配** `limitPerUser`（默认 1） |

### 2.3 订单 / 支付 / 失败

| 项 | 约定 |
|---|---|
| 支付 | **要做支付回调/返回逻辑**，实现为 **Mock，固定返回成功** |
| 支付时限 | 待支付订单 **3 分钟**未付 → 自动关单（`EXPIRED`）并回滚 Redis 库存（延迟队列 TTL+DLX） |
| 活动到期 | 开抢时按 `end_at` 投延迟消息，到期自动关抢并删 Redis open 标记 |
| 取消 | **要做取消订单** |
| 回滚 | 取消 / 超时 / 落单失败时 **回滚 Redis 库存**，并清理已购标记 |
| 预扣成功但 MQ/落单失败 | **自动回滚 Redis 库存**（并清已购标记） |

## 3. 逻辑架构

```text
[B端 Vue3 + Element Plus]
        |
   Gateway  /api   （验 JWT → X-User-Id / X-User-Role）
   /    |     |     \
 user activity core order
   \    |     |     /
  共享 MySQL(PVC)  Redis(库存)  RocketMQ（可用 seckill.mq.enabled 关闭）
```

| 模块 | 职责 |
|---|---|
| `apps/web` | 登录注册、商城浏览、活动/订单/用户管理、数据看板、预热、自测抢购、Mock 支付 |
| `seckill-gateway` | 路由、CORS、JWT 校验与用户透传、抢购 IP 限流 |
| `seckill-user` | 注册(USER)、登录、种子 ADMIN、`/me`、**用户管理（ADMIN）**、签发 JWT |
| `seckill-activity` | 活动状态机；`end_at` 延迟+扫表关抢；库存对账；商城公开接口 |
| `seckill-core` | Redis Lua 预扣（`limitPerUser`）；`seckill.mq.enabled=true` 时 RocketMQ 投递，否则 HTTP 同步调 order 建单 |
| `seckill-order` | MQ/同步幂等建单；Mock 支付；超时关单（MQ 延迟或扫表，均可开关）；取消回滚 |
| `infra` | K8s（NodePort、PVC、arm64 镜像） |

## 4. 鉴权（JWT）

1. 注册仅 USER；登录校验 BCrypt → 签发 JWT（`userId`、`role`、TTL）  
2. 前端带 Token（Cookie 或 `Authorization: Bearer`）  
3. Gateway 验签 → 注入 `X-User-Id` / `X-User-Role`  
4. 运营写接口：`ADMIN`；抢购 / 下单 / 支付 / 取消：已登录即可  
5. `JWT_SECRET` 仅环境变量 / Secret  

## 5. 主链路（含支付与取消）

```text
开抢前：建活动(DRAFT) → 预热(PREHEATED) → 开抢(OPEN，按 end_at 投延迟关抢)
开抢：Lua 预扣(库存-1 + 已购) → MQ → order 落库（expire_at = now+3min，投延迟关单）
     └ 若投递/落单失败 → 自动回滚 Redis 库存 + 已购
支付：调用 Mock 支付 → 固定成功 → 订单状态=已支付（已超时则拒绝并走关单）
超时：延迟消息到期 → CREATED→EXPIRED → 回滚 Redis 库存 + 清已购
取消：订单取消 → 回滚 Redis 库存 + 清已购 → 订单状态=已取消
活动到期/手动关：OPEN→CLOSED（终态）→ 删 Redis open；不可再预热开抢
```

### 功能开关（`seckill.mq` / `seckill.schedule`）

| 开关 | 关闭时行为 |
|---|---|
| `seckill.mq.enabled=false` | 抢购不经 RocketMQ：core HTTP 同步调 order `/api/order/internal/create`；不注册 MQ Listener；不投延迟关单/关抢；排除 RocketMQ 自动配置（可停 NameServer/Broker） |
| `seckill.schedule.enabled=false` | 不注册订单过期扫表、活动到期扫表、库存对账定时任务 |

本机默认与 K8s 当前均为 **关闭**。重新开启：环境变量 `SECKILL_MQ_ENABLED=true`、`SECKILL_SCHEDULE_ENABLED=true`，并把 `infra/k8s/12-rocketmq.yaml` 的 `replicas` 改回 `1`。



- Namespace：`seckill`；镜像 **linux/arm64**  
- Docker 内存约 **8GB**；JVM 建议 256–512MB/服务  
- **NodePort** 暴露前端与（或统一）入口；`/api` → gateway  
- MySQL / Redis / RocketMQ Broker 使用 **PVC**  
- 集群内 Service DNS；禁止 `127.0.0.1`  

### 并发风险（实现时会再提示）

- 开抢中途 **直接改 Redis 库存** 可能与 Lua 预扣竞态，演示避免高峰时改库存  
- Mock 支付固定成功，无真实资金流  
- 取消 / 超时关单 / 「预扣失败回滚」都要改 Redis，需与预扣 Lua **同一套原子语义**，防止超卖或库存漂
- 支付与超时关单竞态：以 DB 条件更新（仅 `CREATED`）为准，保证幂等
- 活动手动关抢与到期关抢竞态：到期时若已非 OPEN 则跳过；扫表与延迟消息双保险
- 建单/过期消费失败：吞异常不重试（业务侧已回滚库存时避免重复回滚）；依赖扫表/对账兜底
- 对账依赖预热写入的 `seckill:stock:init:{id}`；旧活动需重新预热才有 init

## 7. 落地顺序

1. K8s 最小可部署空壳（中间件 PVC + 5 服务 + 前端壳，NodePort）  
2. 共享 MySQL + `user`（用户名注册/登录 + JWT + 种子 ADMIN）  
3. `gateway`：JWT 校验与透传；Service DNS 路由  
4. `apps/web`：Element Plus B 端骨架  
5. `activity`：活动 CRUD + 开/关；预热与直接改 Redis 库存  
6. `core`：Lua 预扣（1 件）+ 失败自动回滚  
7. `order`：MQ 建单 + **Mock 支付成功** + **取消并回滚库存**  
8. 订单支付超时（3 分钟）+ 活动 `end_at` 自动关抢  
9. 活动状态机 DRAFT→PREHEATED→OPEN→CLOSED（终态不复用）  
10. RocketMQ 延迟关单/关抢 + 扫表兜底 + 库存对账  
11. 用户管理（列表/创建/启停/改角色/重置密码）  
12. 轻量压测（已完成并归档，以后不再做）— [test-report.md](./test-report.md)  
13. （后置）真实支付态
