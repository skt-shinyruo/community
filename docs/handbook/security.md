# 安全模型

本文档是安全与鉴权 SSOT，覆盖 gateway-first 入口、JWT、refresh cookie、CORS / OriginGuard、授权矩阵、internal scope、trusted proxy、限流、审计日志、头像上传和 prod fail-closed。

## 总体边界

- 浏览器默认只访问 `community-gateway` 暴露的 `/api/**`、`/files/**`、`/ws/im`。
- `community-gateway` 负责 HTTP / WebSocket 路由、浏览器 CORS、trace 和入口级策略。
- `community-app` 负责主站业务授权矩阵、OriginGuard、审计日志、统一错误语义。
- `im-core` 和 `im-realtime` 各自执行 IM HTTP / WebSocket 的安全配置。
- `community-common-security` 是 `security.jwt.*` 的共享配置和 JWT 验签规则来源。

资源服务器：

- `community-app`
- `community-gateway`
- `im-core`
- `im-realtime`

JWT 签发仍由 `community-app` 的 auth 模块负责。

## JWT 和 Refresh Cookie

### Access Token

- auth 模块签发 HS256 JWT。
- 登录响应体返回 `accessToken`。
- 前端保存在内存状态中，每次请求带 `Authorization: Bearer <token>`。
- JWT subject 是用户 UUID。
- authorities / scope 从 token claim 解析，供 Spring Security 判定。
- Servlet / WebFlux 认证失败都返回统一 `Result` 错误包体并带 trace。

### Refresh Token

- refresh token 通过 HttpOnly Cookie 下发，浏览器 JS 不可读取。
- 前端开启 `withCredentials: true`，由浏览器自动携带 cookie。
- 当业务请求返回 `401`，前端调用 `/api/auth/refresh` 获取新 access token 后重试原请求。
- refresh token store 支持 `memory` / `redis` / `db`，当前默认 `db`。
- DB store 使用 `community.auth_refresh_token`，仅保存 token hash。
- refresh 支持 rotation：刷新时可颁发新 refresh token，并使旧 token 失效。
- token family 支持族撤销，复用旧 token 可触发 family revoke。

`GET /api/auth/me` 直接读取已验证 JWT claim，不实时查库；角色变化通常要等下一次 access token 重新签发后反映。

## CORS 和 OriginGuard

gateway-first 模式下，浏览器入口配置来自：

- `FRONTEND_PUBLIC_ORIGIN`
- `GATEWAY_PUBLIC_BASE_URL`
- `IM_WS_PUBLIC_URL`
- `BROWSER_ALLOWED_ORIGINS`

`community-gateway` 是浏览器流量 CORS 唯一出口：

- `gateway.cors.allowed-origins` 由 `GATEWAY_CORS_ALLOWED_ORIGINS` 注入。
- 覆盖 `/api/**` 与 `/files/**`。
- gateway 会去重下游重复 `Access-Control-Allow-*` 头。

`community-app` 默认不对 `/api/**` 和 `/files/**` 输出浏览器 CORS 头。若直接跨 origin 访问 `community-app`，默认不会获得跨域放行。

OriginGuard 位于 `community-app`：

- 覆盖 `POST /api/auth/login`
- 覆盖 `POST /api/auth/refresh`
- 覆盖 `POST /api/auth/logout`
- allowlist 通过 `AUTH_ORIGIN_GUARD_ALLOWED_ORIGINS` 注入。
- 配置键仍沿用 `gateway.origin-guard.*`，执行位置在单体内。
- 同源请求始终放行。
- prod 下 allowlist 缺失会 fail-closed。

如果用 `127.0.0.1` 访问前端，或修改前端端口，需要同步更新浏览器 origin 相关配置。

## 授权矩阵

主站业务授权矩阵不是散落在 controller 上，而是统一收口到：

- `CommunitySecurityConfig`
- `ApiSecurityRules`
- 各业务域 `*SecurityRules`

当前主站业务面通常有两条 Servlet filter chain：

1. Actuator / metrics chain：由基础设施安全配置保护。
2. API chain：由 `CommunitySecurityConfig` 组装主业务授权矩阵。

`ApiSecurityRules` 按 `@Order` 注入并在 `CommunitySecurityConfig` 中注册。最后会统一兜底为 authenticated，避免未声明路径静默匿名开放。

当前典型规则：

- `/api/auth/**`：登录、注册、验证码、refresh 等按 auth 规则开放或保护。
- `/api/users/admin/**`：ADMIN-only。
- `/api/posts/**`：读多为匿名，写需登录，审核/置顶/加精/删除需 ADMIN / MODERATOR。
- `/api/likes/**`、`/api/follows/**`、`/api/blocks/**`：写需登录，部分 GET 允许匿名。
- `/api/notices/**`：需登录。
- `/api/search/posts`：读 permitAll。
- `/api/analytics/**`：ADMIN / MODERATOR。
- `/api/market/**`：公开 listing GET permitAll，其余需登录。
- `/api/admin/market/**`：ADMIN。
- `/api/wallet/**`：需登录。
- `/api/wallet/admin/**`：ADMIN。
- `/api/ops/**`：ADMIN-only。
- `/internal/im/realtime/projections/**`：需要 `SCOPE_im.realtime.internal`。

路径级授权只是第一层。业务内仍要做资源级校验，例如用户只能修改自己的资料、钱包只能操作自己的账户、管理员不能误降权自己等。

## Internal Scope

当前 `/internal/**` 不作为普通运维入口。运维动作统一走 `/api/ops/**`，由 ADMIN 权限保护。

保留的 internal 面主要服务 IM realtime bootstrap：

- `community-app`：`/internal/im/realtime/projections/user-policies`
- `community-app`：`/internal/im/realtime/projections/block-relations`
- `im-core`：`/internal/im/realtime/projections/room-memberships`

这些接口只允许具备内部 scope 的 JWT 访问，不面向浏览器业务流量。

`im-realtime` 启动时：

- 用 internal scope JWT 调 `community-app` 拉用户处罚和拉黑 snapshot。
- 用 internal scope JWT 调 `im-core` 拉房间成员 snapshot。
- 运行期继续消费 IM policy Kafka 事件刷新本地 projection。

## Trusted Proxy 和真实客户端 IP

默认安全态：

- 不信任 `X-Forwarded-For` / `X-Real-IP`。
- 使用 `remoteAddr` 作为客户端 IP，避免客户端伪造转发头绕过风控、限流或统计。

生产部署在 Nginx / Ingress / Load Balancer 后时，可显式开启：

```text
gateway.trusted-proxy.enabled=true
gateway.trusted-proxy.cidrs=[10.0.0.0/8,192.168.0.0/16,...]
```

行为：

- 只有 `remoteAddr` 命中 CIDR allowlist，才解析 `X-Forwarded-For` 第一个 IP。
- 否则继续使用 `remoteAddr`。

prod 下如果开启 trusted proxy：

- CIDR 为空会阻断启动。
- 禁止 `0.0.0.0/0` 或 `::/0`。

## 限流和风控

登录风控：

- 默认启用 `auth.login-rate-limit.*`。
- 维度包括用户名、用户 IP 和组合维度。
- 达到阈值后拒绝登录，必要时要求验证码。

gateway 路径级限流：

- 配置键：`gateway.http.rate-limit.*`
- 当前默认 `enabled=true`、`fail-open-on-error=false`、`policies={}`。
- 能力已接线，但默认没有生效中的路径规则。
- 生产全局限流仍建议优先由反代 / Ingress / WAF 承担。

## 审计日志

`AuditLogFilter` 记录主站写请求：

- 范围：`/api/**` 且 method 非 `GET` / `OPTIONS`。
- 跳过 `/api/auth/login`，避免记录敏感登录参数。
- 记录调用者、路径、状态、耗时、traceId 等信息。

审计日志用于追踪“谁在什么时候调用了哪个写接口，结果如何”。

## 头像上传安全

头像上传是 user 域写链路，采用 token / ticket / confirm 三段式：

1. 签发上传 token。
2. 客户端上传文件。
3. 确认并写回头像 URL。

风险点：

- 大文件 DoS。
- 任意 MIME。
- 覆盖他人对象。
- 绕过上传直接改头像 URL。

约定：

- 签发 token 时绑定 `fileName -> userId`，Redis TTL。
- 更新头像时一次性消费 ticket，防重放、防越权。
- `fileName` 必须为 `avatar/{userId}/...` 前缀。
- Redis 中必须存在对应 ticket 且归属当前用户。
- ticket 使用后删除。
- 最大体积 2 MiB。
- MIME 白名单：`image/jpeg,image/png,image/webp,image/gif`。
- 对象存储 token 使用 `insertOnly=1`，避免覆盖已有对象。
- 上传失败必须视为失败，不允许 demo 兜底“上传失败仍更新头像”。

## Dev-only 安全边界

本地存在一些 dev-only 便利项，生产禁止复用：

- 默认账号：`aaa/aaa`、`admin/aaa`。
- 固定验证码：`auth.captcha.fixed-code`。
- 注册验证码回传：`AUTH_REGISTRATION_EXPOSE_CODE=true`。
- Mock Data Studio 本地控制面。

prod 下约束：

- 禁止固定验证码。
- 禁止回传注册验证码。
- 必须启用 SMTP。
- JWT secret 必须显式配置且长度满足要求。
- 真实密钥必须通过 Secrets / 配置中心注入。

## Prod Fail-closed

启动期和 bean 创建期都会执行 fail-closed：

- `StartupValidation` 聚合各模块 `StartupValidator`。
- `AuthStartupValidator` 校验 refresh cookie、找回密码、注册邮件、固定验证码。
- 共享安全基础设施校验 JWT secret。
- trusted proxy 校验 CIDR。
- actuator / metrics basic auth 如果启用但缺凭据，会失败。
- outbox 启用但缺 JDBC store，会失败。

这些规则的目标是避免“缺配置就用默认值继续上线”。

## 相关代码

- `backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java`
- `backend/community-app/src/main/java/com/nowcoder/community/app/security/ApiSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/security/UserSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/security/SocialSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/security/SearchSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/security/AnalyticsSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/security/WalletSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/security/OpsSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/web/AuthOriginGuardFilter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/ServletInfraSecurityConfig.java`
- `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/AuditLogFilter.java`
