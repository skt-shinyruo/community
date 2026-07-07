# 高并发 BBS 可靠性平台设计

## 背景

本设计面向高并发 BBS 的可靠性主线。目标不是重写现有可靠性框架，而是在已有 DDD owner 边界、HTTP 幂等、DB outbox、投影、热点缓存和观测模型之上，形成统一的可靠性治理体系。

现有约束继续成立：

- `content` 是帖子、评论、热榜候选事实的 owner。
- `social` 是点赞、关注、拉黑事实的 owner。
- `search`、`notice`、`growth` 和 hot feed 是投影、派生读模型或协作域，不能反向决定主事实。
- controller、listener、handler、bridge、enqueuer 和 job 只能进入同域 `*ApplicationService`。
- 跨域同步协作只走 foreign owner `api.query` / `api.action` / `api.model`。
- 跨域异步协作只走 owner `contracts.event`。

## 目标

第一版覆盖四条可靠性主线：

1. 设计与路线图：明确幂等、outbox、`DEAD`、重放、补偿、热点缓存和观测规则。
2. 后端可落地改造：补齐治理面、重放入口、补偿入口和测试守卫。
3. 运维治理能力：提供 backlog、`DEAD`、projection lag、重放结果、job 成败和缓存降级信号。
4. 高并发读写优化：覆盖发帖/评论、点赞/关注、帖子详情/热榜的缓存、回源、降级和一致性策略。

非目标：

- 不把 `ops` 做成新的业务事实 owner。
- 不允许治理面绕过 owner domain 直接修业务事实。
- 不承诺 HTTP 成功后所有搜索、通知、成长和热榜投影同步完成。
- 不把 best-effort 副作用伪装成可靠投影。

## 阶段划分

### P0 可靠性基线

建立统一语义：

- required 写入口必须显式接入 `IdempotencyGuard.executeRequired(...)`。
- 可靠异步副作用必须进入 DB outbox 或 owner 状态机。
- `DEAD` 是自动重试终点，不是业务终点。
- 重放只能让事件回到原 worker 路径，不能直接调用 handler 跳过状态机。
- 补偿任务必须由 owner `ApplicationService` 判断能否修复。
- 热点缓存是派生读模型，必须支持回源、降级、版本和污染清理。

### P1 治理面

新增 `ops` 可靠性治理域，提供：

- outbox backlog 查询。
- `DEAD` 列表和详情。
- 单条 outbox 重放。
- 有范围和 limit 的批量 outbox 重放。
- projection 健康和 lag 查询。
- 补偿 job 触发入口。
- 热点缓存状态、预热和降级信号查询。

### P2 核心业务切片

用三条业务切片验证治理体系：

- 发帖/评论：幂等写入、content 事实、search/notice/growth 投影、`DEAD` 重放。
- 点赞/关注：social 事实、通知/成长/热度更新、重复和乱序保护。
- 帖子详情/热榜：热点缓存、summary cache、counter overlay、rank version、回源和降级。

### P3 高并发优化

增强热点路径：

- 热 key 预热。
- TTL jitter。
- single-flight 回源保护。
- 缓存 payload poison cleanup。
- 热榜 rank version 切换。
- 计数一致性修复。
- 压测和容量验收。

## 架构组件

### `ops.controller`

管理员入口，只负责鉴权、参数绑定、DTO 转换和调用同域 `ops.application`。

典型接口：

- 查询 outbox backlog。
- 查询 `DEAD` 事件。
- 重放单条 outbox。
- 批量重放 outbox。
- 查询 projection lag。
- 触发补偿 job。
- 查询或预热热点缓存。

### `ops.application`

治理用例入口：

- `OutboxGovernanceApplicationService`
- `ProjectionGovernanceApplicationService`
- `ReliabilityPolicyApplicationService`
- `HotCacheGovernanceApplicationService`

职责：

- 编排治理查询。
- 做重放决策。
- 调用技术端口修改 outbox 状态。
- 触发 owner 暴露的补偿入口。
- 记录审计日志和治理指标。

### `ops.domain`

只表达治理语义：

- `OutboxReplayRequest`
- `ReplayDecision`
- `ReliabilityPolicy`
- `ProjectionLag`
- `DeadEventDisposition`

`ops.domain` 不表达帖子、评论、点赞、通知、成长任务等业务事实。

### `ops.infrastructure`

实现技术访问：

- outbox 查询和状态变更。
- projection lag 查询。
- Redis 热点缓存状态查询。
- 幂等覆盖清单查询。
- Micrometer 指标和审计日志。

MyBatis、JDBC、Redis 和 mapper/dataobject 细节只能位于 infrastructure。

### `common-outbox`

保持通用 outbox runtime，同时补充治理所需能力：

- 按 status、topic、eventId、时间范围查询。
- 安全地将 `DEAD` 改回 `PENDING`。
- 安全地恢复过期 `PROCESSING` lease。
- 提供 backlog 和 dead count 的指标来源。

## 核心数据流

### 写接口幂等流

```text
Client
  -> Controller 读取 Idempotency-Key
  -> same-domain ApplicationService
  -> IdempotencyGuard.executeRequired(operation, actor, key, fingerprint)
  -> owner domain rule
  -> owner repository
  -> domain / contract event
  -> transaction commit
```

必须覆盖：

- 发帖。
- 评论。
- 市场和钱包写入。
- 其他产生不可重复副作用的写入口。

建议评估覆盖：

- 点赞。
- 关注。
- 收藏。

高频社交写入口如果不使用 required HTTP 幂等，也必须有明确的语义去重、防抖或状态覆盖规则。

### 异步投影流

```text
owner ApplicationService
  -> owner domain event / contract event
  -> BEFORE_COMMIT outbox enqueuer
  -> outbox_event(PENDING)
  -> OutboxWorker
  -> handler
  -> projection ApplicationService
  -> projection store / Kafka / ES / notice table / growth log
```

规则：

- search 投影必须回源 content 当前状态，避免旧事件复活已删除内容。
- notice 使用 `sourceEventId` 去重。
- growth 使用 `sourceEventId`、task key 和 period key 去重。
- hot feed 使用 rank version 和 source version，缓存只作为派生读模型。

### `DEAD` 重放流

```text
Admin
  -> ops.controller
  -> OutboxGovernanceApplicationService
  -> 查询 outbox_event(DEAD)
  -> ReplayDecision
  -> mark DEAD -> PENDING, reset next_retry_at
  -> OutboxWorker 原路径重新处理
  -> 审计日志 + 指标
```

重放前置条件：

- topic 仍有 handler。
- payload 可解析。
- handler 具备幂等语义。
- 重放原因明确。
- 批量重放必须有 topic、status、时间范围和数量上限。

坏 payload、缺 handler、schema 不兼容和业务规则不确定的事件不能自动重放，必须进入人工处置。

### 补偿任务流

```text
Admin / Scheduler
  -> ops 或 owner job 入口
  -> owner ApplicationService
  -> 扫描异常状态
  -> owner domain 判断是否可补偿
  -> 写回 owner 状态 / 重新发出 outbox / 触发缓存重建
```

典型补偿：

- outbox `PROCESSING` lease 恢复。
- search 索引缺失或脏数据按 content 当前事实重建。
- notice/growth 漏投按 source event 或 owner 当前事实补齐。
- hot feed 缺页或 rank version 异常按 content 当前事实重建。
- 长时间悬挂的幂等 `PROCESSING` 按明确策略释放或保持待确认。

### 热点读缓存流

```text
Client
  -> feed/detail API
  -> ApplicationService
  -> Redis hot cache / summary cache / counter cache
  -> miss: owner repository / owner API 回源
  -> 回填缓存
  -> 返回 items + rankVersion + cacheState
```

降级规则：

- Redis 读失败：限流回源，返回 degraded 信号。
- Redis 写失败：不影响主读响应，记录指标。
- 缓存 payload 损坏：删除 poison key，回源重建。
- 热榜为空：允许 latest fallback，记录 fallback 指标。
- 热点回源必须有 page size 上限和 single-flight。

## 错误处理语义

### Fail-closed

这些失败必须拒绝当前请求：

- 鉴权、授权和强风控失败。
- required `Idempotency-Key` 缺失或非法。
- required 幂等入口无法访问幂等存储。
- owner 主事实写入失败。
- 关键同步 owner API 不可用，且当前用例不能安全延后。
- 可靠 outbox 已启用但事务内无法写入 outbox row。

建议返回：

- 参数和幂等 key 问题：`400`
- 并发或 replay conflict：`409`
- 依赖不可用：`503`
- 权限问题：`401` / `403`

### 异步重试

这些失败不阻断主事实，但必须可靠追平：

- search projection 写 ES 失败。
- content、social、user contract event 发布 Kafka 失败。
- IM policy projection 发布失败。
- growth、reward 可靠投影失败。
- hot feed 可靠投影失败。

handler 失败后回到 `PENDING`，超过最大重试次数进入 `DEAD`。

### Best-effort

这些失败可以记录后继续：

- 非关键通知投影。
- analytics 采集。
- 非关键缓存写入。
- 低优先级运营埋点。

best-effort 必须有日志或指标，不能承载强一致用户承诺。

### 人工处置

这些情况不能自动重放：

- payload schema 已不兼容且无法自动迁移。
- topic 没有 handler。
- handler 不具备幂等语义。
- 业务规则已经变化，旧事件重放可能产生错误副作用。
- 投影数据与 owner 当前事实冲突且无法自动判断。
- 幂等记录异常但无法证明业务是否成功。

处置状态：

- `REPLAYED`
- `IGNORED`
- `FIXED_BY_REBUILD`
- `MANUAL_REPAIR_REQUIRED`

每次处置必须记录 actor、reason、eventId、topic、before/after status 和 traceId。

## 一致性承诺

- 发帖/评论 HTTP 成功表示 content 主事实已提交，不表示 search、notice、growth 或 hot feed 已追平。
- 点赞/关注 HTTP 成功表示 social 主事实已提交，不表示通知、成长任务或热度已追平。
- 搜索结果最终一致。
- 通知允许延迟；best-effort 路径可能丢失，可靠路径必须可追踪。
- 热榜是派生读模型，允许短时间延迟和 rank version 切换。
- 计数允许短时间读到缓存值；详情页可叠加 owner 当前 counter overlay 修正。

## 测试与验收

### 架构守卫

新增或扩展 ArchUnit：

- `ops.controller` 只能调用 `ops.application`。
- `ops.application` 不能直接依赖业务域 mapper/dataobject。
- `ops.domain` 不能依赖 Spring、MyBatis、Redis 或 HTTP DTO。
- 业务 inbound adapter 仍只能进入同域 `*ApplicationService`。
- outbox handler 和 listener 不能跨域读取 producer domain、repository、mapper 或 dataobject。

运行：

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

### 幂等测试

每个 required 写接口覆盖：

- 缺少 `Idempotency-Key` 返回 `400`。
- 首次请求成功并保存 `SUCCESS`。
- 同 key 成功重试返回缓存响应。
- 同 key 并发返回 `409`。
- 同 key 不同 fingerprint 返回 replay conflict。
- 幂等存储异常返回 `503`。
- 业务异常释放 `PROCESSING`，允许重试。

### Outbox 和重放测试

通用 outbox 覆盖：

- `PENDING -> PROCESSING -> SUCCEEDED`
- handler 失败回到 `PENDING` 并设置 retry 信息。
- 超过重试进入 `DEAD`。
- lease 过期恢复。
- `DEAD` 重放回到 `PENDING` 并由 worker 原路径执行。
- topic 无 handler 时拒绝自动重放或进入 degraded 可观测状态。
- 批量重放必须有范围和 limit。

业务投影覆盖：

- search 重放旧事件不会复活已删除帖子。
- notice 重放同 `sourceEventId` 不重复生成通知。
- growth 重放同 `sourceEventId` 不重复加进度。
- hot feed 重放乱序事件不回退 rank/source version。

### 缓存降级测试

- Redis miss 回源并回填。
- Redis read failure 回源并记录 degraded 指标。
- Redis write failure 不影响主读响应。
- poison payload 被删除并回源重建。
- 热榜为空触发 latest fallback。
- single-flight 避免同一热点 key 并发击穿。
- rank version 切换后旧游标不会导致异常或错误混页。

### 治理接口测试

- 管理员可查询 backlog 和 `DEAD`。
- 非管理员拒绝访问。
- 单条重放记录审计日志。
- 批量重放超过 limit 被拒绝。
- 坏 payload 重放被拒绝并保留 `DEAD`。
- 补偿 job 重跑幂等。

### 观测验收

必须能看到这些信号：

- `community_outbox_replay_total{topic,result}`
- `community_http_idempotency_total{op,outcome}`
- `GET /api/ops/outbox/backlog`
- `GET /api/ops/projections/lag`
- `community_cache_requests_total{cache,result,scope}`
- `community_job_runs_total{job.name,result}`

### 压测验收

- 发帖/评论重复提交不产生重复事实。
- 点赞/关注高并发不产生错误计数或重复通知。
- 热榜/详情缓存击穿时 DB QPS 被限制在预期范围内。
- outbox backlog 在依赖恢复后能自动下降。
- search/notice/growth 最终追平，无长期未处置 `DEAD`。
- Redis、ES、Kafka 短暂故障时主事实写入策略符合 fail-open/fail-closed 约定。

## 待实施计划入口

本 spec 通过评审后，下一步应编写实施计划，建议按以下顺序拆任务：

1. `common-outbox` 治理查询和重放能力。
2. `ops` 可靠性治理域和管理员接口。
3. outbox、projection、cache 指标。
4. content/search/notice/growth 切片验证。
5. social/notice/growth/hot feed 切片验证。
6. 热点缓存降级、single-flight 和压测验收。

## Implementation Status

第一波实现已经覆盖：

- outbox governance query 和 replay primitives
- `/api/ops/outbox/**` 管理治理 API
- projection lag visibility
- hot-feed cache reliability metrics
- search、notice、growth 和 hot feed 的 replay regression tests
