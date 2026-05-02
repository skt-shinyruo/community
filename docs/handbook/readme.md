# docs/handbook

本目录是项目长期维护的开发者手册。内容以当前代码、`deploy/` 配置和架构守卫测试为准，历史 spec / plan 只作为背景材料。

约定：本文档集合中的命令默认从仓库根目录执行。

## 怎么读

### 新人先理解项目

1. [overview.md](overview.md)：项目形态、主请求路径、主站和 IM 的拆分方式、读源码顺序。
2. [architecture.md](architecture.md)：模块边界、DDD Tactical Layering、跨域协作规则。
3. [system-design.md](system-design.md)：同步 API、异步事件、最终一致、投影和失败语义。

### 本地启动和联调

1. [local-development.md](local-development.md)：single / cluster 启动、端口、dev-only 账号、Mock Data Studio。
2. [operations.md](operations.md)：observability、Kibana、压测、reindex、scheduler、outbox worker 排障。
3. [security.md](security.md)：JWT、refresh cookie、CORS / OriginGuard、授权矩阵、internal token。

### 改业务或查实现链路

1. [business-flows.md](business-flows.md)：按业务域整理的实现链路，替代旧的 `business-logic/*.md` 碎片文档。
2. [integration-contracts.md](integration-contracts.md)：跨域同步 API、异步事件契约、IM Kafka contract、HTTP 写接口契约。
3. [reliability.md](reliability.md)：Idempotency-Key、outbox、single-flight、重试、补偿、fail-open / fail-closed。
4. [data-and-storage.md](data-and-storage.md)：MySQL 表、Redis key、Kafka topic、Elasticsearch alias/index。

## 文档职责边界

- [architecture.md](architecture.md) 是架构规则 SSOT。后端业务代码的分层、包形态、禁止模式和守卫测试以这里为准。
- [system-design.md](system-design.md) 是系统协作模型 SSOT。同步协作、异步事件、最终一致和投影策略以这里为准。
- [security.md](security.md) 是安全模型 SSOT。路径鉴权、JWT、OriginGuard、internal scope、prod fail-closed 以这里为准。
- [reliability.md](reliability.md) 是可靠性机制 SSOT。HTTP 幂等、outbox、重试、补偿和 single-flight 以这里为准。
- [business-flows.md](business-flows.md) 只解释“当前业务能力如何落地”，不重新定义架构规则。

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
| `business-logic/README.md` | [business-flows.md](business-flows.md) |
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
