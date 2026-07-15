# 安全模型

本文档是安全与鉴权 SSOT，覆盖 gateway-first 入口、JWT、refresh cookie、CORS / OriginGuard、授权矩阵、internal scope、trusted proxy、限流、审计日志、头像上传和 prod fail-closed。

## 总体边界

- 浏览器默认只访问 `community-gateway` 暴露的 `/api/**`、`/files/**` 和 IM WebSocket 前缀 `/ws/im`；`/api/im/sessions` 由 `community-im-gateway` 返回稳定的 `/ws/im`，worker 选择和内部桥接不对浏览器暴露。
- `community-gateway` 负责 HTTP / WebSocket 路由、浏览器 CORS、trace 和入口级策略。
- `community-app` 负责主站业务授权矩阵、OriginGuard、审计日志、统一错误语义。
- `im-core` 和 `im-realtime` 各自执行 IM HTTP / WebSocket 的安全配置。
- `community-common-security` 是 `security.jwt.*` 的共享配置和 JWT 验签规则来源。

资源服务器：

- `community-app`
- `community-gateway`
- `community-oss`
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
- 当非 `/api/auth/**` 业务请求返回 `401`，前端调用 `/api/auth/refresh` 获取新 access token 后重试原请求；auth 自身入口的 `401` 不触发 refresh 重试，避免循环和误刷新。
- refresh token、registration token 和 password reset token 明文由 auth application 使用统一的 256-bit `SecureRandom` 生成器生成，并使用 base64url 无填充编码。
- refresh token store 支持 `redis` / `db`，当前默认 `db`；不提供进程内存实现。
- DB store 使用 `community.auth_refresh_token`，仅保存 token hash。
- refresh 支持 recoverable rotation：刷新时先把旧 session 转入 `PENDING_ROTATION`，再回源校验用户仍允许 refresh，成功后 finish rotation 使旧 session 变为 `CONSUMED` tombstone、同 family replacement 变为 `ACTIVE`；临时失败会 rollback，无法安全恢复或用户不存在、账号被禁用、`refreshAllowed=false` 时撤销 family 并清 cookie。session 保存 `securityVersionAtIssue`；与 user 当前版本不一致时 auth 拒绝续期、撤销 family 并清 cookie。
- token family 支持族撤销，复用旧 token 可触发 family revoke。

`GET /api/auth/me` 直接读取已验证 JWT claim，不实时查库；高风险 admin/ops/wallet admin URI prefix 会额外校验 JWT 中的 `security_version`，版本落后时要求刷新或重新登录。普通前端权限展示可能仍滞后到下一次 access token 重新签发。具体 prefix、401/403 映射和失败语义见 [Token Freshness 与高风险请求安全](core-logic/security-token-freshness.md)。

`security_version` 是 user owner 的认证授权版本。角色、密码以及新增或延长活跃账号级封禁会递增该版本；user 不反向调用 auth 删除 refresh rows。账号状态和当前活跃封禁会在 login / refresh 校验中被拒绝，安全版本变化还会让旧 refresh family 在下一次续期时失效。`muteUntil` 只影响发言能力，不影响登录或 refresh。

`banUntil` 是账号级暂停，影响 login、refresh 和依赖 moderation guard 的敏感写操作。`muteUntil` 是发言级限制，只影响发帖、评论、回复和 IM / 内容侧发言能力。

## 找回密码和凭证变更

找回密码链路的安全目标是防用户枚举、防 reset link 泄漏、防旧 session 继续可用：

- 请求重置必须通过验证码；验证码通过后先按客户端 IP 做请求限流，再按邮箱查 user owner。
- 邮箱维度限流只对存在且可用的账号计数。
- 邮箱不存在、未激活或状态不可用时也返回受理结果，但不签发 token、不发送邮件，也不消耗邮箱 quota。
- reset link 只通过邮件下发，HTTP 响应体不返回链接或 token。
- token 存储在 `auth:pwdreset:<token>`，带短 TTL，确认时一次性消费；token 明文是 256-bit base64url 随机值；若 token 写入后邮件发送失败，会 best-effort 删除该 token。
- 确认重置也需要验证码。
- 新密码由 user owner 的密码策略校验：长度 8 到 `ValidationLimits.PASSWORD_MAX`，至少包含两类字符，并拒绝首尾空白字符。
- 密码更新成功后递增 user `securityVersion`。旧 cookie 下次续期时因 `securityVersionAtIssue` 不匹配而被 auth 拒绝并撤销 family。
- 如果密码更新失败，reset token 会按消费时捕获的剩余 TTL 恢复，避免把有效期延长回完整 TTL。
- prod 下 `AuthStartupValidator` 要求找回密码基础配置可用，禁止 reset link 回传和注册验证码回传类 dev-only 行为；OriginGuard 启用且 fail-closed 时必须配置 allowlist。

## CORS 和 OriginGuard

gateway-first 模式下，浏览器入口配置来自：

- `FRONTEND_PUBLIC_ORIGIN`
- `GATEWAY_PUBLIC_BASE_URL`
- `IM_GATEWAY_PUBLIC_WS_URL`
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
- 覆盖 `POST /api/auth/register`
- 覆盖 `POST /api/auth/register/code/resend`
- 覆盖 `POST /api/auth/register/code/verify`
- 覆盖 `POST /api/auth/password/reset/request`
- 覆盖 `POST /api/auth/password/reset/confirm`
- allowlist 通过 `AUTH_ORIGIN_GUARD_ALLOWED_ORIGINS` 注入。
- 配置键仍沿用 `gateway.origin-guard.*`，执行位置在单体内。
- 同源请求始终放行。
- prod 下 allowlist 缺失会 fail-closed，并由启动校验提前阻断。

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

`community-oss` 作为独立资源服务器保护对象管理面：

- `/api/oss/**`：需登录，调用方通过 `community-oss-client` 转发当前 bearer token。
- `/internal/oss/**`：需登录，用于对象引用绑定/释放等服务内协作入口。
- `GET /files/**`：匿名开放，只返回 OSS 判定可公开读取的文件内容。

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

密码重置请求风控：

- 默认启用 `auth.password-reset.request-window-seconds`、`max-requests-per-email`、`max-requests-per-ip`。
- 验证码通过后按邮箱和客户端 IP 自增计数，超过阈值返回 `TOO_MANY_REQUESTS`。
- 邮箱不存在或不可用的请求不会消耗邮箱 quota，但仍可能计入 IP 维度限流；系统不会暴露该邮箱是否存在。

gateway 路径级限流：

- 配置键：`gateway.http.rate-limit.*`
- 当前默认 `enabled=true`、`fail-open-on-error=false`、`policies={}`。
- 能力已接线，但默认没有生效中的路径规则。
- 生产全局限流仍建议优先由反代 / Ingress / WAF 承担。

## 审计日志

`AuditLogFilter` 记录主站写请求：

- 范围：`/api/**` 且 method 非 `GET` / `OPTIONS`。
- 跳过 `/api/auth/login`，避免记录敏感登录参数。
- 记录调用者、路径、状态、耗时和业务响应 `traceId` 等信息。结构化 MDC 只写 `trace.id` / `span.id`，gateway access log 不再维护旧 `traceId` MDC key。

审计日志用于追踪“谁在什么时候调用了哪个写接口，结果如何”。

## 响应数据最小暴露

- 公共用户资料和内容作者摘要不返回钱包余额、钱包状态、refresh token、密码 hash、reset token 或内部处罚实现细节。
- 钱包余额和交易只能通过 `/api/wallet/**` 的登录态接口读取；管理员钱包操作走 `/api/wallet/admin/**`。
- notice、content、market 等响应只暴露展示所需快照。涉及资金、地址、治理原因时，返回字段要以 owner DTO 为准，不复用 persistence dataobject。
- internal projection endpoint 只给服务间同步使用，不面向浏览器；浏览器需要的 IM 状态通过 `/api/im/**` 和 WebSocket frame 获得。

## 头像上传安全

头像上传是 user 域写链路，采用 OSS upload session / confirm：

1. 签发 OSS upload session。
2. 客户端按返回指令直接上传文件。
3. 使用 `objectId` 确认，user 回源 OSS metadata 校验对象归属并写回头像 URL。

风险点：

- 大文件 DoS。
- 任意 MIME。
- 覆盖他人对象。
- 绕过上传直接改头像 URL。

约定：

- 创建 upload session 时记录 OSS owner：`community-app/user/avatar/{userId}`。
- 更新头像时只接受 `objectId`，不接受 URL、路径或客户端拼接的 public file key。
- 更新头像前必须校验 OSS metadata 的 `usage/owner/visibility/status` 与当前用户匹配。
- 最大体积 2 MiB。
- MIME 白名单：`image/jpeg,image/png,image/webp,image/gif`。
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
- `AuthStartupValidator` 会在 `auth.registration.code.expose-code=true` 时阻断启动。
- 必须启用 SMTP。
- JWT secret 必须显式配置且长度满足要求。
- 真实密钥必须通过 Secrets / 配置中心注入。

## Prod Fail-closed

启动期和 bean 创建期都会执行 fail-closed：

- `StartupValidation` 聚合各模块 `StartupValidator`。
- `AuthStartupValidator` 校验 refresh cookie、找回密码、注册邮件、固定验证码和 OriginGuard fail-closed allowlist。
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
