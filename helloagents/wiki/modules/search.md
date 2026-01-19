# search

## Purpose
提供帖子全文检索能力，并与发帖/删帖事件联动维护索引（迭代 1 起迁移到 `search-service`）。

## Module Overview
- **Responsibility：** ES 索引维护（保存/删除）；按关键字搜索；高亮 title/content
- **Status：** ✅Stable
- **Last Updated：** 2026-01-19

## Specifications

### Requirement: 全局搜索
**Module:** search
用户可根据关键字搜索帖子，并高亮匹配内容。

#### Scenario: 搜索帖子并展示高亮
- 返回搜索结果与高亮字段
- 支持按置顶/分数/时间排序

### Requirement: 索引同步
**Module:** search
发帖/删帖后索引自动更新。

#### Scenario: 消费发帖事件写入索引
- Kafka 消费 publish 事件
- 保存帖子到 ES
 - 通过 `community_search.search_consumed_event` 做 eventId 幂等去重，避免重复索引副作用

#### Scenario: 消费删帖事件删除索引
- Kafka 消费 delete 事件
- 从 ES 删除帖子
 - 通过 `community_search.search_consumed_event` 做 eventId 幂等去重

## API Interfaces（现状）
- `GET /api/search/posts?keyword=xxx`
- `POST /api/search/internal/reindex`（仅管理员；用于迁移期 reindex）
- `POST /internal/search/reindex`（服务内部入口：需要 `X-Internal-Token`）

## Data Models
### Elasticsearch: discuss_post
（详见 `helloagents/wiki/data.md` 的 “Elasticsearch 索引” 小节）

## Dependencies
- content（帖子数据源；reindex 通过 content-service 内部 API 拉取）
- infra（Kafka、Elasticsearch）
- MySQL（仅 `community_search` schema：幂等去重表 `search_consumed_event`；不再跨 schema 直读内容域）

## Change History
- 2026-01-18：DLQ 指标与告警补齐（`kafka_dlq_published_total`），并将 search-service 幂等表归属迁移到独立 schema（`community_search`）。
- 2026-01-19：reindex 从“跨 schema 直读 content 表”升级为“调用 content-service 内部 API 扫描帖子”，支持严格的“每服务仅访问本 schema”。
