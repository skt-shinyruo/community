# 定时任务、补偿与重试链路实现说明

这篇文档讲的不是某一个业务域，而是整个项目里“后台如何兜底”的机制，包括：

- Spring 本地 `@Scheduled`
- XXL-Job 任务处理器
- outbox worker
- Redis single-flight
- 本地重试队列

如果把这些链路漏掉，你会以为系统只有“接口请求 -> service -> DB”这一条主路；但这个项目里很多最终一致性、清理、追平和自动动作，都靠这些后台链路完成。

## 1. 先给一张总图

当前项目里的后台兜底机制主要分成四类：

### 1.1 本地 fixed-delay 定时任务

代表类：

- `RefreshTokenCleanupJob`
- `PendingRegistrationUserCleanupJob`
- `PostScoreRefresher`
- `OutboxWorkerScheduler`

特点：

- 跟着 `community-app` 进程跑
- 不依赖外部任务平台也能工作
- 适合轻量清理、轮询、追平

### 1.2 XXL-Job 任务处理器

代表类：

- `PendingRegistrationUserCleanupHandler`
- `SearchReindexHandler`
- `MarketOrderAutoConfirmHandler`

特点：

- 由外部调度平台触发
- 更适合跨实例统一调度的高成本任务

### 1.3 单飞锁保护

代表类：

- `SingleFlightTaskGuard`
- `ReindexJobService`

特点：

- 多实例部署下避免同一个高风险任务并发执行

### 1.4 异步补偿与重试

代表类：

- `OutboxWorker`
- `OutboxWorkerScheduler`
- `RedisPostScoreQueue`

特点：

- 主请求里先落事实
- 后台慢慢补齐异步投影或重算

## 2. 本地 `@Scheduled` 任务

## 2.1 refresh token 过期清理

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenCleanupJob.java`

配置类：

- `backend/community-app/src/main/java/com/nowcoder/community/auth/config/RefreshTokenCleanupProperties.java`

### 2.1.1 触发频率

默认 fixed delay：

- `auth.refresh.cleanup.interval-ms = 3600000`

也就是默认每小时跑一次。

### 2.1.2 开关

默认：

- `auth.refresh.cleanup.enabled = true`

### 2.1.3 做什么

任务会调用：

- `UserRefreshTokenSessionActionApi.deleteExpiredBefore(Instant.now())`

底层实现是：

- `RefreshTokenSessionService.deleteExpiredBefore(...)`

也就是删除所有已过期 refresh token 会话。

### 2.1.4 失败怎么处理

失败不会中断进程，只会：

- `log.warn("[auth] refresh-token cleanup failed: ...")`

这是典型的 best-effort 清理任务。

### 2.1.5 为什么可以重复跑

这类任务本质是“按过期时间删历史垃圾数据”：

- 已删掉的行不会再删出副作用
- 没删掉的下次还能继续删

所以它天然适合 fixed-delay 重复执行。

## 2.2 未激活注册用户清理

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJob.java`

配置类：

- `backend/community-app/src/main/java/com/nowcoder/community/auth/config/RegistrationProperties.java`

### 2.2.1 触发频率

默认 fixed delay：

- `auth.registration.pending-user.cleanup-interval-ms = 300000`

也就是默认每 5 分钟跑一次。

### 2.2.2 开关

当前本地兜底开关默认：

- `auth.registration.pending-user.local-scheduler-enabled = true`

### 2.2.3 做什么

任务会：

1. 读取 pending user TTL
2. 把 TTL 下限钳到至少 60 秒
3. 调 `UserRegistrationActionApi.cleanupExpiredPendingUsers(ttl)`

底层实现是：

- `UserRegistrationService.cleanupExpiredPendingUsers(...)`

它会删除：

- `status = 0`
- 且创建时间早于 cutoff

的未激活注册用户。

### 2.2.4 为什么也能重复跑

这同样是基于条件删除：

- 过期且未激活的删掉
- 已激活的不删
- 已删掉的下次自然不会再命中

因此它也是天然幂等型清理任务。

### 2.2.5 失败怎么处理

失败同样只记 warning，不让主进程失败。

## 2.3 帖子热度刷新器

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`

队列实现：

- `backend/community-app/src/main/java/com/nowcoder/community/content/score/RedisPostScoreQueue.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreQueue.java`

### 2.3.1 触发频率

默认 fixed delay：

- `content.score.refresh.delay-ms = 30000`

也就是每 30 秒拉一批。

### 2.3.2 批量大小

默认：

- `content.score.refresh.batch-size = 200`

代码还会把它钳到：

- 最小 `1`
- 最大 `2000`

### 2.3.3 正常路径

每次批量循环里：

1. `scoreQueue.pop()` 取一个待刷新帖子
2. `postService.getById(postId)` 读取帖子
3. 查点赞数与评论数
4. 按热度公式重算 score
5. `scoreCommandService.updateScore(postId, score)`
6. `scoreQueue.onSuccess(postId)` 清掉重试计数

### 2.3.4 失败路径

如果帖子不存在：

- 记为 `drop_not_found`
- 不再重试

如果是其他业务异常或运行时异常：

- 调 `scoreQueue.reenqueue(postId)`
- 重新入队
- 打 warning

### 2.3.5 Redis 重试队列怎么做退避

`RedisPostScoreQueue` 不是简单把失败项立即塞回去，而是：

- 用 ZSET `post:score:z` 存 due time
- 用 HASH `post:score:retry` 记录重试次数
- HASH TTL 为 3 天

每次失败重入会按指数退避计算下一次执行时间：

- 基础延迟 `5s`
- 最大 `10min`
- 外加少量 jitter

这能避免单个坏帖子把 worker 打成紧循环。

### 2.3.6 兼容升级

`RedisPostScoreQueue.pop()` 还保留了：

- `post:score` 旧 SET 队列兜底读取

它的意义是：

- 升级后不丢旧队列里的历史任务

这也是一种典型的灰度兼容补偿。

## 2.4 outbox 轮询 worker

核心类在共享模块：

- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`

配置类：

- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxProperties.java`

### 2.4.1 默认是否启用

默认：

- `events.outbox.enabled = false`

只有显式打开时，下面这些组件才会生效：

- BEFORE_COMMIT enqueuer
- topic handler
- `OutboxWorkerScheduler`

### 2.4.2 触发频率

默认 fixed delay：

- `events.outbox.worker-fixed-delay-ms = 1000`

也就是每秒轮询一次。

### 2.4.3 存储模型

outbox 主表状态有四种：

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `DEAD`

### 2.4.4 正常路径

worker 每次 `pollOnce()` 会：

1. 先做一次过期 lease 恢复
2. 查找到期的 `PENDING` 事件
3. 抢占处理权，把状态改成 `PROCESSING`
4. 按 topic 找 handler
5. handler 成功后标记 `SUCCEEDED`

### 2.4.5 失败路径

如果没有 handler：

- 10 秒后重试

如果 handler 抛异常：

- `retry_count + 1`
- 按指数退避算 `next_retry_at`
- 重新回到 `PENDING`

默认退避配置：

- `baseBackoff = 5s`
- `maxBackoff = 10min`
- `maxRetries = 50`

超过最大重试次数后，事件会进入：

- `DEAD`

### 2.4.6 为什么说它是补偿链路

因为 BEFORE_COMMIT enqueuer 会在主事务里先写一条 outbox 行，之后真正的：

- 通知投影
- 积分投影
- 搜索投影
- growth 任务投影

都可以在后台重放。

这让主请求具备两个特性：

- 异步投影失败不会立刻打爆 HTTP 路径
- 后台 worker 可以不断补偿直到成功或死信

## 3. 业务 outbox 在哪些地方被接上

当前几个典型 enqueuer / handler 是：

- `PointsOutboxEnqueuer` / `PointsOutboxHandler`
- `NoticeOutboxEnqueuer` / `NoticeOutboxHandler`
- `PostOutboxEnqueuer` / `PostOutboxHandler`
- `TaskProgressOutboxEnqueuer` / `TaskProgressOutboxHandler`

它们的共同模式都是：

1. `@TransactionalEventListener(BEFORE_COMMIT)` 写 outbox
2. worker 异步取出
3. topic handler 反序列化 payload
4. 调对应 projection service

也就是说，outbox 是这几个业务投影链路共同的后台补偿底座。

## 4. XXL-Job 任务

## 4.1 基础设施如何挂载

自动配置类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobAutoConfiguration.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobProperties.java`

只有当：

- classpath 有 `XxlJobSpringExecutor`
- `xxl.job.enabled = true`

时才会创建 executor。

并且要求这些配置非空：

- `xxl.job.admin.addresses`
- `xxl.job.admin.accessToken`
- `xxl.job.executor.appname`

### 4.1.1 这说明什么

XXL-Job 在当前项目不是硬依赖，而是可选调度平面。

本地 scheduler 可以独立工作；接入 XXL-Job 后，再把更适合集中调度的任务交给平台。

## 4.2 pending registration 清理 handler

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/PendingRegistrationUserCleanupHandler.java`

它与本地 `PendingRegistrationUserCleanupJob` 调的是同一条业务 action：

- `cleanupExpiredPendingUsers(ttl)`

区别只在触发方式：

- 一个靠本地 fixed-delay
- 一个靠 XXL-Job

因此这条链路本质上是“双通道兜底”。

## 4.3 search reindex handler

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/SearchReindexHandler.java`

它调用：

- `SearchReindexActionApi.reindex()`

如果 reindex 已在运行中：

- 返回 skipped
- XXL 侧记成功日志，但不会重复启动第二个任务

真正的并发保护不在 handler，而在下面的 single-flight。

## 4.4 market auto confirm handler

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandler.java`

它调用：

- `MarketOrderAutoConfirmActionApi.autoConfirmDueOrders()`

返回：

- 完成数
- 跳过数

这个 handler 只是“调度入口”，真正业务状态机仍在 market 域里。

## 5. single-flight 与 reindex 防重

## 5.1 通用分布式单飞锁

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/scheduler/SingleFlightTaskGuard.java`

自动配置：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/scheduler/autoconfig/SchedulerInfraAutoConfiguration.java`

只要有 `StringRedisTemplate`，就会自动注入这个 guard。

### 5.1.1 锁实现

它使用：

- `SET NX + TTL` 获取锁
- compare-and-del Lua 脚本释放锁
- compare-and-pexpire Lua 脚本续租

因此它是一个典型的 Redis 分布式租约锁。

### 5.1.2 失败策略

如果 Redis 不可用或获取失败：

- 记录 warning
- 返回 `null`

具体是 fail-open 还是 fail-closed，由上层任务自己决定。

## 5.2 reindex 怎么用 single-flight

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`

### 5.2.1 启动阶段

`ReindexJobService.tryStart()` 会尝试抢：

- `sf:task:search:reindex`

默认 TTL：

- `search.reindex.lock-ttl = 30m`

抢不到就返回：

- `acquired = false`

### 5.2.2 长任务续租

如果抢到锁，`startRenewal(job)` 会：

- 先立即续租一次
- 再起一个后台线程
- 以 `TTL / 3` 的频率续租

这样长时间 reindex 不会因为锁自然过期而被第二个实例插队。

### 5.2.3 结束阶段

无论成功失败，`finally` 都会：

- `reindexJobService.finish(job)`

释放锁。

### 5.2.4 为什么这条链路重要

因为 reindex 同时可以由：

- `OpsController` 手动触发
- `SearchReindexHandler` 定时触发

如果没有 single-flight，多入口会把整个索引重建打并发。

## 6. 这些后台链路对主业务的真正作用

## 6.1 清理类任务

代表：

- refresh token cleanup
- pending user cleanup

作用：

- 控制垃圾数据
- 降低认证和注册链路的历史负担

## 6.2 追平类任务

代表：

- outbox worker
- 帖子热度刷新器

作用：

- 把主事务后的异步副作用慢慢追平
- 把失败项延迟重试，而不是在请求线程里硬扛

## 6.3 自动业务动作

代表：

- market auto confirm
- search reindex

作用：

- 让业务状态机能自动收敛
- 让高成本维护动作可以平台化运行

## 7. 初学者最容易忽略的几个点

### 7.1 不是所有核心逻辑都在 controller 里

这个项目里很多真正决定最终状态的逻辑都在：

- scheduler
- outbox worker
- job handler
- retry queue

### 7.2 “最终一致”不是一句口号，而是有明确后台执行器

这里的最终一致，至少靠了三套具体机制：

- outbox worker
- post score retry queue
- xxl-job / local scheduler

### 7.3 同一个能力可能有两套触发器

比如：

- pending user cleanup 既有本地 scheduler，也有 XXL-Job handler
- reindex 既能从 ops API 触发，也能从 XXL-Job 触发

所以读代码时必须把“业务动作”和“调度入口”分开看。

## 8. 与其他业务文档的关系

如果你想继续沿主业务往下读，建议配合这些文档：

- `search-projection-reindex-flow.md`
- `market-order-dispute-flow.md`
- `social-like-follow-outbox-flow.md`
- `growth-task-grant-level-flow.md`
- `content-post-comment-bookmark-subscription-flow.md`

这篇文档的目标不是重复它们的业务语义，而是把后台补偿与调度平面单独拎出来。

## 9. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenCleanupJob.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJob.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/score/RedisPostScoreQueue.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/PendingRegistrationUserCleanupHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/SearchReindexHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobAutoConfiguration.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/scheduler/SingleFlightTaskGuard.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java`
