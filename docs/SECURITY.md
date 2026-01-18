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
- 前端 origin 为 `http://localhost:12881`
- gateway 与 auth-service 都配置了允许的 origin 白名单，并允许携带凭证（cookie）

注意：
- 若用 `127.0.0.1` 访问前端，会导致 origin 变化，需要同步调整 allowlist（或统一用 `localhost`）。

---

## 4. 网关限流（Redis + metrics）

gateway 支持基于 Redis 的规则化限流：
- 规则按「method + path-pattern」匹配
- key 策略支持 IP / USER / USER_OR_IP

对外表现：
- 被限流时返回 `429 Too Many Requests`
- 同时记录指标 `gateway_rate_limit_total`（带 `rule`、`outcome` 标签）

---

## 5. 审计日志（写路径）

gateway 对写请求会记录审计日志：
- 范围：`/api/**` 且 method 非 `GET/OPTIONS`
- 例外：跳过 `/api/auth/login`（避免在网关层记录敏感登录参数）

目的：
- 让“谁在什么时候调用了哪个写接口、返回状态、耗时、traceId”可追踪

---

## 6. 内部 token（服务间内部接口）

部分内部接口通过“内部 token”保护（用于避免被普通用户调用）：
- `ANALYTICS_INTERNAL_TOKEN`
- `SEARCH_INTERNAL_TOKEN`

本地示例值见：
- `deploy/.env.example`

---

## 7. 本地安全建议（即便是开发环境）
- 修改 `.env` 中的 `JWT_HMAC_SECRET`（>= 32 字节），不要长期用默认值
- 不要把 `.env`（含真实密钥）提交到版本库
- 默认不暴露内部依赖端口到宿主机；需要时再用 overlay 显式开启

