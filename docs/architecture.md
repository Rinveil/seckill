# 爆款秒杀系统 — 目标架构（定稿）

> 演示目标：在 Mac（**Apple Silicon / arm64**）用 **Docker Desktop Kubernetes** 部署全栈（前端 + 全部 Java + 中间件均在容器内），通过 **唯一 B 端（Vue3 + Element Plus）** 自测。  
> 不以本机 IntelliJ 启动 `*Application` 为运行方式。密钥只走环境变量 / K8s Secret。  
> **优先把项目跑通**。网关限流、活动布隆、商城浏览已落地；压测已归档（见 [test-report.md](./test-report.md)），以后不再做。  
> 并发与未修缺口见 [risks.md](./risks.md)。

## 1. 已确认决策

| 项 | 选择 |
|---|---|
| 前端 | 唯一 B 端：Vue3 + Vite + Element Plus（商城浏览 + 运营 + 自测抢购） |
| 部署 | Docker Desktop K8s；镜像 **arm64**；Docker 内存约 **8GB**；业务各 1 副本 |
| 入口 | **NodePort 30080** + localhost（可选 `externalIPs` 跨机） |
| 数据 | 共享 **一个 MySQL**；中间件 **PVC 持久化**；业务库访问统一 **MyBatis-Plus** |
| 鉴权 | **JWT**（网关本地验签，TTL 默认 2h）；Redis **不做** Session |
| Redis | 库存预扣、已购标记、开抢活动布隆（防 ID 穿透）；B 端改库存 **直接改 Redis**（仅 PREHEATED） |
| 后置 | 真实支付（未做；Mock 固定成功） |

## 2. 产品约定（账号 / 活动 / 订单）

### 2.1 账号

| 项 | 约定 |
|---|---|
| 注册 | 开放；角色固定为 **USER**，不可选 ADMIN |
| 管理员 | **种子账号** + **运营用户管理**可创建 ADMIN；公开注册不可选 ADMIN |
| 用户管理 | B 端运营：列表筛选、创建、改昵称/角色、启停、重置密码；禁用后不能登录，网关拒绝未过期 JWT |
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
| 支付 | **Mock，固定返回成功**（无真实回调验签） |
| 支付时限 | 待支付 **3 分钟**未付 → `EXPIRED` 并回滚 Redis（RocketMQ 延迟消息 + 扫表兜底） |
| 活动到期 | 开抢时按 `end_at` 投延迟消息，到期自动关抢并删 Redis open 标记、重建布隆 |
| 取消 | **要做取消订单** |
| 回滚 | 取消 / 超时 / 落单失败时 **回滚 Redis 库存**，并清理已购标记 |
| 预扣成功但 MQ/落单失败 | **自动回滚 Redis 库存**（并清已购标记） |
| 商城 | 公开 `GET /api/mall/**`；DRAFT 不对外 |
| 克隆 | 前端表单拷贝，新建活动（无后端 clone API） |

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
| `seckill-core` | 布隆拦截无效活动 ID；Redis Lua 预扣（`limitPerUser`）；`seckill.mq.enabled=true` 时 RocketMQ 投递，否则 HTTP 同步调 order 建单 |
| `seckill-order` | MQ/同步幂等建单；Mock 支付；超时关单（MQ 延迟或扫表，均可开关）；取消回滚 |
| `infra` | K8s（NodePort、PVC、arm64 镜像） |

## 4. 鉴权（JWT）

1. 注册仅 USER；登录校验 BCrypt 且账号须启用 → 签发 JWT（`userId`、`role`、TTL 默认 2h）  
2. 前端 `localStorage` + `Authorization: Bearer`（不用 Cookie Session）  
3. Gateway 验签 → 注入 `X-User-Id` / `X-User-Role`（先剥客户端伪造头）  
4. 运营写接口：`ADMIN`；抢购 / 下单 / 支付 / 取消：已登录即可  
5. `JWT_SECRET` 仅环境变量 / Secret  
6. 禁用账号：登录、`/me` 与网关 Redis 标记都会拦；网关 Redis 异常时 fail-open  

## 5. 主链路（含支付与取消）

```text
开抢前：建活动(DRAFT) → 预热(PREHEATED) → 开抢(OPEN，按 end_at 投延迟关抢)
开抢：布隆（无效 ID 直接拒）→ Lua 预扣(库存-1 + 已购) → MQ → order 落库（expire_at = now+3min，投延迟关单）
     └ 若投递/落单失败 → 自动回滚 Redis 库存 + 已购
支付：调用 Mock 支付 → 固定成功 → 订单状态=已支付（已超时则拒绝并走关单）
超时：延迟消息到期 → CREATED→EXPIRED → 回滚 Redis 库存 + 清已购
取消：订单取消 → 回滚 Redis 库存 + 清已购 → 订单状态=已取消
活动到期/手动关：OPEN→CLOSED（终态）→ 删 Redis open、重建布隆；不可再预热开抢
```

### 功能开关（`seckill.mq` / `seckill.schedule`）

| 开关 | 关闭时行为 |
|---|---|
| `seckill.mq.enabled=false` | 抢购 HTTP 同步调 order `/api/order/internal/create`（不经网关）；不注册 MQ Listener；关单/关抢改本机 `TaskScheduler`；排除 RocketMQ 自动配置 |
| `seckill.schedule.enabled=false` | 不注册订单过期扫表、活动到期扫表、库存对账 Job（本机延迟仍在） |

本机 `application.yml` 默认两开关为 **false**。**当前 K8s 为 true**。  
重新开启 MQ：`SECKILL_MQ_ENABLED=true`、`SECKILL_SCHEDULE_ENABLED=true`，RocketMQ replicas=1。

## 6. 部署形态

- Namespace：`seckill`；镜像 **linux/arm64**，tag `0.1.0`  
- Docker 内存约 **8GB**；JVM 建议 256MB/服务（Broker limit **2Gi**）  
- **NodePort 30080**；`/api` → gateway  
- MySQL / Redis / RocketMQ Broker 使用 **PVC**  
- 集群内 Service DNS；禁止业务代码写 `127.0.0.1`  
- 本机部署：`./infra/scripts/deploy-local.sh`，见 [ci-cd.md](./ci-cd.md)、[deploy-plan.md](./deploy-plan.md)

## 7. 并发风险（摘要）

完整清单见 **[risks.md](./risks.md)**。

| 级别 | 点 | 代码事实 |
|---|---|---|
| 高 | 内部建单口经网关暴露 | **已修**：网关 403 + order 拒绝带 `X-User-Id` 的调用 |
| 高 | 支付未 CAS | **已修**：`pay()` 与过期/取消同样 `CREATED` 条件更新 |
| 中 | 预扣成功 MQ 失败 | 回滚库存，用户看到「系统繁忙」需重试 |
| 中 | 开关双关 | **已修**：MQ 关时本机 `TaskScheduler` 延迟关单/关抢 |
| 中 | PREHEATED 改限购未再预热 | **已修**：更新时同步写 Redis `limit` |
| 低 | 布隆假阳性 / fail-open | 假阳性进 Lua；`ready` 缺失不误杀真开抢 |
| 低 | 限流单机 | 仅 `/api/seckill/**` 与登录注册；IP 取 nginx `X-Real-IP` |

其它已按设计落地的：Lua 预扣与回滚同一语义；建单 `orderNo` 幂等；关抢与到期双保险；对账 `init ≈ redis + CREATED + PAID`（依赖预热写入的 init）。

## 8. 落地顺序

1. K8s 最小可部署空壳（中间件 PVC + 5 服务 + 前端壳，NodePort）— **已完成**  
2. 共享 MySQL + `user`（用户名注册/登录 + JWT + 种子 ADMIN）— **已完成**  
3. `gateway`：JWT 校验与透传；Service DNS 路由 — **已完成**  
4. `apps/web`：Element Plus B 端 — **已完成**（商城 / 看板 / 用户管理 / 克隆表单）  
5. `activity`：活动 CRUD + 开/关；预热与直接改 Redis 库存 — **已完成**  
6. `core`：Lua 预扣 + 失败自动回滚 + 布隆拦截无效 ID — **已完成**  
7. `order`：MQ 建单 + **Mock 支付成功** + **取消并回滚库存** — **已完成**（支付 CAS 已做）  
8. 订单支付超时（3 分钟）+ 活动 `end_at` 自动关抢 — **已完成**  
9. 活动状态机 DRAFT→PREHEATED→OPEN→CLOSED（终态不复用）— **已完成**  
10. RocketMQ 延迟关单/关抢 + 扫表兜底 + 库存对账 — **已完成**  
11. 用户管理（列表/创建/启停/改角色/重置密码）— **已完成**  
12. 轻量压测（已归档，以后不再做）— [test-report.md](./test-report.md)  
13. （后置）真实支付 — **未做**

编号外已做：网关抢购 IP 限流、公开商城 API、活动布隆。
