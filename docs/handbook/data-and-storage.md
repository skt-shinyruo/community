# 数据与存储

本文档是存储事实索引，覆盖 MySQL 当前态 schema、Redis key、Kafka topic、Elasticsearch alias/index 和本地种子数据。业务流程不在这里展开，见 [business-flows.md](business-flows.md)。

## MySQL

数据库与账号 bootstrap：

- `deploy/mysql/primary-init/001_create_databases.sh`：mysql-primary 首次建库和最小权限账号。
- `deploy/mysql/primary-init/010_current_schema.sql`：三个业务 schema 的唯一当前态建表 SQL，由 MySQL entrypoint 在主库数据目录为空时执行一次。

schema：

- `community`：主站业务表和 shared tables。
- `community_oss`：对象元数据、版本、上传会话、授权、引用和生命周期表。
- `im_core`：IM 权威消息、房间、会话、已读状态。
- `xxl_job`：XXL-JOB Admin。

最小权限账号：

- `${MYSQL_USER:-community}` -> `community`：`select/insert/update/delete`。
- `${IM_MYSQL_USER:-im_core}` -> `im_core`：`select/insert/update/delete`。
- `${OSS_MYSQL_USER:-community_oss}` -> `community_oss`：`select/insert/update/delete`。
- `${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}` -> `community`、`community_oss`、`im_core`：`select/insert/update/delete`。

所有 runtime 和 Mock Data Studio 账号都只保留 DML 权限。建库建表由 MySQL entrypoint 以初始化权限完成，不提供常驻 DDL 账号。

UUID 持久化：

- Java 层统一使用 `UUID`，MySQL 侧核心表优先用 16-byte `BINARY` / `VARBINARY` 保存。
- `common-core.id.BinaryUuidCodec` 负责 UUID 与 16-byte 大端序二进制互转，非法长度会 fail fast。
- `community-app` 的 `infra.persistence.mybatis.UuidBinaryTypeHandler`、`community-oss` 的 `oss.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` 和 `im-core` 的 `im.core.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` 把 MyBatis 参数 / 结果集接到同一个 codec，避免各 owner 仓储手写 UUID byte 转换。

## 当前态 Schema 快照

`deploy/mysql/primary-init/010_current_schema.sql` 同时拥有 `community`、`community_oss`、`im_core` 三个固定名称的业务 schema。文件只保存最终 `CREATE TABLE` 定义和运行所需的引用数据，不保存结构演进过程、history table 或开发用户。必要引用数据包括分类、任务模板、OSS usage policy 和 IM version counter。

MySQL entrypoint 按文件名顺序先执行 `001_create_databases.sh`，再执行 `010_current_schema.sql`，且只在主库 `/var/lib/mysql` 为空时运行。single 只把快照挂到 `mysql`；cluster 只挂到 `mysql-primary`，初始化 DDL 和 DML 通过 GTID 复制到两个 replica。runtime 等待账号 bootstrap，cluster runtime 还等待 replication bootstrap 完成。

结构变化时直接修改快照中的最终定义，并同步受影响的 H2 `schema.sql` 测试夹具和 schema 契约。不要追加 `ALTER TABLE` 演进记录，也不要向已有 volume 手工重放快照。完成验证后使用：

```bash
./deploy/deployment.sh reset-mysql --topology single
./deploy/deployment.sh up --topology single
```

`reset-mysql` 会停止完整拓扑，并且只删除该拓扑明确命名的 MySQL primary/replica volumes；其他中间件数据卷不受影响。这一模型只适用于可丢弃并重建的环境。需要保留既有业务数据的环境必须先设计数据导出/导入或正式的前向升级方案，不能直接重放当前态 SQL。

## community 主要表

| 表 | 说明 |
| --- | --- |
| `user` | 用户基础信息、角色、处罚状态、`security_version` 等用户事实 |
| `user_security_version_counter` | user 认证授权版本计数器，用于分配 `user.security_version` |
| `auth_refresh_token` | refresh token 状态，仅存 token hash |
| `discuss_post` | 帖子主事实；`aggregate_version` 是编辑、治理和删除共用的 CAS / 事件版本，`score_version` 是派生热度的独立单调版本 |
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
| `market_listing` | 市场商品 listing；状态迁移使用行锁和 expected-status CAS |
| `market_inventory_unit` | 市场预加载库存单元 |
| `market_order` | 市场订单，保存价格、标题、地址等下单快照 |
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

身份种子由独立的 development-only SQL 提供，不进入当前态快照：

```text
deploy/mysql/community/090_seed_identity.sql
```

默认账号：

- 普通用户：`aaa/aaa`
- 管理员：`admin/aaa`

`community-dev-seed` 使用 `mysql:8.0` 客户端执行该文件。它只有在 `COMMUNITY_DEV_SEED_ENABLED=true` 且 `DEPLOYMENT_ENVIRONMENT=development` 时运行；其他环境即使误开 seed 开关也会失败关闭。`demo_*` / `ai_config` 表定义属于当前态快照，`tools/mock-data-studio/src/db/bootstrap.mjs` 只幂等写入 `Default` AI 配置，不执行 DDL。

seed 会为每个示例账号显式写入正 `policy_version` 和 `security_version`，并用 `greatest(current_version, seed_version)` 保留用户已有版本、推进两个全局版本计数器。重复执行不会让版本倒退，也不会生成 freshness 永久判 stale 的 `security_version=0` 账号。

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
| 帖子计数 legacy 基线（升级期只读） | `post:counter:<postId>` |
| 帖子计数 Cluster-safe overlay | `post:counter:{post:counter:dirty}:<postId>` |
| 帖子计数 dirty revision | `post:counter:dirty` + `post:counter:{post:counter:dirty}:sequence` |
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

guard tombstone 之外，每个 Redis sink 同时保留 terminal fence 和 aggregate-version floor，两者 TTL 都是 7 天。terminal fence 无条件拒绝普通回填，aggregate floor 保存该 sink 最小可接受的 Post `aggregateVersion`。hot-feed 和 summary 另外保存 `scoreVersion`：更大的 aggregate version 可替换当前值，同一 aggregate version 只有不小于当前 score version 的写入可更新；较小 aggregate version 即使携带更大 score version 也会被拒绝。feed upsert 与 summary put/evict 分别在同一个 Lua 中写 payload 并刷新各自的二元版本 marker，避免旧 score 在同一 aggregate version 下回填；detail 不缓存最终 score，只按 aggregate version 保护。帖子普通变更的 `remove/evict` 会删除当前 sink，将版本 floor 提升到当前值与传入值的字典序最大值并刷新 TTL；终态删除还会写 terminal fence。

feed 的删除覆盖全站 feed、事件 payload board 以及当时 category repository 返回的所有 board，并按 board ID 去重。每组 feed zset/terminal fence/version floor 都把完整 feed key 放入第一组 `{...}`；summary/detail 也把完整 cache key 放入对应 fence/floor 的第一组 `{...}`。因此每个 sink 的检查、删除和写入共享 Redis Cluster slot。

counter 写入使用 `post:counter:{post:counter:dirty}:<postId>` overlay；花括号中的 tag 与原有全局 dirty zset `post:counter:dirty` 的完整 key 相同，因此 HINCR/HSET、全局 sequence 自增和 ZADD 可在一个 Cluster Lua 中原子执行。升级期间读取把旧 `post:counter:<postId>` 当作只读基线，对 view/like/comment/bookmark 叠加 overlay，score 则优先取 overlay。dirty zset 的 score 是全局严格递增 revision；flush 只在当前 revision 仍等于读取值时用 Lua ZREM，批次读取后发生的新计数不会被旧批次误确认。该设计保留单一 dirty slot 的既有吞吐边界，但不再产生 CROSSSLOT。

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
| `aggregateVersion` | Post 内容/治理全文档版本 |
| `scoreVersion` | 派生 score 版本；同一 aggregateVersion 下只允许更高版本更新 score |
| `createTime` | 毫秒时间戳，避免日期序列化不一致 |
| `score` | 热度排序分 |

ES upsert 使用原子 Painless CAS：缺失文档的 `create` 分支先写入完整 upsert source；已有文档由更大的 `aggregateVersion` 替换全文档，聚合版本相等时只有更大的 `scoreVersion` 可以更新 `score` 和 `scoreVersion`，其余字段保持不变。更小的任一版本都 noop。`PostDeleted` 写版本化 tombstone 并保留在索引中，查询排除 `status=2`，因此迟到 score 事件不能复活删除文档。

## Outbox 表

`community.outbox_event` 是共享可靠投递表，承载：

- owner eventbus：`eventbus.content`、`eventbus.social`、`eventbus.user`。
- 唯一内部 projection outbox：`projection.im.policy`，把 user policy / social block 变化发布给 `im-realtime`。

状态语义：

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `DEAD`

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
