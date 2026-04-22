# growth 签到与任务中心链路现状说明

这篇文档专门说明一件事：当前仓库里“签到 / 任务中心”并不是一个已经完整落地的前后台功能面，而是只保留了成长任务投影与等级计算底座，旧的签到与任务中心表面已经被明确退休。

如果你先看到了设计稿、历史计划或旧命名，很容易误以为项目里已经有：

- `GrowthCenterController`
- `CheckInService`
- `TaskCenterService`
- `growth_check_in` 表
- 任务中心读接口 / 领奖接口

当前代码不是这个状态。

## 1. 先给结论

当前 growth 域真实存在的是：

- 任务模板 `task_template`
- 任务进度 `user_task_progress`
- 任务事件去重日志 `user_task_event_log`
- 奖励发放记录 `reward_grant_record`
- 用户等级规则 `user_level_rule_config`
- 内容 / 社交 / growth 事件驱动的任务投影
- 基于签到任务完成数的等级计算

当前 growth 域明确不存在的是：

- 对外签到 controller
- 签到事实表
- 任务中心 controller / service / DTO
- 手动领取奖励接口
- growth 专属 security rules
- 奖励商城、排行榜等旧 growth 面

所以这块现在更准确的定位是：

- “成长任务计算内核还在”
- “签到与任务中心产品面已经退休或尚未重建”

## 2. 旧签到与任务中心表面已经被显式退休

证据不靠猜，而是代码里有专门的退休测试：

- `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`

这个测试明确要求以下类不能再存在于 classpath：

- `com.nowcoder.community.growth.controller.GrowthController`
- `com.nowcoder.community.growth.controller.GrowthCenterController`
- `com.nowcoder.community.growth.service.CheckInService`
- `com.nowcoder.community.growth.service.TaskCenterService`
- `com.nowcoder.community.growth.security.GrowthSecurityRules`
- `com.nowcoder.community.growth.entity.GrowthCheckIn`
- `com.nowcoder.community.growth.mapper.GrowthCheckInMapper`

它还额外断言：

- `growth_check_in_mapper.xml` 不存在
- schema 中不允许再出现 `growth_check_in`

这说明“签到与任务中心旧实现被移除”不是偶然，而是仓库层面明确执行过一次 retirement。

## 3. 当前 growth 域到底还剩下什么

### 3.1 数据底座

当前 schema 里仍然保留了成长任务需要的几张核心表：

- `task_template`
- `user_task_progress`
- `user_task_event_log`
- `reward_grant_record`
- `user_level_rule_config`

对应 mapper / entity 也都还在：

- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/TaskTemplate.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/UserTaskProgress.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardGrantRecord.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/UserLevelRuleConfig.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/TaskTemplateMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/UserTaskProgressMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/UserTaskEventLogMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardGrantRecordMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/UserLevelRuleConfigMapper.java`

### 3.2 任务模板种子

测试 schema 里当前种了 4 个模板：

- `DAILY_CHECK_IN`
- `DAILY_POST`
- `WEEKLY_COMMENTER`
- `LIFETIME_RECEIVE_LIKE`

它们分别绑定事件：

- `DailyCheckIn`
- `PostPublished`
- `CommentCreated`
- `LikeCreated`

而且当前种子里 `claim_required` 全是 `false`。

这意味着：

- 数据模型支持“需要手动领取”
- 但当前默认模板仍然全部走自动发奖

## 4. 签到写路径现在是什么状态

### 4.1 growth 事件接口仍然存在

当前仍有：

- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/payload/CheckInPayload.java`

`LocalGrowthEventPublisher.publishCheckInCompleted(...)` 会发布：

- `GrowthLocalEvent`
- `type = GrowthEventTypes.CHECK_IN_COMPLETED`

而 `GrowthEventTypes.CHECK_IN_COMPLETED` 当前常量值是：

- `DailyCheckIn`

也就是说，growth 域仍然保留了“签到完成事件”的标准输入格式。

### 4.2 但当前仓库没有真实生产者

全仓库搜索结果表明：

- `publishCheckInCompleted(...)` 没有任何生产代码调用点
- 只剩接口定义、发布器实现、测试和旧计划文档

这很关键。

它说明当前代码虽然知道“如果有签到完成事件，应该怎样推进任务”，但并没有实际的签到写入口去产生这个事件。

### 4.3 也没有签到事实表

当前 schema 已明确退休：

- `growth_check_in`

因此现在不存在“每个用户每个业务日一行签到记录”这一层主事实存储。

所以如果你要追问：

- 今天用户有没有签到
- 连签几天
- 月历哪些天亮了

当前代码里没有一条完整的在线写读链路回答这些问题。

## 5. 任务中心读模型现在是什么状态

### 5.1 没有对外任务中心接口

当前 `backend/community-app/src/main/java/com/nowcoder/community/growth` 目录下：

- 没有 controller
- 没有 `TaskCenterService`
- 没有 `TaskCenterResponse`
- 没有 `TaskItemResponse`

所以现在并不存在：

- `GET /api/growth/tasks`
- `POST /api/growth/tasks/{taskCode}/claim`
- `GET /api/growth/check-in/status`
- `GET /api/growth/check-in/calendar`

这类任务中心 / 签到中心接口。

### 5.1.1 这也意味着没有单独的 growth 授权面

因为：

- 没有 growth controller
- `GrowthSecurityRules` 也已被 retirement test 明确退休

所以当前 growth 域不是“接口还在，只是没人调”，而是连独立 HTTP 表面和对应授权矩阵都已经收掉了。

### 5.2 也没有任务领取写路径

虽然 `TaskTemplate.claimRequired` 和 `UserTaskProgress.status=CLAIMABLE` 都还在模型里，但当前代码里没有看到：

- claim controller
- claim service
- claim action api

这意味着“手动领奖”只存在于数据模型预留，不存在当前仓库的业务入口。

## 6. 当前真正还在工作的 growth 链路

这部分已经在 [growth-task-grant-level-flow.md](./growth-task-grant-level-flow.md) 里详细展开，这里只说和签到 / 任务中心有关的部分。

### 6.1 任务推进内核还在

`TaskProgressService.processEvent(...)` 仍然会：

1. 按 `triggerEventType` 找激活模板
2. 用 `user_task_event_log` 做去重
3. 确保有 `user_task_progress` 行
4. 推进 `current_value`
5. 达标后自动发奖或进入 `CLAIMABLE`

### 6.2 当前真实能推进的任务

在当前仓库里，真正有生产代码输入的主要是：

- 发帖
- 评论
- 被点赞

因为这些都有内容 / 社交域的真实事件来源。

签到任务虽然模板还在，但要推进它，必须先有人发布：

- `DailyCheckIn`

当前这一步没有接线。

### 6.2.1 “有签到任务模板”不等于“有签到功能”

当前最容易误读的一点就是：

- schema 里仍然有 `DAILY_CHECK_IN`
- growth 里仍然有 `DailyCheckIn` 事件类型

但这只能证明成长底座还认识“签到完成”这种输入，不代表当前仓库已经提供了：

- 用户签到写接口
- 签到事实存储
- 月历状态读取
- 任务中心展示

### 6.3 自动发奖仍然可用

任务达标后仍会通过：

- `UnifiedGrantService`
- `WalletRewardActionApi`

把奖励记到 `reward_grant_record`，再写入钱包奖励流水。

所以 growth 的“奖励会计底座”没有退休，只是“签到 / 任务中心产品面”没有在当前代码里暴露出来。

## 7. 用户等级现在是什么状态

### 7.1 等级计算服务仍然存在

`UserLevelService` 仍然实现了：

- `UserLevelQueryApi.evaluateLevel(UUID userId)`

它的等级依据仍是：

- `DAILY_CHECK_IN` 任务在配置窗口内完成了多少天

### 7.2 但当前用户页没有真正启用它

`GetUserProfilePageQuery` 虽然注入了 `UserLevelQueryApi`，但当前实现直接写死了：

- `UserLevelSummaryView levelSummary = null`
- `boolean userLevelEnabled = false`

而且测试也明确验证：

- `userLevelEnabled == false`
- `userLevel == null`
- `signInDaysInWindow == null`
- `userLevelQueryApi` 不会被调用

这表示等级底座还在，但用户页聚合暂时没有打开这条能力。

### 7.3 这条降级行为同样被测试固定住了

`GetUserProfilePageQueryTest` 当前不是只验证返回值，而是明确断言：

- `userLevelQueryApi` 不会被调用

所以这里不是“实现还没补完”的模糊中间态，而是当前代码显式承认的降级现状。

## 8. 为什么会出现“底座在，产品面没了”的状态

从代码现状看，这次重构保留了三类能力：

- 任务投影
- 奖励发放
- 等级计算规则

同时主动移除了旧的：

- 签到表面
- 任务中心表面
- 奖励商城表面

这通常意味着项目处于一种过渡态：

- 不再维护旧 growth 产品
- 但仍保留成长计算内核，供 profile、奖励、钱包、未来功能继续复用

## 9. 对初学者最重要的判断

如果你想快速理解当前代码，不要把 growth 当成“完整签到系统”去读，而要把它理解成：

- 一个以事件驱动为主的成长投影底座

当前最接近“可运行主链路”的是：

- 内容 / 社交事件 -> 任务进度 -> 自动发奖 -> 钱包

当前还不是完整实现的是：

- 用户主动签到 -> 月历状态 -> 任务中心聚合 -> 手动领奖

## 10. 推荐的阅读顺序

如果你想顺着当前仍然在线的 growth 能力往下读，顺序应该是：

1. `growth-task-grant-level-flow.md`
2. `wallet-ledger-flow.md`
3. `user-profile-avatar-flow.md`

如果你先看历史计划里的 `/api/growth/*`，很容易把目标态误读成现状。

## 11. 和设计稿之间的关系

仓库里仍然保留了一份相关计划：

- `docs/superpowers/plans/2026-03-22-community-signin-task-center.md`

这份文档描述的是一个更完整的目标状态，包括：

- `GrowthCenterController`
- `CheckInService`
- `TaskCenterService`
- `/api/growth/check-in*`
- 任务中心页面

但它是计划，不是当前代码现实。

看当前仓库时，应始终以：

- 已存在 Java 类
- 已存在 schema
- 已存在调用点

为准。

## 12. 关键代码定位

- `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventTypes.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UserLevelService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java`
- `backend/community-app/src/test/resources/schema.sql`
