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

- auth 模块使用 RS256 签发 access JWT；JOSE header `typ=at+jwt`，payload `aud=community-api`。
- 只有 `community-app` 注入 `security.jwt.access-private-key`；gateway、OSS 和 IM 资源服务只持有 `access-public-key`，验签端泄露不能获得 access token 签发能力。
- access decoder 同时强制 RS256、configured issuer、`typ=at+jwt` 和 `aud=community-api`，service token 或缺少 typ/aud 的 JWT 不能混入浏览器 API chain。
- 登录响应体返回 `accessToken`。
- 前端保存在内存状态中，每次请求带 `Authorization: Bearer <token>`。
- JWT subject 是用户 UUID。
- authorities / scope 从 token claim 解析，供 Spring Security 判定。
- Servlet / WebFlux 认证失败都返回统一 `Result` 错误包体并带 trace。

### Service Token

- 服务间 `/internal/**` 调用使用独立的 `security.jwt.service-hmac-secret` 签发 HS256 JWT，不复用 access RSA 私钥或 IM session-ticket secret。
- JOSE header 必须是 `typ=service+jwt`，payload 必须匹配 configured issuer 和目标服务 audience，例如 `community-app`、`im-core` 或 `community-oss`，并携带入口要求的 scope。
- internal endpoint 使用独立 SecurityFilterChain 和 service decoder；`at+jwt` 不会被当成 service token，`service+jwt` 也不会被普通 `/api/**` access decoder 接受。
- service HMAC secret 的持有者仍属于同一内部信任域；这次拆分保证其泄露不能伪造浏览器 access token，不等同于为每个内部调用方建立独立签名身份。

### Refresh Token

- refresh token 通过 HttpOnly Cookie 下发，浏览器 JS 不可读取。
- 前端开启 `withCredentials: true`，由浏览器自动携带 cookie。
- 当非 `/api/auth/**` 业务请求返回 `401`，前端调用 `/api/auth/refresh` 获取新 access token 后重试原请求；auth 自身入口的 `401` 不触发 refresh 重试，避免循环和误刷新。
- refresh token 和 registration token 明文由 auth application 使用统一的 256-bit `SecureRandom` 生成器生成并使用 base64url 无填充编码；password reset token 从随机 delivery ID 和独立 HMAC 密钥确定性派生，outbox 无需保存 bearer token。
- refresh session 固定使用数据库持久化，不提供 Redis 或进程内存实现；`community.auth_refresh_token` 仅保存 token hash。
- refresh 支持 recoverable rotation：刷新时先把旧 session 转入 `PENDING_ROTATION`，再回源校验用户仍允许 refresh，成功后 finish rotation 使旧 session 变为 `CONSUMED` tombstone、同 family replacement 变为 `ACTIVE`；临时失败会 rollback，无法安全恢复或用户不存在、账号被禁用、`refreshAllowed=false` 时撤销 family。session 保存 `securityVersionAtIssue`；与 user 当前版本不一致时 auth 拒绝续期并撤销 family。refresh 失败响应不写 `Set-Cookie`，只有显式 logout 清 cookie。
- token family 支持族撤销，复用旧 token 可触发 family revoke。

`GET /api/auth/me` 直接读取已验证 JWT claim，不单独组装数据库用户视图；`community-app` 对所有携带已认证 access JWT 的 `/api/**` 请求校验 `security_version`，版本落后时返回 `401` 并要求 refresh 或重新登录。`community-im-gateway` 在签发 session ticket 前、`im-core` 在放行浏览器 `/api/**` 前，都把原始 Bearer token 回源该入口做权威 freshness 判定；缺失或非法版本返回 `401`，账号不允许登录返回 `403`，owner 超时或不可用返回 `503`，均不降级放行。匿名访问 permitAll 路径时没有 JWT，不执行 freshness 查询。具体映射、超时和失败语义见 [Token Freshness 与 API 请求安全](core-logic/security-token-freshness.md)。

`security_version` 是 user owner 的认证授权版本。角色、密码以及新增或延长活跃账号级封禁会递增该版本；user 不反向调用 auth 删除 refresh rows。账号状态和当前活跃封禁会在 login / refresh 校验中被拒绝，安全版本变化还会让旧 refresh family 在下一次续期时失效。`muteUntil` 只影响发言能力，不影响登录或 refresh。

`banUntil` 是账号级暂停，影响 login、refresh 和依赖 moderation guard 的敏感写操作。`muteUntil` 是发言级限制，只影响发帖、评论、回复和 IM / 内容侧发言能力。

## 找回密码和凭证变更

找回密码链路的安全目标是防用户枚举、防 reset link 泄漏、防旧 session 继续可用：

- 请求重置必须通过验证码；验证码通过后在查询 user owner 前，按客户端 IP 和规范化邮箱分别做请求限流。
- IP / 邮箱 Redis key 只包含使用独立 `AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET` 计算的 HMAC 标识，不存储原始标识符；该密钥不得回退或复用 `JWT_SERVICE_HMAC_SECRET`，生产值至少 32 字节。
- refresh session 只有数据库实现，避免形成没有生产用途的可切换存储协议和单 Redis Cluster slot 热点。
- 邮箱不存在、未激活或状态不可用时也消耗相同 quota，并写 dummy reset token 与空收件地址 outbox；worker 不调用 SMTP，HTTP 返回相同受理结果，避免通过响应和内部处理时序差异枚举账号。
- reset link 只通过邮件下发，HTTP 响应体不返回链接或 token。
- token 是从随机 delivery ID 与独立 HMAC 密钥派生的 256-bit base64url 值；Redis key 只包含 SHA-256 token ID，记录绑定签发时 `securityVersion` 和 generation。邮件 outbox payload 不包含 bearer token，只携带不可逆 derivation key ID；轮换期间 worker 可从受控旧密钥 keyring 派生原链接，成功后 outbox 原子清空 payload。
- 确认重置也需要验证码。
- reset token 确认采用 `ACTIVE -> PENDING -> consumed` lease/fencing 状态机；进程或下游失败后只有当前 lease owner 能在原剩余 TTL 内 rollback，过期 lease 可被新请求接管。
- 新密码由 user owner 的密码策略校验：长度 8 到 `ValidationLimits.PASSWORD_MAX`，UTF-8 编码最多 72 字节，至少包含两类字符，并拒绝 Unicode 首尾空白字符。
- user owner 只在 token 签发 `securityVersion` 仍匹配时 CAS 改密。成功或 stale CAS 都撤销旧 generation；后签发的新 generation 不会被旧请求清理。旧 cookie 下次续期时因安全版本不匹配而被 auth 拒绝并撤销 family。
- prod 下 `AuthStartupValidator` 要求找回密码基础配置可用，禁止 reset link 回传和注册验证码回传类 dev-only 行为；OriginGuard 启用且 fail-closed 时必须配置 allowlist。

## CORS 和 OriginGuard

gateway-first 模式下，浏览器入口配置来自：

- `FRONTEND_PUBLIC_ORIGIN`
- `GATEWAY_PUBLIC_BASE_URL`
- `IM_GATEWAY_PUBLIC_WS_URL`
- `BROWSER_ALLOWED_ORIGINS`

`community-gateway` 是浏览器流量 CORS 唯一出口：

- `nacos-config-bootstrap` 使用唯一的 `BROWSER_ALLOWED_ORIGINS` 输入渲染
  `community-gateway.yaml`，再发布 `gateway.cors.allowed-origins`。
- 同一个 bootstrap 还渲染 `FRONTEND_PUBLIC_ORIGIN`、`GATEWAY_PUBLIC_BASE_URL`、
  `OSS_PUBLIC_BASE_URL` 和 `IM_GATEWAY_PUBLIC_WS_URL`，分别用于重置密码链接、运行时
  Gateway 地址、OSS 文件地址和 IM WebSocket 地址。
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
- `community-app.yaml` 使用同一个 `BROWSER_ALLOWED_ORIGINS` 渲染 OriginGuard allowlist。
- 配置键仍沿用 `gateway.origin-guard.*`，执行位置在单体内。
- 同源请求始终放行。
- prod 下 allowlist 缺失会 fail-closed，并由启动校验提前阻断。

`community-im-gateway.yaml`、`im-core.yaml` 和 `im-realtime.yaml` 的浏览器 CORS
allowlist 也由同一个输入渲染。若使用 `127.0.0.1` 或修改前端端口，只需要更新
`BROWSER_ALLOWED_ORIGINS`；不应把 owner-specific CORS 变量加入 runtime service
Compose environment。`application.yml` 中的 `GATEWAY_CORS_ALLOWED_ORIGINS`、
`AUTH_ORIGIN_GUARD_ALLOWED_ORIGINS` 和 `IM_CORS_ALLOWED_ORIGINS` 仅用于绕过 Nacos
时的本地/测试 fallback。

## 授权矩阵

主站业务授权矩阵不是散落在 controller 上，而是统一收口到：

- `CommunitySecurityConfig`
- `ApiSecurityRules`
- 各业务域 `*SecurityRules`

当前主站业务面通常有三条 Servlet filter chain：

1. Actuator / metrics chain：由基础设施安全配置保护。
2. Internal chain：只接受 `service+jwt`，按目标 audience 和 scope 保护 `/internal/**`。
3. API chain：只接受 `at+jwt`，由 `CommunitySecurityConfig` 组装主业务授权矩阵并执行 token freshness。

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
- `/internal/oss/**`：要求 `service+jwt`、`aud=community-oss` 和配置的 internal scope，用于对象引用绑定/释放等服务内协作入口。
- `GET /files/**`：匿名开放，只返回 OSS 判定可公开读取的文件内容。

路径级授权只是第一层。业务内仍要做资源级校验，例如用户只能修改自己的资料、钱包只能操作自己的账户、管理员不能误降权自己等。

## Internal Scope

当前 `/internal/**` 不作为普通运维入口。运维动作统一走 `/api/ops/**`，由 ADMIN 权限保护。

保留的 internal 面主要服务 IM realtime bootstrap：

- `community-app`：`/internal/im/realtime/projections/user-policies`
- `community-app`：`/internal/im/realtime/projections/block-relations`
- `im-core`：`/internal/im/realtime/projections/room-memberships`

这些接口只允许具备内部 scope、`typ=service+jwt`、正确 issuer 和目标 audience 的 service token 访问，不面向浏览器业务流量。普通 access token 的 `aud=community-api`，不能进入 internal chain。

`im-realtime` 启动时：

- 用 internal scope JWT 调 `community-app` 拉用户处罚和拉黑 snapshot。
- 用 internal scope JWT 调 `im-core` 拉房间成员 snapshot。
- 运行期继续消费 IM policy Kafka 事件刷新本地 projection。

## Trusted Proxy 和真实客户端 IP

默认安全态：

- 不信任 `X-Forwarded-For` / `X-Real-IP`。
- 使用 `remoteAddr` 作为客户端 IP，避免客户端伪造转发头绕过风控、限流或统计。

生产部署在 Nginx / Ingress / Load Balancer 后时，按服务 owner 分别配置 trusted proxy，不能把两个服务的 allowlist 放进共享配置：

Gateway（WebFlux）使用 `gateway.trusted-proxy`，环境变量为 `GATEWAY_TRUSTED_PROXY_ENABLED` 和 `GATEWAY_TRUSTED_PROXY_CIDRS`：

```text
gateway.trusted-proxy.enabled=true
gateway.trusted-proxy.cidrs=[10.0.0.0/8,192.168.0.0/16,...]
```

Community Servlet（`community-app` 及使用 common-web 的服务）使用 `community.web.trusted-proxy`，当前部署环境变量为 `COMMUNITY_APP_TRUSTED_PROXY_ENABLED` 和 `COMMUNITY_APP_TRUSTED_PROXY_CIDRS`：

```text
community.web.trusted-proxy.enabled=true
community.web.trusted-proxy.cidrs=[10.0.0.0/8,192.168.0.0/16,...]
```

行为：

- 只有 `remoteAddr` 命中本服务的 CIDR allowlist，才读取 `X-Forwarded-For`。
- XFF 按“客户端 -> 各级代理”排列；解析时从右向左剥离连续命中的可信代理 hop，选择最靠右的第一个不可信 hop 作为客户端 IP。
- `remoteAddr` 不可信、XFF hop 格式非法或链路超出限制时，回退使用 `remoteAddr`。
- Gateway 会先规范化转发头；Servlet 服务仍按自己的 `community.web.trusted-proxy` allowlist 独立解析。

prod 下 `community-app` 如果开启 Servlet trusted proxy：

- CIDR 为空、无法绑定为列表或不是 IPv4/IPv6 literal CIDR 会阻断启动。
- 禁止 `0.0.0.0/0` 或 `::/0`。

迁移说明：历史 Servlet 配置若使用 `gateway.trusted-proxy` 前缀，必须迁移到 `community.web.trusted-proxy`，并将 `GATEWAY_TRUSTED_PROXY_*` 改为 `COMMUNITY_APP_TRUSTED_PROXY_*`。`gateway.trusted-proxy` 仍仅归 Gateway owner 使用。

## 限流和风控

登录风控：

- 默认启用 `auth.login-rate-limit.*`。
- 维度包括用户 IP、查库前的精确用户名输入和数据库排序规则 authoritative subject；Redis key 只保存带版本的 HMAC 伪名，不暴露原始用户名、IP 或排序权重。
- 达到阈值后拒绝登录，必要时要求验证码。
- 任何 user owner / MySQL 调用前，先按 IP 与 trim 后但不做 Java Unicode 折叠的原始输入获取 tokenized Redis ZSET permit。user owner 随后用不读取用户表的 MySQL `utf8mb4_unicode_ci` `WEIGHT_STRING` scalar 返回存在性无关的 opaque subject；auth 先取得 subject lease，再释放 provisional input lease，IP lease 全程不断档。
- captcha、登录失败计数和成功清理只使用 authoritative subject，不使用 `userId`。排序规则别名以及已知 / 未知账号经过同一主体推导，避免账号存在性 oracle 和别名绕过预算。
- 同 slot Lua 使用 Redis `TIME`，只有 `已提交失败数 + 活跃 token lease 数` 尚未达到阈值时才准入；失败次数提交后才释放最终 permit。
- user owner 同时校验登录输入和数据库返回的存量用户名。若旧行含控制字符、Unicode format 字符或不可见字符，即使安全输入能按 `utf8mb4_unicode_ci` 命中该行，也只返回与账号不存在相同的 dummy challenge，不暴露真实 user ID 或密码 hash。
- 缺失账号和非法密码 hash 也执行固定 dummy BCrypt；禁用账号在密码错误时仍返回通用无效凭据。
- 成功登录只清 authoritative subject 失败桶，不清共享 IP 桶。

密码重置请求风控：

- 默认启用 `auth.password-reset.request-window-seconds`、`max-requests-per-email`、`max-requests-per-ip`。
- 验证码通过后按邮箱和客户端 IP 自增计数，超过阈值返回 `TOO_MANY_REQUESTS`。
- 邮箱不存在或不可用的请求也消耗规范化邮箱 quota 和 IP quota；两类 key 都是 HMAC 伪名，系统不会通过配额行为暴露该邮箱是否存在。

注册请求风控：

- captcha 和字段校验后、BCrypt 与 draft 创建前，按客户端 IP、规范化用户名、规范化邮箱分别原子计数。
- 三类 Redis key 都只保存 HMAC 伪名；依赖异常 fail-closed，避免 Redis 故障时放大密码哈希或邮件副作用。

gateway 路径级限流：

- 配置键：`gateway.http.rate-limit.*`
- 当前默认 `enabled=true`、`fail-open-on-error=false`。
- `POST /api/drive/shares/{shareToken}/verify` 默认按客户端身份限制为每分钟 10 次；匿名请求使用 canonical client IP，已认证请求使用 principal。所有实际 share token 共用稳定的路径模式键，不能靠更换 token 绕过同一身份预算。
- 路径策略使用 Spring `PathPattern` 语义；精确路径优先，多个模式同时命中时使用更具体的模式。
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
- 必须启用 SMTP，并配置非空 host、port、From 和有界 connection/read/write timeout；启用 SMTP auth 时还必须注入 username/password，并启用 STARTTLS 或隐式 SSL。
- OriginGuard 必须启用且显式 fail-closed，allowlist 至少包含一个合法的 http/https Origin；空项、路径、userinfo、query 和 fragment 均会阻断启动。
- 密码重置窗口/邮箱/IP quota、注册请求窗口/用户名/邮箱/IP quota、注册重发窗口/registration identity/邮箱/IP quota，以及验证码 TTL/失败次数/IP 签发 quota 必须是有界正数，不能用 `0` 关闭生产防滥用控制。
- access RSA 公私钥必须显式配置、至少 2048 bit 且匹配；私钥只注入 `community-app`。
- service HMAC secret 必须显式配置、至少 32 bytes，并与 IM session-ticket secret 分离。
- 真实密钥必须通过 Secrets / 配置中心注入。

## Prod Fail-closed

启动期和 bean 创建期都会执行 fail-closed：

- `StartupValidation` 聚合各模块 `StartupValidator`；`prod` / `production` profile 匹配不区分大小写，`PROD` 同样会启用校验。
- `AuthStartupValidator` 校验 refresh cookie、找回密码、注册邮件、固定验证码和 OriginGuard fail-closed allowlist。
- 共享安全基础设施校验 access 公钥、issuer、audience；签发端额外校验 access 私钥和公钥匹配，service token 使用方校验独立 HMAC secret。
- trusted proxy 校验 CIDR。
- actuator / metrics basic auth 如果启用但缺凭据，会失败；`community-im-gateway` 的 `/actuator/prometheus` 与其他运行时服务一样要求 `METRICS_BASIC_AUTH_*`，健康和 info 端点仍可匿名读取。
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
