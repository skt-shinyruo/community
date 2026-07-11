# 异步事件骨干

本文只描述当前运行时。Content、Social、User 各自通过一条 owner outbox 到 Kafka 的链路发布跨域契约；Search、Growth、User reward、Hot feed、Notice 和 IM policy 都从 owner Kafka topic 进入本域 ApplicationService。不存在本地 publisher 回滚开关或 secondary projection topic。

## 通道模型

owner 对外契约 topic 如下：

| Owner | DB outbox topic | Kafka topic |
| --- | --- | --- |
| content | `eventbus.content` | `content.events` |
| social | `eventbus.social` | `social.events` |
| user | `eventbus.user` | `user.events` |

统一链路是：

```text
owner transaction
  -> eventbus.<owner>
  -> owner outbox handler
  -> <owner>.events
  -> consumer Kafka listener
  -> consumer ApplicationService
```

`events.outbox.enabled` 必须为 `true`；关闭它会让 `community-app` 启动失败。跨域消费者只能使用 owner 的 `contracts.event`，不能读取生产方 domain、infrastructure、mapper 或 dataobject。

当前唯一内部 projection outbox 是 `projection.im.policy`。它是 IM policy 的可靠性边界，不是迁移期重复链路。

## Owner 发布链路

- content：`PostDomainEventBridge` / `CommentDomainEventBridge` 进入 content application，`OutboxContentEventPublisher` 写 `eventbus.content`，随后由 `ContentEventKafkaOutboxHandler`、`ContentEventDispatchApplicationService` 和 `ContentEventKafkaSenderAdapter` 发布 `content.events`。
- social：`OutboxSocialDomainEventPublisher` 写 `eventbus.social`，随后由 `SocialEventKafkaOutboxHandler`、`SocialEventDispatchApplicationService` 和 `SocialEventKafkaSenderAdapter` 发布 `social.events`。
- user：`OutboxUserPolicyEventPublisher` 写 `eventbus.user`，随后由 `UserEventKafkaOutboxHandler`、`UserEventDispatchApplicationService` 和 `UserEventKafkaSenderAdapter` 发布 `user.events`。

`JdbcOutboxEventStore.enqueue` 依靠唯一 `event_id` 去重。`OutboxWorker` 抢占到期行并按 topic 找到 handler；handler 成功后标记 `SUCCEEDED`，失败则按 outbox lease、退避和 `DEAD` 状态机恢复。

## Canonical consumer

| Consumer | Source | Application boundary |
| --- | --- | --- |
| Search | `content.events` | `SearchPostProjectionKafkaListener -> SearchPostProjectionApplicationService` |
| Growth | `content.events` / `social.events` | `TaskProgressEventBackboneKafkaListener -> TaskProgressApplicationService` |
| User reward | `content.events` / `social.events` | `UserRewardKafkaListener -> UserRewardApplicationService` |
| Hot feed | `content.events` / `social.events` | `PostHotFeedProjectionKafkaListener -> PostHotFeedProjectionApplicationService` |
| Notice | `content.events` / `social.events` | `NoticeProjectionKafkaListener -> NoticeProjectionApplicationService` |
| IM policy | `user.events` / `social.events` | `ImPolicyBackboneKafkaListener -> ImPolicyProjectionApplicationService` |

非目标事件类型直接忽略。目标事件一旦被识别，缺失或非法的 event ID、owner version、发生时间、主体标识或必需 payload 字段必须抛错，交给共享 Kafka retry / DLQ 策略处理，不能静默跳过。

## IM policy projection outbox

`ImPolicyBackboneKafkaListener` 保留源 `eventId`、发生时间和正数 owner version，并调用 `ImPolicyProjectionApplicationService`。ApplicationService 通过 `ImPolicyProjectionOutboxPort` 进入 `JdbcImPolicyProjectionOutboxAdapter`：

```text
user.events / social.events
  -> ImPolicyBackboneKafkaListener
  -> ImPolicyProjectionApplicationService
  -> JdbcImPolicyProjectionOutboxAdapter
  -> projection.im.policy
  -> ImPolicyKafkaOutboxHandler
  -> ImPolicyEventDispatchApplicationService
  -> IM policy Kafka event
```

adapter 以 source domain、source event ID 和 kind 生成确定性 ASCII event ID，长度不超过 64；同一源事件重复投递最多产生一行 outbox。随后分别发布 `im.event.user-messaging-policy-changed` 或 `im.event.user-block-relation-changed`。

## 重试和 DLQ

owner outbox 是 at-least-once，状态为 `PENDING`、`PROCESSING`、`SUCCEEDED`、`DEAD`。Kafka listener 已取得记录后的异常不会回写 producer outbox，而由 `CommunityKafkaListenerConfiguration` 的共享 `DefaultErrorHandler` 处理：

- 总投递次数来自 `KafkaPolicyDecisions.retryMaxAttempts()`，包含首次投递。
- 退避使用配置的 base 和 max duration。
- 最终失败发布到源 topic 的 `.dlq`，保留原 partition 与异常 headers。
- `content.events`、`social.events`、`user.events` 及各自 `.dlq` 均为 12 partitions。

## 幂等

- Search 每次处理都通过 `PostScanQueryApi` 回读 content 当前事实，不让乱序事件复活已删除帖子。
- Notice 以 `sourceEventId` 记录 projection event，重复源事件直接跳过。
- Growth 以 `user_task_event_log` 去重；like removed 按 relation key 回滚尚未 claimed 的贡献。
- User reward 生成稳定 wallet request ID，由 wallet owner 保证最终幂等。
- IM policy 先以确定性 outbox event ID 去重，realtime projection 再只接受更大的显式 owner version。

## 边界约束

Kafka listener、outbox handler 和 bridge 都只能先进入同域 ApplicationService。跨域同步回读必须经过 owner `api.query` / `api.action`；异步协作只能使用 owner `contracts.event`。
