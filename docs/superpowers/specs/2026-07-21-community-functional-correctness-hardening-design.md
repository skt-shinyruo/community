# Community 功能正确性加固设计

- 日期：2026-07-21
- 状态：设计已批准
- 范围：代码审计确认的 8 个安全性、完整性、并发、分页和事务一致性问题
- 选定方案：复用现有 owner-domain、lease fencing、after-commit 和 forward-only migration 模式，在各业务边界内分批修复

## 1. 背景

本次代码审计确认以下八个缺陷：

1. IM session ticket 与 access token 共用签名密钥和 issuer；access-token 验证器不区分 token 类型，使 session ticket 可以申请新 session，并可能访问普通认证接口。
2. Drive prepare 把非空 checksum 写入 OSS session，但 complete 从 multipart 组装空 checksum，导致 OSS 的严格一致性检查必然失败。
3. `market_wallet_action` 只有处理期限，没有 fencing token；租约过期并被重新认领后，旧 worker 仍可覆盖新 worker 的状态。
4. Drive 分享访问审计直接存储 `IP|User-Agent`，用户可控内容可能超过 `varchar(128)`，使正确提取码也返回服务端错误。
5. 私信详情固定查询 `afterSeq=0, limit=50`，只展示最早 50 条；会话列表固定只取第一页 20 条。
6. 评论 cursor 只封装 `page/size`，底层仍使用 offset pagination；并发插入或删除会产生重复或遗漏。
7. 评论允许 2000 字符，通知把完整评论序列化为 JSON 后写入 `varchar(4000)`；JSON 转义可能放大内容并导致插入失败。
8. 评论 MySQL 事务内同步修改 Redis counter；数据库回滚不能撤销缓存写入，Redis 故障也可能让已提交或本应提交的评论请求表现为失败。

这些问题跨越 `community-common-security`、`community-im-gateway`、`im-realtime`、`im-core`、`community-app`、`community-oss`、`community-oss-client`、前端和 Community Flyway migration。修复必须遵循仓库的严格 DDD Tactical Layering，不引入新的通用凭证、租约或游标平台。

## 2. 目标

1. 使 access token 与 IM session ticket 在密码学和语义上相互隔离。
2. 保证 Drive prepare 与 complete 使用同一个服务端权威 checksum，并避免原始访问指纹落库。
3. 保证钱包动作旧 worker 在租约被重新认领后不能写入任何处理终态。
4. 让私信详情和会话列表可以完整、稳定地分页浏览。
5. 把评论列表改为真正的 keyset pagination。
6. 让合法长度的评论始终可以生成通知，且通知只携带有界预览。
7. 保证评论事务只提交数据库权威状态，Redis 故障和数据库回滚不会制造跨存储不一致。
8. 使用只向前迁移、兼容接口和确定性竞态测试交付全部修复。

## 3. 非目标

- 不重写现有 IM、Drive、Market、Content 或 Notice 领域。
- 不引入分布式事务、工作流引擎或新的通用 credential/lease/cursor 框架。
- 不改变普通 access token 的现有登录会话兼容性。
- 不承诺旧 IM session ticket 兼容；发布后旧 ticket 立即失效。
- 不修改任何已执行的 Flyway migration 或冻结的 version-one schema manifest。
- 不把本次修复扩展为所有列表接口的分页重构。
- 不把 Redis counter 提升为评论数量的权威来源；MySQL 仍是评论数量 SSOT。

## 4. 架构边界

`community-app` 内所有被触及的业务路径继续遵循：

```text
Controller / Listener / Handler / Bridge / Enqueuer / Job
  -> same-domain *ApplicationService
      -> domain model / domain service / repository interface / domain event
      -> synchronous foreign owner api.query / api.action
      -> asynchronous owner contracts.event
          -> infrastructure implementation
```

具体约束如下：

- Controller 只提取认证身份、绑定 HTTP 输入并转换 DTO；它只能调用同域 `*ApplicationService`。
- ApplicationService 负责事务、命令、游标解析、after-commit 注册、跨域 API 调用和结果装配。
- Domain 持有状态转换和业务不变量，不依赖 Spring、HTTP、MyBatis、Redis、application 或 `api.*`。
- MyBatis mapper 和 data object 仅位于 `infrastructure.persistence`。
- Drive 到 OSS 的同步协作继续通过 application-owned `DriveObjectStoragePort` 和其 OSS adapter。
- Market 到 Wallet 的同步协作继续通过 wallet owner-domain `api.action`。
- Content 到 Notice 的异步协作继续通过 Content domain event、owner contract event 和 Notice listener/application boundary。
- 不增加 legacy `service`、`entity`、root `mapper`、`UseCase` 或同域 `api.*` 入口。

## 5. IM 凭证边界

### 5.1 两类凭证

普通 access token 继续使用：

- `security.jwt.hmac-secret`
- `security.jwt.issuer`
- 现有 access-token TTL 和 claims

IM session ticket 使用独立配置：

```text
im.session-ticket.hmac-secret = IM_SESSION_TICKET_HMAC_SECRET
im.session-ticket.issuer      = IM_SESSION_TICKET_ISSUER (default: community-im-gateway)
im.session-ticket.audience    = IM_SESSION_TICKET_AUDIENCE (default: im-realtime)
```

Gateway 和 Realtime 的启动校验要求：

- ticket secret 至少 32 字节；
- ticket secret 不得等于全局 JWT secret；
- issuer 和 audience 必须为非空规范值；
- 缺少配置时 fail closed。

### 5.2 签发与验证

打开 session 的数据流为：

```text
Bearer access token
  -> Gateway access JwtDecoder
  -> JwtVerifier verifies user subject and rejects typ=im-session-ticket
  -> select realtime worker
  -> dedicated SessionTicketCodec signs ticket
```

ticket 至少包含：

- `iss=community-im-gateway`
- `aud=im-realtime`
- `typ=im-session-ticket`
- `sub=<user UUID>`
- `sid=<session ID>`
- `wid=<worker ID>`
- `iat` 和 `exp`

Gateway WebSocket bridge 和 Realtime worker 都使用专用 decoder 校验 signature、issuer、audience、type 和有效期，不回退到全局 access-token decoder，也不接受旧密钥。

共享 access-token decoder 增加 credential-confusion guard：即使 token 仍由旧全局密钥正确签名，只要 `typ=im-session-ticket` 就拒绝。普通旧 access token 当前没有 `typ`，仍保持兼容。

### 5.3 错误语义

- 打开 session 时缺少或无效 access token 返回 `401`。
- ticket 不能作为 bearer token 再次打开 session。
- WebSocket ticket 验证失败沿用现有认证失败 frame/close 语义。
- 对外不区分错误密钥、issuer、audience、type 或过期原因，详细原因只进入安全日志和指标。

## 6. Drive 完整性与分享指纹

### 6.1 服务端权威 checksum

`DriveUpload` 聚合增加规范化的 `checksumSha256`。prepare 顺序为：

1. ApplicationService 规范化请求 checksum。
2. 使用该值调用 OSS prepare。
3. OSS 返回有效 session/object/version 后，用同一个值创建并持久化 `DriveUpload`。

complete 顺序为：

1. 加载并 claim `DriveUpload`。
2. 验证上传者、文件长度、配额、父目录和重名约束。
3. 从已持久化聚合读取 checksum。
4. 把该 checksum 传给 `DriveObjectStoragePort.completeUpload`。
5. multipart command 中的 checksum 不参与权威决策；可以从内部 content command 中删除，或明确忽略。

迁移前活跃 upload row 的新列为默认空字符串。`STAGED` object metadata 不包含 upload session 的预期 checksum，因此不增加一个跨域查询接口来暴露 session 内部状态。兼容逻辑留在 OSS owner 内部：internal complete 加载并授权 upload session 后，如果调用方 checksum 为空，就使用该 session 的 `expectedChecksumSha256` 作为有效 checksum；调用方提供非空但不一致的值时仍拒绝。普通用户 OSS complete 保持原有严格匹配语义。新建 Drive row 始终提交持久化 checksum，不依赖该兼容分支。

### 6.2 有界分享指纹

`DrivePublicShareController` 在 DTO 转换阶段读取 socket remote address 和 User-Agent，按明确分隔格式组合后计算 SHA-256，并只把 64 字符小写十六进制摘要放入 `VerifyDriveShareCommand`。

Application 和 Repository 只接收不透明摘要：

- 原始 IP 和 User-Agent 不进入 domain、application persistence command 或数据库；
- 任意允许的 Header 长度都得到固定 64 字符结果；
- `drive_share_access.visitor_fingerprint varchar(128)` 无需迁移；
- 指纹继续可用于同一访问来源的审计关联。

提取码失败、分享失效和目标资源失效仍记录失败审计。正确提取码先记录成功审计，再签发 share ticket。审计数据库不可用时沿用 fail-closed 行为，但用户输入长度不能再触发数据库列溢出。

## 7. Market 钱包动作 fencing

### 7.1 Claim 所有权

每次处理 claim 生成新的 UUID lease token：

```text
find due action
  -> generate leaseToken
  -> CAS PENDING/RETRYING -> PROCESSING
     set lease_token, processing_lease_until
  -> invoke idempotent Wallet API
  -> advance monotonic order saga
  -> fenced action transition
```

Domain model 和 repository contract 携带本次 claim token。处理路径上的以下转换必须以 `action_id + status=PROCESSING + lease_token` 为条件：

- succeeded；
- processor-owned cancelled；
- retrying；
- failed；
- recovery-pending；
- dead。

转换成功时清空 lease token 和 deadline。影响 0 行表示 worker 已失去租约，当前执行停止，不再尝试无条件兜底更新。它记录结构化 lost-lease warning/counter，但不把 CAS miss 当成基础设施异常覆盖新 owner 的结果。

订单取消对尚未 claim 的 escrow action 所做的 `PENDING/RETRYING -> CANCELLED` 仍使用自己的状态 CAS，不伪造 processing lease。Recovery 对已经携带 wallet transaction 的非 processing action 使用独立的 expected-status/transaction CAS，不复用 processor 的 fenced 方法。

### 7.2 过期恢复

过期恢复仅匹配：

```text
status=PROCESSING
and processing_lease_until <= asOf
```

恢复把 action 改为立即到期的 `RETRYING`，增加 retry count，并清空旧 token/deadline。后续 claim 必须写入新 token。旧 worker 此后的任何 action 状态写入都会因 token 不匹配而失败。

Wallet API 继续用 `requestId` 保证重复远程调用幂等；订单 saga 转换继续是单调且条件化的。Fencing 解决的是处理所有权，不替代 Wallet 幂等和订单状态机。

## 8. IM 历史与会话分页

### 8.1 兼容接口

现有接口保持原语义：

```text
GET /api/im/conversations?page=&size=
GET /api/im/conversations/{id}/messages?afterSeq=&limit=
```

新增接口：

```text
GET /api/im/conversations/page?cursor=&size=
GET /api/im/conversations/{id}/messages/history?beforeSeq=&limit=
```

Controller 只转换 query parameter 和 response DTO，两个新入口都调用现有 `ConversationApplicationService` 中对应的新 use-case 方法。

### 8.2 会话游标

会话列表排序保持：

```text
sort_at desc, conversation_id asc
```

游标编码版本、`sortAt` 和 `conversationId`。下一页条件为：

```text
sort_at < cursor.sortAt
or (sort_at = cursor.sortAt and conversation_id > cursor.conversationId)
```

Repository 获取 `size + 1` 行。Application 返回 `items`、`nextCursor` 和 `hasMore`。现有 `(user_id, sort_at, conversation_id)` 索引支持该查询，无需新增 IM migration。

会话是实时变化列表；某会话收到新消息后可能移到已加载边界之前。前端按 `conversationId` 去重，并通过刷新或实时事件获得最新排序，不把 keyset 页面解释为永久快照。

### 8.3 消息历史

首次 history 请求不传 `beforeSeq`，查询当前 conversation 最新 `limit + 1` 条。后续请求以当前最小 seq 作为 `beforeSeq`，查询 `seq < beforeSeq`。

MyBatis 利用 `(conversation_id, seq)` 主键倒序读取，Application 在响应前恢复为 seq 升序。响应返回：

- `conversationId`
- `items`
- `nextBeforeSeq`
- `hasMore`
- `lastReadSeq`

前端初次进入详情时使用 history 接口，因此看到最新消息。点击“加载更早消息”后把旧页 prepend，并保持加载前第一条可见消息的滚动锚点。Realtime 消息仍追加到底部；所有合并按 message ID、seq 和 client message identity 去重。成功加载的最大 seq 用于 mark-read。

会话列表改用 cursor endpoint，并提供“加载更多”控制，不再固定为前 20 条。

## 9. 评论 keyset pagination

### 9.1 领域专用 cursor

评论不再复用只表达 page/size 的 `FeedCursorCodec`。新增 application-owned `CommentCursorCodec`，编码 Base64URL JSON，至少包含：

- schema version；
- kind：`ROOT` 或 `REPLY`；
- post ID；
- reply cursor 的 root comment ID；
- boundary create time；
- boundary comment ID。

cursor 不授予权限，但必须验证版本、类型、scope、时间和 UUID。非法 Base64、字段缺失、root cursor 用于 replies、其他 post/root 的 cursor 都返回稳定的参数错误，不能静默退回第一页。

### 9.2 查询规则

根评论保持：

```text
create_time desc, id desc
```

下一页谓词为：

```text
create_time < boundaryTime
or (create_time = boundaryTime and id < boundaryId)
```

回复保持：

```text
create_time asc, id asc
```

下一页谓词方向相反：

```text
create_time > boundaryTime
or (create_time = boundaryTime and id > boundaryId)
```

Repository 接口表达 boundary 和 limit，不再让这两个 HTTP cursor 路径传入 page/offset。每次读取 `size + 1` 行，实际返回最后一条生成下一 cursor。并发插入不会改变已经取得的 boundary，并发删除不会使后续仍存在的记录被 offset 跳过。

第一屏评论缓存仍只缓存空 cursor 请求。Redis key namespace 升级版本，避免发布后命中携带旧 offset cursor 的缓存结果。

## 10. Notice 有界内容

Content 发布的 `CommentCreated` domain/contract event 继续携带完整合法评论内容，不把 Notice 的展示策略反向泄漏到 Content domain。

Notice projection 在构造 `NoticeProjectionContent.Comment` 时把 comment content 转成最多 240 个 Unicode code point 的预览。截断不能切断 surrogate pair；字段名保持 `content`，从而兼容已有 JSON consumer。其他 ID、类型、目标用户和时间字段保持不变。

`notice_record.content` 同时扩大为 nullable `MEDIUMTEXT`。有界预览表达产品语义，扩大列类型则避免任何通知类型依赖 JSON 转义比例估算存储上限。JSON 序列化失败仍回滚 projection transaction；合法的最大评论不能再因列长度失败。

## 11. 评论事务与 Redis

### 11.1 权威写入

评论创建、编辑和删除事务内只执行：

- 评论 repository 写入；
- MySQL 帖子 comment count 更新；
- 幂等记录；
- domain event/outbox 写入。

`PostCounterCache.incrementCommentCount` 和 `CommentPageCache.evictPost` 都注册为 after-commit 动作。创建路径只在真正创建新评论时注册 counter delta，幂等成功重放不能再次增加 Redis counter。删除使用 repository 实际删除数量作为 delta。

### 11.2 失败隔离

每个缓存动作独立执行并捕获 `RuntimeException`：

- 数据库回滚时 after-commit 不执行；
- counter 更新失败不阻止 page-cache eviction 尝试；
- page-cache eviction 失败不改变已提交 HTTP 结果；
- Redis 失败记录 post ID、operation、delta 和 trace context，但不记录评论正文；
- 后续读取仍以 MySQL comment count 为准，现有 reconciliation/刷新路径负责最终收敛。

不修改共享 `AfterCommitExecutor` 的全局异常语义；Content application 使用聚焦的 best-effort wrapper，避免改变其他调用方的契约。

## 12. 数据库迁移

### 12.1 Community schema

在当前 `V011` 之后增加：

1. `V012__persist_drive_upload_checksum.sql`
   - 增加 `drive_upload.checksum_sha256 varchar(128) not null default ''`。
   - 不删除或终态化现有 upload row。
2. `V013__add_market_wallet_action_lease_fencing.sql`
   - 增加 nullable `lease_token binary(16)`。
   - 增加 `(status, processing_lease_until, action_id)` 索引。
   - 在旧 worker 已停止后，把遗留 `PROCESSING` row 改为立即可执行的 `RETRYING`，清空 deadline/token；重复 Wallet 操作由 request ID 幂等保护。
3. `V014__widen_notice_content.sql`
   - 把 `notice_record.content` 从 nullable `varchar(4000)` 改为 nullable `mediumtext`。
   - 保留已有通知内容。

评论现有索引已经包含其 scope、时间和 ID 排序键。IM 的 message 主键以及 inbox 排序索引也已经覆盖新查询，因此两者不新增 migration。

### 12.2 迁移规则

- 不修改 `V001-V011`、IM `V001-V002` 或 schema manifest。
- 更新 Community migration count、latest schema column/index 断言和 legacy upgrade fixture。
- 更新 `community-app` H2/test schema，使其反映运行时最新列。
- 使用 Testcontainers MySQL 验证空 schema、version-one upgrade、数据保留、重复 migrate no-op 和 Flyway validate。
- 不提供 down migration；后续 schema 修复只能发布更高版本 migration。

## 13. 发布顺序

1. 生成 `IM_SESSION_TICKET_HMAC_SECRET`，同步配置给 Gateway 与 Realtime，并确认它不同于 `JWT_HMAC_SECRET`。
2. 暂停 `marketWalletActionProcessor` 和 `marketWalletActionRecovery`，并短暂停止签发新 IM session。
3. 执行 Community `V012-V014`。
4. 部署带 credential-confusion guard 的所有资源服务；普通 access token 保持有效。
5. 在同一维护窗口重启 Realtime 和 IM Gateway，使两端同时切换到新 ticket 密钥、issuer 和 audience。
6. 恢复钱包 worker 和 IM 流量。
7. 观察 lost-lease、ticket rejection、Drive legacy checksum fallback 和 after-commit cache failure 信号。

旧 IM ticket 不兼容且立即失效，客户端重新打开 session。旧 `page/size`、`afterSeq` 接口继续工作。旧评论 cursor 是页面内短期状态；它在新 decoder 下会得到参数错误并刷新第一页。评论缓存 namespace 升级，因此无需清空整个 Redis。

回滚采用 application-forward。加列、加索引和扩大列类型对旧 reader 基本兼容，但禁止重新混入没有 wallet fencing 的旧 worker，也不为 ticket 增加双密钥兼容窗口。

## 14. 错误与可观测性

| 条件 | 对外行为 | 内部信号 |
| --- | --- | --- |
| access token 缺少或无效 | HTTP `401` | `invalid_token` counter |
| session ticket 验证失败 | WebSocket 认证失败 | 按内部原因分类的 rejection counter |
| ticket 被用作 bearer token | HTTP `401` | credential-confusion rejection |
| Drive checksum 非空且与 OSS session 不一致 | 现有非法上传语义 | session/object/version 结构化 warning |
| Drive/OSS 暂时不可用 | 现有 storage unavailable 语义 | dependency failure metric |
| 钱包 worker 丢失 lease | 不覆盖状态；当前 item 停止 | lost-lease counter 和 action ID |
| cursor 非法或 scope 不匹配 | 参数错误 | cursor kind/version reason |
| Notice JSON 序列化失败 | projection 重试/回滚 | event type 与 source event ID |
| after-commit Redis 失败 | 已提交请求仍成功 | cache operation、post ID、delta、trace |

日志不得包含 JWT、session ticket、Drive share password、原始 IP/User-Agent 或完整评论正文。

## 15. 测试策略

实现使用 TDD，每个工作流先加入能复现原缺陷的失败测试。

### 15.1 凭证测试

- 共享 access decoder 拒绝由全局密钥签发且 `typ=im-session-ticket` 的 legacy ticket。
- 普通无 `typ` access token 仍通过。
- 专用 ticket codec 分别拒绝错误 secret、issuer、audience、type 和过期时间。
- session ticket 不能再次调用 open-session。
- ticket secret 为空、过短或等于全局 secret 时配置启动失败。

### 15.2 Drive 测试

- prepare 后 checksum 经过 domain/data object/mapper round trip 保持不变。
- complete 传给 OSS 的 checksum 来自 persisted aggregate。
- multipart 中的空值或伪造值不能覆盖预期 checksum。
- legacy 空 checksum row 由 internal OSS complete 使用 session expected checksum；非空不匹配仍拒绝。
- 超长 User-Agent 只产生 64 字符摘要，原始输入不进入 repository，正确提取码成功。
- migration 测试验证新列和已有 row 保留。

### 15.3 Wallet fencing 测试

使用确定性双 worker 场景：

```text
worker A claim(token A)
  -> lease expires and is recovered
worker B claim(token B)
  -> worker B writes terminal state
worker A returns late
  -> every success/retry/fail transition affects zero rows
```

每个 processor-owned transition 都有 mapper contract 断言。Recovery transition 另外验证 expected status/transaction 条件。Migration 测试验证遗留 `PROCESSING` row 变为可重试且旧 deadline 被清除。

### 15.4 IM 分页测试

- 51 条以上消息首次 history 返回最新 `limit` 条。
- 连续向前分页无重复、无遗漏，输出保持 seq 升序。
- 相同 `sort_at` 的会话按 ID 稳定分页。
- 旧 controller contract 保持不变。
- 前端覆盖首次加载、加载更早、滚动锚点、实时消息去重和会话加载更多。

### 15.5 评论、Notice 和缓存测试

- 在翻页之间插入或删除评论，剩余记录无重复和遗漏。
- 相同毫秒时间使用 UUID tie-breaker。
- 非法、跨 kind、跨 post/root cursor 返回参数错误。
- 2000 个引号、反斜线和 emoji 均能生成合法通知 JSON，预览不超过 240 code point。
- transaction rollback 不调用 Redis。
- commit 后才调用 Redis，且 Redis 异常不向已提交请求传播。
- 幂等 replay 不重复增加 comment counter。
- 新 Redis namespace 不读取旧 cursor cache。

### 15.6 最终验收

至少执行：

```bash
cd backend
mvn test -pl :community-common-security,:community-db-migrations,:community-im-db-migrations,:community-app,:community-im-gateway,:im-core,:im-realtime -am
mvn test -pl :community-app -Dtest='*ArchTest'

cd ../frontend
npm test
npm run build
```

另外执行相关 deploy contract tests，并对私信详情和会话列表做桌面、移动视口检查。

## 16. 验收标准

1. 新 IM ticket 与 access token 使用不同密钥域，并且任一 session ticket 都不能访问普通认证接口或申请新 session。
2. 使用非空 checksum prepare 的 Drive 上传可以成功 complete，OSS 收到相同 checksum。
3. 租约被重新认领后，旧钱包 worker 的所有处理终态写入均失败，最终状态属于新 worker。
4. 任意允许长度的 User-Agent 都不会使分享验证因指纹列溢出失败，数据库不保存原始 IP/UA。
5. 用户可以浏览最新私信、加载完整历史并加载超过 20 个会话。
6. 评论 keyset 在并发插入/删除下不因 offset 位移而重复或漏掉仍存在记录。
7. 最大合法评论可以可靠投影为合法、有界的通知内容。
8. 评论事务回滚不修改 Redis；Redis 故障不改变已提交评论的 HTTP 成功结果。
9. Community 架构测试、相关后端测试、migration 测试、前端测试和构建全部通过。
