# 后续实现顺序

以 [architecture.md](./architecture.md) 为准。缺口与并发见 [risks.md](./risks.md)。

要点：K8s 全容器（arm64 / NodePort / PVC）；共享 MySQL + **MyBatis-Plus**；JWT。压测已归档，以后不再做。  
产品：注册仅 USER + 种子 ADMIN；活动状态机终态不复用；限购可配（默认 1）；Mock 支付；取消/超时回滚。  
流程约定：每完成一步 → commit → **push 远程** → Agent 本机 `deploy-local.sh` 部署，见 [ci-cd.md](./ci-cd.md)。

| # | 步骤 | 状态 |
|---|---|---|
| 1 | K8s 最小可部署空壳 | 已完成 |
| 2 | 共享 MySQL + `user`（用户名 + JWT + 种子 ADMIN） | 已完成 |
| 3 | `gateway`：JWT + Service DNS | 已完成 |
| 4 | `apps/web`：Element Plus B 端 | 已完成 |
| 5 | `activity`：CRUD + 开/关 + 预热/改 Redis | 已完成 |
| 6 | `core`：Lua 预扣 + 失败回滚 + 布隆 | 已完成 |
| 7 | `order`：MQ 建单 + Mock 支付 + 取消回滚 | 已完成（支付 CAS 未做） |
| 8 | 订单 3 分钟超时 + 活动 `end_at` 关抢 | 已完成 |
| 9 | 状态机 DRAFT→PREHEATED→OPEN→CLOSED | 已完成 |
| 10 | RocketMQ 延迟 + 扫表 + 对账 | 已完成 |
| 11 | 用户管理 | 已完成 |
| 12 | 轻量压测 | 已归档，不再做 |
| 13 | 真实支付 | **后置，未做** |

编号外已做：公开商城、网关抢购限流、活动布隆、数据看板、前端克隆表单、可配 `limitPerUser`。

未做且值得修（非新功能）：内部建单口不进网关、`pay()` 条件更新。见 [risks.md](./risks.md)。
