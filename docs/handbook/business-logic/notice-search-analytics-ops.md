# Notice / Search / Analytics / Ops 业务逻辑

本文覆盖四类支撑业务：站内通知读模型、搜索索引读模型、统计采集与查询，以及投影运行治理。这些域大多不是主事实 owner，而是从主业务事件派生读模型或观测其追平状态。

## 数据流

本文中的四类支撑域都以“上游事实 -> 派生读模型 / 运维动作”的方式工作：

1. Notice：content / social owner event 经 `content.events` / `social.events` 到 `NoticeProjectionKafkaListener`，再由 `NoticeProjectionApplicationService` 计算收件人、topic 和内容快照。读取列表、未读数和摘要时只读 notice 自己的读模型。
2. Search：content owner event 经 `content.events` 到 `SearchPostProjectionKafkaListener`，listener 进入 `SearchPostProjectionApplicationService` 回源 content 当前状态后决定 ES upsert 还是 delete。重建索引使用 single-flight 和 alias 原子切换。
3. Analytics：请求链成功完成后由 `AnalyticsRequestCaptureFilter` 采集，classifier 决定是否记录 UV / DAU；`AnalyticsRequestCaptureApplicationService` 按开关选择 Kafka 或同步 ingest，登录成功也可通过 action API 计入 DAU。
4. Ops：`ProjectionOpsController` 只进入 `ProjectionGovernanceApplicationService`，通过 application-owned port 汇总 projection outbox backlog，不直接修改 owner 数据。

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
- `markRead(...)` 对 ids 去重并限制每批最多 100 个，只允许当前用户的 unread notice 转成 read；revoked notice 不可被重新激活。

`NoticeProjectionApplicationService`：

1. `NoticeProjectionKafkaListener` 从 `content.events` / `social.events` 接收 contract event。
2. 根据事件类型解析收件人、topic 和 content JSON。
3. `NoticeProjectionDomainService.shouldProject(...)` 判断是否应投影。
4. 先按 source event ID 去重，再写 notice。
5. 点赞通知额外按稳定 `relationKey` 持久化投影状态。social owner 在关系写入与 outbox 的同一事务内从 `social_like_relation_version` 分配单调版本，notice 只按该版本拒绝乱序事件。`relationInstanceId` 作为不透明生命周期身份随 payload 保留，不比较 UUID 版本或数值，因此历史 UUIDv1 回填也可正常撤销。新持久化版本使用高位协议区间，必然高于旧事件的 epoch-millisecond 版本。

语义：

- 点赞、评论、关注和治理事件可生成通知。
- `LIKE_REMOVED` 只在持久化状态机接受更新 relation version 后撤销 like notice；旧生命周期的延迟 removal 不会撤销新一轮点赞通知。
- 通知投影失败按共享 Kafka retry / `.dlq` 恢复，不回滚已经提交的上游主事务。

## Search 搜索

### Owner / SSOT

- content owns 帖子事实。
- search owns Elasticsearch 索引、查询语义和索引 alias。
- ES 是最终一致读模型，不是帖子事实。

### 入口

HTTP：

- `GET /api/search/posts`

后台：

- `content.events -> SearchPostProjectionKafkaListener -> SearchPostProjectionApplicationService`。
- XXL-JOB `searchReindex -> SearchReindexHandler -> SearchReindexApplicationService`，仅在 `search.storage=es` 时装配。

### 查询流程

`SearchApplicationService.searchPosts(...)`：

1. `PostSearchDomainService.normalizeSearchQuery(...)` 规范化 keyword、categoryId、tag、page、size。
2. page/size 有上限，避免深分页风险。
3. repository 查询 ES。
4. 支持关键词、分类和标签组合过滤。
5. keyword 为空时可退化为 match-all。
6. 命中结果带关键词高亮。

关键词高亮由 `KeywordHighlightSupport` 处理：

- text 或 keyword 为空时直接返回原 text。
- keyword 按空白拆 token。
- token trim 后转小写去重，并保留首次出现顺序。
- 最多取 6 个 token，每个 token 最长 32 字符。
- 使用 regex quote 后构造大小写不敏感匹配，避免用户输入被当作正则。
- 命中内容用 `<em>...</em>` 包裹。
- replacement 使用 `Matcher.quoteReplacement`，避免命中文本里的 `$` / `\` 破坏替换。

### 投影流程

1. content 主事务写 `eventbus.content`，owner handler 发布 `content.events`。
2. `SearchPostProjectionKafkaListener` 识别 post published/updated/deleted 和 `PostScoreUpdated`，并校验 source metadata；score 事件的 envelope version 必须等于 payload `scoreVersion`。
3. listener 进入 `SearchPostProjectionApplicationService`。
4. application 把 event 当作触发信号，回源 content owner 当前帖子状态。
5. `PostSearchDomainService.shouldIndex(...)` 判断是否应索引。
6. ES 以 `aggregateVersion` 单调替换全文档；相同聚合版本只按更大的 `scoreVersion` 更新 score，避免两个消费组并发留下旧排序分。
7. 应索引则 upsert ES；不应索引则 delete ES。

### 全量重建

1. `SearchReindexApplicationService` 先通过 Redis single-flight lease 保证集群内只有一个重建执行，并在长任务期间续租。
2. application 通过 content owner 的 `PostScanQueryApi` 做游标分页，不能直接读取 content mapper 或表。
3. `ElasticsearchSearchIndexRebuildAdapter` 创建隔离的版本化索引，并把当前重建目标登记到 Redis；独立心跳续租该目标，在线增量投影同时写 alias 和该目标，覆盖扫描期间的并发变化。目标登记读取失败时增量投影失败并交给 Kafka 重试，不能按“无重建”降级成单写。
4. 扫描期间持续校验执行 lease 与目标 lease；扫描、续租或写入失败时只删除确定已从 Redis 注销且未发布的索引，现有 alias 保持不变。目标登记、注销或 alias 切换的响应结果不明确时保留目标索引；Redis target TTL 失效后，孤立索引由后续历史清理回收。
5. 完整扫描后 refresh 新索引，再原子切换 `community_posts_alias`；随后只清理超过 `search.index.keep-history` 的旧版本。

分页大小由 `search.reindex.page-size` 控制，执行 lease 由 `search.reindex.lock-ttl` 控制（最小 3 秒）。重建期间必须保持 `search.projection-enabled=true`。默认 XXL 任务为手动且停止状态，适用于投影丢失、DLQ 修复后或索引映射重建，不应当作日常增量同步机制。`search.index.keep-history` 表示 active index 之外保留的历史索引数。

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

采集编排：

1. filter 只在下游 filter chain 正常完成后调用采集；请求本身抛出异常时不追加统计动作。
2. `AnalyticsRequestCaptureApplicationService.capture(...)` 在 `analytics.ingest.async-enabled=true` 且 `AnalyticsRequestCapturePort` 可用时发布到默认 topic `analytics.request`；publisher 不可用或 async 关闭时同步调用 ingest。
3. `AnalyticsRequestKafkaListener` 只在 `analytics.ingest.enabled=true` 且 async 开启时注册，默认 group `analytics-request`、concurrency `2`；收到 `null` 直接忽略，其余 payload 映射回 `RecordRequestCommand`。
4. async publish、同步 ingest 或 classifier 的运行时异常都由 filter 捕获并节流记录；它们不能改写已经完成的 HTTP status/body，也不会重新抛给客户端。

查询：

- `AnalyticsApplicationService.calculateUv(...)`
- `calculateDau(...)`
- `AnalyticsDomainService.validateRange(...)` 校验查询日期范围。

失败语义：

- 采集异常只记录日志，不改变业务 HTTP 响应。
- Redis 写失败不回滚业务。

## Ops 投影治理

### 入口与语义

- `GET /api/ops/projections/lag`
- `ProjectionOpsController` 只负责 HTTP result 转换，进入 `ProjectionGovernanceApplicationService.listProjectionLag()`。
- application 通过 `ProjectionLagPort` 读取 lag；当前 `OutboxProjectionLagAdapter` 从已注册的 outbox handler topic 中只选择名称包含 `projection` 的 topic。
- 查询只统计 `outbox_event` 的 `PENDING`、`PROCESSING`、`DEAD`，按 topic/status 返回 `count` 和最老记录的 `oldestAge`；没有匹配 topic 时返回空列表。
- 这是只读运行治理视图，不代表 Kafka consumer lag，也不修复 owner 主事实。处理方式仍按 [运行与排障](../operations.md#outbox-dead-triage) 和 [可靠性机制](../reliability.md#outbox-governance) 执行。

## 关键代码

Notice：

- `notice.controller.NoticeController`
- `notice.application.NoticeApplicationService`
- `notice.application.NoticeProjectionApplicationService`
- `notice.domain.service.NoticeDomainService`
- `notice.domain.service.NoticeProjectionDomainService`
- `notice.infrastructure.event.NoticeProjectionKafkaListener`

Search：

- `search.controller.SearchController`
- `search.application.SearchApplicationService`
- `search.application.SearchPostProjectionApplicationService`
- `search.application.SearchReindexApplicationService`
- `search.domain.service.PostSearchDomainService`
- `search.domain.service.KeywordHighlightSupport`
- `search.infrastructure.event.SearchPostProjectionKafkaListener`
- `search.infrastructure.job.SearchReindexHandler`
- `search.infrastructure.persistence.PostIndexManager`

Analytics：

- `analytics.controller.AnalyticsController`
- `analytics.application.AnalyticsApplicationService`
- `analytics.application.AnalyticsIngestApplicationService`
- `analytics.application.AnalyticsRequestCaptureApplicationService`
- `analytics.domain.service.AnalyticsDomainService`
- `analytics.domain.service.AnalyticsIngestDomainService`
- `analytics.infrastructure.event.AnalyticsRequestKafkaListener`
- `analytics.infrastructure.web.AnalyticsRequestCaptureFilter`
- `analytics.infrastructure.web.AnalyticsRequestClassifier`

Ops：

- `ops.controller.ProjectionOpsController`
- `ops.application.ProjectionGovernanceApplicationService`
- `ops.application.ProjectionLagPort`
- `ops.infrastructure.outbox.OutboxProjectionLagAdapter`
