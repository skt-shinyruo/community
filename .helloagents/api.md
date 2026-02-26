# API 手册

## 1. 总览

- **对外入口：** 统一由 `gateway` 暴露 `/api/**`（本地默认 `http://localhost:12882`）。
- **鉴权：** 以 JWT Access Token 为主（`Authorization: Bearer <token>`）；刷新/登出等策略由 `auth-service` 管理。
- **统一返回：** 后端统一返回 `Result<T>`（`code/message/data/traceId/timestamp`）。
- **错误协议：** 错误场景允许返回 **HTTP 4xx/5xx**（HTTP status 表达“错误类别”），响应体仍保持 `Result` 结构（`Result.code` 表达“业务细分”）。
- **Trace：** `gateway` 注入并透传 `X-Trace-Id`，各服务在响应中回传该 header，便于跨服务串联日志。

> SSOT：网关路由以 `gateway/src/main/resources/application.yml` 为准。

---

## 2. 网关路由与服务归属（SSOT）

- `/api/auth/**` → `auth-service`
- `/api/users/**` → `user-service`
- `/api/posts/**` → `content-service`
- `/api/reports/**`、`/api/moderation/**` → `content-service`
- `/api/bookmarks/**`、`/api/subscriptions/**` → `content-service`
- `/api/likes/**`、`/api/follows/**` → `social-service`
- `/api/blocks/**` → `social-service`
- `/api/messages/**`、`/api/notices/**` → `message-service`
- `/api/search/**` → `search-service`
- `/api/analytics/**` → `analytics-service`
- `/api/ops/**` → 运维入口（默认仅管理员可访问；具体以网关路由与权限矩阵为准）

---

## 3. 内部调用（Dubbo RPC 为主）

### 3.1 Dubbo RPC（服务间同步调用，推荐）

说明：服务间同步调用已从 HTTP internal client 迁移为 Dubbo RPC（默认 Nacos registry；必要时可用 `DUBBO_REGISTRY_ADDR` 覆盖）。
RPC 契约与 DTO 统一沉淀在 `*-api` 模块（consumer/provider 共同依赖），避免跨服务依赖实现模块导致的循环依赖与演进失控。

- `user-api`：`UserInternalRpcService` / `UserReadRpcService` / `UserModerationRpcService`
- `social-api`：`SocialReadRpcService` / `SocialBlockRpcService` / `SocialOutboxRpcService`
- `content-api`：`ContentScanRpcService` / `ContentEntityRpcService` / `ContentOutboxRpcService`
- `analytics-api`：`InternalAnalyticsRpcService`（gateway 采集 best-effort 调用）

约定（Best Practices）：
- **返回协议：** RPC 统一返回 `Result<T>`（跨服务错误语义与错误码口径一致）。
- **Trace/观测：** 通过 Dubbo attachment 透传 `X-Trace-Id/traceparent`；consumer/provider 侧统一埋点调用次数/时延。
- **安全边界：** 禁止在 RPC attachment 中透传用户 `Authorization`；只允许透传观测字段（traceId 等）。

### 3.2 运维入口（/api/ops/**）

说明：对外运维入口统一通过 `gateway` 路由到独立的 `ops-service`（`/api/ops/**`）；`ops-service` 再通过 Dubbo RPC 调用对应服务。

这样做的目的：将高风险/高成本运维能力从主转发面隔离，避免运维代码/依赖变更影响全站 `/api/**` 的爆炸半径。服务端不再提供 HTTP `/internal/**` 运维入口（避免 internal HTTP 与 RPC 并存导致长期“半迁移”治理债务）。

- `POST /api/ops/search/reindex`：重建索引（search-service，高成本）
- `GET /api/ops/content/outbox/health`：outbox 健康检查（content-service）
- `POST /api/ops/content/outbox/replay?limit=200`：重放失败 outbox（content-service）
- `GET /api/ops/social/outbox/health`：outbox 健康检查（social-service）
- `POST /api/ops/social/outbox/replay?limit=200`：重放失败 outbox（social-service）
- `GET /api/ops/user/outbox/health`：outbox 健康检查（user-service）
- `POST /api/ops/user/outbox/replay?limit=200`：重放失败 outbox（user-service）

legacy（固定返回 410）：
- `POST /api/search/internal/reindex`：历史遗留命名；固定返回 410 并提示迁移到 `/api/ops/search/reindex`

---

## 4. Controller 入口索引（快速定位代码）

- **ops-service（ops handlers）**：
  - `ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`
- **auth-service**：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`
- **user-service**：`user/user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`
- **user-service**：`user/user-service/src/main/java/com/nowcoder/community/user/api/LeaderboardController.java`
- **content-service**：
  - `content/content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
  - `content/content-service/src/main/java/com/nowcoder/community/content/api/BookmarkController.java`
  - `content/content-service/src/main/java/com/nowcoder/community/content/api/SubscriptionController.java`
  - `content/content-service/src/main/java/com/nowcoder/community/content/api/ReportController.java`
  - `content/content-service/src/main/java/com/nowcoder/community/content/api/ModerationController.java`
- **social-service**：
  - `social/social-service/src/main/java/com/nowcoder/community/social/like/LikeController.java`
  - `social/social-service/src/main/java/com/nowcoder/community/social/follow/FollowController.java`
  - `social/social-service/src/main/java/com/nowcoder/community/social/block/BlockController.java`
- **message-service**：
  - `message-service/src/main/java/com/nowcoder/community/message/api/MessageController.java`
  - `message-service/src/main/java/com/nowcoder/community/message/api/NoticeController.java`
- **search-service**：
  - `search/search-service/src/main/java/com/nowcoder/community/search/api/SearchController.java`
  - `search/search-service/src/main/java/com/nowcoder/community/search/api/SearchLegacyController.java`（legacy 410 stub）
- **analytics-service**：
  - `analytics/analytics-service/src/main/java/com/nowcoder/community/analytics/api/AnalyticsController.java`

---

## 5. 错误处理与安全约定

- **错误码：**
  - 通用错误码：`contracts-core/src/main/java/com/nowcoder/community/contracts/api/CommonErrorCode.java`
  - 鉴权域错误码：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthErrorCode.java`
  - 用户域错误码：`user/user-api/src/main/java/com/nowcoder/community/user/api/UserErrorCode.java`
  - 内容域错误码：`content/content-api/src/main/java/com/nowcoder/community/content/api/ContentErrorCode.java`
  - 社交域错误码：`social/social-api/src/main/java/com/nowcoder/community/social/api/SocialErrorCode.java`
  - 消息域错误码：`message-service/src/main/java/com/nowcoder/community/message/api/MessageErrorCode.java`
  - 搜索域错误码：`search/search-api/src/main/java/com/nowcoder/community/search/api/SearchErrorCode.java`
  - 统计域错误码：`analytics/analytics-api/src/main/java/com/nowcoder/community/analytics/api/AnalyticsErrorCode.java`
  - 网关域错误码：`gateway/src/main/java/com/nowcoder/community/gateway/api/GatewayErrorCode.java`
- **安全：**
  - 对外接口统一走 gateway 路由与 CORS；业务鉴权以各服务为 SSOT（gateway 仅对 `/api/ops/**`、`/internal/**` 等边界路径做收敛与双保险）。
  - 运维能力统一收敛到 `/api/ops/**`（ADMIN）；`/internal/**` 在代码层已移除，且 gateway 显式拒绝 `/internal/**`。
  - 未认证（Authentication 缺失）统一返回 401（`UNAUTHORIZED`），避免把鉴权问题伪装成参数错误（400）。
