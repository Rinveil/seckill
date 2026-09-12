# 后续实现顺序

以 [architecture.md](./architecture.md) 为准。

要点：K8s 全容器（arm64 / NodePort / PVC）；共享 MySQL + **MyBatis-Plus**；JWT；限流与压测后置。  
产品：注册仅 USER + 种子 ADMIN；活动开/关；直接改 Redis 库存；限购 1；Mock 支付成功；取消回滚库存；落单失败自动回滚。  
流程约定：每完成一步 → commit → **push 远程**。

1. K8s 最小可部署空壳  
2. 共享 MySQL + `user`（用户名 + JWT + 种子 ADMIN，MyBatis-Plus）  
3. `gateway`：JWT + Service DNS  
4. `apps/web`：Element Plus B 端骨架  
5. `activity`：CRUD + 开/关 + 预热/改 Redis 库存  
6. `core`：Lua 预扣 + 失败自动回滚  
7. `order`：MQ 建单 + Mock 支付 + 取消回滚  
8. （后置）限流、对账、压测
