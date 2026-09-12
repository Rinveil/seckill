# 爆款秒杀系统 — 目标架构（定稿）

> 演示目标：在 Mac（**Apple Silicon / arm64**）用 **Docker Desktop Kubernetes** 部署全栈（前端 + 全部 Java + 中间件均在容器内），通过 **唯一 B 端（Vue3 + Element Plus）** 自测。  
> 不以本机 IntelliJ 启动 `*Application` 为运行方式。密钥只走环境变量 / K8s Secret。  
> **优先把项目跑通**；网关限流、压测后置。

## 1. 已确认决策

| 项 | 选择 |
|---|---|
| 前端 | 仅 B 端：Vue3 + Vite + Element Plus（运营 + 自测抢购） |
| 部署 | Docker Desktop K8s；镜像 **arm64**；Docker 内存约 **8GB**；5 服务先各 1 副本 |
| 入口 | **NodePort** + localhost |
| 数据 | 共享 **一个 MySQL**；中间件 **PVC 持久化**；业务库访问统一 **MyBatis-Plus** |
| 鉴权 | **JWT**（网关本地验签）；Redis **不做** Session |
| Redis | 库存预扣、已购标记；B 端改库存 **直接改 Redis** |
| 后置 | 限流、压测 |

## 2. 产品约定（账号 / 活动 / 订单）

### 2.1 账号

| 项 | 约定 |
|---|---|
| 注册 | 开放；角色固定为 **USER**，不可选 ADMIN |
| 管理员 | **种子账号**写入（如启动/SQL 初始化），不开放注册成 ADMIN |
| 自测方式 | **同一账号可做运营 + 自测抢购**（用种子 ADMIN 即可两条链路都测）；普通 USER 主要用于抢购 |
| 标识 | 仅 **用户名**（不用手机、邮箱） |
| 验证码 | **无** |
| 密码 | 登录用明文传输（本地 HTTP 演示可接受）；库内 **BCrypt 哈希存储**（不落库明文） |

### 2.2 活动 / 库存

| 项 | 约定 |
|---|---|
| 状态 | 仅 **关 / 开**（不做草稿→预热→进行中等多态机） |
| 改库存 | 预热后允许 B 端 **直接改 Redis**（改 DB 库存为可选同步，实现时最小做 Redis） |
| 限购 | 每用户每活动 **1 件** |

### 2.3 订单 / 支付 / 失败

| 项 | 约定 |
|---|---|
| 支付 | **要做支付回调/返回逻辑**，实现为 **Mock，固定返回成功** |
| 取消 | **要做取消订单** |
| 回滚 | 取消时 **回滚 Redis 库存**，并清理已购标记 |
| 预扣成功但 MQ/落单失败 | **自动回滚 Redis 库存**（并清已购标记） |

## 3. 逻辑架构

```text
[B端 Vue3 + Element Plus]
        |
   Gateway  /api   （验 JWT → X-User-Id / X-User-Role）
   /    |     |     \
 user activity core order
   \    |     |     /
  共享 MySQL(PVC)  Redis(库存)  RabbitMQ
```

| 模块 | 职责 |
|---|---|
| `apps/web` | 登录注册、活动开关/库存、预热、自测抢购、订单、Mock 支付、取消 |
| `seckill-gateway` | 路由、CORS、JWT 校验与用户透传 |
| `seckill-user` | 注册(USER)、登录、种子 ADMIN、`/me`、签发 JWT |
| `seckill-activity` | 活动 CRUD、开/关；共享 MySQL |
| `seckill-core` | Redis Lua 预扣（限 1）；发 MQ；失败自动回滚库存 |
| `seckill-order` | MQ 幂等建单；Mock 支付成功；取消订单 + 通知回滚库存 |
| `infra` | K8s（NodePort、PVC、arm64 镜像） |

## 4. 鉴权（JWT）

1. 注册仅 USER；登录校验 BCrypt → 签发 JWT（`userId`、`role`、TTL）  
2. 前端带 Token（Cookie 或 `Authorization: Bearer`）  
3. Gateway 验签 → 注入 `X-User-Id` / `X-User-Role`  
4. 运营写接口：`ADMIN`；抢购 / 下单 / 支付 / 取消：已登录即可  
5. `JWT_SECRET` 仅环境变量 / Secret  

## 5. 主链路（含支付与取消）

```text
开抢前：建活动(DB) → 预热 Redis → 状态=开
开抢：Lua 预扣(库存-1 + 已购) → MQ → order 落库
     └ 若投递/落单失败 → 自动回滚 Redis 库存 + 已购
支付：调用 Mock 支付 → 固定成功 → 订单状态=已支付
取消：订单取消 → 回滚 Redis 库存 + 清已购 → 订单状态=已取消
```

## 6. 运行时（Docker Desktop K8s）

- Namespace：`seckill`；镜像 **linux/arm64**  
- Docker 内存约 **8GB**；JVM 建议 256–512MB/服务  
- **NodePort** 暴露前端与（或统一）入口；`/api` → gateway  
- MySQL / Redis / RabbitMQ 使用 **PVC**  
- 集群内 Service DNS；禁止 `127.0.0.1`  

### 并发风险（实现时会再提示）

- 开抢中途 **直接改 Redis 库存** 可能与 Lua 预扣竞态，演示避免高峰时改库存  
- Mock 支付固定成功，无真实资金流  
- 取消与「预扣失败回滚」都要改 Redis，需与预扣 Lua **同一套原子语义**，防止超卖或库存漂

## 7. 落地顺序

1. K8s 最小可部署空壳（中间件 PVC + 5 服务 + 前端壳，NodePort）  
2. 共享 MySQL + `user`（用户名注册/登录 + JWT + 种子 ADMIN）  
3. `gateway`：JWT 校验与透传；Service DNS 路由  
4. `apps/web`：Element Plus B 端骨架  
5. `activity`：活动 CRUD + 开/关；预热与直接改 Redis 库存  
6. `core`：Lua 预扣（1 件）+ 失败自动回滚  
7. `order`：MQ 建单 + **Mock 支付成功** + **取消并回滚库存**  
8. （后置）限流、对账、压测  

`mvn` / 压测执行前须确认。
