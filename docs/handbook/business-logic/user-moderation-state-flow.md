# 用户禁言 / 封禁状态链路实现说明

本文档说明当前仓库中用户处罚状态的实际实现路径，聚焦以下问题：

- 用户的禁言 / 封禁状态存在哪里
- 这些状态是谁写入的
- 发帖、评论、私信发送前如何同步检查这些状态
- 当前是否有独立的解封 / 解除禁言入口

相关文档：

- `docs/handbook/business-logic/report-moderation-flow.md`
- `docs/handbook/business-logic/social-block-im-governance-flow.md`

---

## 1. 参与组件

- `UserModerationApplicationService`：用户处罚状态 owner application service
- `UserModerationApiAdapter`：发布 `UserModerationActionApi` / `UserModerationQueryApi`
- `content.UserModerationGuard`：内容侧发言守卫
- `ImPolicySnapshotService`：为 `im-realtime` policy projection 提供处罚状态快照
- `PolicyProjectionService`：`im-realtime` 本地私信发送判定
- `ModerationApplicationService`：处罚命令的上游发起者
- MySQL：
  - `user.mute_until`
  - `user.ban_until`

---

## 2. 状态模型

当前用户处罚状态只有两条时间线：

- `muteUntil`
- `banUntil`

判定逻辑都采用：

- 若字段非空且晚于 `now`，则处罚仍生效

因此：

- 禁言是“临时不能发言 / 发消息”
- 封禁是“临时不能使用对应能力”
- 两者都不靠单独 status 枚举，而靠截止时间

---

## 3. 状态写入来源

### 3.1 当前公开来源：举报治理

当前对外最明确的处罚来源是：

- `report-moderation-flow`

也就是：

1. `ModerationApplicationService`
2. `UserModerationActionApi.applyModeration(...)`
3. `UserModerationApiAdapter`
4. `UserModerationApplicationService.applyModeration(...)`

### 3.2 `UserModerationApplicationService.applyModeration(...)`

它支持这些动作：

- `mute`
- `ban`
- `unmute`
- `unban`

当前实现行为：

- `mute`：写 `muteUntil = now + durationSeconds`
- `ban`：写 `banUntil = now + durationSeconds`
- `unmute`：清空 `muteUntil`
- `unban`：清空 `banUntil`

不过从当前对外链路看：

- `mute` / `ban` 已经被治理链路实际使用
- `unmute` / `unban` 目前没有看到独立对外 controller 暴露

也就是说，服务层能力存在，但当前公开业务入口主要还是处罚，不是解除处罚。

---

## 4. 内容侧如何使用处罚状态

内容侧守卫在：

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/UserModerationGuard.java`

入口方法：

- `assertCanSpeak(UUID userId)`

主逻辑：

1. 通过 `UserModerationQueryApi.getModerationState(userId)` 回源读取当前处罚状态
2. 如果 `banUntil > now`
   - 拒绝：`账号已被封禁，无法发言`
3. 如果 `muteUntil > now`
   - 拒绝：`你已被禁言，暂时无法发言`

这条守卫被以下主链路调用：

- `PostPublishingApplicationService.create(...)`
- `PostPublishingApplicationService.updatePost(...)`
- `CommentApplicationService.create(...)`
- `CommentApplicationService.update(...)`

所以在当前代码里，“发帖”和“评论 / 编辑评论”都会受禁言 / 封禁影响。

---

## 5. IM 侧如何使用处罚状态

IM 侧不再由 `community-app` 提供逐条私信守卫，而是由 `im-realtime` 本地 policy projection 判定。

快照与本地判定相关代码：

- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotService.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicySnapshotClient.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicyProjectionService.java`

入口方法：

- `PolicyProjectionService.canSendPrivateMessage(UUID fromUserId, UUID toUserId)`

它会根据 `community-app` 提供的 user policy snapshot 和后续 Kafka policy change 事件判定：

- 发送方 / 接收方是否存在
- 发送方是否禁言或封禁
- 接收方是否允许私信

这条本地判定被：

- `ImWebSocketHandler.handleSendPrivate(...)`

调用。

所以 IM 私信发送前会检查处罚状态，但不是每次发送都同步回源 `community-app`。

---

## 6. 当前能力边界

### 6.1 内容同步回源，IM 本地投影

内容侧 guard 的注释明确说明了：

- 已移除本地投影
- 内容写路径同步依赖 `user` 模块的实时可用性

IM 侧则相反：`im-realtime` 本地持有 policy projection，`community-app` 提供 bootstrap snapshot 和 policy change outbox。

也就是说：

- 内容写路径每次同步向 `user` 域回源
- IM 发送路径每次查询 `im-realtime` 本地 projection

### 6.2 只覆盖“能不能说话 / 发消息”

当前代码能明确看到的限制面有：

- 发帖
- 评论 / 回复
- 私信发送

文档里不应把它扩大解读成“全站所有能力都受同一处罚状态控制”，因为当前代码没证明这一点。

### 6.3 数字 userId 旧接口已废弃

内容侧 guard 还保留了一个：

- `assertCanSpeak(int userId)`

但当前实现直接报错：

- `numeric userId 已不再受支持`

这说明当前系统已经明确切到 UUID 路径。

---

## 7. 失败语义

常见失败点：

- `userId` 为空
- 用户不存在
- `banUntil` 尚未到期
- `muteUntil` 尚未到期

这些失败都发生在主写链路前置校验阶段，不会进入真正的帖子 / 评论 / 私信主写逻辑。

---

## 8. 一句话总结

当前用户处罚状态链路的核心思路是：

- 状态统一 owned by `user` 域
- 处罚命令主要由举报治理链路触发
- 内容在写入前同步回源检查 `muteUntil / banUntil`，IM 在 `im-realtime` 本地 policy projection 中检查
- 服务层已经支持 `unmute / unban`，但当前公开业务入口主要还是处罚而不是解除处罚
