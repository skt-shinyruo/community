# Cookie 与 CSRF 策略（refresh token / OriginGuard / SameSite）

## 背景
本项目采用：
- **access token**：JWT（Authorization: Bearer ...），前端发起请求时携带
- **refresh token**：仅存放于 **HttpOnly cookie**（避免被 JS 读取），用于刷新 access token

refresh token 属于高价值凭证，一旦被 CSRF 或旁路利用，会导致会话被劫持或被动登录状态被“滥用刷新”。

## 目标
1. 浏览器侧默认安全：refresh cookie 具备合理的 `SameSite`/`Secure`/`HttpOnly`
2. 网关与服务侧都具备“旁路不降级”：绕过 gateway 直连 auth-service 时仍能阻断非可信 Origin
3. 配置可演进：支持同源部署与跨站部署两种形态，并将策略写入 SSOT

## 核心策略（推荐）

### 1) 同源部署（推荐）
典型形态：前端与 gateway 同域名（或同站点），例如：
- `https://community.example.com`（前端）
- `https://community.example.com/api/**`（gateway）

推荐配置：
- refresh cookie：`SameSite=Lax`（或 `Strict`，视业务跳转而定）
- refresh cookie：`Secure=true`（生产必须）
- auth-service / gateway：启用 OriginGuard，但 **同源请求始终放行**

收益：
- 对大多数浏览器默认防御较强
- 允许普通站内跳转，不需要额外 CSRF token

### 1.1) 反向代理 / HTTPS offload 注意事项
若 gateway 处于反向代理/Ingress/HTTPS offload 后（外部是 `https`，gateway 内部可能感知为 `http`）：
- 建议开启可信代理：`gateway.trusted-proxy.enabled=true` + `gateway.trusted-proxy.cidrs`（仅信任反向代理 IP/CIDR）
- 确保代理正确回填并覆盖 `Forwarded` / `X-Forwarded-Proto/Host/Port`（避免客户端伪造）
- 目的：让 OriginGuard 的“同源请求放行”在 login/refresh/logout 上能正确识别公网的 scheme/host/port，避免误拦

### 2) 跨站部署（谨慎）
典型形态：前端与 gateway 不同 Origin，例如：
- `https://app.example.com`（前端）
- `https://api.example.com`（gateway）

要点：
- refresh cookie 必须：`SameSite=None` 且 **必须** `Secure=true`
- gateway / auth-service 必须启用 OriginGuard allowlist（精确匹配），并在 prod 下 **allowlist 为空时 fail-closed**
- CORS 必须：`allowCredentials=true` 且 `allowedOrigins` 精确匹配（禁止 `*`）

风险提示：
- 跨站 cookie 模式天然更容易引入 CSRF/旁路风险，请优先考虑同源部署

## 运维检查清单
- [ ] refresh cookie：`HttpOnly=true`
- [ ] refresh cookie：`Secure=true`（prod）
- [ ] refresh cookie：`SameSite` 与部署形态一致（同源：Lax/Strict；跨站：None）
- [ ] gateway OriginGuard：对 `/api/auth/login|refresh|logout` 生效，allowlist 与前端 Origin 一致
- [ ] auth-service OriginGuard：同样覆盖 login/refresh/logout，避免旁路
- [ ] 外部网络只暴露 gateway（旁路禁止）
