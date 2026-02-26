# 项目技术约定（SSOT）

> 本文件定义项目的技术栈与协作约定。项目级文档入口见 `.helloagents/overview.md`。

---

## 1. 技术栈

### 1.1 当前现状（基于代码扫描）
- **后端：** Spring Boot 3.2.6 + 多模块 Maven（父工程 `pom.xml`）
- **语言：** Java 17（父工程 `pom.xml` 统一配置）
- **当前模块：**
  - `common`：统一 `Result<T>` / 错误码 / 全局异常 / traceId（目标态规范基线）
  - `user-api`：user-service 的 Dubbo RPC 接口/DTO（供 auth/message/content 等调用）
  - `social-api`：social-service 的 Dubbo RPC 接口/DTO（供 user/content/message 等调用）
  - `content-api`：content-service 的 Dubbo RPC 接口/DTO（供 search/social 等调用）
  - `message-api`：message-service 的 Dubbo RPC 接口/DTO（ops 平面调用）
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

### 2.4 Dubbo 同步依赖政策（SSOT）

> 本节用于控制“分布式单体”风险：同步依赖必须少、可解释、可观测、可回滚；写路径默认不引入跨服务同步依赖。

扫描口径（事实基线）：
- 统计方式：扫描仓库 `src/main/java` 中的 `@DubboReference`
- 基线（2026-02-25）：`@DubboReference` 共 **15 处**，聚合为以下 “服务 → 服务” 边（allowlist）

允许存在的同步依赖边（allowlist）：
- `auth-service -> user-service`：认证/会话内部调用
- `user-service -> social-service`：用户主页展示类读路径聚合计数/状态（必须可配置 fail-open）
- `content-service -> user-service`：发言权限校验（禁言/封禁状态查询；写路径守卫，默认 fail-closed）
- `content-service -> social-service`：互动写路径反骚扰校验（拉黑关系点查；默认 fail-closed）+ 展示类读路径点赞查询（可配置 fail-open）
- `message-service -> user-service`：私信权限校验（禁言/封禁状态查询；写路径守卫，默认 fail-closed）+ 用户只读查询/解析（会话列表展示、toName 解析）
- `message-service -> social-service`：私信写路径反骚扰校验（拉黑关系点查；默认 fail-closed）
- `social-service -> content-service`：写路径可信解析（POST/COMMENT entity resolve；默认 fail-closed）
- `search-service -> content-service`：reindex 扫描（高成本，只允许 ops 触发）
- `gateway -> analytics-service`：analytics 采集（可丢弃链路，失败不影响主链路）
- `ops-service -> search-service/content-service/social-service/user-service`：运维平面隔离（仅 `/api/ops/**` 入口）

约束（必须遵守）：
1. `@DubboReference` 必须显式设置 `retries = 0` 且显式 `timeout`（避免默认重试放大故障、避免无界等待）。
2. 跨服务 RPC 必须有“降级策略”：写路径的鉴权/反骚扰/可信校验默认 **fail-closed**；展示类读路径可 **fail-open**（必须可观测且不伪装为真实值）。
3. 避免 N+1：需要批量补水（多用户/多实体）时优先批量 RPC 或短 TTL 缓存（容量受控），禁止在热路径做无界 fan-out。
4. Dubbo provider（`@DubboService`）只能存在于 `*-service` 模块；禁止出现在 `common` / `infra-*` / `contracts-*` / `*-api`。

证据文件（便于追溯）：
- `auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java`
- `user/user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`
- `content/content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`
- `content/content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`
- `content/content-service/src/main/java/com/nowcoder/community/content/like/RpcLikeQueryService.java`
- `message-service/src/main/java/com/nowcoder/community/message/service/UserModerationClient.java`
- `message-service/src/main/java/com/nowcoder/community/message/service/SocialBlockClient.java`
- `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`
- `search/search-service/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java`
- `gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java`
- `ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`
- `social/social-service/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java`

### 2.5 跨服务 RPC 降级策略（SSOT）

策略定义：
- **fail-closed**：依赖不可用时快速失败（通常 503），保证“不越权/不写错”，但可用性依赖下游健康。
- **fail-open**：依赖不可用时继续但受控降级（默认值/空集合/提示），适用于展示类读路径或可丢弃链路。

当前关键 RPC 与策略（以代码为准）：
- `social-service -> content-service`：`ContentEntityRpcService#resolveEntity`（写路径可信解析）→ **fail-closed**
- `content-service -> user-service`：`UserModerationRpcService#getStatus`（发言权限守卫）→ **fail-closed**
- `message-service -> user-service`：`UserModerationRpcService#getStatus`（私信权限守卫）→ **fail-closed**
- `content-service -> social-service`：`SocialBlockRpcService#isEitherBlocked`（评论/回复写路径反骚扰）→ **fail-closed**
- `message-service -> social-service`：`SocialBlockRpcService#isEitherBlocked`（私信写路径反骚扰）→ **fail-closed**
- `content-service -> social-service`：`SocialReadRpcService#entityLikeCount/hasLiked`（帖子详情/热帖刷新等展示类读路径）→ **fail-open（默认）**
- `message-service -> user-service`：`UserReadRpcService#batchSummary/resolveByUsernameOrNull/getByIdOrNull`（会话列表展示与 toName 解析）→ **可配置**（默认 fail-closed；如需更强韧性可启用 fail-open）
- `user-service -> social-service`：`SocialReadRpcService#userProfileStats/...`（主页聚合）→ **fail-open**（必须可观测并区分降级占位）
- `gateway -> analytics-service`：`InternalAnalyticsRpcService`（采集链路）→ **fail-open**（可丢弃）

约定：
- 所有跨服务 RPC 必须：显式 timeout、retries=0，并在 KB 中记录关键路径的降级策略（本节）。
- 避免 N+1：需要多用户/多实体补水时，优先批量 RPC 或短 TTL 缓存（容量受控）。

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

### 5.1 测试策略（仅保留 Unit Tests）
- **默认回归（CI / `mvn test`）：** 仅运行纯单元测试（JUnit5 + Mockito/AssertJ 等），禁止启动 Spring 容器、禁止监听本地端口、禁止真实网络 IO。
- **外部依赖处理：** DB/Redis/Kafka/ES/Nacos/HTTP 等一律使用 mock/stub/in-memory（不使用 Docker/Testcontainers、也不依赖本地 docker compose）。
- **禁止项（项目约定）：** `@SpringBootTest`、`@WebMvcTest/@WebFluxTest/@DataJpaTest/@JdbcTest`、`@AutoConfigureMockMvc/@AutoConfigureWebTestClient`、Testcontainers、以及 Reactor Netty `HttpServer.bindNow()` 等嵌入式 server 形态。
- **建议写法：** Web 层优先直接 `new Controller(...)` 并用 `MockHttpServletRequest/Response` 或 `MockServerWebExchange` 驱动；配置类只验证“构造/装配结果与关键参数”而不是发起真实请求。

### 5.2 约束执行（仅文档）
- 按需求变更：当前仓库**不通过 gate tests（扫描式门禁）**强制上述约定，避免“门禁漂移/误伤”阻断日常开发。
- 约束执行建议通过：code review + CI（编译/单测/静态检查）+ 文档 SSOT（本文件）组合完成。
- 若确需端到端验证，统一以 **手工联调/运行手册（docker compose + curl）** 方式进行，不纳入 `mvn test` 默认回归。

### 5.3 交付与回滚
- 每个服务独立构建与部署；灰度/回滚由 Gateway 路由策略支持。
