# social

## Purpose
提供点赞、关注/粉丝等社交关系能力（主要基于 Redis）。

## Module Overview
- **Responsibility：** 点赞/取消点赞；统计实体点赞数；关注/取关；关注列表/粉丝列表
- **Status：** ✅Stable
- **Last Updated：** 2026-01-16

## Specifications

### Requirement: 点赞
**Module:** social
用户可对帖子/评论点赞，并可取消。

#### Scenario: 点赞帖子
前置条件：用户已登录
- Redis 记录点赞关系
- 更新被赞用户的获赞计数
- 触发点赞事件（通知）

### Requirement: 关注/粉丝
**Module:** social
用户可关注用户并查看关注列表/粉丝列表。

#### Scenario: 关注用户
前置条件：用户已登录
- ZSet 记录关注时间
- 触发关注事件（通知）

## API Interfaces（现状）
- `POST /like`
- `POST /follow`、`POST /unfollow`
- `GET /followees/{userId}`、`GET /followers/{userId}`

## Data Models
### Redis Keys
（详见 `helloagents/wiki/data.md` 的 “Redis Key 设计” 小节）

## Dependencies
- user（用户资料用于列表展示）
- message（点赞/关注通知）
- infra（Redis/Kafka）

## Change History
- （暂无）
