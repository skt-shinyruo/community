# 事件契约与幂等策略（迭代 1 基线）

Directory: `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/`

> 目标：定义字段级事件契约（topic/type/payload），并给出最小可实现的幂等、重试、死信策略，避免演进为“事件泥球”。

---

## 1. Topic 命名规范（推荐）

### 1.1 命名规范

建议使用统一前缀 + 领域 + 大版本号：
- `community.event.<domain>.v<major>`

示例（迭代 1 v1 基线）：
- `community.event.post.v1`
- `community.event.comment.v1`
- `community.event.social.v1`

死信 Topic（约定后缀）：
- `<topic>.dlq`（例如 `community.event.post.v1.dlq`）

---

## 2. 事件 Envelope（统一外层）

所有事件统一使用以下结构（JSON）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| eventId | string | ✅ | 全局唯一，uuid |
| type | string | ✅ | 事件类型，例如 `PostPublished` |
| version | int | ✅ | 事件版本，从 1 开始 |
| occurredAt | string | ✅ | ISO8601（UTC），例如 `2026-01-16T14:28:00Z` |
| producer | string | ✅ | 生产者服务名，例如 `content-service` |
| traceId | string | ❌ | 链路追踪 ID（从 gateway 透传；无则为空） |
| payload | object | ✅ | 事件负载（随事件类型不同） |

版本演进规则（最小集）：
- **只允许新增字段**（向后兼容）
- 删除/重命名字段必须升大版本并双写一段时间（或并行 topic）

---

## 3. 事件定义（字段级）

### 3.1 PostPublished（发帖）
- Topic：`community.event.post.v1`
- type：`PostPublished`
- payload（v1）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| postId | int | ✅ | 帖子 ID |
| userId | int | ✅ | 作者 ID |
| title | string | ✅ | 标题（可为已转义/过滤版本） |
| content | string | ✅ | 内容（用于搜索索引；如担心体积可只传摘要并由 search-service 回源拉取） |
| type | int | ✅ | 置顶等类型 |
| status | int | ✅ | 精华/删除等状态 |
| createTime | string | ✅ | ISO8601 |
| score | double | ❌ | 初始分数（可为 0；缺省表示未知） |

### 3.2 PostDeleted（删帖）
- Topic：`community.event.post.v1`
- type：`PostDeleted`
- payload（v1）：`{ "postId": 123 }`（其余字段可省略）

### 3.3 CommentCreated（评论/回复）
- Topic：`community.event.comment.v1`
- type：`CommentCreated`
- payload（v1）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| commentId | int | ✅ | 评论 ID |
| postId | int | ✅ | 所属帖子 ID（方便通知跳转） |
| userId | int | ✅ | 评论者 |
| entityType | int | ✅ | 目标实体类型（post/comment） |
| entityId | int | ✅ | 目标实体 ID |
| targetUserId | int | ❌ | 回复/通知目标用户（无则为 null） |
| content | string | ❌ | 评论内容（通知侧可选存储） |
| createTime | string | ❌ | ISO8601（事件生成时刻或评论创建时刻） |

### 3.4 LikeCreated（点赞）
- Topic：`community.event.social.v1`
- type：`LikeCreated`
- payload（v1）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| actorUserId | int | ✅ | 点赞者 |
| entityType | int | ✅ | 目标实体类型 |
| entityId | int | ✅ | 目标实体 ID |
| entityUserId | int | ❌ | 被点赞对象所属用户（用于通知；无则为 null） |
| postId | int | ❌ | 若点赞发生在评论上，建议带所属 postId（便于顺序/跳转） |
| createTime | string | ❌ | ISO8601 |

### 3.5 FollowCreated（关注）
- Topic：`community.event.social.v1`
- type：`FollowCreated`
- payload（v1）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| actorUserId | int | ✅ | 发起关注的用户 |
| entityType | int | ✅ | 被关注实体类型（迭代 1 建议仅 user） |
| entityId | int | ✅ | 被关注实体 ID（例如 userId） |
| entityUserId | int | ❌ | 被关注对象所属用户（一般同 entityId；用于通知） |
| createTime | string | ❌ | ISO8601 |

---

## 4. 分区键与顺序保证（最小约定）

建议分区键（partition key）：
- Post 相关：`postId`
- Comment 相关：`postId`（保证同帖事件大致有序）
- Like：优先 `postId`（若为空则退化为 `entityId`）
- Follow：`entityId`（被关注目标）或 `entityUserId`

---

## 5. 幂等、重试与死信（最小可实现）

### 5.1 幂等（必须）
- 幂等键：`eventId`
- 落地方式（迭代 1 最小实现）：
  - message/search 等消费者侧维护一张 `consumed_event` 表（或 Redis Set）记录已处理 eventId
  - 处理前先查是否已处理，已处理则直接 ack

### 5.2 重试（建议）
- 可选实现：
  - Spring Kafka `@RetryableTopic`（指数退避 + 最大重试次数）
  - 或 `DefaultErrorHandler + FixedBackOff + 自定义 DLQ recoverer`（迭代 1 当前实现）
- 重试耗尽进入 DLQ topic：`<topic>.dlq`

### 5.3 DLQ（必须可观测）
- DLQ 消息 payload 需包含：原始事件 + errorMessage + failedAt + consumerName
- 提供最小运维入口：可通过脚本/接口查看 DLQ 堆积与重放策略（后续迭代）
