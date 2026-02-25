# 变更提案：移除 internal 接口鉴权与 token 机制（/internal/** 全量放行）

## 需求背景

当前仓库的 `/internal/**` 接口在服务端侧普遍采用 `permitAll`（默认放行），但内部客户端仍在“带 token”（例如 `X-Internal-Token` / `X-Ops-Token`）。与此同时，仓库内缺少服务端读取/校验这些 header 的实现点，导致出现“发不验”的假安全感与约定漂移风险：

- 一旦某个服务端口被误暴露、反代误配、或网关策略漂移，内部高成本/高敏感接口将直接对外可达
- 典型影响包括：数据泄露（批量拉取 title/content 等内容）、刷指标（UV/DAU）、以及反复触发 reindex/outbox replay 导致 DoS

本次变更选择 **显式承认并统一现状：服务端对 `/internal/**` 不做认证与鉴权**，并清理仓库中残留的 token 发送逻辑、配置项与 runbook，避免“看似有保护但实际无校验”的误导。

## 变更内容

1. **移除 internal token 发送：** 内部 HTTP 客户端不再注入 `X-Internal-Token`，并删除相关常量/配置项（包括 `internal-token`、`ops-internal-token` 等）。
2. **移除 ops-token 发送与运维 guard 约定：** 前端与脚本不再发送 `X-Ops-Token`，并移除/废弃 break-glass、allowlist、single-flight 等“服务侧运维保护器”相关约定说明（以代码为准）。
3. **统一文档与部署配置：** 删除/更新 `deploy/*`、`docs/*`、`.helloagents/*` 中与 internal-token / ops-token 相关的说明，使 SSOT 与代码一致。

## 影响范围

- **Modules:**
  - common（internal client headers 清理）
  - auth-service/search-service/content-service/user-service/social-service/message-service（内部客户端与配置清理）
  - gateway（安全说明对齐：仍建议保留 `/internal/**` deny 兜底）
  - frontend/scripts（运维入口调用与提示文案调整）
  - deploy/docs/.helloagents/wiki（配置与文档对齐）
- **APIs:**
  - 服务间内部接口：`/internal/**`（保持服务端侧无需 JWT/无需 token）
  - 对外运维入口：`/api/ops/**`（仍由网关侧角色收敛；本次不引入 header token）
- **Data:** 无（纯接口与配置层变更）

## 核心场景

### Requirement: `/internal/**` 不做服务端鉴权
**Module:** all services
统一约定：`/internal/**` 无需携带 `Authorization` / `X-Internal-Token` / `X-Ops-Token`。

#### Scenario: internal 调用无需 token
内部服务调用 `/internal/**`：
- 请求不携带 `X-Internal-Token`
- 仍可得到与原来一致的业务响应（权限不再成为变量）

### Requirement: 仓库内不再出现“发 token 但不验”的实现
**Module:** common + 各 service client + frontend/scripts
清理 token header 注入与相关配置项，降低误解与维护成本。

#### Scenario: 代码与配置不再依赖 internal-token/ops-token
- internal client 不再读取 `*.internal-token*`
- 前端/脚本不再提示或要求输入 `X-Ops-Token`
- deploy/nacos-config 与 compose 环境变量不再包含 `*_INTERNAL_TOKEN` / `OPS_*_TOKEN` 作为前置条件

## 风险评估

- **风险：** 服务端侧不校验 internal token 后，`/internal/**` 的安全边界完全依赖网关/网络隔离；一旦误暴露，影响面扩大且响应速度更快。
- **缓解：**
  - 保持 gateway 对 `/internal/**` 的显式拒绝（denyAll）兜底，避免误配路由对外暴露
  - 部署侧确保各服务端口不对公网开放（仅内网/集群网络可达）
  - 对高成本入口（reindex/outbox replay/backfill）在网关或基础设施层增加限流/审计（如需）
