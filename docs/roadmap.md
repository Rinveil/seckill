# 后续实现顺序

以 [architecture.md](./architecture.md) 为准。

要点：K8s 全容器（arm64 / NodePort / PVC）；共享 MySQL + **MyBatis-Plus**；JWT；单机 Docker 轻量并发（目标约 300 QPS、0 超卖）。  
产品：注册仅 USER + 种子 ADMIN；活动状态机终态不复用；限购 1；Mock 支付；取消/超时回滚。  
流程约定：每完成一步 → commit → **push 远程** → Agent 本机 `deploy-local.sh` 部署，见 [ci-cd.md](./ci-cd.md)。

1. K8s 最小可部署空壳  
2. 共享 MySQL + `user`（用户名 + JWT + 种子 ADMIN，MyBatis-Plus）  
3. `gateway`：JWT + Service DNS  
4. `apps/web`：Element Plus B 端骨架  
5. `activity`：CRUD + 开/关 + 预热/改 Redis 库存  
6. `core`：Lua 预扣 + 失败自动回滚  
7. `order`：MQ 建单 + Mock 支付 + 取消回滚  
8. 订单支付超时（3 分钟）+ 活动 `end_at` 自动关抢  
9. 活动状态机 DRAFT→PREHEATED→OPEN→CLOSED（终态，同活动不复用）  
10. RocketMQ 延迟关单/关抢 + 扫表兜底 + 库存对账  
11. 用户管理（列表/创建/启停/改角色/重置密码）  
12. 轻量压测（跑前确认）  
13. （后置）限流、真实支付态
