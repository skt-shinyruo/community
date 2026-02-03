# 安全与鉴权模型

> 本文档描述“本地前端直连 gateway”模式下的安全边界与关键机制。以网关（统一入口）与 auth-service（签发 token）为中心，说明 JWT、refresh cookie、CORS、限流、审计与内部 token 的协作关系。

---

## 1. 总体安全边界（推荐理解方式）

- **浏览器只直连 gateway**：对外统一入口 `/api/**`，避免前端直接访问内部微服务。
- **gateway 负责鉴权与统一策略**：JWT 验签、权限矩阵、CORS、限流、审计日志等。
- **auth-service 负责签发与会话闭环**：登录/刷新/登出、验证码、注册/激活、找回密码等。

---

## 2. JWT + Refresh Cookie（会话模型）

### 2.1 Access Token（JWT）
- 由 auth-service 签发（HS256），在响应体返回 `accessToken`
- 前端保存到内存/状态（Pinia store），并在每次请求加上 `Authorization: Bearer <token>`
- gateway 作为资源服务器验签：密钥来自 `JWT_HMAC_SECRET`（需与 auth-service 保持一致）

### 2.2 Refresh Token（HttpOnly Cookie）
- refresh token 通过 **HttpOnly Cookie** 下发（浏览器 JS 无法读取）
- 前端开启 `withCredentials: true` 让浏览器自动携带 cookie
- 触发 refresh 的典型机制：
  - 当业务请求返回 `401`，前端调用 `/api/auth/refresh` 获取新 access token，然后重试原请求

### 2.3 Refresh Token 存储与旋转
- auth-service 支持 `memory` / `redis` 两种 store（以配置为准）
- refresh token 支持“旋转刷新（rotation）”语义：refresh 时可颁发新 refresh token，并使旧 token 失效（按 familyId 族撤销）

---

## 3. CORS / Origin（前端直连的前提）

本地直连模式下：
- 前端 origin 默认为 `http://localhost:12881`（也可能因本地端口调整变为 `http://localhost:12888` 等）
- Origin/CORS allowlist 以 **gateway 为 SSOT**：
  - **gateway CORS**：负责浏览器跨端口直连时的 CORS 响应头（是否允许带 cookie / 预检等）
  - **gateway OriginGuard**：对敏感接口（`/api/auth/login|refresh|logout`）执行 Origin 白名单校验（服务端硬拦截）
- **auth-service OriginGuard（旁路兜底）**：同样覆盖 `login/refresh/logout`，并复用同一套 allowlist 配置键（`gateway.origin-guard.*`），避免绕过网关直连 auth-service 时降低安全性

注意：
- 若用 `127.0.0.1` 访问前端，会导致 origin 变化，需要同步调整 allowlist（或统一用 `localhost`）。
- 若前端端口变化，需要同步更新 allowlist（gateway CORS / gateway OriginGuard / auth-service OriginGuard）。

---

## 4. 网关限流（Redis + metrics）

gateway 支持基于 Redis 的规则化限流：
- 规则按「method + path-pattern」匹配
- key 策略支持 IP / USER / USER_OR_IP

对外表现：
- 被限流时返回 `429 Too Many Requests`
- 同时记录指标 `gateway_rate_limit_total`（带 `rule`、`outcome` 标签）

### 4.1 可信代理与真实客户端 IP（X-Forwarded-For）

默认安全态（fail-closed）：
- **默认不信任** `X-Forwarded-For` / `X-Real-IP`（防止客户端伪造转发头绕过风控/限流/统计）。

当生产部署在 Nginx/Ingress/Load Balancer 后时，建议显式启用可信代理模式：
- 配置键（网关与服务侧共享同一份键名，避免“网关正确但服务侧错误”）：
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
- `deploy/.env.example`

---

## 5. 审计日志（写路径）

gateway 对写请求会记录审计日志：
- 范围：`/api/**` 且 method 非 `GET/OPTIONS`
- 例外：跳过 `/api/auth/login`（避免在网关层记录敏感登录参数）

目的：
- 让“谁在什么时候调用了哪个写接口、返回状态、耗时、traceId”可追踪

---

## 6. 内部 token（服务间内部接口）

内部接口通过“内部 token”保护（用于避免被普通用户调用）：
- 统一约定：所有服务的 `/internal/**` 由 `common` 层 `InternalTokenFilter` 强制校验 `X-Internal-Token`（fail-closed）。
- token 分域建议：
  1) 按 internal segment 分域：`USER_INTERNAL_TOKEN` / `CONTENT_INTERNAL_TOKEN` / `SEARCH_INTERNAL_TOKEN` / `ANALYTICS_INTERNAL_TOKEN` / `SOCIAL_INTERNAL_TOKEN`
  2) **不推荐**使用全局 `INTERNAL_TOKEN` 兜底（会扩大爆炸半径，且与 fail-closed 的默认安全态相冲突）
- 最小权限补充（user-service 高权限写入口分域 token）：
  - `/internal/users/{id}/password`、`/internal/users/{id}/moderation` 需要 **独立 token**：`user.ops.internal-token`
  - 调用方需分别配置：
    - auth-service：`auth.user-client.ops-internal-token`
    - content-service：`clients.user.ops-internal-token`

本地示例值见：
- `deploy/.env.example`

### 6.2 internal OPS 运维入口（break-glass + allowlist + X-Ops-Token）

对于极高风险的 internal 运维动作（如 search reindex / outbox replay），额外引入 `X-Ops-Token` 强保护：
- `common` 层 `InternalOpsGuardFilter` 默认 deny（break-glass 关闭时直接拒绝）
- 必须同时满足：
  - `X-Internal-Token`（基础 internal token）
  - `X-Ops-Token`（ops-token，按 segment 分域：`ops.<segment>.token`）
  - `ops.guard.<op>.enabled=true`（临时开启）
  - `ops.guard.<op>.allowlist` 命中（来源 IP/CIDR）
  - Redis 可用（用于 rate limit + single-flight；不可用时 fail-closed 返回 503）

典型入口（SSOT）：
- outbox replay（需 `ops.guard.outbox-replay.*` + `OPS_*_TOKEN`）：
  - `POST /internal/content/outbox/replay`
  - `POST /internal/social/outbox/replay`
  - `POST /internal/users/outbox/replay`
- search reindex（需 `ops.guard.search-reindex.*` + `OPS_SEARCH_TOKEN`）：
  - `POST /internal/search/reindex`（服务侧入口）
  - `POST /api/ops/search/reindex`（推荐：对外运维入口，经 gateway 转发）

⚠️ legacy 入口提示：`POST /api/search/internal/reindex` 属于历史兼容路径，**默认禁用**（gateway 返回 410 并给出迁移提示），请迁移到 `/api/ops/search/reindex`。

运维流程与回滚步骤见：
- `helloagents/wiki/runbooks/internal-ops.md`

---

## 6.3 写接口幂等（Idempotency-Key）

为避免浏览器重复点击/网络重试导致的重复副作用，本项目对部分 **HTTP 写接口** 启用幂等保护：
- header：`Idempotency-Key: <unique-key>`
- 幂等维度：`userId + operation + Idempotency-Key`
- 行为：
  - 首次请求：执行业务副作用并缓存响应
  - 重复请求：直接复用缓存响应（避免重复写入/重复通知等副作用）
  - 并发同 key：返回 `409`（提示“处理中，可重试”）

强约束（fail-closed）：
- 对“必须幂等”的入口，缺失 `Idempotency-Key` 会直接返回 `400`，以避免非预期重复副作用。
- 脚本/第三方客户端请务必显式携带该 header；示例见 `scripts/curl-idempotent-post.sh`。

TTL 配置（可按环境调整）：
- `http.idempotency.processing-ttl`（默认 `30s`）：并发互斥的 processing 锁 TTL
- `http.idempotency.success-ttl`（默认 `24h`）：成功响应缓存 TTL

> 提示：当链路可能超过 30s（慢 DB/慢下游）时，可适当提高 `processing-ttl`，降低“锁过期后二次执行”的理论风险。

---

## 7. 本地安全建议（即便是开发环境）
- 修改 `.env` 中的 `JWT_HMAC_SECRET`（>= 32 字节），不要长期用默认值
- 不要把 `.env`（含真实密钥）提交到版本库
- 默认不暴露内部依赖端口到宿主机；需要时再用 overlay 显式开启

---

## 8. 头像上传（user-service + 对象存储直传）

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
