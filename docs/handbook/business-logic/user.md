# User 用户业务逻辑

用户域拥有用户账号事实、资料事实、凭据事实、角色事实、处罚状态和头像业务规则；头像对象本身由 `community-oss` 承担。auth、content、social、growth、IM 等域都围绕 user owner 的同步 API 或事件协作。

## Owner / SSOT

- `user` owns `user` 表中的账号、邮箱、密码 hash、头像业务投影、角色、状态、禁言和封禁时间；头像对象本身在 `community-oss`。
- 钱包余额和奖励账本由 `wallet` owner 拥有；`user` 不再持有 profile score 字段。
- refresh token 策略和 session 存储事实都属于 `auth`；user 只发布当前 `securityVersion` 供登录、refresh 和高风险请求校验。
- 用户最近帖子/评论不是 user 主事实，来自 content owner 查询聚合。
- IM 的本地 policy projection 不是用户处罚主事实；user owner 通过 snapshot 和事件向 IM 投影。

## 入口

HTTP：

- `POST /api/users/batch-summary`
- `POST /api/users/{userId}/avatar/upload-sessions`
- `PUT /api/users/{userId}/avatar`
- `GET /files/**`（由 `community-oss` 提供公共文件读取）
- `GET /api/users/admin/search`
- `POST /api/users/admin/role`

内部 API：

- auth 调 user 完成注册、登录认证和密码更新。
- profile/content/social/growth 调 user 做资料、摘要、存在性或处罚判断。
- IM snapshot 拉取用户 policy。

## 数据流

用户域的数据流围绕“用户事实被谁读写”展开：

1. 资料读取：`UserReadApplicationService` 和 user owner query 只读取 `user` 主事实。用户主页由 `profile.application.UserProfileQueryApplicationService` 回源 user/social/content/growth 聚合，最近内容不是 user 表冗余字段。
2. 头像更新：`UserAvatarApplicationService` 校验 actor 只能改本人头像，先通过 OSS prepare upload 获得 `objectId/versionId` 和上传指令，前端直传 OSS 后以 `objectId` 确认；user 回源 OSS metadata 校验对象归属，把 canonical OSS public URL 写回 `user.header_url`。blob 和 version 由 `community-oss` owning。
3. 凭据协作：auth 登录通过 `UserCredentialQueryApi.authenticate(...)` 查询并校验密码；密码重置通过 `UserCredentialActionApi` 更新 BCrypt hash。更新同时递增 `securityVersion`，但 user 不直接访问或撤销 auth refresh rows。
4. 注册用户创建：auth 只保存 draft；验证码通过后调用 user owner 创建 active 用户。user 写入 `user` 行后发布 user policy changed，驱动 IM policy projection 识别用户存在性。
5. 处罚和角色：管理员或治理动作进入 user application，更新 `muteUntil`、`banUntil` 或 `type`。处罚变化经 `eventbus.user -> user.events -> projection.im.policy` 最终刷新 realtime 本地 projection；角色变化要等 access token 重新签发后才体现在前端权限里。
6. 奖励投影：content/social 事件由 wallet 的 `WalletRewardKafkaListener` 直接消费，user 不承担奖励语义桥接或余额事实。

`securityVersion` 是 user owner 的认证授权版本。角色调整、密码更新以及新增或延长活跃账号级封禁会递增该版本；这些路径不跨域同步撤销 auth refresh rows。auth refresh 会比较 session 的 `securityVersionAtIssue`，不匹配时拒绝并撤销 family。`muteUntil` 只影响发言能力，不影响登录或 refresh。

## 用户资料和摘要

`UserReadApplicationService` 提供基础用户读取能力：

- 按 ID 查询用户摘要。
- 按用户名查询摘要。
- 按邮箱查询摘要。
- 批量按 ID 查询摘要，并保持输入顺序。
- 校验用户是否存在。

读取参数规则由 `UserReadDomainService` 收敛：

- `assertValidUserId(...)` 要求 userId 非空。
- `normalizeUsername(...)` trim 后要求非空。
- `normalizeEmail(...)` trim 后要求非空。

旧 `user.score` 和基于 score 的 profile level 已退休。用户主页只暴露 growth 的签到等级字段；可用余额必须从 wallet owner 查询。

用户主页不在 user 域内聚合。`UserProfileQueryApi` 只发布 user owner 的基础资料；`profile` 域再组合 social 计数、content 最近内容和 growth 等级。完整链路见 [profile.md](profile.md)。

## 头像流程

头像是 upload session + confirm 流程，对象存储由 OSS owner 承担：

1. `createUploadSession(actorUserId, userId, command)`：只能本人操作，校验文件名、MIME 和大小，并向 OSS prepare upload。
2. 前端按返回的 URL、method、fields、headers 直接提交到 OSS 上传入口。
3. `updateAvatar(actorUserId, userId, objectId)`：只能本人确认，回源 OSS metadata 校验 `usage/owner/visibility/status` 后，把 canonical OSS public URL 写回 `user.header_url`。

规则：

- 非本人操作返回 forbidden。
- confirm 阶段不接受 URL 或路径，只接受 `objectId`。
- OSS metadata 必须属于 `community-app/user/avatar/{userId}`，且为 `PUBLIC/ACTIVE`。
- 上传失败不能更新头像。
- 前端只执行 upload session 返回的 URL、method、fields、headers 和约束，不读取 storage provider、bucket 或物理路径。
- 旧 `UserFileApplicationService` 和本地/R2 avatar storage provider 已退休，community-app 不再拥有公开文件读取入口。

文件读取不再由 `community-app` user 域承接；gateway 将 `/files/**` 路由到 `community-oss`，由 OSS owner 完成 canonical URL 解析和 blob 读取。

## 凭据与密码

`UserCredentialApplicationService` 负责 user owner 内的凭据逻辑：

- `authenticate(username, password)`：返回认证成功、无效凭据或账号禁用。
- `getByUserId(...)`：给 auth refresh 或当前用户读取凭据。
- `findByEmailOrNull(...)`：密码重置时按邮箱定位用户。
- `updatePassword(...)`：写 BCrypt 新密码。
- `validatePasswordPolicy(...)`：调用密码复杂度规则。
- `authoritiesOf(...)`：根据 `user.type` 生成角色。

密码策略由 `PasswordPolicyDomainService` 控制。用户密码持久化格式为 BCrypt。

## 注册用户创建

当前注册由 auth 发起，user owner 只负责用户事实：

- `prepareRegistrationUser(...)`：验证注册输入，前置检查用户名/邮箱冲突，准备用户名/邮箱/密码 hash/默认头像，不写库。
- `createVerifiedRegistrationUser(...)`：验证码通过后插入 active 用户。

创建 active 用户后，user owner 发布 user policy changed，通知 IM 用户存在性/策略投影发生变化。

## 用户处罚状态

`UserModerationApplicationService` 管理禁言和封禁：

- `getModerationState(userId)`：读取用户处罚状态。
- `scanModerationStatesAfterId(...)`：给 IM snapshot 分页扫描用户 policy。
- `applyModeration(command)`：应用禁言、封禁或解除类动作。

处罚规则在 `UserModerationDomainService`：

- action 必须非空且属于允许动作。
- duration 决定处罚到期时间。
- 返回新的 `UserModerationStatus`。

写入后：

1. 更新 user 表中的 `muteUntil` / `banUntil`。
2. 发布 `UserPolicyChanged`。
3. IM outbox 把变化投递给 realtime。
4. content 写路径同步回源 user owner 判断是否可发言。

`muteUntil` 只影响发言能力；`banUntil` 是账号级暂停，影响 login / refresh 和需要鉴权的敏感写入口。新增或延长活跃账号级封禁会递增 `securityVersion`，旧 refresh family 在下次续期时由 auth 拒绝。

## 管理员角色

`AdminUserApplicationService` 管理用户角色调整：

1. 管理员按 userId、username 或 email 搜索用户。
2. 修改角色必须带目标用户、目标 type、reason 和 confirm。
3. 禁止管理员把自己降级为非管理员。
4. 找不到目标用户失败。
5. 同角色更新直接返回，不重复写库。
6. 变更成功写 `user.type`，并通过 audit port 记录审计日志。

角色决定 JWT authorities，但已签发的 access token 不会立即变化，需要重新签发后体现。角色调整会同时提升 `securityVersion`；高风险入口立即按 freshness 拒绝旧 access token，旧 refresh family 在下次续期时被 auth 拒绝。

## 用户事件发布

`OutboxUserPolicyEventPublisher` 是 user owner 的唯一 policy contract event adapter：

1. application 只依赖 domain `UserPolicyEventPublisher` port。
2. 发布 policy change 时，adapter 包装为 `UserContractEvent`。
3. eventId 由 userId 和正数 owner version 确定性生成。
4. type 固定为 `USER_POLICY_CHANGED`。
5. payload 是 `UserPolicyChangedPayload`。
6. adapter 与 user 主事实同事务写 `eventbus.user`；`UserEventKafkaOutboxHandler` 经 dispatch application 发布 `user.events`。
7. `ImPolicyBackboneKafkaListener` 从 `user.events` 进入 IM policy ApplicationService；奖励消费者位于 wallet，不消费 `user.events`。

## 关键代码

- `user.controller.UserController`
- `user.controller.AdminUserController`
- `user.application.UserReadApplicationService`
- `user.application.UserAvatarApplicationService`
- `user.application.UserCredentialApplicationService`
- `user.application.UserRegistrationApplicationService`
- `user.application.UserModerationApplicationService`
- `user.application.AdminUserApplicationService`
- `user.domain.service.*`
- `user.infrastructure.api.*`
- `user.infrastructure.oss.*`
