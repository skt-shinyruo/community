# 数据模型

## 1. 概览

当前仓库以 MySQL 为主存储（用户、帖子、评论、消息），Redis 作为缓存与关系数据存储（点赞、关注、登录凭证、验证码、UV/DAU 等），并使用 Elasticsearch 存储帖子搜索索引。

### 1.1 数据归属与拆分阶段（迭代 3 策略）
为降低一次性拆库风险，本项目采用“共享库 → 独立库”的分阶段策略（详见 `helloagents/plan/202601161428_boot3_ms_vue3_nacos/how.md` 的 ADR-007）。

**P0 当前默认（同实例多 schema）：**
- **MySQL：** 同一 MySQL 实例，按业务域拆为多个 schema（降低耦合与误写风险）：
  - `community`：身份域（`user`），P0 暂由 auth-service/user-service 共享访问
  - `community_content`：内容域（`discuss_post`、`comment`）
  - `community_message`：消息域（`message`、`consumed_event`）
  - `community_search`：搜索域（`search_consumed_event`）
- **最小权限：** 每服务使用独立 MySQL 用户，仅授权自己的 schema（root 仅用于初始化/演练恢复）。
- **Redis：** 以 Key 前缀与命名空间划分归属（见第 3 节）。

**后续演进（P1+）：**
- 身份域彻底解耦：auth-service 不再直连 `community.user`，改为调用 user-service 内部 API。
- 事件可靠投递：在写路径引入 Outbox Pattern（同事务写 outbox + 后台可靠投递 + 可观测堆积）。

---

## 2. MySQL 表（基于 MyBatis Mapper 推断）

> 表归属（共享库阶段建议）：
> - `community.user`：user-service（auth-service 迁移期可只读访问以完成登录校验）
> - `community_message.message` / `community_message.consumed_event`：message-service
> - `community_search.search_consumed_event`：search-service（幂等去重表）

### 2.1 user

**Description：** 用户账号与资料。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 用户 ID |
| username | varchar | Unique | 用户名 |
| password | varchar | Not Null | 加盐哈希后的密码 |
| salt | varchar | Not Null | 盐 |
| email | varchar | Unique | 邮箱 |
| type | int | Not Null | 用户类型（普通/管理员/版主） |
| status | int | Not Null | 激活状态 |
| activation_code | varchar |  | 激活码 |
| header_url | varchar |  | 头像 URL |
| create_time | datetime |  | 创建时间 |

### 2.2 discuss_post

**Description：** 帖子。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 帖子 ID |
| user_id | int | FK(user.id) | 作者 |
| title | varchar |  | 标题 |
| content | text |  | 内容 |
| type | int |  | 置顶标识等 |
| status | int |  | 状态（精华/删除等） |
| create_time | datetime |  | 创建时间 |
| comment_count | int |  | 评论数 |
| score | double |  | 热度分数 |

### 2.3 comment

**Description：** 评论与回复（通过 entity_type/entity_id 关联目标实体）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 评论 ID |
| user_id | int | FK(user.id) | 评论者 |
| entity_type | int |  | 目标实体类型（帖子/评论） |
| entity_id | int |  | 目标实体 ID |
| target_id | int |  | 回复目标用户 ID（可为 0） |
| content | text |  | 内容 |
| status | int |  | 状态 |
| create_time | datetime |  | 创建时间 |

### 2.4 message

**Description：** 私信与系统通知（系统通知用 from_id=1 + conversation_id=topic）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 消息 ID |
| from_id | int |  | 发送者（系统通知时为 1） |
| to_id | int |  | 接收者 |
| conversation_id | varchar |  | 会话 ID 或 topic |
| content | text |  | 内容（通知场景为 JSON 字符串） |
| status | int |  | 状态（未读/已读/删除等） |
| create_time | datetime |  | 创建时间 |

### 2.5 consumed_event

**Description：** 消费端幂等去重表（按 eventId 记录已消费事件，避免重复通知/重复副作用）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 自增 ID |
| event_id | varchar | Unique | 事件 ID（eventId） |
| consumed_at | datetime |  | 消费时间 |

### 2.6 search_consumed_event

**Description：** search-service 消费端幂等去重表（按 eventId 记录已消费事件，避免重复索引副作用）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 自增 ID |
| event_id | varchar | Unique | 事件 ID（eventId） |
| consumed_at | datetime |  | 消费时间 |

### 2.7 login_ticket（Deprecated）

**Description：** 早期登录票据表，当前实现已迁移到 Redis（代码中 Mapper 标记为 `@Deprecated`）。

---

## 3. Redis Key 设计（基于代码推断）

> 具体 key 生成逻辑见 `RedisKeyUtil`。

- **实体点赞集合：** `like:entity:<entityType>:<entityId>`（Set，归属：social-service）
- **用户被赞计数：** `like:user:<userId>`（String counter，归属：social-service）
- **关注列表：** `followee:<userId>:<entityType>`（ZSet，归属：social-service）
- **粉丝列表：** `follower:<entityType>:<entityId>`（ZSet，归属：social-service）
- **UV：** `uv:<yyyy-MM-dd>`（HyperLogLog）+ `uv:<start>:<end>`（union 结果，归属：analytics-service）
- **DAU：** `dau:<yyyy-MM-dd>`（Bitmap）+ `dau:<start>:<end>`（OR 结果，归属：analytics-service）
- **帖子分数刷新集合：** `post:score`（Set，归属：content-service）
  - `captcha:<captchaId>`（String，归属：auth-service）
  - `captcha:fail:<captchaId>`（String counter + TTL，归属：auth-service）
- **登录失败限流（auth-service）：** `auth:login:fail:ip:<ip>` / `auth:login:fail:user:<username>`（String counter + TTL）
- **找回密码（auth-service）：** `auth:pwdreset:<token>`（String userId + TTL，一次性消费）
- **auth-service refresh token（迭代 0）：**
  - `auth:refresh:<refreshToken>` -> JSON（包含 userId/familyId/expiresAt，带 TTL）
  - `auth:refresh:family:<familyId>` -> Set（refreshToken 列表，带 TTL）
- **网关限流（gateway）：** `gateway:ratelimit:<ruleId>:<bucket>:<key>`（String counter + TTL）

---

## 4. Kafka 事件（Envelope + Payload）

> 旁路服务（search/message 等）的数据一致性以“最终一致”为主，事件契约是跨服务协作的边界。  
> 详细约定见方案包：`helloagents/plan/202601161428_boot3_ms_vue3_nacos/event-contract.md`。

### 4.1 Envelope（统一外层）
所有事件统一使用 `EventEnvelope<T>`：
- `eventId`：全局唯一 ID（uuid）
- `traceId`：链路追踪 ID（从 gateway 透传；无则为空）
- `type`：事件类型（例如 `PostPublished`）
- `version`：版本号（从 1 开始）
- `occurredAt`：事件时间（ISO8601）
- `producer`：生产者服务名（例如 `content-service`）
- `payload`：事件负载（随类型变化）

版本演进规则（最低要求）：
- **只新增字段，不删除/不改名**（向后兼容）

### 4.2 v1 示例（节选）

PostPublished（`type=PostPublished`）：
```json
{
  "eventId": "e1",
  "traceId": "t1",
  "type": "PostPublished",
  "version": 1,
  "occurredAt": "2026-01-16T06:28:00Z",
  "producer": "content-service",
  "payload": {
    "postId": 123,
    "userId": 1,
    "title": "hello",
    "content": "content",
    "type": 0,
    "status": 0,
    "createTime": "2026-01-16T06:28:00Z",
    "score": 0.0
  }
}
```

CommentCreated（`type=CommentCreated`）：
```json
{
  "eventId": "e2",
  "traceId": "t1",
  "type": "CommentCreated",
  "version": 1,
  "occurredAt": "2026-01-16T06:28:10Z",
  "producer": "content-service",
  "payload": {
    "commentId": 456,
    "postId": 123,
    "userId": 2,
    "entityType": 1,
    "entityId": 123,
    "targetUserId": 1,
    "content": "hi",
    "createTime": "2026-01-16T06:28:10Z"
  }
}
```

> 说明：LikeCreated/FollowCreated 的字段与可选项，参考 `event-contract.md` 与 `common/src/main/java/com/nowcoder/community/common/event/payload/*`。

---

## 5. Elasticsearch 索引

### discuss_post
- 索引字段（建议）：`postId`、`userId`、`title`、`content`、`type`、`status`、`createTime`、`score`
- 用途：帖子全文检索 + 高亮
- 映射策略（建议）：
  - `title/content`：text（可选 analyzer：`standard` 或 `ik_smart/ik_max_word`，取决于 ES 是否安装 IK）
  - `postId/userId/type/status`：keyword/number
  - `createTime`：date
  - `score`：double（用于热帖/相关性排序）
- 高亮字段：`title`、`content`（标签建议：`<em>...</em>`）
