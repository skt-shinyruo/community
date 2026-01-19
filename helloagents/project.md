# 项目技术约定（SSOT）

> 本文件定义项目的技术栈与协作约定。项目级文档入口见 `helloagents/wiki/overview.md`。

---

## 1. 技术栈

### 1.1 当前现状（基于代码扫描）
- **后端：** Spring Boot 3.2.6 + 多模块 Maven（父工程 `pom.xml`）
- **语言：** Java 17（父工程 `pom.xml` 强制门禁）
- **当前模块：**
  - `common`：统一 `Result<T>` / 错误码 / 全局异常 / traceId（目标态规范基线）
  - `gateway`：Spring Cloud Gateway（统一入口：CORS/鉴权/trace/错误收敛）
  - `auth-service`：JWT 登录/刷新/登出（refresh token 旋转，Redis/内存存储可切换）
  - `user-service`：用户资料与头像（Qiniu）
  - `content-service`：帖子/评论/热帖/敏感词过滤（MySQL + Redis + Kafka）
  - `social-service`：点赞/关注（Redis + Kafka）
  - `message-service`：通知/私信（MySQL + Kafka）
  - `search-service`：搜索（默认内存实现，支持从 DB reindex；后续可切换 ES）
  - `analytics-service`：UV/DAU（默认 Redis，gateway 可采集写入）
  - `frontend`：Vue3 SPA（Vite + Router + Pinia + Axios）
- **持久化：** MyBatis + MySQL（迭代 0 仍复用 legacy 的 user 表）
- **缓存：** Redis（auth refresh token、social 点赞/关注、analytics UV/DAU 等）
- **安全：** Spring Security 6（gateway/auth/legacy）
- **服务治理：** Spring Cloud 2023.0.x + Spring Cloud Alibaba 2023.x + Nacos（注册发现/配置中心）

> 注：legacy 的 Elasticsearch 实现已在迁移期降级移除（后续迭代 1 将以独立 `search-service` 重写）。

### 1.2 目标态（迁移方向）
- **后端：** Spring Boot 3.x + Spring Cloud（微服务）+ Spring Cloud Alibaba Nacos（注册发现/配置中心）
- **语言：** Java 17
- **前端：** Vue 3（前后端分离，SPA）
- **鉴权：** JWT Access Token + Refresh Token（推荐旋转刷新）
- **API：** RESTful JSON，统一返回结构与错误码

---

## 2. 工程与模块约定

### 2.1 微服务命名与边界
- 服务按领域拆分：`gateway`、`auth-service`、`user-service`、`content-service`、`social-service`、`message-service`、`search-service`、`analytics-service`
- **原则：** 一个服务拥有自己的数据归属与演进节奏；跨服务通过 API 或事件交互，禁止跨库 JOIN。

### 2.2 配置管理
- 所有环境配置以 Nacos 为准，禁止把密钥/Token/账号密码写入代码库。
- 配置按环境隔离（dev/test/prod），并保持可本地启动的最小配置集（可用 mock/本地 docker compose 支撑）。
- 建议隔离策略（迭代 0 起写入约定）：
  - namespace：按环境隔离（dev/test/prod）
  - group：按系统/团队隔离（默认 `DEFAULT_GROUP`，后续可按需要细分）
  - profile：按服务运行环境（例如 `spring.profiles.active=dev`）

本地开发约定（推荐）：
- `deploy/.env.example`：示例环境变量模板（可复制为 `deploy/.env`）。
- `deploy/.env`：本地私有配置（已加入 `.gitignore`，禁止提交）。
- `deploy/docker-compose.yml`：本地基础设施（MySQL/Redis/Kafka/ES/Nacos）。启动命令建议使用：
  - `docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d`

敏感配置清单（必须通过环境变量或 Nacos 注入）：
- JWT HMAC：`JWT_HMAC_SECRET` / `AUTH_JWT_HMAC_SECRET` / `GATEWAY_JWT_HMAC_SECRET`
- 内部调用 token：`ANALYTICS_INTERNAL_TOKEN`、`SEARCH_INTERNAL_TOKEN`
- 对象存储：`QINIU_ACCESS_KEY` / `QINIU_SECRET_KEY` 等

---

## 3. API 与错误处理约定

### 3.1 统一返回结构（建议）
- 统一返回：`code` / `message` / `data` / `traceId` / `timestamp`
- 错误码按模块分段（例如：`AUTH_****`、`USER_****`、`CONTENT_****`）

### 3.2 全局异常处理
- 由 `@RestControllerAdvice` 统一收敛异常，禁止在 Controller 中返回拼接字符串 JSON。
- 对外仅暴露稳定错误码与可读 message，敏感堆栈只写日志。

---

## 4. 日志与可观测性

### 4.1 Trace 约定
- Header：`X-Trace-Id`（gateway 生成并透传；后端服务写入 MDC 并在响应回传）
- 约定：所有服务对外响应都应包含同一个 `X-Trace-Id`（用于全链路排障）

### 4.2 日志规范
- 统一采用结构化日志或固定格式日志（至少包含：时间、等级、服务名、traceId、用户标识、关键业务字段）。

---

## 5. 测试与交付

### 5.1 测试分层
- **单元测试：** 核心领域逻辑（Service、工具类），对外部依赖（DB/Redis/Kafka/HTTP）使用 mock/stub。
- **切片测试：** `@WebMvcTest/@DataJpaTest` 等，只验证当前层的配置与行为；对下游依赖使用 mock（例如 `@MockBean`）。
- **集成测试：** 覆盖 DB/Redis/Kafka/ES 关键链路，**主流做法是使用 Testcontainers**（CI/本地一致）；仅在必要时使用 docker compose 作为开发联调环境。
- **契约测试：** 服务间 API/事件契约（推荐逐步引入）。

### 5.2 交付与回滚
- 每个服务独立构建与部署；灰度/回滚由 Gateway 路由策略支持。
