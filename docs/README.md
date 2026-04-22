# docs/

本目录是“面向开发者”的项目文档集合，用于解释本仓库的架构、运行方式、关键设计与运维观测。内容以**代码与 deploy 配置为准**，并优先覆盖本地开发/联调场景。

> 约定：本文档集合中的命令默认从**仓库根目录**执行。

## 推荐阅读顺序
1. `docs/ARCHITECTURE.md`：总体架构、模块边界、端口、关键链路（读/写/事件）、可观测性入口
2. `docs/CORE_LOGIC.md`：面向初学者的项目主线导读，串起统一入口、主站写路径、事件扩散和 IM 双服务模型
3. `docs/DEPLOYMENT.md`：本地启动（layered docker compose / overlays）、端口暴露策略、gateway-first 的工作方式
4. `docs/OBSERVABILITY.md`：本地 observability 说明，覆盖 `observability` compose 路径下的 logs / traces / metrics 接入、Kibana 资产导入，以及基于 `trace.id`、`service.name`、`community.category` / `community.action` / `community.outcome` 的 fielded Kibana 排障 runbook
5. `docs/SYSTEM_DESIGN.md`：系统设计（同步 API + 异步事件、最终一致、幂等、DLQ）
6. `docs/SECURITY.md`：鉴权模型（JWT + refresh cookie）、CORS、限流、审计日志、内部 token
7. `docs/DATA_MODEL.md`：MySQL/Redis/Kafka/ES 的最小数据模型与约定（以 `deploy/` 与代码常量为准）
8. `docs/LOAD_TESTING.md`：自研压测工具与推荐压测分层（面向 IM/长连）
9. `docs/business-logic/`：按业务能力拆开的实现逻辑文档，关注“具体功能在当前代码里如何落地”

## 文档范围说明
- 本仓库默认本地运行模式：**前端经由 community-gateway 进入后端**（frontend `12881` / gateway `12880`）。
- `helloagents/` 下的内容属于项目内置的知识库与历史方案包；本 `docs/` 目录侧重给普通开发者快速理解与上手。
