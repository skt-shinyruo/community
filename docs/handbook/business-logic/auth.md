# Auth 认证业务逻辑

认证域负责用户进入系统的凭证和会话生命周期：注册、登录、验证码、密码重置、access token、refresh token 和登录风控。登录、刷新和退出的逐步链路另见 [../auth-login-session-flow.md](../auth-login-session-flow.md)；本文补齐认证域全部业务能力的域级视角。

## Owner / SSOT

- `auth` 拥有登录流程、验证码流程、注册验证码流程、密码重置 token、JWT 签发、refresh token 策略、refresh session 存储事实和登录风控。
- `user` 拥有用户账号、密码 hash、用户状态、角色和 `securityVersion`。
- access token 是客户端持有的短期 JWT，服务端不保存在线 access session。
- refresh token 明文只存在于浏览器 HttpOnly cookie 和当前请求/响应内；默认 DB 存储只保存 SHA-256 hash。

## 入口

HTTP 入口位于 `AuthController`：

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/register`
- `POST /api/auth/register/code/resend`
- `POST /api/auth/register/code/verify`
- `GET /api/auth/captcha`
- `POST /api/auth/password/reset/request`
- `POST /api/auth/password/reset/confirm`

后台入口：

- `RefreshTokenCleanupJob` 清理过期 refresh session。

## 应用层入口

`AuthController` 直接进入具体 auth 用例应用服务，不再经过总入口式聚合门面：

- `LoginApplicationService`：登录、refresh、logout、token 签发。
- `RegistrationApplicationService`：注册开始、生成 draft 和验证码。
- `RegistrationVerificationApplicationService`：重发注册验证码、验证注册验证码并登录。
- `RegistrationCodeMailDeliveryApplicationService`：校验 outbox delivery fencing、发送注册邮件并提升 replacement code。
- `CaptchaApplicationService`：图片验证码生成和校验。
- `PasswordResetApplicationService`：密码重置请求和确认。
- `LoginRateLimitApplicationService`：登录失败计数、验证码触发和封锁。
- `RefreshTokenApplicationService`：refresh token 签发、旋转、撤销、family 处理、cookie 规格。

## 数据流

认证域的数据流分成四条主线：

1. 注册：`AuthController` 接收请求后进入 `RegistrationApplicationService`，auth 先校验验证码和字段，再按客户端 IP、规范化用户名、规范化邮箱执行 HMAC 伪名请求配额；只有通过配额后才调用 `UserRegistrationActionApi.prepareRegistrationUser(...)`，避免用重复注册请求持续触发 BCrypt 和邮件。user owner 检查冲突并准备用户名、邮箱、密码 hash 和默认头像。auth application 生成 registration token，draft 仓储只负责按 token 存储上下文；注册码通过带 UUID lease 的 pending 消费态后，再调用 `createVerifiedRegistrationUser(...)` 插入 active 用户，并复用登录签发链路返回 access token 和 refresh cookie。
2. 登录：`LoginApplicationService.login(...)` 先校验凭据字符边界，再在任何 user owner / MySQL 调用前按 HMAC 伪名 IP 与精确输入获取 Redis provisional permit。user owner 用存在性无关的 MySQL 排序权重 scalar 返回 authoritative subject；auth 先取得 subject lease，再释放 input lease 并继续账号查询。IP lease 全程保留；captcha、失败计数和成功清理都使用 authoritative subject，不以 userId 分桶。lease 心跳和 BCrypt 前后所有权校验均 fail-closed。
3. refresh / logout：浏览器只把 refresh token 放在 HttpOnly cookie 中。refresh 时 auth 用随机 fencing lease 把旧 session 转入 `PENDING_ROTATION`，再回源 user 校验用户仍允许登录和 refresh，并读取当前 `securityVersion`；校验通过后才生成 replacement token，并用同一 lease finish rotation，把旧 session 标为 `CONSUMED`、新 session 标为 `ACTIVE`。失败时只有 lease owner 能 rollback；若无法安全恢复则撤销 family。logout 可从 active、pending 或 terminal tombstone 识别 family，family marker 会阻止并发 replacement 落地。
4. 密码重置：auth 校验规范化邮箱、captcha、IP/邮箱请求限流和 reset token。token 以 SHA-256 ID 存储，确认使用 generation + fencing lease；user owner 校验新密码策略并按签发 `securityVersion` 做 CAS 改密。成功或 stale CAS 都撤销旧 generation，后签发的新 generation 不受旧请求清理影响。旧 refresh token 下次续期时因安全版本不匹配而被拒绝。

auth 不直接写 user 表；refresh session 则通过 auth 自己的 `RefreshTokenRepository` 进入 MyBatis 或 Redis infrastructure。

## 注册流程

当前注册采用 Verify-First 流程，核心目标是高并发下避免先创建大量未激活用户行。

1. `AuthController.register(...)` 解析请求和客户端 IP。
2. controller 组装 `RegisterCommand`，调用 `RegistrationApplicationService.register(...)`。
3. `RegistrationApplicationService.register(...)` 校验验证码、用户名、密码、邮箱和邮件配置，再按 IP、规范化用户名和规范化邮箱原子增加请求配额；Redis 失败时 fail-closed。
4. auth 域通过 `UserRegistrationActionApi.prepareRegistrationUser(...)` 进入 user owner。配额位于 BCrypt 和 draft 创建之前。
5. user owner 规范化用户名和邮箱，先检查用户名/邮箱是否已存在，再生成预备用户 ID、计算 BCrypt 密码、准备默认头像，但不插入 `user` row。
6. auth application 生成 256-bit base64url opaque `registrationToken`，把 `PreparedRegistrationDraft` 存入 draft store；token 冲突最多重试 5 次。
7. auth 域用安全随机生成器签发 6 位注册验证码，将随机 delivery ID 与 active code 一起写入 Redis，再持久化 `auth.registration-code-mail` outbox；HTTP 成功表示邮件任务已受理。
8. `verifyRegisterCode(...)` 根据 `registrationToken` 找回 draft，用随机 lease 把验证码转入 `PENDING_VERIFICATION`。
9. 验证通过后调用 `UserRegistrationActionApi.createVerifiedRegistrationUser(...)`，由 user owner 插入 active 用户；创建成功后只有同一 lease 能 consume pending code 并删除 draft。若创建前失败，同一 lease 才能 restore；lease 过期后可由新请求接管，旧 owner 不能覆盖新状态。
10. 注册验证成功后复用登录签发能力，直接返回 access token 和 refresh cookie。
11. 注册验证成功后会 best-effort 删除 draft/code；失败不应让已创建用户回滚到未注册状态。

失败语义：

- 验证码错误或过期返回认证错误，不创建用户。
- 用户名或邮箱冲突由 user owner 判断；prepare 阶段做前置查重，最终插入仍依赖数据库唯一约束兜住并发竞态。
- 初次签发或重发只有在 outbox 持久化失败时才同步回滚 draft/code 或 replacement；SMTP 失败由共享 outbox 重试，不能把暂时不可达伪装成已投递。
- 重发先通过一个 Redis Lua 原子消费可信客户端 IP、规范化邮箱和 registration identity 三个 HMAC 配额，再以同一 UUID 作为 delivery ID 与 replacement lease 写 `PENDING_REPLACEMENT`。worker 发送前必须核对 exact delivery/code/lease 并续租，SMTP 成功后只有该 lease 能 promote；失败保持 pending 供 outbox 重试，新的 replacement 接管后旧事件会被 fencing 丢弃。原 active code 在 replacement 成功前继续保存。
- 验证失败达到上限时 Redis 不删除 key，而是移除 code、写 `EXHAUSTED` 冷却墓碑并从耗尽时刻重新计算 resend cooldown；correct-code 重试和立即 beginReplacement 都不能绕过失败预算。
- Redis 使用 `auth:regcode:v2:{<userId>}` 结构化 Hash。首次访问用只操作 legacy key 的 Lua 原子执行 `GET + PTTL + DEL`，再解析并用只操作 v2 key 的 Lua 条件导入；两个 key 不会进入同一 Lua，兼容 Redis Cluster。该一次性 drain 要求旧 writer 已完全停止，发布约束见 [运行与排障](../operations.md#注册验证码-redis-v2-切换)。
- active 用户创建成功但自动登录 token 签发失败时，返回 `REGISTRATION_ACTIVATED_LOGIN_REQUIRED`，前端应清理注册上下文并提示直接登录。
- abandoned draft 过期后自然清理，不会产生用户行。

## 登录流程

登录由 `LoginApplicationService.login(...)` 编排：

1. `AuthDomainService.requireCredentials(...)` 校验必填值，并拒绝控制字符、Unicode format 字符、未配对 surrogate 和不可见用户名；非法输入只累计 IP 风控。
2. 在任何 user owner / MySQL 调用前，按 IP 和 trim 后的精确原始用户名输入获取 provisional permit；输入不做大小写、重音或兼容字符折叠，分别使用 `login-ip` / `login-input` HMAC scope，避免攻击者无界触发身份解析。
3. 调 `UserCredentialQueryApi.authenticationSubject(...)`；user owner 用不读取用户表的 MySQL `WEIGHT_STRING` scalar，按 `utf8mb4_unicode_ci` 生成 `utf8mb4_unicode_ci:v1:<digest>` opaque 主体。主体只取决于排序规则权重，与账号是否存在无关。
4. auth 先获取 `subject:v3` lease，再把 permit 从 IP + provisional input 替换为 IP + authoritative subject 并释放输入 lease；任何失败都 fail-closed，且不使用 `userId` 作为风控 key。
5. 调 `UserCredentialQueryApi.prepareAuthentication(...)`；user owner 随后才按数据库真实身份等价规则查询一次，并再次校验查询返回的存量用户名。不存在账号或存量用户名违反当前 owner 策略时，都返回不含 stable userId 和真实 hash 的 dummy challenge，防止安全别名命中不安全旧账号。
6. 失败 String 与 lease ZSET 通过 hash tag 同 slot。Lua 用 Redis `TIME` 清理过期 lease，并原子要求 `已提交失败数 + 活跃 lease 数 < 阈值`；部分获取失败时反序释放。
7. 在 IP + authoritative subject permit 内用同一个合并预算决定是否要求 captcha。缺参或校验失败会先计入这两个维度的失败次数，再返回 `CAPTCHA_REQUIRED` / `CAPTCHA_INVALID`。
8. 后台按 lease 的四分之一周期续租，并在 BCrypt 前后主动确认所有 slot 的 token 所有权；丢失或 Redis 异常均 fail-closed。
9. challenge 执行 BCrypt；缺失账号或非法 hash 使用固定 dummy BCrypt，禁用状态只在密码正确后返回。
10. 认证失败先向 IP 和 authoritative subject 提交失败计数再释放 permit；成功只 reset authoritative subject 桶，IP 桶保留到窗口到期。
11. 调 `issueLoginResult(...)` 签发 access token 和 refresh token，记录安全日志，并通过 `AnalyticsIngestActionApi.recordLoginSuccess(...)` 记录登录成功采集。

user owner 的密码校验只接受 BCrypt；新密码还必须满足 UTF-8 编码最多 72 字节。

## Refresh 和 Logout

refresh 使用 refresh token rotation：

1. controller 从 refresh cookie 读 token。
2. `LoginApplicationService.refresh(...)` 调 `RefreshTokenApplicationService.beginRotation(...)`，把旧 refresh session 转入 `PENDING_ROTATION`，lease 为 30 秒。
3. token hash 找不到、已撤销、过期或 family 被撤销都会失败；已撤销 token 复用会触发 family reuse 检测。
4. 旧 session 处于 pending 后，通过 `UserCredentialQueryApi.getByUserId(...)` 校验用户仍存在、允许登录且允许 refresh，并读取当前 `securityVersion`。
5. 用户不存在、`loginAllowed=false` 或 `refreshAllowed=false` 时撤销该 family 并返回 `USER_DISABLED`；失败响应不改写 cookie。
6. `securityVersionAtIssue` 与 user 当前 `securityVersion` 不一致时拒绝续期并撤销整个 family；这使密码、角色或活跃封禁变更无需反向同步调用 auth。
7. 用户校验通过后签发新的 access token，生成同 family 的 256-bit base64url replacement refresh token，再调用 `finishRotation(...)` 持久化 replacement session，同时记录当前安全版本。
8. begin 后遇到临时失败时携带本次 `rotationLeaseId` 调 `rollbackPendingRotation(...)`；旧 lease 在被新请求接管后失去写权限。rollback 失败时 auth 撤销 family。所有 refresh 失败响应都不写 `Set-Cookie`。

logout：

1. controller 从 cookie 读 refresh token。
2. `LoginApplicationService.logout(...)` 从 active、`PENDING_ROTATION` 或 terminal tombstone 识别 family 并写撤销 marker；并发 finish 必须检查 marker。
3. controller 写 clear cookie。
4. access token 不会被服务端即时拉黑，依赖短 TTL 过期。

## Captcha 和登录风控

验证码由 `CaptchaApplicationService` 管理：

发放：

1. `issue(...)` 生成无短横线 UUID 作为 captchaId。
2. code 默认从 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` 生成 4 位随机码；如果配置了 fixedCode，则使用固定码。
3. TTL 使用 `captcha.ttlSeconds`，最小 1 秒。
4. captchaId/code 先写 `CaptchaRepository`，写入失败返回 `SERVICE_UNAVAILABLE`。
5. 图片为 120x40 PNG，白底、深色文字，并加 6 条噪声线。
6. 返回 `captchaId`、PNG base64 和 TTL。

校验：

1. 空 captchaId 或空 code 直接返回 `false`。
2. `CaptchaDomainService.normalizeCode(...)` 只做 trim；大小写规则由具体 repository 负责。
3. repository 用同一个 Lua 完成取值、大小写无关比较、成功消费或失败计数递增，避免正确请求与并发错误请求穿透阈值。
4. `MATCHED` 会同时删除验证码和失败计数；`NOT_FOUND` 直接失败。
5. `MISMATCH` 的失败计数 TTL 对齐验证码剩余 TTL；达到 `captcha.maxFailures` 时脚本返回 `EXHAUSTED` 并同时删除两者，要求重新获取。
7. repository 读写异常返回验证码服务不可用。

`CaptchaDomainService.requireCaptcha(...)` 是同步规则：captchaId 或 code 缺失时抛 `CAPTCHA_REQUIRED`。当前登录主路径先在 application 层判断缺参，再调用验证码校验。

登录风控按 IP、临时输入和 authoritative subject 分阶段处理：

- `LoginRateLimitDomainService.keyOf(...)` 只 trim 输入，不尝试在 Java 中复制 MySQL Unicode 排序规则。查 user owner 前的 `auth:login:fail:input:v3-<hmac>` 使用精确输入和 `login-input` HMAC scope，只持有 provisional lease。
- user owner 通过 MySQL `utf8mb4_unicode_ci` 的 `WEIGHT_STRING` scalar 生成存在性无关的 `utf8mb4_unicode_ci:v1:<digest>`；auth 再以 `login-subject` scope 生成 `auth:login:fail:subject:v3-<hmac>`。因此排序规则别名、已知账号和未知账号使用相同的主体推导路径，无 userId 分支，也不在 Redis 暴露输入或数据库权重。
- IP 桶继续使用 `auth:login:fail:ip:v2-<hmac>` 和 `login-ip` scope。auth 获取 subject lease 后才释放 input lease，IP lease 全程不断档。
- 身份查询和密码哈希共享 `auth:login:inflight:{<完整 failure key>}:<完整 failure key>` ZSET permit；常规 key 的 hash tag 就是对应 failure String 的完整 key。每个请求使用独立 UUID token，脚本原子比较失败数与活跃 lease 之和。ZSET 的 `PEXPIREAT` 来自最大存活 score 加清理余量，混合租约配置不会由短租约截断长租约。
- `isCaptchaRequired(...)` 分别读取 IP 和 authoritative subject 计数；阈值 `<=0` 表示只要有该维度 key 就要求验证码。
- 查库前许可已原子执行阈值判断，不再使用可被并发请求一起越过的独立 `count -> acquire` 预检查。
- `recordFailure(...)` 在取得 authoritative subject 后对 IP 和该主体分别 increment，TTL 是 `windowSeconds`；非法凭据在取得主体前只累计 IP。达到上限会抛 `TOO_MANY_REQUESTS`。
- `resetSubject(...)` 在登录成功后只删除 authoritative subject 的失败计数；共享 IP 桶不删除。reset 存储异常只记录日志，不影响登录成功。
- 风控存储异常按 fail-closed 处理：判断是否需要验证码时返回 true；封锁/失败计数异常返回 `SERVICE_UNAVAILABLE`。
- Micrometer 指标名为 `auth_login_rate_limit_total`，tag 包含 `outcome` 和规范化后的 `ip_source`。

基础凭据规则：

- `AuthDomainService.requireCredentials(...)` 要求 username 和 password 非空，否则统一抛 `INVALID_CREDENTIALS`，避免暴露是用户名还是密码缺失。
- `PasswordResetDomainService.requireResetRequestEmail(...)` 要求密码重置请求必须有 email。
- `PasswordResetDomainService.requireConfirmFields(...)` 要求 resetToken 和 newPassword 同时存在。
- `RegistrationDomainService.requireRegisterFields(...)` 要求注册 username、password、email 非空；密码字段不做静默 trim，首尾空白由 user owner 密码策略拒绝。
- `RegistrationDomainService.maskEmail(...)` 用于注册验证码响应：非法邮箱原样返回；单字符 local 部分显示 `*`；两字符 local 保留首字符；更长 local 保留首尾，中间变 `***`。

## 密码重置

密码重置由 `PasswordResetApplicationService` 处理：

1. 请求阶段必须提交邮箱和验证码。
2. 验证码通过后，在查询 user owner 前按客户端 IP 和规范化邮箱分别自增请求限流计数；key 只包含用独立 HMAC secret 计算的伪名标识。
3. 邮箱不存在、用户不可用或未激活时，也消耗相同 quota、写 dummy reset token 和空收件地址 outbox，并返回已受理；handler 对空收件地址不调用 SMTP，避免通过响应或内部持久化时序差异枚举账号。
4. 生成随机 delivery ID，用独立 HMAC 密钥派生 256-bit base64url reset token；Redis key 只包含 token 的 SHA-256 ID。同一 HTTP 事务写入不含 token 明文的 `auth.password-reset-mail` outbox，payload 携带不可逆 derivation key ID，使密钥轮换后仍可从受控旧密钥 keyring 派生同一链接。SMTP 失败由通用 worker 重试，成功状态转换会原子清空 outbox payload。
5. HTTP 响应不返回 reset link。
6. 确认阶段再次校验验证码和新密码策略，再用 UUID lease 把 reset token 从 `ACTIVE` 转为 `PENDING`。
7. user owner 只在当前 `securityVersion` 等于 token 签发版本时 CAS 更新 BCrypt hash。密码策略拒绝 Unicode 首尾空白，不静默修改输入，并限制 UTF-8 最多 72 字节。
8. CAS 成功会递增 `securityVersion`；auth 撤销旧 token generation 并完成当前 lease。旧 refresh family 在下一次 refresh 比对失败时被撤销。
9. user owner 调用失败时只有 lease owner 能按 Redis 原剩余 TTL rollback；CAS stale 时 token 不恢复，而是撤销该旧 generation。后签发的新 generation 不会被旧请求误删。

## 跨域协作

同步 owner API：

- `UserCredentialQueryApi`：认证账号、取角色、取凭据。
- `UserRegistrationActionApi`：准备注册用户、创建已验证用户。
- `UserCredentialActionApi`：密码策略校验和密码更新。
- `AnalyticsIngestActionApi`：登录成功采集。

认证域不直接访问 user mapper 或 user dataobject。

## 关键代码

- `auth.controller.AuthController`
- `auth.application.LoginApplicationService`
- `auth.application.RegistrationApplicationService`
- `auth.application.RegistrationVerificationApplicationService`
- `auth.application.CaptchaApplicationService`
- `auth.application.PasswordResetApplicationService`
- `auth.application.RefreshTokenApplicationService`
- `auth.application.LoginRateLimitApplicationService`
- `auth.application.TokenFreshnessApplicationService`
- `auth.domain.service.*`
- `auth.infrastructure.jwt.JwtTokenService`
- `auth.domain.repository.RefreshTokenRepository`
- `auth.infrastructure.persistence.MyBatisRefreshTokenRepository`
- `auth.infrastructure.persistence.RedisRefreshTokenRepository`
- `auth.infrastructure.web.TokenFreshnessFilter`
- `auth.infrastructure.job.RefreshTokenCleanupJob`
- `user.application.UserCredentialApplicationService`
