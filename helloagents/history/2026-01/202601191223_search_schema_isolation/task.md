# Task List: Search 重建索引去跨 Schema 直读（严格 Schema 隔离）

Directory: `helloagents/plan/202601191223_search_schema_isolation/`

---

## 1. content-service（内部扫描接口）
- [√] 1.1 新增游标扫描 DAO：为 `DiscussPostMapper` 增加 `selectDiscussPostsAfterId(afterId, limit)`，并在 `discusspost-mapper.xml` 实现 `where id > afterId order by id asc limit ...`（避免 offset 分页），对照 why.md “Search Reindex 不跨 Schema/管理员触发 reindex” 场景
- [√] 1.2 新增内部接口 `GET /internal/content/posts`：校验 `X-Internal-Token`，支持 `afterId/limit`，返回 `items + nextAfterId + hasMore`（items 用于索引所需字段），对照 why.md 场景
- [√] 1.3 放行内部接口但保留最小保护：在 `ContentSecurityConfig` 放行 `/internal/content/**`（不走 JWT），由 controller 强制 token 校验
- [√] 1.4 配置落地：在 `content-service` 增加 `content.internal-token`（读取 `CONTENT_INTERNAL_TOKEN` 或 `INTERNAL_TOKEN`），并在 Compose 透传环境变量

## 2. search-service（reindex API 化）
- [√] 2.1 新增 content-service 客户端配置：`search.content-client.*`（base-url/internal-token/timeout/page-size），并提供 `ContentServiceClientProperties`
- [√] 2.2 实现 `ContentServiceClient.scanPosts(afterId, limit)`：携带 `X-Internal-Token` 调用 content-service 内部接口，记录指标 `search_content_client_requests_total/latency`
- [√] 2.3 重构 `PostSearchService`：将 `clearAndReindexFromDb()` 改为 `clearAndReindexFromContentService()`，分页拉取并 upsert，增加“afterId 不推进则 break”的死循环防御
- [√] 2.4 更新 reindex 入口：`/api/search/internal/reindex` 与 `/internal/search/reindex` 均改为调用新方法（不再依赖跨 schema JDBC）

## 3. Infra（严格 schema 隔离）
- [√] 3.1 MySQL 初始化脚本移除跨 schema 授权：删除 search 用户对 `community_content.*` 的 `GRANT SELECT`（重建后默认即严格隔离）
- [√] 3.2 Compose 配置补齐：`content-service/search-service` 透传 `CONTENT_INTERNAL_TOKEN`（以及可选 `INTERNAL_TOKEN`），保证 reindex 可调用
- [√] 3.3 运行中实例权限收敛（演练窗口执行）：对 search 用户执行 `REVOKE SELECT ON community_content.* FROM community_search@'%'`，并用 `SHOW GRANTS` 验证仅剩 `community_search.*`

## 4. 文档与知识库同步
- [√] 4.1 更新 runbook：在 `deploy/backups/README.md` 补充 reindex 前置条件（需要配置 `CONTENT_INTERNAL_TOKEN`）
- [√] 4.2 更新系统设计与模块文档：说明 reindex 已通过 content-service 内部 API 拉取数据，不再跨 schema 直读
- [√] 4.3 更新 `helloagents/CHANGELOG.md` 与 `helloagents/wiki/modules/{content,search}.md`，记录本次边界收敛变更

## 5. Testing / Verification
- [√] 5.1 `mvn -pl search-service -am test`：确保 SearchControllerTest 通过（mock content client，highlight 断言不回退）
- [√] 5.2 `mvn -pl content-service -am test`：确保内容域改动不影响现有链路
- [√] 5.3 Compose 烟测：执行 `POST /internal/search/reindex`（携带 `SEARCH_INTERNAL_TOKEN`），确认返回 indexedCount>0；并验证 MySQL `SHOW GRANTS` 已无跨 schema select

---

## Notes
> Note: 本方案仅解决“严格 schema 隔离下的 reindex 冷启动”，不替代 P1 的 Outbox / 事件回放能力建设。
