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

说明：服务间同步调用已从 HTTP internal client 迁移为 Dubbo RPC（Zookeeper registry）。
RPC 契约与 DTO 统一沉淀在 `*-api` 模块（consumer/provider 共同依赖），避免跨服务依赖实现模块导致的循环依赖与演进失控。

- `user-api`：`UserInternalRpcService` / `UserReadRpcService` / `UserModerationRpcService`
- `social-api`：`SocialReadRpcService` / `SocialBlockRpcService` / `SocialLikeScanRpcService`
- `content-api`：`ContentScanRpcService` / `ContentEntityRpcService`
- `analytics-api`：`InternalAnalyticsRpcService`（gateway 采集 best-effort 调用）

约定（Best Practices）：
- **返回协议：** RPC 统一返回 `Result<T>`（跨服务错误语义与错误码口径一致）。
- **Trace/观测：** 通过 Dubbo attachment 透传 `X-Trace-Id/traceparent`；consumer/provider 侧统一埋点调用次数/时延。
- **安全边界：** 禁止在 RPC attachment 中透传用户 `Authorization`；只允许透传观测字段（traceId 等）。

### 3.2 HTTP internal（/internal/**，运维/兼容）

说明：`/internal/**` 主要保留用于运维入口/兼容调试。
开发阶段（当前实现）：internal HTTP 端点默认不再要求 header token 鉴权；生产建议通过网络隔离/仅内网可达收敛暴露面，且避免在 gateway 中对外暴露 `/internal/**`。

- **content-service**
  - `GET /internal/content/posts`：legacy（原供 search-service reindex 扫描；现推荐使用 Dubbo `ContentScanRpcService`）。
  - `GET /internal/content/entities/resolve?entityType=&entityId=`：legacy（原供 social 写路径 resolve；现推荐使用 Dubbo `ContentEntityRpcService`）。
  - `POST /internal/content/likes/backfill?entityType=&maxItems=&batchSize=`：回填 Redis 点赞投影（运维入口，默认关闭；需显式开启 `content.like.backfill.endpoint-enabled=true`）。
- **social-service**
  - `GET /internal/social/blocks/relation?userIdA=&userIdB=`：legacy（现推荐使用 Dubbo `SocialBlockRpcService`）。
  - `GET /internal/social/likes/scan?entityType=&afterEntityId=&afterUserId=&limit=`：legacy（现推荐使用 Dubbo `SocialLikeScanRpcService`）。
  - `GET /internal/social/read/users/{userId}/profile-stats?viewerId=`：legacy（现推荐使用 Dubbo `SocialReadRpcService`）。
- **user-service**
  - `GET /internal/users/{userId}/moderation-status`：legacy（现推荐使用 Dubbo `UserModerationRpcService`）。
  - `POST /internal/users/{userId}/moderation`：legacy（现推荐使用 Dubbo `UserModerationRpcService`）。
  - outbox 运维（开发阶段默认放行；生产建议通过网络隔离/网关策略收敛暴露面）：
    - `GET /internal/users/outbox/health`
    - `POST /internal/users/outbox/replay?limit=200`
- **search-service**
  - `POST /internal/search/reindex`：重建索引内部入口。
    - 对外运维入口（推荐）：`POST /api/ops/search/reindex`
    - 历史兼容入口（弃用中，默认禁用）：`POST /api/search/internal/reindex`（gateway 通过 blocked-path-patterns 关闭，按 404 拒绝并提示迁移）
- **analytics-service**
  - `POST /internal/analytics/uv/record`：legacy（现推荐使用 Dubbo `InternalAnalyticsRpcService`）。
  - `POST /internal/analytics/dau/record`：legacy（现推荐使用 Dubbo `InternalAnalyticsRpcService`）。

---

## 4. Controller 入口索引（快速定位代码）

- **auth-service**：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`
- **user-service**：`user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`
- **user-service**：`user-service/src/main/java/com/nowcoder/community/user/api/LeaderboardController.java`
- **content-service**：
  - `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
  - `content-service/src/main/java/com/nowcoder/community/content/api/BookmarkController.java`
  - `content-service/src/main/java/com/nowcoder/community/content/api/SubscriptionController.java`
  - `content-service/src/main/java/com/nowcoder/community/content/api/ReportController.java`
  - `content-service/src/main/java/com/nowcoder/community/content/api/ModerationController.java`
  - `content-service/src/main/java/com/nowcoder/community/content/api/InternalContentController.java`
- **social-service**：
  - `social-service/src/main/java/com/nowcoder/community/social/like/LikeController.java`
  - `social-service/src/main/java/com/nowcoder/community/social/follow/FollowController.java`
  - `social-service/src/main/java/com/nowcoder/community/social/block/BlockController.java`
  - `social-service/src/main/java/com/nowcoder/community/social/block/InternalBlockController.java`
- **message-service**：
  - `message-service/src/main/java/com/nowcoder/community/message/api/MessageController.java`
  - `message-service/src/main/java/com/nowcoder/community/message/api/NoticeController.java`
- **search-service**：
  - `search-service/src/main/java/com/nowcoder/community/search/api/SearchController.java`
  - `search-service/src/main/java/com/nowcoder/community/search/api/InternalSearchController.java`
- **analytics-service**：
  - `analytics-service/src/main/java/com/nowcoder/community/analytics/api/AnalyticsController.java`
  - `analytics-service/src/main/java/com/nowcoder/community/analytics/api/InternalAnalyticsController.java`

---

## 5. 错误处理与安全约定

- **错误码：**
  - 通用错误码：`common/src/main/java/com/nowcoder/community/common/api/CommonErrorCode.java`
  - 鉴权域错误码：`common/src/main/java/com/nowcoder/community/common/api/AuthErrorCode.java`
  - 用户域错误码：`common/src/main/java/com/nowcoder/community/common/api/UserErrorCode.java`
  - 内容域错误码：`common/src/main/java/com/nowcoder/community/common/api/ContentErrorCode.java`
  - 社交域错误码：`common/src/main/java/com/nowcoder/community/common/api/SocialErrorCode.java`
  - 消息域错误码：`common/src/main/java/com/nowcoder/community/common/api/MessageErrorCode.java`
  - 搜索域错误码：`common/src/main/java/com/nowcoder/community/common/api/SearchErrorCode.java`
  - 统计域错误码：`common/src/main/java/com/nowcoder/community/common/api/AnalyticsErrorCode.java`
  - 网关域错误码：`common/src/main/java/com/nowcoder/community/common/api/GatewayErrorCode.java`
- **安全：**
  - 对外接口统一走网关鉴权与 CORS。
  - 开发阶段 internal 接口默认放行；生产建议通过网络隔离/仅内网可达等方式收敛暴露面，并避免对外暴露 `/internal/**`。
  - 未认证（Authentication 缺失）统一返回 401（`UNAUTHORIZED`），避免把鉴权问题伪装成参数错误（400）。
