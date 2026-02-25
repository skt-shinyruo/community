# Change Proposal: Search 重建索引去跨 Schema 直读（严格 Schema 隔离）

## Requirement Background
现状（P0 多 schema 之后）：

- `search-service` 的 `/internal/search/reindex` 为了冷启动/修复，会直接用 JDBC 查询 `community_content.discuss_post/comment`。
- 为了让它能直读内容域表，MySQL 初始化脚本给 `community_search` 用户额外授予了 `community_content.*` 的只读权限。

问题：

1. **破坏微服务数据边界**：search-service 直接依赖 content-service 的表结构与库名，属于跨域直连，未来 content 域改表会“悄悄炸”，且无法通过接口契约提前发现。
2. **权限最小化被打破**：为了 reindex 临时加的跨 schema `GRANT SELECT` 会长期存在，扩大误用/误写风险（即使是只读，也会让“数据归属”变得模糊）。
3. **生产可运营性下降**：当我们希望“每服务仅访问本 schema”时，search-service 的跨域直读会成为必须长期保留的例外点，阻碍后续治理（审计/合规/权限收敛）。

目标（严格验收）：

- search-service **仅访问** `community_search` schema（幂等表 `search_consumed_event` 等）。
- reindex 不再依赖跨 schema `SELECT`，MySQL 初始化脚本中不再为 search 账号授予 `community_content.*` 权限。
- reindex 仍可用：能在多 schema 环境下完成索引冷启动/修复。

## Change Content
1. content-service 增加内部扫描接口：按主键游标分页扫描帖子数据，供 search-service 重建索引使用。
2. search-service 的 reindex 改为调用 content-service 内部 API 拉取帖子列表，再写入 ES。
3. MySQL 初始化脚本移除 search 用户对内容域表的跨 schema 授权；并在演练/运行环境撤销历史授予。

## Impact Scope
- **Modules:** content-service, search-service, deploy, docs, .helloagents/wiki
- **Files:** Java API/DAO/配置、docker-compose、mysql-init、runbook、wiki
- **APIs:**
  - Added: `GET /internal/content/posts`（content-service）
  - Changed: `POST /internal/search/reindex` / `POST /api/search/internal/reindex` 的数据来源从“直读 DB”变更为“调用 content-service”
- **Data:**
  - No schema change（仅查询方式与权限策略变化）

## Core Scenarios

### Requirement: Search Reindex 不跨 Schema
**Module:** search + content
search-service 的 reindex 需要在严格 schema 隔离下仍可完成全量重建。

#### Scenario: 管理员/内部触发 reindex
前置条件：
- `CONTENT_INTERNAL_TOKEN` / `SEARCH_INTERNAL_TOKEN` 已配置
- content-service 可访问 `community_content`（自身 schema）
- search-service 可访问 `community_search`（自身 schema）

期望结果：
- search-service 调用 content-service 内部扫描接口分页拉取帖子数据
- search-service 完成 ES 索引清空 + 重新写入
- MySQL 中 search 用户不再拥有对 `community_content.*` 的 `SELECT` 权限

## Risk Assessment
- **Risk:** 内部接口被误暴露/被滥用导致数据泄露或资源耗尽
  - **Mitigation:** 仅放行 `/internal/content/**` 且强制 `X-Internal-Token`；分页限制（limit 最大 1000）；仅返回索引所需字段
- **Risk:** reindex 过程中 content-service 不可用导致重建失败
  - **Mitigation:** reindex 为后台运维操作，失败可重试；客户端设置超时与可观测指标（请求量/延迟/失败）
