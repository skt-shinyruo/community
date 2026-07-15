# Token Freshness 与高风险请求安全

本文只描述当前已实现的 token freshness 行为。它在普通 JWT 验签之后，为少量高风险 URI 追加一次 `security_version` 校验，避免旧 access token 在账号凭证或授权版本变化后继续访问敏感入口。

## JWT 签发

`LoginTokenIssuer.issueAccessToken(UserCredentialView user)` 从 `UserCredentialView.securityVersion()` 读取当前认证授权版本，并传给 `AuthTokenPort.createAccessToken(...)`。

`JwtTokenService.createAccessToken(...)` 签发 access token 时，把该版本写入 JWT claim `security_version`。JWT subject 仍是用户 UUID。

## 过滤器位置与生效范围

`CommunitySecurityConfig.apiSecurityFilterChain(...)` 的 security chain 匹配 `/api/**` 和 `/internal/**`，并把 `TokenFreshnessFilter` 加在 `BearerTokenAuthenticationFilter` 之后。

`TokenFreshnessFilter` 并不校验该 chain 内的全部请求。当前 freshness enforcement 只按请求 URI 前缀生效：

- `/api/users/admin/`
- `/api/ops/`
- `/api/admin/market/`
- `/api/wallet/admin/`

不匹配这些前缀的路径会直接继续 filter chain，跳过 freshness verification。当前代码按路径前缀门禁，不按 HTTP method 区分读写请求。

## 请求校验流程

命中高风险前缀时，`TokenFreshnessFilter` 执行以下只读流程：

1. 从 `SecurityContextHolder` 当前认证主体读取 `Jwt`。
2. 从 JWT subject 解析用户 UUID；缺失或格式非法会得到 `null`。
3. 从 JWT claim `security_version` 读取数值版本；缺失时按 `0` 传入。
4. 调用 `TokenFreshnessApplicationService.verify(userId, tokenSecurityVersion)`。

`verify(...)` 的结果语义：

- `STALE`：`userId` 缺失或非法、token 版本缺失 / 非正数、或 token 版本与当前凭证版本不一致。
- `DENIED`：用户凭证不存在，或 `loginAllowed=false`。
- `ACCEPTED`：用户凭证存在、允许登录，且当前 `securityVersion` 与 token 中版本一致。

filter 响应映射：

- `ACCEPTED`：继续执行后续 filter / handler。
- `STALE`：返回 HTTP `401`。
- `DENIED`：返回 HTTP `403`。

## 一致性与失败语义

该机制是 read-only 且幂等的：它只读取当前 JWT、认证上下文和 user owner 暴露的凭证视图，不修改用户、access token 或 refresh session。

密码更新、角色调整以及新增或延长活跃账号级封禁由 user owner 递增 `securityVersion`。同一个版本同时约束两类 token：高风险 URI 上的旧 access token 由本 filter 立即拒绝；auth refresh session 的 `securityVersionAtIssue` 在续期时由 `LoginApplicationService.refresh(...)` 比对，不一致会撤销整个 family。user 不需要也不允许跨域直接操作 auth refresh repository。

`TokenFreshnessApplicationService.verify(...)` 不捕获 `UserCredentialQueryApi` 的异常；`TokenFreshnessFilter` 也没有把应用服务或运行时异常转成本地放行。除 subject UUID 解析失败会转换成 stale 外，意外的 user API / runtime failure 会继续向外传播。

## 关键代码

- `com.nowcoder.community.auth.application.TokenFreshnessApplicationService`
- `com.nowcoder.community.auth.infrastructure.web.TokenFreshnessFilter`
- `com.nowcoder.community.auth.infrastructure.jwt.JwtTokenService`
- `com.nowcoder.community.auth.application.LoginTokenIssuer`
- `com.nowcoder.community.user.api.model.UserCredentialView`
- `com.nowcoder.community.app.security.CommunitySecurityConfig`
