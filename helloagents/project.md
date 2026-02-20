# 项目技术约定（SSOT）

> 本文件定义项目的技术栈与协作约定。项目级文档入口见 `helloagents/wiki/overview.md`。

---

## 1. 技术栈

### 1.1 当前现状（基于代码扫描）
- **后端：** Spring Boot 3.2.6 + 多模块 Maven（父工程 `pom.xml`）
- **语言：** Java 17（父工程 `pom.xml` 强制门禁）
- **当前模块：**
  - `common`：统一 `Result<T>` / 错误码 / 全局异常 / traceId（目标态规范基线）
  - `user-api`：user-service 的 Dubbo RPC 接口/DTO（供 auth/message/content 等调用）
  - `social-api`：social-service 的 Dubbo RPC 接口/DTO（供 user/content/message 等调用）
  - `content-api`：content-service 的 Dubbo RPC 接口/DTO（供 search/social 等调用）
  - `analytics-api`：analytics-service 的 Dubbo RPC 接口/DTO（供 gateway 采集调用）
  - `gateway`：Spring Cloud Gateway（统一入口：CORS/鉴权/trace/错误收敛）
  - `auth-service`：JWT 登录/刷新/登出（refresh token 旋转；默认 Redis 存储，内存实现需显式启用）
  - `user-service`：用户资料与头像（Qiniu）
  - `content-service`：帖子/评论/热帖/敏感词过滤（MySQL + Redis + Kafka）
  - `social-service`：点赞/关注/拉黑（MySQL 为 SSOT + Kafka 事件；Redis 仅作为可选加速层演进）
  - `message-service`：通知/私信（MySQL + Kafka）
  - `search-service`：搜索（默认 ES；支持通过 internal reindex 冷启动/纠偏，测试环境可切换 memory）
  - `analytics-service`：UV/DAU（默认 Redis，gateway 可采集写入）
  - `frontend`：Vue3 SPA（Vite + Router + Pinia + Axios）
- **持久化：** MyBatis + MySQL（迭代 0 仍复用 legacy 的 user 表）
- **缓存：** Redis（auth refresh token、social 点赞/关注、analytics UV/DAU 等）
- **安全：** Spring Security 6（gateway/auth/legacy）
- **服务治理：** Spring Cloud 2023.0.x + Spring Cloud Alibaba 2023.x + Nacos（HTTP 路由的注册发现/配置中心）+ Dubbo（服务间同步调用）+ Zookeeper（Dubbo registry）

> 注：legacy 的 Elasticsearch 实现已在迁移期降级移除（后续迭代 1 将以独立 `search-service` 重写）。

### 1.2 目标态（迁移方向）
- **后端：** Spring Boot 3.x + Spring Cloud（微服务）+ Nacos（注册发现/配置中心）+ Dubbo（服务间同步调用）
- **语言：** Java 17
- **前端：** Vue 3（前后端分离，SPA）
- **鉴权：** JWT Access Token + Refresh Token（推荐旋转刷新）
- **API：** RESTful JSON，统一返回结构与错误码

---

## 2. 工程与模块约定

### 2.1 微服务命名与边界
- 服务按领域拆分：`gateway`、`auth-service`、`user-service`、`content-service`、`social-service`、`message-service`、`search-service`、`analytics-service`
- **原则：** 一个服务拥有自己的数据归属与演进节奏；跨服务通过 API 或事件交互，禁止跨库 JOIN。

	接口边界（SSOT）：
	- External（对外业务）：`/api/**`
	- Ops（对外运维）：`/api/ops/**`（高风险操作；仅管理员可触发，建议通过 Ops Console 等受控入口执行）
	- Internal-RPC（服务间同步调用）：Dubbo RPC（接口/DTO 统一沉淀在 `*-api` 模块；registry=Zookeeper）
	- Internal-HTTP：❌ 当前版本不再提供 `/internal/**`（避免 internal HTTP 与 RPC 并存导致长期“半迁移”治理债务）
	- legacy 对外 internal 命名（示例：`/api/search/internal/reindex`）：❌ 不再保留功能语义，固定返回 410；新入口为 `/api/ops/search/reindex`

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
- 端口暴露策略（默认 fail-closed）：
  - 默认不暴露业务服务端口（避免旁路绕过网关）
  - 需要对外暴露 gateway 时叠加 `deploy/docker-compose.ports.yml`
  - 需要前端容器直连时叠加 `deploy/docker-compose.frontend-direct.yml`

敏感配置清单（必须通过环境变量或 Nacos 注入）：
- JWT HMAC：`JWT_HMAC_SECRET`（建议 >= 32 字节；auth-service 签发、gateway/资源服务验签需一致）
  - 可选覆盖：`AUTH_JWT_HMAC_SECRET`、`GATEWAY_JWT_HMAC_SECRET`
- 对象存储：`QINIU_ACCESS_KEY` / `QINIU_SECRET_KEY` 等
- DB 账号（按服务最小权限，禁止复用 `MYSQL_USER/MYSQL_PASSWORD` 作为其它服务业务账号）：
  - user-service：`USER_DB_URL` / `USER_DB_USERNAME` / `USER_DB_PASSWORD`
  - content-service：`CONTENT_DB_URL` / `CONTENT_DB_USERNAME` / `CONTENT_DB_PASSWORD`
  - social-service：`SOCIAL_DB_URL` / `SOCIAL_DB_USERNAME` / `SOCIAL_DB_PASSWORD`
  - message-service：`MESSAGE_DB_URL` / `MESSAGE_DB_USERNAME` / `MESSAGE_DB_PASSWORD`
  - search-service：`SEARCH_DB_URL` / `SEARCH_DB_USERNAME` / `SEARCH_DB_PASSWORD`

### 2.3 头像上传约定（SSOT）
- 上传方式：对象存储直传（前端拿到 upload token 后直接上传，不走 gateway）
- key 前缀：`avatar/{userId}/...`（服务侧强校验，避免任意 key 注入）
- 限额（服务侧兜底 + 存储侧拒绝）：
  - 最大体积：2 MiB
  - MIME 白名单：`image/jpeg,image/png,image/webp,image/gif`
- 防重放/防越权：upload-token 签发时绑定 `fileName -> userId`（Redis TTL=600s），更新头像时一次性消费（ticket 被消费后再次使用将被拒绝）

---

## 3. API 与错误处理约定

### 3.1 统一返回结构（建议）
- 统一返回：`code` / `message` / `data` / `traceId` / `timestamp`
- 错误码按模块分段（例如：`AUTH_****`、`USER_****`、`CONTENT_****`）
- 错误协议：HTTP status 表达“错误类别”，`Result.code` 表达“业务细分”（详见 `docs/SYSTEM_DESIGN.md`）

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

### 5.2 测试 Quick win 约定（默认）
- **优先级：** 能用单元/切片解决的回归，默认不新增 `@SpringBootTest`；`@SpringBootTest` 主要保留给“wiring/配置/事务边界/真实序列化组合/真实 DB 行为”等必须场景。
- **Kafka Consumer 类测试：** 优先直接构造被测类并调用 `handleRecord(...)`，避免启动 Spring 容器；若 payload DTO 含 `Instant` 等时间类型，建议 `new ObjectMapper().findAndRegisterModules()` 以保证可反序列化。
- **Outbox 类逻辑：** 参数裁剪（limit/retention）、fallback（如 SKIP LOCKED 探测降级）等逻辑优先用 Mockito 单测覆盖；跨服务重复的 Outbox 集成测试应控制数量，避免“同类用例在每个服务都全量复制”拖慢默认回归。
- **并发/不确定性用例：** 如必须保留，建议后续通过 Tag/profile 隔离（例如 nightly），避免影响默认 `mvn test` 的稳定性与耗时。

### 5.3 交付与回滚
- 每个服务独立构建与部署；灰度/回滚由 Gateway 路由策略支持。
