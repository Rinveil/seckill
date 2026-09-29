# Elasticsearch 与商城搜索

> 本文件 = **落地设计** + **面试口径**。实现以代码为准，未做的不要往外说。  
> 热路径（Lua 预扣 / 抢购）**不走 ES**。ES 只服务公开商城浏览搜索。

---

## 1. 本项目怎么用（先能讲清楚现状）

| 项 | 选择 |
|---|---|
| 中间件 | Elasticsearch **8.x 单节点**（K8s `elasticsearch`，PVC，arm64） |
| 职责 | 活动标题检索、状态过滤、分页、高亮 |
| 真相源 | **MySQL `t_activity`**；ES 是衍生索引 |
| 可见范围 | 只索引 PREHEATED / OPEN / CLOSED；**DRAFT 不进索引** |
| 写入 | 活动写库后双写（失败重试 1 次，**不回滚活动**）；启动 + 定时对账覆盖索引 |
| 查询 | `GET /api/mall/search?q=&status=&page=&size=` |
| 降级 | ES 超时/异常 → MySQL `LIKE`（RestClient 连接 1s / 读 3s） |
| 中文分词 | 演示用 **ngram(1–2)**，不装 IK。生产应上 IK / ICU |
| 开关 | `SECKILL_SEARCH_ES_ENABLED`（K8s true；裸起 Java 默认 false） |
| 不做 | 搜索用户/订单、建议词、聚合看板、Canal、ES 参与扣库存、IK 自定义镜像 |

资源（本机 Docker 8GB）：堆 **512MB**，Pod limit **1536Mi**。`node.store.allow_mmap=false`，避免 Desktop K8s 的 `vm.max_map_count` 坑。

---

## 2. 面试高频：为什么要 ES，而不是 MySQL `LIKE`

| | `LIKE '%石%'` | Elasticsearch |
|---|---|---|
| 结构 | B+Tree，前缀友好，中缀要扫行 | **倒排索引**（term → posting list） |
| 中文 | 无分词，整列匹配 | 分词后按 term 查 |
| 相关性 | 没有 | TF-IDF / BM25 |
| 高亮 / 拼音 / 纠错 | 自己做 | 内置或插件 |
| 写放大 | 无 | 要维护索引 |

口述：商品标题检索是读多写少、要中缀/相关度时，用 ES；**下单库存仍以 Redis+MySQL 为准**，搜索挂了可以降级，抢购不能挂在 ES 上。

---

## 3. 倒排索引（必问）

正排：docId → 字段内容。  
倒排：term → `[docId, 词频, 位置…]`。

查询「石头」≈ 取 term 的 posting list 做交/并，再按 BM25 打分。比「每行 `title LIKE`」便宜得多。

本项目 mapping 要点：`title` 用 ngram 分析器；`status` 用 `keyword` 做 filter（不要分词）。

---

## 4. 分词：IK vs 本仓库 ngram

生产中文常用：

- `ik_smart`：粗切，召回少、更准  
- `ik_max_word`：细切，召回多  

本机不打自定义 ES 镜像，所以用 **ngram 1–2 字**，对短标题够用，索引会膨胀。面试可以说：演示用 ngram；SKU 多了上 IK，`search_analyzer` 与 `analyzer` 要成对设计。

`standard` 对英文空格分词好，对中文整句往往切不好，所以标题检索不要只靠 standard。

---

## 5. Mapping、动态映射、text vs keyword

- `text`：要分词、要搜  
- `keyword`：精确过滤、排序、聚合（本项目的 `status`）  
- 动态 mapping 容易把数字映射错；**商城索引启动时显式 create**，不靠动态猜

---

## 6. 近实时（NRT）与 refresh

Lucene 先写 buffer，`refresh`（默认 1s）后才能被搜到。不是 MySQL 那种提交即可见。

本项目：运营单条 upsert/delete 用 `refresh=wait_for`，改完立刻能搜到。启动/定时全量重建用 `refresh=false`，全部写完再 `indices.refresh` 一次，避免 N 条活动刷 N 次。高写量生产也不要每条 wait。

---

## 7. 一致性：MySQL 与 ES 怎么对齐

常见三种，本项目用第一种，并加了重试和对账：

| 方案 | 做法 | 取舍 |
|---|---|---|
| **应用双写**（本项目） | 活动写库成功后再 index/delete；失败再试 1 次 | 实现简单；仍 **不回滚活动** |
| 事务消息 / Outbox | 同一本地事务写 outbox 再消费 | 更可靠，代码重 |
| Canal / Debezium | 听 MySQL binlog | 解耦好，运维多 |

本仓库兜底（对照代码）：

1. 双写失败只打日志，活动事务已提交  
2. 启动 `MallIndexSyncRunner` 全量覆盖  
3. `seckill.schedule.enabled=true` 时 `MallIndexReconcileJob` 每 120s 再覆盖，并删掉 ES 里已不该存在的文档（DRAFT / 已删）  
4. 查询侧 `must_not status=DRAFT`，hydrate 再用 MySQL 丢掉草稿/已删行  

口述：搜索允许短暂不一致；库存不允许。所以搜索双写失败 **不能回滚活动**。

---

## 8. 和 Redis 怎么分工（必问）

| | Redis | Elasticsearch | MySQL |
|---|---|---|---|
| 本项目 | 库存预扣、限购、布隆、禁用名单 | 商城标题搜索 | 活动/订单/用户真相 |
| 擅长 | 极低延迟 KV、原子计数 | 全文、相关度 | 事务、关系 |

不要用 Redis 做全文（SCAN + 包含），不要用 ES 做秒杀库存。已抢件数不进 ES，卡片上的 soldCount 在 hydrate 时用 Redis `init - stock` 算。

---

## 9. 分页

`from + size` 要在每个分片上取 `from+size` 再合并，**深分页**成本高。本项目 `size≤20` 且 `from+size≤200`。  
更深用 `search_after` / scroll（scroll 不适合实时分页）。

---

## 10. 故障与降级（对照代码）

| 风险 | 落地 |
|---|---|
| 双写失败留下旧标题 | 重试 1 次 + 启动/120s 对账覆盖；不回滚活动 |
| ES 里残留 DRAFT/已删 | 对账按 MySQL 可见 ID 删多余文档；查询 `must_not DRAFT`；hydrate 再滤一层 |
| 全量重建每条 wait_for | 单条运营写才 wait_for；reindex 批量 false + 一次 refresh |
| ES 挂了拖死商城 | RestClient 连接 1s / 读 3s；异常走 MySQL LIKE；activity 探针不绑 ES |
| 深分页打爆协调节点 | `from+size≤200` 直接拒 |
| 循环查库补卡片 | hydrate 一次 `IN (ids)`，禁止逐条 `selectById` |
| 单节点无副本 | **演示取舍**：replicas=0 + PVC；Pod 没了等拉起，搜索降级 LIKE，抢购不受影响。Docker 8GB 不加第二节点 |
| ngram 索引膨胀 | **有意后置**：短标题演示够用；生产换 IK，不在本仓库打自定义 ES 镜像 |

抢购链路零依赖 ES。

---

## 11. 60 秒口述稿

> 商城搜索用 ES 倒排做标题检索和高亮，MySQL 仍是活动主库。写路径是应用双写，失败重试一次且不回滚活动，靠启动和定时对账对齐。DRAFT 不进索引。读路径 search API，超时 3 秒降级 LIKE。抢购走 Redis Lua，不经过 ES。单条写 wait_for，全量重建集中 refresh。演示分词用 ngram，生产会换成 IK。深分页我们直接禁掉。

---

## 12. 代码入口

| 文件 | 作用 |
|---|---|
| `infra/k8s/13-elasticsearch.yaml` | 单节点 ES |
| `ElasticsearchConfig` | RestClient 超时 |
| `MallActivityIndex` | 建索引 / upsert / delete / search / 全量覆盖+删残留 |
| `MallIndexReconciler` | 从 MySQL 拉可见活动再 reindex |
| `MallIndexSyncRunner` | 启动对账 |
| `MallIndexReconcileJob` | `schedule` 开时每 120s 对账 |
| `ActivityService.searchForMall` | 编排 + 降级 + IN 查询补 soldCount |
| `GET /api/mall/search` | 公开接口（须写在 `/{id}` 旁边，避免 path 冲突） |
| `Mall.vue` | 防抖、服务端分页、高亮 |
