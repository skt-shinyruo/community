# 系统设计（同步 API + 异步事件）

本文档聚焦“系统设计层面”的关键点：模块边界、数据流、事件契约、最终一致、幂等与失败处理。其目标是让开发者理解“为什么这样拆”“链路如何走”“如何安全演进”。

本项目当前形态：**`community-app` 主业务单体 + `community-gateway` 统一入口 + `community-im` IM 聚合模块**。默认浏览器流量先进入 `community-gateway`，再路由到 `community-app` 或 IM 服务。

---

## 1. 模块边界（按职责划分）

### 1.1 统一入口：community-gateway + community-app
- 默认浏览器后端入口：`community-gateway` 暴露的 `/api/**`、`/files/**`、`/ws/im`
- `community-app` 仍是主业务 owner，承接主站 `/api/**`、`/files/**`、`/api/ops/**`
- `community-gateway` 负责入口级路由、CORS、traceId、HTTP/WS 边缘策略
- `community-app` 负责业务鉴权矩阵、OriginGuard、审计、统一错误协议

边界与弃用窗口（SSOT）：
- External（对外业务）：`/api/**`
- Files（静态文件）：`/files/**`
- Ops（对外运维）：`/api/ops/**`（ADMIN-only）
- Internal（跨模块同步调用）：**统一通过 owner-domain `api.query` / `api.action` / `api.model` 协作**
  - 约束：尽量避免跨模块 JOIN；跨模块数据聚合优先走 owner-domain API + 批量/缓存
  - 边界：`service`、`entity`、`mapper` 仅作为 owner 域内部实现细节，不再作为默认跨域入口
- Async Internal（跨模块异步协作）：**统一通过 owner-domain `contracts.event` 协作**
  - 当前 contract 形态：`content.contracts.event.*`、`social.contracts.event.*`
  - 边界：producer 域的 `event` 包负责发布与 transport adapter，consumer 不再直接依赖 foreign `event.payload` 或 foreign local-event wrapper

### 1.2 身份与会话：auth（入口）+ user（SSOT）
- auth 模块（对外入口）：登录/刷新/登出闭环（签发 JWT access token + refresh cookie）；验证码、注册/激活、找回密码等账号安全能力
- user 模块（SSOT）：身份域数据与会话状态（`user`、`auth_refresh_token`）归 user 模块（MySQL）管理
  - `auth.refresh.store=db` 时，refresh token 仅存 `token_hash`，便于撤销/旋转（familyId 族）
  - auth ↔ user 的同步协作已收敛为 user owner-domain API（如 `user.api.query.UserCredentialQueryApi`、`user.api.action.UserRegistrationActionApi` 等），不再直接依赖 `user.service` / `user.entity`

### 1.3 业务域模块（示例）
- content：帖子/评论/回复（写主存储并发布事件）
- social：点赞/关注/拉黑（写主存储并发布事件）
- notice：站内通知（消费内容/社交事件写通知）
- community-im：私信实时入口、Kafka backplane、历史查询与未读状态
- search：搜索投影（消费事件写 ES 索引；提供 reindex）
- analytics：统计（Redis，按日/区间查询）

### 1.4 配置中心与 profile（fail-closed）

本项目通过 profile 明确区分“开发便捷”与“生产安全默认态”：

- dev/local：默认使用 `backend/community-app/src/main/resources/application.yml` + 环境变量（`deploy/.env`）。
- prod：必须显式启用 `prod` profile，此时：
  - 启动期校验会启用 fail-closed：关键密钥缺失会直接阻断启动（见 `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java` 与各模块的 `StartupValidator`）
  - 建议通过 secret store / KMS 注入 `JWT_HMAC_SECRET`、metrics basic-auth 等敏感配置，避免默认值上线
- 启动期校验（Startup Validation）：`prod` 下启用启动校验，关键密钥缺失会直接阻断启动（fail-closed），避免“带着默认值上线”。

运维约定：
- 生产部署入口必须显式设置 `SPRING_PROFILES_ACTIVE=prod`（避免 dev/default 默认值误用）。

---

## 2. 同步 API：读写分离的基本形态

### 2.1 读路径（示例：帖子列表）
1. 前端请求 `/api/posts?...`
2. `community-gateway` 将请求路由到 `community-app`
3. `community-app` SecurityFilterChain 按路径规则鉴权（读接口多为 permitAll）
4. `content` 模块读 MySQL/Redis 组装结果返回

### 2.2 写路径（示例：发帖/评论/点赞/关注）
1. 前端请求写接口（携带 JWT）
2. `community-gateway` 将请求路由到 `community-app`
3. `community-app` 统一鉴权/审计（以及关键 cookie 会话入口的 OriginGuard）
4. 目标模块写入主存储（DB/Redis），并通过同进程事务事件驱动后续投影

### 2.3 错误协议（HTTP status + Result.code）
本项目对外 HTTP 统一返回 `Result<T>` 结构；同进程内部 service 协作不再用 `Result<T>` 作为 transport，但仍保持统一异常语义。对外协议同时要求：
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

示例（仓库内单体模块）：
- `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/RewardShopControllerTest.java`

### 2.6 HTTP 写接口幂等（Idempotency-Key）

为避免浏览器重复点击/网络重试导致的重复副作用，本项目对部分 **HTTP 写接口** 启用幂等保护：
- header：`Idempotency-Key: <unique-key>`
- 幂等维度：`userId + operation + Idempotency-Key`
- 行为：
  - 首次请求：执行业务副作用并缓存响应
  - 重复请求：直接复用缓存响应（避免重复写入/重复通知等副作用）
  - 并发同 key：返回 `409`（提示“处理中，可重试”）

配置（SSOT）：`backend/community-app/src/main/resources/application.yml` 的 `http.idempotency.*`
示例：当前仓库暂无脚本；可直接在 HTTP 客户端里设置 header `Idempotency-Key`。

### 2.6.1 核心组件与状态模型

幂等能力的核心组件位于：
- `backend/community-app/src/main/java/com/nowcoder/community/infra/idempotency/IdempotencyGuard.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/idempotency/IdempotencyStore.java`

`IdempotencyGuard` 不是业务逻辑本身，而是一个“包裹写操作的统一执行器”：
- 业务方把真正会产生副作用的逻辑放进 `executeRequired(..., supplier)` 中。
- Guard 先判断这次请求是“首次执行”“处理中并发重复”还是“已成功的重复请求”，再决定执行、拒绝还是复用旧结果。

底层共享状态只有两种：
- `PROCESSING`：表示该幂等 key 已被某个请求占用，业务仍在执行中。
- `SUCCESS`：表示该 key 已成功执行过，并保存了成功响应 JSON，可直接复用。

### 2.6.2 执行流程

一次 `executeRequired(operation, userId, idempotencyKey, type, supplier)` 的执行过程如下：

1. 参数校验
- 校验 `userId`、`operation`、`supplier` 非空且合法。
- 校验 `Idempotency-Key` 存在且长度不超过 128；对必须幂等的入口，缺失 key 直接返回 `400`。

2. 规范化输入与 TTL
- `operation` 会统一转成小写并去空白。
- `Idempotency-Key` 会做 `trim()`。
- 读取 `processingTtl` 和 `successTtl` 两个配置项；默认分别为 `30s` 和 `24h`。

3. 抢占 `PROCESSING`
- Guard 调用 `IdempotencyStore.tryAcquireProcessing(...)` 尝试占用幂等 key。
- 若成功，说明本次请求是第一次执行；若失败，说明该 key 已存在，可能是并发请求，也可能是历史成功重试。

4. 首次请求执行真实副作用
- Guard 调用 `supplier.get()` 执行业务逻辑，例如发帖、发表评论。
- 如果业务逻辑抛异常，Guard 会删除 `PROCESSING` 状态，允许后续重试，而不是让 key 永久卡死在处理中。

5. 成功后写入 `SUCCESS`
- 业务逻辑成功后，Guard 会把返回值序列化为 JSON，并调用 `saveSuccess(...)` 记录成功状态。
- 后续同 key 的重复请求可直接从该 JSON 反序列化出结果并返回。

6. 非首次请求的分支处理
- 若读取到 `SUCCESS`：直接复用旧响应，不再重复执行业务副作用。
- 若读取到 `PROCESSING`：返回 `409`，提示调用方稍后重试。
- 若状态缺失或不合法：返回 `503`，避免在不确定状态下继续写入。

### 2.6.3 当前仓库的存储实现

自动装配支持两种 `IdempotencyStore`：
- `JdbcIdempotencyStore`：当前仓库默认实现，见 `http.idempotency.store=DB`
- `RedisIdempotencyStore`：可选实现，适合作为共享缓存式幂等存储

当前仓库默认启用 DB 方案，配置位于：
- `backend/community-app/src/main/resources/application.yml`

DB 方案特点：
- 依赖表 `http_idempotency`
- 唯一键为 `(operation, user_id, idem_key)`
- 通过 insert-first + 唯一约束实现“谁先占到 key，谁先执行”
- 表内同时保存 `status`、`response_json`、`processing_expires_at`、`success_expires_at`

Redis 方案特点：
- 使用 `SETNX + TTL` 抢占 `PROCESSING`
- 使用普通 `SET` 覆盖写入 `SUCCESS + response_json`
- 更轻，但在 Redis 抖动时会把幂等依赖直接暴露在写链路上

### 2.6.4 当前接入点

当前仓库已在以下业务链路接入 `IdempotencyGuard`：
- 发帖：`POST /api/posts`
- 发表评论：`POST /api/posts/{postId}/comments`

这些链路的共同特点是：
- 都属于 HTTP 写接口
- 都存在浏览器重复点击、超时重试、网关重试导致重复副作用的风险
- 都缺少一个天然稳定且对业务方透明的“唯一业务单号”

因此这里选择 `Idempotency-Key` 比“单纯依赖请求体去重”更合理，也比把幂等逻辑散落进各业务 service 更易治理。

### 2.6.5 为什么采用这种方案

这是一个主流且务实的 HTTP 幂等方案，原因主要有四点：
- 契约明确：客户端通过 `Idempotency-Key` 显式声明“这是同一次业务尝试”，服务端不需要猜测请求体是否重复。
- 多实例可用：幂等状态放在共享存储里，应用扩容后依然成立。
- 侵入性低：业务代码只需要把真实副作用包进 `executeRequired(...)`，不必每个接口各自实现去重。
- 适用面广：对发帖、评论这类“没有天然业务唯一单号”的 HTTP 写接口尤其合适。

与常见替代方案相比：
- 比“前端按钮防抖”更可靠，因为最终一致性边界仍在服务端。
- 比“单机内存锁”更适合多实例部署。
- 比“数据库唯一索引只防并发”更进一步，因为它还能复用历史成功响应。

### 2.6.6 设计边界与权衡

这套方案是“高性价比的实用型幂等”，不是最强一致性的 exactly-once 方案。

主要边界如下：
- 业务副作用成功与 `saveSuccess(...)` 之间不是单事务提交；极端故障下，第一次请求已成功，但成功状态未完全落盘，后续重试的防重能力会下降。
- `processingTtl` 配置过短时，慢链路下可能出现锁过期，随后第二个请求重新获取执行权的理论窗口。
- 该方案依赖客户端正确复用 `Idempotency-Key`；如果每次重试都换新 key，服务端不会把它们视为同一次请求。
- 结果复用依赖返回值序列化；如果返回对象难以稳定序列化，Guard 会优先保证第一次成功请求正常返回，而不是把整个请求打成失败。

这也是当前实现选择 fail-closed 的原因：
- 对“必须幂等”的入口，如果幂等存储不可用，宁可返回 `503`，也不绕过保护继续写入。
- 其目标不是提升可用性表面数值，而是降低重复副作用风险。

---

## 3. 异步事件：本地事务事件（最终一致）

### 3.1 Topic 约定（SSOT）
当前单体业务链路：投影/通知通过本地 DB outbox 实现（不依赖 Kafka）。

IM 链路：使用 Kafka 作为 backplane（topic 常量见 `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/contracts/ImTopics.java`）。

私信写路径的实际入口已收敛为：
1. 客户端通过 WebSocket `sendPrivateText` 向 `community-gateway` 暴露的 `/ws/im` 发起请求
2. `community-gateway` 将连接转发到 `im-realtime`
3. `im-realtime` 先调用 `community-app` 的 `POST /api/im-governance/private-messages/validate` 做治理校验
4. 治理通过后，`im-realtime` 把 command 写入 Kafka
5. `im-core` 消费 Kafka command 并完成私信持久化；历史查询、会话列表与未读状态则通过 `/api/im/**` 暴露

### 3.2 事件契约：Owner-Domain Contracts（SSOT）
当前 `community-app` 内部跨域事件 contract 由 owner-domain 显式暴露：
- `content.contracts.event.ContentContractEvent` + `content.contracts.event.*`
- `social.contracts.event.SocialContractEvent` + `social.contracts.event.*`

这些 contract 由 producer 域拥有并版本化，consumer 只理解 contract，不再直接 import producer 的 `event.payload` 或本地 `event` 实现包。

`common.event.EventEnvelope` 仍保留为通用 envelope 能力，但它不是当前包级单体内部投影协作的默认 contract 入口；本地投影路径以 owner-domain `contracts.event` 为准。

### 3.3 事务后本地监听
当前实现采用：
1. 写模块在 DB 事务内发布 owner-domain contract event（例如 `ContentContractEvent`、`SocialContractEvent`）
2. owner-domain projection service 先统一完成“该事件是否需要投影、投影目标是什么”的业务判定
3. 对“必须最终达成”的投影（通知/积分/搜索）使用 `@TransactionalEventListener(phase = BEFORE_COMMIT)` 写入 `community.outbox_event`（同事务提交）
4. 本地 `@Scheduled` outbox worker 轮询并执行投影 handler（失败自动重试，最终一致）
5. 若关闭 outbox，本地 `AFTER_COMMIT` listener 仍走同一个 projection service；监听器本身不拥有业务分支，避免双路径同构逻辑长期漂移
6. 对“尽力而为”的本地副作用（如热度队列）仍使用 after-commit 执行，并在监听方做兜底（不影响主链路）

当前已经显式收口到 shared projection service 的链路包括：
- notice projection：`NoticeProjectionService`
- user points：`PointsProjectionService`
- growth task progress：`TaskProgressProjectionService`

下一阶段目标：
- 继续缩小 ArchUnit migration baseline，把剩余 foreign `service` / `exception` / `session` 依赖迁回 owner-domain API
- 在语义稳定后，再把 `api` / `contracts` / `impl` 进一步拆成独立 Maven artifact，避免继续依赖“包结构自律”

### 3.4 典型消费方（最终一致）
- notice：消费评论/社交事件，生成通知
- search：消费帖子/评论等事件，更新 ES 索引

说明：这里的“消费”指单体内的事务事件 + outbox handler，并非通过 Kafka 订阅 `community.event.*`。

补充：搜索属于“事件驱动投影”，因此天然最终一致：
- 写成功后到可搜索存在短暂延迟（事务提交 + 本地监听 + ES refresh）
- 若出现长时间缺失/冷启动缺口：优先排查监听链路与 ES 健康，必要时执行 reindex（`POST /api/ops/search/reindex`）。

### 3.5 离散后台任务调度（XXL-JOB）
当前仓库把后台工作分成两类：

1. 持续型本地 worker
   - 例如 outbox worker、帖子热度刷新
   - 继续留在应用内 `@Scheduled`，追求低延迟和持续轮询
2. 离散型清理/运维任务
   - 例如 `pendingRegistrationUserCleanup`
   - 例如 `searchReindex`
   - 通过 `xxl-job-admin` 统一调度和记录执行历史

phase 1 的运行边界是：
- `community-app` 是唯一 XXL executor
- `pendingRegistrationUserCleanup` 与 `searchReindex` 通过 XXL handler 进入业务 service
- `PendingRegistrationUserCleanupJob` 仅在无 admin 的本地开发环境里保留兜底；这份 compose 栈作为 XXL-enabled 路径会显式关闭本地 scheduler，避免双跑
- `PostScoreRefresher` 与 `OutboxWorkerScheduler` 明确保留本地调度，不迁入 XXL

---

## 4. 同进程内部回源（去投影，直接 service 协作）

按当前需求取舍：仓库已移除跨域本地投影（`*_projection` 表、Redis 投影、投影消费者与 backfill 入口），统一改为 **同进程直接 service 实时回源 SSOT**。这里列出的 direct service 协作仍属于 migration baseline，不是最终目标边界；后续仍应优先收敛为 owner-domain `api.*`。

典型场景（均为进程内调用，非网络调用）：
- content/message 写路径反骚扰（拉黑校验）：直接调 `social` 的 `BlockService`（默认 fail-closed）
- content/message 写路径处罚状态守卫：直接调 `user` 的 `UserModerationService#moderationStatus`（默认 fail-closed）
- social 写路径可信解析（entity resolve）：直接回源 `content` 的 `ContentEntityService`（默认 fail-closed）
- user 读路径聚合展示（主页点赞/关注/粉丝）：直接调 `social` 的 `LikeService`/`FollowService` 同步组装结果，当前不提供配置驱动的 fail-open 降级开关，异常按调用链直接返回

风险与约束：
- 同步依赖链会让“模块边界”更显性；因此需要强约束编译期依赖图（禁止环）
- 避免 N+1：优先批量接口或短 TTL 缓存（容量受控）
- 对关键路径明确 fail-open/fail-closed，并在调用方直接记录指标/日志与错误码（HTTP 503 等）

---

## 5. 幂等与失败处理（P0）

### 5.1 投影幂等（单体）
本仓库默认使用本地事务事件 + DB outbox：
- 幂等点位在 outbox 的唯一键（`outbox_event.event_id`）与投影处理器的“读当前状态再写入”（如 search 投影）。

说明：历史上的 `consumed_event/search_consumed_event` 表在当前单体运行路径下不再使用。

### 5.2 Outbox worker 的最小正确性（P0）
Outbox worker 的最小正确性目标是：**标记 succeeded 之前，投影副作用必须已成功提交**。因此：
- handler 内部需要把副作用做成幂等（或可重试）
- worker 失败应可重试（带退避）且可观测（error 日志 + retry_count）

### 5.4 投影幂等点位（避免“标记成功但副作用未落地”）
本地 outbox 处理应确保副作用尽可能幂等；对幂等副作用（如 ES upsert/delete）优先保持“副作用本身幂等”。

### 5.5 事件版本与未知类型/版本处理
事件演进需要显式约定：
- 对 outbox payload（投影 topic）：只由本仓库生成，handler 只处理已知结构；未知/坏数据应 fail-closed 并可重试/进入 DEAD。
- 对 IM Kafka（跨进程）：按 topic/版本做兼容；不支持版本建议进入 DLQ（fail-closed，便于排查与回放）。

---

## 6. 演进建议（契约优先）

事件契约是跨服务协作边界，演进建议：
- 通过 `version` 做向后兼容（先双写/双读，再切换）
- payload 避免敏感字段（密码/邮箱等）
- 生产端与消费端都要对“未知类型/未知版本”容错（可跳过并记录）
- `entityType/targetType` 等关键枚举值以 `com.nowcoder.community.common.constants.EntityTypes` 为准。

---

## 7. 验收口径（关键约束）
本轮治理的验收建议（可逐步完善为自动化测试/监控告警）：
- 错误协议：全链路（服务端/前端）对 4xx/5xx 的 status 与 `Result` 载荷处理一致
- fail-closed：关键安全能力（Origin allowlist、请求体上限、限流依赖故障）在缺失/故障时默认拒绝并可观测
- 最终一致：处罚/拉黑状态可在“最终一致窗口”内传播到写服务投影；缺口可通过 internal 扫描/纠偏补齐
- 幂等与失败处理：消费端不会出现“幂等已标记但副作用未落地”的丢失窗口；未知事件不会 silent drop
