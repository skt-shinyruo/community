# 站内通知链路实现说明

本文档说明当前仓库中站内通知能力的实际实现路径，聚焦以下问题：

- 通知列表、未读数、摘要、批量已读分别从哪里进入系统
- 评论、点赞、关注、治理通知是如何从业务事件投影成 notice 记录的
- 当前 notice 的 topic、发送者、已读语义是什么
- 为什么 notice 属于典型的“异步读模型”，而不是主写链路的一部分

相关文档：

- `docs/business-logic/content-post-comment-bookmark-subscription-flow.md`
- `docs/business-logic/report-moderation-flow.md`
- `docs/business-logic/social-like-follow-outbox-flow.md`
- `docs/SYSTEM_DESIGN.md`

---

## 1. 参与组件

通知链路涉及以下组件：

- 前端：读取通知列表、topic 摘要、未读数，并提交已读操作
- `community-app`：
  - `NoticeController`：通知读接口入口
  - `NoticeService`：通知读写主服务
  - `NoticeProjectionService`：把内容 / 社交事件转换成 notice 投影命令
  - `NoticeOutboxEnqueuer` / `NoticeOutboxHandler`：默认 outbox 模式下的入箱与消费
  - `NoticeProjectionListener`：关闭 outbox 时的本地 after-commit 投影
- MySQL（`community` schema）：
  - `NoticeRecord`
  - `NoticeMapper`
- 上游事件来源：
  - `ContentContractEvent`
  - `SocialContractEvent`

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionService.java`

---

## 2. 对外接口

当前通知对外接口包括：

- `GET /api/notices`
  - 按 `topic` 分页读取通知列表
- `GET /api/notices/unread-count`
  - 读取某个 `topic` 或全部通知的未读数
- `GET /api/notices/summary`
  - 读取按 `topic` 聚合后的摘要
- `PUT /api/notices/read`
  - 批量标记已读

这些接口都需要登录态，当前用户通过 `CurrentUser.requireUserUuid(authentication)` 获取。

---

## 3. notice 主事实长什么样

通知当前的主存储模型是 `NoticeRecord`：

- `id`
- `senderUserId`
- `recipientUserId`
- `topic`
- `content`
- `status`
- `createTime`

关键约定：

- `senderUserId` 统一使用 `SYSTEM_NOTICE_SENDER_ID = 00000000-0000-0000-0000-000000000000`
- `status=0` 表示未读
- `status=1` 表示已读
- `content` 保存的是 JSON 字符串，而不是拆平后的结构化列

也就是说，notice 不是一套复杂的强结构消息表，而是：

- 用少量固定字段做检索与状态管理
- 用 JSON `content` 保存事件上下文

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/notice/entity/NoticeRecord.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/mapper/NoticeMapper.java`

---

## 4. 读路径：列表、未读数、摘要、已读

### 4.1 通知列表

`GET /api/notices?topic=...` 的链路如下：

1. `NoticeController.list(...)`
2. `NoticeService.listNoticeItems(...)`
3. `NoticeService.listNotices(...)`
4. `NoticeMapper.selectNotices(userId, topic, offset, limit)`

这里的列表是严格按 `recipientUserId + topic` 过滤出来的，不会跨 topic 混查。

### 4.2 未读数

`GET /api/notices/unread-count` 直接调用：

- `NoticeService.unreadCount(userId, topic)`
- `NoticeMapper.selectNoticeUnreadCount(...)`

如果 `topic` 为空，则由 mapper 负责返回当前用户全量未读数；如果有 `topic`，则按该 topic 统计。

### 4.3 摘要页

`GET /api/notices/summary` 当前是固定 topic 集合的摘要，而不是动态枚举。

固定 topic 为：

- `comment`
- `like`
- `follow`
- `moderation`

`NoticeService.topicSummary(...)` 会对每个 topic：

1. 查最新一条 notice
2. 查该 topic 总数
3. 查该 topic 未读数
4. 组装成 `NoticeTopicSummaryResponse`

所以通知摘要页的语义是：

- “每种通知类型一个卡片 / 分组”
- 而不是“全局混排的一页摘要”

### 4.4 批量已读

`PUT /api/notices/read` 的链路如下：

1. `NoticeController.markRead(...)`
2. `NoticeService.markRead(userId, ids)`
3. `NoticeMapper.updateNoticesStatusForRecipient(ids, STATUS_READ, userId)`

这个更新语义很重要：

- 只会更新当前用户自己的通知
- 即使请求里带了别人的 notice id，也不会被误改

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java`

---

## 5. 通知不是谁都能直接写：要先变成投影命令

当前项目里，站内通知不是业务代码到处直接 `createNotice(...)`。

更稳定的做法是：

1. 上游业务发布事件
2. `NoticeProjectionService` 判断是否需要投影成通知
3. 生成 `NoticeProjectionCommand`
4. 再由 handler / listener 真正写入 notice 表

这层投影转换很关键，因为它把：

- 上游业务事件
- notice topic
- notice 收件人
- notice content 结构

统一收敛在一个地方。

---

## 6. 上游事件如何转换成通知

### 6.1 内容事件

`NoticeProjectionService.commandForContentEvent(...)` 当前只处理两类内容事件：

1. `COMMENT_CREATED`
- topic: `comment`
- 收件人：`payload.targetUserId`
- payload：`CommentPayload`

2. `MODERATION_ACTION_APPLIED`
- topic: `moderation`
- 收件人：`payload.toUserId`
- payload：`ModerationPayload`

也就是说：

- 发帖不会直接生成 notice
- 评论 / 回复会给被互动用户生成 `comment` notice
- 治理动作会给被处置人或举报人生成 `moderation` notice

### 6.2 社交事件

`NoticeProjectionService.commandForSocialEvent(...)` 当前处理两类社交事件：

1. `LIKE_CREATED`
- topic: `like`
- 收件人：`payload.entityUserId`

2. `FOLLOW_CREATED`
- topic: `follow`
- 收件人：`payload.entityUserId`

注意：

- 当前只对“创建型”社交事件生成 notice
- `LIKE_REMOVED`、`FOLLOW_REMOVED` 不会反向撤销或生成通知

### 6.3 notice content 长什么样

`project(...)` 真正写 notice 时，会把内容统一序列化成：

```json
{
  "eventId": "...",
  "type": "...",
  "payload": { ... }
}
```

所以通知表里的 `content` 不是“最终渲染好的文案”，而是带上下文的 JSON 快照。

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionService.java`

---

## 7. 默认路径：outbox 投影

当前默认配置是：

- `events.outbox.enabled=true`

所以通知的默认生成路径是：

1. 上游发布 `ContentContractEvent` / `SocialContractEvent`
2. `NoticeOutboxEnqueuer` 在 `BEFORE_COMMIT` 阶段生成 `NoticeProjectionCommand`
3. `JdbcOutboxEventStore.enqueue(...)` 把命令写入 outbox
4. `OutboxWorker` 轮询并分发到 `NoticeOutboxHandler`
5. `NoticeOutboxHandler.handle(...)`
6. `NoticeProjectionService.project(...)`
7. `NoticeService.createNotice(...)`
8. `NoticeMapper.insertNotice(...)`

### 7.1 为什么要先入 outbox

因为 notice 是典型的“允许异步追平”的读模型：

- 评论主写链路成功，不要求通知同步写完
- 点赞 / 关注主写链路成功，不要求通知同步写完
- 治理动作主写链路成功，不要求通知同步写完

outbox 的价值是：

- 与主事务一起提交“我需要生成通知”这个事实
- 真正写 notice 时支持重试
- 避免一次通知写失败反过来把主业务打回滚

### 7.2 outbox 的 topic

通知投影在 outbox 里的 handler topic 固定是：

- `projection.notice`

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java`

---

## 8. 关闭 outbox 时的降级路径

如果 `events.outbox.enabled=false`，项目会退回到本地监听器模式：

- `NoticeProjectionListener`

它会在 `AFTER_COMMIT` 阶段直接执行：

- `commandForContentEvent(...)`
- `commandForSocialEvent(...)`
- `project(...)`

这条路径的语义是：

- 主事务提交后 best-effort 写通知
- 写失败只记日志，不自动重试

所以它更适合作为关闭 outbox 时的简化实现，而不是默认生产路径。

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`

---

## 9. 典型通知从哪里来

### 9.1 评论通知

链路来源：

- `CommentService.addComment(...)`
- 发布 `COMMENT_CREATED`
- `NoticeProjectionService` 转成 `comment` notice

对应场景：

- 评论帖子
- 回复一级评论

只要 `targetUserId` 存在，就可能产生一条 `comment` notice。

### 9.2 点赞 / 关注通知

链路来源：

- `social` 域发布 `LIKE_CREATED`
- `social` 域发布 `FOLLOW_CREATED`
- `NoticeProjectionService` 转成 `like` / `follow` notice

这里 notice 只对被互动对象生成，不对操作者自己生成。

### 9.3 治理通知

链路来源：

- `TakeModerationActionUseCase`
- `ModerationNoticePublisher.publish(...)`
- 发布 `MODERATION_ACTION_APPLIED`
- `NoticeProjectionService` 转成 `moderation` notice

治理通知的接收者由 `ModerationPayload.toUserId` 决定，所以：

- 可以发给举报人
- 也可以发给被处置目标

---

## 10. 一致性与失败语义

### 10.1 notice 是异步读模型

这条链路最重要的认知是：

- notice 不是主事实
- notice 是从内容 / 社交 / 治理事件派生出的读模型

因此：

- 主业务成功，不代表通知已立即可见
- 通知允许比主业务稍晚出现

### 10.2 收件人为 null 时不会生成 notice

`NoticeProjectionService.command(...)` 明确要求：

- `toUserId != null`

否则直接返回 `null`，后续不会入箱、不会写 notice。

这能避免一些“无目标用户”的事件被错误投影成系统通知。

### 10.3 已读更新是按收件人保护的

`markRead(...)` 不会简单按 id 全表更新，而是带 `userId` 作为限制条件。

所以这条链路的安全边界是：

- 当前用户只能把自己的通知标成已读

---

## 11. 建议源码阅读顺序

建议按下面顺序读：

1. `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
2. `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java`
3. `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionService.java`
4. `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java`
5. `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java`
6. `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`
7. `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentService.java`
8. `backend/community-app/src/main/java/com/nowcoder/community/content/app/moderation/ModerationNoticePublisher.java`
9. `backend/community-app/src/main/java/com/nowcoder/community/social/event/LocalSocialEventPublisher.java`

---

## 12. 一句话总结

当前站内通知的实现思路是：

- 用固定 topic 和 JSON content 建模一张轻量 notice 表
- 让内容 / 社交 / 治理事件先转换成 `NoticeProjectionCommand`
- 默认通过 outbox 异步写入通知读模型
- 最终由 `NoticeController` 对外暴露列表、未读数、摘要和已读能力
