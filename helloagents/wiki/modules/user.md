# user

## Purpose
提供用户资料、个人主页与头像设置能力，并作为 **身份域 SSOT** 提供 internal 鉴权能力（供 auth-service 调用）。

## Module Overview
- **Responsibility：** 用户资料查询；个人主页展示所需数据；头像上传（local/qiniu）与头像 URL 回写；internal 身份鉴权/注册/激活/密码更新接口；管理员用户角色管理
- **Status：** ✅Stable
- **Last Updated：** 2026-02-01

## Specifications

### Requirement: 用户个人主页
**Module:** user
展示用户信息、获赞数、关注/粉丝数，以及“是否已关注”状态。

#### Scenario: 访问用户主页
前置条件：用户存在
- 返回用户资料与统计信息
- 登录态存在时返回是否关注该用户

> P0 生产可用：个人主页聚合依赖 social-service，同步调用必须具备超时 + 降级 + 可观测，
> 避免下游短暂不可用拖垮上游线程池与网关链路。

### Requirement: 用户头像设置
**Module:** user
支持可插拔存储（默认 local filesystem，自托管友好；可选 qiniu）。

#### Scenario: 获取上传凭证并更新头像 URL
前置条件：用户已登录
- 返回 provider + fileName，以及不同 provider 的上传契约：
  - local：返回 uploadUrl/uploadMethod（服务端接收 multipart 并落盘）
  - qiniu：返回 uploadToken/bucketUrl（客户端直传）
- 上传完成后调用 `PUT /api/users/{userId}/avatar` 写入 header_url

### Requirement: 成长体系（积分/等级/榜单）
**Module:** user
通过消费跨服务事件（发帖/评论/点赞等）生成积分流水，并在用户主页展示等级与积分，同时提供榜单接口。

#### Scenario: 消费事件记账（幂等）
- 消费 `community.event.post.v1` / `community.event.comment.v1` / `community.event.social.v1`
- 以 `user_score_log.event_id` 做幂等去重（避免重复消费导致积分膨胀）
- 对 `LikeRemoved` 做对称处理（扣减积分）以对齐“点赞可逆事件”，避免通过“点赞开关”刷分；积分在 SQL 层做非负保护

#### Scenario: 查询榜单
- 返回 Top-N 用户（按 score 降序）

### Requirement: 禁言/封禁（治理落地）
**Module:** user
提供用户禁言/封禁状态字段与 internal API，供 content-service 在写路径前置校验与治理动作落地。

## API Interfaces（现状）
- `GET /api/users/{userId}`（公开；返回增加 `score/level`）
- `GET /api/users/leaderboard?limit=`（公开；Top 用户榜单）
- `GET /api/users/{userId}/avatar/upload-token`（需要登录，仅本人）
- `POST /api/users/{userId}/avatar/upload`（需要登录，仅本人；local provider）
- `PUT /api/users/{userId}/avatar`（需要登录，仅本人，写入 header_url）
- `POST /api/users/batch-summary`（公开；仅返回公开字段，用于 Feed 聚合读避免 N+1）
- `GET /files/**`（公开；仅 local provider 生效，限制 avatar 前缀并防路径穿越）
- admin（仅管理员，JWT ROLE_ADMIN）：
  - `GET /api/users/admin/search?userId=&username=&email=`（搜索用户）
  - `POST /api/users/admin/role`（修改用户 type：USER/ADMIN/MODERATOR，要求 reason+confirm）
- internal（仅服务间调用，要求 `X-Internal-Token`，不走 JWT）：
  - `POST /internal/users/authenticate`
  - `GET /internal/users/{userId}/session-profile`
  - `POST /internal/users/register`
  - `POST /internal/users/{userId}/activate`
  - `GET /internal/users/by-email`
  - `POST /internal/users/{userId}/password`
  - `GET /internal/users/{userId}/moderation-status`
  - `POST /internal/users/{userId}/moderation`

## Data Models
### user
（详见 `helloagents/wiki/data.md` 的 “user” 小节）

### user_score_log
（详见 `helloagents/wiki/data.md` 的 “user_score_log” 小节）

## Dependencies
- social（关注/粉丝关系）
- kafka（消费内容/社交事件以生成积分）
- infra（登录态/权限控制）
- external: Qiniu（可选）
- auth-service（通过 internal API 调用 user-service 作为身份域 SSOT）

## Change History
- 2026-01-18：补齐 user-service -> social-service 同步调用韧性（强制超时、降级返回默认值、指标可观测）。
- 2026-01-20：新增 `/internal/users/**` 作为身份域 internal API，并在登录成功后对 legacy 密码做渐进 rehash（MD5+salt -> BCrypt）。
- 2026-01-23：新增成长体系（积分/等级/榜单）与治理落地字段（禁言/封禁），并补齐 internal moderation API 供 content-service 调用。
- 2026-02-01：积分链路支持 `LikeRemoved` 触发回退，并对 `user.score` 做非负保护（防刷分与边界值安全）。
