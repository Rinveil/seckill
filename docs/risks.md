# 并发与鲁棒性（对照当前代码）

> 对照 `main` 实现。压测数据见 [test-report.md](./test-report.md)（已归档）。  
> 热路径：`POST /api/seckill/{id}` → 布隆 → Lua 预扣 → MQ/HTTP 建单 → 支付/取消/过期。

## 1. 已经做对的

| 点 | 实现 |
|---|---|
| 库存原子 | Lua：`open` / `bought>=limit` / `DECR stock + INCR bought`；失败码 `-3/-1/-2` |
| 回滚原子 | `StockRollbackHelper`：`bought>0` 才 `DECR bought + INCR stock` |
| 建单幂等 | `orderNo` 先查再插，吃 `DuplicateKey` |
| 支付 / 取消 / 过期 | 均 `WHERE status=CREATED` 条件更新；支付成功不再回滚库存 |
| 内部建单 | 网关拦截 `/api/order/internal/**`；order 若见到 `X-User-Id` 也拒绝。core 走 Service DNS |
| 伪造登录头 | 网关先剥 `X-User-*` 再注入 JWT claims |
| 关抢竞态 | `expireIfOpen` 仅当仍 OPEN；MQ 延迟 + 扫表；MQ 关时本机 `TaskScheduler` |
| 布隆误杀 | `ready` 缺失 fail-open；重建 tmp+RENAME |
| 禁用账号 | Redis `seckill:user:disabled:{id}`，网关验签后拒绝；Redis 异常 fail-open |
| 商城不漏草稿 | `listForMall` / `detailForMall` / 搜索索引均排除 DRAFT |
| 商城搜索 | ES 倒排；RestClient 1s/3s 超时后 LIKE；探针不绑 ES；不参与抢购 |
| ES 与 MySQL | 双写失败重试 1 次且不回滚活动；启动 + 120s 对账覆盖并删残留；查询滤 DRAFT |
| ES 深分页 / hydrate | `from+size≤200`；命中 ID 一次 `IN` 再补 soldCount |
| ES refresh | 单条 upsert `wait_for`；全量 reindex 不逐条 wait |
| 限流 IP | nginx 写 `X-Real-IP=$remote_addr`，不信任客户端 XFF 首段；body `code=1002` |

## 2. 热路径竞态（现状）

| 级别 | 场景 | 现状 |
|---|---|---|
| — | 经网关打内部建单 | **已修**：403 |
| — | 支付 vs 过期/取消 | **已修**：`pay` 同样 CAS |
| 中 | 预扣成功、MQ `syncSend` 失败 | 回滚库存，用户重试。符合预期 |
| 中 | 投递成功、消费者失败后再投 | 幂等不二次扣库存，可能少卖。对账能看出 |
| — | PREHEATED 改限购 | **已修**：同步写 `seckill:limit:{id}` |
| — | MQ 关且扫表关 | **已修**：建单/开抢时本机延迟关单/关抢 |
| 低 | 布隆假阳性 | 仍进 Lua，可接受 |
| 低 | 限流单机内存 | 单副本演示可接受；多副本不共享桶 |
| — | ES 双写失败 / 残留文档 | **已修**：重试 + 启动/定时对账删多余文档；查询与 hydrate 再滤 DRAFT |
| — | ES 挂了拖慢商城 | **已修**：RestClient 超时后 LIKE；探针不绑 ES |
| — | ES 深分页 / 循环查库 | **已修**：`from+size≤200`；hydrate `IN (ids)` |
| 低 | 双写两次都失败 | 最多等到下一轮 120s 对账；搜索允许短暂旧数据 |
| 低 | ES 单节点无副本 | **演示取舍**：replicas=0 + PVC + limit 1.5Gi；不加第二节点 |
| 低 | ngram 索引膨胀 | **有意后置**：生产换 IK，本仓库不打自定义 ES 镜像 |

## 3. 未做（有意后置或演示范围）

| 点 | 说明 |
|---|---|
| 真实支付 | Mock 固定成功，无资金流 |
| 克隆 API | 前端表单拷贝即可 |
| Java 单测套件 | 仍以本机 UI / `e2e-smoke.py` 为主 |
| 分布式限流 | 单副本内存令牌桶 |
| Canal / Outbox | 搜索双写+对账足够演示，不上 binlog 同步 |
| IK 分词 | 演示 ngram；生产再打带插件的 ES 镜像 |

## 4. 配置事实

| 项 | 事实 |
|---|---|
| `application.yml` 默认 | `seckill.mq/schedule/search.elasticsearch.enabled=false`（裸起 Java） |
| **当前 K8s** | mq / schedule / search.elasticsearch 均为 **true** |
| 关单 | RocketMQ 延迟（3 分钟 delayLevel=7）+ 扫表；MQ 关则本机定时 |
| JWT | Bearer + localStorage；禁用账号另写 Redis 标记 |
| 限流 | order `-110`，早于 JWT；登录/注册另桶 |
| 布隆 | Redis bitmap，无 RedisBloom 模块 |
| Broker limit | `2Gi` |
