# Auth 核心类细分

本文是 [../auth.md](../auth.md) 的类级补充。先读域级流程，再用这里定位源码。

## 先读顺序

1. `AuthApplicationService`
2. `LoginApplicationService`
3. `RegistrationApplicationService` / `RegistrationVerificationApplicationService`
4. `RefreshTokenApplicationService`
5. `PasswordResetApplicationService`
6. `CaptchaApplicationService` / `LoginRateLimitApplicationService`

## 入口适配器

| 类 | 层 | 角色 |
| --- | --- | --- |
| `auth.controller.AuthController` | controller | `/api/auth/**` 的 HTTP 绑定，只做请求解包、响应装配和调用应用层。 |

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `auth.application.AuthApplicationService` | auth 域总入口，把 me / session / 登录 / 注册 / 重置等流程收束到一致的应用边界。 | 先看它如何把 controller 级请求路由到更细的 use case。 |
| `auth.application.LoginApplicationService` | 登录主路径，串起风控、验证码、凭据校验、JWT 签发、refresh token 写入和安全/分析副作用。 | 看它如何 fail-closed，以及成功后如何 reset 风控状态。 |
| `auth.application.RefreshTokenApplicationService` | refresh、logout、token family 旋转和 reuse detection。 | 看 family 级撤销如何传播到当前 token 和整个 family。 |
| `auth.application.RegistrationApplicationService` | Verify-First 注册起点，先准备 draft 和验证码，再等待验证。 | 看它如何先回源 user 准备注册材料，再落 auth 自己的临时态。 |
| `auth.application.RegistrationVerificationApplicationService` | 重新发送验证码、消费验证码、验证成功后创建 active user。 | 看它如何把注册 draft 转成真正用户，并尽量清理临时态。 |
| `auth.application.PasswordResetApplicationService` | 找回密码 token、邮件、密码更新和 session 撤销。 | 看它如何把 reset token 和用户 session 绑定到同一条恢复链路。 |
| `auth.application.CaptchaApplicationService` | 验证码发放和校验。 | 看验证码 TTL、失败计数和删除策略。 |
| `auth.application.LoginRateLimitApplicationService` | 登录失败计数、IP / 用户维度限流和验证码触发。 | 看它如何在异常时偏向保护系统而不是放行。 |

## 领域服务

| 类 | 核心规则 |
| --- | --- |
| `auth.domain.service.AuthDomainService` | 凭据字段最小校验，避免暴露到底是用户名还是密码缺失。 |
| `auth.domain.service.CaptchaDomainService` | captchaId/code 必填、验证码归一化。 |
| `auth.domain.service.LoginRateLimitDomainService` | 风控 key 归一化、封锁判断和验证码触发规则。 |
| `auth.domain.service.PasswordResetDomainService` | reset token、email 和新密码的约束。 |
| `auth.domain.service.RefreshTokenDomainService` | refresh token family、旋转和复用检测规则。 |
| `auth.domain.service.RegistrationDomainService` | Verify-First 注册字段校验、邮箱遮罩、注册草稿规则。 |

## 基础设施

| 类 | 核心职责 |
| --- | --- |
| `auth.infrastructure.jwt.JwtTokenService` | HS256 access token 签发和 claims 组装。 |
| `auth.infrastructure.web.AuthOriginGuardFilter` | unsafe HTTP method 的 OriginGuard。 |
| `auth.infrastructure.job.RefreshTokenCleanupJob` | 定时清理过期 refresh session。 |

## 关键语义

- 登录是 fail-closed 的：风控、验证码、凭据、下游写失败都会优先保护系统。
- 注册是 Verify-First 的：先有 draft 和验证码，再有 active user。
- refresh token 不是单个 token 的状态，而是 family 级状态。
- 密码重置会影响 session 生命周期，不只是改一条密码 hash。

