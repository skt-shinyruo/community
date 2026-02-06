# auth-service 模块

## 1. 职责（迭代 0）
- 提供登录/刷新/登出闭环：`/api/auth/login`、`/api/auth/refresh`、`/api/auth/logout`，以及联调用的 `GET /api/auth/me`。
- 签发 JWT access token（HS256），refresh token 默认使用 **HttpOnly Cookie** 并支持 **旋转刷新（rotation）**。
- 身份域数据所有权收敛到 `user-service`：用户名密码校验、用户状态/角色查询、密码渐进升级（legacy→BCrypt）均通过 `user-service` 的 `/internal/users/**` 完成；`auth-service` **不再直连 MySQL**。
- 提供验证码与账号安全防护：
  - `GET /api/auth/captcha`（captchaId + imageBase64）
  - `POST /api/auth/captcha/verify`（一次性 + TTL + 失败次数阈值作废）
  - 登录采用 **风险触发**：失败达到阈值后强制验证码（避免仅 UI 校验可绕过）
  - 注册/找回密码默认强制验证码
- 提供找回密码（迭代 0 增量）：
  - `POST /api/auth/password/reset/request`
  - `POST /api/auth/password/reset/confirm`
  - dev/联调环境可选择在响应中回传 `resetLink`（形成闭环）；prod 默认禁止回传（安全默认态）。
- 安全增强：
  - 支持按 **IP/账号维度** 的登录失败限流（防爆破）。
- 同站不同源（HTTP）场景下的 Origin 白名单校验由 **gateway OriginGuard** 统一负责（login/refresh/logout），避免多点配置漂移。

## 2. 关键文件
- 启动类：`auth-service/src/main/java/com/nowcoder/community/auth/AuthServiceApplication.java`
- 安全配置：`auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java`
- JWT 签发：`auth-service/src/main/java/com/nowcoder/community/auth/config/JwtCryptoConfig.java`
- 业务入口：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`
- 核心服务：`auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- user-service internal client（身份域 SSOT）：
  - 配置：`auth-service/src/main/java/com/nowcoder/community/auth/config/UserServiceClientProperties.java`
  - RestTemplate：`auth-service/src/main/java/com/nowcoder/community/auth/config/AuthRestClientConfig.java`
  - 客户端：`auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java`
  - 统一支撑：复用 `common` 的 `InternalClientSupport`（headers/错误语义保真/metrics 统一）
  - DTO：`auth-service/src/main/java/com/nowcoder/community/auth/service/dto/*`
- 验证码：
  - 配置：`auth-service/src/main/java/com/nowcoder/community/auth/config/CaptchaProperties.java`
  - 逻辑：`auth-service/src/main/java/com/nowcoder/community/auth/service/CaptchaService.java`
  - 存储接口：`auth-service/src/main/java/com/nowcoder/community/auth/service/CaptchaStore.java`
  - 内存实现：`auth-service/src/main/java/com/nowcoder/community/auth/service/InMemoryCaptchaStore.java`
  - Redis 实现：`auth-service/src/main/java/com/nowcoder/community/auth/service/RedisCaptchaStore.java`
- 找回密码：
  - 配置：`auth-service/src/main/java/com/nowcoder/community/auth/config/PasswordResetProperties.java`
  - 逻辑：`auth-service/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java`
  - token 存储接口：`auth-service/src/main/java/com/nowcoder/community/auth/service/PasswordResetTokenStore.java`
  - 内存实现：`auth-service/src/main/java/com/nowcoder/community/auth/service/InMemoryPasswordResetTokenStore.java`
  - Redis 实现：`auth-service/src/main/java/com/nowcoder/community/auth/service/RedisPasswordResetTokenStore.java`
- refresh token：
  - 业务：`auth-service/src/main/java/com/nowcoder/community/auth/service/RefreshTokenService.java`
  - 存储接口：`auth-service/src/main/java/com/nowcoder/community/auth/service/RefreshTokenStore.java`
  - 内存实现（默认/测试用）：`auth-service/src/main/java/com/nowcoder/community/auth/service/InMemoryRefreshTokenStore.java`
  - Redis 实现（生产建议）：`auth-service/src/main/java/com/nowcoder/community/auth/service/RedisRefreshTokenStore.java`
- 登录限流：
  - 配置：`auth-service/src/main/java/com/nowcoder/community/auth/config/LoginRateLimitProperties.java`
  - 逻辑：`auth-service/src/main/java/com/nowcoder/community/auth/service/LoginRateLimitService.java`
- 配置：`auth-service/src/main/resources/application.yml`

## 3. refresh token Redis Key
- `auth:refresh:<refreshToken>` -> JSON（包含 `userId` / `familyId` / `expiresAt`，带 TTL）
- `auth:refresh:family:<familyId>` -> Set（family 下的 refreshToken 列表，带 TTL）

## 4. 安全与配置约定
- **JWT HMAC 密钥**：通过 `security.jwt.hmac-secret` 配置，建议 >= 32 字节，且需与 gateway 的验签密钥保持一致。
- **refresh cookie**：默认 `SameSite=Lax`、`Path=/api/auth`；生产环境建议启用 `Secure=true` 并按部署形态评估 `SameSite` 策略。
- **refresh token 存储**：通过 `auth.refresh.store=redis|memory` 切换（默认 memory，生产建议 redis）。
- **登出语义**：登出会按 refresh token 的 `familyId` 做“家族级”失效，避免同一会话族残留 token。
- **user-service internal 调用**：auth-service 会调用 user-service 的 `/internal/**` 完成登录/刷新/注册等链路（开发阶段 internal 接口默认放行；生产建议通过网络隔离/网关策略收敛暴露面）。
- **验证码（captchaId）**：
  - `GET /api/auth/captcha` 返回 `{ captchaId, imageBase64, ttlSeconds }`，验证码与失败计数在服务端存储（Redis/memory）。
  - 校验成功一次性失效；失败达到阈值作废并要求重新获取（默认失败 3 次）。
  - 本地/测试环境可配置 `auth.captcha.fixed-code=0000` 用于调试（生产环境禁止开启；prod 下 `StartupValidation` 会 fail-closed 阻断误配）。
- **找回密码**：
  - request 接口为避免用户枚举，邮箱不存在也返回“已处理”（不回传 token/不发邮件）。
  - resetToken 为一次性消费，建议短 TTL（默认 10 分钟）。

### 4.1 Onboarding（注册/激活/找回密码闭环）
- **激活链接 base URL**：`auth.registration.activation-base-url`（env：`AUTH_ACTIVATION_BASE_URL`）
  - 用途：生成注册激活链接 `/api/auth/activation/{userId}/{code}` 与找回密码重置链接 `/#/auth/password/reset?token=...`
- **dev/联调：回传链接（可选，提升开箱即用）**
  - `auth.registration.expose-activation-link=true`（env：`AUTH_EXPOSE_ACTIVATION_LINK=true`）
  - `auth.password-reset.expose-reset-link=true`（env：`AUTH_EXPOSE_RESET_LINK=true`）
  - 默认配置见：`auth-service/src/main/resources/application-dev.yml`
- **prod：默认安全态（必须）**
  - `auth.registration.expose-activation-link=false`
  - `auth.password-reset.expose-reset-link=false`
  - `auth.registration.mail.enabled=true` + `spring.mail.*` 配置可用
  - `common` 的 `StartupValidation` 会在 prod profile 下强制校验上述项（不满足则 fail-closed 阻断启动）。

## 5. 测试策略（分层）

### 5.1 切片测试（mock）
- 目标：只验证 Controller 层的入参/出参/响应头等行为，不依赖 DB/Redis 等外部服务。
- 建议：新增 `@WebMvcTest(AuthController.class)` 覆盖 login/refresh/logout/register/password-reset 等关键接口。

### 5.2 集成测试（Testcontainers）
- 目标：覆盖登录/注册/验证码/找回密码等关键链路，允许引入外部依赖（Redis/DB 等）。
- 建议：使用 Testcontainers 启动 Redis，并以 mock/stub 方式替代 `user-service internal`（或在 compose 环境跑端到端 smoke）。

### 5.3 常用命令
- 全量跑 `auth-service` 测试（推荐，确保依赖模块一起构建）：`mvn -pl auth-service -am test`
- 全仓测试（CI backend-test 一致）：`mvn test`
