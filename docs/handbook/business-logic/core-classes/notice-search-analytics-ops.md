# Notice / Search / Analytics / Ops 核心类细分

本文是 [../notice-search-analytics-ops.md](../notice-search-analytics-ops.md) 的类级补充。这里把四个支撑域放在一起，因为它们围绕读模型、投影、采集和运行治理协作。

## 先读顺序

1. Notice：`NoticeApplicationService` -> `NoticeProjectionApplicationService`
2. Search：`SearchApplicationService` -> `SearchPostProjectionApplicationService`
3. Analytics：`AnalyticsRequestCaptureApplicationService` -> `AnalyticsIngestApplicationService`
4. Ops：`ProjectionGovernanceApplicationService` / `OutboxGovernanceApplicationService`

## Notice

| 类 | 核心职责 |
| --- | --- |
| `notice.application.NoticeApplicationService` | 通知写入、列表、未读数和批量已读。 |
| `notice.application.NoticeProjectionApplicationService` | content / social / moderation event 到通知读模型。 |
| `notice.domain.service.NoticeDomainService` | 通知分页、状态和创建校验。 |
| `notice.domain.service.NoticeProjectionDomainService` | 通知投影规则。 |
| `notice.infrastructure.event.NoticeProjectionKafkaListener` | 从 owner Kafka contract event 进入 notice application。 |
| `notice.infrastructure.persistence.MyBatisNoticeRepository` | notice 读模型持久化。 |

## Search

| 类 | 核心职责 |
| --- | --- |
| `search.application.SearchApplicationService` | 搜索查询。 |
| `search.application.SearchPostProjectionApplicationService` | Kafka event 触发后回源 content 并 upsert/delete ES。 |
| `search.domain.service.PostSearchDomainService` | 搜索 query 规则。 |
| `search.domain.service.KeywordHighlightSupport` | 搜索关键词高亮。 |
| `search.infrastructure.event.SearchPostProjectionKafkaListener` | 从 `content.events` 识别帖子投影事件并进入 application。 |
| `search.infrastructure.persistence.PostIndexManager` | ES alias / index 管理。 |
| `search.infrastructure.persistence.ElasticsearchPostSearchRepository` | ES 读写实现。 |

## Analytics

| 类 | 核心职责 |
| --- | --- |
| `analytics.application.AnalyticsApplicationService` | UV / DAU 查询和区间校验。 |
| `analytics.application.AnalyticsRequestCaptureApplicationService` | async publisher 可用时发布，否则同步 ingest。 |
| `analytics.application.AnalyticsIngestApplicationService` | 请求 / 登录成功采集写入。 |
| `analytics.domain.service.AnalyticsDomainService` | UV / DAU 查询区间规则。 |
| `analytics.domain.service.AnalyticsIngestDomainService` | UV / DAU 是否记录规则。 |
| `analytics.infrastructure.web.AnalyticsRequestCaptureFilter` | 请求完成后的采集过滤器。 |
| `analytics.infrastructure.web.AnalyticsRequestClassifier` | include / exclude / status / method 判定。 |
| `analytics.infrastructure.event.AnalyticsRequestKafkaListener` | `analytics.request` 到 ingest application。 |
| `analytics.infrastructure.web.AnalyticsPrincipalResolver` | 采集用 principal 解析。 |
| `analytics.infrastructure.api.AnalyticsIngestActionApiAdapter` | 登录成功采集的 action API 适配。 |
| `analytics.infrastructure.persistence.RedisAnalyticsRepository` | Redis 统计存储。 |
| `analytics.infrastructure.persistence.RedisAnalyticsUserOrdinalRepository` | DAU ordinal 映射存储。 |

## Ops

| 类 | 核心职责 |
| --- | --- |
| `ops.application.ProjectionGovernanceApplicationService` | 通过 application port 查询 projection outbox lag。 |
| `ops.controller.ProjectionOpsController` | `/api/ops/projections/lag` HTTP binding。 |
| `ops.infrastructure.outbox.OutboxProjectionLagAdapter` | 聚合 projection topic 的 `PENDING/PROCESSING/DEAD` 数量与最老年龄。 |
| `ops.application.OutboxGovernanceApplicationService` | outbox 状态、retry 与治理审计。 |
| `ops.application.CompensationGovernanceApplicationService` | allowlisted compensation trigger 和 owner repair 协作。 |
| `ops.application.HotCacheGovernanceApplicationService` | hot-cache status/prewarm/degradation 的跨 owner 编排。 |
| `content.application.HotFeedCacheGovernanceApplicationService` | content owner 的 hot-cache 查询和动作实现。 |

## 关键语义

- Notice、Search、Analytics 都是下游读模型或采集，不拥有上游主事实；Ops 只观察或编排治理动作。
- Notice 投影失败按 Kafka retry / `.dlq` 恢复，不回滚上游事务。
- Search 永远回源 content owner，不把 event payload 当成索引事实。
- Analytics 采集失败不影响业务响应。
- Projection lag 接口只统计 outbox backlog，不等于 Kafka consumer lag，也不直接修数据。
