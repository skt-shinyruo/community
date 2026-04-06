# 数据模型与依赖组件（最小约定）

本文档描述本地 compose 运行所需的最小数据模型与关键依赖：MySQL、Redis、Kafka、Elasticsearch。内容以 `deploy/` 与代码常量为准，避免与历史实现产生偏差。

---

## 1. MySQL（最小表结构）

本地 compose 的 MySQL 初始化脚本位于：
- `deploy/mysql-init/001_create_databases.sh`（建库 + 最小权限账号）
- `deploy/mysql-init/010_schema.sql`（最小表结构 + 本地种子数据；覆盖 `community` + `im_core`）

> 说明：当前 modular monolith 默认使用单 schema `community`（业务表 + shared tables），IM 使用独立 schema `im_core`。

### 1.1 主要表
- `community.user`：用户基础信息
- `community.auth_refresh_token`：refresh token（仅存 token_hash）
- `community.demo_batch`：mock-data 批次元数据（sidecar，不侵入业务表）
- `community.demo_job`：批次内作业状态元数据
- `community.demo_batch_target`：批次目标集合（按实体类型/目标键建模）
- `community.demo_entity_ref`：批次生成实体引用（用于后续清理，支持单 id 与复合 key）
- `community.discuss_post`：帖子
- `community.comment`：评论/回复
- `community.message`：私信/站内信（含 conversationId）
- `community.report` / `community.moderation_action`：举报与治理动作
- `community.growth_check_in` / `community.user_task_progress`：成长任务样本
- `community.reward_account` / `community.reward_ledger` / `community.reward_grant_record` / `community.reward_item` / `community.reward_order`：奖励与兑换样本
- `community.outbox_event`：outbox（可靠事件投递）
- `im_core.im_room` / `im_core.im_room_message`：群聊（seq）
- `im_core.im_conversation` / `im_core.im_private_message`：私聊（seq）

### 1.2 本地种子数据
`deploy/mysql-init/010_schema.sql` 提供演示用户（仅本地开发用途）。

此外，`mock-data-studio` 在启动时会执行一份与 `deploy/mysql-init/010_schema.sql` 对齐的 metadata bootstrap：
- 新数据卷：由 `deploy/mysql-init/010_schema.sql` 创建 `demo_*` metadata tables
- 已存在数据卷：由 `tools/mock-data-studio/src/db/bootstrap.mjs` 使用 `CREATE TABLE IF NOT EXISTS` 补齐同样的 sidecar 表

### 1.3 最小权限账号（P0）
mysql init 会创建最小权限账号（仅授权对应 schema），避免“全服务共享账号”带来的误写风险：
- `${MYSQL_USER:-community}` → `${MYSQL_DATABASE:-community}`（`select/insert/update/delete`）
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` → `${MYSQL_DATABASE:-community}`（`select/insert/update/delete/create`；用于 studio startup bootstrap，并保留后续直接写业务表的能力）
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` → `${IM_MYSQL_DATABASE:-im_core}`（`select/insert/update/delete`；用于 studio 直接写 IM phase 样例）
- `${IM_MYSQL_USER:-im_core}` → `${IM_MYSQL_DATABASE:-im_core}`（`select/insert/update/delete`）

---

## 2. Redis（会话/验证码/限流/业务缓存）

Redis 主要用于：
- auth：refresh token、验证码、登录失败计数等
- social/content/analytics：业务侧的快速存取（以各模块实现为准）

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
---

## 3. Kafka（事件总线）

### 3.1 Topic（v1）
本地由默认 layered compose 栈（`deploy/compose.yml` + `deploy/compose.infra.yml` + `deploy/compose.runtime.yml`）中的 `kafka-init` 创建：
- IM（必需）：
  - `im.command.private_text.v1`
  - `im.command.room_text.v1`
  - `im.event.private_persisted.v1`
  - `im.event.room_persisted.v1`
  - `im.event.room_member_changed.v1`
  - 对应 DLQ：`im.command.private_text.v1.dlq`、`im.command.room_text.v1.dlq`

`community.event.*`：历史遗留的跨服务 topic（当前仓库默认不创建/不使用）；`community-app` 运行时不依赖 Kafka（投影/通知通过本地 DB outbox 实现）。

### 3.2 事件契约
事件契约分为两层（common + domain）：

1) **通用事件协议（common）：** `backend/community-app/src/main/java/com/nowcoder/community/common/event/`
- `backend/community-app/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java`
- `backend/community-app/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java`
- `backend/community-app/src/main/java/com/nowcoder/community/common/event/UnknownEventAction.java`
- `backend/community-app/src/main/java/com/nowcoder/community/common/event/EventTopicConventions.java`

2) **域事件契约（生产方域 owns semantics，当前同模块内）：**
- content：`backend/community-app/src/main/java/com/nowcoder/community/content/event/ContentEventTypes.java` + `backend/community-app/src/main/java/com/nowcoder/community/content/event/payload/*`
- social：`backend/community-app/src/main/java/com/nowcoder/community/social/event/SocialEventTypes.java` + `backend/community-app/src/main/java/com/nowcoder/community/social/event/payload/*`

---

## 4. Elasticsearch（搜索索引）

### 4.1 索引名
本地默认索引：
- `community_posts`

### 4.2 初始化
默认 layered compose 栈中的 `es-init` 会在 ES 启动后自动创建该索引（如不存在）。
