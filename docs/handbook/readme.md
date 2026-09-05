# docs/handbook

本目录是项目长期维护的开发者手册。内容以当前代码、`deploy/` 配置和架构守卫测试为准，历史 spec / plan 只作为背景材料。

约定：本文档集合中的命令默认从仓库根目录执行。

## 怎么读

### 新人先理解项目

1. [overview.md](overview.md)：项目形态、主请求路径、主站和 IM 的拆分方式、读源码顺序。
2. [architecture.md](architecture.md)：模块边界、轻量领域分层、跨域协作规则。
3. [system-design.md](system-design.md)：同步 API、异步事件、最终一致、投影和失败语义。
4. [frontend.md](frontend.md)：Vue3 SPA 的路由鉴权、会话恢复、API 访问、IM 长连和页面状态。

### 本地启动和联调

1. [local-development.md](local-development.md)：single / cluster 启动、端口、dev-only 账号、Mock Data Studio。
2. [observability.md](observability.md)：SLO/SLI、信号契约、指标维度、trace 命名、告警优先级和观测治理。
3. [operations.md](operations.md)：observability、Kibana、压测、scheduler、outbox worker 排障。
4. [performance-testing.md](performance-testing.md)：k6 压测套件、profile、阈值、观测点和安全注意事项。
5. [security.md](security.md)：JWT、refresh cookie、CORS / OriginGuard、授权矩阵、internal token。
6. [testing.md](testing.md)：后端、前端、架构守卫、可靠性和工具测试的运行策略。

### 改业务或查实现链路

1. [business-flows.md](business-flows.md)：按业务域整理的实现链路总览。
2. [business-logic/README.md](business-logic/README.md)：按业务域拆分的详细业务逻辑文档集，每篇都展开 owner、入口、数据流、状态、失败、幂等、跨域协作和关键代码。
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
- [observability.md](observability.md) 是观测模型 SSOT。SLO/SLI、信号契约、指标维度、trace/span 命名、告警优先级和观测治理以这里为准。
- [operations.md](operations.md) 是运行排障 SSOT。新增 scheduler、观测字段或人工恢复步骤时必须同步。
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
| 新增观测字段、指标、trace/span 命名、告警规则或 SLO | [observability.md](observability.md)、[operations.md](operations.md)、必要时同步 `deploy/observability/contracts` 和 `deploy/tests` |
| 新增测试层级、关键测试套件、验证命令或工具测试约定 | [testing.md](testing.md) |
| 修改 backend 架构规则或包边界 | [architecture.md](architecture.md)、[system-design.md](system-design.md) 和 ArchUnit 测试 |

文档应描述当前代码真实行为。未来设计和迁移说明也放在本目录，并明确标注状态；落地后必须把当前行为同步到对应 handbook 页面。

前台 UI 迁移（波次 0–10）已全部落地，[frontend-ui-optimization.md](frontend-ui-optimization.md) 转为设计历史；当前前台行为以 [frontend.md](frontend.md) 为准。

新增或修改 backend 架构规则时，还必须同步：

- [architecture.md](architecture.md)
- [system-design.md](system-design.md)
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch`
