# auth-service 模块

## 1. 职责（迭代 0）
- 提供登录/刷新/登出闭环：`/api/auth/login`、`/api/auth/refresh`、`/api/auth/logout`，以及联调用的 `GET /api/auth/me`。
- 签发 JWT access token（HS256），refresh token 默认使用 **HttpOnly Cookie** 并支持 **旋转刷新（rotation）**。
- 登录校验兼容 legacy 的 `MD5(password + salt)`（迁移期与 legacy 共用 `user` 表）。
- 提供验证码与账号安全防护：
  - `GET /api/auth/captcha`（captchaId + imageBase64）
  - `POST /api/auth/captcha/verify`（一次性 + TTL + 失败次数阈值作废）
  - 登录采用 **风险触发**：失败达到阈值后强制验证码（避免仅 UI 校验可绕过）
  - 注册/找回密码默认强制验证码
- 提供找回密码（迭代 0 增量）：
  - `POST /api/auth/password/reset/request`
  - `POST /api/auth/password/reset/confirm`
- 安全增强：
  - 支持按 **IP/账号维度** 的登录失败限流（防爆破）。
  - 支持按 `Origin` 白名单校验（防止被非预期站点发起跨站请求）。

## 2. 关键文件
- 启动类：`auth-service/src/main/java/com/nowcoder/community/auth/AuthServiceApplication.java`
- 安全配置：`auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java`
- JWT 签发：`auth-service/src/main/java/com/nowcoder/community/auth/config/JwtCryptoConfig.java`
- 业务入口：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`
- 核心服务：`auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
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
- MyBatis mapper：`auth-service/src/main/resources/mapper/user_mapper.xml`
- 配置：`auth-service/src/main/resources/application.yml`

## 3. refresh token Redis Key
- `auth:refresh:<refreshToken>` -> JSON（包含 `userId` / `familyId` / `expiresAt`，带 TTL）
- `auth:refresh:family:<familyId>` -> Set（family 下的 refreshToken 列表，带 TTL）

## 4. 安全与配置约定
- **JWT HMAC 密钥**：通过 `security.jwt.hmac-secret` 配置，建议 >= 32 字节，且需与 gateway 的验签密钥保持一致。
- **refresh cookie**：默认 `SameSite=Lax`、`Path=/api/auth`；生产环境建议启用 `Secure=true` 并按部署形态评估 `SameSite` 策略。
- **refresh token 存储**：通过 `auth.refresh.store=redis|memory` 切换（默认 memory，生产建议 redis）。
- **登出语义**：登出会按 refresh token 的 `familyId` 做“家族级”失效，避免同一会话族残留 token。
- **验证码（captchaId）**：
  - `GET /api/auth/captcha` 返回 `{ captchaId, imageBase64, ttlSeconds }`，验证码与失败计数在服务端存储（Redis/memory）。
  - 校验成功一次性失效；失败达到阈值作废并要求重新获取（默认失败 3 次）。
  - 本地/测试环境可配置 `auth.captcha.fixed-code=0000` 用于调试（生产环境不建议开启）。
- **找回密码**：
  - request 接口为避免用户枚举，邮箱不存在也返回“已处理”（不回传 token/不发邮件）。
  - resetToken 为一次性消费，建议短 TTL（默认 10 分钟）。

## 5. 测试策略（分层）

### 5.1 切片测试（mock）
- 目标：只验证 Controller 层的入参/出参/响应头等行为，不依赖 DB/Redis 等外部服务。
- 示例：`auth-service/src/test/java/com/nowcoder/community/auth/api/AuthControllerWebMvcTest.java`
  - `@WebMvcTest(AuthController.class)` + `@MockBean(AuthService/RegistrationService/...)`
  - 适合 CI 快速回归（无需 Docker）。

### 5.2 集成测试（Testcontainers）
- 目标：覆盖登录/注册/验证码/找回密码等关键链路，允许引入外部依赖（Redis/DB 等）。
- 示例：`auth-service/src/test/java/com/nowcoder/community/auth/api/AuthControllerTest.java`
  - 使用 Testcontainers 启动 Redis，并通过 `@DynamicPropertySource` 注入 `spring.data.redis.host/port`。

### 5.3 常用命令
- 全量跑 `auth-service` 测试（推荐，确保依赖模块一起构建）：`mvn -pl auth-service -am test`
- 全仓测试（CI backend-test 一致）：`mvn test`
