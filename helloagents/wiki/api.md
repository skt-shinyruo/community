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
- `/api/likes/**`、`/api/follows/**` → `social-service`
- `/api/messages/**`、`/api/notices/**` → `message-service`
- `/api/search/**` → `search-service`
- `/api/analytics/**` → `analytics-service`

---

## 3. 内部接口（/internal/**）

说明：内部接口仅用于服务间调用/运维操作，必须携带 `X-Internal-Token`。
Token 通过环境变量或 Nacos 注入（见 `deploy/.env.example` 与 `deploy/nacos-config/*.yaml`）。

- **content-service**
  - `GET /internal/content/posts`：供 search-service 扫描帖子数据以完成 reindex（严格 schema 隔离下不允许跨库直读）。
- **search-service**
  - `POST /internal/search/reindex`：管理员触发的重建索引入口（同逻辑也暴露为 `/api/search/internal/reindex`）。
- **analytics-service**
  - `POST /internal/analytics/uv/record`
  - `POST /internal/analytics/dau/record`

---

## 4. Controller 入口索引（快速定位代码）

- **auth-service**：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`
- **user-service**：`user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`
- **content-service**：
  - `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
  - `content-service/src/main/java/com/nowcoder/community/content/api/InternalContentController.java`
- **social-service**：
  - `social-service/src/main/java/com/nowcoder/community/social/like/LikeController.java`
  - `social-service/src/main/java/com/nowcoder/community/social/follow/FollowController.java`
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
