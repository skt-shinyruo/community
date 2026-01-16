# auth-service 模块

## 1. 职责（迭代 0）
- 提供登录/刷新/登出闭环：`/api/auth/login`、`/api/auth/refresh`、`/api/auth/logout`。
- 签发 JWT access token（HS256），refresh token 使用 HttpOnly Cookie 并在 Redis 中做旋转刷新（rotation）。

## 2. 关键文件
- 启动类：`auth-service/src/main/java/com/nowcoder/community/auth/AuthServiceApplication.java`
- 安全配置：`auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java`
- 业务入口：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`
- 核心服务：`auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- refresh token 存储：`auth-service/src/main/java/com/nowcoder/community/auth/service/RefreshTokenService.java`
- MyBatis mapper：`auth-service/src/main/resources/mapper/user_mapper.xml`
- 配置：`auth-service/src/main/resources/application.yml`

## 3. refresh token Redis Key
- `auth:refresh:token:<refreshToken>` -> `<userId>`
- `auth:refresh:user:<userId>` -> `<refreshToken>`

## 4. 密码兼容策略
- 兼容 legacy 的 `MD5(password + salt)` 校验。
- 可选开关：`auth.password.rehash-md5-to-bcrypt=true` 支持登录成功后渐进升级为 BCrypt（仅更新 password 字段）。

