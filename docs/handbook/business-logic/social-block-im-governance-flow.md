# 拉黑与 IM 治理联动链路实现说明

本文档说明当前仓库中“拉黑关系如何影响内容互动与 IM 私信”的实际实现路径，聚焦以下问题：

- 用户如何拉黑、取消拉黑、查看拉黑状态
- 评论 / 回复为什么会受拉黑关系影响
- IM 私信发送前如何通过本地 policy projection 做治理判定
- 拉黑、禁言、封禁在私信治理里如何共同生效

相关文档：

- `docs/handbook/business-logic/report-moderation-flow.md`
- `docs/handbook/business-logic/im-private-message-flow.md`

---

## 1. 参与组件

- `BlockController`：拉黑入口
- `BlockApplicationService`：拉黑用例编排、事务边界、领域事件发布
- `BlockDomainService`：拉黑领域规则
- `BlockRepository`：domain repository interface，`db / redis / memory` 三种后端由 infrastructure 实现
- `CommentApplicationService`：内容互动里检查拉黑关系
- `ImPolicySnapshotController`：供 `im-realtime` bootstrap 的 internal policy 快照入口
- `ImPolicySnapshotApplicationService` / `ImPolicySnapshotService`：通过 `user` / `social` owner API 构造 policy 快照
- `PolicyProjectionService`：`im-realtime` 本地私信发送判定
- `ImPolicyOutboxEnqueuer` / `ImPolicyKafkaOutboxHandler`：把拉黑与用户处罚变化发布到 IM policy Kafka topic
- `content.UserModerationGuard`：内容侧禁言 / 封禁守卫
- `UserModerationQueryApi`：读取用户处罚状态

---

## 2. 对外接口

拉黑对外接口：

- `POST /api/blocks`
- `DELETE /api/blocks?userId=...`
- `GET /api/blocks`
- `GET /api/blocks/status?userId=...`

IM policy internal 快照接口：

- `GET /internal/im/realtime/projections/user-policies`
- `GET /internal/im/realtime/projections/block-relations`

这些接口不是给浏览器页面直接发消息用的，而是给 `im-realtime` 使用内部 scope JWT 拉取 bootstrap 快照。

---

## 3. 拉黑主链路

### 3.1 拉黑

`POST /api/blocks` -> `BlockController` -> `BlockApplicationService.block(BlockCommand)`

关键步骤：

1. 校验双方用户 id 合法
2. 禁止拉黑自己
3. 通过 repository 落状态
4. 注册 rollback 钩子
5. 发布 `BlockRelationChangedDomainEvent(blocked=true)`，由 `LocalSocialDomainEventPublisher` 映射为 `BlockPayload`

这里有一个重要实现细节：

- 如果发布 block 事件失败，会尝试把刚写入的 block 关系回滚

### 3.2 取消拉黑

`DELETE /api/blocks` -> `BlockController` -> `BlockApplicationService.unblock(UnblockCommand)`

逻辑和拉黑对称：

1. 删除关系
2. 注册 rollback 钩子
3. 发布 `BlockRelationChangedDomainEvent(blocked=false)`，由 infrastructure event adapter 映射为 `BlockPayload`

### 3.3 当前默认存储

根据配置：

- `social.storage=db`

所以当前默认使用：

- `MyBatisBlockRepository`

其语义是：

- 重复拉黑：返回 `false`，视为幂等
- 取消拉黑：按当前关系删除

---

## 4. 拉黑如何影响内容互动

`CommentApplicationService.createInsideTransaction(...)` 在真正写评论前，会做一条反骚扰校验：

- 如果评论目标用户与当前用户存在任意一侧拉黑关系
- 直接拒绝评论 / 回复

这里的检查不是“我拉黑了对方才拦截”，而是：

- `isEitherBlocked(actorUserId, targetUserId)`

所以只要任意一侧拉黑，互动就会被阻断。

这条规则覆盖：

- 评论帖子作者
- 回复一级评论的评论作者

---

## 5. 拉黑如何影响 IM 私信

### 5.1 IM policy 快照与增量入口

`im-realtime` 启动或重连时会调用：

- `GET /internal/im/realtime/projections/user-policies`
- `GET /internal/im/realtime/projections/block-relations`

控制器：

- `ImPolicySnapshotController`

这些接口只接受带 `SCOPE_im.realtime.internal` 的内部 JWT，不是浏览器业务 API。快照服务通过 `UserModerationQueryApi.scanModerationStatesAfterId(...)` 与 `SocialBlockQueryApi.scanBlockRelationsAfter(...)` 回源读取 owner SSOT。

拉黑或用户处罚状态变化后，`ImPolicyOutboxEnqueuer` 在 `BEFORE_COMMIT` 写入 `projection.im.policy` outbox，`ImPolicyKafkaOutboxHandler` 再发布到 IM policy Kafka topic，供 `im-realtime` 增量更新本地 projection。

### 5.2 私信本地判定

`PolicyProjectionService.canSendPrivateMessage(fromUserId, toUserId)` 当前按这个顺序执行：

1. 校验 `fromUserId`、`toUserId`
2. 确认发送者存在
3. 确认接收者存在
4. 校验发送者未被禁言 / 封禁
5. 校验接收者允许私信
6. 校验双方不存在任意方向拉黑关系

只要任何一步失败，就直接拒绝受理私信。

---

## 6. 拉黑、禁言、封禁三者的关系

当前私信发送前，会同时经过三类限制：

1. 用户存在性
2. 用户处罚状态
3. 双方拉黑关系

其中：

- 禁言 / 封禁是“发送者自己是否还能说话”
- 拉黑是“发送者和接收者之间是否允许互动”

所以一个用户即使没被禁言，只要双方存在拉黑关系，也不能发私信。

同样，一个用户即使没被拉黑，但如果正处于禁言 / 封禁中，也不能发私信。

---

## 7. 失败语义

常见失败点：

- 给自己发私信
- 发送者或接收者不存在
- 发送者已被禁言
- 发送者已被封禁
- 双方存在拉黑关系

对应结果是：

- 在内容侧，评论 / 回复直接抛业务异常
- 在 IM 侧，本地 policy 判定失败，`im-realtime` 最终给客户端回 `sendReject`

---

## 8. 一句话总结

当前实现里，“拉黑”不是孤立的社交功能，而是一个横切治理状态：

- 在内容互动里阻断评论 / 回复
- 在 IM 私信里阻断发送
- 与禁言 / 封禁共同构成用户发言治理矩阵
