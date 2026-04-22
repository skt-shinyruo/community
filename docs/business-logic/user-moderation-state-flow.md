# 用户禁言 / 封禁状态链路实现说明

本文档说明当前仓库中用户处罚状态的实际实现路径，聚焦以下问题：

- 用户的禁言 / 封禁状态存在哪里
- 这些状态是谁写入的
- 发帖、评论、私信发送前如何同步检查这些状态
- 当前是否有独立的解封 / 解除禁言入口

相关文档：

- `docs/business-logic/report-moderation-flow.md`
- `docs/business-logic/social-block-im-governance-flow.md`

---

## 1. 参与组件

- `UserModerationService`：用户处罚状态 owner
- `ModerationCommandListener`：消费来自内容治理的处罚命令
- `content.UserModerationGuard`：内容侧发言守卫
- `im.governance.UserModerationGuard`：私信发送守卫
- `PrivateMessageGovernanceService`：IM 发送前治理编排
- `TakeModerationActionUseCase`：处罚命令的上游发起者
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

1. `TakeModerationActionUseCase`
2. `UserModerationCommandPublisher.publishModerationCommand(...)`
3. 发布 `MODERATION_COMMAND_REQUESTED`
4. `ModerationCommandListener`
5. `UserModerationService.applyModeration(...)`

### 3.2 `UserModerationService.applyModeration(...)`

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

- `backend/community-app/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`

入口方法：

- `assertCanSpeak(UUID userId)`

主逻辑：

1. 通过 `UserModerationQueryApi.getModerationState(userId)` 回源读取当前处罚状态
2. 如果 `banUntil > now`
   - 拒绝：`账号已被封禁，无法发言`
3. 如果 `muteUntil > now`
   - 拒绝：`你已被禁言，暂时无法发言`

这条守卫被以下主链路调用：

- `CreatePostUseCase.createPost(...)`
- `UpdatePostUseCase.updatePost(...)`
- `CommentService.addComment(...)`
- `CommentService.updateComment(...)`

所以在当前代码里，“发帖”和“评论 / 编辑评论”都会受禁言 / 封禁影响。

---

## 5. IM 侧如何使用处罚状态

IM 侧守卫在：

- `backend/community-app/src/main/java/com/nowcoder/community/im/governance/UserModerationGuard.java`

入口方法：

- `assertCanSendMessage(UUID userId)`

它的判定逻辑与内容侧几乎完全一致，只是报错文案变成：

- 封禁：`账号已被封禁，无法发送私信`
- 禁言：`你已被禁言，暂时无法发送私信`

这条守卫被：

- `PrivateMessageGovernanceService.validateCanSendPrivateMessage(...)`

调用。

所以 IM 私信发送前也会同步回源检查处罚状态。

---

## 6. 当前能力边界

### 6.1 同步回源，不做本地投影缓存

两个 guard 的注释都明确说明了：

- 已移除本地投影
- 写路径同步依赖 `user` 模块的实时可用性

也就是说，当前不是“把处罚状态同步一份到 content / im 再本地判断”，而是：

- 每次写路径前同步向 `user` 域回源

### 6.2 只覆盖“能不能说话 / 发消息”

当前代码能明确看到的限制面有：

- 发帖
- 评论 / 回复
- 私信发送

文档里不应把它扩大解读成“全站所有能力都受同一处罚状态控制”，因为当前代码没证明这一点。

### 6.3 数字 userId 旧接口已废弃

两个 guard 都保留了一个：

- `assertCanSpeak(int userId)`
- `assertCanSendMessage(int userId)`

但当前实现都是直接报错：

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
- 内容和 IM 在写入前同步回源检查 `muteUntil / banUntil`
- 服务层已经支持 `unmute / unban`，但当前公开业务入口主要还是处罚而不是解除处罚
