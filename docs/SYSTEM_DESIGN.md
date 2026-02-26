# 系统设计（同步 API + 异步事件）

本文档聚焦“系统设计层面”的关键点：模块边界、数据流、事件契约、最终一致、幂等与失败处理。其目标是让开发者理解“为什么这样拆”“链路如何走”“如何安全演进”。

---

## 1. 模块边界（按职责划分）

### 1.1 统一入口：gateway
- 浏览器唯一后端入口：`/api/**`
- 对外运维入口：`/api/ops/**`（高风险操作；仅管理员可触发，建议通过 Ops Console 等受控入口执行）
- 服务间同步调用：Dubbo RPC（契约与 DTO 统一沉淀在 `*-api` 模块）
- 统一能力：鉴权、CORS、限流、审计、traceId
- 路由：按路径前缀转发到各微服务（见 `gateway/src/main/resources/application.yml`）

边界与弃用窗口（SSOT）：
- External（对外业务）：`/api/**`
- Ops（对外运维）：`/api/ops/**`
  - 默认要求：管理员角色（ADMIN）（由网关鉴权收敛）
  - 建议：对高成本入口在网关/基础设施层补齐限流与审计（可选）
- Internal-RPC（服务间同步调用）：Dubbo RPC（契约在 `*-api` 模块；禁止跨库 JOIN）
- 历史遗留路径（示例）：`/api/search/internal/reindex`
  - 不再保留功能语义：gateway 固定返回 410 并提示迁移；新入口为 `/api/ops/search/reindex`

### 1.2 身份与会话：auth-service
- 登录/刷新/登出闭环
- 验证码、注册激活、找回密码等账号安全能力

### 1.3 业务域服务（示例）
- content-service：帖子/评论（写主存储并发布事件）
- social-service：点赞/关注（写 Redis 并发布事件）
- message-service：私信/通知（消费事件写通知）
- search-service：搜索（消费事件写 ES 索引；提供 reindex 用于冷启动/修复，重建通过 content-service Dubbo RPC 拉取数据）
- analytics-service：统计（可由 gateway 采集或事件驱动写入）

### 1.4 配置中心与 profile（fail-closed）

本项目通过 profile 明确区分“开发便捷”与“生产安全默认态”：

- dev/local：允许 `spring.config.import=optional:nacos:...`，便于不接入 Nacos 的本地启动与单测。
- prod：必须显式启用 `prod` profile，此时：
  - `spring.config.import` 对 `${spring.application.name}.yaml` 为 **required/fail-fast**（配置中心不可用直接失败，禁止静默退化）。
  - 可选导入 `${spring.application.name}-prod.yaml` 作为 prod 专用覆盖（例如可信代理 CIDR、更严格开关/阈值）。
- 启动期校验（Startup Validation）：`common` 在 `prod` 下启用启动校验，关键密钥缺失会直接阻断启动（fail-closed），避免“带着默认值上线”。

运维约定：
- 生产部署入口必须显式设置 `SPRING_PROFILES_ACTIVE=prod`（避免 dev/default 默认值误用）。

---

## 2. 同步 API：读写分离的基本形态

### 2.1 读路径（示例：帖子列表）
1. 前端请求 `/api/posts?...`
2. gateway 路由到 content-service
3. content-service 读 MySQL/Redis 组装结果返回

### 2.2 写路径（示例：发帖/评论/点赞/关注）
1. 前端请求写接口（携带 JWT）
2. gateway 统一鉴权/限流/审计
3. 目标服务写入主存储（DB/Redis）并发布事件

### 2.3 错误协议（HTTP status + Result.code）
本项目对外（以及内部）统一返回 `Result<T>` 结构，但同时要求：
- **HTTP status：表达错误类别**（4xx/5xx，便于网关/监控/前端统一处理）
- **Result.code：表达业务细分**（如 `AuthErrorCode` 10001+）

基本约定：
- 参数错误：HTTP 400 + `Result.code=400`
- 未登录/令牌无效：HTTP 401 + `Result.code=10003/401`（业务码优先表达细分）
- 无权限：HTTP 403
- 依赖故障（DB/Redis/Kafka/下游服务不可用）：HTTP 503（fail-closed）
- 未捕获异常：HTTP 500

前端 Axios 约定：
- 对非 2xx 响应仍尝试解析 `Result` 并展示 message/traceId
- 401 的 refresh 逻辑需区分“可刷新/不可刷新”，避免登录失败误触发 refresh

> P0 生产可用优先：对于“DB 事务 + Kafka 事件”的场景，事件发布必须在事务提交后执行（After-Commit），
> 避免出现“DB 回滚但事件已发出”的幽灵事件。

### 2.4 refresh cookie 与 CSRF（SameSite + OriginGuard）

本项目 refresh token 采用 **HttpOnly cookie** 存储（防止被 JS 读取），因此需要同时关注：
- cookie 级别的 `SameSite`/`Secure` 策略
- 请求级别的 Origin 校验（OriginGuard）来降低 CSRF/旁路风险

策略约定（SSOT）：
- **同源部署（推荐）**：
  - refresh cookie：`SameSite=Lax`（或 Strict，视业务跳转而定）
  - prod：refresh cookie `Secure=true`（HTTPS）
  - gateway/auth-service：启用 OriginGuard，但同源请求始终放行
- **跨站部署（谨慎）**：
  - refresh cookie：必须 `SameSite=None` 且 `Secure=true`
  - gateway/auth-service：OriginGuard allowlist 必须配置且在 prod 下 allowlist 为空时 **fail-closed**
  - CORS：必须 `allowCredentials=true` 且 `allowedOrigins` 精确匹配（禁止 `*`）

详细说明与运维清单见：`helloagents/wiki/runbooks/cookie-and-csrf.md`。

### 2.5 API DTO 与契约测试（字段白名单）

为避免“直接返回 entity 导致字段泄露/契约被数据库结构绑定”，公共接口应返回 DTO（字段白名单）。

约定：
- 公共读接口（例如评论/回复列表）不得暴露治理字段（如 `status`、`deletedReason` 等）
- 契约通过回归测试固化，避免后续重构/联表扩展时不小心把字段带出

示例：
- `content/content-service/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java`

---

## 3. 异步事件：最终一致（Kafka）

### 3.1 Topic 约定
事件 topic 由 `contracts-event-core` 的 `EventTopics` 统一定义（SSOT：`platform/contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventTopics.java`）：
- `community.event.post.v1`
- `community.event.comment.v1`
- `community.event.social.v1`
- `community.event.moderation.v1`
- 约定 DLQ：`<topic>.dlq`

### 3.2 事件 Envelope（契约边界）
代码位置（SSOT）：`platform/contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventEnvelope.java`。

事件消息使用统一 envelope：
- `eventId`：全局唯一，用于幂等
- `traceId`：贯穿请求链路，便于日志串联
- `type`：事件类型（如 `PostPublished`、`CommentCreated`）
- `version`：事件版本（当前为 v1）
- `occurredAt`：发生时间
- `producer`：生产者服务名
- `payload`：具体数据（避免敏感字段）

#### 3.2.1 版本治理与 unknown handling（fail-closed + 可控降噪）
消费端必须对 envelope 做统一校验（required fields + version）：
- **unsupported envelope version**：默认进入错误处理（重试/DLQ），避免 silent drop（fail-closed）
- **unknown event type**：由于 topic 按 domain 聚合（如 `social.v1` 同时包含 Like/Follow/Block），单个 consumer 可能只关心子集 type；默认允许 **SKIP** 并按 type 去重告警，避免 DLQ 噪音

unknown handling 为可配置策略（服务级别）：
- `community.kafka.consumer.unsupported-version-action`：`DLQ`（默认）/ `SKIP`
- `community.kafka.consumer.unknown-type-action`：`SKIP`（默认）/ `DLQ`

### 3.3 典型消费方
- message-service：消费评论/社交事件，生成通知（最终一致）
- search-service：消费帖子事件，更新 ES 索引（最终一致）

补充：搜索属于“事件驱动投影”，因此天然是最终一致：
- 发帖/编辑成功后，到可搜索通常存在秒级延迟（事件传播 + 消费处理 + ES refresh）
- 用户体验层面需要明确提示（前端已在搜索页做延迟说明），避免“写成功但搜索不到”被误解为丢数据
- 若出现“长时间缺失/冷启动缺口”，建议按以下顺序排查与修复：
  1) Kafka 消费滞后与 DLQ：查看 lag/错误日志，必要时回放 DLQ（见 `scripts/kafka-replay-dlq.sh`）
  2) ES 索引健康：检查索引是否存在、mapping 是否兼容、写入是否报错
  3) 冷启动/纠偏：必要时执行 reindex（`scripts/search-reindex.sh` 或 `/api/ops/search/reindex`）

### 3.4 跨域校验与聚合：Dubbo RPC 回源（去投影）
按当前需求取舍：仓库已移除跨域本地投影（`*_projection` 表、Redis 投影、投影消费者与 backfill 入口），统一改为 **Dubbo RPC 实时回源 SSOT**。

典型场景：
- content/message 写路径反骚扰（拉黑校验）：RPC 调 `social-service`（`SocialBlockRpcService#isEitherBlocked`），默认 fail-closed（social 不可用则 503）
- content/message 写路径处罚状态守卫：RPC 调 `user-service`（`UserModerationRpcService#getStatus`），默认 fail-closed（user 不可用则 503）
- social 写路径可信解析（entity resolve）：RPC 调 `content-service`（`ContentEntityRpcService#resolveEntity`），默认 fail-closed（content 不可用则 503）
- content 读路径点赞查询：RPC 调 `social-service`（`SocialReadRpcService#entityLikeCount/hasLiked`），展示类读路径默认 fail-open（可配置）

风险与约束：
- 同步依赖链增加（且存在 `content-service ↔ social-service` 双向依赖），更容易出现级联超时与部署牵制
- 所有 `@DubboReference` 必须显式 `timeout` + `retries=0`，并为关键路径明确 fail-open/fail-closed 策略
- 避免 N+1 RPC：优先批量 RPC 或短 TTL 缓存（容量受控）

---

## 4. 幂等与失败处理

### 4.1 消费幂等（eventId 去重）
消费端通过记录已消费的 `eventId` 来保证幂等：
- message-service：`consumed_event` 表
- search-service：`search_consumed_event` 表

这能避免：
- Kafka 重平衡/重试导致的重复消费产生重复副作用（重复通知、重复索引更新）

### 4.2 消费侧“事务 + ack”的最小正确性（P0）
消费端最小正确性目标是：**ack 之前，业务副作用必须已成功提交**。因此：
- Listener 仅做“调用处理器 + 成功后 ack”
- 处理器使用 `@Transactional`，并确保幂等记录与业务写入同事务提交
- 避免“同类内部调用导致事务不生效”的自调用陷阱（建议拆分为独立 `@Service`）

### 4.3 DLQ（死信队列）
当消费端处理失败（反序列化/业务异常等）：
- 通过统一的错误处理器将消息投递到 `<topic>.dlq`
- 便于离线排查与人工/脚本回放

Runbook（SSOT）：
- `helloagents/wiki/runbooks/kafka-dlq-replay.md`

### 4.4 生产侧“事务内直接发 Kafka”的风险与 P0 修复（After-Commit）
在事务内直接发送 Kafka 会导致：
- 事务回滚但事件已发出 → 下游收到“幽灵事件”

P0 修复策略：
- 在事务活跃时，将发送动作注册到 `afterCommit()` 回调中执行（After-Commit）
- 发送失败不应回滚已提交事务（P0 仅要求“避免幽灵事件 + 可观测”）

> 注意：After-Commit 只能解决“回滚却发事件”的硬伤，不能解决“提交成功但发送失败导致下游缺数据”的一致性缺口。
> 该缺口需要在 P1 引入 Outbox Pattern（同事务写 outbox 表 + 后台可靠投递 + 可观测堆积）来彻底解决。

### 4.5 消费幂等点位（避免“已 ack 但副作用未落地”）
消费端的幂等表写入点位建议：
- 对“幂等副作用”（如 ES upsert/delete）：**先执行业务副作用，再写入 consumed 表**
  - 副作用失败时允许 Kafka 重试
  - consumed 表写入异常必须触发重试/DLQ（不能吞掉伪装为重复）

### 4.6 事件版本与未知类型/版本处理
事件演进需要显式约定：
- 消费端必须校验 `version` 与 `type`
- 对“不支持版本/未知类型”：
  - 不应写入 consumed 表（避免未来升级后无法回放）
  - 不支持版本：默认进入 DLQ（fail-closed，便于排查与离线回放）
  - 未知类型：由于 topic 按 domain 聚合，默认允许 SKIP 并按 type 去重告警；若该 consumer 订阅的是“单一职责 topic”，可配置为 DLQ

---

## 5. 演进建议（契约优先）

事件契约是跨服务协作边界，演进建议：
- 通过 `version` 做向后兼容（先双写/双读，再切换）
- payload 避免敏感字段（密码/邮箱等）
- 生产端与消费端都要对“未知类型/未知版本”容错（可跳过并记录）
- `entityType/targetType` 等关键枚举值必须以 `contracts-core` 的 SSOT 为准：`com.nowcoder.community.contracts.domain.EntityTypes`

---

## 6. 验收口径（关键约束）
本轮治理的验收建议（可逐步完善为自动化测试/监控告警）：
- 错误协议：全链路（gateway/服务端/前端）对 4xx/5xx 的 status 与 `Result` 载荷处理一致
- fail-closed：关键安全能力（Origin allowlist、请求体上限、限流依赖故障）在缺失/故障时默认拒绝并可观测
- 最终一致：处罚/拉黑状态可在“最终一致窗口”内传播到写服务投影；缺口可通过 internal 扫描/纠偏补齐
- 幂等与失败处理：消费端不会出现“幂等已标记但副作用未落地”的丢失窗口；未知事件不会 silent drop
