# message

## Purpose
提供私信与系统通知能力，并通过 Kafka 消费事件生成通知消息。

## Module Overview
- **Responsibility：** 私信会话列表/详情/发送；通知列表/详情/未读数；Kafka 消费评论/点赞/关注事件写入通知；消费失败重试与 DLQ
- **Status：** ✅Stable
- **Last Updated：** 2026-01-18

## Specifications

### Requirement: 私信
**Module:** message
用户可查看私信会话与详情，并发送私信。

#### Scenario: 查看私信列表
前置条件：用户已登录
- 返回会话列表、未读数、目标用户信息

#### Scenario: 发送私信
前置条件：目标用户存在
- 私信写入数据库

### Requirement: 系统通知
**Module:** message
用户在被评论/被点赞/被关注时收到系统通知。

#### Scenario: 消费事件生成通知
前置条件：Kafka 收到事件
- 写入 message 表（from_id=系统用户）
- 通知内容包含触发者与目标实体信息

### Requirement: 消费端幂等 + 事务 + ack（P0）
**Module:** message
保证在重复消费/重试场景下“不丢/不乱”，并避免 ack 早于事务提交造成的不一致。

#### Scenario: 重复消费不重复通知
前置条件：同一 `eventId` 的消息被重复投递（重试、重平衡等）
- 以 `consumed_event(event_id unique)` 作为幂等锁（insert-first）
- 幂等记录与通知写入同一 DB 事务提交
- 仅在业务处理成功后 ack（ack 之前副作用已提交）

#### Scenario: 失败进入 DLQ 可观测/可回放
前置条件：消费处理重试耗尽或不可恢复异常
- 投递到 `<topic>.dlq`
- 增加 `kafka_dlq_published_total` 指标并告警
- 通过 `scripts/kafka-replay-dlq.sh` 在演练/受控窗口回放

## API Interfaces（现状）
- `GET /api/messages/conversations`
- `GET /api/messages/conversations/{conversationId}`
- `POST /api/messages`
- `GET /api/messages/unread-count`
- `PUT /api/messages/read`
- `GET /api/notices?topic=like|comment|follow`
- `GET /api/notices/unread-count`
- `PUT /api/notices/read`

## Data Models
### message
（详见 `helloagents/wiki/data.md` 的 “message” 小节）

## Dependencies
- user（目标用户信息）
- infra（Kafka、Security/登录态）

## Change History
- 2026-01-18：消费端幂等/事务/ack 正确性修复（insert-first + 同事务提交），并补齐 DLQ 指标/告警与回放脚本。
