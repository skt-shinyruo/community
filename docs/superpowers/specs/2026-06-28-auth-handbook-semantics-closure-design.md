# Auth Handbook Semantics Closure Design

## 背景

本设计只覆盖 `community-app` 的 `auth` 业务线，以及它和 `user` owner 在 refresh session 事实上的协作面。范围以现有 handbook 为准：

- `docs/handbook/business-logic/auth.md`
- `docs/handbook/business-logic/workflows/auth-registration-login.md`
- `docs/handbook/core-logic-index.md`
- `docs/handbook/auth-login-session-flow.md`
- `docs/handbook/security.md`
- 仓库根目录 `AGENTS.md` 的 strict DDD Tactical Layering

当前实现和 handbook 的偏差集中在五类：

1. refresh rotation 语义单向消费，旧 token 一旦被 consume，中途依赖失败会把会话打进不可恢复黑洞态。
2. logout 只能通过 active token 找 family，带着已 rotate 的旧 token 登出时，可能撤不掉仍然活着的 family。
3. verify-first 注册的 resend 路径先覆盖 code 再发信，邮件失败会把用户卡在“旧码失效，新码没收到”的错误状态。
4. password reset request 的公开 HTTP 契约仍暴露 `resetLink` 字段，和 handbook“HTTP 响应不返回 reset link”的语义不一致。
5. `auth -> user` refresh session adapter 实现类放在 `auth.application`，违反仓库要求的 DDD Tactical Layering。

这次设计的目标不是做局部热修，而是把 `auth` 的 refresh / logout / verify-first resend / password reset request 契约和跨域适配边界一次性拉回 handbook 语义，并让后续实现能够被 ArchUnit、业务测试和 handbook 一致守护。

## 目标

- 保持 auth 和 user 的 owner 边界不变：auth 拥有门禁、会话发行、refresh token 策略和编排；user 拥有账号事实、密码 hash、用户状态、角色和 refresh session 存储事实。
- 让 refresh 的业务顺序继续符合 handbook：`消费旧 token -> 回源校验用户 -> 签发新 access token 与同 family 新 refresh token`。
- 消除 refresh 过程中的单向黑洞态，不再把依赖瞬时失败伪装成 token invalid。
- 让 logout 真正满足“撤销当前 token 或 token family”，即便客户端带来的是刚 rotate 之前的旧 token。
- 让 verify-first 注册的 resend 满足“邮件发送失败不能推进验证状态”。
- 让 password reset request 的对外响应和 handbook 完全一致，不再保留 `resetLink` 公开契约。
- 把 `auth -> user` refresh session adapter 从 `application` 收回到 `infrastructure`，恢复 strict DDD Tactical Layering。
- 为新状态机补齐单测、集成测试、controller 契约测试和架构测试。

## 非目标

- 不改变领域 owner：refresh token 策略仍归 auth，refresh session 存储事实仍归 user。
- 不把 refresh 改成中心化 access session，也不引入 access token blacklist。
- 不把 refresh rotation 扩展成通用 saga 或通用工作流框架。
- 不改变 JWT / cookie 的公开产品语义：access token 仍是短期 JWT，refresh token 仍是 HttpOnly cookie。
- 不扩大到 `user` 业务线的其他问题，不处理 content / social / wallet / market 等非 auth 业务线规则。
- 不改变 current gateway-first CORS / OriginGuard 策略，只在 auth 业务线内保持与 handbook 一致。

## 选型比较

### 方案 A：最小风险热修

只修最危险的行为偏差：

- refresh 失败时不再错误映射成 `REFRESH_TOKEN_INVALID`
- logout 支持用旧 token 找 family
- resend 发信失败时回滚 code

优点：

- 改动小，回归面窄。

缺点：

- handbook 仍不是强语义 SSOT。
- refresh 仍然容易残留半状态或错误边界。
- DDD 边界和 password reset 契约漂移问题继续存在。

### 方案 B：语义对齐型收口

围绕 handbook 已声明的业务语义，把 refresh / logout / verify-first resend / password reset request / adapter 边界一起收口。

优点：

- 能一次性关闭本次审查发现的主要问题。
- 不改变领域 owner，也不引入额外平台能力。
- 后续实现标准清楚，测试更容易稳定守护。

缺点：

- 要修改 `auth` 与 `user` 之间的 refresh session owner API 和存储事实。
- 测试矩阵会明显扩大。

### 方案 C：owner-domain 下沉

把 refresh rotation 状态机更多地下沉到 user owner，让 auth 只保留 cookie/JWT 外壳。

优点：

- 从数据 owner 角度看最集中。

缺点：

- 与 handbook 当前“auth 拥有 refresh token 策略，user 只拥有 refresh session 存储事实”的定义冲突。
- 变成一次领域归属调整，而不是本次语义收口。

## 选定方向

采用方案 B。

理由：

- 目标是“按 handbook 语义做彻底收口”，而不是只打补丁。
- handbook 已经明确 refresh / logout / verify-first / password reset request 的外部语义，本次只把实现拉回 handbook，不新增新的领域归属和产品能力。
- 该方向可以通过扩展 user owner 的 refresh session 存储事实来解决一致性问题，同时仍然由 auth application 保持策略编排。

## Owner 边界

### Auth owner

auth 继续拥有以下职责：

- 注册、登录、captcha、password reset token、JWT / refresh token、登录风控。
- refresh token family、rotation、reuse detection、cookie 规格和登录 / refresh / logout / verify-first 注册自动登录编排。
- 对外 HTTP surface：`/api/auth/**`。

auth 的边界形状保持：

```text
AuthController / Filter / Job
  -> auth.application.*ApplicationService
      -> auth domain service / auth repository interface / application port
      -> user.api.query / user.api.action
      -> analytics.api.action
          -> auth.infrastructure implementation
```

### User owner

user 继续拥有以下事实：

- 账号、邮箱、用户名、密码 hash、角色、状态、securityVersion。
- refresh session DB 持久化事实和 family revoke marker。

auth 不直接触达 user mapper / dataobject / infrastructure，只通过 `user.api.query` 和 `user.api.action` 协作。

### DDD 边界要求

- `auth.application` 只保留用例编排、事务边界、错误语义映射和 foreign owner API 协调。
- `auth.infrastructure` 放所有技术实现、仓储适配、mail adapter、web filter、job、跨域 port adapter。
- `auth.application` 中不得再保留 `RefreshTokenSessionApplicationPortAdapter` 这类跨域适配器实现类。

## 目标语义

### Refresh

handbook 语义保持不变：

1. controller 从 refresh cookie 读取 token。
2. auth 先推进旧 token 的消费流程。
3. auth 回源 user owner 校验用户仍存在、允许登录且允许 refresh。
4. 用户校验通过后签发新的 access token 和同 family 新 refresh token。
5. token invalid / user disabled 时 controller 清 cookie。

本次设计要改变的是“内部状态推进”，不是外部业务顺序。实现必须保证：

- 不再出现旧 token 已被不可逆 consume，但 user 查询或新 token 存储失败，导致整个会话黑洞化的情况。
- 依赖瞬时失败不再被伪装成 `REFRESH_TOKEN_INVALID`。
- reused-token family revoke 仍成立，但不能把处于中间态的 token 误判为 replay。

### Logout

logout 的业务语义保持：

- 请求没 cookie 时 best-effort 清浏览器 cookie。
- 请求有 token 时，撤销当前 token；如果能识别 family，则撤销整个 family。

本次设计的关键补强是：

- family 识别不能只依赖 token 当前是否 active。
- 对刚 rotate 掉的旧 token，只要系统在可识别窗口里还能解析出 familyId，logout 也必须撤销整个 family。

### Verify-First Registration Resend

handbook 语义保持：

- resend 仍要求 captcha 和有效 registration draft。
- 成功时重发验证码。
- 邮件发送失败时不能伪造成功。

本次设计新增的强约束是：

- resend 发信失败时，不得推进验证码存储状态。
- 不能出现旧码已失效而新码未送达的卡死状态。

### Password Reset Request Response

handbook 语义保持：

- reset token 存在于内部 token store 和邮件链接中。
- 对外 HTTP 响应不返回 reset link。

本次设计要求响应契约和实现完全一致，不再保留 `resetLink` 字段这个公开 surface。

## Refresh 设计

### 问题定义

当前 refresh 实现使用“立即 consume 旧 token”的模式：

```text
consume old token
  -> query user owner
  -> issue new access token
  -> store new refresh token
```

一旦 `query user owner`、`issueInFamily(...)` 或其底层持久化在 consume 之后失败，旧 token 已经不可恢复，客户端会话被打断，且错误语义常被映射成 `REFRESH_TOKEN_INVALID`。

### 目标行为

refresh 需要一个可恢复的两阶段推进模型。旧 token 不能在确认新 token 已落成之前直接进入不可逆终态。

### 状态机

DB refresh session 新增或显式引入状态字段，推荐值：

- `ACTIVE`
- `PENDING_ROTATION`
- `CONSUMED`

同时保留：

- `expiresAt`
- `revokedAt`
- `familyId`

状态推进如下：

```text
ACTIVE
  -> PENDING_ROTATION   // refresh begin
PENDING_ROTATION
  -> CONSUMED           // replacement refresh token stored successfully
PENDING_ROTATION
  -> ACTIVE             // user check or new token store failed and rollback succeeded
ACTIVE / CONSUMED
  -> REVOKED family     // family revoke path
```

如果不想显式引入字符串状态，也可以用等价的 owner fact 组合表达，但必须具备同样语义：

- 能区分“正常活跃”
- 能区分“正在旋转，暂未完成”
- 能区分“旋转完成 / 已终结”
- 能把中途失败回滚到活跃态

### Owner API 形状

user owner 需要提供能表达两阶段语义的同步 API。建议 `user.api.action` 新增或替换成以下动作：

- `beginRotation(tokenHash, now)`  
  返回：`RotationCandidateView(tokenHash, userId, familyId, expiresAt, stateVersion)`
- `completeRotation(tokenHash, replacementTokenHash, now)`  
  语义：把旧 token 从 `PENDING_ROTATION` 终结为 `CONSUMED`，并要求 replacement token 已存在于同 family。
- `rollbackRotation(tokenHash, now)`  
  语义：把旧 token 从 `PENDING_ROTATION` 恢复为 `ACTIVE`。
- `findActiveOrTerminated(tokenHash)`  
  返回可用于 logout / replay 检测的 family 识别视图。

auth 仍通过自己的 `RefreshTokenRepository` 与 `RefreshTokenApplicationService` 编排这些动作；只是 DB-backed repository 需要调用新的 owner API。

### Refresh 编排

新的 refresh 路径：

```text
AuthController.refresh
  -> LoginApplicationService.refresh
      -> RefreshTokenApplicationService.beginRotation(refreshToken)
      -> UserCredentialQueryApi.getByUserId(...)
      -> issue access token
      -> RefreshTokenApplicationService.issueReplacementInFamily(...)
      -> RefreshTokenApplicationService.completeRotation(...)
```

失败处理：

- beginRotation 找不到 token、token 已终结、family 已撤销、token 已过期：返回 `REFRESH_TOKEN_INVALID`。
- user 不存在、`loginAllowed=false`、`refreshAllowed=false`：撤销 family，返回 `USER_DISABLED`，controller 清 cookie。
- user owner 查询或 replacement token 存储抛出瞬时依赖错误：
  - 先尝试 rollback old token。
  - rollback 成功：返回 `SERVICE_UNAVAILABLE`，不清 cookie，允许稍后重试。
  - rollback 失败：fail-closed，撤销 family，返回 `SERVICE_UNAVAILABLE`，controller 清 cookie。
- completeRotation 失败：
  - 如果能确认 replacement token 已成功写入但旧 token 还没终结，优先重试 complete。
  - 若无法确认一致状态，fail-closed 撤销 family，并清 cookie。

### Error 语义

refresh 只在这些情况下返回 `REFRESH_TOKEN_INVALID`：

- cookie 缺失或空值
- token 不存在
- token 已终结且不可接受
- token 已过期
- family 已撤销
- revoked token replay 被判定为无效

refresh 返回 `USER_DISABLED` 的条件保持 handbook 定义：

- user 不存在
- `loginAllowed=false`
- `refreshAllowed=false`

refresh 返回 `SERVICE_UNAVAILABLE` 的条件：

- begin / complete / rollback 之外的底层依赖瞬时故障
- user owner 查询瞬时故障
- replacement token 写入故障
- 可恢复或不可恢复的中间失败，但不属于 token 本身无效

### Replay / reuse detection

`maybeRevokeFamilyForReusedToken(...)` 的语义保留，但判断基础从“旧 token 已 consume”改成“旧 token 已终结为 replay-detectable 状态”。中间态 `PENDING_ROTATION` 不属于 revoked replay。

grace window 行为保持：

- grace 内的旧 token 再次到达，不立即撤 family。
- 超过 grace 且 token 原本仍在有效期内，再触发 family revoke。

### Cookie 清理策略

controller 清 cookie 的条件：

- `REFRESH_TOKEN_INVALID`
- `USER_DISABLED`
- refresh 中间态回滚失败后选择 fail-closed 撤 family

controller 不清 cookie 的条件：

- 瞬时 `SERVICE_UNAVAILABLE`，且 rollback 已恢复旧 token 为 `ACTIVE`

这样浏览器不会因为瞬时依赖失败丢掉本来还可继续使用的 refresh cookie。

## Logout 设计

### 问题定义

当前 logout 只通过 active token `find()` 来解析 family。若客户端提交的是刚 rotate 掉的旧 token，则找不到 active row，family revoke 会被跳过。

### 目标行为

logout 必须支持通过 active token 或刚终结的旧 token 都能识别 family。

### 设计

`RefreshTokenRepository` 增加一个统一查询能力，例如：

- `findFamilyReference(refreshToken)`

返回：

- `FamilyReferenceView(userId, familyId, tokenState, expiresAt, revokedAt)`

它可以来自：

- active token record
- terminated / revoked tombstone

logout 新流程：

```text
read refresh cookie
  -> repository.findFamilyReference(token)
  -> revoke token best-effort
  -> if familyId present then revoke family
  -> clear cookie
```

结果要求：

- active token 登出：撤掉 family。
- 刚 rotate 掉的旧 token 登出：仍撤掉同 family。
- 完全无效或无法识别的 token：不做 family revoke，但仍 clear cookie。

### 识别窗口

旧 token 的 family 识别依赖 terminated tombstone 或等价视图，因此 owner fact 需要保证：

- 在 refresh token 原始过期时间内，旧 token 至少能用于 replay 检测和 family 识别。
- cleanup job 不得过早删除这类信息。

## Verify-First Resend 设计

### 问题定义

当前 resend 先写新 code，再发信。发信失败时没有 rollback，旧 code 已失效，新 code 又没送达。

### 目标行为

重发验证码只有在“邮件已成功进入发送路径”后，新的 active code 才能生效。

### 状态机

注册验证码状态推荐显式为：

- `ACTIVE`
- `PENDING_CONSUMPTION`   // verify-first create user path
- `PENDING_RESEND`        // resend path
- `CONSUMED`

或用等价字段表达同样语义。

resend 路径状态推进：

```text
ACTIVE(oldCode)
  -> PENDING_RESEND(candidateNewCode)
PENDING_RESEND
  -> ACTIVE(newCode)      // mail send succeeded
PENDING_RESEND
  -> ACTIVE(oldCode)      // mail send failed and rollback succeeded
```

### 编排

新的 resend 流程：

```text
require captcha
  -> resolve registration draft
  -> registrationCodeStore.beginResend(...)
  -> mailService.sendRegistrationCodeMail(...)
  -> registrationCodeStore.commitResend(...)
```

失败处理：

- cooldown active：继续返回 `REGISTRATION_CODE_RESEND_COOLDOWN`。
- beginResend 失败：按 invalid / expired / cooldown 现有语义返回。
- 发信失败：
  - rollback candidate code，恢复 old active code。
  - 返回邮件发送失败或内部错误，不伪造 issued。
- rollback resend 若失败：
  - fail-closed 删除 candidate 和 active code，要求用户重新开始 resend。
  - 但这种情况必须可观测，有日志和测试覆盖。

### 与 verifyAndLogin 的关系

verify-first 创建用户的 `PENDING_CONSUMPTION` 语义继续保留，和 resend 的 `PENDING_RESEND` 分开，避免语义冲突。

同一 registration user 的 code store 不允许同时进入两个 pending 态。

## Password Reset Request Contract 设计

### 目标行为

对外 HTTP 响应不再出现 `resetLink` 字段。

### 设计

- `PasswordResetRequestResult` 改成只表达对外语义，例如：
  - `PasswordResetRequestResult(boolean issued)`
- `PasswordResetRequestResponse` DTO 改成只保留：
  - `boolean issued`
- `PasswordResetApplicationService` 内部仍构建 reset link 并传给 `MailPort.sendPasswordResetMail(...)`。
- controller 不再接触 reset link。

### 兼容性

这是一次公开响应收紧。当前实现虽然返回空串，但前端若仍在读取该字段，需要同步清理。允许短期前后端同时兼容空字段，但目标代码状态必须移除该字段，不保留长期漂移 surface。

## DDD 边界收口设计

### `RefreshTokenSessionApplicationPortAdapter`

当前类位于 `auth.application`，但它是：

- `auth` 调 `user.api.query` / `user.api.action` 的技术适配器
- 不是 application use-case

因此它应迁移到：

- `auth.infrastructure.api` 或
- `auth.infrastructure.persistence`

推荐位置：

```text
com.nowcoder.community.auth.infrastructure.api.RefreshTokenSessionPortAdapter
```

它继续实现 `auth.application.port.RefreshTokenSessionPort`，由 application service 注入这个 port，而不是依赖实现类。

### 其他边界要求

- `AuthController` 继续只做 HTTP binding、cookie 读写和 DTO 转换，不参与 user owner API、domain 或 persistence 协作。
- `LoginApplicationService` 继续保留用例编排，不直接触达 DB mapper。
- refresh / resend 的中间状态推进逻辑通过 auth repository interface + user owner API 落地，不让 application 直接感知 mapper / row 结构。

## 持久化设计

### User owner DB refresh session facts

当前 `auth_refresh_token` 和 `auth_refresh_token_family_revocation` 需要支持新的 refresh 语义。建议最小增量：

- `auth_refresh_token`
  - 保留 `token_hash`, `user_id`, `family_id`, `expires_at`, `revoked_at`
  - 新增 `state`
  - 允许记录 `ACTIVE`, `PENDING_ROTATION`, `CONSUMED`
- 如有必要，新增：
  - `rotation_started_at`
  - `replacement_token_hash`

也可以不新增 `replacement_token_hash`，而是在 owner action 中通过同事务查询 family + token relation 完成 `completeRotation(...)`，但必须保证事务内可证明一致性。

### Registration code store facts

当前 Redis registration code payload 需要支持：

- active code
- resend candidate
- pending consumption
- 失败回滚

建议从当前字符串拼接 payload 升级到明确结构化值，至少包含：

- `activeCode`
- `activeExpiresAt`
- `failures`
- `issuedAt`
- `state`
- `candidateCode`
- `candidateIssuedAt`

保留 Redis 存储，不引入新 DB 表。

### Cleanup

- refresh cleanup job 删除过期 session 时，不得提前删除仍需用于 replay 检测或 family 识别的终结 token facts。
- registration draft / code cleanup 继续 best-effort，但不允许清理动作回滚已完成的 user create。

## 测试设计

### Auth application unit tests

需要新增或重写以下测试：

- refresh 成功：旧 token begin -> user active -> new token stored -> old token completed。
- refresh user disabled：family revoke，clear cookie。
- refresh user owner query transient failure：rollback 成功，返回 `SERVICE_UNAVAILABLE`，不 clear cookie。
- refresh replacement token store failure：rollback 成功，返回 `SERVICE_UNAVAILABLE`。
- refresh rollback failure：family revoke，clear cookie。
- refresh replay outside grace：family revoke。
- logout with active token：family revoke。
- logout with rotated old token：family revoke。
- resend mail failure：old code preserved，new code not activated。

### User owner integration tests

需要补 `RefreshTokenSessionApplicationService` / repository / mapper 层测试：

- beginRotation 把 `ACTIVE` 变 `PENDING_ROTATION`。
- completeRotation 只在 replacement 已存在时成功。
- rollbackRotation 从 `PENDING_ROTATION` 恢复 `ACTIVE`。
- family revoke 会终结 active 和 pending rotation tokens。
- active 或 terminated token 都能解析 family reference。

### Controller / contract tests

- `/api/auth/refresh` 在 `SERVICE_UNAVAILABLE` 且 rollback 成功时，不应 clear cookie。
- `/api/auth/refresh` 在 token invalid / user disabled 时 clear cookie。
- `PasswordResetRequestResponse` 不再包含 `resetLink`。

### Architecture tests

因为会移动 adapter 包位，至少要跑：

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

若现有 ArchUnit 未覆盖 `auth.application` 中跨域适配器实现类落位，还应补守卫，避免同类越界再次出现。

## 文档更新

实现完成后要同步更新：

- `docs/handbook/business-logic/auth.md`
- `docs/handbook/business-logic/workflows/auth-registration-login.md`
- `docs/handbook/auth-login-session-flow.md`
- `docs/handbook/core-logic-index.md`

更新重点：

- refresh 内部采用两阶段推进，但外部业务顺序不变。
- logout 能用旧 token 撤销 family。
- resend 发信失败不推进 code 状态。
- password reset request 响应不再暴露 `resetLink`。
- `RefreshTokenSessionPortAdapter` 的包位和 owner API 形状更新。

## 风险与迁移

### 主要风险

- refresh session DB schema 增量会影响现有 login / refresh / logout 主路径，必须先有迁移脚本和回滚策略。
- Redis registration code payload 结构调整要注意兼容现网旧值，避免部署瞬间把所有在途注册都打断。
- controller contract 收紧会影响前端若仍读取 `resetLink` 的代码。

### 迁移策略

推荐顺序：

1. 先扩 owner fact 和 owner API，保留旧行为兼容。
2. auth repository 切到新 API，但保持旧 token / old payload 兼容读取。
3. 上线后再移除旧 DTO 字段和无用旧路径。

### 兼容读取

- refresh session repository 在迁移窗口内允许读取“无显式 state 的旧 row”，并将其视为 `ACTIVE`。
- registration code repository 在迁移窗口内允许读取旧 4/5 段字符串 payload，并在首次写入时升级到新结构。

## 实施原则

- 只实现 handbook 已经声明的业务语义，不借这次工作重定义领域 owner。
- refresh 的一致性优先于局部性能优化，先保证语义闭环。
- 任何中间态失败都要有明确的回滚或 fail-closed 终结规则，不能留下 silent unknown state。
- 所有新状态机必须先有测试，再写实现。
