# 秒杀系统测试报告

> **已归档，以后不再压测。** 本文是既有一版结果，仅作查阅。  
> 测试时间：2026-09-13 22:44 ~ 23:02（CST）  
> 入口：`http://localhost:30080`（NodePort）  
> 脚本：`infra/scripts/e2e-smoke.py`、`infra/scripts/load-test.py`  
> 原始指标：`/tmp/seckill-load-*.txt`、`/tmp/seckill-smoke-results.json`

---

## 1. 环境

| 项 | 说明 |
|---|---|
| 主机 | Mac Apple Silicon（`arm64` / `aarch64`） |
| 运行时 | Docker Desktop Kubernetes |
| Docker 内存 | 约 **7.75 GiB** |
| Namespace | `seckill` |
| 镜像平台 | `linux/arm64`，tag `0.1.0` |
| 入口 | NodePort **30080**（前端 `/` + 网关 `/api`） |
| 中间件 | MySQL（PVC）、Redis（PVC）、RocketMQ Namesrv + Broker（PVC） |
| 业务服务 | gateway / user / activity / core / order / web 各 1 副本 |
| 种子账号 | `admin` / `admin123` |
| 架构目标 | 约 **300 QPS**、**0 超卖**（见 `docs/architecture.md`） |

---

## 2. 部署结果

### 2.1 部署命令

```bash
cd /Users/zheng/IdeaProjects/seckill
./infra/scripts/deploy-local.sh
kubectl -n seckill rollout status deploy/rocketmq-namesrv --timeout=180s
kubectl -n seckill rollout status deploy/rocketmq-broker --timeout=240s
kubectl -n seckill get pods
```

- 全量构建 + apply + rollout restart：**成功**（约 11.7 分钟，`exit_code=0`）
- RocketMQ Namesrv / Broker：`successfully rolled out`

### 2.2 Pod 列表（时间戳：2026-09-13 23:02:14 CST）

```text
NAME                                READY   STATUS    RESTARTS        AGE
mysql-64b8fc4d74-tg549              1/1     Running   1 (3m36s ago)   34h
redis-656678b765-gsx7j              1/1     Running   0               34h
rocketmq-broker-9d8f55c95-vnwtk     1/1     Running   0               26m
rocketmq-namesrv-84bd945c7c-zfg77   1/1     Running   0               130m
seckill-activity-5946c7d887-g2km2   1/1     Running   0               6m45s
seckill-core-55d9b84bf9-8222q       1/1     Running   0               6m45s
seckill-gateway-769f8b9944-rfm4c    1/1     Running   0               6m44s
seckill-order-7c5c7cbb9-wskmh       1/1     Running   0               6m45s
seckill-user-85c84c4b8d-q6xcz       1/1     Running   0               6m45s
seckill-web-6f4f8d854-hm488         1/1     Running   0               6m44s
```

**结论：业务 Pod 全部 Ready；中间件 Ready。** MySQL 在压测窗口内出现过 1 次重启（资源紧张迹象，见风险）。

---

## 3. 功能冒烟测试（HTTP API）

基址：`http://localhost:30080/api`  
约定：响应 envelope `{"code":0,...}` 表示成功；鉴权 `Authorization: Bearer <token>`。

| # | 步骤 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 前端首页 `GET /` | HTTP 200 | HTTP 200 | **PASS** |
| 1 | `/api` 可达（未登录访问活动列表） | 网关有响应 | HTTP 401 `未登录` | **PASS** |
| 2 | `POST /user/login` admin | code=0 + token | code=0，token 正常 | **PASS** |
| 3a | `POST /activity` stock=20 | 创建成功 | activityId=6 | **PASS** |
| 3b | `POST /activity/{id}/preheat` | code=0 | ok | **PASS** |
| 3c | `POST /activity/{id}/open` | status=OPEN | OPEN，redisStock=20 | **PASS** |
| 4a | 注册/登录 USER | 获得 token | 成功 | **PASS** |
| 4b | `POST /seckill/{id}` 首次抢购 | code=0 + orderToken | 成功返回 orderToken | **PASS** |
| 4c | 订单落库 | CREATED（允许 MQ 延迟轮询） | ~1s 内落库 CREATED | **PASS** |
| 5 | `POST /order/{orderNo}/pay` | status=PAID | PAID | **PASS** |
| 6 | 同用户再次抢购 | code=1004 DUPLICATE | 1004 请勿重复下单 | **PASS** |
| 7a | 创建第二活动 | 成功 | activityId=7 | **PASS** |
| 7b | 另一用户抢购 | code=0 | 成功 | **PASS** |
| 7c | 取消订单 | code=0 | 成功 | **PASS** |
| 7d | 取消后对账 | consistent=true，库存回滚 | redis 4→5，consistent | **PASS** |
| 8 | 活动对账 | init = redis + CREATED + PAID | init=20 redis=19 paid=1 | **PASS** |
| 9 | 短 endAt 活动开抢 | open 成功（未等待到期） | activityId=8 | **PASS** |

**功能冒烟：17/17 PASS。**

说明：首次冒烟时曾因 MQ 消费延迟约十几秒、脚本等待不足导致「订单未落库」假失败；改为轮询后稳定通过。冷启动后 MQ 偶发延迟需在测试与产品体验中考虑。

---

## 4. 并发 / 压测

工具：Python `ThreadPoolExecutor` / 多线程（本机无 `hey`/`wrk`，有 `ab`，统一用脚本便于带 JWT 与业务码统计）。

指标定义：

- **HTTP QPS**：客户端发出请求数 / 墙钟耗时  
- **业务 TPS**：业务 `code=0`（抢购成功、拿到 orderToken）数 / 墙钟耗时  
- **超卖校验**：`GET /activity/{id}/reconcile`，要求 `consistent=true` 且 `redisStock >= 0`，满足 `init ≈ redis + CREATED + PAID`

### 4.1 场景 A：预热 Warm-up

| 项 | 值 |
|---|---|
| 活动 | id=9，stock=50 |
| 并发 | 50 独立用户各抢 1 次 |
| 耗时 | 1.056 s |
| 总请求 | 50 |
| 成功 code=0 | 50 |
| HTTP QPS | **47.35** |
| 业务 TPS | **47.35** |
| 成功率 | 100% |
| 延迟 | p50=423.5ms，p95=767.6ms，p99=952.6ms |
| 原始文件 | `/tmp/seckill-load-warm-1789311506.txt` |

### 4.2 场景 B：目标 Burst（库存量级唯一用户）

#### B1：200 用户 × 并发 200（stock=200）

| 项 | 值 |
|---|---|
| 活动 | id=14，stock=200 |
| 并发 / 请求 | 200 / 200 |
| 耗时 | 0.806 s |
| 成功 code=0 | **200** |
| 售罄/重复 | 0 / 0 |
| HTTP QPS | **248.13** |
| 业务 TPS | **248.13** |
| 成功率 | **100%** |
| 延迟 | p50=415.7ms，p95=772.6ms，p99=787.2ms |
| 对账 | redis=0，created=200，**consistent=true**，无超卖 |
| 原始文件 | `/tmp/seckill-load-burst-1789311687.txt` |

#### B2：300 用户 × 并发 250（stock=300，逼近目标）

| 项 | 值 |
|---|---|
| 活动 | id=15，stock=300 |
| 并发 / 请求 | 250 / 300 |
| 耗时 | 1.16 s |
| 成功 code=0 | **300** |
| HTTP QPS | **258.56** |
| 业务 TPS | **258.56** |
| 成功率 | **100%** |
| 延迟 | p50=619.6ms，p95=1016.3ms，p99=1055.8ms |
| 对账 | redis=0，created=300，**consistent=true**，无超卖 |
| 原始文件 | `/tmp/seckill-load-burst-1789311734.txt` |

#### B3：200 用户 × 并发 100（对照，出现 MQ 繁忙）

| 项 | 值 |
|---|---|
| 活动 | id=13，stock=200 |
| 成功 / 失败 | 167 成功，**33** 业务 400 |
| HTTP QPS | 128.65 |
| 业务 TPS | 107.42 |
| 对账 | redis=33 + created=167 = 200，**consistent=true** |
| 失败原因 | RocketMQ Broker：`[TIMEOUT_CLEAN_QUEUE] broker busy` → core 回滚 Redis 库存 |
| 原始文件 | `/tmp/seckill-load-burst-1789311665.txt` |

**要点：即便 MQ 投递失败，Lua 预扣后失败会回滚库存，对账仍一致，未出现超卖。**

### 4.3 场景 C：Hammer（持续打满，测 HTTP 吞吐）

限购 1 件/用户，故大量请求为 `DUPLICATE(1004)`；用于观察网关+core 在重复请求下的 HTTP QPS。

| 场景 | 并发 | 时长 | 总请求 | code=0 | DUPLICATE | HTTP QPS | 业务 TPS | 对账 |
|---|---|---|---|---|---|---|---|---|
| Hammer-1 | 150 | 20 s | 15017 | 80 | 14937 | **749.77** | 3.99 | consistent |
| Hammer-2 | 200 | 15 s | 13822 | 100 | 13715 | **913.56** | 6.61 | consistent |

延迟（Hammer-2）：p50=190.3ms，p95=497.3ms，p99=826.4ms  
原始文件：`/tmp/seckill-load-hammer-1789311583.txt`、`/tmp/seckill-load-hammer-1789311611.txt`

### 4.4 压测数字汇总

| 指标 | 结果 | 备注 |
|---|---|---|
| HTTP 层峰值 QPS | **~914** | Hammer 200 并发 × 15s |
| 业务成功峰值 TPS | **~259** | Burst 300 库存 / 250 并发 |
| 逼近架构目标 300 QPS | 业务成功约 **259 TPS**；HTTP 已远超 300 | Docker Desktop 8GB 资源受限 |
| 超卖 | **0** | 各场景 reconcile 一致 |
| Redis 负库存 | **未出现** | — |

---

## 5. 结论与风险

### 5.1 结论

1. **全量本机部署成功**，全部 Pod Ready，冒烟链路（登录→建活动→预热/开抢→抢购→支付→重复拦截→取消回滚→对账）通过。  
2. **防超卖有效**：Redis Lua 预扣 + 失败回滚 + 对账公式 `init = redis + CREATED + PAID` 在压测后均一致。  
3. **吞吐**：  
   - HTTP QPS 峰值约 **914**（含重复拦截）  
   - 真实成交业务 TPS 峰值约 **259**（接近目标 300，未完全摸到）  
4. 架构目标「~300 QPS / 0 超卖」：**0 超卖达标**；业务成功 TPS 达到约 **86%** 目标值，HTTP 层已超过目标。

### 5.2 风险与观察

| 风险 | 说明 |
|---|---|
| RocketMQ Broker 忙 | 突发并发时出现 `broker busy` / `TIMEOUT_CLEAN_QUEUE`，同步 `syncSend` 失败后业务返回 400「系统繁忙，库存已回滚」。正确防超卖，但用户体验为失败需重试。 |
| MQ 消费延迟 | 冷启动后偶发十余秒才落单；支付/列表需轮询或异步通知。 |
| Docker 内存约 8GB | MySQL 压测窗口内有重启记录；再提高并发可能抖动。 |
| 限购 1 | Hammer 场景业务 TPS 会被 DUPLICATE 拉低，评估成交能力应以「唯一用户 Burst」为准。 |
| 同步发 MQ | 抢购接口等待 Broker ACK，延迟 p50 约 0.4~0.7s，限制业务 TPS 上限。 |

### 5.3 建议（非本次必须）

- Broker 资源/流控参数调优，或抢购侧异步投递 + 更明确的「排队中」语义  
- 压测前预热 Topic/Consumer，避免冷启动延迟干扰功能验收  
- 后续再测限流（架构后置项）

---

## 6. 压测命令复现

```bash
cd /Users/zheng/IdeaProjects/seckill

# 0) 部署
./infra/scripts/deploy-local.sh
kubectl -n seckill get pods

# 1) 功能冒烟
python3 infra/scripts/e2e-smoke.py
# 结果：/tmp/seckill-smoke-results.json

# 2) 准备活动 + 用户（示例 stock=200）
python3 infra/scripts/load-test.py prepare \
  --stock 200 --users 220 --prefix "burst$(date +%s)_" \
  --out /tmp/seckill-load-meta-burst.json

# 3) Warm-up
python3 infra/scripts/load-test.py warm \
  --meta /tmp/seckill-load-meta-burst.json --n 50 --concurrency 50

# 4) Burst（唯一用户抢购，测业务 TPS / 超卖）
python3 infra/scripts/load-test.py burst \
  --meta /tmp/seckill-load-meta-burst.json --n 200 --concurrency 200

# 5) Hammer（持续加压，测 HTTP QPS）
python3 infra/scripts/load-test.py prepare \
  --stock 300 --users 100 --prefix "ham$(date +%s)_" \
  --out /tmp/seckill-load-meta-hammer.json
python3 infra/scripts/load-test.py hammer \
  --meta /tmp/seckill-load-meta-hammer.json --concurrency 200 --duration 15

# 原始输出默认写到 /tmp/seckill-load-*.txt
```

依赖：本机可访问 `http://localhost:30080`，Python 3，`kubectl` 上下文指向 Docker Desktop。

---

## 7. 附件索引

| 文件 | 内容 |
|---|---|
| `/tmp/seckill-smoke-results.json` | 冒烟用例 JSON |
| `/tmp/seckill-load-warm-*.txt` | Warm 指标 |
| `/tmp/seckill-load-burst-*.txt` | Burst 指标 |
| `/tmp/seckill-load-hammer-*.txt` | Hammer 指标 |
| `/tmp/seckill-pods-status.txt` | Pod 快照 |
| `infra/scripts/e2e-smoke.py` | 冒烟脚本 |
| `infra/scripts/load-test.py` | 压测脚本 |
