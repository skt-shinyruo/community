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

## 应用层入口

`AuthController` 直接进入具体 auth 用例应用服务，不再经过总入口式聚合门面：

- `LoginApplicationService`：登录、refresh、logout、token 签发。
- `RegistrationApplicationService`：注册开始、生成 draft 和验证码。
- `RegistrationVerificationApplicationService`：重发注册验证码、验证注册验证码并登录。
- `CaptchaApplicationService`：图片验证码生成和校验。
- `PasswordResetApplicationService`：密码重置请求和确认。
- `LoginRateLimitApplicationService`：登录失败计数、验证码触发和封锁。
- `RefreshTokenApplicationService`：refresh token 签发、旋转、撤销、family 处理、cookie 规格。

## 数据流

认证域的数据流分成四条主线：

1. 注册：`AuthController` 接收请求后进入 `RegistrationApplicationService`，auth 先校验验证码、请求字段和邮件配置，再通过 `UserRegistrationActionApi.prepareRegistrationUser(...)` 让 user owner 检查用户名/邮箱冲突并准备用户名、邮箱、密码 hash 和默认头像。auth application 生成 registration token，draft 仓储只负责按 token 存储上下文；验证码通过后再调用 `createVerifiedRegistrationUser(...)` 插入 active 用户，并复用登录签发链路返回 access token 和 refresh cookie。
2. 登录：`LoginApplicationService.login(...)` 先经过登录风控和 captcha 判断，再用 `UserCredentialQueryApi.authenticate(...)` 回源 user owner 校验凭据。认证成功后签发 access token，写 refresh session，并异步/同步触发安全日志和 analytics 登录采集。
3. refresh / logout：浏览器只把 refresh token 放在 HttpOnly cookie 中。refresh 时 auth 先消费旧 token，再回源 user 校验用户仍 active，最后才签发同 family 的新 token；logout 撤销当前 token 或 family，并由 controller 写 clear cookie。
4. 密码重置：auth 校验邮箱、captcha、请求限流和 reset token，user owner 校验新密码策略并更新 BCrypt hash；成功后撤销该用户 refresh sessions，避免旧会话继续刷新。

auth 不直接写 user 表或 refresh session 表；这些状态变化都通过 user owner API 进入 user application。

## 注册流程

当前注册采用 Verify-First 流程，核心目标是高并发下避免先创建大量未激活用户行。

1. `AuthController.register(...)` 解析请求和客户端 IP。
2. controller 组装 `RegisterCommand`，调用 `RegistrationApplicationService.register(...)`。
3. `RegistrationApplicationService.register(...)` 校验验证码、用户名、密码、邮箱和邮件配置。
4. auth 域通过 `UserRegistrationActionApi.prepareRegistrationUser(...)` 进入 user owner。
5. user owner 规范化用户名和邮箱，先检查用户名/邮箱是否已存在，再生成预备用户 ID、计算 BCrypt 密码、准备默认头像，但不插入 `user` row。
6. auth application 生成 256-bit base64url opaque `registrationToken`，把 `PreparedRegistrationDraft` 存入 draft store；token 冲突最多重试 5 次。
7. auth 域用安全随机生成器签发 6 位注册验证码并发送邮件。
8. `verifyRegisterCode(...)` 根据 `registrationToken` 找回 draft，消费验证码。
9. 验证通过后调用 `UserRegistrationActionApi.createVerifiedRegistrationUser(...)`，由 user owner 插入 active 用户。
10. 注册验证成功后复用登录签发能力，直接返回 access token 和 refresh cookie。
11. 注册验证成功后会 best-effort 删除 draft/code；失败不应让已创建用户回滚到未注册状态。

失败语义：

- 验证码错误或过期返回认证错误，不创建用户。
- 用户名或邮箱冲突由 user owner 判断；prepare 阶段做前置查重，最终插入仍依赖数据库唯一约束兜住并发竞态。
- 邮件发送失败时，注册流程不应伪造成功。
- active 用户创建成功但自动登录 token 签发失败时，返回 `REGISTRATION_ACTIVATED_LOGIN_REQUIRED`，前端应清理注册上下文并提示直接登录。
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

user owner 的密码校验只接受 BCrypt。

## Refresh 和 Logout

refresh 使用 refresh token rotation：

1. controller 从 refresh cookie 读 token。
2. `LoginApplicationService.refresh(...)` 调 `RefreshTokenApplicationService.consume(...)` 消费旧 token。
3. token hash 找不到、已撤销、过期或 family 被撤销都会失败。
4. 当前 token 被消费后，先通过 `UserCredentialQueryApi.getByUserId(...)` 校验用户仍存在且 active。
5. 用户不存在或被禁用时撤销该 family 并返回 `USER_DISABLED`，controller 清 cookie。
6. 用户校验通过后签发新的 access token 和同 family 的 256-bit base64url refresh token。
7. refresh 失败时 controller 视错误决定是否清 cookie。

logout：

1. controller 从 cookie 读 refresh token。
2. `LoginApplicationService.logout(...)` 撤销 token 或 token family。
3. controller 写 clear cookie。
4. access token 不会被服务端即时拉黑，依赖短 TTL 过期。

## Captcha 和登录风控

验证码由 `CaptchaApplicationService` 管理：

发放：

1. `issue(...)` 生成无短横线 UUID 作为 captchaId。
2. code 默认从 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` 生成 4 位随机码；如果配置了 fixedCode，则使用固定码。
3. TTL 使用 `captcha.ttlSeconds`，最小 1 秒。
4. captchaId/code 先写 `CaptchaRepository`，写入失败返回 `SERVICE_UNAVAILABLE`。
5. 图片为 120x40 PNG，白底、深色文字，并加 6 条噪声线。
6. 返回 `captchaId`、PNG base64 和 TTL。

校验：

1. 空 captchaId 或空 code 直接返回 `false`。
2. `CaptchaDomainService.normalizeCode(...)` 只做 trim；大小写规则由具体 repository 负责。
3. repository `verifyAndConsume(...)` 返回 `MATCHED` 时校验成功，并消费验证码。
4. `NOT_FOUND` 直接失败。
5. 其他失败会增加失败计数；失败计数 TTL 与验证码 TTL 一致。
6. 失败次数达到 `captcha.maxFailures` 后删除验证码，要求重新获取。
7. repository 读写异常返回验证码服务不可用。

`CaptchaDomainService.requireCaptcha(...)` 是同步规则：captchaId 或 code 缺失时抛 `CAPTCHA_REQUIRED`。当前登录主路径先在 application 层判断缺参，再调用验证码校验。

登录风控按 IP 和用户名分别计数：

- `LoginRateLimitDomainService.keyOf(...)` 把 username trim 后转小写，IP 只 trim。
- Redis key 前缀为 `auth:login:fail:ip:` 和 `auth:login:fail:user:`。
- `isCaptchaRequired(...)` 分别读取 IP 和用户名计数；阈值 `<=0` 表示只要有该维度 key 就要求验证码。
- `assertNotBlocked(...)` 在登录前按 `maxFailuresPerIp` / `maxFailuresPerUser` 判断是否封锁。
- `recordFailure(...)` 对 IP 和用户名分别 increment，TTL 是 `windowSeconds`；达到上限会抛 `TOO_MANY_REQUESTS`。
- `reset(...)` 在登录成功后删除当前 username/IP 的失败计数；reset 存储异常只记录日志，不影响登录成功。
- 风控存储异常按 fail-closed 处理：判断是否需要验证码时返回 true；封锁/失败计数异常返回 `SERVICE_UNAVAILABLE`。
- Micrometer 指标名为 `auth_login_rate_limit_total`，tag 包含 `outcome` 和规范化后的 `ip_source`。

基础凭据规则：

- `AuthDomainService.requireCredentials(...)` 要求 username 和 password 非空，否则统一抛 `INVALID_CREDENTIALS`，避免暴露是用户名还是密码缺失。
- `PasswordResetDomainService.requireResetRequestEmail(...)` 要求密码重置请求必须有 email。
- `PasswordResetDomainService.requireConfirmFields(...)` 要求 resetToken 和 newPassword 同时存在。
- `RegistrationDomainService.requireRegisterFields(...)` 要求注册 username、password、email 非空；密码字段不做静默 trim，首尾空白由 user owner 密码策略拒绝。
- `RegistrationDomainService.maskEmail(...)` 用于注册验证码响应：非法邮箱原样返回；单字符 local 部分显示 `*`；两字符 local 保留首字符；更长 local 保留首尾，中间变 `***`。

## 密码重置

密码重置由 `PasswordResetApplicationService` 处理：

1. 请求阶段必须提交邮箱和验证码。
2. 验证码通过后按邮箱/IP 自增请求限流计数，超过阈值返回 `TOO_MANY_REQUESTS`。
3. 邮箱不存在、用户不可用或未激活时，也返回已受理，避免用户枚举。
4. 符合条件时生成 256-bit base64url reset token，存入 token store，并发送邮件链接；如果邮件发送失败，会 best-effort 删除已写入 token。
5. HTTP 响应不返回 reset link。
6. 确认阶段再次校验验证码、reset token 和新密码策略。
7. user owner 更新密码；密码策略拒绝首尾空白字符，不静默修改用户输入。
8. 密码更新成功后撤销该用户 refresh sessions。
9. 如果密码更新失败，reset token 会按消费时捕获的剩余 TTL 恢复，允许用户在原有效期内重试且不延长完整有效期。

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
- `auth.application.LoginApplicationService`
- `auth.application.RegistrationApplicationService`
- `auth.application.RegistrationVerificationApplicationService`
- `auth.application.CaptchaApplicationService`
- `auth.application.PasswordResetApplicationService`
- `auth.application.RefreshTokenApplicationService`
- `auth.application.LoginRateLimitApplicationService`
- `auth.domain.service.*`
- `auth.infrastructure.jwt.JwtTokenService`
- `auth.infrastructure.job.RefreshTokenCleanupJob`
- `user.application.UserCredentialApplicationService`
- `user.application.RefreshTokenSessionApplicationService`
