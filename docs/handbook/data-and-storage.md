# 数据与存储

本文档是存储事实索引，覆盖 MySQL schema、Redis key、Kafka topic、Elasticsearch alias/index、初始化脚本和本地种子数据。业务流程不在这里展开，见 [business-flows.md](business-flows.md)。

## MySQL

初始化脚本：

- `deploy/mysql/primary-init/001_create_databases.sh`：mysql-primary 首次建库和最小权限账号。
- `deploy/mysql/community/*.sql`：最小表结构和本地种子数据，覆盖 `community` 与 `im_core`。

schema：

- `community`：主站业务表和 shared tables。
- `im_core`：IM 权威消息、房间、会话、已读状态。
- `xxl_job`：XXL-JOB Admin。

最小权限账号：

- `${MYSQL_USER:-community}` -> `${MYSQL_DATABASE:-community}`：`select/insert/update/delete`。
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` -> `${MYSQL_DATABASE:-community}`：`select/insert/update/delete/create/alter`。
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` -> `${IM_MYSQL_DATABASE:-im_core}`：`select/insert/update/delete`。
- `${IM_MYSQL_USER:-im_core}` -> `${IM_MYSQL_DATABASE:-im_core}`：`select/insert/update/delete`。

## community 主要表

| 表 | 说明 |
| --- | --- |
| `user` | 用户基础信息、角色、处罚状态等用户事实 |
| `auth_refresh_token` | refresh token 状态，仅存 token hash |
| `discuss_post` | 帖子 |
| `comment` | 评论 / 回复 |
| `message` | 主站站内通知与 legacy/demo message 样例，不再是 IM 私信 SSOT |
| `report` / `moderation_action` | 举报与治理动作 |
| `social_like` / `social_follow` | 点赞与关注关系 |
| `http_idempotency` | HTTP 写接口幂等状态 |
| `user_consumed_event` | 用户侧消费去重样例 |
| `task_template` | 成长任务模板 |
| `user_task_progress` | 用户任务进度 |
| `user_task_event_log` | 任务事件去重日志 |
| `user_level_rule_config` | 用户等级规则 |
| `reward_account` / `reward_ledger` / `reward_grant_record` | 历史 reward 样本表；在线余额事实已收敛到 wallet |
| `wallet_account` | 钱包账户 |
| `wallet_txn` | 钱包交易事实 |
| `wallet_entry` | 钱包双分录流水 |
| `outbox_event` | DB outbox 可靠投递表 |
| `demo_batch` | Mock Data Studio 批次元数据 |
| `demo_job` | Mock Data Studio 批次内作业状态 |
| `demo_batch_target` | Mock Data Studio 批次目标 |
| `demo_entity_ref` | Mock Data Studio 生成实体引用，支持后续清理 |
| `ai_config` | Mock Data Studio AI 配置元数据 |

## im_core 主要表

| 表 | 说明 |
| --- | --- |
| `im_room` | 群聊房间 |
| `im_room_member` | 房间成员 |
| `im_room_message` | 群消息，按 room seq 排序 |
| `im_room_read_state` | 群聊已读水位 |
| `im_conversation` | 私信会话 |
| `im_private_message` | 私信消息，按 conversation seq 排序 |
| `im_conversation_read_state` | 私信已读水位 |

IM 消息权威状态在 `im_core`，不是 `community.message`。

## 本地种子数据

身份种子：

```text
deploy/mysql/community/090_seed_identity.sql
```

默认账号：

- 普通用户：`aaa/aaa`
- 管理员：`admin/aaa`

Mock Data Studio metadata bootstrap：

- 新数据卷：`deploy/mysql/community/011_schema_demo_metadata.sql` 创建 `demo_*` / `ai_config` 表。
- 已存在数据卷：`tools/mock-data-studio/src/db/bootstrap.mjs` 用 `CREATE TABLE IF NOT EXISTS` 补齐 metadata 表。

## Redis

Redis 用于 session / 验证码 / 风控 / 缓存 / analytics / single-flight 等快速状态。

已知 key 前缀：

| 能力 | Key |
| --- | --- |
| refresh token | `auth:refresh:<refreshToken>` |
| refresh family | `auth:refresh:family:<familyId>` |
| refresh family revoked | `auth:refresh:family:revoked:<familyId>` |
| 登录失败 IP | `auth:login:fail:ip:<ip>` |
| 登录失败用户 | `auth:login:fail:user:<username>` |
| 验证码 | `captcha:<captchaId>` |
| 验证码失败计数 | `captcha:fail:<captchaId>` |
| 找回密码 | `auth:pwdreset:<token>` |
| HTTP 幂等 Redis 方案 | `idem:<operation>:<userId>:<Idempotency-Key>` |
| search reindex single-flight | `sf:task:search:reindex` |

analytics 主要用 Redis HyperLogLog / Bitmap：

- UV：按日期记录 HyperLogLog。
- DAU：把 UUID 映射为 analytics-only 整数 ordinal 后写入当日 Bitmap。

具体 key 以代码常量和配置为准。

## Kafka

IM 必需 topic：

- `im.command.private_text.v1`
- `im.command.room_text.v1`
- `im.event.private_persisted.v1`
- `im.event.room_persisted.v1`
- `im.event.private_rejected.v1`
- `im.event.room_rejected.v1`
- `im.event.room_member_changed.v1`

DLQ：

- `im.command.private_text.v1.dlq`
- `im.command.room_text.v1.dlq`

IM policy projection 还会通过主站 outbox 发布用户处罚 / 拉黑变化到 IM realtime 消费的 policy topic，topic 名以代码常量为准。

`community.event.*` 是历史遗留跨服务 topic，当前默认 compose 不创建、不使用；`community-app` 的主站投影/通知不依赖 Kafka，而是使用本地事务事件、DB outbox 或同步 owner API。

## 事件契约位置

通用事件协议：

- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java`
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java`
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/UnknownEventAction.java`
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventTopicConventions.java`

owner-domain async contracts：

- `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/*`
- `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/*`

同步协作模型位于各 owner-domain `api.model`，不复用 `contracts.event`。

## Elasticsearch

搜索存储支持可插拔实现：

- `search.storage=es`：Elasticsearch 实现。
- `search.storage=memory`：内存实现，用于本地调试和测试，不需要 ES。

索引约定：

| 角色 | 名称 |
| --- | --- |
| legacy index | `community_posts` |
| stable alias | `community_posts_alias` |
| managed index prefix | `community_posts_v` |
| versioned index | `community_posts_vYYYYMMDDHHmmss[_n]` |

本地 compose 的 `es-init` 首次会创建 legacy `community_posts`。运行时 `PostIndexManager` 会优先把 alias 初始化到 legacy index，后续 reindex 再切换到版本化索引。

ES 文档 `EsPostDocument` 字段：

| 字段 | 说明 |
| --- | --- |
| `postId` | UUID 文本，同时作为 ES document ID |
| `userId` | 发布用户 UUID 文本 |
| `categoryId` | 分类 UUID 文本 |
| `tags` | keyword 精确匹配 |
| `title` | 分词检索 |
| `content` | 分词检索 |
| `type` | 帖子类型 |
| `status` | 状态标记 |
| `createTime` | 毫秒时间戳，避免日期序列化不一致 |
| `score` | 热度排序分 |

Reindex 配置：

```yaml
search:
  storage: es
  reindex:
    lock-ttl: 30m
  index:
    prefix: community_posts_v
    keep-history: 2
  post-scan:
    page-size: 500
```

## Outbox 表

`community.outbox_event` 是共享可靠投递表，承载：

- search post projection：topic `projection.search.post`。
- IM policy projection：user punishment / social block 变化发布给 `im-realtime`。

状态语义：

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `DEAD`

完整投递语义见 [reliability.md](reliability.md)。

## HTTP 幂等表

`community.http_idempotency` 支持 DB 版 HTTP 写接口幂等。

核心字段：

| 字段 | 含义 |
| --- | --- |
| `operation` | 服务端内部操作名 |
| `user_id` | 当前用户 ID |
| `idem_key` | 客户端幂等 key |
| `request_hash` | 可选请求语义指纹 |
| `status` | `P` 或 `S` |
| `response_json` | 成功响应 JSON |
| `processing_expires_at` | PROCESSING 过期时间 |
| `success_expires_at` | SUCCESS 过期时间 |

唯一键：

```sql
unique key uk_http_idem (operation, user_id, idem_key)
```

完整执行语义见 [reliability.md](reliability.md)。
