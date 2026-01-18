# Technical Design: 验证码最佳实践（captchaId + 风险触发强制）

## Technical Solution

### Core Technologies
- Spring Boot 3（auth-service）
- Redis（验证码与重置 token 短期存储）
- Vue 3（frontend 交互适配）

### Implementation Key Points
1. **captchaId 替代 Cookie 绑定**
   - `GET /api/auth/captcha` 返回 `Result<{captchaId, imageBase64, ttlSeconds}>`
   - `captchaId` 使用高随机 ID（UUID 去掉短横线）
   - 服务端在 Redis 写入验证码内容与元信息（TTL=60s）
2. **验证码一次性 + 失败次数限制**
   - 成功校验：立即删除验证码记录
   - 失败校验：累加失败次数；失败达到 3 次后删除验证码记录
3. **后端强制校验（避免仅 UI 校验可绕过）**
   - 登录：风险触发阈值达到后强制验证码
   - 注册：默认强制验证码
   - 找回密码：请求/确认均强制验证码
4. **阈值策略（默认值，可配置）**
   - 登录：`failuresPerUser >= 2` 或 `failuresPerIp >= 5` 时强制验证码
   - 注册/找回：默认强制（阈值视为 0）

## Architecture Design
无需引入新基础设施；在 auth-service 内新增“验证码门禁（captcha gate）”与“找回密码 token”服务即可。

## API Design

### [GET] /api/auth/captcha
- **Response (Result.data)：**
  - `{ captchaId: string, imageBase64: string, ttlSeconds: number }`
- **Headers：** `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`

### [POST] /api/auth/captcha/verify
- **Request Body：** `{ captchaId: string, code: string }`
- **Response.data：** `true/false`

### [POST] /api/auth/login
- **Request Body：**
  - `{ username: string, password: string, captchaId?: string, captchaCode?: string }`
- **Error Codes：**
  - `CAPTCHA_REQUIRED`：达到风险阈值但未提供验证码
  - `CAPTCHA_INVALID`：验证码错误/失效/失败次数超限

### [POST] /api/auth/register
- **Request Body：**
  - `{ username: string, password: string, email: string, captchaId: string, captchaCode: string }`
- **Error Codes：**
  - `CAPTCHA_REQUIRED` / `CAPTCHA_INVALID`

### [POST] /api/auth/password/reset/request
- **Request Body：**
  - `{ email: string, captchaId: string, captchaCode: string }`
- **Response.data：** `{ issued: boolean, resetLink?: string }`（`resetLink` 仅本地/测试可回传）
- **Notes：** 统一返回成功提示，避免用户枚举（邮箱不存在也返回 issued=true 但不发送邮件/不回传 link）

### [POST] /api/auth/password/reset/confirm
- **Request Body：**
  - `{ resetToken: string, newPassword: string, captchaId: string, captchaCode: string }`
- **Response.data：** `true/false`

## Data Model
Redis keys（示例）：
- `captcha:{captchaId}` → code（TTL=60s）
- `captcha:fail:{captchaId}` → failCount（TTL 同步）
- `pwdreset:{token}` → userId（TTL 建议 10 分钟，可配置）

## Security and Performance
- **Security：**
  - 验证码：TTL=60s；成功一次性失效；失败 3 次作废
  - 登录：风险触发强制验证码，降低爆破效率；仍保留 IP/账号维度限流（现有 LoginRateLimit + Gateway RateLimit）
  - 找回密码：请求接口不暴露邮箱是否存在；reset token 单次使用/短 TTL；重置时必须携带验证码
  - 不再依赖验证码 Cookie，降低跨域/SameSite 配置复杂度
- **Performance：**
  - Redis 读写为 O(1)；验证码图片生成仅在需要时触发
  - base64 图片大小可接受（默认 120x40 PNG），可按需压缩/调尺寸

## Testing and Deployment
- **Testing：**
  - auth-service：验证码下发/校验/失败次数、登录风险触发、注册强制、找回密码接口（含枚举防护）单测
  - frontend：登录/注册在验证码 required/invalid 分支下交互正确
- **Deployment：**
  - Nacos 配置补充 `auth.captcha.*` 与 `auth.password-reset.*`（如引入新配置项）
  - gateway 放行/限流规则补齐找回密码相关接口
