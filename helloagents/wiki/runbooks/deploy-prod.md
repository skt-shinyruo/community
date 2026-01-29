# 生产部署（prod profile）Runbook

本 Runbook 用于把“生产默认安全态（fail-closed）”与“最终一致性治理”的关键前置条件固化为可执行步骤，避免因 profile/配置中心/密钥缺失导致的 silent fallback。

---

## 1. 核心原则（SSOT）

- **生产必须显式启用 `prod` profile**：`SPRING_PROFILES_ACTIVE=prod`
- **配置中心 fail-fast**：
  - required：`${spring.application.name}.yaml`
  - optional：`${spring.application.name}-prod.yaml`（仅用于 prod 专用覆盖）
- **启动期校验（fail-closed）**：prod 下 `StartupValidation` 会对关键配置做阻断校验（缺失即拒绝启动）
- **internal-token 分域**：不使用全局 `INTERNAL_TOKEN` 兜底；每个 internal segment 使用各自 token（降低爆炸半径）
- **Cookie 安全边界**：refresh cookie 在 prod 必须 `Secure=true`（需要 HTTPS）

---

## 2. 部署前检查清单（必须项）

### 2.1 Profile
- [ ] `SPRING_PROFILES_ACTIVE=prod`

### 2.2 Nacos Data ID（配置中心）
- [ ] `gateway.yaml`（required）
- [ ] `auth-service.yaml`（required）
- [ ] `user-service.yaml`（required）
- [ ] `content-service.yaml`（required）
- [ ] `social-service.yaml`（required）
- [ ] `search-service.yaml`（required）
- [ ] `message-service.yaml`（required）
- [ ] `analytics-service.yaml`（required）
- [ ] （可选）以上各服务的 `*-prod.yaml`（仅用于 prod 专用覆盖）

### 2.3 JWT
- [ ] `JWT_HMAC_SECRET`（>= 32 bytes）

### 2.4 internal-token（分域）
- [ ] `USER_INTERNAL_TOKEN`
- [ ] `CONTENT_INTERNAL_TOKEN`
- [ ] `SOCIAL_INTERNAL_TOKEN`
- [ ] `SEARCH_INTERNAL_TOKEN`
- [ ] `ANALYTICS_INTERNAL_TOKEN`
- [ ] （轮转窗口可选）`*_INTERNAL_TOKEN_PREVIOUS`

### 2.5 HTTPS / Cookie
- [ ] prod 环境的用户访问链路必须为 HTTPS（CDN/Ingress/TLS 终止均可）
- [ ] `AUTH_REFRESH_COOKIE_SECURE=true`
- [ ] `AUTH_REFRESH_COOKIE_SAME_SITE` 与部署形态匹配（Lax/Strict/None）

---

## 3. 启动与验证

1) 部署/启动服务（prod profile）。
2) 若启动失败，请优先查看日志中的 `[startup-validation]` 段落：
   - 会输出 `applicationName`、`activeProfiles`、以及缺失项清单（含修复指引）
3) 基础验证：
   - `GET /actuator/health`（gateway 与各服务）
   - 登录/刷新流程（确认 refresh cookie 正常写入且 Secure=true）
   - internal API（确认 `/internal/**` 必须携带正确的 `X-Internal-Token`）

---

## 4. 常见问题排查

### 4.1 Nacos 不可用/配置缺失导致启动失败
- 现象：prod profile 下启动立即失败
- 处置：恢复 Nacos 或补齐 `${spring.application.name}.yaml`（required）

### 4.2 refresh cookie 不生效
- 现象：refresh 401 / cookie 不落盘
- 排查：
  - 是否 HTTPS（Secure cookie 在 HTTP 下不会写入）
  - 是否误将 `AUTH_REFRESH_COOKIE_SECURE=false` 注入到 prod
  - SameSite 是否与跨站部署匹配（None 需要 Secure=true）

### 4.3 internal-token 校验失败（403）
- 现象：访问 `/internal/**` 返回 403
- 处置：
  - 确认对应 segment token 已配置（例如 `/internal/search/**` 对应 `SEARCH_INTERNAL_TOKEN`）
  - 若处于轮转窗口，确认调用方与被调方 token 的 current/previous 对齐

---

## 5. 回滚策略（建议）

- 优先回滚到上一版镜像（保持配置不变）
- 若问题来自配置变更，回滚 Nacos 配置到上一版本
- internal-token 轮转失败时：
  - 先把旧 token 放入 `*_INTERNAL_TOKEN_PREVIOUS`
  - 确认调用方全部升级后再切换 current

