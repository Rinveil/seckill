# 爆款秒杀系统

唯一 B 端（Vue3 + Element Plus）+ 网关 + 用户 / 活动 / 秒杀 / 订单。  
**运行：Mac Docker Desktop K8s（全部容器）**；共享一个 MySQL；鉴权 **JWT**。

| 文档 | 内容 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 目标架构、状态机、开关、落地顺序 |
| [docs/risks.md](docs/risks.md) | 并发风险与实现缺口（对照当前代码） |
| [docs/code-walkthrough-order.md](docs/code-walkthrough-order.md) | 从前端到订单的代码导读 |
| [docs/deploy-plan.md](docs/deploy-plan.md) | 本机资源与跨机访问 |
| [docs/ci-cd.md](docs/ci-cd.md) | `deploy-local.sh` / `ci-deploy.sh` |
| [docs/test-report.md](docs/test-report.md) | 压测归档（以后不再压测） |
| [docs/roadmap.md](docs/roadmap.md) | 编号步骤（1–12 已完成） |

## 模块

| 模块 | 容器内端口 | 说明 |
|---|---|---|
| `apps/web` | 80 → NodePort 30080 | 登录/注册、商城、运营、抢购、订单 |
| `seckill-gateway` | 8080 | JWT、CORS、抢购 IP 限流、按路径转发 |
| `seckill-user` | 8081 | 注册/登录/JWT、用户管理、种子 ADMIN |
| `seckill-activity` | 8082 | 活动状态机、预热、开/关抢、商城公开 API、布隆重建 |
| `seckill-core` | 8083 | 布隆拦截 + Redis Lua 预扣 + MQ/HTTP 建单 |
| `seckill-order` | 8084 | 幂等落库、Mock 支付、取消/过期回滚 |
| `seckill-common` | — | Result、Redis Key、布隆、MQ 消息、开关 |
| `infra/k8s` | — | Namespace / PVC / 中间件 / 业务清单 |

## 本机跑起来

```bash
# Docker Desktop Kubernetes Ready，内存约 8GB
./infra/scripts/deploy-local.sh
# 浏览器
open http://localhost:30080/mall
```

种子账号：`admin` / `admin123`。密钥只在 K8s Secret / 环境变量。

当前 K8s：**RocketMQ 与扫表均开启**。压测不要跑 `load-test.py`。
