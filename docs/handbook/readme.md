# docs/handbook

本目录是项目长期维护的开发者手册。内容以当前代码、`deploy/` 配置和架构守卫测试为准，历史 spec / plan 只作为背景材料。

约定：本文档集合中的命令默认从仓库根目录执行。

## 怎么读

### 新人先理解项目

1. [overview.md](overview.md)：项目形态、主请求路径、主站和 IM 的拆分方式、读源码顺序。
2. [architecture.md](architecture.md)：模块边界、DDD Tactical Layering、跨域协作规则。
3. [system-design.md](system-design.md)：同步 API、异步事件、最终一致、投影和失败语义。
4. [frontend.md](frontend.md)：Vue3 SPA 的路由鉴权、会话恢复、API 访问、IM 长连和页面状态。

### 本地启动和联调

1. [local-development.md](local-development.md)：single / cluster 启动、端口、dev-only 账号、Mock Data Studio。
2. [operations.md](operations.md)：observability、Kibana、压测、reindex、scheduler、outbox worker 排障。
3. [security.md](security.md)：JWT、refresh cookie、CORS / OriginGuard、授权矩阵、internal token。
4. [testing.md](testing.md)：后端、前端、架构守卫、可靠性和工具测试的运行策略。

### 改业务或查实现链路

1. [business-flows.md](business-flows.md)：按业务域整理的实现链路总览。
2. [business-logic/README.md](business-logic/README.md)：按业务域拆分的详细业务逻辑文档集，覆盖 owner、入口、状态、失败、幂等、跨域协作和关键代码。
3. [auth-login-session-flow.md](auth-login-session-flow.md)：登录、refresh token 续期、logout 和 JWT 鉴权代码链路。
4. [core-logic-index.md](core-logic-index.md)：核心类到 handbook 章节的覆盖矩阵。
5. [integration-contracts.md](integration-contracts.md)：跨域同步 API、异步事件契约、IM Kafka contract、HTTP 写接口契约。
6. [reliability.md](reliability.md)：Idempotency-Key、outbox、single-flight、重试、补偿、fail-open / fail-closed。
7. [data-and-storage.md](data-and-storage.md)：MySQL 表、Redis key、Kafka topic、Elasticsearch alias/index。

## 文档职责边界

- [architecture.md](architecture.md) 是架构规则 SSOT。后端业务代码的分层、包形态、禁止模式和守卫测试以这里为准。
- [system-design.md](system-design.md) 是系统协作模型 SSOT。同步协作、异步事件、最终一致和投影策略以这里为准。
- [security.md](security.md) 是安全模型 SSOT。路径鉴权、JWT、OriginGuard、internal scope、prod fail-closed 以这里为准。
- [reliability.md](reliability.md) 是可靠性机制 SSOT。HTTP 幂等、outbox、重试、补偿和 single-flight 以这里为准。
- [business-flows.md](business-flows.md) 只解释“当前业务能力如何落地”，不重新定义架构规则。
- [business-logic/README.md](business-logic/README.md) 是详细业务逻辑文档集入口。新增或调整业务能力时，优先更新对应域文档，再按需要同步总览和索引。
- [auth-login-session-flow.md](auth-login-session-flow.md) 是登录 / refresh / logout 代码链路详解，不替代 [security.md](security.md) 的安全模型定义。
- [core-logic-index.md](core-logic-index.md) 是核心类文档覆盖索引。新增或调整核心 `ApplicationService`、domain service、listener、handler、enqueuer 或 job 时同步。
- [integration-contracts.md](integration-contracts.md) 是跨边界协议 SSOT。新增 owner API、HTTP 写契约、IM Kafka contract 和客户端语义以这里为准。
- [frontend.md](frontend.md) 是浏览器客户端核心逻辑 SSOT。前端路由、session、endpoint、HTTP interceptor、IM realtime client、页面状态和 stores 以这里为准。
- [data-and-storage.md](data-and-storage.md) 是存储索引 SSOT。新增表、Redis key、Kafka topic、ES alias/index 或本地种子数据时必须同步。
- [operations.md](operations.md) 是运行排障 SSOT。新增 scheduler、XXL job、观测字段或人工恢复步骤时必须同步。
- [local-development.md](local-development.md) 是本地启动和验证 SSOT。新增本地拓扑、端口、dev-only 控制面或常用命令时必须同步。
- [testing.md](testing.md) 是测试策略 SSOT。新增测试层级、关键测试套件或验证命令时必须同步。

## 维护清单

修改代码时按影响面同步 handbook：

| 代码变化 | 必改文档 |
| --- | --- |
| 新增或调整业务链路 | [business-logic/README.md](business-logic/README.md) 下对应域文档、[business-flows.md](business-flows.md)、[core-logic-index.md](core-logic-index.md) |
| 新增 HTTP 接口、请求/响应字段、客户端语义 | [integration-contracts.md](integration-contracts.md)，必要时同步 [security.md](security.md) |
| 新增跨域同步 API 或异步事件 | [architecture.md](architecture.md)、[system-design.md](system-design.md)、[integration-contracts.md](integration-contracts.md) |
| 新增或调整前端路由、会话恢复、endpoint 解析、HTTP interceptor、IM realtime client、复杂页面状态 | [frontend.md](frontend.md)，必要时同步 [integration-contracts.md](integration-contracts.md)、[security.md](security.md) |
| 新增表、索引、Redis key、Kafka topic、ES index/alias | [data-and-storage.md](data-and-storage.md) |
| 新增幂等、outbox、重试、补偿、single-flight 或 pending 状态机 | [reliability.md](reliability.md)、[operations.md](operations.md) |
| 新增安全规则、internal endpoint、Origin/CORS/JWT/cookie 约束 | [security.md](security.md)、必要时同步 [architecture.md](architecture.md) |
| 新增本地服务、端口、env、dev-only 能力 | [local-development.md](local-development.md)、[operations.md](operations.md) |
| 新增测试层级、关键测试套件、验证命令或工具测试约定 | [testing.md](testing.md) |
| 修改 backend 架构规则或包边界 | [architecture.md](architecture.md)、[system-design.md](system-design.md)、严格 DDD spec 和 ArchUnit 测试 |

文档应描述当前代码真实行为，不把历史 spec / plan 当作现状。旧设计仍有参考价值时，只能作为“历史/遗留/已退休”明确标注。当前行为不能只写在 `docs/superpowers/specs` 或 `docs/superpowers/plans`，必须沉淀到本目录相应 handbook。

新增或修改 backend 架构规则时，还必须同步：

- [architecture.md](architecture.md)
- [system-design.md](system-design.md)
- `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch`

## 旧文档迁移索引

旧文档没有直接保留为独立文件，知识点已合并到以下位置：

| 旧文档 | 新位置 |
| --- | --- |
| `CORE_LOGIC.md` | [overview.md](overview.md) |
| `DEPLOYMENT.md` | [local-development.md](local-development.md) |
| `LOCAL_CLUSTER.md` | [local-development.md](local-development.md)、[operations.md](operations.md) |
| `OBSERVABILITY.md` | [operations.md](operations.md) |
| `LOAD_TESTING.md` | [operations.md](operations.md) |
| `DEV_ONLY.md` | [local-development.md](local-development.md)、[security.md](security.md) |
| `DATA_MODEL.md` | [data-and-storage.md](data-and-storage.md) |
| `HTTP_IDEMPOTENCY.md` | [reliability.md](reliability.md)、[integration-contracts.md](integration-contracts.md) |
| `ELASTICSEARCH_ARCHITECTURE.md` | [data-and-storage.md](data-and-storage.md)、[business-flows.md](business-flows.md)、[operations.md](operations.md) |
| 历史 `business-logic/README.md` | 当前路径已重新启用为 [business-logic/README.md](business-logic/README.md)，旧拆分流文档知识点合并到 [business-flows.md](business-flows.md) 和新业务域详解文档。 |
| `business-logic/admin-user-role-management-flow.md` | [business-flows.md](business-flows.md#admin-user-role-management) |
| `business-logic/analytics-ingest-flow.md` | [business-flows.md](business-flows.md#analytics) |
| `business-logic/analytics-uv-dau-flow.md` | [business-flows.md](business-flows.md#analytics) |
| `business-logic/auth-registration-login-flow.md` | [business-flows.md](business-flows.md#auth-registration-login-and-session) |
| `business-logic/content-post-comment-bookmark-subscription-flow.md` | [business-flows.md](business-flows.md#content-post-comment-bookmark-and-subscription) |
| `business-logic/growth-checkin-task-center-flow.md` | [business-flows.md](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) |
| `business-logic/growth-task-grant-level-flow.md` | [business-flows.md](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) |
| `business-logic/im-private-message-flow.md` | [business-flows.md](business-flows.md#im-private-message) |
| `business-logic/im-room-message-flow.md` | [business-flows.md](business-flows.md#im-room-message) |
| `business-logic/market-order-dispute-flow.md` | [business-flows.md](business-flows.md#market-order-and-dispute) |
| `business-logic/notice-projection-read-flow.md` | [business-flows.md](business-flows.md#notice-projection-and-read-model) |
| `business-logic/ops-scheduler-compensation-flow.md` | [operations.md](operations.md)、[reliability.md](reliability.md)、[business-flows.md](business-flows.md#ops-scheduler-and-compensation) |
| `business-logic/report-moderation-flow.md` | [business-flows.md](business-flows.md#report-and-moderation) |
| `business-logic/search-projection-reindex-flow.md` | [business-flows.md](business-flows.md#search-projection-and-reindex)、[operations.md](operations.md) |
| `business-logic/security-authz-boundary-flow.md` | [security.md](security.md) |
| `business-logic/shared-outbox-delivery-guarantee-flow.md` | [reliability.md](reliability.md) |
| `business-logic/social-block-im-governance-flow.md` | [business-flows.md](business-flows.md#social-block-and-im-governance) |
| `business-logic/social-like-follow-outbox-flow.md` | [business-flows.md](business-flows.md#social-like-follow-and-events) |
| `business-logic/startup-fail-closed-runtime-flow.md` | [security.md](security.md)、[operations.md](operations.md) |
| `business-logic/user-moderation-state-flow.md` | [business-flows.md](business-flows.md#user-moderation-state) |
| `business-logic/user-profile-avatar-flow.md` | [business-flows.md](business-flows.md#user-profile-and-avatar) |
| `business-logic/wallet-ledger-flow.md` | [business-flows.md](business-flows.md#wallet-ledger) |
