# 数据与存储

本文档是存储事实索引，覆盖 MySQL schema 与迁移、Redis key、Kafka topic、Elasticsearch alias/index 和本地种子数据。业务流程不在这里展开，见 [business-flows.md](business-flows.md)。

## MySQL

数据库与账号 bootstrap：

- `deploy/mysql/primary-init/001_create_databases.sh`：mysql-primary 首次建库和最小权限账号。
- 业务 schema 不再通过 MySQL `/docker-entrypoint-initdb.d` 或 helper bootstrap 重放最终态 SQL；结构变更统一由下面三个 Flyway deployable 执行。

schema：

- `community`：主站业务表和 shared tables。
- `community_oss`：对象元数据、版本、上传会话、授权、引用和生命周期表。
- `im_core`：IM 权威消息、房间、会话、已读状态。
- `xxl_job`：XXL-JOB Admin。

最小权限账号：

- `${MYSQL_USER:-community}` -> `${MYSQL_DATABASE:-community}`：`select/insert/update/delete`。
- `${COMMUNITY_MIGRATION_USERNAME:-community_migrator}` -> `${MYSQL_DATABASE:-community}`：专用 DDL migration 账号。
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` -> `${MYSQL_DATABASE:-community}`：`select/insert/update/delete/create/alter`。
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` -> `${IM_MYSQL_DATABASE:-im_core}`：`select/insert/update/delete`。
- `${IM_MYSQL_USER:-im_core}` -> `${IM_MYSQL_DATABASE:-im_core}`：`select/insert/update/delete`。
- `${IM_MIGRATION_USERNAME:-im_core_migrator}` -> `${IM_MYSQL_DATABASE:-im_core}`：专用 DDL migration 账号。
- `${OSS_MYSQL_USER:-community_oss}` -> `${OSS_MYSQL_DATABASE:-community_oss}`：`select/insert/update/delete`。
- `${OSS_MIGRATION_USERNAME:-community_oss_migrator}` -> `${OSS_MYSQL_DATABASE:-community_oss}`：专用 DDL migration 账号。

`community-app`、`im-core` 和 `community-oss` runtime 账号只保留 DML 权限；不要把 migration 账号配置给 runtime。Mock Data Studio 是 dev-only 控制面，其额外 metadata DDL 权限不是生产业务 runtime 的授权模板。

UUID 持久化：

- Java 层统一使用 `UUID`，MySQL 侧核心表优先用 16-byte `BINARY` / `VARBINARY` 保存。
- `common-core.id.BinaryUuidCodec` 负责 UUID 与 16-byte 大端序二进制互转，非法长度会 fail fast。
- `community-app` 的 `infra.persistence.mybatis.UuidBinaryTypeHandler`、`community-oss` 的 `oss.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` 和 `im-core` 的 `im.core.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` 把 MyBatis 参数 / 结果集接到同一个 codec，避免各 owner 仓储手写 UUID byte 转换。

## Flyway Migration Deployables

每个 owner schema 都有独立、一次性运行的 migration deployable：

| Deployable | Owner schema | 默认/部署 history table | 固定 location |
| --- | --- | --- | --- |
| `community-db-migrations` | `community` | `community_schema_history` | `classpath:db/migration/community` |
| `community-oss-db-migrations` | `community_oss` | `oss_schema_history` | `classpath:db/migration/community-oss` |
| `community-im-db-migrations` | `im_core` | `im_core_schema_history` | `classpath:db/migration/im-core` |

三个可执行 JAR 的默认 action 都是 `migrate`，并支持：

- `migrate`：校验已应用记录并顺序执行待执行 migration。
- `validate`：只校验 migration history 与随包脚本，不改 schema。
- `baseline`：仅用于接管一个已经精确等于 V001、但尚无 Flyway history 的既有 schema；不是常规部署动作。

Runner 固定 `baselineVersion=1`、`baselineOnMigrate=false`、`cleanDisabled=true`、migration 命名校验和 missing-location fail-fast。`COMMUNITY_MIGRATION_LOCATIONS`、`OSS_MIGRATION_LOCATIONS`、`IM_MIGRATION_LOCATIONS` 均被显式拒绝，不能把临时目录或额外脚本注入发布。OSS/IM 的 history table override 只能等于上表值；community application 仍接受 `COMMUNITY_MIGRATION_HISTORY_TABLE`，而 single/cluster Compose 将其固定为 `community_schema_history`。

### V001 Baseline 保护

每个模块都随包提供 V001 schema manifest：

- `db/migration/community/community-schema-manifest.tsv`
- `db/migration/community-oss/community-oss-schema-manifest.tsv`
- `db/migration/im-core/im-core-schema-manifest.tsv`

`*SchemaCatalog` 从 `information_schema` 捕获实际表、列/类型/默认值、索引和约束，`*SchemaVerifier.verifyExactV001(...)` 与 manifest 做精确比较。缺表、多表或任一受管结构变化都会拒绝 baseline；不能用 baseline 掩盖漂移、跳过 V002+ 或接管未知结构。

baseline 还必须提供对应的精确确认值：

| Deployable | 环境变量 | 必须等于 |
| --- | --- | --- |
| community | `COMMUNITY_MIGRATION_BASELINE_CONFIRMATION` | `I_HAVE_VERIFIED_THE_COMMUNITY_SCHEMA` |
| OSS | `OSS_MIGRATION_BASELINE_CONFIRMATION` | `I_HAVE_VERIFIED_THE_OSS_SCHEMA` |
| IM Core | `IM_MIGRATION_BASELINE_CONFIRMATION` | `I_HAVE_VERIFIED_THE_IM_CORE_SCHEMA` |

部署拓扑先运行 migration，再让 `community-app`、`community-oss` 和 `im-core` 通过 `service_completed_successfully` 等待各自 owner migration。migration 失败时 runtime 不应绕过依赖启动；操作步骤见 [运行与排障](operations.md#database-migration-runbook)。

## community 主要表

| 表 | 说明 |
| --- | --- |
| `user` | 用户基础信息、角色、处罚状态、`security_version` 等用户事实 |
| `user_security_version_counter` | user 认证授权版本计数器，用于分配 `user.security_version` |
| `auth_refresh_token` | refresh token 状态，仅存 token hash |
| `discuss_post` | 帖子 |
| `comment` | 评论 / 回复 |
| `post_counter_snapshot` | 帖子计数快照，承载 comment / like / view / score 聚合读模型 |
| `post_score_snapshot` | 帖子热度分数快照，支撑 durable hot feed ranking |
| `notice_record` | 站内通知读模型、topic、未读状态和内容快照 |
| `report` / `moderation_action` | 举报与治理动作 |
| `social_like` / `social_follow` | 点赞与关注关系 |
| `http_idempotency` | HTTP 写接口幂等状态 |
| `user_consumed_event` | 用户侧消费去重样例 |
| `task_template` | 成长任务模板 |
| `user_task_progress` | 用户任务进度 |
| `user_task_event_log` | 任务事件去重日志 |
| `user_level_rule_config` | 用户等级规则 |
| `wallet_account` | 钱包账户 |
| `wallet_txn` | 钱包交易事实 |
| `wallet_entry` | 钱包双分录流水 |
| `recharge_order` | 钱包充值订单，按 `user_id + request_id` 幂等 |
| `withdraw_order` | 钱包提现订单，按 `user_id + request_id` 幂等 |
| `transfer_order` | 钱包转账订单，按 `from_user_id + request_id` 幂等 |
| `wallet_admin_action` | 钱包管理员冻结、冲正等操作记录 |
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

## community_oss 主要表

| 表 | 说明 |
| --- | --- |
| `oss_object` | 对象 metadata、owner context、current version、visibility 和 lifecycle |
| `oss_object_version` | 对象版本、blob key、content metadata 和版本状态 |
| `oss_upload_session` | 上传会话、claim version、过期时间和恢复状态 |
| `oss_usage_policy` | usage 级上传和访问策略 |
| `oss_object_reference` | consumer owner 对对象/版本的引用事实 |
| `oss_access_grant` | principal 对对象/版本的临时访问授权 |

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

IM 消息权威状态在 `im_core`，主站通知读模型在 `community.notice_record`。

## 本地种子数据

身份种子由 community migration deployable 的 dev-only repeatable migration 提供：

```text
backend/community-db-migrations/src/main/resources/db/dev-seed/community/R__development_seed.sql
```

默认账号：

- 普通用户：`aaa/aaa`
- 管理员：`admin/aaa`

Mock Data Studio metadata bootstrap：

- `deploy/mysql/community/011_schema_demo_metadata.sql` 定义 `demo_*` / `ai_config` 的 canonical schema。
- `tools/mock-data-studio/src/db/bootstrap.mjs` 可重复执行 canonical `CREATE TABLE IF NOT EXISTS`，并幂等创建 `Default` AI 配置。
- 开发环境不支持旧 metadata schema 原地升级；表结构不匹配时删除并重建本地数据卷。

只有 `community-db-migrations` 支持 `development-seed`。该 action 会先执行 production migrations，再使用独立的 `community_development_seed_history` 和 `classpath:db/dev-seed/community`；必须显式满足 `COMMUNITY_MIGRATION_PROFILE=development`。OSS 和 IM migration deployable 不接受 seed action。开发环境结构已漂移且不需要保留数据时，可重建本地 volume 后重新 migrate；不要在 runtime 初始化期间手工修补 schema。

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
| 找回密码请求邮箱限流 | `auth:pwdreset:req:email:<email>` |
| 找回密码请求 IP 限流 | `auth:pwdreset:req:ip:<ip>` |
| HTTP 幂等 Redis 方案 | `idem:<operation>:<userId>:<Idempotency-Key>` |
| 全站热门流 | `post:feed:global:hot` |
| 板块热门流 | `post:feed:board:hot:<boardId>` |
| 帖子摘要缓存 | `post:summary:<postId>` |
| 帖子详情缓存 | `post:detail:<postId>` |
| 帖子计数缓存 | `post:counter:<postId>` |
| 全站/板块 hot-feed 删除成员集 | `post:feed:terminal-members:{<完整 feed zset key>}`（永久，无 TTL） |
| 帖子摘要删除 fence | `post:summary:terminal:{post:summary:<postId>}`（永久，无 TTL） |
| 帖子详情删除 fence | `post:detail:terminal:{post:detail:<postId>}`（永久，无 TTL） |
| Hot-feed 投影 event | `post:feed:hot:projection:event:{<postId>}:<sourceEventId>`（TTL 7 天） |
| Hot-feed 投影 version | `post:feed:hot:projection:version:{<postId>}` |
| Hot-feed 投影 lock | `post:feed:hot:projection:lock:{<postId>}`（lease 30 秒） |
| Hot-feed 删除 tombstone | `post:feed:hot:projection:tombstone:{<postId>}`（永久，无 TTL） |

Hot-feed projection 的 BEGIN/CURRENT/COMMIT/ABORT Lua 对同一帖子使用 `{<postId>}` Redis Cluster hash tag；event key 也按帖子分区，并把 hash tag 放在 source event ID 前，因此任意 event ID 都不能把同一脚本的 key 分散到不同 slot。terminal `PostDeleted` commit 会保留 `max(currentVersion, deletionVersion)` 并写永久 tombstone；此后任何普通版本都不能恢复该帖子。event identity 仍只保留 7 天，tombstone 不设过期时间。

guard tombstone 之外，每个 Redis sink 也保留永久 fence，用于压制已经通过 `CURRENT`、随后丢失 lease 的旧 writer。feed 的普通 upsert 以 Lua 原子检查 scope member set 后再 `ZADD`，terminal deletion 以 Lua 原子 `SADD + ZREM`；删除覆盖全站 feed、事件 payload board 以及删除当时 category repository 返回的所有 board，并按 board ID 去重。summary/detail 的普通 put 以 Lua 原子检查独立 post fence 后再执行带 TTL 的 `SET`，terminal deletion 则原子写永久 fence 并删除 cache。普通 `remove/evict` 不创建、清除或缩短这些 fence。每对 feed zset/member set 以及每对 summary/detail cache/fence 都通过把完整 sink key 放入 fence key 的第一组 `{...}` 来共享 Redis Cluster slot。

这是从旧 `event:<sourceEventId>` / `version:<postId>` / `lock:<postId>` key 到 cluster-safe namespace，并从无 sink fence 到永久 sink fence 的格式切换，不做双读：把旧 guard key 加入同一 Lua 会重新引入跨 slot 操作。升级时必须先暂停并排空 hot-feed consumer，再把所有 `RedisPostFeedCache`、`RedisPostSummaryCache`、`RedisPostDetailCache` writer 实例统一停机升级，禁止新旧 guard 或新旧 cache writer 滚动混跑。环境如有删除历史，必须在恢复普通事件消费和 cache 写入前重放全部 `PostDeleted`，或执行等价投影重建，同时建立 guard tombstone、所有当前 feed scope member fence、summary fence 和 detail fence；只重建 guard tombstone 仍会留下旧 writer 复活 sink 的窗口。旧 event key 可在 7 天窗口后自然过期，旧 lock 最长约 30 秒；旧 version key 无 TTL，只能在确认没有旧实例后按旧精确前缀清理。

guard tombstone、summary fence 和 detail fence 的容量均与 deleted post 数量线性增长；feed member fence 的成员总量约为 `deleted posts x 删除时覆盖的当前 feed scopes`，其中 scopes 包含 global、payload board 和当时全部 category board（去重后）。这些 key/member 属于长期正确性状态，不得按普通缓存 TTL 或容量淘汰；category 新增后如需补齐历史删除，必须重放或重建该新 board scope 的删除成员。

analytics 主要用 Redis HyperLogLog / Bitmap：

- UV：按日期记录 HyperLogLog。
- DAU：把 UUID 映射为 analytics-only 整数 ordinal 后写入当日 Bitmap。
- 采集开关和路径由 `analytics.ingest.*` 控制；默认 include 包含 `/api/posts/**`、`/api/search/**`、`/api/notices/**`，exclude 包含 `/internal/**` 和 `/files/**`。

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
- `im.event.private-committed`
- `im.event.room-persisted`
- `im.event.room-committed`
- `im.event.private-rejected`
- `im.event.room-rejected`
- `im.event.room-member-changed`
- `im.event.user-messaging-policy-changed`
- `im.event.user-block-relation-changed`

DLQ：

- `im.command.private-text.dlq`
- `im.command.room-text.dlq`

IM policy projection 先在主站 outbox 使用内部 topic `projection.im.policy`，再由 outbox handler 发布到 `im.event.user-messaging-policy-changed` / `im.event.user-block-relation-changed` 供 `im-realtime` 消费。

IM 消息事实和发送结果使用不同 outbox event id 空间：私信事实 `im:pf:<messageId>`，群聊事实 `im:rf:<roomId>:<seq>`，私信发送结果 `im:psr:<attemptHash>`，群聊发送结果 `im:rsr:<attemptHash>`。`attemptHash` 来自 `fromUserId + requestId + clientMsgId`，用于避免事实事件和发送尝试回执互相覆盖。

`community.event.*` 是已退休跨服务 topic，当前默认 compose 不创建、不使用；`community-app` 的跨域异步协作固定使用 owner outbox topic（`eventbus.content`、`eventbus.social`、`eventbus.user`）和 owner Kafka topic（`content.events`、`social.events`、`user.events`）。只有 `projection.im.policy` 是保留的内部 projection outbox。

## 事件契约位置

通用事件协议：

- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java`
- `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/EventEnvelopeJsonParser.java`
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/UnknownEventAction.java`
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventTopicConventions.java`

owner-domain async contracts：

- `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/*`
- `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/*`
- `backend/community-app/src/main/java/com/nowcoder/community/user/contracts/event/*`

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

本地 compose 的 `es-init` 只等待 ES ready，不创建业务索引。运行时 `PostIndexManager` 会在 alias 不存在时创建带 mapping 的版本化索引，并将 `community_posts_alias` 指向该索引；如果已有 alias 的 mapping 缺少当前必需字段，启动直接失败。搜索读写只通过 alias 访问。

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

- owner eventbus：`eventbus.content`、`eventbus.social`、`eventbus.user`。
- 唯一内部 projection outbox：`projection.im.policy`，把 user policy / social block 变化发布给 `im-realtime`。

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
| `request_hash` | 必填请求语义指纹，用于拒绝同 key 不同请求 |
| `status` | `P` 或 `S` |
| `response_json` | 成功响应 JSON |
| `processing_expires_at` | PROCESSING 过期时间 |
| `success_expires_at` | SUCCESS 过期时间 |

唯一键：

```sql
unique key uk_http_idem (operation, user_id, idem_key)
```

完整执行语义见 [reliability.md](reliability.md)。
