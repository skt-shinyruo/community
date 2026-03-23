# Registration Email Code Auto-Login Design Spec

**Date:** 2026-03-23
**Status:** Approved for planning
**Owner:** Codex

---

## 1. 背景

当前项目的注册激活链路是：

1. 前端注册页提交用户名、密码、邮箱和图形验证码
2. 后端创建 `status=0` 的未激活用户，并生成 `activationCode`
3. 系统通过邮件发送激活链接
4. 用户访问 `/api/auth/activation/{userId}/{code}` 完成激活
5. 用户再回到登录页，使用用户名和密码登录

这条链路在功能上可用，但存在明显问题：

- 激活动作依赖邮件中的链接跳转，邮件客户端、网关和前端路由之间耦合较重
- 注册成功和真正可登录之间被拆成两步，用户路径长
- 前端为本地联调暴露 `activationLink`，不适合作为长期主流程
- 现有激活接口只返回 `0/1/2` 状态，不适合直接承接“验证成功后自动登录”的体验

用户已经明确确认新的目标路径：

- 注册后通过邮箱发送验证码
- 用户输入验证码后完成激活
- 验证成功后直接登录，不再要求用户手动回到登录页再次输入密码

本设计的目标是在尽量复用现有认证基础设施的前提下，把“邮箱激活链接”替换为“邮箱验证码自动登录”。

---

## 2. 目标

### 2.1 核心目标

1. 注册成功后，系统向用户邮箱发送一次性验证码，而不是激活链接
2. 用户输入验证码后，后端完成账号激活并直接签发登录态
3. 登录态的签发逻辑与用户名密码登录保持一致，继续沿用现有 access token + refresh cookie 模型
4. 未完成邮箱验证的账号仍然不可通过密码登录
5. 删除旧的激活链接逻辑，不保留兼容分支
6. 新流程对现有登录、刷新、登出、找回密码能力不产生回归

### 2.2 用户体验目标

- 用户完成注册后无需离开当前注册流程即可完成邮箱验证
- 验证成功后自动进入社区或原目标页面
- 验证码错误、过期、重复消费、重复发送等场景都有清晰反馈

### 2.3 非目标

本轮设计不包括以下能力：

- 通用“邮箱验证码登录”体系
- 纯邮箱免密登录
- 手机验证码登录
- 密码登录的替换或废弃
- 找回密码流程改造成验证码模式

密码登录仍然保留，邮箱验证码仅服务于“新用户注册后的激活并自动登录”场景。

---

## 3. 已确认产品方向

以下方向已由用户明确确认：

- 激活链接改为邮箱验证码
- 验证成功后直接登录
- 用户目标路径是：
  - 注册
  - 收到邮箱验证码
  - 输入验证码
  - 激活成功并直接进入登录态

---

## 4. 现有系统事实与约束

### 4.1 当前注册与激活事实

当前后端注册逻辑位于 `RegistrationService.register()`，其行为是：

- 校验图形验证码
- 校验 `auth.registration.activation-base-url`
- 创建未激活用户
- 生成激活链接
- 调用 `MailService.sendActivationMail(email, activationLink)`
- 在 `RegisterResponse` 中返回 `activationIssued`，并在本地/测试环境可选回传 `activationLink`

当前激活逻辑位于 `RegistrationService.activate(userId, code)`，其行为是：

- 校验用户和 `activationCode`
- 若用户仍为未激活状态，则更新 `status=1`
- 返回 `ACTIVATION_SUCCESS / ACTIVATION_REPEAT / ACTIVATION_FAILURE`

### 4.2 当前登录事实

当前 `AuthService.login()` 已经完整处理以下能力：

- 登录限流与图形验证码兜底
- 用户名密码校验
- 未激活/禁用用户拦截
- access token 生成
- refresh token 签发与 cookie 回写

因此，“邮箱验证码验证成功后直接登录”不应该再复制一套 JWT 和 refresh cookie 逻辑，而应该复用 `AuthService` 内部的签发能力。

### 4.3 当前邮件与一次性存储事实

当前认证模块已经具备两类可复用基础设施：

- `MailService`
  - SMTP 实现负责真实发信
  - Log 实现负责本地/测试环境降级输出
- `PasswordResetTokenStore`
  - 提供一次性 `store(token, userId, ttl)` 和 `consume(token)` 语义
  - 具备 Redis 和内存两套实现
  - 已经使用原子消费保证 token 只能使用一次

这说明“注册验证码”不需要从零发明存储和一次性消费模式，可以沿用同样的抽象思路。

### 4.4 当前前端事实

当前前端认证页面包含：

- `/auth/register`
- `/auth/login`
- `/auth/password/reset`
- `/auth/activation/:userId/:code`

其中注册页当前行为是：

- 注册成功后显示“请前往邮箱完成激活”
- 若响应里包含 `activationLink`，则允许本地跳转到激活页

现有注册页已经具备图形验证码和提交反馈区，因此适合演进为“两段式注册流程”，而不必强制新建一套独立页面。

---

## 5. 方案对比

### 5.1 方案 A：保留注册接口，新增注册验证码发送与验证接口

流程：

1. `POST /api/auth/register` 创建未激活用户并发送邮箱验证码
2. 前端进入验证码输入阶段
3. `POST /api/auth/register/code/verify` 提交验证码
4. 后端验证成功后激活用户并直接签发登录态

优点：

- 与目标路径完全一致
- 改动边界清晰，注册验证码和密码登录各司其职
- 可复用现有注册、邮件、一次性 token store、JWT 和 refresh cookie
- 前端变更集中在注册流程，不会污染普通登录页

缺点：

- 需要新增验证码存储抽象、DTO 和接口
- 注册接口响应体需要调整为“验证码已发送”语义

### 5.2 方案 B：新增独立“注册验证页”，通过邮箱 + 验证码完成激活登录

流程：

1. 注册后跳转到独立页面
2. 用户输入邮箱和验证码
3. 后端激活并登录

优点：

- 页面职责清晰
- 后续扩展为“邮箱验证码登录”时迁移成本较低

缺点：

- 用户路径更长
- 会过早把“注册验证”和“邮箱免密登录”混为一谈
- 需要新的路由、页面和状态传递

### 5.3 方案 C：直接改造旧激活接口，使其接受验证码并返回登录态

优点：

- 表面上沿用了“activation”概念

缺点：

- 旧接口当前语义是状态查询式激活，不是登录式确认
- 返回值从 `0/1/2` 扩成登录态会让接口语义混乱
- 与前端邮箱验证码输入模型并不匹配

### 5.4 推荐方案

采用方案 A。

原因：

- 它最符合用户明确确认的目标路径
- 它与现有代码边界最一致
- 它能在不引入“第二套登录体系”的情况下完成自动登录体验

---

## 6. 总体设计

### 6.1 新注册流程

新的注册链路改为：

1. 用户在注册页填写用户名、密码、邮箱、图形验证码
2. `POST /api/auth/register` 校验输入并创建未激活用户
3. 后端生成一次性邮箱验证码并发送邮件
4. 前端进入“输入邮箱验证码”阶段
5. 用户输入验证码后调用 `POST /api/auth/register/code/verify`
6. 后端验证成功后：
   - 原子消费验证码
   - 激活用户
   - 签发 access token
   - 签发 refresh cookie
7. 前端写入登录态并跳转到社区或原始重定向地址

### 6.2 用户状态约束

- 新注册用户仍然先以 `status=0` 创建
- 只有邮箱验证码验证成功后，才会变为 `status=1`
- `AuthService.login()` 对 `status=0` 的拦截逻辑继续保留
- 已激活用户不应再依赖注册验证码流程获取新的登录态
- 注册流程不再生成、返回、校验 `activationCode`

### 6.3 验证码模型

注册验证码采用以下约束：

- 一次性消费，验证成功后立即失效
- 仅最后一次发送的验证码有效
- 具备 TTL，过期后不可使用
- 验证码长度固定，优先使用 6 位数字码
- 邮件内容直接展示验证码，不再依赖跳转链接

---

## 7. 后端设计

### 7.1 配置设计

新增 `auth.registration.code` 相关配置，至少包括：

- `ttl-seconds`
- `store`
- `resend-cooldown-seconds`
- `expose-code`

说明：

- `store` 语义与密码重置保持一致，支持 `redis` / `memory`
- `expose-code` 仅用于本地/测试联调，生产环境必须关闭

需要同步删除以下旧配置与启动校验：

- `auth.registration.activation-base-url`
- `auth.registration.expose-activation-link`
- `AuthStartupValidator` 中对 `AUTH_ACTIVATION_BASE_URL` 的强依赖

### 7.2 存储抽象

新增 `RegistrationCodeStore`，职责与 `PasswordResetTokenStore` 相似，但语义更贴合“按用户发送验证码并校验次数/状态”。

推荐接口应直接表达“比对并消费”的业务语义，而不是先读再删。建议接口类似：

- `void save(int userId, String code, Duration ttl)`
- `VerifyResult verifyAndConsume(int userId, String code)`
- `void invalidate(int userId)`

其中 `VerifyResult` 至少区分：

- `SUCCESS`
- `NOT_FOUND`
- `EXPIRED`
- `MISMATCH`
- `TOO_MANY_ATTEMPTS`

设计要求：

- `save()` 覆盖旧值，只保留最后一次发送的验证码
- `verifyAndConsume()` 在验证码正确时原子消费
- 验证码错误时不能把正确验证码提前消费掉
- 失败次数达到阈值后，当前验证码作废并要求重新发送

Redis 实现可以固定 key 前缀存储序列化后的验证码条目。
内存实现通过 `ConcurrentHashMap` 保存验证码、过期时间和失败次数。

### 7.3 领域服务拆分

新增 `RegistrationVerificationService`，负责：

- 生成验证码
- 存储验证码
- 发送验证码邮件
- 处理重发
- 验证验证码
- 验证成功后触发激活与自动登录

现有 `RegistrationService` 聚焦为：

- 注册输入校验
- 创建用户
- 调用验证码签发

这样可以避免 `RegistrationService` 同时承担“创建用户”和“验证后登录”两种职责。

同时需要删除旧的激活链路逻辑：

- `RegistrationService.activate(...)`
- `RegistrationService` 中的 `ACTIVATION_*` 常量
- `InternalUserService.activate(...)`
- `InternalUserService` 中的 `ACTIVATION_*` 常量
- `InternalUserService.register()` 中 `activationCode` 的生成与写入

### 7.4 登录态签发复用

需要把 `AuthService.login()` 中“根据已确认身份的用户对象签发 access token + refresh cookie”的逻辑抽成可复用内部方法，例如：

- `issueLoginResult(User user)`

用户名密码登录在认证成功后调用它。
邮箱验证码验证在激活成功后也调用它。

这样可以保证：

- access token claims 逻辑一致
- refresh token 签发逻辑一致
- 后续如需修改 JWT claims 或 cookie 策略，只需改一个地方

### 7.5 接口设计

#### 7.5.1 调整注册接口

`POST /api/auth/register`

请求体保持当前字段：

- `username`
- `password`
- `email`
- `captchaId`
- `captchaCode`

响应体改为：

- `userId`
- `emailCodeIssued`
- `maskedEmail`
- `debugEmailCode`（仅本地/测试联调用，受配置控制）

说明：

- `activationIssued` 和 `activationLink` 字段从 `RegisterResponse` 中删除
- `maskedEmail` 用于前端提示“验证码已发送到 xxx”

#### 7.5.2 新增重发接口

`POST /api/auth/register/code/resend`

请求体：

- `userId`
- `captchaId`
- `captchaCode`

说明：

- 重新发送验证码是高频可被滥用动作，建议继续要求图形验证码
- 如果后续要做冷却倒计时，服务端仍然必须保留频控保护，不能只靠前端

响应体：

- `issued`
- `maskedEmail`
- `debugEmailCode`（仅本地/测试环境）

#### 7.5.3 新增验证并登录接口

`POST /api/auth/register/code/verify`

请求体：

- `userId`
- `code`

响应体：

- 与当前 `POST /api/auth/login` 一致，返回 `LoginResponse`
- 同时由响应头设置 refresh cookie

服务端行为：

1. 根据 `userId` 读取用户
2. 校验用户存在且尚未激活
3. 原子校验验证码并仅在匹配时消费
4. 验证码一致时激活用户
5. 调用复用后的登录态签发方法
6. 返回 access token，并设置 refresh cookie

### 7.6 邮件设计

`MailService` 由“发送激活链接”改为“发送注册验证码”。

推荐修改为：

- `void sendRegistrationCodeMail(String toEmail, String code)`
- `void sendPasswordResetMail(String toEmail, String resetLink)`

`sendActivationMail(String toEmail, String activationLink)` 从接口和实现中删除。

邮件内容改为：

- 标题：`注册验证码`
- 正文：说明这是本次注册的验证码、有效期、非本人忽略
- 验证码应以高可读方式呈现

日志降级实现也应改为输出 code，而不是 activationLink。

### 7.7 旧逻辑删除范围

旧激活链接逻辑不保留兼容分支，实施时应直接删除以下后端代码：

- `AuthController` 中 `GET /api/auth/activation/{userId}/{code}`
- `AuthSecurityRules` 中对 `/api/auth/activation/*/*` 的放行规则
- `RegistrationService.buildActivationLink(...)`
- `RegisterResponse.activationIssued`
- `RegisterResponse.activationLink`
- `RegistrationProperties.activationBaseUrl`
- `RegistrationProperties.exposeActivationLink`
- `MailService.sendActivationMail(...)`
- `SmtpMailService` 中激活链接邮件模板与实现
- `LogMailService` 中激活链接日志输出
- `User.activationCode` 及其读写路径
- 不再需要的内部 DTO，例如 `InternalActivateRequest`
- 所有围绕 `activationCode` 的无效测试、断言和常量

如果仓库内维护数据库迁移，则 `activation_code` 列也应在同一轮移除；如果迁移由外部流程管理，本轮至少必须做到应用代码完全不再读写该列。

---

## 8. 前端设计

### 8.1 注册页改为两段式流程

推荐继续使用现有 `/auth/register` 页面，不新增独立路由。

页面状态分为两个阶段：

- 阶段一：填写注册信息
- 阶段二：输入邮箱验证码

阶段一提交成功后：

- 清空密码和图形验证码输入
- 保留 `userId` 和必要展示信息
- 显示“验证码已发送到邮箱”
- 展示验证码输入框、确认按钮、重发按钮

### 8.2 验证成功后的前端行为

前端调用验证接口成功后：

1. 保存 access token
2. 调用 `/api/auth/me` 拉取当前登录用户
3. 写入 auth store
4. 跳转到：
   - `redirect` 参数指定页面，若存在且安全
   - 否则跳到帖子列表页

这与当前密码登录成功后的前端收口行为保持一致。

### 8.3 旧前端激活页删除

现有前端激活页与相关调试入口直接删除，不保留兼容路由。

需要删除的前端旧逻辑包括：

- `frontend/src/views/ActivationView.vue`
- `frontend/src/router/index.js` 中 `/auth/activation/:userId/:code` 路由
- `frontend/src/api/services/authService.js` 中 `activation(...)` API
- `frontend/src/views/RegisterView.vue` 中 `activationLink` 调试展示与跳转逻辑

### 8.4 前端提示与状态

前端需要补齐以下状态反馈：

- 注册成功，验证码已发送
- 验证码错误
- 验证码过期
- 验证码已失效，请重新发送
- 重发成功
- 账号已激活，请直接登录

如果启用联调调试字段，调试码只允许在本地/测试环境展示，不得在生产页面渲染。

---

## 9. 错误处理与安全设计

### 9.1 错误处理

注册验证码验证失败至少要区分以下场景：

- 用户不存在
- 用户已激活
- 验证码不存在
- 验证码错误
- 验证码过期

其中对前端暴露的错误文案不必一一精确区分，但服务端错误码应足够稳定，以便前端做交互分支。

### 9.2 防滥用

重发验证码必须考虑以下风险：

- 恶意频繁触发发信
- 通过重发接口轰炸某邮箱
- 无限试错验证码

最小可接受防护包括：

- 重发接口要求图形验证码
- 服务端 resend cooldown
- 验证码 TTL
- 验证失败次数达到阈值后作废当前验证码，要求重新发送

### 9.3 幂等与并发

验证码校验必须是原子操作，确保：

- 同一个验证码在并发请求下只能成功一次
- 成功激活后不会重复签发多个有效验证码结果
- 错误验证码不会提前消耗正确验证码

用户状态更新也要考虑幂等：

- 若用户在消费验证码前已被其他请求激活，当前请求应返回“已激活”语义，而不是再次下发登录态

---

## 10. 测试设计

### 10.1 后端测试

至少覆盖以下测试：

- 注册成功后会生成并发送注册验证码
- 注册接口在本地/测试环境可按配置回传调试验证码
- 验证成功会激活用户并返回登录态
- refresh cookie 会随验证成功响应一起回写
- 验证码错误时不会激活用户
- 验证码过期时不会激活用户
- 验证码被消费一次后不可重复使用
- 并发验证时只能有一个请求成功
- 重发后旧验证码失效，新验证码可用
- 未激活用户仍不可通过密码登录

测试层次建议包括：

- `RegistrationVerificationService` 单元测试
- `RegistrationCodeStore` 的内存/Redis 测试
- `AuthController` 或 WebMvc 集成测试，覆盖 cookie 和响应体

### 10.2 前端测试

至少覆盖以下测试：

- 注册成功后页面切到验证码阶段
- 验证成功后 access token 写入 store
- 验证成功后会拉取 `/me` 并跳转
- 验证失败时页面展示错误，不会误写入登录态
- 重发成功后展示反馈
- 若存在本地调试码字段，仅在对应环境展示

---

## 11. 实施边界与顺序

推荐实施顺序：

1. 先补后端验证码 store、服务、DTO、接口和邮件改造
2. 抽取 `AuthService` 的公共签发逻辑，并同步删除旧激活接口、配置和常量
3. 写后端测试并跑通，确保旧激活路径已不存在
4. 再改前端注册页为两段式流程，并同步删除旧激活页、路由、API 和调试链接
5. 跑前后端回归测试，确认仓库内不再存在激活链接主流程代码

这样可以保证：

- 后端先具备完整能力
- 前端接入时接口边界已经稳定
- 出问题时可分层回退

---

## 12. 风险与开放问题

### 12.1 主要风险

- 如果验证码只按 `userId` 存储，需要保证前端可靠保留 `userId`
- 如果不加 resend cooldown，邮件发送接口可能被滥用
- 如果验证成功后对“已激活用户”仍重复签发 token，幂等边界会不清晰
- 删除旧逻辑后，所有依赖 `AUTH_ACTIVATION_BASE_URL`、`activationLink` 或激活页路由的测试与联调脚本都必须同步更新

### 12.2 设计结论

本设计采用以下结论：

- 保留密码登录，新增注册验证码验证登录能力
- 注册验证码仅服务于未激活用户
- 验证成功后直接签发登录态
- 前端采用注册页内两段式流程
- 旧激活链接接口、页面、配置和 DTO 全量删除，不保留兼容分支
