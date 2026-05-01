# 共享 Outbox 投递保证链路实现说明

这篇文档只讲项目里的共享 outbox 底座，不讲某一个业务域本身的业务语义。

如果你关心：

- 搜索投影为什么可以重试
- IM policy 变化为什么可以可靠投递到 realtime worker
- 某个副作用为什么会反复执行直到死信

你真正要看的不是具体业务 service，而是这套共享 outbox。

## 1. 先给结论

当前项目的 outbox 是一个：

- JDBC 持久化
- pull-based worker
- at-least-once 投递
- topic handler 分发
- 带 lease 恢复、指数退避、dead letter

的共享异步投递底座。

它的职责不是让主写请求“变成异步”，而是：

- 让主写请求先把主事实提交成功
- 再把必须最终达成的投影副作用可靠地补齐

## 2. 代码分布在哪里

### 2.1 共享底座在 common-outbox

核心类位于：

- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/`

关键组件：

- `JdbcOutboxEventStore`
- `OutboxEvent`
- `OutboxEventStatus`
- `OutboxHandler`
- `OutboxWorker`
- `OutboxWorkerScheduler`
- `OutboxProperties`
- `OutboxAutoConfiguration`

### 2.2 业务接线在 community-app

当前接到共享 outbox 的业务 enqueuer / handler 主要有：

- `search.infrastructure.event.PostOutboxEnqueuer` / `PostOutboxHandler`
- `im.projection.ImPolicyOutboxEnqueuer` / `ImPolicyKafkaOutboxHandler`

也就是说：

- 底座是共享模块
- 具体 topic 的生产与消费逻辑在 app 模块

## 3. 当前默认是否启用

### 3.1 框架默认值

`OutboxProperties` 默认：

- `enabled = false`

这表示共享底座本身默认不开启。

### 3.2 当前 community-app 实际配置

`backend/community-app/src/main/resources/application.yml` 当前配置为：

- `events.outbox.enabled: true`

并且还显式配置了：

- `batch-size: 50`
- `processing-lease: 30s`
- `max-retries: 50`
- `base-backoff: 5s`
- `max-backoff: 10m`
- `worker-fixed-delay-ms: 1000`

所以当前主站默认运行在：

- outbox 已开启

的模式下。

## 4. 自动装配是怎么工作的

核心类：

- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/autoconfig/OutboxAutoConfiguration.java`

### 4.1 Store bean

只要 classpath 里有 `JdbcTemplate`，就会创建：

- `JdbcOutboxEventStore`

如果 `JdbcTemplate` 不可用，会直接抛：

- `events.outbox requires JdbcTemplate`

### 4.2 Worker 与 Clock

只有当：

- `events.outbox.enabled = true`

时，才会创建：

- `Clock`
- `OutboxWorkerScheduler`

也就是说：

- 底层 store 可以存在
- 但只有打开开关，scheduler 才会真的开始轮询

## 5. Outbox 表的状态机

状态定义在：

- `OutboxEventStatus`

当前有四种状态：

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `DEAD`

它们的含义分别是：

- `PENDING`：待处理或待重试
- `PROCESSING`：某个 worker 已抢到 lease，正在处理
- `SUCCEEDED`：处理成功
- `DEAD`：超过最大重试次数，放弃自动重试

## 6. 生产端是怎么写入 outbox 的

## 6.1 统一存储入口

所有业务 enqueuer 最终都调用：

- `JdbcOutboxEventStore.enqueue(eventId, topic, eventKey, payload)`

### 6.1.1 enqueue 做了什么

它会：

1. 校验 `eventId/topic/eventKey` 非空
2. 生成一条 UUIDv7 主键
3. 插入 `outbox_event`
4. 初始状态写成 `PENDING`
5. `retry_count = 0`
6. `next_retry_at = null`

### 6.1.2 幂等表现

如果插入时遇到 `DuplicateKeyException`：

- `enqueue(...)` 返回 `false`

这意味着生产端会把重复 enqueue 当成 no-op。

所以 outbox 在“生产端入库”这一层已经有一次去重。

## 6.2 为什么 enqueuer 都是 `BEFORE_COMMIT`

当前需要与源事务同提交的业务 enqueuer 使用：

- `@TransactionalEventListener(phase = BEFORE_COMMIT)`

例如：

- `PostOutboxEnqueuer`
- `ImPolicyOutboxEnqueuer`

这非常关键，因为它保证了：

- outbox 行和主事务写入在同一个 DB 提交边界内

如果主事务回滚：

- outbox 行也不会留下

如果主事务提交：

- outbox 行一定已经落库

这就是 outbox 方案的核心价值。

## 7. Worker 是怎么轮询和分发的

## 7.1 定时入口

`OutboxWorkerScheduler` 每隔：

- `worker-fixed-delay-ms`

调一次 `worker.pollOnce()`。

当前 app 配置默认是：

- `1000ms`

### 7.1.1 不是“推送”，而是“拉取”

这套 outbox 不是消息队列 broker 主动推送，而是：

- app 进程自己定时扫库

因此它本质是：

- pull-based worker

## 7.2 pollOnce 的第一步：恢复过期 lease

`OutboxWorker.pollOnce()` 一上来会先调用：

- `store.recoverExpiredLeases(now)`

也就是把那些：

- 状态还是 `PROCESSING`
- 但 `next_retry_at` 已经过期

的事件重新改回：

- `PENDING`

这一步是 crash recovery 的关键。

它表达的是：

- 某个 worker 曾经抢到事件
- 但在处理过程中崩了，或者 lease 没续上
- 这条事件不能永远卡死在 `PROCESSING`

### 7.2.1 一个当前实现细节

`OutboxProperties` 里有：

- `recoverLimit`

但当前 `OutboxWorker` 调用的是：

- `recoverExpiredLeases(now)`

没有传 limit。

也就是说：

- `recoverLimit` 这个配置当前存在
- 但在当前实现里并没有真正参与恢复逻辑

这是一个很容易误判的细节。

## 7.3 第二步：拉取待处理事件

`findDuePending(limit, now)` 会：

- 只查 `PENDING`
- 只查 `next_retry_at` 为空或已到期
- 按 `id asc`
- limit 最大被钳到 `500`

当前 app 配置 `batch-size=50`，所以默认一次最多处理 50 条。

## 7.4 第三步：抢 lease

对每条事件，worker 会调用：

- `tryClaimProcessing(event.id(), leaseUntil, now)`

也就是把：

- `PENDING -> PROCESSING`

并把 `next_retry_at` 写成 lease 到期时间。

只有抢占成功的 worker 才能继续处理。

这一步是多实例下避免同一事件被同时处理的关键。

## 7.5 第四步：按 topic 分发 handler

`OutboxWorkerScheduler` 启动时会把所有 `OutboxHandler` bean 收集成：

- `topic -> handler`

映射。

worker 处理事件时只做一件事：

- `handlers.get(event.topic())`

所以 outbox 的消费路由完全由：

- `topic()`

字符串决定。

## 8. 处理成功、失败、无 handler 时分别会怎样

## 8.1 成功

如果 handler 正常返回：

- `markSucceeded(event.id(), now)`

事件状态进入：

- `SUCCEEDED`

## 8.2 没有 handler

如果 `topic` 找不到 handler：

- 不会直接丢弃
- 会调 `markFailedAndScheduleRetry(...)`
- 固定 10 秒后重试

这说明“消费端还没部署好”在当前系统里是可恢复状态，不是立刻死信。

## 8.3 handler 抛异常

如果 handler 抛异常，`OutboxWorker` 会进入：

- `handleFailure(event, now, e)`

### 8.3.1 未超过最大重试次数

会：

- `retry_count + 1`
- 按指数退避算下次 `next_retry_at`
- 状态重新回到 `PENDING`

退避计算规则是：

- `baseBackoff * 2^currentRetryCount`
- 上限 `maxBackoff`

### 8.3.2 超过最大重试次数

会：

- `markDead(event.id(), now, e.toString())`

事件进入：

- `DEAD`

不再自动重试。

## 9. 业务 handler 为什么必须幂等

`OutboxHandler` 接口自己就写得很明白：

- handlers should be idempotent

原因很简单：

- outbox 是 at-least-once
- 不是 exactly-once

重复来源至少包括：

- 生产端重复 enqueue 被挡住前的多次事件发布
- worker 抢到后在成功标记前崩溃
- lease 过期恢复后再次处理
- handler 已完成外部副作用，但 `markSucceeded` 前异常

所以真正的幂等边界，不在 worker，而在每个业务 handler 的下游实现。

## 10. 当前几条业务 outbox 是怎么落幂等的

### 10.1 搜索帖子投影

链路：

- `PostOutboxEnqueuer`
- `PostOutboxHandler`

搜索侧天然更适合幂等覆盖式写入，因为目标是：

- 把当前帖子状态投影到索引

不是做不可逆累计。

### 10.2 IM policy 投影

链路：

- `ImPolicyOutboxEnqueuer`
- `ImPolicyKafkaOutboxHandler`

这条链路把用户处罚与拉黑关系变化投递到 IM policy Kafka topic，供 `im-realtime` 本地 projection 更新。

幂等边界在消费侧按事件内容覆盖本地 policy 状态；同一用户处罚或同一拉黑关系重复投递不会产生累计副作用。

## 11. 当前业务 topic 生产方式

当前 enqueuer 的 event id / topic 约定：

### 11.1 Search post

- topic：`projection.search.post`
- 事件 id：`<sourceEventId>:search_post`

### 11.2 IM policy

- topic：`projection.im.policy`
- 事件 id：`im-policy:<kind>:<uuidv7>`

这说明当前 outbox topic 命名不是纯业务名，而是偏“投影用途 + 对象类型”的风格。

## 12. 当前 outbox 与主业务链路的关系

对主写请求而言，outbox 的价值不是“省事”，而是两个更硬的保证：

### 12.1 主事务不被异步副作用拖垮

例如帖子写成功后，不需要在请求线程里同步完成搜索索引更新；用户处罚或拉黑变化提交后，也不需要在源事务里同步完成 IM realtime policy Kafka 发布。

### 12.2 失败后仍可自动追平

如果某个投影 handler 临时失败：

- 不会因为请求已经 200 就永远丢失
- worker 会继续重试

## 13. 当前边界与限制

## 13.1 Outbox 保证的是“可靠投递”，不是“业务全局原子”

在默认 DB 主存储路径下，主写入和 outbox 入库可以同事务提交。

但最终下游副作用完成，仍然是异步阶段。

所以 outbox 保证的是：

- 主事实与待投递消息的一致性

不是：

- 主事实与所有下游最终状态瞬时一致

## 13.2 `DEAD` 只是自动重试终点，不是业务终点

进入 `DEAD` 代表：

- 自动机制放弃了

不代表：

- 业务上就一定可以忽略

这类事件后续仍需要运维或人工介入。

## 13.3 当前 recoverLimit 配置未生效

这个点再次强调一次：

- 配置里有 `recoverLimit`
- 当前 worker 没用到它

如果后面有人以为“恢复条数被限制住了”，那是误读。

## 14. 与其他文档的关系

如果你关心某个业务域自身怎么利用 outbox，请继续看：

- `social-like-follow-outbox-flow.md`
- `notice-projection-read-flow.md`
- `growth-task-grant-level-flow.md`
- `search-projection-reindex-flow.md`
- `ops-scheduler-compensation-flow.md`

本文只解释共享投递保证，不解释具体业务含义。

## 15. 关键代码定位

- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEvent.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEventStatus.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxHandler.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxProperties.java`
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/autoconfig/OutboxAutoConfiguration.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandler.java`
