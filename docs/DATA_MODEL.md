# 数据模型与依赖组件（最小约定）

本文档描述本地 compose 运行所需的最小数据模型与关键依赖：MySQL、Redis、Kafka、Elasticsearch。内容以 `deploy/` 与代码常量为准，避免与历史实现产生偏差。

---

## 1. MySQL（最小表结构）

本地使用的最小 schema 位于：
- `deploy/mysql-init/001_schema.sql`
- `deploy/mysql-init/002_seed.sql`

### 1.1 主要表
- `user`：用户基础信息（迁移期与 legacy 共用）
- `discuss_post`：帖子
- `comment`：评论/回复
- `message`：私信/站内信（含 conversationId）
- `consumed_event`：消息服务消费幂等（eventId 去重）
- `search_consumed_event`：搜索服务消费幂等（eventId 去重）

### 1.2 本地种子数据
`deploy/mysql-init/002_seed.sql` 提供演示用户（仅本地开发用途）。

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

