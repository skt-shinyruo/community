# Token Freshness 与 API 请求安全

本文只描述当前已实现的 token freshness 行为。它在普通 access JWT 验签之后，为主站和 IM 的浏览器 API 请求追加一次 `security_version` 校验，避免旧 access token 在账号凭证或授权版本变化后继续访问业务入口。

## JWT 签发

`LoginTokenIssuer.issueAccessToken(UserCredentialView user)` 从 `UserCredentialView.securityVersion()` 读取当前认证授权版本，并传给 `AuthTokenPort.createAccessToken(...)`。

`JwtTokenService.createAccessToken(...)` 签发 access token 时，把该版本写入 JWT claim `security_version`。JWT subject 仍是用户 UUID。

## 过滤器位置与生效范围

`CommunitySecurityConfig.apiSecurityFilterChain(...)` 只匹配 community-app 的 `/api/**`，并把 `TokenFreshnessFilter` 加在 `BearerTokenAuthenticationFilter` 之后。`community-im-gateway` 在签发 session ticket 前执行同一 owner freshness 判定；`im-core` 的 `AccessTokenFreshnessFilter` 则保护其 `/api/**` history、room、read watermark 和 unread 等 HTTP 入口。各模块的 `/internal/**` 使用独立 service-token chain，不执行 user token freshness。

`TokenFreshnessFilter` 对当前认证主体是 `Jwt` 的请求执行校验；没有 bearer JWT 的匿名 permitAll 请求直接继续 filter chain。它不维护 URI 前缀白名单，也不按 HTTP method 区分读写请求，因此内容治理、moderation、analytics、ops、market admin 和 wallet admin 等路径不会漏出 freshness 例外。IM gateway / core 只接受存在且为正整数的 `security_version`；缺失、非整数、字符串、零或负数在回源前直接返回 `401`。

## 请求校验流程

请求携带已认证 access JWT 时，`TokenFreshnessFilter` 执行以下只读流程：

1. 从 `SecurityContextHolder` 当前认证主体读取 `Jwt`。
2. 从 JWT subject 解析用户 UUID；缺失或格式非法会得到 `null`。
3. 从 JWT claim `security_version` 读取正整数版本；缺失或格式非法时按 stale 处理。
4. 调用 `TokenFreshnessApplicationService.verify(userId, tokenSecurityVersion)`。

`verify(...)` 的结果语义：

- `STALE`：`userId` 缺失或非法、token 版本缺失 / 非正数、或 token 版本与当前凭证版本不一致。
- `DENIED`：用户凭证不存在，或 `loginAllowed=false`。
- `ACCEPTED`：用户凭证存在、允许登录，且当前 `securityVersion` 与 token 中版本一致。

filter 响应映射：

- `ACCEPTED`：继续执行后续 filter / handler。
- `STALE`：返回 HTTP `401`。
- `DENIED`：返回 HTTP `403`。

IM gateway 和 im-core 不复制 user 凭据表，也不缓存 freshness 结果。它们把原始 Bearer access token 转发给 community-app 的 `GET /api/auth/me`；该入口自身经过上述 owner filter，因此 `2xx` 表示版本仍新鲜，`401` 表示 stale，`403` 表示账号不再允许登录。gateway 只在得到 `2xx` 后选择 worker 和签发 ticket，im-core 只在得到 `2xx` 后进入 controller。

owner 返回其他状态、超时、服务发现失败或连接失败时，gateway / im-core 都返回 HTTP `503`，不会在无法证明 token 新鲜时本地放行。请求超时默认 `500ms`，由 `im.access-token-freshness.request-timeout` 配置；目标服务默认是 `community-app`，由 `im.access-token-freshness.community-service-id` 配置。

两个回源客户端都通过有序执行 Boot 4 的 client customizer 构建，因此继承项目统一的 HTTP client observation / metrics 配置。gateway 的 `WebClient` 用 Reactor `timeout` 约束完整请求预算；im-core 的 `RestClient` 另外把同一预算显式写入连接超时和读取超时，避免 servlet 请求线程无界等待。

## 一致性与失败语义

该机制是 read-only 且幂等的：它只读取当前 JWT、认证上下文和 user owner 暴露的凭证视图，不修改用户、access token 或 refresh session。

密码更新、角色调整以及新增或延长活跃账号级封禁由 user owner 递增 `securityVersion`。同一个版本同时约束两类 token：所有 `/api/**` 请求上的旧 access token 由本 filter 立即拒绝；auth refresh session 的 `securityVersionAtIssue` 在续期时由 `LoginApplicationService.refresh(...)` 比对，不一致会撤销整个 family。user 不需要也不允许跨域直接操作 auth refresh repository。

`TokenFreshnessApplicationService.verify(...)` 不捕获 `UserCredentialQueryApi` 的异常；`TokenFreshnessFilter` 也没有把应用服务或运行时异常转成本地放行。除 subject UUID 解析失败会转换成 stale 外，community-app 内部意外的 user API / runtime failure 会继续向外传播；IM 调用方看到非 `2xx` / `401` / `403` 的 owner 结果时统一按 `503` 失败关闭。

## 关键代码

- `com.nowcoder.community.auth.application.TokenFreshnessApplicationService`
- `com.nowcoder.community.auth.infrastructure.web.TokenFreshnessFilter`
- `com.nowcoder.community.auth.infrastructure.jwt.JwtTokenService`
- `com.nowcoder.community.auth.application.LoginTokenIssuer`
- `com.nowcoder.community.user.api.model.UserCredentialView`
- `com.nowcoder.community.app.security.CommunitySecurityConfig`
- `com.nowcoder.community.common.security.jwt.AccessTokenClaims`
- `com.nowcoder.community.im.gateway.security.OwnerAccessTokenFreshnessVerifier`
- `com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService`
- `com.nowcoder.community.im.core.security.AccessTokenFreshnessFilter`
- `com.nowcoder.community.im.core.security.OwnerAccessTokenFreshnessVerifier`
