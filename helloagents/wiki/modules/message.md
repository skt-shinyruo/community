# message

## Purpose
提供私信与系统通知能力，并通过 Kafka 消费事件生成通知消息。

## Module Overview
- **Responsibility：** 私信会话列表/详情/发送；通知列表/详情/未读数；Kafka 消费评论/点赞/关注事件写入通知
- **Status：** ✅Stable
- **Last Updated：** 2026-01-16

## Specifications

### Requirement: 私信
**Module:** message
用户可查看私信会话与详情，并发送私信。

#### Scenario: 查看私信列表
前置条件：用户已登录
- 返回会话列表、未读数、目标用户信息

#### Scenario: 发送私信
前置条件：目标用户存在
- 私信写入数据库

### Requirement: 系统通知
**Module:** message
用户在被评论/被点赞/被关注时收到系统通知。

#### Scenario: 消费事件生成通知
前置条件：Kafka 收到事件
- 写入 message 表（from_id=系统用户）
- 通知内容包含触发者与目标实体信息

## API Interfaces（现状）
- `GET /letter/list`
- `GET /letter/detail/{conversationId}`
- `POST /letter/send`
- `GET /notice/list`
- `GET /notice/detail/{topic}`

## Data Models
### message
（详见 `helloagents/wiki/data.md` 的 “message” 小节）

## Dependencies
- user（目标用户信息）
- infra（Kafka、Security/登录态）

## Change History
- （暂无）
