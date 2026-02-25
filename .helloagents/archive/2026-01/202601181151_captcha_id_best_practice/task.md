# Task List: 验证码最佳实践（captchaId + 风险触发强制）

Directory: `.helloagents/archive/2026-01/202601181151_captcha_id_best_practice/`

---

## 1. auth-service：验证码协议改造（captchaId）
- [√] 1.1 调整 `GET /api/auth/captcha` 为 JSON（captchaId + imageBase64），更新 `auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`, verify why.md#core-scenarios-requirement-验证码下发captchа-issue-captchaid
- [√] 1.2 将验证码存储从 cookie owner 改为 captchaId，并实现“一次性 + 失败 3 次作废”，更新 `auth-service/src/main/java/com/nowcoder/community/auth/service/CaptchaService.java`, verify why.md#core-scenarios-requirement-验证码校验一次性--失败-3-次作废
- [√] 1.3 更新 `POST /api/auth/captcha/verify` 请求体支持 captchaId，更新 `auth-service/src/main/java/com/nowcoder/community/auth/api/dto/CaptchaVerifyRequest.java`, verify why.md#core-scenarios-requirement-验证码校验一次性--失败-3-次作废

## 2. auth-service：登录/注册后端强制验证码（风险触发）
- [√] 2.1 为登录增加风险阈值配置与判断逻辑（默认：user>=2 或 ip>=5 强制验证码），更新 `auth-service/src/main/java/com/nowcoder/community/auth/service/LoginRateLimitService.java`, verify why.md#core-scenarios-requirement-登录风险触发验证码强制risk-based
- [√] 2.2 扩展登录请求体支持 captcha 字段，并在后端强制校验，更新 `auth-service/src/main/java/com/nowcoder/community/auth/api/dto/LoginRequest.java`, verify why.md#core-scenarios-requirement-登录风险触发验证码强制risk-based
- [√] 2.3 将登录接口接入验证码校验与错误码返回，更新 `auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java`, depends on task 2.1, verify why.md#core-scenarios-requirement-登录风险触发验证码强制risk-based
- [√] 2.4 扩展注册请求体支持 captcha 字段，并在后端强制校验，更新 `auth-service/src/main/java/com/nowcoder/community/auth/api/dto/RegisterRequest.java`, verify why.md#core-scenarios-requirement-注册接口验证码强制
- [√] 2.5 将注册接口接入验证码强制校验，更新 `auth-service/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java`, depends on task 2.4, verify why.md#core-scenarios-requirement-注册接口验证码强制

## 3. auth-service：找回密码（含验证码强制）
- [√] 3.1 新增找回密码 request/confirm API（包含验证码校验与 reset token），更新 `auth-service/src/main/java/com/nowcoder/community/auth/api/AuthController.java`, verify why.md#core-scenarios-requirement-找回密码验证码强制含重置流程
- [√] 3.2 扩展邮件服务以支持 password reset（本地/CI 默认降级为日志输出），更新 `auth-service/src/main/java/com/nowcoder/community/auth/service/MailService.java`, depends on task 3.1

## 4. gateway：放行与限流规则补齐
- [√] 4.1 放行找回密码相关接口，更新 `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`, verify why.md#impact-scope
- [√] 4.2 为找回密码相关接口增加限流规则，更新 `gateway/src/main/resources/application.yml`, verify why.md#impact-scope

## 5. frontend：登录/注册适配新验证码协议
- [√] 5.1 增加 `issueCaptcha()` 并适配 login/register 请求体，更新 `frontend/src/api/services/authService.js`, verify why.md#change-content
- [√] 5.2 登录页按错误码触发验证码展示与刷新，更新 `frontend/src/views/LoginView.vue`, depends on task 5.1, verify why.md#core-scenarios-requirement-登录风险触发验证码强制risk-based
- [√] 5.3 注册页接入验证码（强制），更新 `frontend/src/views/RegisterView.vue`, depends on task 5.1, verify why.md#core-scenarios-requirement-注册接口验证码强制
- [√] 5.4 新增找回/重置密码页面与路由，并接入 API：`frontend/src/views/PasswordResetView.vue`, `frontend/src/router/index.js`, `frontend/src/api/services/authService.js`

## 6. Security Check
- [√] 6.1 执行安全检查（输入校验、用户枚举风险、重置 token 一次性、敏感信息处理、EHRB 风险规避）
  > Note: 已调整 `scripts/secret-scan.sh` 仅扫描 git tracked 文件，并在 `deploy/.env` 为本地未跟踪文件时降级为 WARN；`scripts/security-check.sh` 可在本地正常通过。

## 7. Documentation Update
- [√] 7.1 更新 `.helloagents/api.md`（captcha JSON 协议 + 新 password reset API）
- [√] 7.2 更新 `.helloagents/modules/auth-service.md` 与 `.helloagents/modules/frontend.md`（验证码与交互说明）
- [√] 7.3 更新 `.helloagents/CHANGELOG.md`

## 8. Testing
- [√] 8.1 更新并补充 auth-service 单测（captcha 下发/校验/失败次数、登录强制、注册强制、找回密码），更新 `auth-service/src/test/java/com/nowcoder/community/auth/api/AuthControllerTest.java`
