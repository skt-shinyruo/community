# social

## Purpose
提供点赞、关注/粉丝等社交关系能力（主要基于 Redis）。

## Module Overview
- **Responsibility：** 点赞/取消点赞；统计实体点赞数；关注/取关；关注列表/粉丝列表
- **Status：** ✅Stable
- **Last Updated：** 2026-01-23

## Specifications

### Requirement: 点赞
**Module:** social
用户可对帖子/评论点赞，并可取消。

#### Scenario: 点赞帖子
前置条件：用户已登录
- Redis 记录点赞关系
- 更新被赞用户的获赞计数
- 触发点赞事件（通知；若处于事务中则 After-Commit 发送，避免幽灵事件）

### Requirement: 关注/粉丝
**Module:** social
用户可关注用户并查看关注列表/粉丝列表。

#### Scenario: 关注用户
前置条件：用户已登录
- ZSet 记录关注时间
- 触发关注事件（通知；若处于事务中则 After-Commit 发送，避免幽灵事件）

### Requirement: 拉黑/反骚扰
**Module:** social
用户可拉黑/解除拉黑对方，用于反骚扰与私信/回复等写路径约束。

#### Scenario: 拉黑用户
- Redis Set 记录拉黑关系（`block:<userId>`）
- 提供内部接口查询 A/B 拉黑关系，供 message-service/content-service 写路径校验

## API Interfaces（现状）
- `POST /api/likes`（显式 liked=true/false，幂等）
- `GET /api/likes/status`、`GET /api/likes/count`、`GET /api/likes/users/{userId}/count`
- `POST /api/follows`、`DELETE /api/follows`
- `GET /api/follows/status`
- `GET /api/follows/{userId}/followees`、`GET /api/follows/{userId}/followers`
- `GET /api/follows/{userId}/followees/count`、`GET /api/follows/{userId}/followers/count`
- `POST /api/blocks`（拉黑）
- `DELETE /api/blocks?userId=`（解除拉黑）
- `GET /api/blocks`（我的拉黑列表）
- `GET /api/blocks/status?userId=`（查询是否已拉黑）
- internal（仅服务间调用，要求 `X-Internal-Token`）：
  - `GET /internal/social/blocks/relation?userIdA=&userIdB=`

## Data Models
### Redis Keys
（详见 `helloagents/wiki/data.md` 的 “Redis Key 设计” 小节）

## Dependencies
- user（用户资料用于列表展示）
- message（点赞/关注通知）
- infra（Redis/Kafka）

## Change History
- 2026-01-18：Kafka 事件发布统一 After-Commit 策略（在事务活跃时 commit 后发送），并补齐发布失败指标用于观测。
- 2026-01-23：新增拉黑/反骚扰能力（Redis Set 存储 + 对外 API + internal 关系查询）。
