# 站内通知链路实现说明

本文档说明当前仓库中站内通知能力的实际实现路径，重点关注：

- 通知列表、未读数、摘要、批量已读分别从哪里进入系统
- 评论、点赞、关注、治理通知如何从业务事件投影成 notice 记录
- notice 的 topic、发送者、已读语义是什么
- notice 为什么是 best-effort 本地读模型，而不是主写链路的一部分

相关文档：

- `docs/business-logic/content-post-comment-bookmark-subscription-flow.md`
- `docs/business-logic/report-moderation-flow.md`
- `docs/business-logic/social-like-follow-outbox-flow.md`
- `docs/SYSTEM_DESIGN.md`

---

## 1. 参与组件

通知域已按 DDD Tactical Layering 收口：

- `NoticeController`：HTTP 入站适配器，只负责认证提取、请求绑定和 DTO 转换
- `NoticeApplicationService`：通知列表、未读数、摘要、已读、创建通知的 owner application entry
- `NoticeProjectionApplicationService`：把内容 / 社交契约事件编排成 notice 创建命令
- `NoticeDomainService`：分页默认值、状态常量、创建校验
- `NoticeProjectionDomainService`：判断事件是否应该投影
- `NoticeRepository`：domain 持久化契约
- `MyBatisNoticeRepository` / `NoticeMapper` / `NoticeRecordDataObject`：MyBatis 持久化实现
- `NoticeProjectionListener`：本地 after-commit 事件适配器，失败只记录日志，不回滚主事务

关键代码：

- `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/service/NoticeDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/repository/NoticeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeRepository.java`

---

## 2. 对外接口

当前通知对外接口包括：

- `GET /api/notices`：按 `topic` 分页读取通知列表
- `GET /api/notices/unread-count`：读取某个 `topic` 或全部通知的未读数
- `GET /api/notices/summary`：读取按 `topic` 聚合后的摘要
- `PUT /api/notices/read`：批量标记已读

这些接口都需要登录态，当前用户通过 `CurrentUser.requireUserUuid(authentication)` 获取。

HTTP DTO 只存在于 `notice.controller.dto`。controller 会把 application result 转成 response DTO，application 不返回 HTTP DTO。

---

## 3. notice 主事实长什么样

通知当前的 domain model 是 `NoticeRecord`，持久化行对象是 `NoticeRecordDataObject`。

字段语义：

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
- `content` 保存 JSON 字符串，不拆成强结构列
- 当前仍复用 `message` 表承载 notice 行，但表结构细节只暴露在 `notice.infrastructure.persistence`

---

## 4. 读路径：列表、未读数、摘要、已读

### 4.1 通知列表

`GET /api/notices?topic=...` 的链路如下：

1. `NoticeController.list(...)`
2. `NoticeApplicationService.listNoticeItems(ListNoticeItemsCommand)`
3. `NoticeRepository.findByUserAndTopic(...)`
4. `MyBatisNoticeRepository`
5. `NoticeMapper.selectNotices(...)`

列表严格按 `recipientUserId + topic` 过滤，不跨 topic 混查。

### 4.2 未读数

`GET /api/notices/unread-count` 调用：

- `NoticeApplicationService.unreadCount(userId, topic)`
- `NoticeRepository.unreadCount(userId, topic)`
- `NoticeMapper.selectNoticeUnreadCount(...)`

如果 `topic` 为空，则统计当前用户全量未读数；如果有 `topic`，则按该 topic 统计。

### 4.3 摘要页

`GET /api/notices/summary` 使用固定 topic 集合：

- `comment`
- `like`
- `follow`
- `moderation`

`NoticeApplicationService.topicSummary(...)` 会对每个 topic：

1. 查最新一条 notice
2. 查该 topic 总数
3. 查该 topic 未读数
4. 组装 `NoticeTopicSummaryResult`
5. controller 转成 `NoticeTopicSummaryResponse`

### 4.4 批量已读

`PUT /api/notices/read` 的链路如下：

1. `NoticeController.markRead(...)`
2. `NoticeApplicationService.markRead(MarkNoticeReadCommand)`
3. `NoticeRepository.markRead(userId, ids, STATUS_READ)`
4. `NoticeMapper.updateNoticesStatusForRecipient(...)`

更新语义是按 `recipientUserId` 保护的：请求里即使带了别人的 notice id，也不会被误改。

---

## 5. 通知不是谁都能直接写：先进入投影应用服务

站内通知不是由业务代码到处直接写表。

当前稳定路径是：

1. 上游业务发布 `ContentContractEvent` / `SocialContractEvent`
2. `NoticeProjectionListener` 在事务提交后接收事件
3. `NoticeProjectionApplicationService` 判断事件类型、收件人、topic 和 content 快照
4. `NoticeProjectionDomainService` 判断是否应该投影
5. `NoticeApplicationService.createNotice(CreateNoticeCommand)` 创建 notice
6. `NoticeRepository` 写入持久化实现

这层投影转换把上游事件、notice topic、收件人和 content JSON 结构统一收敛在 owner application service 中。

---

## 6. 上游事件如何转换成通知

### 6.1 内容事件

`NoticeProjectionApplicationService` 当前处理两类内容事件：

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

`NoticeProjectionApplicationService` 当前处理两类社交事件：

1. `LIKE_CREATED`
- topic: `like`
- 收件人：`payload.entityUserId`

2. `FOLLOW_CREATED`
- topic: `follow`
- 收件人：`payload.entityUserId`

当前只对创建型社交事件生成 notice，`LIKE_REMOVED`、`FOLLOW_REMOVED` 不会撤销或生成通知。

### 6.3 notice content 长什么样

真正写 notice 时，content 会统一序列化成：

```json
{
  "eventId": "...",
  "type": "...",
  "payload": { }
}
```

所以通知表里的 `content` 不是最终渲染文案，而是带上下文的 JSON 快照。

---

## 7. 投影交付语义

notice 当前是本地 after-commit best-effort 投影，不再拥有 notice outbox adapter。

`NoticeProjectionListener`：

- 监听 `ContentContractEvent`
- 监听 `SocialContractEvent`
- 通过 `NoticeProjectionApplicationService` 执行投影
- 标记为 `@BestEffortLocalEventListener`
- 失败只记录 warn，不回滚内容 / 社交 / 治理主事务

因此 notice 的一致性语义是：

- 主业务成功，不代表通知已经立即可见
- 通知投影失败不会打回主业务事务
- 当前 notice 本地投影没有 outbox 重试语义

---

## 8. 典型通知从哪里来

### 8.1 评论通知

链路来源：

- 内容域发布 `COMMENT_CREATED`
- `NoticeProjectionListener`
- `NoticeProjectionApplicationService`
- `NoticeApplicationService.createNotice(...)`

只要 `targetUserId` 存在，就可能产生一条 `comment` notice。

### 8.2 点赞 / 关注通知

链路来源：

- social 域发布 `LIKE_CREATED`
- social 域发布 `FOLLOW_CREATED`
- `NoticeProjectionApplicationService` 转成 `like` / `follow` notice

notice 只对被互动对象生成，不对操作者自己生成。

### 8.3 治理通知

链路来源：

- 治理动作发布 `MODERATION_ACTION_APPLIED`
- `NoticeProjectionApplicationService` 转成 `moderation` notice

治理通知的接收者由 `ModerationPayload.toUserId` 决定。

---

## 9. 一致性与失败语义

### 9.1 notice 是异步读模型

notice 不是内容、社交或治理的主事实，而是从这些事件派生出的读模型。

因此：

- 主业务成功，不代表通知已立即可见
- 通知允许比主业务稍晚出现
- 通知失败不应改变主业务事务结果

### 9.2 收件人为 null 时不会生成 notice

`NoticeProjectionDomainService.shouldProject(...)` 要求：

- projection 不为空
- `toUserId != null`
- `topic` 非空

否则不会写 notice。

### 9.3 已读更新是按收件人保护的

`markRead(...)` 带 `userId` 作为限制条件，不会简单按 id 全表更新。

---

## 10. 建议源码阅读顺序

建议按下面顺序读：

1. `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
2. `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java`
3. `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
4. `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/service/NoticeDomainService.java`
5. `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/service/NoticeProjectionDomainService.java`
6. `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/repository/NoticeRepository.java`
7. `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeRepository.java`
8. `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListener.java`
9. `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentService.java`
10. `backend/community-app/src/main/java/com/nowcoder/community/content/app/moderation/ModerationNoticePublisher.java`
11. `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`

---

## 11. 一句话总结

当前站内通知的实现思路是：

- 用固定 topic 和 JSON content 建模轻量 notice 读模型
- 让内容 / 社交 / 治理事件进入 `NoticeProjectionApplicationService`
- 用本地 after-commit listener 做 best-effort 投影
- controller 只对外暴露列表、未读数、摘要和已读能力，业务编排固定在 application 层
