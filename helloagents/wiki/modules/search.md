# search

## Purpose
提供帖子全文检索能力，并与发帖/删帖事件联动维护索引（迭代 1 起迁移到 `search-service`）。

## Module Overview
- **Responsibility：** ES 索引维护（保存/删除）；按关键字搜索；高亮 title/content
- **Status：** ✅Stable
- **Last Updated：** 2026-02-03

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
 - 通过 `community_search.search_consumed_event` 做 eventId 幂等去重（先判定是否已消费），避免重复索引副作用
 - 幂等点位：ES upsert/delete 成功后再写入 consumed 表，避免 ES 故障导致“已标记但未更新”的丢失窗口
 - 幂等表按 `consumed_at` 定期清理（可配置 retention-days）

#### Scenario: 消费删帖事件删除索引
- Kafka 消费 delete 事件
- 从 ES 删除帖子
 - 通过 `community_search.search_consumed_event` 做 eventId 幂等去重（delete 成功后再标记 consumed）

#### Scenario: 零停机 reindex（alias/蓝绿）
- alias 固定：`community_posts_alias`
- 实际索引命名：`community_posts_v{yyyyMMddHHmmss}`（保留 N 个历史索引）
- 流程：创建新索引 → 扫描 content-service 回填 → alias 切换 → 清理旧索引

## API Interfaces（现状）
- `GET /api/search/posts?keyword=xxx&categoryId=&tag=`（支持 taxonomy 过滤；返回 `categoryId/tags[]` 供前端展示/二次筛选）
- `POST /api/ops/search/reindex`（仅管理员；高风险运维入口，建议配合限流/审计使用）
  - Response: `{ jobId, indexedCount }`
- `POST /api/search/internal/reindex`（历史兼容入口：弃用中，默认禁用；gateway 通过 blocked-path-patterns 关闭，按 404 拒绝并提示迁移到 `/api/ops/search/reindex`）
- `POST /internal/search/reindex`（服务内部入口：开发阶段默认放行）

## Configuration Notes
- `search.storage=es|memory`
  - 默认推荐：`es`（生产/部署）
  - 测试/演示：可显式切到 `memory`（如 `search-service/src/test/resources/application.yml`）

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
- 2026-01-20：索引与搜索联动 taxonomy：ES 文档增加 `categoryId/tags`，`/api/search/posts` 支持 `categoryId/tag` 过滤，前端搜索页可按分类/标签缩小范围。
- 2026-01-28：search-service 幂等改为 insert-first + 定时清理；reindex 引入 alias/蓝绿切换并支持清理旧索引。
- 2026-02-01：Kafka consumer 统一使用 `EventEnvelopeParser` + `UnknownEventAction`（unknown type/version 可配置 + 降噪），降低事件契约演进带来的 DLQ 噪声与阻塞风险。
- 2026-02-03：reindex single-flight 增加锁续租（owner=jobId + 原子 renew）避免长任务锁过期并发重建；legacy `/api/search/internal/reindex` 默认禁用并返回迁移提示，降低误用与攻击面。
- 2026-02-03：`search_consumed_event` 清理任务改为分批 delete（`order by consumed_at, id limit N`），并支持可选 single-flight（多实例避免重复执行）；索引对齐为 `idx_search_consumed_at(consumed_at, id)`。
