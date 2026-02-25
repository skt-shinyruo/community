# Change Proposal: 事件一致性与契约治理（Outbox + Like 投影 + API/错误语义）

## Requirement Background
当前仓库已具备微服务拆分与基础工程化能力（统一 `Result<T>`、`traceId`、`/internal/**` + `X-Internal-Token`、Kafka DLQ 等），但从代码行为看仍存在以下系统性问题，导致“偶发变必现”的长期数据不一致与排障困难：

1. **事件发布默认不可靠**：`content-service` / `social-service` 默认 `outbox.enabled=false`，事件走 “After-Commit + 异步直发 Kafka（best-effort）”。当 Kafka/网络瞬时抖动时，DB 已提交但事件丢失，下游（search/message/投影）将永久缺失对应变更。
2. **点赞与热帖分数链路数据源不一致**：`content-service` 读点赞数/是否点赞依赖 Redis Set（`like:entity:*`），但 `social-service` 默认以 MySQL 为点赞 SSOT 且不写该 Redis；同时 content 消费社交事件仅触发“刷新热度队列”，未维护点赞投影，导致默认情况下点赞相关展示/热度计算偏离真实值。
3. **拉黑关系在私信写路径存在 fail-open 窗口**：`message-service` 私信发送前只查本地投影，投影缺失时等价于“不拉黑”，在冷启动/漏消息/消费滞后场景会放过本应拦截的写请求。
4. **API 契约不稳定**：部分接口直接返回实体（如 message 的 `Message`），对外协议被内部字段与表结构锁死，后续演进/脱敏/分页元信息扩展困难。
5. **鉴权错误语义不一致**：部分 controller 在认证信息缺失时返回 400（`INVALID_ARGUMENT`）而非 401（`UNAUTHORIZED`），使“鉴权/链路配置问题”在排障时被伪装成“参数问题”。
6. **Outbox relay 健壮性存在“卡死/膨胀”风险**：当前 outbox 认领逻辑只拉取 `NEW/RETRY`（如 `*/src/main/resources/mapper/outbox-event-mapper.xml`），若 relay 在 `markSending` 后崩溃，事件可能长期停留在 `SENDING` 且永不再被认领；同时缺少 `SENT` 事件的清理/归档策略，长期运行会导致 outbox 表膨胀，影响查询与运维。
7. **内容域互动写路径同样存在“拉黑校验投影缺失 fail-open”**：`content-service` 的评论/回复互动依赖本地 `user_block_projection` 判断拉黑关系；但查询逻辑“查不到记录 -> 认为未拉黑”，当消费者滞后/漏消费/冷启动时，会出现“已拉黑仍可评论/回复”的窗口期（与 message-service 的问题同类）。
8. **社交写路径信任边界不清，事件 payload 可被客户端注入**：`social-service` 点赞/关注写接口把 `entityUserId/postId` 作为请求字段直接写入事件与计数（如 `LikeService`/`FollowService`），下游（message/user/content）据此发通知/加积分/刷新热度。恶意客户端可伪造 `entityUserId`，造成通知误发、积分/获赞数被刷或被扣、热帖刷新指向错误帖子等数据污染。
9. **社交写路径缺少 entity 存在性校验**：`social-service` 作为关系 SSOT（like/follow），但默认不校验 `entityId` 是否真实存在（post/comment/user）；可写入“悬空关系”，并通过事件驱动污染下游投影与队列（Redis like set / post score queue / notice/points）。
10. **客户端降级策略默认值不安全，可能把依赖故障伪装成 400**：例如 `message-service` 的 `UserServiceClient` 代码默认 `fail-open=true`，在 user-service 不可用时可能降级返回 null，进而被上层当作“目标用户不存在”（400）处理，导致排障困难与客户端误判。
11. **热帖分数刷新队列存在 at-most-once 丢失窗口**：`post:score` 使用 Redis Set + `SPOP`，`PostScoreRefresher` 在 `pop` 后若发生异常（DB/Redis/Kafka 短暂抖动），该 postId 可能被永久弹出而未刷新，导致分数/索引长期滞后，且缺少可观测的补偿路径。
12. **Outbox 运维/回收 SQL 缺少针对性索引，可能引入“慢查询/抖动”风险**：新增 `SENDING` lease 回收与 `SENT` 保留期清理后，会按 `status + updated_at/created_at` 扫描；若没有对应索引，在 outbox 积压或长期运行时可能触发全表扫描，反过来影响 relay 稳定性与业务库性能。
13. **Kafka 消费者对 envelope 解析/unknown handling 策略不一致**：例如 `search-service` 仍手写 `readTree` + “unknown type -> 抛异常进 DLQ”，当 post 事件新增类型或做版本演进时，容易产生 DLQ 噪音甚至阻塞消费组；需要对齐 `EventEnvelopeParser` 与 `UnknownEventAction`（可配置、可观测、可降噪）。
14. **积分链路对取消点赞不敏感，存在刷分风险**：`user-service` 的 `PointsEventConsumer` 只在 `LikeCreated` 时给 `entityUserId` 加分，不处理 `LikeRemoved`；用户可通过反复“点赞/取消点赞”让积分持续增长，污染排行榜与成长体系。
15. **internal 点赞回填入口缺少统一 break-glass 保护**：`content-service` 的 `/internal/content/likes/backfill` 仅靠 endpoint-enabled 开关控制，缺少 `InternalOpsGuardFilter` 的 allowlist + token + 限流等二次保护；误触可能对 social/content/Redis 造成压力。

本变更旨在把上述问题从“靠约定/人工兜底”升级为“靠默认安全态 + 机制化闭环”。

## Change Content
1. **可靠投递默认化**：将 Outbox 作为 `content-service` / `social-service` 的默认事件生产路径（默认开启、可开关回滚），把“丢事件”降级为“延迟可见 + 可重放”。
2. **点赞事件契约补齐**：在 `community.event.social.v1` 中新增 `LikeRemoved`（取消点赞）事件，与 `LikeCreated` 对称，允许下游做可逆投影与热度回落。
3. **content-service 点赞投影落地**：消费 `LikeCreated/LikeRemoved` 并维护 Redis `like:entity:{type}:{id}` 投影，确保帖子详情点赞展示与热帖分数计算使用同一数据源且最终一致。
4. **message-service 拉黑校验闭环**：私信写路径采用“投影优先 + 缺失回源 SSOT（social-service internal）+ 回填投影”的策略，消除 fail-open 窗口。
5. **API/错误语义治理**：将 message 对外接口逐步替换为 DTO 输出（保持字段兼容窗口），并把“未认证”统一为 401（`UNAUTHORIZED`）。
6. **Outbox relay 健壮性补强**：增加 `SENDING` 卡死回收（lease 超时回退到 `RETRY`）与 `SENT` 事件保留期清理（默认关闭、可开关），避免“永不投递/表膨胀”。
7. **content-service 互动拉黑校验补强**：评论/回复写路径采用“投影优先 + 缺失回源 SSOT + 回填投影”的策略，与私信保持一致，消除反骚扰窗口期。
8. **社交写路径契约收敛（服务端补全/校验）**：点赞/关注写路径在服务端解析并校验 entity 元信息（owner/postId/存在性），禁止信任客户端注入字段；事件 payload 以服务端事实为准，消除“伪造通知/积分/热度”的攻击面。
9. **客户端降级默认安全态**：统一 internal client 的 `fail-open` 默认值为 false（写路径 fail-closed），避免把依赖故障伪装成 400；必要的读路径降级需显式配置并打指标。
10. **热度刷新链路可靠性补强**：对 `post:score` 队列增加失败回补（至少一次语义）与可观测指标，避免刷新任务异常导致 postId 永久丢失。
11. **积分链路与事件可逆性对齐**：支持 `LikeRemoved` 触发积分回退，并对积分做非负保护，避免“点赞开关刷分”与边界场景的负数积分。
12. **运维入口统一纳入 Ops Guard**：将 `/internal/*/likes/backfill` 纳入 `InternalOpsGuardFilter` 的 break-glass 保护策略（默认关闭、显式开启），降低误触与滥用风险。

## Impact Scope
- **Modules：**
  - `common`（事件类型常量、通用错误语义约定）
  - `content-service`（Outbox 默认、社交事件消费、点赞投影、热帖刷新正确性）
  - `social-service`（Outbox 默认、点赞取消事件发布）
  - `user-service`（Outbox 默认与 relay 健壮性、LikeRemoved 积分回退与防刷分）
  - `message-service`（拉黑投影校验补强、API DTO 化）
  - `frontend`（如需：适配 message 返回 DTO 或字段兼容校验）
  - `deploy/`（默认配置对齐、可观测与演练脚本）
  - `.helloagents/*`（架构 ADR、API 手册、模块说明同步）
- **APIs：**
  - Kafka：`SOCIAL_EVENTS_V1` 增加 `LikeRemoved`
  - message：`/api/messages/conversations/{conversationId}` 等接口输出由实体迁移为 DTO（兼容窗口内字段保持一致）
  - internal ops：Outbox replay/health 维持不变；新增 ops-guard 覆盖 `/internal/*/likes/backfill`（仍需 internal-token）
- **Data：**
  - Outbox 表已存在（`outbox_event`），主要是“默认开关”与“运行策略”变化
  - Redis：新增/完善 `like:entity:*` 投影作为读路径与热度计算的统一来源

## Core Scenarios

### Requirement: 事件可靠投递（Outbox 默认开启）
**Module:** content/social

#### Scenario: Kafka 短暂抖动或 broker 重启
前置条件：帖子/点赞等写请求已成功提交 DB
- 事件不会因 Kafka 抖动永久丢失：写侧入 Outbox，relay 重试直到成功或进入 FAILED
- 运维可通过 `/internal/*/outbox/health` 观测 backlog，通过 `/internal/*/outbox/replay` 重放失败事件

### Requirement: Outbox relay 健壮性（SENDING 回收 + SENT 清理）
**Module:** content/social/user

#### Scenario: relay 在 markSending 后崩溃 / 实例重启
- 事件不会永久卡在 `SENDING`：超过 lease 超时（例如 60s）会自动回退到 `RETRY` 并重新投递
- 允许幂等重放：下游消费应以 eventId/幂等写实现“至少一次”安全

#### Scenario: outbox_event 长期膨胀影响性能
- 可配置启用 `SENT` 事件按保留期清理（例如保留 7/14/30 天）
- 默认关闭（保守），由运维按环境评估开启

### Requirement: 点赞一致性与热帖分数正确性
**Module:** social/content

#### Scenario: 用户点赞/取消点赞帖子
- social-service 写入 SSOT（DB），并发布 `LikeCreated/LikeRemoved`
- content-service 消费事件并更新 Redis 投影：`like:entity:POST:{postId}`
- 帖子详情 `likeCount/liked` 与热帖分数使用同一投影来源，且支持取消点赞后的热度回落

### Requirement: 社交写路径事件契约可信（禁止客户端注入）
**Module:** social/content/message/user

#### Scenario: 恶意客户端伪造 entityUserId/postId
- social-service 在写路径通过 content-service internal 解析 entity owner/postId，并校验 entity 存在性
- 事件 payload 中的 `entityUserId/postId` 以服务端解析为准，下游无需再信任客户端传参
- 若无法解析/校验（依赖不可用或 entity 不存在）：写请求 fail-closed（400/404/503 语义化），避免写入脏关系与污染下游

### Requirement: 热度刷新队列可靠性（至少一次 + 可观测）
**Module:** content

#### Scenario: PostScoreRefresher 执行中异常
- 刷新失败不应导致 postId 永久丢失：可回补重试或进入可运营的补偿队列
- 有指标可观测：成功/失败/回补次数、队列积压量

### Requirement: 私信拉黑校验一致性（消除 fail-open）
**Module:** message/social

#### Scenario: 冷启动/投影缺失情况下发送私信
- message-service 若发现拉黑投影缺失或不可信，应回源 social-service internal 查询
- 若任意一方拉黑另一方：严格拒绝写入（403），且回填投影以减少后续回源

### Requirement: 评论/回复互动拉黑校验一致性（消除 fail-open）
**Module:** content/social

#### Scenario: 冷启动/投影缺失情况下评论/回复
- content-service 若发现拉黑投影缺失/不可判定，应回源 social-service internal 查询
- 若任意一方拉黑另一方：严格拒绝写入（403），并回填投影以减少后续回源

### Requirement: API 契约稳定化（DTO）
**Module:** message/frontend

#### Scenario: 私信列表与会话详情展示
- message-service 对外返回 DTO（字段稳定、可扩展），避免直接暴露实体
- 前端在兼容窗口内无需或仅做最小改动

### Requirement: 鉴权错误语义统一
**Module:** user/social/message/content

#### Scenario: 绕过网关直连下游服务或配置误用
- 当未携带认证信息时返回 401（`UNAUTHORIZED`），便于定位鉴权与链路配置问题

## Risk Assessment
- **Risk：跨服务兼容与发布顺序**（新增事件类型、切换 Outbox 默认、投影策略变更）
  - **Mitigation：** 采用“先兼容、后启用、再清理”的发布策略：先让消费者对未知事件 SKIP；再上线生产者与投影；最后默认开启 Outbox 与回填历史投影。
- **Risk：Outbox backlog 积压影响延迟**
  - **Mitigation：** 通过 backlog 指标告警（NEW/RETRY/SENDING/FAILED）+ relay 参数可调（batch/interval/retry/backoff），并提供 replay 工具。
- **Risk：SENDING 回收会引入重复投递（至少一次语义）**
  - **Mitigation：** 下游消费必须幂等：以 eventId 幂等表（message/search/user）或幂等写（Redis Set/upsert）保证重复投递无副作用。
- **Risk：Redis 投影与 SSOT 存在短暂最终一致窗口**
  - **Mitigation：** 关键写路径（私信拉黑校验）在投影缺失时回源 SSOT；点赞投影提供 backfill/演练脚本以缩短窗口。
- **Risk：社交写路径引入 content-service 解析依赖**
  - **Mitigation：** 仅在写路径调用 internal resolve（短超时 + 明确 503），并可引入短 TTL 缓存降低放大效应；同时保留降级开关（仅限非 prod/演示环境）。
