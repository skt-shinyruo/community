# Security 6 + JWT 策略细化（迭代 0）

Directory: `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/`

> 目标：给迭代 0 的 `gateway + auth-service + Vue3` 登录闭环提供明确可实现的“接口 + token 策略 + 权限矩阵”。

---

## 1. Token 策略（推荐基线）

### 1.1 Token 类型与过期策略
- **Access Token（JWT）：** 15 分钟（建议 10~30 分钟内）
- **Refresh Token：** 7 天（或 14 天），采用“旋转刷新（rotation）”
- **旋转刷新规则：**
  - refresh 成功后签发新的 refresh token
  - 旧 refresh token 立即失效（防重放）
  - 支持“宽限期”可选（例如 30 秒）防并发刷新导致误伤（可先不做）

### 1.2 签名算法与密钥
- 推荐 **RS256（非对称）**：网关只需公钥即可验签（更利于多服务扩展）
- 若迭代 0 追求简化也可用 **HS256（对称）**，但密钥分发与轮换成本更高

### 1.3 存储位置（前后端分离安全性取舍）
推荐组合（更安全）：
- **Access Token：** 前端内存（Pinia state）或 SessionStorage（不推荐 LocalStorage，暴露面更大）
- **Refresh Token：** HttpOnly Cookie（`Secure` + `SameSite=Lax/Strict`），前端 JS 不可读取，仅用于调用 `/api/auth/refresh`

> 影响：refresh 使用 Cookie 时，要考虑 CSRF 防护（见 1.4）。

### 1.4 CSRF 与 CORS（refresh 走 Cookie 时必须说明）
当 refresh token 存在 Cookie：
- Gateway 必须：
  - 只允许受信任的 Origin（Vue3 域名/端口）
  - `Access-Control-Allow-Credentials: true`
  - 对 refresh 接口校验 `Origin/Referer`（至少其一）
- Refresh 接口建议额外要求一个自定义 header（例如 `X-Client-Type: web` 或 `X-CSRF-Token`）以提高攻击门槛

---

## 2. JWT Claim 约定（最小集）

建议 Claim：
- `sub`：用户 ID（字符串）
- `jti`：token 唯一 ID（uuid）
- `iat` / `exp`：签发与过期
- `iss`：签发者（例如 `community-auth`）
- `roles`：角色数组（`user/admin/moderator`）
- `traceId`（可选）：用于联调（生产不一定需要放进 token）

---

## 3. 权限模型与传递

### 3.1 authority 的来源
迭代 0 建议：登录时由 auth-service 查询用户角色并写入 JWT 的 `roles`。  
优点：网关与下游服务无需每次查库；缺点：角色变更需要刷新 token 才生效（可接受）。

后续增强：
- 增加 `tokenVersion`（或 `roleVersion`）字段：当角色变更时递增版本，网关校验版本是否一致（需要 Redis/DB 支撑）

### 3.2 Gateway 授权矩阵（迭代 0 最小集）

| API | 匿名 | user | moderator | admin |
|-----|------|------|-----------|-------|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/auth/refresh` | ✅（依赖 Cookie） | ✅ | ✅ | ✅ |
| `POST /api/auth/logout` | ❌ | ✅ | ✅ | ✅ |
| `GET /api/auth/me` | ❌ | ✅ | ✅ | ✅ |
| `GET /actuator/health`（各服务） | ✅ | ✅ | ✅ | ✅ |

> 后续迭代会补齐 posts/likes/follows 等授权矩阵，并同步到 `.helloagents/api.md`。

---

## 4. Auth API（迭代 0 约定）

### 4.1 登录
- `POST /api/auth/login`
- Request：`{ "username": "...", "password": "..." }`
- Response：统一 `Result`，data 包含 access token（refresh token 建议写 Cookie）

### 4.2 刷新
- `POST /api/auth/refresh`
- Request：空 body（refresh token 从 Cookie 取）
- Response：返回新的 access token；并更新 refresh cookie

### 4.3 登出
- `POST /api/auth/logout`
- 行为：失效 refresh token（Redis 黑名单或 token family）

---

## 5. 与 legacy-community 的关系（迁移期）

迭代 0 仅要求 legacy-community 在 Boot 3 下可编译/可启动；鉴权闭环以 gateway+auth-service 为准。  
legacy-community 的旧 ticket/cookie 认证可以暂时保留（迁移期），但不要继续扩展其能力，最终在迭代 3 下线。
