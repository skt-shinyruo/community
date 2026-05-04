# User 用户业务逻辑

用户域拥有用户账号事实、资料事实、凭据事实、角色事实、处罚状态和头像。auth、content、social、growth、IM 等域都围绕 user owner 的同步 API 或事件协作。

## Owner / SSOT

- `user` owns `user` 表中的账号、邮箱、密码 hash、头像、角色、状态、积分/score、禁言和封禁时间。
- `user` owns DB refresh session 存储事实，但 refresh token 策略由 auth 域编排。
- 用户最近帖子/评论不是 user 主事实，来自 content owner 查询聚合。
- IM 的本地 policy projection 不是用户处罚主事实；user owner 通过 snapshot 和事件向 IM 投影。

## 入口

HTTP：

- `GET /api/users/{userId}`
- `GET /api/users/{userId}/recent-posts`
- `GET /api/users/{userId}/recent-comments`
- `POST /api/users/batch-summary`
- `GET /api/users/{userId}/avatar/upload-token`
- `POST /api/users/{userId}/avatar/upload`
- `PUT /api/users/{userId}/avatar`
- `GET /files/**`
- `GET /api/users/admin/search`
- `POST /api/users/admin/role`

内部 API：

- auth 调 user 完成注册、登录认证、密码更新、refresh session 存储。
- content/social/growth 调 user 做摘要查询、积分或处罚判断。
- IM snapshot 拉取用户 policy。

## 用户资料和摘要

`UserReadApplicationService` 提供基础用户读取能力：

- 按 ID 查询用户摘要。
- 按用户名查询摘要。
- 按邮箱查询摘要。
- 批量按 ID 查询摘要，并保持输入顺序。
- 校验用户是否存在。

`UserProfileApplicationService` 负责用户主页聚合：

1. 读取用户基础资料。
2. 根据 viewer 判断是否本人。
3. 读取用户最近帖子和最近评论。
4. 可组合社交状态、等级或其他展示字段。
5. 返回面向页面的应用层结果。

用户主页不是 user 独占数据拷贝；最近内容必须回源 content owner。

## 头像流程

头像是三段式：

1. `createUploadToken(actorUserId, userId)`：只能本人操作，生成上传 token 或文件 key。
2. `upload(actorUserId, userId, fileName, content)`：只能本人上传，校验文件名、MIME、大小，写入存储。
3. `updateAvatar(actorUserId, userId, fileName)`：只能本人确认，消费上传 ticket，把头像 URL 写回 `user.header_url`。

规则：

- 非本人操作返回 forbidden。
- ticket 绑定 `userId + fileName`。
- confirm 阶段一次性消费 ticket。
- `fileName` 必须符合头像路径规则。
- 上传失败不能更新头像。

文件读取由 `UserFileApplicationService.loadAvatarOrNull(...)` 和 `FilesController` 承接。

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

- 发帖积分命令。
- 评论积分命令。
- 点赞创建/移除积分命令。
- 将积分投影转为 wallet / reward 侧可执行的动作。

用户积分展示字段与 wallet 在线余额不是同一事实。当前奖励和余额以 wallet owner 为资金事实。

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
- `user.controller.FilesController`
- `user.application.UserReadApplicationService`
- `user.application.UserProfileApplicationService`
- `user.application.UserAvatarApplicationService`
- `user.application.UserFileApplicationService`
- `user.application.UserCredentialApplicationService`
- `user.application.UserRegistrationApplicationService`
- `user.application.UserModerationApplicationService`
- `user.application.AdminUserApplicationService`
- `user.application.UserPointsApplicationService`
- `user.application.RefreshTokenSessionApplicationService`
- `user.domain.service.*`
- `user.infrastructure.api.*`
