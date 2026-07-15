# Social 核心类细分

本文是 [../social.md](../social.md) 的类级补充。social 只管关系事实：点赞、关注、拉黑，以及这些关系带来的事件。

## 先读顺序

1. `LikeApplicationService`
2. `LikeCleanupReconciliationApplicationService`
3. `FollowApplicationService`
4. `BlockApplicationService`
5. `LikeDomainService`
6. `FollowDomainService` / `BlockDomainService`

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `social.application.LikeApplicationService` | 点赞关系、resolved target 校验、删除 fencing、清理和事件。 | 看 interaction 提供的可信目标如何在 social 写模型中再次校验。 |
| `social.application.LikeCleanupReconciliationApplicationService` | 扫描 deleted target 上的遗留点赞并重跑 owner cleanup。 | 看分页游标、source version 和失败计数。 |
| `social.application.FollowApplicationService` | 关注关系和 follow event。 | 看它如何保持关注关系幂等。 |
| `social.application.BlockApplicationService` | 拉黑关系、follow 清理和 owner event。 | 看它如何同时维护 social 主事实并驱动 IM 下游。 |

## 领域服务

| 类 | 核心职责 |
| --- | --- |
| `social.domain.service.LikeDomainService` | 点赞实体和计数规则。 |
| `social.domain.service.FollowDomainService` | 关注关系规则。 |
| `social.domain.service.BlockDomainService` | 拉黑关系和 follow 清理规则。 |

## 基础设施

| 类 | 核心职责 |
| --- | --- |
| `social.infrastructure.api.*` | 给 content / user / IM 等域提供关系查询和清理的同步适配。 |
| `social.infrastructure.persistence.*` | like、follow、block 的 MyBatis / Redis 持久化。 |
| `social.infrastructure.event.OutboxSocialDomainEventPublisher` | social domain event 映射后写 `eventbus.social`。 |
| `social.infrastructure.event.SocialEventKafkaOutboxHandler` | owner outbox 进入 dispatch application。 |
| `social.infrastructure.event.SocialEventKafkaSenderAdapter` | 发布 `social.events`。 |
| `social.infrastructure.event.SocialContentDeletionKafkaListener` | 消费 content 删除事件并进入 `LikeApplicationService`。 |
| `social.infrastructure.job.SocialLikeCleanupReconciliationJob` | 扫描 deleted post/comment target 的 reconciliation 入口。 |

## 关键语义

- 点赞写入口先经过 interaction；social 不做 foreign owner 查询，只校验 resolved target 并拥有最终关系写入。
- `LikeTargetState` 的正数 source version 阻止迟到删除事件覆盖新状态，deleted target 禁止新增点赞。
- 拉黑会同时影响 follow 清理和 IM policy。
- like / follow / block 都是关系事实，不是内容事实。
