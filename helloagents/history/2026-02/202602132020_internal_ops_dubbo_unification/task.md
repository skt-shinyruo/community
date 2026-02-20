# Task List: internal_ops_dubbo_unification

Directory: `helloagents/plan/202602132020_internal_ops_dubbo_unification/`

---

## 1. search-api / search-service（reindex ops RPC）
- [√] 1.1 新增 `search-api` 模块并定义 `SearchOpsRpcService` + DTO，更新 `pom.xml` modules 与依赖，verify why.md#requirement-内部主路径统一http-internal--dubbo-rpc-scenario-管理员触发-reindexgateway-ops--dubbo--search-service
- [√] 1.2 在 `search-service` 实现 `@DubboService` provider（复用 `PostSearchService`/`ReindexJobService`），并移除 `InternalSearchController` 与相关测试/安全放行，verify why.md#requirement-删除-internal-http-端点以减少治理面-scenario-不再存在可调用的-internal-功能入口

## 2. content-api / content-service（outbox + like backfill ops RPC）
- [√] 2.1 在 `content-api` 新增 `ContentOutboxRpcService` 与 `ContentLikeOpsRpcService` + DTO（health/replay/backfill），verify why.md#requirement-内部主路径统一http-internal--dubbo-rpc-scenario-管理员触发-outbox-replaygateway-ops--dubbo--各服务
- [√] 2.2 在 `content-service` 实现对应 `@DubboService` provider（复用 `OutboxEventService`/`LikeProjectionBackfillJob`），并移除 `/internal/content/**` Controller 与安全放行，verify why.md#requirement-删除-internal-http-端点以减少治理面-scenario-不再存在可调用的-internal-功能入口

## 3. social-api / social-service（outbox ops RPC + 移除 legacy internal）
- [√] 3.1 在 `social-api` 新增 `SocialOutboxRpcService` + DTO（health/replay），verify why.md#requirement-内部主路径统一http-internal--dubbo-rpc-scenario-管理员触发-outbox-replaygateway-ops--dubbo--各服务
- [√] 3.2 在 `social-service` 实现 provider，移除 `/internal/social/*` legacy Controller（blocks/likes-scan/outbox），并收紧安全配置，verify why.md#requirement-删除-internal-http-端点以减少治理面-scenario-不再存在可调用的-internal-功能入口

## 4. user-api / user-service（outbox ops RPC + 移除 internal users）
- [√] 4.1 在 `user-api` 新增 `UserOutboxRpcService` + DTO（health/replay），verify why.md#requirement-内部主路径统一http-internal--dubbo-rpc-scenario-管理员触发-outbox-replaygateway-ops--dubbo--各服务
- [√] 4.2 在 `user-service` 实现 provider，移除 `InternalUserController` 与 `/internal/users/outbox` Controller，并收紧安全配置，verify why.md#requirement-删除-internal-http-端点以减少治理面-scenario-不再存在可调用的-internal-功能入口

## 5. gateway（/api/ops 收敛 + legacy 下线）
- [√] 5.1 gateway 增加 `/api/ops/**` Controller：reindex/outbox/like-backfill 统一走 Dubbo（offload 到 boundedElastic，并保持 Result/traceId 协议），verify why.md#requirement-内部主路径统一http-internal--dubbo-rpc-scenario-管理员触发-reindexgateway-ops--dubbo--search-service
- [√] 5.2 移除 `spring.cloud.gateway.routes` 中 legacy `/api/search/internal/reindex` 与 `/api/ops/search/reindex` 的 route-to-internal 配置，并默认启用 blocked-path-patterns 返回 410 迁移提示，verify why.md#requirement-legacy-入口下线不保留兼容实现-scenario-legacy-入口返回迁移提示410
- [√] 5.3 补齐网关限流规则：reindex/outbox replay/like backfill（按 USER），verify why.md#requirement-内部主路径统一http-internal--dubbo-rpc-scenario-管理员触发-outbox-replaygateway-ops--dubbo--各服务

## 6. Scripts & Knowledge Base
- [√] 6.1 更新 `scripts/search-reindex.sh` 改为调用 `POST /api/ops/search/reindex`（不再直连 `/internal/search/reindex`），verify why.md#requirement-legacy-入口下线不保留兼容实现-scenario-legacy-入口返回迁移提示410
- [√] 6.2 更新知识库：`helloagents/wiki/api.md`、`helloagents/wiki/runbooks/internal-ops.md`、`helloagents/wiki/modules/*`，移除 `/internal/**` 推荐路径并以 `/api/ops/**` + Dubbo 为 SSOT
- [√] 6.3 更新 `helloagents/CHANGELOG.md`：记录移除 internal HTTP + 新增 ops RPC/ops API

## 7. Security Check
- [√] 7.1 执行安全检查：确认不再对外暴露 `/internal/**`，legacy 入口返回 410，ops 入口仅 ADMIN 可访问；检查敏感信息不落日志（token/password）

## 8. Testing
- [√] 8.1 执行 `mvn test`（至少覆盖 gateway/search/content/user/social），并补齐必要的合约测试/单测以锁定行为
