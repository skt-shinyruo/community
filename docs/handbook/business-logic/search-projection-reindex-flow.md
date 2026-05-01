# 搜索投影与重建索引链路实现说明

本文档说明当前仓库中搜索能力的实际实现路径，聚焦以下问题：

- 帖子搜索接口怎么读
- 帖子事件如何追平到搜索索引
- 默认为什么走 outbox
- reindex 如何做单飞控制和零停机切换
- 运维入口和 XXL 任务如何触发 reindex

相关文档：

- `docs/handbook/business-logic/content-post-comment-bookmark-subscription-flow.md`
- `docs/handbook/business-logic/notice-projection-read-flow.md`

---

## 1. 参与组件

- `SearchController`：搜索读接口
- `SearchApplicationService`：搜索查询、帖子投影 upsert/delete 的同域应用入口
- `SearchReindexApplicationService`：reindex 主编排入口
- `ReindexJobApplicationService`：reindex 单飞锁与 jobId 管理
- `PostSearchDomainService` / `SearchReindexDomainService`：搜索参数、索引条件与 reindex 参数规则
- `PostSearchRepository`：搜索 domain repository 接口
- `ElasticsearchPostSearchRepository` / `InMemoryPostSearchRepository`：搜索 repository 的 infrastructure 实现
- `PostSearchIndexRepository` / `PostIndexManager`：索引 alias 切换能力与 ES 实现
- `PostOutboxEnqueuer` / `PostOutboxHandler`：`search.infrastructure.event` 下的默认 outbox 投影适配器
- `OpsController`：对外手工触发入口
- `SearchReindexHandler`：XXL Job 入口
- `SearchReindexActionApiAdapter`：foreign ops/job 进入 search 的 action API 适配器
- `PostScanQueryApi`：从 content 域扫描帖子权威快照

---

## 2. 对外接口

- `GET /api/search/posts`
- `POST /api/ops/search/reindex`
- XXL Job：`searchReindex`

其中：

- `/api/search/posts` 是普通读接口
- `/api/ops/search/reindex` 是高风险运维入口

---

## 3. 搜索读路径

`GET /api/search/posts` -> `SearchController.searchPosts(...)`

主链路：

1. controller 把 HTTP 参数转换成 `SearchPostsCommand`
2. `SearchApplicationService.searchPosts(...)`
3. `PostSearchDomainService.normalizeSearchQuery(...)`
   - 标准化 `keyword`
   - 处理可选 `categoryId`
   - 标准化 `tag`，去掉前导 `#`
   - 规范化分页
4. 调 `PostSearchRepository.search(...)`
5. 把 domain hit 转成 application result，再由 controller 转成 response DTO

这里搜索层本身不再回查 `content` 主表；它读的是自己的搜索投影。

---

## 4. 默认投影路径：outbox

### 4.1 入口

当前默认配置：

- `events.outbox.enabled=true`

所以帖子事件不会直接同步写搜索索引，而是：

1. `ContentContractEvent`
2. `PostOutboxEnqueuer`
3. outbox
4. `PostOutboxHandler`
5. `SearchApplicationService`
6. `PostSearchRepository`

### 4.2 入箱规则

`PostOutboxEnqueuer` 只处理：

- `POST_PUBLISHED`
- `POST_UPDATED`
- `POST_DELETED`

并把：

- `postId`
- `sourceEventId`
- `sourceEventType`

序列化成 outbox payload。

### 4.3 出箱规则

`PostOutboxHandler` 的关键策略是：

- 不直接信任事件 payload 里的全部字段
- 总是再通过 `PostScanQueryApi.getPostProjectionAllowDeleted(postId)` 回源当前数据库权威状态

然后：

- 如果帖子不存在：通过 `SearchApplicationService.deletePost(...)` 删搜索文档
- 否则：通过 `SearchApplicationService.syncPostProjection(...)` 按当前权威快照收敛搜索文档

`PostSearchDomainService.shouldIndex(...)` 决定当前快照是否应该留在索引中；已删除状态会被收敛为 delete。

这能避免乱序事件把已删除帖子重新“复活”进索引。

---

## 5. 关闭 outbox 时的语义

如果：

- `events.outbox.enabled=false`

当前不会再启用旧的 `PostProjectionListener` 降级路径。搜索投影的 after-commit listener 已退役，测试会断言它不在 classpath。

因此关闭 outbox 的含义是：

- 新的帖子变化不会自动追平到搜索索引
- 需要恢复 outbox 或手工执行 reindex 来修复缺口

---

## 6. reindex 主链路

### 6.1 执行器

`SearchReindexApplicationService.reindex(...)` 是重建索引的统一入口。

主步骤：

1. `ReindexJobApplicationService.tryStart()` 获取单飞锁与 `jobId`
2. 获取不到锁：
   - 返回 `skipped=true`
   - reason 为“already running”
3. 获取到锁：
   - 记录开始日志
   - 启动续租线程
   - 分页扫描 content 权威快照并重建搜索索引
   - 完成后释放锁

### 6.2 单飞控制

`ReindexJobApplicationService` 背后依赖：

- `SingleFlightTaskGuard`

它提供：

- tryAcquire
- refresh
- release

所以同一时刻只允许一个 reindex 在跑。

### 6.3 真正重建索引

`SearchReindexApplicationService.reindex(...)` 的主步骤：

1. 如果有 `PostSearchIndexRepository` 实现（当前 ES 下为 `PostIndexManager`）
   - `ensureAliasReady()`
   - 新建目标索引
2. 如果没有 index repository
   - 直接清空当前索引
3. 通过 `PostScanQueryApi.scanPosts(afterId, pageSize)` 分页扫描帖子主数据
4. 每页 upsert 到目标索引
5. 全部完成后：
   - alias 切换到新索引
   - 清理旧索引

所以当前 reindex 的实现不是“边删边写同一索引”，而是更接近：

- 新索引构建
- alias 切换
- 旧索引回收

---

## 7. 运维入口

### 7.1 HTTP 手工触发

`POST /api/ops/search/reindex` -> `OpsController.reindex()`

它会：

- 调 `SearchReindexActionApi.reindex()`
- 同进程实现为 `SearchReindexActionApiAdapter -> SearchReindexApplicationService`
- 如果 `skipped=true`，转成业务错误
- 否则返回：
  - `jobId`
  - `indexedCount`

### 7.2 XXL Job

`SearchReindexHandler` 暴露：

- `@XxlJob("searchReindex")`

它调用同一个 `SearchReindexActionApi.reindex()`，只是把结果写进 XXL 日志与任务状态。

所以：

- 手工触发和定时触发走的是同一个 search action API，再进入同一个 `SearchReindexApplicationService`

---

## 8. 一致性与失败语义

### 8.1 搜索是异步读模型

帖子写成功不等于搜索结果立即可见。

默认语义是：

- 主事实先提交
- 搜索索引稍后通过 outbox 追平

### 8.2 reindex 是单飞任务

如果已有一个任务在跑：

- 新 reindex 不会并发执行
- 会返回 `skipped`

### 8.3 删除语义按当前 DB 状态收敛

出箱 handler 不是简单“收到 delete 事件就删、收到 update 事件就写”，而是回源 DB 当前状态。

这使得搜索最终更接近当前主事实，而不是事件到达顺序。

---

## 9. 一句话总结

当前搜索实现的核心思路是：

- 读侧直接查搜索投影
- 写侧默认通过 outbox 从 content 事件异步追平
- 全量重建通过单飞锁、分页扫描和 alias 切换完成
- HTTP 与 XXL 只是两个触发入口，真正执行器只有一套
