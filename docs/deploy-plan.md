# 本机部署方案（Apple M5 / 16GB / arm64）

> 目标：全功能跑通（RocketMQ 异步建单 + 延迟关单/关抢 + 扫表兜底 + 对账 + 商城浏览），同时控制内存/CPU。

## 1. 机器

| 项 | 值 |
|---|---|
| CPU | Apple M5，10 核 |
| 内存 | 16 GB |
| 架构 | arm64 |
| 局域网 IP | 192.168.2.53 |

## 2. Docker Desktop 配置

- Settings → Resources：**8 GB 内存 / 4 CPU / 64 GB 磁盘**
- Settings → Kubernetes：启用
- 留 8 GB 给 macOS + IDE + 浏览器

## 3. 功能开关

| 开关 | 值 | 作用 |
|---|---|---|
| `SECKILL_MQ_ENABLED` | `true` | 抢购走 RocketMQ 异步建单；投延迟关单/关抢 |
| `SECKILL_SCHEDULE_ENABLED` | `true` | 订单过期扫表、活动到期扫表、库存对账定时任务 |
| `SECKILL_MQ_AUTOCONFIG_EXCLUDE` | `""` | 不排除 RocketMQ 自动配置 |

## 4. 资源配置（K8s limits）

| 组件 | replicas | JVM | request | limit |
|---|---|---|---|---|
| rocketmq-broker | 1 | -Xms256m -Xmx512m | 512Mi | 1Gi |
| rocketmq-namesrv | 1 | -Xms128m -Xmx256m | 256Mi | 384Mi |
| mysql | 1 | — | 256Mi | 512Mi |
| redis | 1 | — | 32Mi | 128Mi |
| seckill-user | 1 | -Xms128m -Xmx256m | 128Mi | 384Mi |
| seckill-activity | 1 | -Xms128m -Xmx256m | 128Mi | 384Mi |
| seckill-core | 1 | -Xms128m -Xmx256m | 128Mi | 384Mi |
| seckill-order | 1 | -Xms128m -Xmx256m | 128Mi | 384Mi |
| seckill-gateway | 1 | -Xms128m -Xmx256m | 128Mi | 384Mi |
| seckill-web | 1 | — | 32Mi | 128Mi |
| **合计 limits** | | | | **~4.1 GiB** |

实际占用约 **2.5–3 GiB**，Docker 8GB 内有余量。

## 5. 跨机访问（同 WiFi）

`seckill-web` Service 已加 `externalIPs: [192.168.2.53]`：

```yaml
spec:
  type: NodePort
  externalIPs:
    - 192.168.2.53
  ports:
    - port: 80
      targetPort: 80
      nodePort: 30080
```

其他机器浏览器：`http://192.168.2.53:30080`

- macOS 防火墙需放行 30080 入站
- IP 变了改 `30-web.yaml` 的 `externalIPs`

## 6. 部署命令

```bash
# 前置：Docker Desktop 已开，Kubernetes Ready，分配 8GB
cd /Users/zheng/IdeaProjects/seckill
./infra/scripts/deploy-local.sh          # 全量构建 + apply + 重启
kubectl -n seckill get pods              # 等全部 Ready
```

## 7. 验证清单

- [ ] 全部 Pod `1/1 Running`（含 rocketmq-broker/namesrv）
- [ ] 浏览器 `http://localhost:30080/mall` 看到商城卡片
- [ ] `http://192.168.2.53:30080/mall` 跨机可访问
- [ ] 登录 admin → 建活动 → 预热 → 开抢
- [ ] 普通用户抢购 → 订单 CREATED → 支付 PAID
- [ ] 不支付等 3 分钟 → 自动 EXPIRED + 回滚库存（MQ 延迟）
- [ ] 对账 `consistent=true`

## 8. 降占用回退

若需再降：把 `SECKILL_MQ_ENABLED=false`、`SECKILL_SCHEDULE_ENABLED=false`，rocketmq replicas 改 0，JVM 回 `-Xmx192m`，limits 回 384Mi。约 2.2 GiB。
