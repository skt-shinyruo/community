# 系统设计（同步 API + 异步事件）

本文档聚焦“系统设计层面”的关键点：模块边界、数据流、事件契约、最终一致、幂等与失败处理。其目标是让开发者理解“为什么这样拆”“链路如何走”“如何安全演进”。

---

## 1. 模块边界（按职责划分）

### 1.1 统一入口：gateway
- 浏览器唯一后端入口：`/api/**`
- 统一能力：鉴权、CORS、限流、审计、traceId
- 路由：按路径前缀转发到各微服务（见 `gateway/src/main/resources/application.yml`）

### 1.2 身份与会话：auth-service
- 登录/刷新/登出闭环
- 验证码、注册激活、找回密码等账号安全能力

### 1.3 业务域服务（示例）
- content-service：帖子/评论（写主存储并发布事件）
- social-service：点赞/关注（写 Redis 并发布事件）
- message-service：私信/通知（消费事件写通知）
- search-service：搜索（消费事件写 ES 索引）
- analytics-service：统计（可由 gateway 采集或事件驱动写入）

---

## 2. 同步 API：读写分离的基本形态

### 2.1 读路径（示例：帖子列表）
1. 前端请求 `/api/posts?...`
2. gateway 路由到 content-service
3. content-service 读 MySQL/Redis 组装结果返回

### 2.2 写路径（示例：发帖/评论/点赞/关注）
1. 前端请求写接口（携带 JWT）
2. gateway 统一鉴权/限流/审计
3. 目标服务写入主存储后发布事件

---

## 3. 异步事件：最终一致（Kafka）

### 3.1 Topic 约定
事件 topic 由 `common` 统一定义：
- `community.event.post.v1`
- `community.event.comment.v1`
- `community.event.social.v1`
- 约定 DLQ：`<topic>.dlq`

### 3.2 事件 Envelope（契约边界）
事件消息使用统一 envelope：
- `eventId`：全局唯一，用于幂等
- `traceId`：贯穿请求链路，便于日志串联
- `type`：事件类型（如 `PostPublished`、`CommentCreated`）
- `version`：事件版本（当前为 v1）
- `occurredAt`：发生时间
- `producer`：生产者服务名
- `payload`：具体数据（避免敏感字段）

### 3.3 典型消费方
- message-service：消费评论/社交事件，生成通知（最终一致）
- search-service：消费帖子事件，更新 ES 索引（最终一致）

---

## 4. 幂等与失败处理

### 4.1 消费幂等（eventId 去重）
消费端通过记录已消费的 `eventId` 来保证幂等：
- message-service：`consumed_event` 表
- search-service：`search_consumed_event` 表

这能避免：
- Kafka 重平衡/重试导致的重复消费产生重复副作用（重复通知、重复索引更新）

### 4.2 DLQ（死信队列）
当消费端处理失败（反序列化/业务异常等）：
- 通过统一的错误处理器将消息投递到 `<topic>.dlq`
- 便于离线排查与人工/脚本回放

---

## 5. 演进建议（契约优先）

事件契约是跨服务协作边界，演进建议：
- 通过 `version` 做向后兼容（先双写/双读，再切换）
- payload 避免敏感字段（密码/邮箱等）
- 生产端与消费端都要对“未知类型/未知版本”容错（可跳过并记录）

