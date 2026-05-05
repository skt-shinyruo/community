# Notice / Search / Analytics / Ops 业务逻辑

本文覆盖四类支撑业务：站内通知读模型、搜索索引读模型、统计采集与查询、运维入口。这些域大多不是主事实 owner，而是从主业务事件派生读模型或提供后台操作。

## Notice 通知

### Owner / SSOT

- notice owns 站内通知记录、通知 topic、未读状态和通知摘要。
- content/social/moderation owns 源业务事实。
- notice content 是 JSON 快照，不是最终 UI 文案。

### 入口

HTTP：

- `GET /api/notices`
- `GET /api/notices/unread-count`
- `GET /api/notices/summary`
- `PUT /api/notices/read`

事件：

- content contract event。
- social contract event。
- moderation/content governance event。

### 读写流程

`NoticeApplicationService`：

- `createNotice(...)` 校验 toUserId、topic 和 contentJson 后写 unread notice。
- `listNoticeItems(...)` 按用户和 topic 分页查询。
- `unreadCount(...)` 返回未读数。
- `topicSummary(...)` 对默认 topic 返回最新通知、总数和未读数。
- `markRead(...)` 只把当前用户给定 ids 标记为 read。

`NoticeProjectionApplicationService`：

1. 接收 content/social contract event。
2. 根据事件类型解析收件人、topic 和 content JSON。
3. `NoticeProjectionDomainService.shouldProject(...)` 判断是否应投影。
4. 写 notice。

语义：

- 点赞、评论、关注和治理事件可生成通知。
- `LIKE_REMOVED` 和 `FOLLOW_REMOVED` 当前不撤销通知。
- 通知投影是 after-commit best-effort，失败不回滚上游主事务。

## Search 搜索

### Owner / SSOT

- content owns 帖子事实。
- search owns Elasticsearch 索引、查询语义、reindex job 和索引 alias。
- ES 是最终一致读模型，不是帖子事实。

### 入口

HTTP：

- `GET /api/search/posts`
- `POST /api/ops/search/reindex`

后台：

- `SearchReindexHandler` XXL job。
- content post event -> search outbox -> `PostOutboxHandler`。

### 查询流程

`SearchApplicationService.searchPosts(...)`：

1. `PostSearchDomainService.normalizeSearchQuery(...)` 规范化 keyword、categoryId、tag、page、size。
2. page/size 有上限，避免深分页风险。
3. repository 查询 ES。
4. 支持关键词、分类和标签组合过滤。
5. keyword 为空时可退化为 match-all。
6. 命中结果带关键词高亮。

### 投影流程

1. content 发布帖子事件。
2. `PostOutboxEnqueuer` 写 search projection outbox。
3. outbox worker dispatch 到 `PostOutboxHandler`。
4. handler 把 event payload 当作触发信号，不信任其作为索引事实。
5. handler 回源 content owner 当前帖子状态。
6. `PostSearchDomainService.shouldIndex(...)` 判断是否应索引。
7. 应索引则 upsert ES；不应索引则 delete ES。

### Reindex

`SearchReindexApplicationService.reindex(...)`：

1. 获取 single-flight 执行权。
2. 生成新索引名。
3. 创建索引并应用 mapping。
4. 启动 heartbeat/续期。
5. 游标分页扫描 content 当前权威快照。
6. 批量写入新索引。
7. 原子切换 alias。
8. 清理超出保留数量的旧索引。

reindex 失败不影响旧 alias 继续服务。

## Analytics 分析

### Owner / SSOT

- analytics owns UV/DAU 采集写入和统计查询。
- Redis 是当前主要统计存储。
- analytics 不影响核心业务写路径。

### 入口

HTTP：

- `GET /api/analytics/uv`
- `GET /api/analytics/dau`

采集：

- `AnalyticsRequestCaptureFilter` 在请求完成后采集。
- `AnalyticsIngestActionApi.recordLoginSuccess(...)` 在登录成功后采集 DAU。

### 采集规则

`AnalyticsRequestClassifier` 判断是否采集：

- analytics.ingest 开关未开启时直接跳过。
- 默认排除 `/api/analytics/**`、`/api/auth/**`、`/api/ops/**`、`/actuator/**`、`/internal/**`、`/files/**`。
- `OPTIONS` 不采集。
- HTTP 5xx 不采集。
- 只采集配置允许的路径、方法和状态。

`AnalyticsIngestApplicationService.recordRequest(...)`：

1. 解析请求日期。
2. `AnalyticsIngestDomainService.shouldRecordUv(...)` 判断是否记录 UV。
3. `shouldRecordDau(...)` 判断是否记录 DAU。
4. UV 使用 IP 写 Redis HyperLogLog。
5. DAU 使用 user UUID 映射到 analytics ordinal 后写 Redis Bitmap。

查询：

- `AnalyticsApplicationService.calculateUv(...)`
- `calculateDau(...)`
- `AnalyticsDomainService.validateRange(...)` 校验查询日期范围。

失败语义：

- 采集异常只记录日志，不改变业务 HTTP 响应。
- Redis 写失败不回滚业务。

## Ops 运维

当前 ops 域是适配入口，不 owns 搜索事实。

入口：

- `POST /api/ops/search/reindex`

流程：

1. `OpsController.reindex(...)` 处理 HTTP。
2. 调 `OpsApplicationService.reindexSearch(...)`。
3. ops application 通过 `SearchReindexActionApi` 调 search owner。
4. 返回 search reindex 结果。

ops controller 不直接访问 search application 或 infrastructure。

## 关键代码

Notice：

- `notice.controller.NoticeController`
- `notice.application.NoticeApplicationService`
- `notice.application.NoticeProjectionApplicationService`
- `notice.domain.service.NoticeDomainService`
- `notice.domain.service.NoticeProjectionDomainService`
- `notice.infrastructure.event.NoticeProjectionListener`

Search：

- `search.controller.SearchController`
- `search.application.SearchApplicationService`
- `search.application.SearchPostProjectionApplicationService`
- `search.application.SearchReindexApplicationService`
- `search.application.ReindexJobApplicationService`
- `search.domain.service.PostSearchDomainService`
- `search.domain.service.SearchReindexDomainService`
- `search.infrastructure.event.PostOutboxEnqueuer`
- `search.infrastructure.event.PostOutboxHandler`
- `search.infrastructure.job.SearchReindexHandler`

Analytics：

- `analytics.controller.AnalyticsController`
- `analytics.application.AnalyticsApplicationService`
- `analytics.application.AnalyticsIngestApplicationService`
- `analytics.domain.service.AnalyticsDomainService`
- `analytics.domain.service.AnalyticsIngestDomainService`
- `analytics.infrastructure.web.AnalyticsRequestCaptureFilter`
- `analytics.infrastructure.web.AnalyticsRequestClassifier`

Ops：

- `ops.controller.OpsController`
- `ops.application.OpsApplicationService`
- `search.api.action.SearchReindexActionApi`
