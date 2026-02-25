# ops-service

## Purpose
隔离高风险/高成本运维能力（reindex / outbox replay / backfill 等），降低主转发面（gateway）的爆炸半径与发布风险。

## Module Overview
- **Responsibility：** 对外暴露 `/api/ops/**` 运维入口；内部通过 Dubbo RPC 调用各服务的 ops RPC（search/content/social/user）
- **Status：** ✅Stable
- **Last Updated：** 2026-02-20

## Key Files
- 启动类：`ops-service/src/main/java/com/nowcoder/community/ops/OpsServiceApplication.java`
- API：`ops-service/src/main/java/com/nowcoder/community/ops/api/OpsController.java`
- 安全：`ops-service/src/main/java/com/nowcoder/community/ops/config/OpsSecurityConfig.java`（仅 ADMIN）
- 配置：`ops-service/src/main/resources/application.yml`
- 契约测试：`ops-service/src/test/java/com/nowcoder/community/ops/api/OpsSecurityContractTest.java`

## API Interfaces（现状）
- `POST /api/ops/search/reindex`：重建索引（search-service，高成本）
- `GET /api/ops/content/outbox/health`：outbox 健康检查（content-service）
- `POST /api/ops/content/outbox/replay?limit=200`：重放失败 outbox（content-service）
- `GET /api/ops/social/outbox/health`：outbox 健康检查（social-service）
- `POST /api/ops/social/outbox/replay?limit=200`：重放失败 outbox（social-service）
- `GET /api/ops/user/outbox/health`：outbox 健康检查（user-service）
- `POST /api/ops/user/outbox/replay?limit=200`：重放失败 outbox（user-service）
- `POST /api/ops/content/likes/backfill?entityType=&maxItems=&batchSize=`：回填点赞投影（content-service，高成本；默认关闭，需显式开启 `content.like.backfill.endpoint-enabled=true`）

legacy：
- `POST /api/search/internal/reindex`：历史遗留命名；在 **search-service** 固定返回 410，并提示迁移到 `/api/ops/search/reindex`（避免误用与攻击面回潮）。

## Security Notes
- `ops-service` 自身对 `/api/ops/**` 强制 `ROLE_ADMIN`（JWT resource server）。
- gateway 侧也对 `/api/ops/**` 做 ADMIN 收敛（双保险）；并可通过 `gateway.rate-limit.blocked-path-patterns` 一键关闭高风险入口。

## Dependencies
- gateway：对外统一入口，路由 `/api/ops/** -> lb://ops-service`
- Dubbo + Nacos registry：ops-service 作为 Dubbo consumer 调用各下游服务的 ops RPC

## Change History
- 2026-02-20：引入 `ops-service`，将 gateway 内部运维 handlers 迁移到独立发布单元，降低全站爆炸半径。

