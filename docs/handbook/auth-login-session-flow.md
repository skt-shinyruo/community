# 登录、刷新和会话流程

本文档描述 `community-app` 当前登录、refresh token 续期、logout 和后续 JWT 鉴权的代码链路。安全模型总览见 [security.md](security.md)，业务链路总览见 [business-flows.md](business-flows.md)。

## 入口和边界

认证相关入口由 `AuthSecurityRules` 放行：

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/captcha`
- `POST /api/auth/captcha/verify`

全局 API 安全配置在 `CommunitySecurityConfig`：`/api/**` 使用 stateless session，禁用 CSRF，并通过 Spring Security resource server 验证 Bearer JWT。除 `AuthSecurityRules` 显式放行的入口外，其余接口默认要求已认证。

`AuthOriginGuardFilter` 只覆盖 login / refresh / logout 这类 cookie 会话敏感入口。浏览器请求带 `Origin` 时，必须满足同源或 allowlist；无 `Origin` 的非浏览器客户端兼容放行。

## 登录入口

登录入口是 `AuthController.login(...)`，路径为 `POST /api/auth/login`。

controller 的职责只限 HTTP 适配：

1. 解析 `LoginRequest` 中的 `username`、`password`、`captchaId` 和 `captchaCode`。
2. 通过 `ClientIpResolver` 解析客户端 IP 和 IP 来源。
3. 组装 `LoginCommand`，调用 `AuthApplicationService.login(...)`。
4. 成功后将 `LoginResult.refreshCookie()` 写入 `Set-Cookie`。
5. 响应体只返回 `LoginResponse.accessToken`。

核心调用链：

```text
AuthController.login(...)
  -> AuthApplicationService.login(LoginCommand)
  -> LoginApplicationService.login(LoginCommand)
```

## 登录核心编排

`LoginApplicationService.login(...)` 是登录状态机。它不直接查用户表，而是编排 auth 域能力，并通过 user owner 的同步 API 校验账号。

主流程：

1. 从 `LoginCommand` 取 `username`、`password`、`captchaId`、`captchaCode`、`clientIp` 和 `clientIpSource`。
2. 调用 `LoginRateLimitApplicationService.assertNotBlocked(...)`，检查当前 IP / 用户名是否已经超过失败阈值。
3. 调用 `LoginRateLimitApplicationService.isCaptchaRequired(...)`，判断当前请求是否必须带验证码。
4. 如果需要验证码但请求没有 `captchaId` / `captchaCode`，记录一次失败，写安全日志，抛出 `AuthErrorCode.CAPTCHA_REQUIRED`。
5. 如果请求带验证码，调用 `CaptchaApplicationService.verify(...)`。验证失败时记录失败，写安全日志，抛出 `AuthErrorCode.CAPTCHA_INVALID`。
6. 调用 `AuthDomainService.requireCredentials(...)` 校验用户名和密码非空。空值按 `AuthErrorCode.INVALID_CREDENTIALS` 处理。
7. 调用 `UserCredentialQueryApi.authenticate(...)` 进入 user owner 域校验用户名、密码和账号状态。
8. user 域返回未认证时，`authenticationFailure(...)` 将结果转换为 `INVALID_CREDENTIALS` 或 `USER_DISABLED`。
9. 认证失败、账号禁用、验证码缺失和验证码错误都会调用 `LoginRateLimitApplicationService.recordFailure(...)`，并写结构化安全日志。
10. 认证成功后调用 `LoginRateLimitApplicationService.reset(...)` 清理该用户名 / IP 的失败计数。
11. 调用 `issueLoginResult(...)` 签发 access token 和 refresh token。
12. 写登录成功安全日志，并通过 `AnalyticsIngestActionApi.recordLoginSuccess(...)` 记录登录成功埋点。

失败语义：

- 用户名为空、密码为空、用户不存在、密码错误：`AuthErrorCode.INVALID_CREDENTIALS`。
- 用户 `status == 0`：`AuthErrorCode.USER_DISABLED`。
- 需要验证码但未提交：`AuthErrorCode.CAPTCHA_REQUIRED`。
- 验证码错误或过期：`AuthErrorCode.CAPTCHA_INVALID`。
- 达到登录失败阈值：`CommonErrorCode.TOO_MANY_REQUESTS`。
- 登录风控依赖异常：`CommonErrorCode.SERVICE_UNAVAILABLE`。

## 登录风控和验证码

登录风控由 `LoginRateLimitApplicationService` 编排，底层通过 `LoginRateLimitRepository` 计数。

默认配置：

```yaml
auth:
  login-rate-limit:
    enabled: true
    window-seconds: 60
    max-failures-per-ip: 20
    max-failures-per-user: 5
    captcha-required-failures-per-ip: 5
    captcha-required-failures-per-user: 2
```

关键行为：

- 失败计数按 IP 和用户名分别维护。
- 用户名失败 2 次或 IP 失败 5 次后要求验证码。
- 用户名失败 5 次或 IP 失败 20 次后直接拒绝登录。
- 登录成功后，当前用户名 / IP 的失败计数会被清理。
- 风控依赖异常时，阻断登录并返回服务不可用，避免 fail-open。

验证码由 `CaptchaApplicationService` 负责。默认 Redis store，TTL 60 秒，最多失败 3 次。`verify(...)` 使用 `CaptchaRepository.verifyAndConsume(...)`，匹配成功后验证码被消费；失败次数达到上限后删除验证码，要求重新获取。

## 账号密码校验

auth 域通过 `UserCredentialQueryApi.authenticate(...)` 调 user owner。适配器是 `UserCredentialApiAdapter`，实际进入 `UserCredentialApplicationService.authenticate(...)`。

user owner 认证流程：

1. 通过 `UserCredentialDomainService.trim(...)` 规范化用户名和密码。
2. 用户名或密码为空时返回 `UserAuthenticationResult.invalidCredentials()`。
3. 通过 `UserRepository.findByUsername(...)` 查询用户。
4. 用户不存在时返回 `invalidCredentials()`，不暴露账号是否存在。
5. `user.status() == 0` 时返回 `UserAuthenticationResult.userDisabled(...)`。
6. `passwordMatches(...)` 校验密码。
7. 如果历史密码格式校验成功，调用 `userRepository.updatePassword(...)` 将密码升级为 BCrypt。
8. 校验通过后返回 `UserAuthenticationResult.authenticated(...)`，再由 `UserCredentialApiAdapter` 转为 `UserAuthenticationResultView` 给 auth 域。

密码格式：

- BCrypt：`BCryptPasswordEncoder.matches(rawPassword, encodedPassword)`。
- 历史格式：`MD5(rawPassword + salt)`，规则在 `UserCredentialDomainService.legacyPasswordMatches(...)`。

authorities 也由 user owner 计算：`UserCredentialApplicationService.authoritiesOf(...)` 根据 `user.type` 返回 `ROLE_ADMIN`、`ROLE_MODERATOR` 或 `ROLE_USER`。

## Token 签发

登录成功后，`LoginApplicationService.issueLoginResult(...)` 负责签发凭证：

```text
UserCredentialQueryApi.authoritiesOf(user)
  -> AuthTokenPort.createAccessToken(...)
  -> RefreshTokenApplicationService.issue(userId)
  -> LoginResult(accessToken, refreshCookie)
```

access token 由 `JwtTokenService.createAccessToken(...)` 签发：

- 签名算法：HS256。
- `sub`：用户 UUID。
- `username`：用户名。
- `authorities`：角色列表。
- `issuer`：`security.jwt.issuer`。
- `issuedAt` / `expiresAt`：签发和过期时间。

refresh token 由 `RefreshTokenApplicationService.issue(...)` 签发：

- 生成新的 token family。
- 生成随机 refresh token。
- 根据 `security.jwt.refresh-token-ttl-seconds` 计算过期时间。
- 通过 `RefreshTokenRepository.store(...)` 保存。
- 构造 HttpOnly refresh cookie。

默认 JWT / cookie 配置：

```yaml
security:
  jwt:
    issuer: community-auth
    access-token-ttl-seconds: 900
    refresh-token-ttl-seconds: 604800
    refresh-cookie-name: refresh_token
    refresh-cookie-path: /api/auth
    refresh-cookie-same-site: Lax
    refresh-cookie-secure: false
```

当前默认 `auth.refresh.store=db`。DB-backed 实现是 `DbRefreshTokenRepository`：auth application 先对 refresh token 做 SHA-256，再通过 user owner 的 `UserRefreshTokenSessionActionApi` / `UserRefreshTokenSessionQueryApi` 读写 `auth_refresh_token`，不会明文落库。

## 后续鉴权

登录响应体返回的 access token 由前端放入后续业务请求：

```text
Authorization: Bearer <accessToken>
```

`CommunitySecurityConfig` 的 resource server 会验证 JWT 并构造 `Authentication`。业务 controller 从 Spring Security 当前认证对象读取登录用户。

`GET /api/auth/me` 直接读取已验证 JWT claim：

- `sub` 转为 `userId`。
- `username` 从 claim 读取。
- `authorities` 从 claim 读取。

`/api/auth/me` 不实时回源查库；角色变化通常要等下一次 access token 重新签发后体现。

## Refresh 续期

`POST /api/auth/refresh` 从 `refresh_token` cookie 读取 refresh token，进入 `LoginApplicationService.refresh(...)`。

主流程：

1. refresh token 为空时抛出 `AuthErrorCode.REFRESH_TOKEN_INVALID`。
2. `RefreshTokenApplicationService.rotate(...)` 消费旧 token。
3. 旧 token 找不到、已过期、已撤销或新 token 写入失败时，返回无效 refresh token。
4. 旋转成功后，通过新 refresh token 查出用户 ID。
5. 回源 user owner 查询用户凭证。
6. 用户不存在或 `status == 0` 时抛出 `AuthErrorCode.USER_DISABLED`。
7. 重新获取 authorities。
8. 签发新的 access token。
9. 返回新的 refresh cookie。

refresh token rotation 语义：

- 每次 refresh 都消费旧 refresh token。
- 成功后签发同一 family 下的新 refresh token。
- 旧 token 不再保持 active。
- 如果检测到已撤销 refresh token 被复用，`maybeRevokeFamilyForReusedToken(...)` 会按 grace window 判断是否撤销整个 family。

如果 refresh 失败且错误码是 `USER_DISABLED` 或 `REFRESH_TOKEN_INVALID`，`AuthController.refresh(...)` 会通过 `AuthApplicationService.clearRefreshCookie()` 写入 `maxAge=0` 的 refresh cookie。

## Logout

`POST /api/auth/logout` 从 cookie 读取 refresh token，进入 `LoginApplicationService.logout(...)`。

行为：

1. refresh token 为空时，只清浏览器 cookie，不做 repository 操作。
2. refresh token 存在时，调用 `RefreshTokenApplicationService.revokeFamilyByToken(...)`。
3. repository 先撤销当前 token。
4. 如果能找到该 token 所属 family，则撤销整个 family。
5. controller 写入 `maxAge=0` 的 refresh cookie，让浏览器清掉 `refresh_token`。

logout 不撤销已经签出的 access token；access token 继续依赖短 TTL 自然过期。退出登录的主要服务端效果是阻止 refresh token 继续续期。

## Refresh Session DB 状态

`auth_refresh_token` 是 DB refresh session 主状态：

- 保存 refresh token hash，不保存 refresh token 明文。
- 保存用户、family、过期时间和撤销时间。
- `store(...)` 在签发 refresh token 时写入 active session。
- `consume(...)` 在 refresh 时消费当前 active token。
- `revoke(...)` 用于单 token 撤销。
- `revokeFamily(...)` 用于 token family 族撤销。
- `revokeByUserId(...)` 用于密码重置后撤销该用户全部 refresh sessions。
- `deleteExpiredBefore(...)` 由 cleanup job 调用，只清理已过期 refresh session，不影响已经签出的 access token。

## 关键代码

- `auth.controller.AuthController`
- `auth.security.AuthSecurityRules`
- `auth.infrastructure.web.AuthOriginGuardFilter`
- `auth.application.AuthApplicationService`
- `auth.application.LoginApplicationService`
- `auth.application.LoginRateLimitApplicationService`
- `auth.application.CaptchaApplicationService`
- `auth.application.RefreshTokenApplicationService`
- `auth.application.DbRefreshTokenRepository`
- `auth.infrastructure.jwt.JwtTokenService`
- `auth.domain.service.AuthDomainService`
- `auth.domain.service.CaptchaDomainService`
- `auth.domain.service.LoginRateLimitDomainService`
- `auth.domain.service.RefreshTokenDomainService`
- `user.infrastructure.api.UserCredentialApiAdapter`
- `user.application.UserCredentialApplicationService`
- `user.application.RefreshTokenSessionApplicationService`
