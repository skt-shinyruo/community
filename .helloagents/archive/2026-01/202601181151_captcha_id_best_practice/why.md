# Change Proposal: 验证码最佳实践（captchaId + 风险触发强制）

## Requirement Background
当前项目验证码链路存在以下问题，导致与业界主流最佳实践不一致：

1. **验证码绑定依赖 Cookie（kaptchaOwner）**：对跨域/多域部署不友好，也容易引发前端对 HttpOnly/CORS 的误解。
2. **验证码不在敏感接口内强制校验**：前端先调用 `/api/auth/captcha/verify` 再调用 `/api/auth/login` 的方式可被绕过（直接调登录接口即可）。
3. **缺少“风险触发”策略闭环**：验证码应在登录失败达到阈值后强制触发，以降低正常用户摩擦，同时提升防爆破能力。
4. **缺少“失败次数限制 + 作废”机制**：同一验证码应限制失败次数（本需求：失败 3 次作废）并在成功后一次性失效，降低重放/撞库脚本效率。

## Change Content
1. 将验证码下发改造为 **captchaId 绑定（无 Cookie）**：`GET /api/auth/captcha` 返回 JSON（`captchaId` + `imageBase64`），并设置 `Cache-Control: no-store`。
2. 将验证码校验改造为 `captchaId + code`：`POST /api/auth/captcha/verify` 支持一次性校验，并加入**失败次数=3 作废**与 TTL=60s。
3. 在后端敏感接口中落地“风险触发强制”：
   - 登录：失败达到阈值后，`/api/auth/login` 必须携带并校验验证码（否则返回明确错误码）。
   - 注册：默认强制验证码（降低批量注册风险）。
   - 找回密码：请求重置与重置密码均强制验证码（降低撞库与邮件轰炸风险）。
4. 前端适配：登录/注册页面按后端返回的错误码/提示自动展示验证码并刷新，避免“仅 UI 校验可绕过”。
5. 同步更新：API 文档、模块文档与单元测试。

## Impact Scope
- **Modules：**
  - auth-service（验证码下发/校验、登录/注册/找回密码强制）
  - gateway（放行与限流规则补齐）
  - frontend（登录/注册验证码交互、API 协议适配）
  - .helloagents/wiki（API 与模块说明同步）
- **APIs：**
  - `GET /api/auth/captcha`（PNG → JSON）
  - `POST /api/auth/captcha/verify`（新增 captchaId）
  - `POST /api/auth/login`（请求体新增 captcha 字段；风险触发强制）
  - `POST /api/auth/register`（请求体新增 captcha 字段；默认强制）
  - `POST /api/auth/password/reset/request`（新增；强制验证码）
  - `POST /api/auth/password/reset/confirm`（新增；强制验证码）
- **Data：**
  - Redis：新增/调整 key（captcha、captcha failures、password reset token）

## Core Scenarios

### Requirement: 验证码下发（Captcha Issue, captchaId）
**Module:** auth-service

#### Scenario: 获取验证码（captchaId + imageBase64）
用户打开登录/注册/找回密码页面，需要获取验证码图片。
- 返回 `captchaId` 与 `imageBase64`（PNG base64），且响应禁止缓存
- 验证码 TTL=60s（到期自动失效）

### Requirement: 验证码校验（一次性 + 失败 3 次作废）
**Module:** auth-service

#### Scenario: 校验成功/失败/过期
- 校验成功：验证码立即失效（不可重放）
- 校验失败：失败次数累计；同一 `captchaId` 失败达到 3 次后强制作废，必须重新获取
- 过期：返回“验证码已失效/请刷新”的明确提示与错误码

### Requirement: 登录风险触发验证码强制（Risk-based）
**Module:** auth-service

#### Scenario: 失败达到阈值后必须带验证码
- 在登录失败次数未达到阈值前：允许不带验证码登录（降低正常用户摩擦）
- 达到阈值后：若请求未携带验证码 → 返回“需要验证码”的错误码；若验证码错误 → 返回“验证码错误”错误码并要求刷新
- 验证码正确且密码正确：登录成功并重置失败计数

### Requirement: 注册接口验证码强制
**Module:** auth-service

#### Scenario: 注册必须通过验证码
- 注册请求必须携带验证码并通过校验（防批量注册）
- 验证码错误/缺失：返回明确错误码与提示

### Requirement: 找回密码验证码强制（含重置流程）
**Module:** auth-service

#### Scenario: 请求重置与重置密码都要求验证码
- 请求重置：必须携带验证码，避免邮件轰炸与撞库
- 重置确认：必须携带验证码与 resetToken，避免 token 重放与脚本撞库

## Risk Assessment
- **Risk：API 变更导致旧客户端不兼容（captcha PNG → JSON）**
  - **Mitigation：** 同步更新 frontend；在文档中明确新协议；如需兼容可保留旧 PNG endpoint（可选）
- **Risk：阈值不合适导致用户体验下降或防护不足**
  - **Mitigation：** 阈值与强制策略全部配置化（dev/prod 可差异化），并保留观测点（traceId + 日志）
- **Risk：找回密码链路存在用户枚举风险**
  - **Mitigation：** 请求重置接口不暴露“邮箱是否存在”，统一返回成功提示；内部按需发送邮件
