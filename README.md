# 爆款秒杀系统

IntelliJ 多模块工程：唯一 B 端（Vue3 + Element Plus）+ 网关 + 用户/活动/秒杀/订单。  
**运行：Mac Docker Desktop K8s（全部容器）**；共享一个 MySQL；登录鉴权用 **JWT**。

目标架构见 [docs/architecture.md](docs/architecture.md)；落地顺序见 [docs/roadmap.md](docs/roadmap.md)；  
K8s 空壳部署见 [infra/k8s/README.md](infra/k8s/README.md)。

## 模块

| 模块 | 容器内端口 | 说明 |
|---|---|---|
| `apps/web` | 80 | B 端（当前会场骨架，经 NodePort 30080） |
| `seckill-gateway` | 8080 | 路由（集群 Service DNS） |
| `seckill-user` | 8081 | 登录占位 → 后续 JWT |
| `seckill-activity` | 8082 | 活动占位 |
| `seckill-core` | 8083 | 秒杀占位（内存库存） |
| `seckill-order` | 8084 | 订单占位 |
| `infra/k8s` | — | Namespace / PVC / 中间件 / 业务清单 |

## 第 1–2 步自测（K8s）

1. 开启 Docker Desktop Kubernetes（约 8GB 内存）  
2. 构建镜像：`./infra/scripts/build-images.sh`（或只建 user：见下）  
3. 部署：`kubectl apply -f infra/k8s/`  
4. 浏览器：http://localhost:30080  

### 用户 API（第 2 步）

- `POST /api/user/register` `{"username":"u1","password":"pass123"}` → 仅 USER + JWT  
- `POST /api/user/login` `{"username":"admin","password":"admin123"}` → 种子 ADMIN  
- `GET /api/user/me` Header: `Authorization: Bearer <token>`  

密钥来自 K8s Secret（`JWT_SECRET` / MySQL 密码等），不写在业务配置明文里。
