# 数据与存储

本文档是存储事实索引，覆盖 MySQL schema、Redis key、Kafka topic、Elasticsearch alias/index、初始化脚本和本地种子数据。业务流程不在这里展开，见 [business-flows.md](business-flows.md)。

## MySQL

初始化脚本：

- `deploy/mysql/primary-init/001_create_databases.sh`：mysql-primary 首次建库和最小权限账号。
- `deploy/mysql/community/*.sql`：最小表结构和本地种子数据，覆盖 `community` 与 `im_core`。
- `deploy/mysql/community_oss/*.sql`：OSS 独立 schema 的初始化与种子数据。

schema：

- `community`：主站业务表和 shared tables。
- `community_oss`：对象元数据、版本、上传会话、授权、引用和生命周期表。
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
| `message` | 主站站内通知样例，不再是 IM 私信 SSOT |
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
| `recharge_order` | 钱包充值订单，按 `user_id + request_id` 幂等 |
| `withdraw_order` | 钱包提现订单，按 `user_id + request_id` 幂等 |
| `transfer_order` | 钱包转账订单，按 `from_user_id + request_id` 幂等 |
| `wallet_admin_action` | 钱包管理员冻结、冲正等操作记录 |
| `oss_object` / `oss_object_version` / `oss_upload_session` / `oss_usage_policy` / `oss_object_reference` / `oss_access_grant` | OSS 对象、版本、会话、策略、引用和授权事实 |
| `post_media_asset` | 帖子媒体资源 draft/uploaded/bound 状态和 OSS object/version/reference 投影 |
| `post_content_block` | 帖子正文 block，承载 paragraph/code/media block 顺序 |
| `market_listing` | 市场商品 listing |
| `market_inventory_unit` | 市场预加载库存单元 |
| `market_order` | 市场订单，保存价格、标题、地址等下单快照 |
| `market_wallet_action` | market 到 wallet 的 durable saga command，承载 escrow / release / refund 状态 |
| `market_dispute` | 市场订单争议 |
| `market_address` | 市场收货地址簿 |
| `market_delivery` | 虚拟商品交付记录 |
| `market_shipment` | 实物商品发货记录 |
| `drive_space` | 用户网盘空间 quota、used 和更新时间 |
| `drive_entry` | 网盘目录树条目，文件 / 文件夹及 ACTIVE/TRASHED/DELETED 状态 |
| `drive_upload` | 网盘上传会话和 OSS object/version/session 映射 |
| `drive_share` | 网盘分享 token、提取码 hash、过期时间和状态 |
| `drive_share_access` | 分享提取码校验访问日志 |
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
| 头像上传 ticket | 以 OSS avatar storage adapter 代码常量为准，语义为 `fileName -> userId` 短 TTL |
| OSS avatar upload owner | `user:avatar:oss-owner:<fileName>` |
| OSS avatar upload session | `user:avatar:oss-session:<fileName>` |
| OSS avatar public URL | `user:avatar:oss-public-url:<fileName>` |
| 帖子热度刷新队列 | Redis-backed `PostScoreQueue` |

analytics 主要用 Redis HyperLogLog / Bitmap：

- UV：按日期记录 HyperLogLog。
- DAU：把 UUID 映射为 analytics-only 整数 ordinal 后写入当日 Bitmap。
- 采集开关和路径由 `analytics.ingest.*` 控制；默认 include 包含 `/api/posts/**`、`/api/search/**`、`/api/messages/**`、`/api/notices/**`，exclude 包含 `/internal/**` 和 `/files/**`。

具体 key 以代码常量和配置为准。

## OSS Runtime

- `community-oss` 只负责对象 metadata、版本、授权和引用事实；blob 存储隐藏在 `ObjectStore` port 后面。
- dev 可以使用 local filesystem 或 Garage single-node。
- 生产至少 3 节点 Garage，并开启副本、健康检查和监控。
- 将来切换 Ceph RGW 时，只替换 `ObjectStore` adapter 和配置，不改业务 API。

## Kafka

IM 必需 topic：

- `im.command.private-text`
- `im.command.room-text`
- `im.event.private-persisted`
- `im.event.room-persisted`
- `im.event.private-rejected`
- `im.event.room-rejected`
- `im.event.room-member-changed`
- `im.event.user-messaging-policy-changed`
- `im.event.user-block-relation-changed`

DLQ：

- `im.command.private-text.dlq`
- `im.command.room-text.dlq`

IM policy projection 先在主站 outbox 使用内部 topic `projection.im.policy`，再由 outbox handler 发布到 `im.event.user-messaging-policy-changed` / `im.event.user-block-relation-changed` 供 `im-realtime` 消费。

`community.event.*` 是已退休跨服务 topic，当前默认 compose 不创建、不使用；`community-app` 的主站投影/通知不依赖 Kafka，而是使用本地事务事件、DB outbox 或同步 owner API。

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

搜索存储：

- `search.storage=es`：Elasticsearch 实现。
- 运行时不提供内存搜索实现；本地测试和开发也使用共享持久化依赖或显式 mock。

索引约定：

| 角色 | 名称 |
| --- | --- |
| stable alias | `community_posts_alias` |
| managed index prefix | `community_posts_v` |
| versioned index | `community_posts_vYYYYMMDDHHmmss[_n]` |

本地 compose 的 `es-init` 只等待 ES ready，不创建业务索引。运行时 `PostIndexManager` 负责创建带 mapping 的版本化索引，并将 `community_posts_alias` 指向该索引。搜索读写只通过 alias 访问。

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

## Market Wallet Action 表

`community.market_wallet_action` 是 market owner 的资金动作命令表，不是 `outbox_event` 的业务别名。它保存 market 到 wallet 的 escrow / release / refund 命令状态，支持重试、恢复和人工排查。

核心字段：

| 字段 | 含义 |
| --- | --- |
| `action_id` | command 主键 |
| `order_id` | 关联市场订单 |
| `dispute_id` | 争议相关 action 的争议 id，可为空 |
| `action_type` | `ESCROW` / `RELEASE` / `REFUND` |
| `request_id` | 钱包总账 requestId，唯一，格式为 `market-order:<orderId>:<action>` |
| `wallet_biz_id` | 钱包业务 id，当前为 `market-order:<orderId>` |
| `actor_user_id` | 钱包 action 的主要用户；escrow/refund 通常为买家，release 通常为卖家 |
| `counterparty_user_id` | 对手方用户 |
| `amount` | 资金动作金额 |
| `status` | `PENDING` / `PROCESSING` / `RETRYING` / `SUCCEEDED` / `CANCELLED` / `FAILED` / `DEAD` |
| `result_type` | `APPLIED` 或 `NOOP` |
| `wallet_txn_id` | wallet 侧已产生的交易 id |
| `failure_code` / `last_error` | 最近失败分类和错误摘要 |
| `retry_count` / `next_retry_at` | retry/backoff 状态 |
| `processing_lease_until` | processor lease 截止时间 |

索引：

```sql
unique key uk_market_wallet_action_request (request_id)
key idx_market_wallet_action_status_next (status, next_retry_at, action_id)
key idx_market_wallet_action_order_type (order_id, action_type)
```

排查口径：

- `PENDING` / `RETRYING` 长时间不动：检查 `marketWalletActionProcessor` XXL job。
- `PROCESSING` 超过 lease：检查 `marketWalletActionRecovery` 是否恢复过期 lease。
- 有 `wallet_txn_id` 但 action 非 `SUCCEEDED`：恢复任务应尝试把 wallet txn 重新应用到 market saga 状态。
- 订单处于 `ESCROW_PENDING` / `RELEASE_PENDING` / `REFUND_PENDING` / dispute pending 但没有 action：恢复任务应补写缺失 command。

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
