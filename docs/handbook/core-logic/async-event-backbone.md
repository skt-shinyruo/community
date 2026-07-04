# 异步事件骨干

本文只描述当前已实现的异步事件运行时行为。这里的“骨干”包含两类通道：owner contract events 和内部 projection topics。

## 两类事件通道

owner contract events 是领域对外发布的异步契约。事件类型和 payload 属于生产方 `contracts.event`，当前 owner outbox topic 是 `eventbus.content`、`eventbus.social`、`eventbus.user`，最终 Kafka topic 分别是 `content.events`、`social.events`、`user.events`。外域只能消费这些契约事件，不能把生产方 domain、infrastructure、mapper 或 dataobject 当入口。

内部 projection topics 是本服务内的派生工作队列，不是 owner 对外契约。当前 topic 包括 `projection.user.reward.comment`、`projection.growth.task.post`、`projection.growth.task.comment`、`projection.growth.task.like`、`projection.search.post`、`projection.im.policy`。它们承接某个 projection 的可靠处理、二次分发或本地读模型更新，payload 可以是 owner contract payload 的子集或 projection 自己的内部格式。

## Owner Contract Event 发布链路

源端先产生 domain event 或 contract event，再进入 outbox source：

- content：`PostDomainEventBridge`、`CommentDomainEventBridge` 在 `BEFORE_COMMIT` 调用 content application service，`OutboxContentEventPublisher` 将 `ContentContractEvent` 写入 `eventbus.content`。
- social：`OutboxSocialDomainEventPublisher` 将 like、follow、block 的 `SocialContractEvent` 写入 `eventbus.social`。
- user：`OutboxUserPolicyEventPublisher` 将 `UserContractEvent` 写入 `eventbus.user`。

`JdbcOutboxEventStore.enqueue` 写 `outbox_event`，用唯一 `event_id` 去重；重复插入被视为已入队。`OutboxWorker` 轮询 `PENDING` 且到期的行，抢占为 `PROCESSING`，按 `topic()` 路由到 `OutboxHandler`：

- `ContentEventKafkaOutboxHandler` -> `ContentEventDispatchApplicationService` -> `ContentEventKafkaSenderAdapter` -> `content.events`
- `SocialEventKafkaOutboxHandler` -> `SocialEventDispatchApplicationService` -> `SocialEventKafkaSenderAdapter` -> `social.events`
- `UserEventKafkaOutboxHandler` -> `UserEventDispatchApplicationService` -> `UserEventKafkaSenderAdapter` -> `user.events`

dispatch application service 会校验 outbox payload 非空，反序列化为 owner `contracts.event` 类型，并要求 `eventId`、`type` 存在。sender adapter 通过 `TraceKafkaSender.send(...).join()` 发送 Kafka，并注入 trace headers。发送成功后 outbox 标记 `SUCCEEDED`；反序列化、校验或 Kafka publish 抛错会回到 `OutboxWorker`。

## Projection 与二次分发

内部 projection topic 也走同一个 DB outbox worker，但 topic 语义是 projection 自己的工作项。

- `projection.user.reward.comment`：`CommentRewardOutboxEnqueuer` 从 `ContentContractEvent(CommentCreated)` 入队，`CommentRewardOutboxHandler` 进入 `UserRewardApplicationService`。
- `projection.growth.task.post` / `comment` / `like`：对应 `PostTaskProgressKafkaOutboxEnqueuer`、`CommentTaskProgressOutboxEnqueuer`、`LikeTaskProgressKafkaOutboxEnqueuer` 入队；handler 调用 `TaskProgressOutboxDispatchApplicationService`，再发送到 `growth.task.post-published`、`growth.task.comment-created`、`growth.task.like-created`、`growth.task.like-removed`。
- `projection.search.post`：`PostOutboxEnqueuer` 只记录 post id 与 source event 信息；`PostOutboxHandler` 调用 `SearchPostProjectionApplicationService`。
- `projection.im.policy`：`ImPolicyChangePublisher` 入队，`ImPolicyKafkaOutboxHandler` 调用 `ImPolicyEventDispatchApplicationService`，再发布 IM policy Kafka 事件。

同时存在直接消费 owner Kafka topic 的 projection listener：`UserRewardKafkaListener`、`TaskProgressEventBackboneKafkaListener`、`SearchPostProjectionKafkaListener`、`NoticeProjectionKafkaListener`、`SocialInteractionBackboneKafkaListener`、`ImPolicyBackboneKafkaListener`。这些 listener 失败后的重试、DLQ 属于消费者侧路径；DB outbox 的 retry 只覆盖写入 Kafka 之前和发送 Kafka 这一步。

## 失败与重试

DB outbox 是 at-least-once。状态只有 `PENDING`、`PROCESSING`、`SUCCEEDED`、`DEAD`：

- `PENDING`：可被 worker 拉取；`next_retry_at` 为空或到期才会被处理。
- `PROCESSING`：worker 抢占成功后的处理中状态，`next_retry_at` 同时作为 processing lease。
- `SUCCEEDED`：handler 完成。
- `DEAD`：失败次数超过 `events.outbox.max-retries`。

`OutboxWorker` 每轮先恢复过期 lease，将卡住的 `PROCESSING` 重新置为 `PENDING`。handler 抛错时，worker 递增 `retry_count`，按 base backoff 指数退避并受 max backoff 限制；超过最大重试次数后标记 `DEAD`。无 handler 的 topic 也会回到 `PENDING` 并延迟重试。

Kafka listener 已经拿到消息后的异常不回写 producer outbox。它应由该消费者的 Spring Kafka retry/DLQ 配置处理；例如 IM runtime 使用 `DefaultErrorHandler` 和 `.dlq` topic。

## 去重与幂等

当前幂等策略按 projection 各自实现：

- source outbox：`outbox_event.event_id` 唯一，重复 enqueue 返回 false。
- search：`SearchPostProjectionApplicationService` 不信任事件顺序，处理时通过 `PostScanQueryApi.getPostProjectionAllowDeleted` 重读 content 当前状态；当前状态不存在则删除索引。
- notice：可靠 Kafka 路径要求 `sourceEventId`，`MyBatisNoticeProjectionEventRecorder` 先记录 source event，重复 source event 直接跳过；notice content 里也写入 `eventId`。
- growth：`TaskProgressApplicationService` 通过 `user_task_event_log` 记录 `userId + taskCode + periodKey + sourceEventId`，重复插入失败即跳过；like removed 按 relation key 查贡献日志并回滚未 claimed 的进度。
- reward：`UserRewardApplicationService` 生成 wallet requestId `wallet-reward:<sourceEventId>`，钱包域负责最终幂等。
- IM policy：`ImPolicyChangePublisher` 生成带 version 的 policy delta，`PolicyProjectionService` 只接受比当前版本新的 user policy 或 block relation delta。

## 边界约束

projection listener、outbox handler、bridge 都只进入本域 application service 或 owner 发布的 API。典型例子是 search 通过 `PostScanQueryApi` 读取 content 当前投影，notice 进入 `NoticeProjectionApplicationService`，growth 进入 `TaskProgressApplicationService`，reward 进入 `UserRewardApplicationService` 后调用 wallet owner API。它们不直接读取 producer 的 domain model、repository、mapper 或 dataobject。

## 当前接入点

- content：`PostDomainEventBridge`、`CommentDomainEventBridge`、`OutboxContentEventPublisher`、`ContentEventKafkaOutboxHandler`、`ContentEventDispatchApplicationService`、`ContentEventKafkaSenderAdapter`、`SocialInteractionProjectionListener`、`SocialInteractionBackboneKafkaListener`、`SocialInteractionProjectionApplicationService`
- social：`OutboxSocialDomainEventPublisher`、`SocialEventKafkaOutboxHandler`、`SocialEventDispatchApplicationService`、`SocialEventKafkaSenderAdapter`
- user：`OutboxUserPolicyEventPublisher`、`UserEventKafkaOutboxHandler`、`UserEventDispatchApplicationService`、`UserEventKafkaSenderAdapter`、`CommentRewardOutboxEnqueuer`、`CommentRewardOutboxHandler`、`UserRewardKafkaListener`、`UserRewardApplicationService`
- growth：`PostTaskProgressKafkaOutboxEnqueuer`、`CommentTaskProgressOutboxEnqueuer`、`LikeTaskProgressKafkaOutboxEnqueuer`、`PostTaskProgressKafkaOutboxHandler`、`CommentTaskProgressKafkaOutboxHandler`、`LikeTaskProgressKafkaOutboxHandler`、`TaskProgressOutboxDispatchApplicationService`、`TaskProgressKafkaSenderAdapter`、`TaskProgressEventBackboneKafkaListener`、`TaskProgressKafkaListener`、`TaskProgressApplicationService`
- search：`PostOutboxEnqueuer`、`PostOutboxHandler`、`SearchPostProjectionKafkaListener`、`SearchPostProjectionApplicationService`
- notice：`NoticeProjectionListener`、`NoticeProjectionKafkaListener`、`NoticeProjectionApplicationService`、`MyBatisNoticeProjectionEventRecorder`
- IM policy：`ImPolicyOutboxEnqueuer`、`ImPolicyBackboneKafkaListener`、`ImPolicyChangePublisher`、`ImPolicyKafkaOutboxHandler`、`ImPolicyEventDispatchApplicationService`、`ImPolicyEventKafkaSenderAdapter`、`EventConsumers`、`PolicyProjectionService`

## 审计备注

IM policy 当前有一个需要持续可见的不对称点：social block 事件通过 `ImPolicyBackboneKafkaListener` 从 `social.events` 进入 `projection.im.policy`；user policy 事件也可以通过本地 `ImPolicyOutboxEnqueuer` 从 `UserContractEvent` 进入同一个内部 topic。这是现状记录，不按代码缺陷处理；审计 async event backbone 时应继续显式检查这两条路径。
