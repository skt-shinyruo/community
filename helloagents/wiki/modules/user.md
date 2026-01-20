# user

## Purpose
提供用户资料、个人主页与头像设置能力，并作为 **身份域 SSOT** 提供 internal 鉴权能力（供 auth-service 调用）。

## Module Overview
- **Responsibility：** 用户资料查询；个人主页展示所需数据；头像上传（七牛）与头像 URL 回写；internal 身份鉴权/注册/激活/密码更新接口
- **Status：** ✅Stable
- **Last Updated：** 2026-01-18

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
通过对象存储（七牛）更新头像。

#### Scenario: 获取上传凭证并更新头像 URL
前置条件：用户已登录
- 返回上传 token 与 fileName
- 上传完成后更新用户头像 URL

## API Interfaces（现状）
- `GET /api/users/{userId}`（公开）
- `GET /api/users/{userId}/avatar/upload-token`（需要登录，仅本人）
- `PUT /api/users/{userId}/avatar`（需要登录，仅本人，写入 header_url）
- internal（仅服务间调用，要求 `X-Internal-Token`，不走 JWT）：
  - `POST /internal/users/authenticate`
  - `GET /internal/users/{userId}/session-profile`
  - `POST /internal/users/register`
  - `POST /internal/users/{userId}/activate`
  - `GET /internal/users/by-email`
  - `POST /internal/users/{userId}/password`

## Data Models
### user
（详见 `helloagents/wiki/data.md` 的 “user” 小节）

## Dependencies
- social（关注/粉丝关系）
- infra（登录态/权限控制）
- external: Qiniu
- auth-service（通过 internal API 调用 user-service 作为身份域 SSOT）

## Change History
- 2026-01-18：补齐 user-service -> social-service 同步调用韧性（强制超时、降级返回默认值、指标可观测）。
- 2026-01-20：新增 `/internal/users/**` 作为身份域 internal API，并在登录成功后对 legacy 密码做渐进 rehash（MD5+salt -> BCrypt）。
