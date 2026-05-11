# Growth 成长业务逻辑

成长域当前保留任务模板、任务进度、事件去重、自动奖励和用户等级计算。旧的签到 controller、任务中心页面接口、手动领奖接口等不属于当前后端真实暴露面。

## Owner / SSOT

- growth owns `task_template`、`user_task_progress`、`user_task_event_log` 和 `user_level_rule_config`。
- wallet owns 在线余额和奖励资金事实。
- content/social 通过 owner API 触发成长事件。
- 用户等级不是 `user` 表上的实时字段，而是按成长规则计算的结果。

## 入口

同步 owner API：

- `GrowthTaskProgressActionApi.triggerPostPublished(...)`
- `GrowthTaskProgressActionApi.triggerCommentCreated(...)`
- `GrowthTaskProgressActionApi.triggerLikeCreated(...)`
- `UserLevelQueryApi.evaluateLevel(...)`

应用服务：

- `TaskProgressApplicationService.processEvent(...)`
- `triggerPostPublished(...)`
- `triggerCommentCreated(...)`
- `triggerLikeCreated(...)`
- `UserLevelApplicationService.evaluateLevel(...)`
- `getConfig(...)`
- `updateConfig(...)`

当前没有公开 growth controller。

## 数据流

成长域当前不是面向浏览器的独立业务面，而是被内容和社交事件驱动：

1. 事件入口：content/social 通过 `GrowthTaskProgressActionApi` 把发帖、评论、点赞创建等业务事件转成 growth command，进入 `TaskProgressApplicationService`。
2. 去重：application 按 `sourceEventId` 先写 `user_task_event_log`。唯一约束冲突表示同一源事件已经处理，直接跳过，避免重复推进任务。
3. 进度推进：按事件类型查询 active `TaskTemplate`，计算 `periodKey`，确保并锁定 `user_task_progress`，再由 domain service capped 增加进度并判断是否达到目标。
4. 奖励：达到目标后，如果模板需要手动领取，状态变 `CLAIMABLE`；如果自动发奖，生成稳定 reward grant id 并调用 `WalletRewardActionApi`，由 wallet owner 完成总账入账和幂等。
5. 等级：等级不是 user 表字段；查询时 `UserLevelApplicationService` 读取规则配置和任务完成统计，实时计算 level。

## 任务进度模型

核心概念：

- `TaskTemplate`：任务模板，包含任务编码、任务类型、周期类型、触发事件类型、目标值、奖励、是否需要手动领取、展示顺序、状态。
- `UserTaskProgress`：某用户某任务某周期的进度。
- `UserTaskEventLog`：source event 去重表，防止同一业务事件重复推进。
- `periodKey`：由周期类型和业务日期计算，常见为日维度字符串。

任务状态：

- `IN_PROGRESS`：未达成。
- `CLAIMABLE`：已达成但需要手动领取。
- `CLAIMED`：已达成且奖励已发放或无需再处理。

## 事件触发任务

`TaskProgressApplicationService` 主流程：

1. 调用方传入 userId、triggerEventType、sourceEventId 和发生时间。
2. application 将发生时间转换为 growth business date。
3. `TaskProgressDomainService.isProcessableEvent(...)` 校验事件可处理。
4. 按 triggerEventType 查询 active 任务模板。
5. 对每个模板计算 periodKey。
6. 先向 `user_task_event_log` 插入 source event，插入冲突表示重复事件，直接跳过。
7. 确保 `user_task_progress` 行存在。
8. 锁定 progress 行。
9. 按目标值 capped 增加当前进度。
10. 若达到目标，设置 reachedAt。
11. 若模板需要领取，状态变为 `CLAIMABLE`。
12. 若模板不需要领取且未发奖，生成 rewardGrantId 并调用 wallet reward action。
13. 更新 progress 状态、进度、claimedAt、rewardGrantId 和 lastSourceEventId。

触发语义：

- 发帖事件推进发帖相关任务。
- 评论事件推进评论相关任务。
- 点赞创建事件推进被点赞用户的任务；actor 给自己点赞或目标用户为空会跳过。

## 奖励

`RewardGrantDomainService` 提供奖励幂等规则：

- sourceEventId 必须有效。
- task reward grant id 格式为 `task:<userId>:<taskCode>:<periodKey>`。
- reward amount 当前由 growth delta 和 balance delta 合并计算。

真正余额入账由 `WalletRewardActionApi.issue(...)` 完成。wallet owner 通过 requestId 和总账规则保证不重复记账。

当前没有公开的手动领取奖励接口；`CLAIMABLE` 状态是模型支持，不代表现有 HTTP 面已经开放领取。

## 用户等级

`UserLevelApplicationService.evaluateLevel(...)`：

1. 读取 active 等级规则配置。
2. 配置无效时使用默认配置：
   - windowDays = 100
   - lv2SignInDays = 12
   - lv3SignInDays = 88
3. 如果配置 disabled，返回 level 1，并标记规则未启用。
4. 计算窗口起止日期。
5. 统计用户在窗口内 `DAILY_CHECK_IN` 任务完成天数。
6. `UserLevelDomainService.levelForSignInDays(...)` 计算等级。

配置更新：

- 必须带 actorUserId。
- `enabled` 必填。
- windowDays、lv2、lv3 必须满足 domain service 校验。
- 更新当前配置；不存在时插入。

## 失败和一致性

- 任务事件重复由 `user_task_event_log` 唯一约束去重。
- progress 行创建并发由唯一约束处理，冲突后重新锁定读取。
- 钱包奖励失败会让当前任务推进事务失败；调用方需要按业务语义重试。
- 等级计算是查询时计算，不依赖异步投影。

## 关键代码

- `growth.application.TaskProgressApplicationService`
- `growth.application.UserLevelApplicationService`
- `growth.application.GrowthBusinessTimeService`
- `growth.domain.service.TaskProgressDomainService`
- `growth.domain.service.TaskPeriodKeyResolver`
- `growth.domain.service.RewardGrantDomainService`
- `growth.domain.service.UserLevelDomainService`
- `growth.infrastructure.api.GrowthTaskProgressActionApiAdapter`
- `growth.infrastructure.api.UserLevelQueryApiAdapter`
- `wallet.api.action.WalletRewardActionApi`
