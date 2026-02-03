# API 手册

## 1. 总览

- **对外入口：** 统一由 `gateway` 暴露 `/api/**`（本地默认 `http://localhost:12882`）。
- **鉴权：** 以 JWT Access Token 为主（`Authorization: Bearer <token>`）；刷新/登出等策略由 `auth-service` 管理。
- **统一返回：** 后端统一返回 `Result<T>`（`code/message/data`）。
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
- `/api/ops/**` → 运维入口（默认仅管理员可访问，且通常需要额外 `X-Ops-Token`；具体以网关路由与下游保护器为准）

---

## 3. 内部接口（/internal/**）

说明：内部接口仅用于服务间调用/运维操作，必须携带 `X-Internal-Token`。
Token 通过环境变量或 Nacos 注入（见 `deploy/.env.example` 与 `deploy/nacos-config/*.yaml`）。

- **content-service**
  - `GET /internal/content/posts`：供 search-service 扫描帖子数据以完成 reindex（严格 schema 隔离下不允许跨库直读）。
  - `GET /internal/content/entities/resolve?entityType=&entityId=`：解析 POST/COMMENT 的 owner/postId（供 social 写路径构造可信事件 payload，禁止信任客户端注入）。
  - `POST /internal/content/likes/backfill?entityType=&maxItems=&batchSize=`：回填 Redis 点赞投影（运维入口，默认关闭；受 `X-Internal-Token` + ops-guard + endpoint-enabled 共同保护）。
- **social-service**
  - `GET /internal/social/blocks/relation?userIdA=&userIdB=`：查询 A/B 的拉黑关系（互斥私信等写路径校验）。
  - `GET /internal/social/likes/scan?entityType=&afterEntityId=&afterUserId=&limit=`：按游标扫描点赞关系（供 content-service 回填 Redis 点赞投影使用）。
  - `GET /internal/social/read/users/{userId}/profile-stats?viewerId=`：用户主页聚合只读接口（一次返回获赞/关注/粉丝/是否关注；供 user-service 聚合展示，降低 fan-out）。
- **user-service**
  - `GET /internal/users/{userId}/moderation-status`：查询用户禁言/封禁状态（content-service 写路径前置校验）。
  - `POST /internal/users/{userId}/moderation`：应用禁言/封禁（治理动作落地，供 content-service 治理动作转发调用）。
  - outbox 运维（受 ops-guard 强保护，默认 break-glass 关闭）：
    - `GET /internal/users/outbox/health`
    - `POST /internal/users/outbox/replay?limit=200`
- **search-service**
  - `POST /internal/search/reindex`：重建索引内部入口（受 `X-Internal-Token` + ops-guard 强保护）。
    - 对外运维入口（推荐）：`POST /api/ops/search/reindex`
    - 历史兼容入口（弃用中，默认禁用）：`POST /api/search/internal/reindex`（gateway 返回 410 并提示迁移）
- **analytics-service**
  - `POST /internal/analytics/uv/record`
  - `POST /internal/analytics/dau/record`

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
- **安全：**
  - 对外接口统一走网关鉴权与 CORS。
  - 内部接口使用 `X-Internal-Token` 做最小保护（生产可进一步升级为 mTLS/内网隔离）。
  - 运维高风险入口额外使用 ops-guard（`X-Ops-Token` + allowlist + 限流，默认 break-glass 关闭），典型场景：`/internal/*/outbox/replay`、`/internal/*/likes/backfill`、`/internal/search/reindex`。
  - 未认证（Authentication 缺失）统一返回 401（`UNAUTHORIZED`），避免把鉴权问题伪装成参数错误（400）。
