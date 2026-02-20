# OPS 运维入口 Runbook（现行：统一 /api/ops/** + Dubbo）

> ⚠️ SSOT=代码：当前版本已移除各服务的 HTTP `/internal/**` 运维入口；运维动作统一通过 gateway 的 `/api/ops/**` 触发，并由 gateway 通过 Dubbo RPC 调用对应服务。
> legacy 对外路径 `POST /api/search/internal/reindex` 不再保留功能语义，固定返回 410 并提示迁移。

## 0. 目标

- 列出对外 ops 运维入口清单（reindex / outbox replay / backfill）
- 给出推荐触发方式（仅 gateway）
- 给出最低限度的安全注意事项（鉴权、审计、限流、避免并发重复触发）

## 1. 适用范围（对外 ops 端点清单）

- `POST /api/ops/search/reindex`（search-service：重建索引，高成本）
- `GET /api/ops/content/outbox/health`（content-service：outbox 健康检查）
- `POST /api/ops/content/outbox/replay?limit=200`（content-service：重放失败 outbox）
- `GET /api/ops/social/outbox/health`（social-service：outbox 健康检查）
- `POST /api/ops/social/outbox/replay?limit=200`（social-service：重放失败 outbox）
- `GET /api/ops/user/outbox/health`（user-service：outbox 健康检查）
- `POST /api/ops/user/outbox/replay?limit=200`（user-service：重放失败 outbox）
- `POST /api/ops/content/likes/backfill?entityType=&maxItems=&batchSize=`（content-service：点赞投影冷启动/纠偏回填，高成本；默认关闭，需显式开启 `content.like.backfill.endpoint-enabled=true`）

## 2. 推荐触发方式

### 2.1 通过 gateway（对外运维入口，唯一推荐）

- 所有 `/api/ops/**` 入口仅管理员可触发（网关侧鉴权收敛）。
- gateway 内部通过 Dubbo RPC 调用下游服务，不再允许通过 HTTP 调用 `/internal/**`。

脚本示例：
- `scripts/search-reindex.sh`（调用 gateway 的 `POST /api/ops/search/reindex`；需要设置 `OPS_ACCESS_TOKEN`）

### 2.2 legacy 入口（固定返回 410）

- `POST /api/search/internal/reindex`：历史遗留命名；固定返回 410，并在响应头给出 successor link（迁移到 `/api/ops/search/reindex`）。

## 3. 必须的安全注意事项（重要）

1) **最小权限**
- `/api/ops/**` 属于高权限运维能力，只授予必要的管理员账号，并做好权限回收。

2) **优先使用受控来源发起**
- 建议仅在堡垒机/内网管理网段发起，减少误触与溯源成本；避免在个人环境长期保存管理员 token。

3) **为高成本入口补齐限流与审计（建议）**
- 由于运维入口具有高成本副作用，请在网关/基础设施层补齐：
	  - 频率限制（防误触与脚本重试风暴）
	  - 审计日志（谁在什么时候触发了什么运维动作）

4) **避免并发重复触发**
- reindex/outbox replay/backfill 建议单次执行并观察日志/指标，避免重复触发造成下游雪崩或队列堆积

## 4. 历史说明（已废弃）

旧版 runbook 中的 `break-glass + allowlist + X-Internal-Token + X-Ops-Token` 保护机制已从当前代码移除，因此本文不再包含相关配置 key 与排障步骤。
