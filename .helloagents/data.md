# 数据模型

## 1. 概览

当前仓库以 MySQL 为主存储（用户、帖子、评论、消息），Redis 作为缓存与关系数据存储（点赞、关注、登录凭证、验证码、UV/DAU 等），并使用 Elasticsearch 存储帖子搜索索引。

### 1.1 数据归属与拆分阶段（迭代 3 策略）
为降低一次性拆库风险，本项目采用“共享库 → 独立库”的分阶段策略（详见 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/how.md` 的 ADR-007）。

**P0 当前默认（同实例多 schema）：**
- **MySQL：** 同一 MySQL 实例，按业务域拆为多个 schema（降低耦合与误写风险）：
  - `community`：身份域（`user`），由 user-service 持有（auth-service 不再直连 MySQL）
  - `community_content`：内容域（`discuss_post`、`comment`、`outbox_event`）
  - `community_message`：消息域（`message`、`consumed_event`）
  - `community_search`：搜索域（`search_consumed_event`）
- **最小权限：** 每服务使用独立 MySQL 用户，仅授权自己的 schema（root 仅用于初始化/演练恢复）。
- **Redis：** 以 Key 前缀与命名空间划分归属（见第 3 节）。

**后续演进（P1+）：**
- 身份域彻底解耦（已完成）：auth-service 不再直连 `community.user`，改为调用 user-service 内部 API。
- 事件可靠投递：在写路径引入 Outbox Pattern（同事务写 outbox + 后台可靠投递 + 可观测堆积）。

---

## 2. MySQL 表（基于 MyBatis Mapper 推断）

> 表归属（共享库阶段建议）：
> - `community.user`：user-service（auth-service 通过 user-service internal API 完成鉴权/注册/激活/重置密码）
> - `community_message.message` / `community_message.consumed_event`：message-service
> - `community_search.search_consumed_event`：search-service（幂等去重表）
> - `community_content.outbox_event`：content-service（Outbox 可靠投递）

### 2.1 user

**Description：** 用户账号与资料。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 用户 ID |
| username | varchar | Unique | 用户名 |
| password | varchar |  | 密码哈希（legacy: MD5+salt；新用户/重置后: BCrypt） |
| salt | varchar |  | legacy 盐（BCrypt 不需要，可为空/空字符串） |
| email | varchar | Unique | 邮箱 |
| type | int | Not Null | 用户类型（普通/管理员/版主） |
| status | int | Not Null | 激活状态 |
| activation_code | varchar |  | 激活码 |
| header_url | varchar |  | 头像 URL |
| create_time | datetime |  | 创建时间 |
| score | int | Not Null | 成长积分（用于等级/榜单） |
| mute_until | datetime |  | 禁言到期时间（可为空；大于当前时间表示禁言中） |
| ban_until | datetime |  | 封禁到期时间（可为空；大于当前时间表示封禁中） |

### 2.1.1 user_score_log

**Description：** 成长积分流水（消费端幂等 + 反作弊：按 eventId 去重）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | bigint | PK | 自增 ID |
| user_id | int | FK(user.id) | 用户 ID |
| event_id | varchar | Unique | 事件 ID（eventId） |
| type | varchar |  | 事件类型（例如 `PostPublished`/`CommentCreated`/`LikeCreated`） |
| delta | int |  | 积分增量（可为负） |
| created_at | datetime |  | 记账时间 |

### 2.1.2 auth_refresh_token

**Description：** refresh token 会话状态（SSOT=DB）。auth-service 不直连 MySQL，由 user-service 托管 internal session API。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| token_hash | char(64) | PK | refresh token 的 SHA-256 hex（仅存 hash，避免明文凭据落库） |
| user_id | int | Not Null | 用户 ID |
| family_id | varchar(64) | Not Null | token family（用于 rotate/revokeFamily） |
| expires_at | timestamp | Not Null | 过期时间 |
| revoked_at | timestamp |  | 撤销时间（为空表示有效） |
| created_at | timestamp |  | 创建时间 |

**Indexes：**
- `idx_refresh_family(family_id, expires_at)`
- `idx_refresh_user(user_id, expires_at)`

### 2.2 discuss_post

**Description：** 帖子。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 帖子 ID |
| user_id | int | FK(user.id) | 作者 |
| category_id | int |  | 分类 ID（可为空，指向 `category.id`） |
| title | varchar |  | 标题 |
| content | text |  | 内容 |
| type | int |  | 置顶标识等 |
| status | int |  | 状态（精华/删除等） |
| create_time | datetime |  | 创建时间 |
| update_time | datetime |  | 更新时间 |
| edit_count | int |  | 编辑次数（用于显示/风控） |
| deleted_by | int |  | 删除操作者（作者软删/版主删；0 表示无） |
| deleted_reason | varchar |  | 删除原因（可为空/空字符串） |
| deleted_time | datetime |  | 删除时间 |
| comment_count | int |  | 评论数 |
| score | double |  | 热度分数 |

### 2.2.1 category

**Description：** 分类字典（Discourse-like taxonomy）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 分类 ID |
| name | varchar | Unique | 分类名 |
| description | varchar |  | 描述 |
| position | int |  | 排序（越小越靠前） |
| create_time | datetime |  | 创建时间 |

**Indexes：**
- `uk_category_name(name)`

### 2.2.2 tag

**Description：** 标签字典（唯一）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 标签 ID |
| name | varchar | Unique | 标签名 |
| create_time | datetime |  | 创建时间 |

**Indexes：**
- `uk_tag_name(name)`

### 2.2.3 post_tag

**Description：** 帖子-标签关联表（多对多）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| post_id | int | PK(post_id, tag_id) | 帖子 ID |
| tag_id | int | PK(post_id, tag_id) | 标签 ID |
| create_time | datetime |  | 创建时间 |

**Indexes：**
- `idx_post_tag_post_id(post_id)`
- `idx_post_tag_tag_id(tag_id)`

### 2.2.4 report

**Description：** 举报单（帖子/评论/用户），用于治理闭环（提交 → 审核 → 处置）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | bigint | PK | 举报 ID |
| reporter_id | int |  | 举报人 |
| target_type | varchar |  | 目标类型（POST/COMMENT/USER） |
| target_id | int |  | 目标 ID（userId/postId/commentId） |
| reason_code | varchar |  | 举报原因码（前端枚举） |
| reason_text | varchar |  | 补充说明（可为空） |
| status | varchar |  | 状态（OPEN/RESOLVED/REJECTED） |
| created_at | datetime |  | 创建时间 |
| resolved_at | datetime |  | 完结时间（可为空） |

### 2.2.5 moderation_action

**Description：** 治理处置审计表（谁在什么时间对哪个目标做了什么动作，具备可追溯性）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | bigint | PK | 处置动作 ID |
| report_id | bigint |  | 对应的举报单（可为空：直接处置） |
| moderator_id | int |  | 版主/管理员 |
| action_type | varchar |  | 动作类型（DELETE_POST/DELETE_COMMENT/MUTE_USER/BAN_USER/NO_ACTION 等） |
| target_type | varchar |  | 目标类型（POST/COMMENT/USER） |
| target_id | int |  | 目标 ID |
| action_payload | text |  | 动作参数（JSON；例如禁言/封禁到期时间、原因） |
| created_at | datetime |  | 执行时间 |

### 2.2.6 post_bookmark

**Description：** 帖子收藏（用户-帖子多对多）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| user_id | int | PK(user_id, post_id) | 用户 ID |
| post_id | int | PK(user_id, post_id) | 帖子 ID |
| created_at | datetime |  | 收藏时间 |

### 2.2.7 user_subscription_category

**Description：** 分类订阅（用户-分类多对多，用于“仅看订阅”）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| user_id | int | PK(user_id, category_id) | 用户 ID |
| category_id | int | PK(user_id, category_id) | 分类 ID |
| created_at | datetime |  | 订阅时间 |

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
| update_time | datetime |  | 更新时间 |
| edit_count | int |  | 编辑次数 |
| deleted_by | int |  | 删除操作者（作者软删/版主删；0 表示无） |
| deleted_reason | varchar |  | 删除原因（可为空/空字符串） |
| deleted_time | datetime |  | 删除时间 |

### 2.4 outbox_event

**Description：** content-service Outbox 事件表（同事务写入，后台可靠投递 Kafka）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | bigint | PK | 自增 ID |
| event_id | varchar | Unique | 事件 ID（eventId） |
| topic | varchar |  | Kafka topic |
| event_key | varchar |  | Kafka key |
| payload | text |  | 事件 JSON |
| status | varchar |  | NEW/SENDING/RETRY/SENT/FAILED |
| retry_count | int |  | 重试次数 |
| next_retry_at | datetime |  | 下次重试时间 |
| last_error | varchar |  | 最近一次错误 |
| created_at | datetime |  | 创建时间 |
| updated_at | datetime |  | 更新时间 |

**Indexes：**
- `idx_outbox_status_next(status, next_retry_at, id)`

### 2.5 message

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

### 2.6 consumed_event

**Description：** 消费端幂等去重表（按 eventId 记录已消费事件，避免重复通知/重复副作用）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 自增 ID |
| event_id | varchar | Unique | 事件 ID（eventId） |
| consumed_at | datetime |  | 消费时间 |

**Indexes：**
- `idx_consumed_event_at(consumed_at, id)`

### 2.7 search_consumed_event

**Description：** search-service 消费端幂等去重表（按 eventId 记录已消费事件，避免重复索引副作用）。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | int | PK | 自增 ID |
| event_id | varchar | Unique | 事件 ID（eventId） |
| consumed_at | datetime |  | 消费时间 |

**Indexes：**
- `idx_search_consumed_at(consumed_at, id)`

### 2.8 login_ticket（Deprecated）

**Description：** 早期登录票据表，当前实现已迁移到 Redis（代码中 Mapper 标记为 `@Deprecated`）。

### 2.9 http_idempotency

**Description：** HTTP 写接口幂等表（SSOT=DB）。用于发帖/评论/私信等 required 幂等：同一 `(operation, user_id, idem_key)` 只执行一次副作用；后续请求返回同一响应。

> 该表按业务域落在各自 schema 中：
> - `community_content.http_idempotency`（content-service）
> - `community_message.http_idempotency`（message-service）

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | bigint | PK | 自增 ID |
| operation | varchar(64) | Not Null | 幂等操作名（路由级别 key） |
| user_id | int | Not Null | 用户 ID |
| idem_key | varchar(128) | Not Null | 请求幂等键（Idempotency-Key） |
| status | varchar(16) | Not Null | 状态（PROCESSING/SUCCESS 等） |
| response_json | mediumtext |  | 成功响应缓存 JSON（便于重复请求返回同一响应） |
| processing_expires_at | timestamp |  | PROCESSING 租约 TTL（避免死锁） |
| success_expires_at | timestamp |  | SUCCESS 缓存 TTL（幂等窗口） |
| created_at | timestamp |  | 创建时间 |
| updated_at | timestamp |  | 更新时间 |

**Indexes：**
- `uk_http_idem(operation, user_id, idem_key)`
- `idx_http_idem_processing_expires(processing_expires_at, id)`
- `idx_http_idem_success_expires(success_expires_at, id)`

---

## 3. Redis Key 设计（基于代码推断）

> 具体 key 生成逻辑见 `RedisKeyUtil`。

- **实体点赞集合：** `like:entity:<entityType>:<entityId>`（Set，归属：social-service）
- **用户被赞计数：** `like:user:<userId>`（String counter，归属：social-service）
- **关注列表：** `followee:<userId>:<entityType>`（ZSet，归属：social-service）
- **粉丝列表：** `follower:<entityType>:<entityId>`（ZSet，归属：social-service）
- **拉黑集合：** `block:<userId>`（Set，被拉黑用户 ID 列表，归属：social-service）

> 一致性说明（Redis 非 SSOT 场景）：  
> 当 `social.storage=redis` 时，social-service 写路径通过 Lua 脚本将跨 key 的复合更新收敛为单次原子操作：  
> - follow：`followee:*` + `follower:*` 原子双写（并对历史/异常窗口造成的“双写不一致”做 best-effort 自愈）；  
> - like：`like:entity:*` + `like:user:*` 原子更新（计数做非负保护）。  
> 仍需注意：Redis 状态与 DB Outbox 无法强原子，仅做 best-effort 回滚与边界文档化。
- **UV：** `uv:<yyyy-MM-dd>`（HyperLogLog，日粒度）+ `uv:tmp:<start>:<end>:<rand>`（区间 union 临时 key：查询结束 delete，异常时短 TTL 兜底；归属：analytics-service）
- **DAU：** `dau:<yyyy-MM-dd>`（Bitmap，日粒度）+ `dau:tmp:<start>:<end>:<rand>`（区间 OR 临时 key：查询结束 delete，异常时短 TTL 兜底；归属：analytics-service）
- **帖子分数刷新集合：** `post:score`（Set，归属：content-service）
  - `captcha:<captchaId>`（String，归属：auth-service）
  - `captcha:fail:<captchaId>`（String counter + TTL，归属：auth-service）
- **登录失败限流（auth-service）：** `auth:login:fail:ip:<ip>` / `auth:login:fail:user:<username>`（String counter + TTL）
- **找回密码（auth-service）：** `auth:pwdreset:<token>`（String userId + TTL，一次性消费）
- **auth-service refresh token（可选 Redis store，legacy）：**
  - 默认已迁移到 MySQL `auth_refresh_token`（由 user-service 托管）；仅当 `auth.refresh.store=redis` 时使用以下 key
  - `auth:refresh:<refreshToken>` -> JSON（包含 userId/familyId/expiresAt，带 TTL）
  - `auth:refresh:family:<familyId>` -> Set（refreshToken 列表，带 TTL）
- **网关限流（gateway）：** `gateway:ratelimit:<ruleId>:<bucket>:<key>`（String counter + TTL）

---

## 4. Kafka 事件（Envelope + Payload）

> 旁路服务（search/message 等）的数据一致性以“最终一致”为主，事件契约是跨服务协作的边界。  
> 详细约定见方案包：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/event-contract.md`。

### 4.0 Topics（v1）

- `community.event.post.v1`：`PostPublished` / `PostUpdated` / `PostDeleted`
- `community.event.comment.v1`：`CommentCreated`
- `community.event.social.v1`：`LikeCreated` / `FollowCreated`
- `community.event.moderation.v1`：`ModerationActionApplied`

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

> 说明：LikeCreated/FollowCreated 的字段与可选项，参考 `event-contract.md` 与 `social-api/src/main/java/com/nowcoder/community/social/api/event/payload/*`。

---

## 5. Elasticsearch 索引

### discuss_post
- 索引字段（建议）：`postId`、`userId`、`title`、`content`、`type`、`status`、`createTime`、`score`、`categoryId`、`tags`
- 用途：帖子全文检索 + 高亮
- 映射策略（建议）：
  - `title/content`：text（可选 analyzer：`standard` 或 `ik_smart/ik_max_word`，取决于 ES 是否安装 IK）
  - `postId/userId/type/status/categoryId`：keyword/number
  - `createTime`：date
  - `score`：double（用于热帖/相关性排序）
  - `tags`：keyword（数组；用于按标签过滤/聚合展示）
- 高亮字段：`title`、`content`（标签建议：`<em>...</em>`）
