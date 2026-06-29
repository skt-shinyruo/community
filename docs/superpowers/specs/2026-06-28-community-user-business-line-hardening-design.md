# Community User Business Line Hardening Design

## 背景

本设计覆盖 `backend/community-app` 中 `user` 业务线的完整硬化包。范围包括：

- 注册准备与创建
- 用户名 / 邮箱 / 密码事实
- 角色、状态、禁言、封禁
- `securityVersion` 与 refresh session 失效语义
- 头像 upload session / confirm
- 用户资料与对外 published query model
- 用户奖励语义桥接
- user policy 事件与下游协作

设计依据：

- 仓库根目录 `AGENTS.md` 的 strict DDD Tactical Layering
- `docs/handbook/architecture.md`
- `docs/handbook/security.md`
- `docs/handbook/business-logic/auth.md`
- `docs/handbook/business-logic/user.md`
- `docs/superpowers/specs/2026-06-19-auth-user-security-boundary-hardening-design.md`

本次审查确认，`user` 业务线已经形成基本 owner 边界，但仍存在六类结构性问题：

1. 注册 create 阶段信任调用方回传的 prepared material，owner 边界没有真正闭合。
2. `securityVersion` 初始化与 auth token freshness 语义不一致。
3. 用户事实校验主要停留在 controller / auth DTO 层，owner domain 缺少 authoritative 账号规则。
4. `UserProfileQueryApi` 混入 wallet owner 事实，published model 边界偏移。
5. published action/query surface 偏宽，暴露了容易被误用的半成品动作。
6. reward / event 路径存在双通道和遗留 internal surface，可靠性与演化性不够清晰。

本设计的目标不是重写 `user` 域，而是在保持现有 DDD Tactical Layering 的前提下，收紧 owner 事实、统一认证语义、缩小 published contract，并把异步协作整理成可长期演化的形态。

## 目标

- 让 `user` owner 对账号、凭据、角色、处罚、头像投影、refresh session 事实保持单一权威。
- 让注册 create 只能消费可验证的 owner-prepared artifact，不再信任外部传回的 hash 或默认头像。
- 定义清晰、可测试的 `securityVersion` 生命周期，并与 login / refresh / high-risk freshness 严格对齐。
- 把用户名、邮箱、密码复杂度这类账号事实规则下沉到 owner domain，而不是依赖 HTTP DTO 注解。
- 让 `user` published query model 只暴露 user owner 事实，不继续夹带 wallet owner 数据。
- 收紧 cross-domain API surface，只保留完成语义的动作，不暴露容易被误用的低层入口。
- 把 user reward 与 user policy 异步链路整理成单一主路径，减少重复消费和部署模式歧义。
- 补齐文档、测试和迁移约束，使后续实现可被 ArchUnit 与业务测试稳定守护。

## 非目标

- 不把 `user` 拆成独立 deployable service。
- 不重写 auth 的登录、验证码或 refresh rotation 策略。
- 不改变 avatar object 的 owner；对象事实仍属于 `community-oss`。
- 不改变 wallet 的余额与账本 owner；余额事实仍属于 `wallet`。
- 不重做 content / social / growth / IM 的产品规则，只修复它们与 user owner 的协作边界。
- 不引入全局 JWT blacklist 或每请求查库的普通鉴权模型。

## 候选方案

### 方案 A：最小补丁式修复

只修几个高风险点：

- 给新用户写正数 `securityVersion`
- 给 `createVerifiedRegistrationUser(...)` 多加几条参数校验
- 删除部分 unused API

优点：

- 改动最小，落地快。

缺点：

- 不能真正关闭注册 trust gap。
- contract 仍然过宽，后续仍容易回退到错误用法。
- profile / reward / event 这些边界偏移不会被系统性修正。

### 方案 B：Owner Hardening + Contract Narrowing

在保持模块结构不变的前提下，系统性收紧 user owner：

- 注册改成“prepare proof + create verify”
- 明确 `securityVersion` 规则
- 账号事实规则下沉到 owner domain
- 缩小 published query/action contract
- 统一 reward 与 policy 事件主路径

优点：

- 能一次性解决本次审查中的主要结构问题。
- 不需要拆服务或引入新的基础设施平台。
- 便于后续继续在同一 DDD 结构下演化。

缺点：

- 需要修改 auth/user contract 和部分下游调用方。
- 需要一次数据迁移和一轮较完整回归测试。

### 方案 C：更激进的 Auth/User 重分层

将 registration draft、prepared artifact、refresh session 等再做更大力度的重新切分，甚至为 user 增加独立 temporary fact store。

优点：

- 理论上 owner 边界最纯。

缺点：

- 变更面过大，超出本次硬化范围。
- 会把当前问题升级成一次架构迁移。

## 选定方向

采用方案 B。

理由：

- 它能修复注册、版本语义、profile contract、API surface、reward/event 主路径这几个真正会长期出问题的点。
- 它保留现有模块、应用服务和 repository 结构，不引入新的 deployable 或跨仓库拆分。
- 它可以拆成多步实施，并在每一步保持业务可运行。

## Owner 边界

### User owner

`user` 域继续拥有以下事实：

- 账号 ID、用户名、邮箱与其 canonical form
- 密码复杂度规则与密码 hash 接受边界
- 角色、账号状态、禁言、封禁
- `securityVersion`
- 头像业务投影 URL
- DB refresh session 存储事实
- user policy contract event

### Auth owner

`auth` 域继续拥有以下策略与流程：

- 登录、refresh、logout
- registration draft、验证码、captcha、password reset token
- access token claim 组装
- refresh token family rotation 与 reuse detection

### Foreign owners

- wallet 拥有余额与账户状态事实。
- OSS 拥有 avatar object、version、public file resolve 与 metadata。
- content 拥有 recent posts / recent comments。
- social / growth / IM 只能通过 user published contract 读取 user owner 事实，不能反向拥有这些事实。

## 核心设计

### 1. 注册准备与创建

#### 问题定义

当前 `prepareRegistrationUser(...)` 与 `createVerifiedRegistrationUser(...)` 虽然流程上分离，但 create 阶段仍接受调用方回传：

- `encodedPassword`
- `headerUrl`
- `username`
- `email`

这意味着 owner 没有能力证明这些字段确实来自 prepare 阶段，也无法阻止 foreign caller 绕过 prepare 直接创建 active user。

#### 目标行为

注册 create 必须只接受 owner 可验证的 prepared artifact。

推荐形态：

```text
prepareRegistrationUser(input)
  -> owner canonicalize + validate
  -> owner choose default avatar projection
  -> owner generate encodedPassword
  -> owner generate preparedProof over canonical fields
  -> return canonical fields + preparedProof + proofExpiresAt

createVerifiedRegistrationUser(command)
  -> verify preparedProof
  -> reject tampered / expired prepared material
  -> insert active user
```

#### 具体设计

- `prepareRegistrationUser(...)` 返回结构增加 `preparedProof` 与 `proofExpiresAt`。
- `preparedProof` 采用 user owner 生成的 HMAC-signed opaque proof，不新增第二个 temporary fact store。
- `preparedProof` 由 user owner 生成，覆盖以下字段：
  - `userId`
  - canonical `username`
  - canonical `email`
  - `encodedPassword`
  - canonical default `headerUrl`
  - `proofExpiresAt`
- `auth` draft 只保存 user owner 返回的 canonical fields 和 `preparedProof`，不得自行改写。
- `createVerifiedRegistrationUser(...)` 在 insert 前验证 proof，任何字段被改动都必须失败。
- create 阶段不再接受“无 proof 的直接激活”。
- proof 验证通过后仍要执行用户名 / 邮箱唯一性检查和 DB 唯一约束兜底。
- proof 过期后必须重新走 prepare，不允许 auth 或 user 侧刷新 proof TTL。

#### 默认头像

- 默认头像仍由 user owner 在 prepare 阶段决定。
- create 阶段不得接受调用方自行指定 header URL。
- 如果未来要改默认头像策略，只改 prepare owner 逻辑，不改 create API。

### 2. 用户名、邮箱与密码策略下沉

#### 问题定义

当前邮箱格式等校验主要依赖 `auth` controller DTO 注解。对于 internal caller、异步路径或绕过 HTTP 的调用，owner 没有 authoritative 规则。

#### 目标行为

账号事实规则必须由 user owner 统一定义。

#### 具体设计

新增或收敛 owner domain policy：

- `AccountIdentityPolicy` 或等价 domain service
  - `normalizeUsername(...)`
  - `validateUsername(...)`
  - `normalizeEmail(...)`
  - `validateEmail(...)`
- `PasswordPolicyDomainService`
  - 保持密码复杂度规则的唯一权威

规则要求：

- username / email 在 owner 侧统一 trim。
- email 在 owner 侧执行格式校验，而不是只依赖 `@Email`；语义保持与当前 HTTP `@Email` 约束兼容，不在本次引入新的产品格式规则。
- username 在 owner 侧至少执行 nonblank、trim、长度上限和 canonical uniqueness 规则；本次不额外引入新的字符集产品规则。
- create 阶段只接受 owner prepare 过的 canonical email / username，不再依赖调用方“自觉传对”。
- password 原始复杂度规则只在 prepare / password change 时验证；registration create 不再重新读取 raw password，而是通过 prepared proof 保证它来自有效 prepare。

HTTP DTO 注解可以保留，但它们只作为前置拦截，不再是账号事实的唯一规则来源。

### 3. `securityVersion` 生命周期

#### 问题定义

当前新用户 create 返回的 `securityVersion` 为 `0`，而 auth high-risk freshness 把 `<= 0` 视为 stale。与此同时，ban/unban 的版本变更语义也不完整。

#### 目标行为

`securityVersion` 必须成为 user owner 的单一认证授权版本事实，并满足：

- 新 active user 一创建就是正数版本。
- 所有会改变认证 / 授权结果的动作都按统一规则 bump。
- login、refresh、high-risk freshness 都以该事实为准。

#### 版本规则

以下动作必须 bump `securityVersion`：

- active user create
- 角色变化
- 密码 hash 变化
- 会影响登录能力的 status 变化
- active ban 生效
- active ban 延长或缩短且仍处于 active
- active ban 解除

以下动作不 bump `securityVersion`：

- mute / unmute
- avatar confirm
- 普通资料读取

#### 具体设计

- user create 在 insert 后立即获得正数 `securityVersion`，并通过 `UserCredentialView` 返回给 auth。
- auth 登录和 refresh 签发 access token 时，总是使用 user owner 返回的当前版本。
- high-risk freshness 保持“JWT claim 与 owner 当前版本相等才 accepted”的模型，不再依赖 `0` 作为特殊合法值。

### 4. refresh session 撤销语义

#### 目标行为

下列 user owner 动作必须撤销该用户全部 refresh sessions：

- 密码更新成功
- 角色更新成功
- 会影响登录能力的 status 更新成功
- active ban 生效、调整、解除

#### 具体设计

- refresh session 撤销仍由 user application 在事务语义内编排，不下放给 auth 直接操作。
- published `UserRefreshTokenSessionActionApi` 不再对 foreign caller 暴露 `revokeByUserId(...)`。
- auth 对 refresh session 的 foreign port 只保留：
  - `store`
  - `find`
  - `consume`
  - `revoke`
  - `revokeFamily`
  - `deleteExpiredBefore`

这样可以避免 future caller 直接把 user owner 的 session 事实当成通用远程管理接口。

### 5. 角色与处罚边界

#### Role

`AdminUserApplicationService` 继续作为角色变更唯一入口，但要满足：

- role type 由 owner domain 明确校验，不接受任意整数。
- 禁止管理员把自己降级为非管理员。
- 同角色更新直接返回，不重复写库。
- 变更成功后：
  - bump `securityVersion`
  - revoke refresh sessions
  - 写审计日志

#### Moderation

`UserModerationApplicationService` 继续拥有 `muteUntil` / `banUntil` 的写语义，但需要新增一层明确的 security impact 判定：

```text
old moderation state
  + action
  + now
  -> next moderation state
  -> security impact decision
```

security impact decision 负责回答：

- 是否影响 login / refresh
- 是否需要 bump `securityVersion`
- 是否需要 revoke refresh sessions

这样可以把 ban/unban 语义从“散落在 application 里的条件判断”收敛为 owner policy。

### 6. 头像业务投影

头像主设计保持不变：

- 只能本人发起 upload session
- confirm 只接受 `objectId`
- user 回源 OSS metadata
- 只在 `community-app/user/avatar/{userId}`、`PUBLIC`、`ACTIVE` 且存在 canonical public URL 时写 `header_url`

本次不改业务形态，只补强约束：

- 将“只接受 objectId，不接受 URL/path”继续固化到 DTO 与 application boundary。
- 为 metadata 缺失、非 PUBLIC、非 ACTIVE、无 `publicUrl` 等失败路径补充测试。

### 7. Published query contract 收口

#### 问题定义

`UserProfileQueryApi` 当前通过 `UserProfileView` 对外暴露：

- `walletBalance`
- `walletStatus`

这会把 wallet owner 事实长期绑定到 user published model。

#### 目标行为

user published query model 只暴露 user owner 事实。

#### 具体设计

- `UserProfileView` 缩减为：
  - `userId`
  - `username`
  - `headerUrl`
  - `type`
  - `status`
  - `createTime`
- `UserReadApplicationService#getProfile(...)` 不再回源 wallet owner 拼装 published profile。
- 如果下游需要“用户资料 + 钱包状态”的聚合视图，必须在 user owner 外部定义专门 aggregation service / BFF，而不是继续扩展 `user.api.query`.

### 8. Password / Credential API surface 收紧

#### 问题定义

`UserCredentialActionApi` 当前暴露 `updatePassword(...)`。该动作只改 hash，不表达 refresh session 失效语义，是典型的半成品 foreign action。

#### 目标行为

foreign caller 只能调用完成语义的动作。

#### 具体设计

- 对外保留：
  - `validatePasswordPolicy(...)`
  - 完成密码变更且同步撤销 refresh sessions 的动作
- 对外移除或废弃：
  - `updatePassword(UUID, String)`

如果未来需要“已登录用户主动改密”，应定义完整语义的 owner action，例如：

```text
changePasswordAndRevokeRefreshSessions(actorUserId, oldPassword, newPassword)
```

而不是复用一个低层 write method。

### 9. Reward 语义桥接与事件主路径

#### 问题定义

当前 user reward 有三类入口同时存在：

- `UserRewardActionApi`
- comment reward 本地 outbox handler
- `UserRewardKafkaListener`

其中 `UserRewardActionApi` 没有生产主调用方，comment reward 又同时具备本地 outbox 和 Kafka path，容易让正确性建立在 wallet 幂等而不是清晰链路上。

#### 目标行为

user reward 只保留一个 canonical 异步主路径。

#### 具体设计

- 在默认生产式部署中，content / social contract event Kafka backbone 作为 user reward 的 canonical source。
- `UserRewardKafkaListener` 继续承担 reward projection 入口。
- `CommentRewardOutboxEnqueuer` / `CommentRewardOutboxHandler` 作为过渡实现退役。
- `UserRewardActionApi` 在当前仓库生产代码中视为 dead surface；实施时若确认仍无真实 caller，则在本轮硬化中直接移除。若发现 caller，caller 必须先迁移到 canonical async path，再删除该 API。

奖励语义本身保持不变：

- 发帖 `+10`
- 评论 `+2`
- 点赞创建 `+1`
- 点赞移除 `-1`

wallet requestId 继续承担最终幂等，但系统设计不再故意保留多条生产主路径。

### 10. User policy event internal surface 收口

当前 `user` 内同时存在：

- `UserPolicyEventPublisher`
- `UserEventPublisher`
- `LocalUserPolicyEventPublisher`
- `LocalUserEventPublisher`

其中 `UserEventPublisher` / `LocalUserEventPublisher` 已不在主业务写路径上承担 owner event 入口。

目标行为：

- user policy contract event 只保留一套 owner-facing publish port。
- 未被主路径使用的 legacy port / adapter 退役。
- 文档和测试只围绕一条真实主路径维护。

## 数据与契约变更

### 新增 / 调整字段

- registration prepared result / draft
  - `preparedProof`
  - `proofExpiresAt`
- user credential view
  - 保证 `securityVersion` 为正数有效值

### 缩减字段

- `UserProfileView`
  - 删除 `walletBalance`
  - 删除 `walletStatus`

### 废弃接口

- `UserCredentialActionApi.updatePassword(...)`
- `UserRefreshTokenSessionActionApi.revokeByUserId(...)`
- `UserRewardActionApi`，若确认无生产 caller
- 未使用的 legacy `UserEventPublisher` surface

## 数据迁移与兼容性

### `securityVersion` 回填

需要一次 DB migration：

1. 找出 `security_version` 为 `0` 或空的用户行。
2. 按统一规则回填为正数版本。
3. 将全局 security version counter 推进到不小于当前最大值。

兼容性语义：

- 迁移前签发的旧 `security_version=0` access token 在高风险入口会被视为 stale。
- 旧 token 不需要额外兼容；用户重新登录或 refresh 后拿到新版本即可。

### 注册 draft

auth 的 registration draft repository 需要兼容新字段：

- 在 prepare 后保存 `preparedProof` 和 `proofExpiresAt`
- verify 阶段把 proof 一并传回 user owner create

## 测试与文档要求

### 必补测试

- registration create 拒绝无 proof 或 proof 被篡改的 prepared material
- registration create 不能绕过 prepare 接受弱密码的 BCrypt hash
- 新 active user create 返回正数 `securityVersion`
- high-risk freshness 接受新正数版本，拒绝旧版本与 `0` 版本
- role update 成功后 bump version + revoke sessions
- active ban 生效、调整、解除都按定义 bump version + revoke sessions
- mute / unmute 不影响 login / refresh version
- `UserProfileQueryApi` 不再暴露 wallet 字段
- comment reward 只经 canonical async path 生效
- avatar confirm 在 metadata 非法时绝不写 `header_url`
- 废弃 API 的 adapter 与调用方移除后，编译与集成测试通过

### 文档更新

至少更新：

- `docs/handbook/business-logic/user.md`
- `docs/handbook/business-logic/auth.md`
- `docs/handbook/security.md`
- `docs/handbook/core-logic-index.md`

如果 role / moderation / refresh 语义变更影响现有 handbook 其他章节，也必须同步修正文档，不允许设计与 handbooks 长期漂移。

## 实施阶段建议

建议按以下顺序落地：

1. 注册 proof + `securityVersion` 初始化 + auth freshness 对齐
2. role / status / moderation 的 version 与 session 失效矩阵
3. published profile contract 收口与 wallet 字段移除
4. password / refresh session foreign API 收口
5. reward 主路径统一与 legacy event / API 清理
6. handbook、测试、遗留 internal surface 清理

这样可以先关闭最高风险安全口子，再做 contract 缩减和链路整理。

## 预期结果

完成本设计后，`user` 业务线应满足以下状态：

- 注册 create 无法绕过 owner prepare。
- 新老账号都具备一致、可测试的 `securityVersion` 语义。
- role / password / status / ban 对 refresh 和 high-risk access token 的影响清晰一致。
- user published model 不再扩散 wallet 事实。
- foreign caller 只能调用完成语义的 owner action。
- avatar confirm 继续保持 object-owner 与 projection-owner 分离。
- reward 与 policy event 链路有单一主路径，不再依赖“双通道 + 幂等兜底”的模糊正确性。

这份 spec 完成后，后续 implementation plan 应按上述实施阶段拆解，不再把 `user` 业务线作为单一大改动一次性落地。
