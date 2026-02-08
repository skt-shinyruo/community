# internal OPS 运维入口 Runbook（现行：无 internal token 鉴权）

> ⚠️ SSOT=代码：当前实现中，服务端对 `/internal/**` **不校验** `X-Internal-Token` / `X-Ops-Token`，调用方也不再发送这些 header。
> 因此 internal 运维入口的安全边界主要依赖 **网关拒绝 `/internal/**` + 部署网络隔离（端口不对外暴露）**。
> 本 runbook 以“在缺少服务侧 token 保护的前提下如何降低误操作/误暴露风险”为主。

## 0. 目标

- 列出 internal 运维入口清单（reindex / outbox replay / backfill）
- 给出推荐触发方式（优先对外 ops 入口，其次仅内网直连）
- 给出最低限度的安全注意事项（网络隔离、审计、限流、避免并发重复触发）

## 1. 适用范围（internal 端点清单）

- `POST /internal/search/reindex`（search-service：重建索引，高成本）
- `POST /internal/content/outbox/replay`（content-service：重放失败 outbox）
- `POST /internal/social/outbox/replay`（social-service：重放失败 outbox）
- `POST /internal/users/outbox/replay`（user-service：重放失败 outbox）
- `POST /internal/*/likes/backfill`（content-service：点赞投影冷启动/纠偏回填，高成本）

## 2. 推荐触发方式

### 2.1 通过 gateway（对外运维入口，推荐）

- `POST /api/ops/search/reindex`（仅管理员，网关侧鉴权收敛）
- legacy：`POST /api/search/internal/reindex`（历史兼容命名；是否可用以网关策略为准）

> 说明：由于 gateway 显式拒绝 `/internal/**`，对外运维应走 `/api/ops/**`。

### 2.2 仅内网直连服务（不经过网关）

当你在 **内网/容器网络/堡垒机** 中执行运维动作时，可以直连目标服务的 internal 端点：

- search-service：`POST http://search-service:8083/internal/search/reindex`
- content-service：`POST http://content-service:8088/internal/content/outbox/replay?limit=200`
- social-service：`POST http://social-service:8086/internal/social/outbox/replay?limit=200`
- user-service：`POST http://user-service:8087/internal/users/outbox/replay?limit=200`

脚本示例：
- `scripts/search-reindex.sh`（直连 search-service 的 `/internal/search/reindex`）

## 3. 必须的安全注意事项（重要）

1) **确认端口不可公网访问**
- `/internal/**` 必须只在集群/内网可达；避免将服务端口直接暴露到公网或不受控网段

2) **优先使用受控来源发起**
- 建议仅在堡垒机/内网管理网段发起，减少误触与溯源成本

3) **为高成本入口补齐限流与审计（建议）**
- 由于服务侧已无 ops guard，请在网关/基础设施层补齐：
  - 频率限制（防误触与脚本重试风暴）
  - 审计日志（谁在什么时候触发了什么运维动作）

4) **避免并发重复触发**
- reindex/outbox replay/backfill 建议单次执行并观察日志/指标，避免重复触发造成下游雪崩

## 4. 历史说明（已废弃）

旧版 runbook 中的 `break-glass + allowlist + X-Internal-Token + X-Ops-Token` 保护机制已从当前代码移除，因此本文不再包含相关配置 key 与排障步骤。
