# 事件契约与幂等策略（迭代 1 基线）

Directory: `helloagents/history/2026-01/202601161428_boot3_ms_vue3_nacos/`

> 目标：定义字段级事件契约（topic/type/payload），并给出最小可实现的幂等、重试、死信策略，避免演进为“事件泥球”。

---

## 1. Topic 命名规范（推荐）

建议按领域拆 topic（便于扩容与权限隔离）：
- `community.content.post`
- `community.content.comment`
- `community.social.like`
- `community.social.follow`

死信 Topic（约定后缀）：
- `<topic>.dlq`（例如 `community.social.like.dlq`）

---

## 2. 事件 Envelope（统一外层）

所有事件统一使用以下结构（JSON）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| eventId | string | ✅ | 全局唯一，uuid |
| eventType | string | ✅ | 事件类型，例如 `PostPublished` |
| version | int | ✅ | 事件版本，从 1 开始 |
| occurredAt | string | ✅ | ISO8601（UTC），例如 `2026-01-16T14:28:00Z` |
| producer | string | ✅ | 生产者服务名，例如 `content-service` |
| traceId | string | ✅ | 链路追踪 ID（从 gateway 透传） |
| payload | object | ✅ | 事件负载（随事件类型不同） |

版本演进规则（最小集）：
- **只允许新增字段**（向后兼容）
- 删除/重命名字段必须升大版本并双写一段时间（或并行 topic）

---

## 3. 事件定义（字段级）

### 3.1 PostPublished（发帖）
- Topic：`community.content.post`
- eventType：`PostPublished`
- payload（v1）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| postId | int | ✅ | 帖子 ID |
| authorId | int | ✅ | 作者 ID |
| title | string | ✅ | 标题（可为已转义/过滤版本） |
| content | string | ✅ | 内容（用于搜索索引；如担心体积可只传摘要并由 search-service 回源拉取） |
| type | int | ✅ | 置顶等类型 |
| status | int | ✅ | 精华/删除等状态 |
| createTime | string | ✅ | ISO8601 |
| score | double | ✅ | 初始分数（可为 0） |

### 3.2 PostDeleted（删帖）
- Topic：`community.content.post`
- eventType：`PostDeleted`
- payload（v1）：`{ "postId": 123 }`

### 3.3 CommentCreated（评论/回复）
- Topic：`community.content.comment`
- eventType：`CommentCreated`
- payload（v1）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| commentId | int | ✅ | 评论 ID |
| userId | int | ✅ | 评论者 |
| entityType | int | ✅ | 目标实体类型（post/comment） |
| entityId | int | ✅ | 目标实体 ID |
| targetUserId | int | ❌ | 回复目标用户（无则 0 或 null） |
| postId | int | ✅ | 所属帖子 ID（方便通知跳转） |

### 3.4 LikeCreated（点赞）
- Topic：`community.social.like`
- eventType：`LikeCreated`
- payload（v1）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | int | ✅ | 点赞者 |
| entityType | int | ✅ | 目标实体类型 |
| entityId | int | ✅ | 目标实体 ID |
| entityOwnerId | int | ✅ | 被点赞对象所属用户（用于通知） |
| postId | int | ❌ | 若点赞发生在评论上，也可带所属 postId |

### 3.5 FollowCreated（关注）
- Topic：`community.social.follow`
- eventType：`FollowCreated`
- payload（v1）：`{ "userId": 1, "targetUserId": 2, "followedAt": "ISO8601" }`

---

## 4. 分区键与顺序保证（最小约定）

建议分区键（partition key）：
- Post 相关：`postId`
- Comment 相关：`postId`（保证同帖事件大致有序）
- Like/Follow：`entityId` 或 `targetUserId`（按业务需要选择）

---

## 5. 幂等、重试与死信（最小可实现）

### 5.1 幂等（必须）
- 幂等键：`eventId`
- 落地方式（迭代 1 最小实现）：
  - message/search 等消费者侧维护一张 `consumed_event` 表（或 Redis Set）记录已处理 eventId
  - 处理前先查是否已处理，已处理则直接 ack

### 5.2 重试（建议）
- 优先使用 Spring Kafka 的 `@RetryableTopic`（指数退避 + 最大重试次数）
- 重试耗尽进入 DLQ topic：`<topic>.dlq`

### 5.3 DLQ（必须可观测）
- DLQ 消息 payload 需包含：原始事件 + errorMessage + failedAt + consumerName
- 提供最小运维入口：可通过脚本/接口查看 DLQ 堆积与重放策略（后续迭代）
