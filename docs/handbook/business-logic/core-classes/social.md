# Social 核心类细分

本文是 [../social.md](../social.md) 的类级补充。social 只管关系事实：点赞、关注、拉黑，以及这些关系带来的事件。

## 先读顺序

1. `LikeApplicationService`
2. `FollowApplicationService`
3. `BlockApplicationService`
4. `LikeDomainService`
5. `FollowDomainService`
6. `BlockDomainService`

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `social.application.LikeApplicationService` | 点赞关系、内容实体解析、任务 / 积分协作和事件。 | 看它如何回源 content owner 解析目标。 |
| `social.application.FollowApplicationService` | 关注关系和 follow event。 | 看它如何保持关注关系幂等。 |
| `social.application.BlockApplicationService` | 拉黑关系、follow 清理和 owner event。 | 看它如何同时维护 social 主事实并驱动 IM 下游。 |
| `social.application.ContentEntityResolver` | 解析 content owner 实体。 | 看它如何把内容实体类型和目标 ID 规范化。 |

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

## 关键语义

- social 不信任客户端提交的目标实体，尤其是点赞时的 post owner 解析。
- 拉黑会同时影响 follow 清理和 IM policy。
- like / follow / block 都是关系事实，不是内容事实。
