# 成长任务、奖励发放与用户等级链路实现说明

本文说明当前代码里“事件如何推进任务进度、任务如何发奖、等级如何计算”的真实实现。它不是一个独立 HTTP 模块，而是一组挂在内容、社交、签到事件之后的投影与奖励链路。

## 1. 这条链路解决什么问题

成长域当前主要承担三件事：

- 把帖子、评论、被点赞、签到等事件投影成任务进度
- 在任务达成或积分投影时，统一落奖励发放记录，并把增量写入钱包
- 基于签到类任务完成情况计算用户等级

这意味着成长域本身不是“主事实写入口”，它更像一层业务投影器：

- 上游模块产生日志事实
- 成长域消费事件
- 成长域更新进度、发放奖励、输出等级视图

## 2. 关键入口与核心类

### 2.1 任务进度投影入口

- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`

### 2.2 任务状态推进核心

- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressService.java`

### 2.3 奖励统一发放核心

- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/api/action/GrowthGrantActionApi.java`

### 2.4 用户等级查询与配置核心

- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UserLevelService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/api/query/UserLevelQueryApi.java`

## 3. 上游事件如何进入成长域

`TaskProgressProjectionService` 负责把不同领域事件统一翻译成：

- `userId`
- `triggerEventType`
- `sourceEventId`
- `bizDate`

也就是 `TaskProgressProjectionCommand`。

当前已接入三类事件。

### 3.1 内容事件

支持：

- `POST_PUBLISHED`
- `COMMENT_CREATED`

对应逻辑：

- 发帖事件取 `PostPayload.userId`
- 评论事件取 `CommentPayload.userId`
- 业务日期从事件里的 `createTime` 转成业务日

也就是说，谁发帖、谁评论，谁的任务进度就被推进。

### 3.2 社交事件

支持：

- `LIKE_CREATED`

这里不是给点赞人加任务，而是给被点赞对象加任务：

- 取 `LikePayload.entityUserId` 作为任务用户
- 如果 `entityUserId == actorUserId`，直接忽略

所以它表达的是“内容收到别人的点赞”这一类成长行为，而不是“我点了一个赞”。

### 3.3 成长域本地事件

支持：

- `CHECK_IN_COMPLETED`

这里直接从 `CheckInPayload` 取：

- `userId`
- `bizDate`

签到完成后，成长域会再反过来推动签到类任务累计。

## 4. 投影是怎么触发的

当前代码同时保留了两种消费方式。

### 4.1 事务提交后直接监听

`TaskProgressProjectionListener` 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 监听：

- `ContentContractEvent`
- `SocialContractEvent`
- `GrowthLocalEvent`

流程很直接：

1. 上游事务提交
2. listener 收到事件
3. 交给 `TaskProgressProjectionService`
4. 翻译为统一命令
5. 调用 `TaskProgressService.processEvent(...)`

这种方式简单、实时，但依赖本地事务事件发布成功。

### 4.2 outbox 异步补位

代码里同时存在：

- `TaskProgressOutboxEnqueuer`
- `TaskProgressOutboxHandler`

这说明成长投影也支持通过 outbox 做异步追平，目的通常有两个：

- 降低主事务里直接投影的耦合
- 在跨模块投影中保留更稳定的补偿能力

对初学者来说，可以把它理解为：

- listener 负责“事务提交后立即做一次”
- outbox handler 负责“用可靠消息再追一遍”

真正的幂等，最终靠 `TaskProgressService` 内部的事件去重保证。

## 5. 任务进度状态机如何工作

核心方法在：

- `TaskProgressService.processEvent(UUID userId, String triggerEventType, String sourceEventId, LocalDate bizDate)`

### 5.1 第一步：按事件类型找激活中的任务模板

代码先做基础校验：

- `userId` 不能为空
- `triggerEventType` 不能为空
- `sourceEventId` 不能为空
- `bizDate` 不能为空

随后查：

- `taskTemplateMapper.selectActiveByTriggerEventType(triggerEventType.trim())`

也就是说，成长任务不是写死在 Java 代码里，而是由任务模板表决定：

- 什么事件会推动它
- 目标值是多少
- 周期是什么
- 是否需要手动领取
- 奖励是多少

### 5.2 第二步：按模板逐条应用

对每个模板，`applyTemplate(...)` 会做四件关键事。

#### 5.2.1 解析 periodKey

先用：

- `TaskPeriodKeyResolver.resolve(template.getPeriodType(), bizDate)`

把业务日转成周期键。常见理解：

- 日任务按天累计
- 周任务按周累计
- 生命周期任务可能是固定键

`periodKey` 是后续去重和进度聚合的核心维度。

#### 5.2.2 记录 source event，做幂等去重

先插入：

- `userTaskEventLogMapper.insert(id, userId, taskCode, periodKey, sourceEventId)`

如果唯一键冲突，捕获 `DataIntegrityViolationException` 并返回 `false`。

这意味着同一个：

- 用户
- 任务
- 周期
- 源事件

只能记一次。重复事件、重复投影、outbox 重放，都会在这里被挡住。

#### 5.2.3 确保有进度行

调用：

- `userTaskProgressMapper.insert(...)`

若并发下别人先插入成功，也只是吞掉冲突，继续走后面的 `select ... for update`。

这是典型的“先尝试插入，再加锁读取”的并发安全写法。

#### 5.2.4 锁定并推进进度

通过：

- `selectByUserTaskAndPeriodForUpdate(...)`

拿到当前进度行并加锁，然后计算：

- `nextValue = min(targetValue, currentValue + 1)`

进度不会超过目标值。

### 5.3 任务状态只有三种

`TaskProgressService` 里定义了三种状态：

- `IN_PROGRESS`
- `CLAIMABLE`
- `CLAIMED`

状态演进规则如下。

#### 5.3.1 未完成

如果 `nextValue < targetValue`：

- 状态保持 `IN_PROGRESS`

#### 5.3.2 已达成但需要手动领取

如果已达成且 `template.isClaimRequired()`：

- 状态变成 `CLAIMABLE`

这表示任务完成了，但奖励还没有真正发放。

#### 5.3.3 已达成且自动发奖

如果已达成且不需要手动领取：

1. 生成 `rewardGrantId = "task:" + userId + ":" + taskCode + ":" + periodKey`
2. 计算 `rewardAmount = rewardGrowthDelta + rewardBalanceDelta`
3. 如果奖励大于 0，则调用 `walletRewardActionApi.issue(...)`
4. 状态置为 `CLAIMED`
5. 记录 `claimedAt`

注意这里有个重要实现选择：

- 成长值奖励和余额奖励在这里被合并成一个 `rewardAmount`
- 最终由钱包奖励接口统一落账

所以当前实现里，“成长值”和“余额”在底层发放路径上并没有完全分离，而是统一走钱包奖励增量。

### 5.4 已经领过的不再重复发

如果当前行已经是：

- `status == CLAIMED`
- 且 `rewardGrantId != null`

那么直接返回，不再重复发放。

因此这条链路的重复保护有两层：

- 源事件日志去重
- 已领奖进度行短路

## 6. 积分投影为什么也会进钱包

这部分核心在：

- `UnifiedGrantService`

它实现了 `GrowthGrantActionApi`，最关键的方法是：

- `applyPointsProjection(userId, sourceEventId, sourceEventType, growthDelta)`

### 6.1 grant record 先落库

`applyPointsProjection(...)` 会把 `grantId` 固定成：

- `sourceEventId + ":points"`

随后进入 `applyGrant(...)`，先插入 `reward_grant_record`。

如果插入时唯一键冲突，直接返回 `false`。

这表示：

- 同一个 source event 的积分投影只会成功一次
- 积分投影具备天然幂等

### 6.2 再把增量交给钱包

插入成功后会算：

- `walletDelta = growthDelta + rewardDelta`

只要不为 0，就调用：

- `walletRewardActionApi.applyDelta(grantId, userId, walletDelta, grantType)`

这里要特别提醒初学者：

- 从业务命名看，这是“成长奖励/积分奖励”
- 从底层落账看，最终还是统一调用钱包奖励接口

所以钱包域不仅是充值提现转账的资金底座，也承接了成长奖励、积分投影等“内部奖励型资金事实”。

## 7. 用户等级是怎么计算出来的

核心类：

- `UserLevelService`

它实现了只读查询接口：

- `UserLevelQueryApi`

### 7.1 当前等级只依赖签到任务

当前代码写死使用任务码：

- `DAILY_CHECK_IN`

等级计算逻辑不是看 `user.score`，也不是看钱包余额，而是看某个窗口内完成了多少天签到任务。

### 7.2 默认规则

如果数据库里没有有效配置，就用默认值：

- 统计窗口 `100` 天
- 达到 `12` 天是 `LV2`
- 达到 `88` 天是 `LV3`

### 7.3 计算过程

`evaluateLevelSummary(...)` 会：

1. 读取当前启用配置
2. 计算窗口起点 `bizDate - windowDays + 1`
3. 调 `userTaskProgressMapper.countByUserTaskAndPeriodKeyRange(...)`
4. 统计用户在窗口内完成了多少个 `DAILY_CHECK_IN`
5. 用阈值判定 `LV1/LV2/LV3`

所以等级不是一个长期冗余字段，而是一次查询时的计算结果。

### 7.4 配置修改如何落库

`updateConfig(...)` 会先校验：

- `enabled` 不能为空
- 窗口、LV2、LV3 阈值必须合法
- `lv2 < lv3 <= windowDays`

然后：

- 优先 `updateCurrent(config)`
- 如果当前配置不存在，则尝试 `insert`
- 若并发插入冲突，再回退 `updateCurrent`

这是一种“先更新、再插入、冲突后重试更新”的并发兼容写法。

## 8. 这条链路与其他业务的关系

### 8.1 与内容域的关系

内容域发出：

- 发帖成功
- 评论成功

成长域订阅后推进任务。

### 8.2 与社交域的关系

社交域发出：

- 点赞成功

成长域把“被点赞人”视为成长对象推进任务。

### 8.3 与签到链路的关系

签到完成后发：

- `CHECK_IN_COMPLETED`

成长域以它为唯一可信输入，推进签到任务。

### 8.4 与钱包域的关系

无论是：

- 任务自动领奖
- 积分投影

最终都通过 `WalletRewardActionApi` 写入钱包奖励流水。

这说明钱包是奖励入账的统一会计底座。

## 9. 初学者最容易误解的点

### 9.1 成长域不是一个前台控制器模块

当前代码的核心并不在某个对外 controller，而在：

- 监听事件
- 投影进度
- 发奖
- 查等级

也就是说它首先是“领域内核”，其次才可能挂接前台页面。

### 9.2 任务完成不等于一定已经发奖

如果模板配置了 `claimRequired=true`：

- 任务会进入 `CLAIMABLE`
- 奖励并不会在投影时立即发放

只有自动领奖模板才会直接进入 `CLAIMED`。

### 9.3 等级并不是独立存储字段

当前实现是“按签到任务完成数实时计算”，不是把等级常驻写在 `user` 表里。

### 9.4 幂等不是靠 listener 保证的

真正的幂等在：

- `user_task_event_log`
- `reward_grant_record`

listener、outbox、重放只是触发方式，去重仍然发生在服务内部。

## 10. 可以据此继续补的文档点

如果后面还要继续深入，这条链路还能再补三类细节：

- 任务模板表结构与每种 `periodType` 的 periodKey 规则
- 签到写路径本身如何生成 `CHECK_IN_COMPLETED`
- 前台任务中心如果存在，读取接口如何把 `UserTaskProgress`、等级摘要、奖励状态拼成页面 DTO

## 11. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UserLevelService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/api/action/GrowthGrantActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/api/query/UserLevelQueryApi.java`
