# Growth 核心类细分

本文是 [../growth.md](../growth.md) 的类级补充。growth 负责任务进度、奖励协作和用户等级。

## 先读顺序

1. `TaskProgressApplicationService`
2. `RewardGrantDomainService`
3. `UserLevelApplicationService`
4. `UserLevelDomainService`
5. `GrowthBusinessTimeService`

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `growth.application.TaskProgressApplicationService` | 任务进度更新、事件去重和奖励触发。 | 看它如何处理重复事件和已完成任务。 |
| `growth.application.UserLevelApplicationService` | 用户等级查询和等级相关聚合。 | 看它如何把规则配置和用户进度合并。 |
| `growth.application.GrowthBusinessTimeService` | growth 的 business time source。 | 看日/周/月统计的时间切分口径。 |

## 领域服务

| 类 | 核心职责 |
| --- | --- |
| `growth.domain.service.TaskProgressDomainService` | 任务模板、进度推进和事件去重规则。 |
| `growth.domain.service.RewardGrantDomainService` | 奖励发放幂等规则。 |
| `growth.domain.service.UserLevelDomainService` | 等级计算和配置规则。 |

## 基础设施

| 类 | 核心职责 |
| --- | --- |
| `growth.infrastructure.event.TaskProgressEventBackboneKafkaListener` | 从 `content.events` / `social.events` 识别成长事件并进入 application。 |
| `growth.infrastructure.api.UserLevelQueryApiAdapter` | 对外暴露等级查询。 |
| `growth.infrastructure.persistence.*` | task template、progress、event log、level rule 的持久化。 |

## 关键语义

- growth 不直接记钱包账，只发奖励协作。
- 任务进度和奖励都要做事件去重；like removed 会撤销尚未 claimed 的原点赞贡献。
- 成长任务只从 owner Kafka contract event 推进，不存在同步 task action 或 secondary task topic。
- 用户等级读取依赖规则配置，不是静态枚举。
