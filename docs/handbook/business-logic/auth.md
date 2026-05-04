# Auth 认证业务逻辑

认证域负责用户进入系统的凭证和会话生命周期：注册、登录、验证码、密码重置、access token、refresh token 和登录风控。登录、刷新和退出的逐步链路另见 [../auth-login-session-flow.md](../auth-login-session-flow.md)；本文补齐认证域全部业务能力的域级视角。

## Owner / SSOT

- `auth` 拥有登录流程、验证码流程、注册验证码流程、密码重置 token、JWT 签发、refresh token 策略和登录风控。
- `user` 拥有用户账号、密码 hash、用户状态、角色和 DB refresh session 存储事实。
- access token 是客户端持有的短期 JWT，服务端不保存在线 access session。
- refresh token 明文只存在于浏览器 HttpOnly cookie 和当前请求/响应内；默认 DB 存储只保存 SHA-256 hash。

## 入口

HTTP 入口位于 `AuthController`：

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/register`
- `POST /api/auth/register/code/resend`
- `POST /api/auth/register/code/verify`
- `GET /api/auth/captcha`
- `POST /api/auth/password/reset/request`
- `POST /api/auth/password/reset/confirm`

后台入口：

- `RefreshTokenCleanupJob` 清理过期 refresh session。
- `PendingRegistrationUserCleanupJob` 保留兼容旧待激活用户清理面。

## 应用层入口

`AuthApplicationService` 是 controller 面向 auth 域的聚合入口，内部转发到更细的应用服务：

- `LoginApplicationService`：登录、refresh、logout、token 签发。
- `RegistrationApplicationService`：注册开始、生成 draft 和验证码。
- `RegistrationVerificationApplicationService`：重发注册验证码、验证注册验证码并登录。
- `CaptchaApplicationService`：图片验证码生成和校验。
- `PasswordResetApplicationService`：密码重置请求和确认。
- `LoginRateLimitApplicationService`：登录失败计数、验证码触发和封锁。
- `RefreshTokenApplicationService`：refresh token 签发、旋转、撤销、family 处理、cookie 规格。

## 注册流程

当前注册采用 Verify-First 流程，核心目标是高并发下避免先创建大量未激活用户行。

1. `AuthController.register(...)` 解析请求和客户端 IP。
2. controller 组装 `RegisterCommand`，调用 `AuthApplicationService.register(...)`。
3. `RegistrationApplicationService.register(...)` 校验验证码、用户名、密码、邮箱和邮件配置。
4. auth 域通过 `UserRegistrationActionApi.prepareRegistrationUser(...)` 进入 user owner。
5. user owner 规范化用户名和邮箱、生成预备用户 ID、计算 BCrypt 密码、准备默认头像，但不插入 `user` row。
6. auth 域把 `PreparedRegistrationDraft` 存入 draft store，并生成 opaque `registrationToken`。
7. auth 域签发注册验证码并发送邮件。
8. `verifyRegisterCode(...)` 根据 `registrationToken` 找回 draft，消费验证码。
9. 验证通过后调用 `UserRegistrationActionApi.createVerifiedRegistrationUser(...)`，由 user owner 插入 active 用户。
10. 注册验证成功后复用登录签发能力，直接返回 access token 和 refresh cookie。
11. draft/code 删除属于 cleanup，失败不应让已创建用户回滚到未注册状态。

失败语义：

- 验证码错误或过期返回认证错误，不创建用户。
- 用户名或邮箱冲突由 user owner 判断。
- 邮件发送失败时，注册流程不应伪造成功。
- abandoned draft 过期后自然清理，不会产生用户行。

## 登录流程

登录由 `LoginApplicationService.login(...)` 编排：

1. 先用用户名和 IP 调 `LoginRateLimitApplicationService.assertNotBlocked(...)`。
2. 判断当前用户名/IP 是否达到必须提交验证码的阈值。
3. 需要验证码但请求未提交时，记录失败并返回 `CAPTCHA_REQUIRED`。
4. 请求带验证码时调用 `CaptchaApplicationService.verify(...)`，失败则记录失败并返回 `CAPTCHA_INVALID`。
5. 调 `AuthDomainService.requireCredentials(...)` 校验用户名和密码非空。
6. 调 `UserCredentialQueryApi.authenticate(...)` 让 user owner 校验账号和密码。
7. 认证失败、账号禁用、验证码失败都会计入登录失败次数。
8. 认证成功后 reset 失败计数。
9. 调 `issueLoginResult(...)` 签发 access token 和 refresh token。
10. 记录安全日志，并通过 `AnalyticsIngestActionApi.recordLoginSuccess(...)` 记录登录成功采集。

user owner 的密码校验兼容两种格式：

- BCrypt：当前主格式。
- 历史 MD5 + salt：校验成功后升级为 BCrypt。

## Refresh 和 Logout

refresh 使用 refresh token rotation：

1. controller 从 refresh cookie 读 token。
2. `LoginApplicationService.refresh(...)` 调 `RefreshTokenApplicationService.rotate(...)`。
3. token hash 找不到、已撤销、过期或 family 被撤销都会失败。
4. 当前 token 被消费后签发新 token。
5. refresh 成功返回新的 access token 和新的 refresh cookie。
6. refresh 失败时 controller 视错误决定是否清 cookie。

logout：

1. controller 从 cookie 读 refresh token。
2. `LoginApplicationService.logout(...)` 撤销 token 或 token family。
3. controller 写 clear cookie。
4. access token 不会被服务端即时拉黑，依赖短 TTL 过期。

## Captcha 和登录风控

验证码由 `CaptchaApplicationService` 管理：

- `issue(...)` 生成 captchaId、图片 base64 和 TTL。
- `verify(...)` 大小写不敏感。
- 校验成功后消费验证码。
- 校验失败次数超过限制后验证码失效。

登录风控按 IP 和用户名分别计数：

- 失败次数达到低阈值后要求验证码。
- 失败次数达到高阈值后拒绝登录。
- 登录成功后清理当前用户名/IP 的失败计数。
- 风控存储异常按 fail-closed 处理，避免绕过保护。

## 密码重置

密码重置由 `PasswordResetApplicationService` 处理：

1. 请求阶段必须提交邮箱和验证码。
2. 邮箱不存在、用户不可用或未激活时，也返回已受理，避免用户枚举。
3. 符合条件时生成 reset token，存入 token store，并发送邮件链接。
4. HTTP 响应不返回 reset link。
5. 确认阶段再次校验验证码、reset token 和新密码策略。
6. user owner 更新密码。
7. 密码更新成功后撤销该用户 refresh sessions。
8. 如果密码更新失败，reset token TTL 可恢复，允许用户重试。

## 跨域协作

同步 owner API：

- `UserCredentialQueryApi`：认证账号、取角色、取凭据。
- `UserRegistrationActionApi`：准备注册用户、创建已验证用户。
- `UserRefreshTokenSessionActionApi` / `QueryApi`：DB refresh session 写入、消费、撤销、清理。
- `UserCredentialActionApi`：密码更新和会话撤销。
- `AnalyticsIngestActionApi`：登录成功采集。

认证域不直接访问 user mapper 或 user dataobject。

## 关键代码

- `auth.controller.AuthController`
- `auth.application.AuthApplicationService`
- `auth.application.LoginApplicationService`
- `auth.application.RefreshTokenApplicationService`
- `auth.application.RegistrationApplicationService`
- `auth.application.RegistrationVerificationApplicationService`
- `auth.application.CaptchaApplicationService`
- `auth.application.PasswordResetApplicationService`
- `auth.application.LoginRateLimitApplicationService`
- `auth.domain.service.*`
- `auth.infrastructure.jwt.JwtTokenService`
- `auth.infrastructure.job.RefreshTokenCleanupJob`
- `user.application.UserCredentialApplicationService`
- `user.application.RefreshTokenSessionApplicationService`
