# 安全与鉴权模型

> 本文档描述 **community-gateway + community-app + IM** 形态下的安全边界与关键机制。  
> 默认浏览器入口由 `community-gateway` 暴露，对主站业务的授权矩阵 SSOT 仍在 `community-app`。  
>
> 约定：本文档中的路径/命令默认从**仓库根目录**执行。

---

## 1. 总体安全边界（推荐理解方式）

- **浏览器默认直连 community-gateway**：对外统一入口 `/api/**`（业务）、`/files/**`（静态文件）与 `/ws/im`。
- **community-gateway 负责入口级边界护栏**：
  - HTTP/WS 路由与基础边缘策略
  - 对浏览器流量统一执行 CORS 处理，并维护浏览器入口的 origin allowlist
- **共享安全基础设施负责 JWT 规则与统一错误语义**：
  - `community-common-security` 是 `security.jwt.*` 的 SSOT，`community-app`、`community-gateway`、`im-core`、`im-realtime` 都复用同一套验签/subject 解析规则
  - Servlet 服务通过 `community-common-web`，WebFlux 服务通过 `community-common-webflux` 统一回写 `X-Trace-Id` / `traceparent`，并输出一致的 `Result` 错误包体
- **community-app 负责主站业务安全策略**：
  - OriginGuard（仅覆盖 cookie 会话敏感入口：`/api/auth/login|refresh|logout`）
  - traceId 注入与统一异常映射
  - 写请求审计日志（`backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/AuditLogFilter.java`）
- **授权矩阵 SSOT 在 community-app**：路径级鉴权统一在 `backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java` 中收敛（JWT resource server）。
- **auth 模块负责签发与会话闭环**：登录/刷新/登出、验证码、注册/激活、找回密码、登录风控等；对外路径仍为 `/api/auth/**`，但运行在同一进程。
- **user 模块托管 refresh token 状态**：`auth.refresh.store=db` 时，refresh token 状态落在 MySQL 表 `auth_refresh_token`（单一 schema：`community`）。

---

## 2. JWT + Refresh Cookie（会话模型）

### 2.1 Access Token（JWT）
- 由 auth 模块签发（HS256），在响应体返回 `accessToken`（`POST /api/auth/login`）
- 前端保存到内存/状态（Pinia store），并在每次请求加上 `Authorization: Bearer <token>`
- 资源服务器验签：`community-app`、`community-gateway`、`im-core`、`im-realtime` 都通过共享 `community-common-security` 读取 `security.jwt.*` 并完成 JWT 验签；JWT 签发仍由 `community-app` 的 auth 模块负责，主站授权矩阵仍由 `community-app` 收敛
- 认证/鉴权失败语义：Servlet 与 WebFlux 服务都会统一输出 `Result` 错误包体，并回写 `X-Trace-Id` / `traceparent`

### 2.2 Refresh Token（HttpOnly Cookie）
- refresh token 通过 **HttpOnly Cookie** 下发（浏览器 JS 无法读取）
- 前端开启 `withCredentials: true` 让浏览器自动携带 cookie
- 触发 refresh 的典型机制：
  - 当业务请求返回 `401`，前端调用 `/api/auth/refresh` 获取新 access token，然后重试原请求

### 2.3 Refresh Token 存储与旋转
- auth 模块支持 `memory` / `redis` / `db` 三种 store（以 `auth.refresh.store` 为准；默认 `db`）
  - `db`：refresh token 状态落在 MySQL 表 `auth_refresh_token`（仅存 token_hash），由 user 模块的 session 组件托管（A-1 下为同进程内部调用）
- refresh token 支持“旋转刷新（rotation）”语义：refresh 时可颁发新 refresh token，并使旧 token 失效（按 familyId 族撤销）

---

## 3. CORS / Origin（前端直连的前提）

本地 gateway-first 模式下：
- 浏览器入口相关值统一来自部署配置：`FRONTEND_PUBLIC_ORIGIN`、`GATEWAY_PUBLIC_BASE_URL`、`IM_WS_PUBLIC_URL`、`BROWSER_ALLOWED_ORIGINS`
- `community-gateway` 是浏览器流量的 **CORS 唯一出口**：
  - `gateway.cors.allowed-origins` 通过 `GATEWAY_CORS_ALLOWED_ORIGINS` 注入，统一覆盖 `/api/**` 与 `/files/**`
  - gateway 会去重下游重复返回的 `Access-Control-Allow-*` 头，避免浏览器因重复 CORS 头判定失败
- `community-app` 不再对 `/api/**` 与 `/files/**` 输出浏览器 CORS 头：
  - 浏览器默认应经 `community-gateway` 访问主站 API / files
  - 若直接跨 origin 访问 `community-app`，默认不会获得跨域放行
- **OriginGuard** 仍由 `community-app` 内的 `AuthOriginGuardFilter` 覆盖敏感入口（`POST /api/auth/login|refresh|logout`）
  - allowlist 通过 `AUTH_ORIGIN_GUARD_ALLOWED_ORIGINS` 注入；配置键名仍沿用 `gateway.origin-guard.*`（历史兼容），但执行位置在单体内

注意：
- 若用 `127.0.0.1` 访问前端，会导致 origin 变化，需要同步调整 `BROWSER_ALLOWED_ORIGINS`。
- 若前端端口变化，需要同步更新 `FRONTEND_PUBLIC_ORIGIN`、`IM_WS_PUBLIC_URL` 与 `BROWSER_ALLOWED_ORIGINS`。

---

## 4. 限流与风控（模块化单体）

### 4.1 登录风控（auth.login-rate-limit）
目前默认启用登录风控（避免暴力破解/撞库）：
- 维度：用户名/用户 IP（以及组合维度，按配置）
- 行为：达到阈值后拒绝登录并可要求验证码
- 配置：`auth.login-rate-limit.*`

### 4.2 全局 API 限流（当前取舍）
在 A-1 默认路径下：
- `community-gateway` 已接入应用内路径级限流过滤器，配置键为 `gateway.http.rate-limit.*`
- 当前默认配置是 `enabled=true`、`fail-open-on-error=false`、`policies={}`，因此**限流能力已接线，但默认没有任何生效中的路径规则**
- 生产如果需要真正的全局或入口级限流，仍建议优先由反代/Ingress/WAF 承担；gateway 内置能力更适合补充少量显式路径策略

### 4.1 可信代理与真实客户端 IP（X-Forwarded-For）

默认安全态（fail-closed）：
- **默认不信任** `X-Forwarded-For` / `X-Real-IP`（防止客户端伪造转发头绕过风控/限流/统计）。

当生产部署在 Nginx/Ingress/Load Balancer 后时，建议显式启用可信代理模式：
- 配置键（沿用历史命名，当前由 community-app 读取）：
  - `gateway.trusted-proxy.enabled=true`
  - `gateway.trusted-proxy.cidrs=[10.0.0.0/8,192.168.0.0/16,...]`
- 行为约定：
  - 仅当 `remoteAddr ∈ cidrs allowlist` 才解析 `X-Forwarded-For` 的第一个 IP 作为客户端 IP；
  - 否则严格使用 `remoteAddr`（避免 XFF 伪造）。

强约束（prod 下）：
- 当 `SPRING_PROFILES_ACTIVE=prod` 且 `gateway.trusted-proxy.enabled=true`：
  - `gateway.trusted-proxy.cidrs` 为空会 **阻断启动**（避免 silent misconfig）；
  - 禁止使用 `0.0.0.0/0` 或 `::/0`（全量信任会带来 XFF 伪造风险）。

本地/示例配置见：
- `deploy/.env.single.example`
- `deploy/.env.cluster.example`

---

## 5. 审计日志（写路径）

`community-app` 对写请求会记录审计日志（`backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/AuditLogFilter.java`）：
- 范围：`/api/**` 且 method 非 `GET/OPTIONS`
- 例外：跳过 `/api/auth/login`（避免记录敏感登录参数，也尽量不污染审计流量）

目的：
- 让“谁在什么时候调用了哪个写接口、返回状态、耗时、traceId”可追踪

---

## 6. 内部接口（/internal/**）

开发阶段（当前实现）：
- 本项目已移除 HTTP `/internal/**` 运维入口（避免 internal HTTP 与 ops 面并存导致长期“半迁移”治理债务）。
- 对外运维入口统一走 `/api/ops/**`（例如 `POST /api/ops/search/reindex` 仅管理员可访问）。

IM 说明：
- IM 保留独立服务形态（`im-core`/`im-realtime`），其中 `im-core` 仍暴露少量 `/internal/**` 供 `im-realtime` bootstrap 使用（见 `backend/community-im/im-core/.../InternalRealtimeBootstrapController`）。

生产建议（后续迭代方向）：
- 仍需确保下游服务端口不对外暴露（避免旁路绕过网关与运维入口治理）。
- 若未来需要新增“仅内网可达”的 internal 面，建议通过“网络隔离 / mTLS / Service Mesh”来收敛暴露面，而不是依赖外部可注入的 header。

---

## 6.2 IM 私信入口与治理

- 发送链路不再走 legacy message HTTP 写入口；外部发送入口是 `community-gateway` 暴露的 `/ws/im`。
- 客户端在 WebSocket 里发送 `sendPrivateText` 后，请求会先进入 `im-realtime`。
- `im-realtime` 会携带用户 JWT 调用 `community-app` 的 `POST /api/im-governance/private-messages/validate`，先完成拉黑、处罚、目标用户存在性等治理判定。
- 只有治理校验通过后，`im-realtime` 才会把 Kafka private-message command 视为可接受并写入 backplane；因此 Kafka accepted 的前提是治理已经放行。
- 私信历史查询、会话列表、已读与未读状态由 IM HTTP 接口 `/api/im/**` 提供，不由 `community-app` 暴露对应消息读写 API。

---

## 6.3 写接口幂等（Idempotency-Key）

为避免浏览器重复点击/网络重试导致的重复副作用，本项目对部分 **HTTP 写接口** 启用幂等保护：
- header：`Idempotency-Key: <unique-key>`
- 幂等维度：`userId + operation + Idempotency-Key`
- 钱包充值/提现/转账和市场下单兼容旧 body `requestId`，但仅在 header 缺失时使用；header/body 不一致返回 `400`
- 行为：
  - 首次请求：执行业务副作用并缓存响应
  - 重复请求：直接复用缓存响应（避免重复写入/重复通知等副作用）
  - 并发同 key：返回 `409`（提示“处理中，可重试”）

完整说明见 `docs/HTTP_IDEMPOTENCY.md`，其中包括当前覆盖接口、客户端约定、返回语义、TTL、DB/Redis 存储、指标、接入新写接口的步骤和 exactly-once 边界。

---

## 7. 本地安全建议（即便是开发环境）
- 修改 `.env` 中的 `JWT_HMAC_SECRET`（>= 32 字节），不要长期用默认值
- 共享 `community-common-security` 会在启动时拒绝空值或已知占位密钥；本地联调也必须显式提供一个非占位、长度 >= 32 字节的共享密钥，并保持 `security.jwt.issuer` 一致
- 不要把 `.env`（含真实密钥）提交到版本库
- 默认不暴露内部依赖端口到宿主机；需要浏览器访问 Kibana / Elasticsearch localhost 入口时，再执行 `./deploy/deployment.sh up --observability`

---

## 8. 头像上传（user + 对象存储直传）

### 8.1 风险点
- 头像更新链路属于“写接口”，若缺乏约束容易被滥用（大文件 DoS、上传任意类型、覆盖他人对象、绕过上传直接改头像 URL 等）

### 8.2 约定（fail-closed）
- 签发上传 token 时，服务侧绑定 `fileName -> userId`（Redis TTL），并在更新头像时 **一次性消费**（防重放/防越权）
- `PUT /api/users/{userId}/avatar` 必须校验：
  - `fileName` 必须为 `avatar/{userId}/...` 前缀
  - Redis 中存在对应 ticket 且归属当前用户
  - ticket 只能使用一次（GETDEL/消费后删除）
- 对象存储上传 token 限制：
  - 最大体积：2 MiB
  - MIME 白名单：`image/jpeg,image/png,image/webp,image/gif`
  - `insertOnly=1`（避免覆盖已有对象）

### 8.3 前端约定
- 上传失败必须视为失败（不允许“上传失败仍更新头像”的 demo 兜底逻辑）
- 建议在前端也做文件类型/大小预校验（用户体验更好）
