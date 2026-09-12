# 爆款秒杀系统

IntelliJ 多模块工程：C 端页面 + API 网关 + 用户/活动/秒杀/订单服务。当前是可运行骨架，库存预扣先用内存实现，后续接 Redis + MQ。

## 模块

| 模块 | 端口 | 说明 |
|---|---|---|
| `apps/web` | 5173 | 秒杀首页 / 会场倒计时 / 抢购结果 |
| `seckill-gateway` | 8080 | 统一入口、CORS、路由 |
| `seckill-user` | 8081 | 登录占位 |
| `seckill-activity` | 8082 | 活动列表/详情 |
| `seckill-core` | 8083 | 秒杀预扣库存 |
| `seckill-order` | 8084 | 订单查询占位 |
| `seckill-common` | — | 统一返回体 |
| `infra` | — | MySQL / Redis / RabbitMQ |

## 本地启动

1. 基础设施（可选）：`docker compose -f infra/docker-compose.yml up -d`
2. IntelliJ 打开本目录，导入 Maven，分别运行各 `*Application`
3. 前端：安装 Node 后执行 `cd apps/web && npm install && npm run dev`
4. 浏览器打开 `http://127.0.0.1:5173`（Vite 已把 `/api` 代理到网关 `8080`）
