# 数据模型与依赖组件（最小约定）

本文档描述本地 compose 运行所需的最小数据模型与关键依赖：MySQL、Redis、Kafka、Elasticsearch。内容以 `deploy/` 与代码常量为准，避免与历史实现产生偏差。

---

## 1. MySQL（最小表结构）

本地使用的最小 schema 位于：
- `deploy/mysql-init/001_create_databases.sh`（建库 + 最小权限账号）
- `deploy/mysql-init/010_schema_identity.sql`（身份域：user 表，P0 暂留在 `community`）
- `deploy/mysql-init/020_schema_content.sql`（内容域：`community_content`）
- `deploy/mysql-init/030_schema_message.sql`（消息域：`community_message`）
- `deploy/mysql-init/040_schema_search.sql`（搜索域：`community_search`）
- `deploy/mysql-init/090_seed_identity.sql`（本地种子数据）

> P0 策略：同实例多 schema，先拆非身份域（content/message/search），降低迁移风险。

### 1.1 主要表
- `community.user`：身份域用户基础信息（P0 仍为 auth/user 共享）
- `community_content.discuss_post`：帖子
- `community_content.comment`：评论/回复
- `community_message.message`：私信/站内信（含 conversationId）
- `community_message.consumed_event`：message-service 消费幂等（eventId 去重）
- `community_search.search_consumed_event`：search-service 消费幂等（eventId 去重）

### 1.2 本地种子数据
`deploy/mysql-init/090_seed_identity.sql` 提供演示用户（仅本地开发用途）。

### 1.3 最小权限账号（P0）
mysql init 会为各服务创建独立账号（仅授权自身 schema），避免“全服务共享账号”带来的误写风险：
- content-service：`${CONTENT_DB_USER}` → `${CONTENT_DB_NAME}`
- message-service：`${MESSAGE_DB_USER}` → `${MESSAGE_DB_NAME}`
- search-service：`${SEARCH_DB_USER}` → `${SEARCH_DB_NAME}`

---

## 2. Redis（会话/验证码/限流/业务缓存）

Redis 主要用于：
- auth-service：refresh token、验证码、登录失败计数等
- gateway：限流计数器
- social-service/content-service/analytics-service：业务侧的快速存取（以各服务实现为准）

### 2.1 已知 key 前缀（按代码常量）
- refresh token：
  - `auth:refresh:<refreshToken>`
  - `auth:refresh:family:<familyId>`
- 登录失败计数（限流/风控）：
  - `auth:login:fail:ip:<ip>`
  - `auth:login:fail:user:<username>`
- 验证码：
  - `captcha:<captchaId>`
  - `captcha:fail:<captchaId>`
- 找回密码 token：
  - `auth:pwdreset:<token>`
- 网关限流：
  - `gateway:ratelimit:<ruleId>:<bucket>:<key>`

---

## 3. Kafka（事件总线）

### 3.1 Topic（v1）
本地由 `deploy/docker-compose.yml` 的 `kafka-init` 创建：
- `community.event.post.v1`
- `community.event.comment.v1`
- `community.event.social.v1`
- 对应 DLQ：
  - `community.event.post.v1.dlq`
  - `community.event.comment.v1.dlq`
  - `community.event.social.v1.dlq`

### 3.2 事件契约
事件模型位于 `common`：
- `common/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java`
- `common/src/main/java/com/nowcoder/community/common/event/EventTopics.java`
- `common/src/main/java/com/nowcoder/community/common/event/EventTypes.java`
- `common/src/main/java/com/nowcoder/community/common/event/payload/*`

---

## 4. Elasticsearch（搜索索引）

### 4.1 索引名
本地默认索引：
- `community_posts`

### 4.2 初始化
`deploy/docker-compose.yml` 的 `es-init` 会在 ES 启动后自动创建该索引（如不存在）。
