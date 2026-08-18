# 数据与存储

本文档是存储事实索引，覆盖 MySQL 业务 schema、Redis key、Kafka topic、Elasticsearch alias/index 和本地种子数据。业务流程不在这里展开，见 [business-flows.md](business-flows.md)。

## MySQL

数据库与账号 bootstrap：

- `deploy/database/business/init/001_create_databases.sh`：建库和最小权限账号 bootstrap；空卷初始化和正常启动都会幂等执行。
- `deploy/database/business/001_schema.sql`：三个业务 schema 的唯一结构定义，由 MySQL entrypoint 在主库数据目录为空时执行一次。

schema：

- `community`：主站业务表和 shared tables。
- `community_oss`：对象元数据、版本、上传会话、授权、引用和生命周期表。
- `im_core`：IM 权威消息、房间、会话、已读状态。

最小权限账号：

- `${MYSQL_USER:-community}` -> `community`：`select/insert/update/delete`。
- `${IM_MYSQL_USER:-im_core}` -> `im_core`：`select/insert/update/delete`。
- `${OSS_MYSQL_USER:-community_oss}` -> `community_oss`：`select/insert/update/delete`。
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` -> `community`、`community_oss`、`im_core`：`select/insert/update/delete`。

所有 runtime 和 Mock Data Studio 账号都只保留 DML 权限。业务表 DDL 只在空主库卷初始化时由 MySQL entrypoint 执行；应用进程不持有 DDL 凭证，也不在启动代码中补表。

UUID 持久化：

- Java 层统一使用 `UUID`，MySQL 侧核心表优先用 16-byte `BINARY` / `VARBINARY` 保存。
- `common-core.id.BinaryUuidCodec` 负责 UUID 与 16-byte 大端序二进制互转，非法长度会 fail fast。
- `community-app` 的 `infra.persistence.mybatis.UuidBinaryTypeHandler`、`community-oss` 的 `oss.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` 和 `im-core` 的 `im.core.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` 把 MyBatis 参数 / 结果集接到同一个 codec，避免各 owner 仓储手写 UUID byte 转换。

## 业务 Schema

`deploy/database/business/001_schema.sql` 同时拥有 `community`、`community_oss`、`im_core` 三个固定名称的业务 schema。文件只保存最终 `CREATE TABLE` 定义和运行所需的引用数据，不保存结构演进过程或开发用户。必要引用数据包括分类、任务模板、OSS usage policy 和 IM version counter。

MySQL entrypoint 按完整文件名顺序先执行 `001_create_databases.sh`，再执行 `001_schema.sql`，且只在主库 `/var/lib/mysql` 为空时运行。single 只把 schema 挂到 `mysql`；cluster 只挂到 `mysql-primary`，初始化 DDL 和 DML 通过 GTID 复制到两个 replica。正常启动仍会运行账号 bootstrap；development seed 在账号 bootstrap 成功后执行，cluster 还会先等待复制 bootstrap。

开发阶段只维护这一份业务 schema。结构变化直接修改最终定义，并同步适用的 H2 `schema.sql`、MyBatis fixture、契约和本文档；已有 MySQL volume 不会自动升级，必须显式 `reset-mysql` 后重建。首次出现数据必须跨应用版本保留的环境前，需要先建立正式迁移基线。

根评论 tombstone 后的回复清理由 `idx_comment_root_cleanup(root_comment_id,status,create_time,id)` 支撑，按 `(create_time,id)` 稳定顺序锁定有限行；每批提交后才能继续下一批。

`social_like.post_id` 是内容点赞的根帖引用：POST like 等于 `entity_id`，COMMENT like 从 `comment.post_id` 取得，USER like 为 `NULL`。帖子自身点赞使用既有 target 索引清理，`idx_like_post_entity_user(entity_type,post_id,entity_id,user_id)` 支撑删帖后的子评论关系根帖扫描。

`market_order.wallet_recovery_next_attempt_at` 是 pending 资金订单的持久恢复截止时间；`idx_market_order_wallet_recovery(status,wallet_recovery_next_attempt_at,order_id)` 让有限批次只扫描到期候选，避免缺失 action 或暂不可修复订单反复占满固定扫描前缀。

用户与拉黑 policy 使用 append-only owner version history；成长任务按 like relation instance/version fencing 保存 lifecycle state；收藏计数使用事务内 durable reconciliation token。同步 CLI 不再使用的 `demo_job` 和 `ai_config` 不属于业务 schema。

修改业务 schema 后使用 clean reset：

```bash
./deploy/deployment.sh reset-mysql --stack single
./deploy/deployment.sh up --stack single
```

`reset-mysql` 会停止完整拓扑，并且只删除该拓扑明确命名的 MySQL primary/replica volumes；其他中间件数据卷不受影响。当前开发期拓扑不提供保留旧业务数据的 schema 升级路径；需要保留数据时，必须先建立迁移基线，不能在已有 volume 上重放 `001_schema.sql`。

## community 主要表

| 表 | 说明 |
| --- | --- |
| `user` | 用户基础信息、角色、处罚状态、`security_version` 等用户事实 |
| `user_security_version_counter` | user 认证授权版本计数器，用于分配 `user.security_version` |
| `auth_refresh_token` | refresh token 状态，仅存 token hash |
| `auth_refresh_token_family_revocation` | refresh family 注销 marker 及其最晚有效期 |
| `auth_refresh_token_family_lock` | refresh family 持久互斥行；统一轮换、签发和注销的数据库锁序 |
| `discuss_post` | 帖子主事实；`aggregate_version` 是编辑、治理和删除共用的 CAS / 事件版本，`score_version` 是派生热度的独立单调版本 |
| `comment` | 评论 / 回复 |
| `post_counter_snapshot` | 帖子计数快照，承载 comment / like / view / score 聚合读模型 |
| `post_bookmark_counter_reconciliation` | 收藏事实与计数快照的持久对账 token；revision/pending CAS 防止旧 worker 清除并发新任务 |
| `post_score_snapshot` | 帖子热度分数快照，支撑 durable hot feed ranking |
| `notice_record` | 站内通知读模型、topic、未读状态和内容快照 |
| `notice_like_projection_state` | like/unlike 通知的 relation instance、source version 和 tombstone 状态 |
| `report` / `moderation_action` | 举报与治理动作 |
| `social_like` / `social_follow` | 点赞与关注关系；内容点赞在 `social_like.post_id` 保存根帖引用，支持删帖 fence 与有界清理 |
| `social_like_relation_version` | 每个稳定点赞关系的持久化事件序列；高位起始值隔离 legacy 时间戳版本 |
| `social_user_pair_lock` | 规范化用户对互斥行，串行化 follow/block 写入 |
| `http_idempotency` | HTTP 写接口幂等状态 |
| `user_consumed_event` | 用户侧消费去重样例 |
| `task_template` | 成长任务模板 |
| `user_task_progress` | 用户任务进度 |
| `user_task_event_log` | 任务事件去重日志 |
| `user_level_rule_config` | 用户等级规则 |
| `wallet_account` | 钱包账户 |
| `wallet_txn` | 钱包交易事实 |
| `wallet_entry` | 钱包双分录流水 |
| `wallet_test_credit_quota` | 测试积分工具的用户累计发放/销毁配额；销毁量不能超过同一用户的发放量，生产入口默认关闭 |
| `recharge_order` | 测试积分发放订单（历史兼容表名），按 `user_id + request_id` 幂等 |
| `withdraw_order` | 测试积分销毁订单（历史兼容表名），按 `user_id + request_id` 幂等 |
| `transfer_order` | 钱包转账订单，按 `from_user_id + request_id` 幂等 |
| `wallet_admin_action` | 钱包管理员冻结、冲正等操作记录 |
| `post_media_asset` | 帖子媒体资源 draft/uploaded/bound 状态和 OSS object/version/reference 投影 |
| `post_content_block` | 帖子正文 block，承载 paragraph/code/media block 顺序 |
| `market_listing` | 市场商品 listing；状态迁移使用行锁和 expected-status CAS |
| `market_inventory_unit` | 市场预加载库存单元 |
| `market_order` | 市场订单，保存价格、标题、地址等下单快照，并持久化资金恢复下一次尝试时间 |
| `market_wallet_action` | market 到 wallet 的 durable saga command，承载 escrow / release / refund 状态 |
| `market_dispute` | 市场订单争议 |
| `market_address` | 市场收货地址簿；generated active-default user key 的唯一索引约束每用户至多一个活动默认地址 |
| `market_delivery` | 虚拟商品交付记录 |
| `market_shipment` | 实物商品发货记录 |
| `drive_space` | 用户网盘空间 quota、used 和更新时间 |
| `drive_entry` | 网盘目录树条目，文件 / 文件夹及 ACTIVE/TRASHED/DELETED 状态 |
| `drive_upload` | 网盘上传会话和 OSS object/version/session 映射 |
| `drive_share` | 网盘分享 token、提取码 hash、过期时间和状态 |
| `drive_share_access` | 分享提取码校验访问日志 |
| `outbox_event` | DB outbox 可靠投递表 |
| `demo_batch` | Mock Data Studio 批次元数据 |
| `demo_batch_target` | Mock Data Studio 批次目标 |
| `demo_entity_ref` | Mock Data Studio 生成实体引用，支持后续清理 |

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

身份种子由独立的 development-only SQL 提供，不进入业务 schema：

```text
deploy/database/business/seed/090_seed_identity.sql
```

默认账号：

- 普通用户：`aaa/aaa`
- 管理员：`admin/aaa`

`community-dev-seed` 使用 `mysql:8.0` 客户端执行该文件。它只有在 `COMMUNITY_DEV_SEED_ENABLED=true` 且 `DEPLOYMENT_ENVIRONMENT=development` 时运行；其他环境即使误开 seed 开关也会失败关闭。Mock Data Studio 同步 CLI 只写批次、目标和实体引用，不在运行时执行 DDL。

seed 会为每个示例账号显式写入正 `policy_version` 和 `security_version`，并用 `greatest(current_version, seed_version)` 保留用户已有版本、推进两个全局版本计数器。重复执行不会让版本倒退，也不会生成 freshness 永久判 stale 的 `security_version=0` 账号。

## Redis

Redis 用于 session / 验证码 / 风控 / 缓存 / analytics / single-flight 等快速状态。

已知 key 前缀：

| 能力 | Key |
| --- | --- |
| refresh token | `auth:refresh:{auth-refresh}:token:<sha256>` |
| refresh family | `auth:refresh:{auth-refresh}:family:<familyId>` |
| refresh family revoked | `auth:refresh:{auth-refresh}:family-revoked:<familyId>` |
| 登录失败 IP | `auth:login:fail:ip:v2-<hmac>` |
| 登录查库前临时输入 | `auth:login:fail:input:v3-<hmac>`（trim 后原始输入以 `login-input` scope 生成，不做 Java Unicode 折叠；authoritative subject lease 获取后释放其 provisional lease） |
| 登录失败 authoritative subject | `auth:login:fail:subject:v3-<hmac>`（user owner 以 MySQL `utf8mb4_unicode_ci` `WEIGHT_STRING` scalar 生成存在性无关的 `utf8mb4_unicode_ci:v1:<digest>`，auth 再以 `login-subject` scope 生成 Redis 伪名；不存 userId） |
| 登录身份查询 / 密码检查预算 lease | `auth:login:inflight:{<完整 failure key>}:<完整 failure key>`（tokenized ZSET；与 failure String 同 slot；key TTL 覆盖最大存活 score） |
| 验证码 | `captcha:{<captchaId>}:value` |
| 验证码失败计数 | `captcha:{<captchaId>}:fail` |
| 注册验证码状态机 v2 | `auth:regcode:v2:{<userId>}`（Redis Hash） |
| 注册请求/重发原子配额 | `auth:registration:quota:{registration-quota}:<request\|resend>:<dimension>:<hmac>` |
| 找回密码 token | `auth:pwdreset:{password-reset}:token:<sha256>` |
| 找回密码 token generation | `auth:pwdreset:{password-reset}:generation:<userId>:<securityVersion>` |
| 找回密码请求邮箱限流 | `auth:pwdreset:req:email:<hmac>` |
| 找回密码请求 IP 限流 | `auth:pwdreset:req:ip:<hmac>` |
| 找回密码实际投递限流 | `auth:pwdreset:req:delivery:<hmac>` |
| 全站热门流 | `post:feed:global:hot` |
| 板块热门流 | `post:feed:board:hot:<boardId>` |
| Hot-feed 完整排序投影 | `post:feed:projection:{<完整 feed zset key>}`（72 字符 lex member：`type + score + createTime + postId`） |
| Hot-feed 投影成员索引 | `post:feed:projection-member:{<完整 feed zset key>}:<postId>`（活跃成员无 TTL，更新/删除时原子替换或删除） |
| Hot-feed 投影 epoch | `post:feed:projection-epoch:{<完整 feed zset key>}`（仅成员增删或排序 tuple 变化时原子递增；相同投影的预热/版本 floor 刷新不递增） |
| 帖子摘要缓存 | `post:summary:<postId>` |
| 帖子详情缓存 | `post:detail:<postId>` |
| 帖子计数 v2 基线/浏览增量 | `post:counter:v2:{post-counter-<00..1f>}:<postId>` |
| 帖子计数 v2 dirty revision | `post:counter:v2:{post-counter-<00..1f>}:dirty` + `post:counter:v2:{post-counter-<00..1f>}:sequence` |
| 帖子浏览去重 | `post:viewer:v2:{post-counter-<00..1f>}:<postId>:<viewerKey sha256>` |
| 全站/板块 hot-feed 删除 fence | `post:feed:terminal-members:{<完整 feed zset key>}:<postId>`（TTL 7 天） |
| 全站/板块 hot-feed aggregate-version floor | `post:feed:version-members:{<完整 feed zset key>}:<postId>`（TTL 7 天） |
| 全站/板块 hot-feed score-version floor | `post:feed:score-version-members:{<完整 feed zset key>}:<postId>`（TTL 7 天） |
| 帖子摘要删除 fence | `post:summary:terminal:{post:summary:<postId>}`（TTL 7 天） |
| 帖子摘要 aggregate-version floor | `post:summary:version:{post:summary:<postId>}`（TTL 7 天） |
| 帖子摘要 score-version floor | `post:summary:score-version:{post:summary:<postId>}`（TTL 7 天） |
| 帖子详情删除 fence | `post:detail:terminal:{post:detail:<postId>}`（TTL 7 天） |
| 帖子详情 aggregate-version floor | `post:detail:version:{post:detail:<postId>}`（TTL 7 天） |
| Hot-feed 投影 event | `post:feed:hot:projection:event:{<postId>}:<sourceEventId>`（TTL 7 天） |
| Hot-feed 投影 Post version | `post:feed:hot:projection:version:post:{<postId>}`（TTL 7 天，随最近提交刷新） |
| Hot-feed 投影 lock | `post:feed:hot:projection:lock:{<postId>}`（lease 30 秒） |
| Hot-feed 删除 tombstone | `post:feed:hot:projection:tombstone:{<postId>}`（TTL 7 天） |

Hot-feed projection 的 BEGIN/CURRENT/COMMIT/ABORT Lua 对同一帖子使用 `{<postId>}` Redis Cluster hash tag；event key 也按帖子分区，并把 hash tag 放在 source event ID 前，因此任意 event ID 都不能把同一脚本的 key 分散到不同 slot。新 Post event 与携带 `postAggregateVersion` 的 comment event 共享 `post` lane，并以 Post aggregate version 单调判旧。切换前已排队且 payload 没有正 `aggregateVersion` 的 Post event 进入 `legacy-post` lane；缺少 `postAggregateVersion` 的 legacy comment 进入 `comment` lane，social 进入 `social` lane。这三类事件的时间戳版本只做元数据校验，不充当水位；它们按 event ID 去重并在每帖锁内回源当前事实重算，避免节点时钟回拨永久丢失有效重算。terminal `PostDeleted` commit 会保留 Post lane 的 `max(currentVersion, deletionVersion)`，并写 7 天 tombstone；event identity 同样保留 7 天。

注册验证码 v2 Hash 使用 `auth:regcode:v2:{<userId>}`，保存 code、delivery ID、失败次数、状态和 replacement/verification lease。失败达到上限后会清除 code 与 delivery ID，保留 `EXHAUSTED` 冷却墓碑，避免删除 key 后立即重发绕过 cooldown。

guard tombstone 之外，每个 Redis sink 同时保留 terminal fence 和 aggregate-version floor，两者 TTL 都是 7 天。terminal fence 无条件拒绝普通回填，aggregate floor 保存该 sink 最小可接受的 Post `aggregateVersion`。hot-feed 和 summary 另外保存 `scoreVersion`：更大的 aggregate version 可替换当前值，同一 aggregate version 只有不小于当前 score version 的写入可更新；较小 aggregate version 即使携带更大 score version 也会被拒绝。feed upsert 与 summary put/evict 分别在同一个 Lua 中写 payload 并刷新各自的二元版本 marker，避免旧 score 在同一 aggregate version 下回填；detail 不缓存最终 score，只按 aggregate version 保护。帖子普通变更的 `remove/evict` 会删除当前 sink，将版本 floor 提升到当前值与传入值的字典序最大值并刷新 TTL；终态删除还会写 terminal fence。

feed 的删除覆盖全站 feed、事件 payload board 以及当时 category repository 返回的所有 board，并按 board ID 去重。每组 legacy feed zset、完整排序 zset、成员索引、epoch、terminal fence 和 version floor 都把完整 feed key 放入第一组 `{...}`；一次 Lua 写入会删除旧排序成员、写入新成员并推进 epoch。成员索引在帖子仍位于该 scope 时不能过期，否则后续 rank 更新无法删除旧 lex member；显式 remove/terminal remove 会连同索引一起删除。summary/detail 也把完整 cache key 放入对应 fence/floor 的第一组 `{...}`，因此每个 sink 的检查、删除和写入共享 Redis Cluster slot。

counter v2 按 `postId.hashCode()` 分成 32 个 Redis Cluster slot；每个 slot 内的 counter hash、viewer 去重 key、dirty zset 和 sequence 共用 `{post-counter-<00..1f>}` hash tag，因此浏览去重、浏览增量与 dirty revision 可在同一 Lua 中原子完成。点赞、评论、收藏和 score 均以 owner 数据库为事实源：写路径只标记 dirty，读取/flush 时回源重建，不再把乱序到达的绝对值或增量当作事实。首次初始化会原子清理初始化前的派生 overlay，防止已包含新事实的数据库基线再次叠加；持久 snapshot 不可读时禁止写入已初始化标记。若 `initialized` 或 `base*` 损坏，修复脚本把 `deltaViewCount` 原子移入内部 `recoveryViewDelta`，恢复持久基线后再移回增量字段；该内部字段存在期间不得作为零基线完成初始化。

dirty zset 的 score 是分片内严格递增 revision。flush 把该 revision 与快照一起持久化到 `post_counter_snapshot.flush_revision` 和 `post_score_snapshot.flush_revision`，MySQL upsert 只接受更大 revision；因此多实例中迟到的旧 flush 无法覆盖新快照。Redis 初始化会用持久化 revision 抬高本分片 sequence，确保重建后的新修改仍能越过数据库水位。flush 对 Redis 和 owner 事实源使用严格读取，任一来源失败都不落库、不确认 dirty；仅在 dirty revision 仍等于读取值时用 Lua 确认，批次读取后的新修改会留给下一轮。扫描先轮询 32 个分片，再把空分片让出的额度按活跃分片重新分配；队头不可解析的 UUID、落入错误分片的 UUID，以及非正数、非整数或非有限 revision score 会被删除并立即补位。

帖子编辑/治理/删除事务除了写 owner outbox，还注册本域 after-commit callback：更新立即删除 feed / summary / detail cache，删除立即执行 terminal eviction，不依赖 Kafka 回环才开始失效。评论创建、编辑和删除也在同一事务内通过 `incrementActiveCommentCount` 推进 `discuss_post.aggregate_version`，提交后按该版本失效同一组读模型；因此评论变更不会与删帖产生可提交的混合版本。callback 失败按缓存 fail-open 记录日志，后续 Kafka 投影继续追平；因此 Redis 仍是派生状态，不是删除事实的唯一存储。

所有删除 tombstone/fence 和版本 floor 都是有界保护，不是永久正确性数据库；容量与最近 7 天的变更、删除、摘要回源量及覆盖 feed scope 数近似线性相关。运行约束是 Kafka/outbox 重放延迟和可能恢复执行的旧 writer 都必须小于 7 天。超过窗口的历史事件不得直接恢复 cache writer，必须先回源 Post owner 当前事实并重建投影，或重放当前删除事实建立新 fence。

### 历史永久 key 迁移

旧版本使用无 TTL 的 feed 删除成员 Set `post:feed:terminal-members:{<完整 feed zset key>}`，每个 Set member 是一个 `postId`；summary/detail terminal fence 和 projection tombstone 与新版本同名，但也是永久 string。旧的单 lane projection version `post:feed:hot:projection:version:{<postId>}` 同样无 TTL，新版本不再读取它。新 writer 不双读旧 feed Set，因此不得直接删除这些 Set。

升级按以下顺序执行：

1. 暂停入口流量并停止全部 `community-app` 实例，确认 Kafka listener、outbox handler、预热任务和请求回源都不再写上述 key。不允许新旧 guard/cache writer 滚动混跑。
2. 保留 Redis 快照，并确认待恢复的 Kafka/outbox 积压能在 7 天内处理完。如果不能满足该上限，先从 Post owner 当前事实重建投影，不得直接恢复历史 cache writer。
3. 在 standalone Redis 执行一次下面脚本；Redis Cluster 要把 `COMMUNITY_REDIS_URL` 依次指向每个 primary 并各执行一次，因为 `SCAN` 只遍历当前节点。脚本先把旧 feed Set 的每个 member 展开为新的 per-post string fence，成功后才给旧 Set 设置 7 天 TTL；同名的历史 string 也从迁移时刻起保留 7 天。

```bash
export COMMUNITY_REDIS_URL='redis://<host>:<port>/<db>'

redis_cmd() {
  redis-cli -c --no-auth-warning -u "${COMMUNITY_REDIS_URL:?COMMUNITY_REDIS_URL is required}" --raw "$@"
}

fence_ttl_seconds=604800

while IFS= read -r legacy_feed_key; do
  legacy_feed_type="$(redis_cmd TYPE "$legacy_feed_key")"
  if [[ "$legacy_feed_type" == "set" ]]; then
    while IFS= read -r post_id; do
      [[ -n "$post_id" ]] || continue
      redis_cmd SET "${legacy_feed_key}:${post_id}" 1 EX "$fence_ttl_seconds" >/dev/null
    done < <(redis_cmd SMEMBERS "$legacy_feed_key")
    redis_cmd EXPIRE "$legacy_feed_key" "$fence_ttl_seconds" >/dev/null
  elif [[ "$legacy_feed_type" == "string" ]]; then
    redis_cmd EXPIRE "$legacy_feed_key" "$fence_ttl_seconds" >/dev/null
  elif [[ "$legacy_feed_type" != "none" ]]; then
    echo "unexpected Redis type: key=$legacy_feed_key type=$legacy_feed_type" >&2
    exit 1
  fi
done < <(redis_cmd --scan --pattern 'post:feed:terminal-members:*')

for key_pattern in \
  'post:summary:terminal:*' \
  'post:detail:terminal:*' \
  'post:feed:hot:projection:tombstone:*' \
  'post:feed:hot:projection:version:{*}'
do
  while IFS= read -r bounded_key; do
    bounded_type="$(redis_cmd TYPE "$bounded_key")"
    if [[ "$bounded_type" == "string" ]]; then
      redis_cmd EXPIRE "$bounded_key" "$fence_ttl_seconds" >/dev/null
    elif [[ "$bounded_type" != "none" ]]; then
      echo "unexpected Redis type: key=$bounded_key type=$bounded_type" >&2
      exit 1
    fi
  done < <(redis_cmd --scan --pattern "$key_pattern")
done
```

4. 在每个 primary 上用 `SCAN`（不要用生产环境 `KEYS`）重新遍历上述五个前缀：旧 feed Set、新 feed per-post fence、summary/detail fence、projection tombstone 和旧单 lane version 的 `TTL` 都必须在 `1..604800` 秒内，不得再出现 `TTL=-1`；每个旧 feed Set member 都必须存在对应的 `${legacyFeedKey}:${postId}` string fence。不满足时不得启动新 writer。
5. 统一启动新版本实例并恢复消费。受控执行一次帖子变更并等待热度投影后，确认 global/board feed、summary 和 detail 的 aggregate floor 等于本次提交后的 `discuss_post.aggregate_version`，hot-feed 与 summary 的 score floor 等于投影使用的 `score_version`，且 TTL 都在 `1..604800` 秒内；用字典序更小的 `(aggregateVersion, scoreVersion)` 受控回放不得恢复已删除的 zset member 或 summary payload，更小 aggregate version 的回放不得恢复 detail payload。

旧 feed Set 和旧单 lane version 在 7 天后自然过期，无需在切换窗口内手工 `DEL`。

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

owner-domain async contracts：

- `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/*`
- `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/*`
- `backend/community-app/src/main/java/com/nowcoder/community/user/contracts/event/*`

同步协作模型位于各 owner-domain `api.model`，不复用 `contracts.event`。

## Elasticsearch

搜索固定使用 Elasticsearch；运行时不提供内存搜索实现，本地测试和开发也使用共享持久化依赖或显式 mock。

索引约定：

| 角色 | 名称 |
| --- | --- |
| stable alias | `community_posts_alias` |
| managed index prefix | `community_posts_v` |
| versioned index | `community_posts_vYYYYMMDDHHmmss[_n]` |

本地 compose 的 `es-init` 只等待 ES ready，不创建业务索引。运行时 `PostIndexManager` 会在 alias 不存在时创建带 mapping 的版本化索引，并将 `community_posts_alias` 指向该索引；如果已有 alias 的 mapping 缺少当前必需字段，启动直接失败。搜索读写只通过 alias 访问。

Compose 中 Elasticsearch 与 Kibana 共用 `ELASTIC_STACK_VERSION`，当前固定为 `9.2.8`，与 Spring Data
Elasticsearch 6.0 / Elasticsearch Java Client 9.2 系列保持同一主次版本。不得只升级客户端、Elasticsearch
或 Kibana 中的一项。已有 8.x 数据卷不能直接挂载到 9.x；保留数据时必须按 Elastic 官方升级路径先升级到
最新 8.19、完成 Upgrade Assistant 和快照，再进入 9.x。可丢弃的本地搜索数据应在确认具体 Elasticsearch
volume 后使用新卷，并通过数据库事实源和 outbox/reindex 重建业务索引。

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
| `aggregateVersion` | Post 内容/治理全文档版本 |
| `scoreVersion` | 派生 score 版本；同一 aggregateVersion 下只允许更高版本更新 score |
| `createTime` | 毫秒时间戳，避免日期序列化不一致 |
| `score` | 热度排序分 |

ES upsert 使用原子 Painless CAS：缺失文档的 `create` 分支先写入完整 upsert source；已有文档由更大的 `aggregateVersion` 替换全文档，聚合版本相等时只有更大的 `scoreVersion` 可以更新 `score` 和 `scoreVersion`，其余字段保持不变。更小的任一版本都 noop。`PostDeleted` 写版本化 tombstone 并保留在索引中，查询排除 `status=2`，因此迟到 score 事件不能复活删除文档。

## Outbox 表

`community.outbox_event` 是共享可靠投递表，承载：

- owner eventbus：`eventbus.content`、`eventbus.social`、`eventbus.user`。
- 唯一内部 projection outbox：`projection.im.policy`，把 user policy / social block 变化发布给 `im-realtime`。
- 认证副作用：`auth.password-reset-mail`；payload 只有 delivery ID、derivation key ID、收件地址和过期时间，不保存 bearer token。
- 注册邮件：`auth.registration-code-mail`；payload 保存稳定 delivery/registration/lease 元数据、收件地址、短期验证码和过期时间。worker 发送前回查 Redis fencing；成功后 outbox 原子清空 payload。

状态语义：

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `DEAD`

`SUCCEEDED` 转换在同一条 fenced UPDATE 中把 `payload` 清为空串，缩短邮箱等投递数据的保留时间；`PENDING`、`PROCESSING`、`DEAD` 必须保留 payload 以便重试或人工 replay。

worker 先查询 due candidate，再以 `id + PENDING + next_retry_at <= pollNow` 条件更新原子认领；认领成功后按 `id + PROCESSING + lease_token` 回读当前 row，handler 和重试决策只使用该新鲜快照。多实例同时轮询时，旧 candidate 因此不能绕过新的 backoff，也不能在 `DEAD -> PENDING` 原位恢复后沿用恢复前的 retry count。

content media command publisher 使用确定性 event ID；若 enqueue 遇到唯一键冲突，会尝试把原 row 从 `DEAD` 原位重排为 `PENDING`，并清空 retry、lease 和旧错误。只有实际 insert 或 `DEAD -> PENDING` 成功才算重新调度；`PENDING`、`PROCESSING`、`SUCCEEDED` 不会被这种自动恢复覆盖。其他 outbox producer 不自动获得该语义。

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

- `PENDING` / `RETRYING` 长时间不动：检查 `MarketWalletActionProcessorScheduler` 和应用日志。
- `PROCESSING` 超过 lease：检查 `MarketWalletActionRecoveryScheduler` 是否恢复过期 lease。
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
