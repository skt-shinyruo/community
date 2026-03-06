# 系统设计（同步 API + 异步事件）

本文档聚焦“系统设计层面”的关键点：模块边界、数据流、事件契约、最终一致、幂等与失败处理。其目标是让开发者理解“为什么这样拆”“链路如何走”“如何安全演进”。

本项目当前形态：**A-1 模块化单体（Modular Monolith）**。后端整体一起发布，对外只有一个进程：`community-app`。

---

## 1. 模块边界（按职责划分）

### 1.1 统一入口：community-app（HTTP edge）
- 浏览器唯一后端入口：`/api/**`
- 静态文件入口：`/files/**`（头像等）
- 对外运维入口：`/api/ops/**`（高风险操作；仅管理员可触发，建议通过 Ops Console 等受控入口执行）
- 统一能力（应用内横切）：鉴权（JWT resource server + 路径级授权矩阵）、CORS、OriginGuard（仅 cookie 会话入口）、审计、traceId、统一错误协议

边界与弃用窗口（SSOT）：
- External（对外业务）：`/api/**`
- Files（静态文件）：`/files/**`
- Ops（对外运维）：`/api/ops/**`（ADMIN-only）
- Internal（跨模块同步调用）：**进程内接口调用**（契约在 `*-api` 或 `backend/platform/contracts-core`）
  - 约束：尽量避免跨模块 JOIN；跨模块数据聚合优先走“内部接口 + 批量/缓存”

### 1.2 身份与会话：auth（入口）+ user（SSOT）
- auth 模块（对外入口）：登录/刷新/登出闭环（签发 JWT access token + refresh cookie）；验证码、注册/激活、找回密码等账号安全能力
- user 模块（SSOT）：身份域数据与会话状态（`user`、`auth_refresh_token`）归 user 模块（MySQL）管理
  - `auth.refresh.store=db` 时，refresh token 仅存 `token_hash`，便于撤销/旋转（familyId 族）
  - A-1 下 auth ↔ user 的交互是同进程内部接口调用（保留 `*RpcService` 命名以维持契约边界）

### 1.3 业务域模块（示例）
- content：帖子/评论/回复（写主存储并发布事件）
- social：点赞/关注/拉黑（写主存储并发布事件）
- message：私信/通知（消费事件写通知）
- search：搜索投影（消费事件写 ES 索引；提供 reindex）
- analytics：统计（Redis，按日/区间查询）

### 1.4 配置中心与 profile（fail-closed）

本项目通过 profile 明确区分“开发便捷”与“生产安全默认态”：

- dev/local：默认使用 `backend/community-bootstrap/src/main/resources/application.yml` + 环境变量（`deploy/.env`）。
- prod：必须显式启用 `prod` profile，此时：
  - `backend/platform/common` 的 `StartupValidation` 会启用启动期校验：关键密钥缺失会直接阻断启动（fail-closed）
  - 建议通过 secret store / KMS 注入 `JWT_HMAC_SECRET`、metrics basic-auth 等敏感配置，避免默认值上线
- 启动期校验（Startup Validation）：`common` 在 `prod` 下启用启动校验，关键密钥缺失会直接阻断启动（fail-closed），避免“带着默认值上线”。

运维约定：
- 生产部署入口必须显式设置 `SPRING_PROFILES_ACTIVE=prod`（避免 dev/default 默认值误用）。

---

## 2. 同步 API：读写分离的基本形态

### 2.1 读路径（示例：帖子列表）
1. 前端请求 `/api/posts?...`
2. `community-app` SecurityFilterChain 按路径规则鉴权（读接口多为 permitAll）
3. `content` 模块读 MySQL/Redis 组装结果返回

### 2.2 写路径（示例：发帖/评论/点赞/关注）
1. 前端请求写接口（携带 JWT）
2. `community-app` 统一鉴权/审计（以及关键 cookie 会话入口的 OriginGuard）
3. 目标模块写入主存储（DB/Redis），并通过同进程事务事件驱动后续投影

### 2.3 错误协议（HTTP status + Result.code）
本项目对外（以及内部）统一返回 `Result<T>` 结构，但同时要求：
- **HTTP status：表达错误类别**（4xx/5xx，便于监控/前端统一处理）
- **Result.code：表达业务细分**（如 `AuthErrorCode` 10001+）

基本约定：
- 参数错误：HTTP 400 + `Result.code=400`
- 未登录/令牌无效：HTTP 401 + `Result.code=10003/401`（业务码优先表达细分）
- 无权限：HTTP 403
- 依赖故障（DB/Redis/ES/关键基础设施不可用）：HTTP 503（fail-closed）
- 未捕获异常：HTTP 500

前端 Axios 约定：
- 对非 2xx 响应仍尝试解析 `Result` 并展示 message/traceId
- 401 的 refresh 逻辑需区分“可刷新/不可刷新”，避免登录失败误触发 refresh

> 当前实现优先保持单进程语义：事务内发布领域事件，事务后触发本地监听，避免把网络/队列语义引入同 JVM 内的协作。
> 并补齐“提交成功但发送失败导致下游缺数据”的一致性缺口（可重试/可观测/可回放）。

### 2.4 refresh cookie 与 CSRF（SameSite + OriginGuard）

本项目 refresh token 采用 **HttpOnly cookie** 存储（防止被 JS 读取），因此需要同时关注：
- cookie 级别的 `SameSite`/`Secure` 策略
- 请求级别的 Origin 校验（OriginGuard）来降低 CSRF/旁路风险

策略约定（SSOT）：
- **同源部署（推荐）**：
  - refresh cookie：`SameSite=Lax`（或 Strict，视业务跳转而定）
  - prod：refresh cookie `Secure=true`（HTTPS）
  - `community-app`：启用 OriginGuard（仅覆盖 login/refresh/logout），同源请求始终放行
- **跨站部署（谨慎）**：
  - refresh cookie：必须 `SameSite=None` 且 `Secure=true`
  - OriginGuard allowlist 必须配置且在 prod 下 allowlist 为空时 **fail-closed**
  - CORS：必须 `allowCredentials=true` 且 `allowedOrigins` 精确匹配（禁止 `*`）

详细说明见：`docs/SECURITY.md`。

### 2.5 API DTO 与契约测试（字段白名单）

为避免“直接返回 entity 导致字段泄露/契约被数据库结构绑定”，公共接口应返回 DTO（字段白名单）。

约定：
- 公共读接口（例如评论/回复列表）不得暴露治理字段（如 `status`、`deletedReason` 等）
- 契约通过回归测试固化，避免后续重构/联表扩展时不小心把字段带出

示例：
- `message/message-service/src/test/java/com/nowcoder/community/message/api/MessageControllerTest.java`

### 2.6 HTTP 写接口幂等（Idempotency-Key）

为避免浏览器重复点击/网络重试导致的重复副作用，本项目对部分 **HTTP 写接口** 启用幂等保护：
- header：`Idempotency-Key: <unique-key>`
- 幂等维度：`userId + operation + Idempotency-Key`
- 行为：
  - 首次请求：执行业务副作用并缓存响应
  - 重复请求：直接复用缓存响应（避免重复写入/重复通知等副作用）
  - 并发同 key：返回 `409`（提示“处理中，可重试”）

配置（SSOT）：`backend/community-bootstrap/src/main/resources/application.yml` 的 `http.idempotency.*`
示例脚本：`backend/scripts/curl-idempotent-post.sh`

---

## 3. 异步事件：本地事务事件（最终一致）

### 3.1 Topic 约定（SSOT）
事件 topic 的 SSOT：`backend/platform/contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventTopics.java`：
- `community.event.post.v1`
- `community.event.comment.v1`
- `community.event.social.v1`
- `community.event.moderation.v1`
- 写路径与投影/通知解耦，但运行时不再依赖 Kafka/Outbox。

### 3.2 事件契约：Envelope + 校验（SSOT）
代码位置（SSOT）：`backend/platform/contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventEnvelope.java`。

事件消息使用统一 envelope（概念字段）：
- `eventId`：全局唯一，用于幂等
- `traceId`：贯穿请求链路，便于日志串联
- `type`：事件类型（如 `PostPublished`、`CommentCreated`）
- `version`：事件版本（当前为 v1）
- `occurredAt`：发生时间
- `producer`：生产者模块名
- `payload`：具体数据（避免敏感字段）

unknown handling 为可配置策略（consumer 级别）：
### 3.3 事务后本地监听
当前实现采用：
1. 写模块在 DB 事务内发布领域事件
2. `@TransactionalEventListener(phase = AFTER_COMMIT)` 在事务提交后驱动搜索投影、通知、积分、处罚命令等后续动作
3. reindex 等高成本入口通过单进程 single-flight 控制并发

### 3.4 典型消费方（最终一致）
- message：消费评论/社交事件，生成通知
- search：消费帖子/评论等事件，更新 ES 索引

补充：搜索属于“事件驱动投影”，因此天然最终一致：
- 写成功后到可搜索存在短暂延迟（事务提交 + 本地监听 + ES refresh）
- 若出现长时间缺失/冷启动缺口：优先排查监听链路与 ES 健康，必要时执行 reindex（`POST /api/ops/search/reindex` 或 `backend/scripts/search-reindex.sh`）

---

## 4. 同进程内部接口回源（去投影，契约优先）

按当前需求取舍：仓库已移除跨域本地投影（`*_projection` 表、Redis 投影、投影消费者与 backfill 入口），统一改为 **同进程内部接口实时回源 SSOT**。

典型场景（均为进程内接口调用，非网络 RPC）：
- content/message 写路径反骚扰（拉黑校验）：调 `social` 的 `SocialBlockRpcService#isEitherBlocked`（默认 fail-closed）
- content/message 写路径处罚状态守卫：调 `user` 的 `UserModerationRpcService#getStatus`（默认 fail-closed）
- social 写路径可信解析（entity resolve）：回源 `content` SSOT（`EntityResolveRpcService#resolveEntity`，默认 fail-closed）
- user 读路径聚合展示（主页点赞/关注/粉丝）：调 `social` 的 read RPC（展示类读路径允许按配置 fail-open）

风险与约束：
- 同步依赖链会让“模块边界”更显性；因此需要强约束编译期依赖图（禁止环）
- 避免 N+1：优先批量接口或短 TTL 缓存（容量受控）
- 对关键路径明确 fail-open/fail-closed，并把异常映射成可观测的错误码（HTTP 503 等）

---

## 5. 幂等与失败处理（P0）

### 5.1 消费幂等（eventId 去重）
消费端通过记录已消费的 `eventId` 来保证幂等：
- message 模块：`consumed_event` 表
- search 模块：`search_consumed_event` 表

这能避免：
- 事务后监听重复触发导致的重复副作用（例如重复通知、重复索引更新）

### 5.2 消费侧“事务 + ack”的最小正确性（P0）
消费端最小正确性目标是：**ack 之前，业务副作用必须已成功提交**。因此：
- Listener 仅做“调用处理器 + 成功后 ack”
- 处理器使用 `@Transactional`，并确保幂等记录与业务写入同事务提交
- 避免“同类内部调用导致事务不生效”的自调用陷阱（建议拆分为独立 `@Service`）

### 5.4 消费幂等点位（避免“已 ack 但副作用未落地”）
本地监听仍应确保副作用尽可能幂等；对幂等副作用（如 ES upsert/delete）优先保持“副作用本身幂等”，避免额外引入本地消息基础设施。

### 5.5 事件版本与未知类型/版本处理
事件演进需要显式约定：
- 消费端必须校验 `version` 与 `type`
- 对“不支持版本/未知类型”：
  - 不应写入 consumed 表（避免未来升级后无法回放）
  - 不支持版本：默认进入 DLQ（fail-closed，便于排查与离线回放）
  - 未知类型：由于 topic 按 domain 聚合，默认允许 SKIP 并按 type 去重告警；若该 consumer 订阅的是“单一职责 topic”，可配置为 DLQ

---

## 6. 演进建议（契约优先）

事件契约是跨服务协作边界，演进建议：
- 通过 `version` 做向后兼容（先双写/双读，再切换）
- payload 避免敏感字段（密码/邮箱等）
- 生产端与消费端都要对“未知类型/未知版本”容错（可跳过并记录）
- `entityType/targetType` 等关键枚举值必须以 `backend/platform/contracts-core` 的 SSOT 为准：`com.nowcoder.community.contracts.domain.EntityTypes`

---

## 7. 验收口径（关键约束）
本轮治理的验收建议（可逐步完善为自动化测试/监控告警）：
- 错误协议：全链路（服务端/前端）对 4xx/5xx 的 status 与 `Result` 载荷处理一致
- fail-closed：关键安全能力（Origin allowlist、请求体上限、限流依赖故障）在缺失/故障时默认拒绝并可观测
- 最终一致：处罚/拉黑状态可在“最终一致窗口”内传播到写服务投影；缺口可通过 internal 扫描/纠偏补齐
- 幂等与失败处理：消费端不会出现“幂等已标记但副作用未落地”的丢失窗口；未知事件不会 silent drop
