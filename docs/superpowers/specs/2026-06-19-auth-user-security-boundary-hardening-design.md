# Auth/User Security Boundary Hardening Design

## 背景

本设计覆盖 `community-app` 中 auth 与 user 业务线的完整修复包：注册、登录、验证码、JWT、refresh token、密码重置、用户资料、头像、角色、禁用、禁言和封禁状态。设计依据是仓库根目录 `AGENTS.md` 的 strict DDD Tactical Layering，以及现有 handbook：

- `docs/handbook/architecture.md`
- `docs/handbook/security.md`
- `docs/handbook/auth-login-session-flow.md`
- `docs/handbook/business-logic/auth.md`
- `docs/handbook/business-logic/user.md`

当前审查发现的问题集中在三类：

1. 安全会话闭环不完整：角色变化、封禁、部分账号状态变化不能及时影响 refresh 和高风险入口；已签发 JWT 的滞后语义不够清楚。
2. 并发和一致性缺口：注册验证码先消费再建用户、Redis refresh token 消费非原子、密码重置邮箱限流可能被用于 DoS。
3. DDD 边界和低风险校验不够硬：`DbRefreshTokenRepository` 位于 application 包，auth application service 互相充当 helper，role type invariant、DTO 长度、captcha issue 限流和 `/me` subject 兜底不足。

本设计一次性覆盖全部问题，但实现应按风险顺序推进，避免把认证与用户域重写成新的框架。

## 目标

- 保持 auth/user owner 边界清晰：auth 拥有认证流程和 token 策略，user 拥有账号事实、密码 hash、角色、状态、处罚状态和 DB refresh session。
- 让角色变更、密码变更、账号禁用和封禁能够阻止继续 refresh，并对高风险入口提供更快的权限变更感知。
- 明确定义 access token 的短期滞后窗口，不引入全局 JWT 黑名单。
- 修复注册验证码并发、Redis refresh 消费、密码重置限流的业务一致性问题。
- 收敛 auth application 内的 helper 边界，移动不合适的 repository implementation 包位置。
- 补强输入校验、domain invariant、DB constraint、测试和文档。
- 所有改动继续满足 `AGENTS.md` 的 DDD Tactical Layering 和现有 ArchUnit 守卫。

## 非目标

- 不替换 JWT 为中心化 session introspection。
- 不引入全局 access token blacklist 或每请求强制查库的普通鉴权模型。
- 不改变 refresh token 默认 DB store 的主路径；Redis store 只修复可选实现的原子性。
- 不把 auth 直接改成 user 表或 refresh session 表的 owner。
- 不重写 frontend 登录态模型；响应结构尽量保持兼容。
- 不把本次工作扩展到 market/wallet/content/social 的业务规则重构。

## Owner 边界

### Auth owner

auth 域继续拥有以下事实和策略：

- 登录、refresh、logout、注册验证码、密码重置 token、captcha 和登录风控。
- access token 的签发策略、JWT claim 组装和 refresh token rotation 策略。
- opaque token 明文的生成、cookie 规格和 refresh family reuse 处理。

关键入口和目标边界：

```text
auth.controller.AuthController
  -> auth.application.*ApplicationService
      -> auth domain service / repository interface
      -> user.api.query / user.api.action
      -> auth.infrastructure implementation
```

### User owner

user 域继续拥有以下事实：

- `user` 表中的用户账号、邮箱、密码 hash、头像 URL 投影、角色、状态、禁言、封禁和认证安全版本。
- DB refresh session 的持久化事实。
- user policy 事件和给 IM 的处罚/存在性投影。

user 对 auth 暴露同步 API，不允许 auth 绕过 owner 写 user mapper：

```text
auth ApplicationService
  -> user.api.query / user.api.action
  -> user.infrastructure.api adapter
  -> user.application.*ApplicationService
  -> user domain / repository
```

## 安全会话设计

### Access token

access token 保持短期无状态 JWT。角色、封禁、密码或账号状态变化后，已签发的 access token 不做全局即时拉黑，最多继续存活到 `security.jwt.access-token-ttl-seconds`。当前默认是 900 秒。

文档必须明确这个滞后窗口，避免把 JWT 误解为强一致权限载体。

### Refresh token

refresh token 是强一致失效边界。以下 user 侧变化必须撤销该用户所有 refresh sessions：

- 密码重置成功。
- 管理员变更角色。
- 管理员禁用账号，或其他会让 `status` 变为不可登录的动作。
- active ban 被定义为账号级暂停时，封禁开始和封禁仍有效期间的 refresh 必须失败。

刷新流程目标：

```text
AuthController.refresh
  -> LoginApplicationService.refresh
      -> RefreshTokenApplicationService.consume
      -> UserCredentialQueryApi.getByUserId
      -> 校验 status / banUntil / securityVersion
      -> JwtTokenService.createAccessToken
      -> RefreshTokenApplicationService.issueInFamily
```

用户不存在、禁用、被账号级封禁或版本策略不允许续期时，auth 撤销当前 refresh family 并清 cookie。

### JWT 安全版本

user 域新增或明确一个认证授权安全版本。推荐字段名为 `securityVersion`，不要复用含义过宽的 IM policy version，除非后续设计明确它同时覆盖认证授权和 IM policy。

版本变化规则：

- 角色变化：递增。
- 密码重置成功：递增。
- 账号 status 变化：递增。
- banUntil 从非 active 变 active、active ban 延长、active ban 解除：递增。
- muteUntil 可以递增 user policy version，但不必影响 auth security version，除非产品定义 mute 也影响敏感入口。

JWT 增加 claim，例如：

```text
security_version: <long>
```

登录和 refresh 签发 access token 时从 `UserCredentialView` 或新增 view 字段读取最新版本。refresh 不信任旧 JWT claim，只信任 user owner 返回的当前事实。

### 高风险入口版本校验

普通业务请求仍只做 JWT 签名、issuer、exp 和 authority 校验。以下高风险入口建议增加轻量版本校验：

- `/api/users/admin/**`
- `/api/ops/**`
- market admin 写入口。
- wallet admin 写入口。
- 其他后续确认的 moderator/admin 写入口。

校验形态：

```text
Security filter / authorization helper
  -> 只对高风险路径触发
  -> 从 JWT 取 subject 与 security_version
  -> 调 auth application token freshness service
      -> user owner query 读取当前 securityVersion / status / ban state
  -> 版本过旧或账号不可用时返回 401 或 403
```

该组件属于应用级安全基础设施，但不能让 filter/controller 直接查询 user API。技术入口只做 request/JWT 适配并进入 auth 同域 application boundary；auth application 再按 DDD 规则调用 user owner query API。

## Ban / Mute 语义

本设计将 `banUntil` 定义为账号级暂停，将 `muteUntil` 定义为发言级限制。

### banUntil

active ban 的影响：

- 登录失败，返回 `USER_DISABLED` 或新增 `USER_BANNED`。
- refresh 失败，撤销 refresh family 并清 cookie。
- 头像上传 session、头像确认、用户资料敏感写接口拒绝。
- 高风险 admin/moderator 操作入口拒绝被封禁用户作为 actor。
- content 写路径继续拒绝发帖和评论。

管理员或治理动作写入 active ban 时，user owner 撤销被封禁用户的 refresh sessions，并递增 security version。

### muteUntil

active mute 的影响：

- 拒绝发帖、评论、回复等发言动作。
- 不影响登录、refresh、头像、普通资料读取。
- 继续发布 user policy changed 事件给 IM/content 等投影。

## 核心修复设计

### 角色变更与 JWT 滞后

当前 `AdminUserApplicationService.updateRole(...)` 更新 `user.type` 和审计日志，但不撤销 refresh sessions。目标行为：

1. user domain 校验 role type 合法性、自降级保护和 reason/confirm。
2. application 事务内更新角色。
3. 递增 auth security version。
4. 撤销目标用户 refresh sessions。
5. 记录审计日志。
6. 新登录或 refresh 后重新签发 JWT authorities。

已签发 access token 最多继续有效到 TTL；高风险入口版本校验可更快拒绝旧 token。

### 密码重置限流与会话失效

请求阶段目标顺序：

```text
captcha 校验
  -> IP 维度限流
  -> normalize email
  -> user owner 查询用户
  -> 用户存在且可用时计邮箱维度限流
  -> 生成 reset token / 发邮件
  -> 用户不存在或不可用时返回统一受理结果，不计邮箱 quota
```

确认阶段保持一次性消费 reset token。密码更新成功后：

- 更新 BCrypt hash。
- 递增 security version。
- 撤销该用户所有 refresh sessions。

如果 user owner 更新失败，仍按当前设计恢复 reset token 的剩余 TTL，不延长到完整 TTL。

### 注册验证码两阶段

当前注册验证先 `verifyAndConsume` 再创建用户，并发冲突可能导致验证码被消费但用户创建失败。目标是验证码消费和用户创建绑定。

推荐 Redis code 状态机：

```text
active -> pending -> consumed
active -> failed / expired
pending -> active    // 创建用户失败且允许重试
pending -> expired   // pending TTL 超时
```

验证流程：

1. 根据 registration token 读取 draft。
2. 校验验证码并原子标记为 pending，pending TTL 使用短窗口，例如 60 秒。
3. 调 `UserRegistrationActionApi.createVerifiedRegistrationUser(...)`。
4. 创建成功后消费验证码并删除 draft。
5. 创建失败但用户未创建时，恢复验证码到 active，保留原剩余 TTL 或一个不超过原 TTL 的重试 TTL。
6. 创建成功但自动登录失败时，验证码和 draft 仍应清理，返回 `REGISTRATION_ACTIVATED_LOGIN_REQUIRED`。

pending 状态避免两个并发请求同时创建用户；恢复机制避免一次 DB 唯一约束竞态永久吞掉验证码。

### Redis refresh token 原子消费

`RedisRefreshTokenRepository.consume(...)` 的目标是用 Lua 一次完成：

- 检查 family revoked marker。
- 读取 token payload。
- 检查 token 是否存在、是否过期。
- 成功时写 consumed tombstone，保留 reuse detection 所需信息。
- 从 family active set 移除 token。
- 返回 token 的 userId、familyId、expiresAt、revokedAt 状态。

重复消费时：

- grace window 内可以视为已撤销但不立即撤 family。
- 超过 grace window 且 token 原本未过期时触发 family revoke。

这样避免 `getAndDelete` 成功但 tombstone 未写入时进程崩溃导致 reuse detection 盲区。

### DDD 边界收敛

#### `DbRefreshTokenRepository`

`DbRefreshTokenRepository` 实现 auth domain 的 `RefreshTokenRepository`，并通过 user owner API 操作 DB refresh session。它不应位于 `auth.application`。

目标位置：

```text
auth.infrastructure.persistence.DbRefreshTokenRepository
```

若 ArchUnit 不允许 infrastructure 直接依赖 foreign API，则采用 application-owned technical port：

```text
auth.application.port.RefreshSessionStorePort
  <- auth.infrastructure.persistence.DbRefreshSessionStoreAdapter
      -> user.api.action / user.api.query
```

无论采用哪种，auth domain 不依赖 user API，auth controller 不接触 repository。

#### ApplicationService 互调

application 内不再把另一个用例服务当工具类。推荐拆出小组件：

- `CaptchaChallengeComponent`：封装 captcha 必填、校验、错误转换。
- `LoginTokenIssuer`：封装 JWT access token 和 refresh token/cookie spec 签发。
- `RefreshTokenCoordinator`：封装 refresh token rotation/family/reuse 技术编排；是否继续命名为 `RefreshTokenApplicationService` 取决于它是否仍作为独立用例入口被 controller/job 直接调用。

这些组件必须位于 `auth.application` 或 `auth.application.port`，不使用 `FacadeService`、`UseCase`、`CommandService` 等被禁止命名，也不暴露 HTTP transport 类型。

### Role type invariant

`user.type` 的合法值不能只靠 HTTP DTO。

目标约束：

- domain 增加 `UserRole` enum/value object 或 domain service 校验。
- `AdminUserApplicationService.updateRole(...)` 使用 domain 校验后再写库。
- DB schema 增加 check constraint，限制合法 role type。
- MyBatis repository 不接受未校验的任意整数。
- 测试覆盖 application 直接调用非法 type。

### DTO 与低风险安全补强

补强项：

- `PasswordResetRequestRequest` 和 `PasswordResetConfirmRequest` 增加 `captchaId`、`captchaCode`、`resetToken` 长度限制。
- 注册 code、registration token、login captcha 字段复查同类限制。
- captcha issue endpoint 增加 IP 或 device 维度限流，防止无限生成 Redis key 和 PNG。
- `/api/auth/me` 对非 UUID subject 返回统一认证错误，避免裸 `IllegalArgumentException`。
- 安全日志和观测保持不记录 JWT、cookie、reset token、registration token、captcha code、密码和 Redis key 明文。

## 数据模型与契约变更

### User credential view

`UserCredentialView` 或等价 auth-facing model 增加：

```text
securityVersion: long
banUntil: Instant?
status: int
type: int
```

如果不希望 auth 感知 `banUntil` 字段名，可以提供更语义化字段：

```text
loginAllowed: boolean
refreshAllowed: boolean
denialReason: enum
securityVersion: long
```

推荐第二种 API 形态，因为 user owner 保留处罚和状态解释权，auth 只编排认证结果。

### JWT claim

JWT payload 增加：

```text
security_version
```

claim 名称使用 snake_case 或现有 claim 风格统一。decoder 需要兼容旧 token 缺少该 claim 的情况：普通请求仍可通过，只有高风险版本校验路径缺少版本时要求 refresh/relogin。

### Refresh session

DB refresh session 表无需保存 access token。撤销全部 refresh sessions 继续通过 user owner repository 按 userId 写 family revocation / revoked_at。

如果新增 `security_version` DB 字段，则属于 user owner 的用户表或 credential projection，不放在 auth refresh token 表中。

## 错误语义

- 密码错误、用户不存在仍返回 `INVALID_CREDENTIALS`，避免枚举。
- `status` 禁用和 active ban 可以统一返回 `USER_DISABLED`；如果产品需要精确提示，可新增 `USER_BANNED`，但不要在登录凭据阶段泄漏过多账号状态。
- refresh 发现用户禁用或被封禁时返回可清 cookie 的错误，controller 写 clear cookie。
- 高风险入口 token 版本过旧时返回 401 更利于前端 refresh；refresh 后仍不满足权限时返回 403。
- 密码重置请求对未知邮箱、禁用邮箱、封禁邮箱保持统一 accepted 响应。

## 测试计划

### 应用层和 repository 测试

- `AdminUserApplicationService.updateRole(...)`：
  - 同角色 no-op 不撤销 sessions。
  - 角色变化递增 security version 并撤销目标用户 refresh sessions。
  - 非法 role type 被 domain 拒绝。
  - 自降级保护仍有效。
- `UserModerationApplicationService.applyModeration(...)`：
  - active ban 递增 security version 并撤销 refresh sessions。
  - mute 不影响 auth security version。
  - unban 恢复 refresh 能力但不恢复旧 refresh sessions。
- `LoginApplicationService.login/refresh(...)`：
  - disabled/banned 用户不能登录。
  - refresh 回源发现 disabled/banned 后撤销 family 并清 cookie。
  - JWT authorities 和 `security_version` 使用 user owner 最新事实。
- `PasswordResetApplicationService.requestReset/confirmReset(...)`：
  - 未知邮箱不计邮箱 quota 且响应一致。
  - 已存在可用用户计邮箱 quota。
  - 成功重置递增 security version 并撤销 refresh sessions。
  - downstream 失败恢复 reset token 剩余 TTL。
- `RegistrationVerificationApplicationService.verifyAndLogin(...)`：
  - 并发 verify 只有一个创建成功。
  - 创建失败时验证码不会永久丢失。
  - 创建成功但自动登录失败时 draft/code 清理并返回 login required。
- `RedisRefreshTokenRepository`：
  - 原子消费成功写 tombstone。
  - 重复消费按 grace/reuse 规则处理。
  - family revoked、过期 token、缺失 token 行为正确。

### 安全与集成测试

- 高风险 admin 路径 token 版本落后时拒绝，且安全 filter 不直接调用 user API。
- 普通 public/anonymous 入口不因版本校验被误拦截。
- refresh cookie 属性、OriginGuard、prod startup validator 不降级。
- `/api/auth/me` 非 UUID subject 返回统一错误。
- captcha issue 限流触发 `TOO_MANY_REQUESTS`。

### 架构守卫

必须运行：

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest' -DfailIfNoTests=false
```

如果只改 community-app 且依赖已安装，也运行 `AGENTS.md` 中较窄命令：

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

若新增安全基础设施入口，需要补充或调整 ArchUnit 规则，确保 filter/adapter 只进入 auth application boundary，不直接调用 user owner API。

## 文档更新

实现时同步更新：

- `docs/handbook/security.md`
  - access token 滞后窗口。
  - refresh 强一致失效。
  - ban/mute 定义。
  - 高风险入口版本校验。
- `docs/handbook/auth-login-session-flow.md`
  - JWT `security_version` claim。
  - refresh 回源校验 status/ban/version。
  - role/password/ban 后 refresh session 撤销。
- `docs/handbook/business-logic/auth.md`
  - 注册验证码两阶段。
  - 密码重置限流顺序。
  - Redis refresh atomic consume。
- `docs/handbook/business-logic/user.md`
  - securityVersion owner 语义。
  - role 变更和处罚变更的 session 影响。
- 如变更架构规则：
  - `docs/handbook/architecture.md`
  - `docs/handbook/system-design.md`
  - `backend/community-app/src/test/java/com/nowcoder/community/app/arch`

## 实现顺序

1. 用测试刻画当前风险行为，优先覆盖 role/session、ban/login-refresh、password reset quota、registration verify、Redis refresh consume。
2. 在 user 域增加 auth security version 语义，并把 role/password/status/ban 变化接入版本递增和 refresh session 撤销。
3. 修改 auth 登录、refresh 和 JWT claim 签发，接入 user owner 的当前可登录/可刷新判断。
4. 实现高风险入口版本校验，先覆盖 admin/ops/user admin 路径，再评估 wallet/market admin。
5. 改注册验证码两阶段状态机和 Redis refresh Lua 原子消费。
6. 收敛 `DbRefreshTokenRepository` 包位置和 auth application helper 组件。
7. 补 DTO 长度、captcha issue 限流、`/me` subject 兜底和 role DB constraint。
8. 更新 handbook，运行架构测试和相关业务测试。

## 回滚与兼容

- JWT 增加 claim 对旧 token 解码保持兼容；缺 claim 的旧 token 只在高风险版本校验路径被拒绝。
- refresh session 撤销只影响后续 refresh，不删除用户账号事实。
- `USER_BANNED` 如新增，需要前后端协商；如果不想扩展错误码，统一映射到 `USER_DISABLED`。
- Redis refresh store 是可选配置；DB store 主路径保持可用。
- captcha issue 限流参数默认应保守，避免影响正常登录表单加载。

## 风险与缓解

- **安全版本查询导致 DB 压力增加**：只对高风险路径触发，不对所有请求做 introspection。
- **ban 语义改变影响已有用户体验**：文档明确 active ban 是账号级暂停，mute 才是发言级限制；错误提示按产品需要控制。
- **注册验证码两阶段增加 Redis 状态复杂度**：用 Lua 保证状态转换原子，并通过 repository 测试覆盖 active/pending/consume/restore。
- **role DB constraint 与历史数据冲突**：迁移前检查现有 `user.type` 是否都在合法范围，必要时先修数据。
- **ApplicationService helper 拆分过度**：只拆出当前已有复用点，不引入聚合 facade 或新 use case 命名体系。

## 验收标准

- 全部已识别问题都有代码级测试或明确文档化的残余风险。
- role/password/status/ban 变化后，refresh token 不再能继续续期旧权限。
- active ban 用户不能登录或 refresh；active mute 用户只能被禁止发言。
- 注册验证码不会因并发创建失败被永久吞掉。
- Redis refresh token 消费不存在删除成功但 tombstone 缺失的崩溃窗口。
- 密码重置未知邮箱不能消耗任意邮箱 quota。
- auth/user 代码继续通过 ArchUnit DDD 守卫。
- handbook 与实现一致，特别是 JWT 滞后窗口和 ban/mute 语义。
