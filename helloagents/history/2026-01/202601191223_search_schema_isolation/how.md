# Technical Design: Search 重建索引去跨 Schema 直读（严格 Schema 隔离）

## Technical Solution

### Core Technologies
- Spring Boot 3 / Spring MVC
- MyBatis（content-service 侧扫描帖子）
- RestTemplate（search-service 侧内部拉取）
- MySQL（同实例多 schema）

### Implementation Key Points
1. **content-service 提供内部扫描接口**
   - Path：`GET /internal/content/posts`
   - Auth：`X-Internal-Token`（不走 JWT）
   - Pagination：`afterId` + `limit`（主键游标，避免 offset 大表慢）
   - 返回：`items[] + nextAfterId + hasMore`（hasMore 仅为提示，调用方以 items 为空为终止条件）

2. **search-service reindex 改为调用 content-service**
   - 入口保持不变：
     - `POST /api/search/internal/reindex`（管理员 JWT）
     - `POST /internal/search/reindex`（X-Internal-Token）
   - 实现逻辑：
     - clear ES index
     - while 循环分页扫描帖子
     - upsert 到 ES（沿用 PostPayload → EsPostDocument 映射）
   - 防御：
     - `nextAfterId` 不推进时 break，避免死循环

3. **配置与可观测**
   - search-service 新增 `search.content-client.*`：
     - `base-url`（默认 `http://content-service:8088`，Docker Compose 直连）
     - `internal-token`（来自 `CONTENT_INTERNAL_TOKEN` 或 `INTERNAL_TOKEN`）
     - `connect-timeout/read-timeout/page-size`
   - 指标：
     - `search_content_client_requests_total{api,outcome}`
     - `search_content_client_latency{api,outcome}`

4. **严格 schema 隔离落地**
   - 移除 MySQL 初始化脚本中对 search 用户的跨 schema `GRANT SELECT`。
   - 在运行中实例执行 `REVOKE`，确保 `SHOW GRANTS` 仅包含 `community_search.*`。

## API Design

### [GET] /internal/content/posts
- **Request:**
  - Headers：`X-Internal-Token: <token>`
  - Query：
    - `afterId`（int，可选，默认 0）
    - `limit`（int，可选，默认 500，最大 1000）
- **Response:**
  - `Result<InternalPostScanResponse>`
  - `InternalPostScanResponse.items`：`PostPayload[]`（包含 title/content/createTime 等索引必要字段）
  - `nextAfterId`：本页最后一条记录的 id（用于下一页游标）
  - `hasMore`：是否可能还有更多（提示字段）

## Data Model
无新增表；仅新增 MyBatis 查询方法（按 afterId 扫描）。

## Security and Performance
- **Security:**
  - `/internal/content/**` 在 Spring Security 放行，但 controller 强制校验 `X-Internal-Token`。
  - MySQL 权限收敛：search 用户仅授权 `community_search.*`。
- **Performance:**
  - 主键游标扫描避免 offset 分页；limit 上限 1000。
  - reindex 属于后台运维操作，建议在低峰窗口执行。

## Testing and Deployment
- **Testing:**
  - search-service：Mock content client，确保 reindex 仍可触发并写入内存索引（高亮断言保持）。
  - content-service：现有集成测试保持通过（内部接口只增加放行规则与 mapper 方法）。
- **Deployment:**
  - Docker Compose：补齐 `CONTENT_INTERNAL_TOKEN` 环境变量，并重启 content/search 服务生效。
  - MySQL：对已运行环境执行 `REVOKE`，确保权限收敛（可回滚：重新 `GRANT SELECT`，但不建议）。
