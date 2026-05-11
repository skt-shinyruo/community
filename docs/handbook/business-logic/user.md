# User 用户业务逻辑

用户域拥有用户账号事实、资料事实、凭据事实、角色事实、处罚状态和头像业务规则；头像对象本身由 `community-oss` 承担。auth、content、social、growth、IM 等域都围绕 user owner 的同步 API 或事件协作。

## Owner / SSOT

- `user` owns `user` 表中的账号、邮箱、密码 hash、头像业务投影、角色、状态、积分/score、禁言和封禁时间；头像对象本身在 `community-oss`。
- `user` owns DB refresh session 存储事实，但 refresh token 策略由 auth 域编排。
- 用户最近帖子/评论不是 user 主事实，来自 content owner 查询聚合。
- IM 的本地 policy projection 不是用户处罚主事实；user owner 通过 snapshot 和事件向 IM 投影。

## 入口

HTTP：

- `GET /api/users/{userId}`
- `GET /api/users/{userId}/recent-posts`
- `GET /api/users/{userId}/recent-comments`
- `POST /api/users/batch-summary`
- `POST /api/users/{userId}/avatar/upload-sessions`
- `POST /api/users/{userId}/avatar/upload`
- `PUT /api/users/{userId}/avatar`
- `GET /files/**`（由 `community-oss` 提供公共文件读取）
- `GET /api/users/admin/search`
- `POST /api/users/admin/role`

内部 API：

- auth 调 user 完成注册、登录认证、密码更新、refresh session 存储。
- content/social/growth 调 user 做摘要查询、积分或处罚判断。
- IM snapshot 拉取用户 policy。

## 数据流

用户域的数据流围绕“用户事实被谁读写”展开：

1. 资料读取：HTTP 或跨域 query 进入 `UserReadApplicationService` / `UserProfileApplicationService`，先读取 `user` 主事实，再按页面需要回源 content 获取最近帖子/评论，或组合 social、growth 等读模型。最近内容不是 user 表冗余字段。
2. 头像更新：`UserAvatarApplicationService` 校验 actor 只能改本人头像，先通过 OSS prepare upload 获得 opaque `fileKey`，上传完成后消费 ticket，把 canonical OSS public URL 写回 `user.header_url`。blob、version 和 alias 仍由 `community-oss` owning。
3. 凭据协作：auth 登录通过 `UserCredentialQueryApi.authenticate(...)` 查询并校验密码；密码重置通过 `UserCredentialActionApi` 更新 BCrypt hash，并通过 refresh session application 撤销该用户会话。
4. 注册用户创建：auth 只保存 draft；验证码通过后调用 user owner 创建 active 用户。user 写入 `user` 行后发布 user policy changed，驱动 IM policy projection 识别用户存在性。
5. 处罚和角色：管理员或治理动作进入 user application，更新 `muteUntil`、`banUntil` 或 `type`。处罚变化发布 policy event，IM outbox/Kafka 最终刷新 realtime 本地 projection；角色变化要等 access token 重新签发后才体现在前端权限里。
6. 积分投影：content/social/growth 不直接改余额事实；user 只做积分语义转换，最终奖励或撤销进入 wallet owner。

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
- `levelForScore(...)` 按非负 score 每 100 分升一级，最低等级为 1。

`UserProfileApplicationService` 负责用户主页聚合：

1. 读取用户基础资料。
2. 根据 viewer 判断是否本人。
3. 读取用户最近帖子和最近评论。
4. 可组合社交状态、等级或其他展示字段。
5. 返回面向页面的应用层结果。

用户主页不是 user 独占数据拷贝；最近内容必须回源 content owner。

## 头像流程

头像是三段式 upload session 流程，对象存储已迁移到 OSS client boundary：

1. `createUploadToken(actorUserId, userId)`：只能本人操作，生成 upload session 和服务端 opaque `fileKey`，并向 OSS prepare upload。
2. `upload(actorUserId, userId, fileKey, content)`：只能本人上传，校验 file key、MIME、大小，通过 OSS client 完成代理上传。
3. `updateAvatar(actorUserId, userId, fileKey)`：只能本人确认，消费上传 ticket，把 canonical OSS public URL 写回 `user.header_url`。

规则：

- 非本人操作返回 forbidden。
- ticket 绑定 `userId + fileKey`。
- confirm 阶段一次性消费 ticket。
- `fileKey` 对前端是不透明标识；后端当前要求其内部形态符合头像路径规则。
- 上传失败不能更新头像。
- 前端只执行 upload session 返回的 URL、method、fields、headers 和约束，不读取 storage provider、bucket 或物理路径。
- 旧 `UserFileApplicationService` 和本地/R2 avatar storage provider 已退休，community-app 不再拥有公开文件读取入口。

文件读取不再由 `community-app` user 域承接；gateway 将 `/files/**` 路由到 `community-oss`，由 OSS owner 完成 alias 解析和 blob 读取。

## 凭据与密码

`UserCredentialApplicationService` 负责 user owner 内的凭据逻辑：

- `authenticate(username, password)`：返回认证成功、无效凭据或账号禁用。
- `getByUserId(...)`：给 auth refresh 或当前用户读取凭据。
- `findByEmailOrNull(...)`：密码重置时按邮箱定位用户。
- `updatePassword(...)`：写 BCrypt 新密码。
- `validatePasswordPolicy(...)`：调用密码复杂度规则。
- `resetPasswordAndRevokeRefreshSessions(...)`：更新密码后撤销该用户 refresh sessions。
- `authoritiesOf(...)`：根据 `user.type` 生成角色。

密码策略由 `PasswordPolicyDomainService` 控制。历史 MD5 + salt 登录成功后会升级为 BCrypt。

## 注册用户创建

当前注册由 auth 发起，user owner 只负责用户事实：

- `prepareRegistrationUser(...)`：验证注册输入，准备用户名/邮箱/密码 hash/默认头像，不写库。
- `createVerifiedRegistrationUser(...)`：验证码通过后插入 active 用户。
- `registerPendingUser(...)`、`activatePendingUser(...)`、`deletePendingUser(...)`、`cleanupExpiredPendingUsers(...)` 是兼容旧 pending-user 流程的能力。

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

## 管理员角色

`AdminUserApplicationService` 管理用户角色调整：

1. 管理员按 userId、username 或 email 搜索用户。
2. 修改角色必须带目标用户、目标 type、reason 和 confirm。
3. 禁止管理员把自己降级为非管理员。
4. 找不到目标用户失败。
5. 同角色更新直接返回，不重复写库。
6. 变更成功写 `user.type`，并通过 audit port 记录审计日志。

角色决定 JWT authorities，但已签发的 access token 不会立即变化，需要重新签发后体现。

## 积分投影

`UserPointsApplicationService` 当前承担积分命令翻译：

- 发帖：`commandForPostPublished(postId, userId)` 生成 `+10`，sourceEventId 为 `post-published:<postId>`。
- 评论：`commandForCommentCreated(commentId, userId)` 生成 `+2`，sourceEventId 为 `comment-created:<commentId>`。
- 点赞创建：`commandForLikeCreated(sourceEventId, actorUserId, entityUserId)` 给被点赞内容 owner 生成 `+1`。
- 点赞移除：`commandForLikeRemoved(...)` 给被点赞内容 owner 生成 `-1`。
- 点赞类命令要求 sourceEventId 非空、entityUserId 非空，且 actorUserId 不能等于 entityUserId；自己给自己点赞不产生积分命令。
- `project(...)` 对 null、userId 为空、delta 为 0、sourceEventId/type 为空的命令直接跳过。
- 有效命令会调用 `WalletRewardActionApi.applyDelta(...)`，requestId 为 `wallet-reward:<sourceEventId>`，由 wallet owner 承担幂等和余额事实。

用户积分展示字段与 wallet 在线余额不是同一事实。当前奖励和余额以 wallet owner 为资金事实。

## 用户事件发布

`LocalUserEventPublisher` 是 user owner 的本地 contract event adapter：

1. application 只依赖 `UserEventPublisher` port。
2. 发布 policy change 时，adapter 包装为 `UserContractEvent`。
3. eventId 当前使用随机 UUID 字符串。
4. type 固定为 `USER_POLICY_CHANGED`。
5. payload 是 `UserPolicyChangedPayload`。
6. Spring `ApplicationEventPublisher` 负责本地事务事件分发；IM policy outbox enqueuer 在 `BEFORE_COMMIT` 监听该 contract event。

## Refresh Session

`RefreshTokenSessionApplicationService` 被 auth 的 DB refresh token store 调用：

- `store(...)`：保存 refresh token hash、userId、family、expiresAt。
- `find(...)`：查询 token 状态。
- `consume(...)`：消费当前 token。
- `revoke(...)`：撤销单 token。
- `revokeFamily(...)`：撤销 family。
- `revokeByUserId(...)`：密码重置后撤销用户全部 refresh sessions。
- `deleteExpiredBefore(...)`：清理过期记录。

refresh token 明文不归 user 域存储。

## 关键代码

- `user.controller.UserController`
- `user.controller.AdminUserController`
- `user.application.UserReadApplicationService`
- `user.application.UserProfileApplicationService`
- `user.application.UserAvatarApplicationService`
- `user.application.UserCredentialApplicationService`
- `user.application.UserRegistrationApplicationService`
- `user.application.UserModerationApplicationService`
- `user.application.AdminUserApplicationService`
- `user.application.UserPointsApplicationService`
- `user.application.RefreshTokenSessionApplicationService`
- `user.domain.service.*`
- `user.infrastructure.api.*`
- `user.infrastructure.oss.*`
