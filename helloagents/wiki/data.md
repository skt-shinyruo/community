# 数据模型

## 1. 概览

当前仓库以 MySQL 为主存储（用户、帖子、评论、消息），Redis 作为缓存与关系数据存储（点赞、关注、登录凭证、验证码、UV/DAU 等），并使用 Elasticsearch 存储帖子搜索索引。

---

## 2. MySQL 表（基于 MyBatis Mapper 推断）

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

### 2.5 login_ticket（Deprecated）

**Description：** 早期登录票据表，当前实现已迁移到 Redis（代码中 Mapper 标记为 `@Deprecated`）。

---

## 3. Redis Key 设计（基于代码推断）

> 具体 key 生成逻辑见 `RedisKeyUtil`。

- **登录凭证：** `ticket:<ticket>`
- **验证码：** `kaptcha:<owner>`
- **实体点赞集合：** `like:entity:<entityType>:<entityId>`（Set）
- **用户被赞计数：** `like:user:<userId>`（String counter）
- **关注列表：** `followee:<userId>:<entityType>`（ZSet）
- **粉丝列表：** `follower:<entityType>:<entityId>`（ZSet）
- **UV：** `uv:<yyyyMMdd>`（HyperLogLog）+ `uv:<start>:<end>`（union 结果）
- **DAU：** `dau:<yyyyMMdd>`（Bitmap）+ `dau:<start>:<end>`（OR 结果）
- **帖子分数刷新集合：** `post:score`（Set）

---

## 4. Elasticsearch 索引

### discuss_post
- 索引字段：`title`、`content`、`type`、`score`、`createTime` 等
- 用途：帖子全文检索 + 高亮

