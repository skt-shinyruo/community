# Change Proposal: Origin 白名单收敛到 gateway（修复前端端口漂移 403）

## Requirement Background
当前登录/刷新/登出链路同时依赖两层策略：
1) gateway 的 CORS allowlist（浏览器跨域可用性）
2) auth-service 的 Origin allowlist（服务端硬拒绝，返回 403）

当本地前端 dev 端口从 `12881` 调整为 `12888` 时，即使通过 Vite proxy 访问 `/api/*`（浏览器侧同源），请求仍会携带 `Origin: http://localhost:12888` 透传到 auth-service，导致 auth-service 以“Origin 不被允许”拒绝，从而产生 403。

该策略虽然安全（纵深防御），但在“auth-service 真·内网-only 且 gateway 是唯一入口”的前提下，会带来维护成本：每次前端 Origin 变化都需要改动多个服务配置，容易漂移。

## Change Content
1. 将 **Origin allowlist 规则收敛到 gateway**：在 gateway 增加 OriginGuard（仅覆盖敏感接口：`/api/auth/login|refresh|logout`）。
2. auth-service 移除 Origin allowlist 校验（不再因端口变化返回 403）。
3. gateway 的 CORS allowlist 同步允许 `http://localhost:12888`，并与 OriginGuard 使用同一份 allowlist（避免重复配置）。
4. 同步更新安全/部署与模块文档，明确新的安全边界与配置入口（SSOT）。

## Impact Scope
- **Modules:**
  - `gateway`
  - `auth-service`
  - docs / knowledge base
- **Files (planned):**
  - `gateway/src/main/resources/application.yml`
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/*`
  - `auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
  - `auth-service/src/main/java/com/nowcoder/community/auth/config/JwtProperties.java`
  - `auth-service/src/main/resources/application.yml`
  - `docs/SECURITY.md`
  - `helloagents/wiki/modules/gateway.md`
  - `helloagents/wiki/modules/frontend.md`

## Core Scenarios

### Requirement: 前端端口漂移不再触发登录 403
**Module:** gateway / auth-service

#### Scenario: 前端在 12888（Vite dev + proxy）登录
当浏览器从 `http://localhost:12888` 发起 `POST /api/auth/login`：
- 预期 gateway 允许该 Origin，并正常转发到 auth-service
- 预期 auth-service 不再因 Origin 白名单拦截而返回 403
- 登录成功返回 200，且下发 refresh cookie

### Requirement: 保留对敏感接口的 Origin 防护能力
**Module:** gateway

#### Scenario: 不在 allowlist 的 Origin 访问敏感接口
当浏览器携带非 allowlist 的 `Origin` 访问 `POST /api/auth/refresh|logout|login`：
- 预期 gateway 直接返回 403（JSON Result）
- 预期请求不会被转发到 auth-service

## Risk Assessment
- **Risk:** 将 Origin 校验从 auth-service 收敛到 gateway，若 gateway 配置/过滤器被误改，可能降低对 cookie 会话接口的 CSRF 防护能力。
- **Mitigation:**
  - OriginGuard 仅覆盖敏感接口，并默认启用
  - allowlist 保持严格（本地仅 `localhost`），并在文档中明确生产侧应显式配置
  - 要求 auth-service 网络层面保持内网-only（无对外端口暴露），确保 gateway 为唯一入口
  - 增加 gateway 单元测试覆盖 “允许/拒绝” 行为，避免回归

