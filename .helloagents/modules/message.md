# message

## Purpose
提供私信与系统通知能力，并通过 Kafka 消费事件生成通知消息。

## Module Overview
- **Responsibility：** 私信会话列表/详情/发送；通知列表/详情/未读数；Kafka 消费评论/点赞/关注事件写入通知；消费失败重试与 DLQ
- **Status：** ✅Stable
- **Last Updated：** 2026-02-24

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
  - 发送前校验拉黑关系（双向）：仅依赖本地投影 `user_block_projection`（最终一致）
    - 实时纠偏：消费 `BlockRelationChanged` 事件更新投影
    - 冷启动/补洞：通过 `SocialBlockScanRpcService` 扫描“当前拉黑集合”做投影自举（避免 per-request 点查回源）
  - toName 场景（按用户名发送）会触发 username→userId 的 resolve：默认加入短 TTL + 有界容量缓存，降低重复回源导致的依赖放大（配置见 Dependencies）

### Requirement: 系统通知
**Module:** message
用户在被评论/被点赞/被关注时收到系统通知。

#### Scenario: 消费事件生成通知
前置条件：Kafka 收到事件
- 写入 message 表（from_id=系统用户）
- 通知内容包含触发者与目标实体信息
  - 支持治理通知（moderation topic），用于回传治理处置结果给用户

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
- `GET /api/messages/conversations`（会话列表，返回 DTO：`LetterItemResponse`）
- `GET /api/messages/conversations/detail`（会话聚合列表，返回 DTO：`ConversationItemResponse`，包含 targetUser + lastMessage）
- `GET /api/messages/conversations/{conversationId}`（会话详情，返回 DTO：`LetterItemResponse` 列表）
- `POST /api/messages`
- `GET /api/messages/unread-count`
- `PUT /api/messages/read`
- `GET /api/notices?topic=like|comment|follow|moderation`
- `GET /api/notices/unread-count`
- `PUT /api/notices/read`

## Data Models
### message
（详见 `.helloagents/data.md` 的 “message” 小节）

## Dependencies
- user（目标用户信息）
- social（通过 `social-api` Dubbo RPC：`SocialBlockScanRpcService` 扫描当前拉黑集合用于投影自举；写路径不再 per-request 点查）
- infra（Kafka、Security/登录态）
  - username resolve 缓存（message-service -> user-service）：
    - `clients.user.resolve-cache.ttl`（默认 60s）
    - `clients.user.resolve-cache.max-size`（默认 5000）
  - block 投影自举（message-service -> social-service）：
    - `message.projection.block-scan.enabled`（默认 true）
    - `message.projection.block-scan.interval-ms`（默认 2000）
    - `message.projection.block-scan.batch-size`（默认 2000）
    - `message.projection.block-scan.rescan-interval-ms`（默认 3600000）
    - `message.projection.block-scan.single-flight`（默认 true）
    - `message.projection.block-scan.lock-ttl-seconds`（默认 300）

## Change History
- 2026-01-18：消费端幂等/事务/ack 正确性修复（insert-first + 同事务提交），并补齐 DLQ 指标/告警与回放脚本。
- 2026-01-23：私信写路径补齐拉黑校验；通知消费端新增治理事件（moderation topic）支持。
- 2026-02-01：私信拉黑校验消除 fail-open：投影缺失时回源 social SSOT 并回填；对外私信接口返回 DTO，避免直接暴露实体契约。
- 2026-02-02：私信 toName 写路径的 username→userId resolve 增加短 TTL 缓存（容量受控），降低同步依赖放大与尾延迟。
- 2026-02-03：`consumed_event` 清理任务改为分批 delete（`order by consumed_at, id limit N`），并支持可选 single-flight（多实例避免重复执行）；索引对齐为 `idx_consumed_event_at(consumed_at, id)`。
- 2026-02-24：私信写路径移除“投影缺失 -> social 点查回源”同步依赖；拉黑校验收敛为本地投影 + scan 自举；新增架构门禁防止回潮。
