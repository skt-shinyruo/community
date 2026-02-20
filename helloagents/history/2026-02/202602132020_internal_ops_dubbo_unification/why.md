# Change Proposal: internal_ops_dubbo_unification

## Requirement Background

当前项目存在 internal HTTP（`/internal/**`）与 Dubbo RPC 并存的长期治理风险：

1. **主路径不统一**：同一类内部能力可能同时以 internal HTTP Controller 与 Dubbo RPC 暴露，调用侧容易出现“新链路/旧链路/兼容链路”并存，形成长期半迁移。
2. **运维入口多条路径**：对外运维入口（`/api/ops/**`）、历史入口（例如 `POST /api/search/internal/reindex`）、以及服务自身 internal 入口（例如 `POST /internal/search/reindex`）并存，鉴权/审计/限流/契约测试需要覆盖多条路径，成本高且容易漏。
3. **治理链路分裂**：HTTP 侧依赖网关与 Spring Security/Filter；RPC 侧依赖 Dubbo Filter/约定；当两套入口同时存在时，很难做到“一处收敛、默认安全”。

本变更的目标是：**彻底统一内部调用与运维入口的“主路径”**，让治理（鉴权/审计/限流/观测/契约）只需要覆盖一条路径。

## Change Content

1. **内部调用一律使用 Dubbo RPC**
   - 服务间同步调用与后台任务（scan/resolve 等）统一走 `*-api` 契约模块 + Dubbo。
   - 不再允许通过 HTTP 调用下游 `/internal/**` 完成功能型内部调用。

2. **运维/回填入口统一收敛到 gateway 的 `/api/ops/**`**
   - `/api/ops/**` 由 gateway 直接处理（Controller），并通过 Dubbo RPC 调用各服务执行运维动作（reindex / outbox replay / like backfill 等）。
   - 由 gateway 统一做：鉴权（ADMIN）、审计、限流、强关闭（blocked-path-patterns）。

3. **移除 legacy 外部入口**
   - `POST /api/search/internal/reindex` 不再保留路由与兼容实现；
   - 对该路径返回明确的迁移提示（HTTP 410），引导使用 `POST /api/ops/search/reindex`。

4. **移除（或彻底禁用）`/internal/**` HTTP Controller**
   - 删除各服务中功能重复的 internal HTTP Controller（scan/resolve/block/like-scan/outbox/replay/reindex 等）。
   - 服务安全配置移除 `/internal/**` 的默认放行规则，避免未来误用。

5. **同步更新脚本与知识库**
   - 更新 `scripts/*` 与 `helloagents/wiki/*`，以 `/api/ops/**` + Dubbo 为唯一推荐路径与 SSOT。

## Impact Scope

- **Modules:**
  - `gateway`（新增 `/api/ops/**` Controller；移除 route-to-internal；新增 Dubbo consumer）
  - `search-api`（新增：搜索运维 RPC 契约模块）
  - `search-service`（新增 SearchOps RPC provider；移除 `/internal/search/**`）
  - `content-api` / `content-service`（新增 ops/outbox/like-backfill RPC；移除 `/internal/content/**`）
  - `social-api` / `social-service`（新增 outbox RPC；移除 `/internal/social/**`）
  - `user-api` / `user-service`（新增 outbox RPC；移除 `/internal/users/**`）
  - `scripts/*`（运维脚本更新为调用 gateway ops）
  - `helloagents/wiki/*`、`helloagents/CHANGELOG.md`（文档与变更记录同步）

- **APIs:**
  - ✅ Added（对外、唯一入口）：
    - `POST /api/ops/search/reindex`
    - `GET /api/ops/{service}/outbox/health`
    - `POST /api/ops/{service}/outbox/replay`
    - `POST /api/ops/content/likes/backfill`
  - ❌ Removed（不再对外/不再推荐）：
    - `POST /api/search/internal/reindex`（legacy）
    - `POST /internal/search/reindex`
    - `POST /internal/*/outbox/replay`
    - `POST /internal/content/likes/backfill`
    - `GET /internal/content/posts` / `GET /internal/content/entities/resolve`
    - `GET /internal/social/blocks/relation` / `GET /internal/social/likes/scan`
    - `POST /internal/users/authenticate` / `POST /internal/users/register` / `...`

## Core Scenarios

### Requirement: 内部主路径统一（HTTP internal → Dubbo RPC）
**Module:** gateway + *-api + *-service
将“内部能力”收敛为 Dubbo 契约，避免长期双主路径。

#### Scenario: 管理员触发 reindex（gateway ops → Dubbo → search-service）
- 当管理员调用 `POST /api/ops/search/reindex`：
  - gateway 校验 ADMIN 并记录审计日志
  - gateway 通过 Dubbo 调用 search-service 执行 reindex
  - 返回 `jobId + indexedCount`，并保持 Result/traceId 协议一致

#### Scenario: 管理员触发 outbox replay（gateway ops → Dubbo → 各服务）
- 当管理员调用 `POST /api/ops/{service}/outbox/replay?limit=200`：
  - gateway 统一限流与审计
  - 下游服务执行 replay 并返回重放条数

#### Scenario: 管理员触发点赞投影回填（gateway ops → Dubbo → content-service → Dubbo → social-service）
- 当管理员调用 `POST /api/ops/content/likes/backfill?...`：
  - gateway 触发 content-service 回填任务
  - content-service 通过 `SocialLikeScanRpcService` 扫描并回填 Redis
  - 返回扫描/写入统计信息

### Requirement: legacy 入口下线（不保留兼容实现）
**Module:** gateway

#### Scenario: legacy 入口返回迁移提示（410）
- 调用 `POST /api/search/internal/reindex`：
  - 返回 HTTP 410 + 明确迁移提示到 `/api/ops/search/reindex`
  - 不再存在任何实际执行逻辑与路由转发

### Requirement: 删除 internal HTTP 端点以减少治理面
**Module:** search/content/social/user services

#### Scenario: 不再存在可调用的 `/internal/**` 功能入口
- `/internal/**` 不再承载功能与运维动作
- 防止未来新增 internal Controller 形成灰色入口

## Risk Assessment

- **Risk:** 直接调用服务 `/internal/**` 的脚本/运维习惯会失效。
  - **Mitigation:** 提供等价的 `/api/ops/**` 入口；同步更新脚本与 runbook；对 legacy 返回 410 迁移提示。
- **Risk:** gateway 为 WebFlux，直接执行阻塞式 Dubbo 调用会阻塞事件循环。
  - **Mitigation:** ops Controller 内部将 Dubbo 调用 offload 到 `boundedElastic`，并设置合理超时与返回协议。
- **Risk:** 运维动作耗时较长（reindex/backfill），可能触发调用侧超时或误以为失败。
  - **Mitigation:** 为 ops RPC 设置更长 timeout；保留 jobId/统计返回；通过网关审计日志与指标可追踪执行情况。

