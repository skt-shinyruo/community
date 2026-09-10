# 仓库说明

本文件适用于整个仓库。架构规则的完整表述以 [docs/handbook/architecture.md](docs/handbook/architecture.md) 为 SSOT；本文件只保留每次改动都会用到的核心规则、边界与入口。

## 仓库地图

- `backend/`：Java 17 / Spring Boot 4 Maven reactor，后端构建与测试在此目录执行。
  - `community-app`：主站业务 monolith，按业务域分包治理（见下文架构核心规则）。根级 `app` 包是启动包（Application 入口与 app 级 config/security），不承载业务规则。
  - `community-gateway` / `community-im-gateway` / `community-im` / `community-oss` / `community-oss-client`：统一入口、IM 服务、OSS 服务与 typed client。
  - `community-common`：9 个 `common-*` 模块，提供错误协议、安全、幂等、outbox、JSON、Kafka 等横切基础设施。
- `frontend/`：Vue 3 / Vite SPA，前端 npm 命令在此目录执行。
- `deploy/`：本地 Compose 拓扑（infra / single / cluster）、业务 schema、Nacos seed、观测资产与部署契约测试。
- `tools/mock-data-studio/`、`tests/k6/`、`tests/playwright-single/`：独立安装与测试的 Node 工具/套件，frontend 的依赖安装不覆盖它们。
- 行为事实来源：`docs/handbook/`、代码与 ArchUnit 守卫测试。`docs/research/` 是带日期的研究笔记；标注为计划的设计/迁移文档在落地前不代表现状。

## 改动前必读

- 改 `backend/community-app` 业务代码 → 先读 [docs/handbook/architecture.md](docs/handbook/architecture.md)（分层、包形态、禁止模式、守卫测试的完整规则）。
- 改跨域同步/异步协作 → 再读 [docs/handbook/system-design.md](docs/handbook/system-design.md)。
- 改前端 → 先读 [docs/handbook/frontend.md](docs/handbook/frontend.md)；领域术语以根目录 [CONTEXT.md](CONTEXT.md) 为准。
- 测试分层与各工具验证命令 → [docs/handbook/testing.md](docs/handbook/testing.md)。

## community-app 架构核心规则

按保留边界所需的最小流组织代码：

```text
简单查询：Controller -> ApplicationService -> query port / Repository
本地写入：Controller -> ApplicationService -> Domain model / DomainService / Repository
同步跨域：caller ApplicationService -> owner api.query / api.action -> owner ApplicationService -> owner domain
异步跨域：owner ApplicationService -> contracts.event + outbox -> listener -> consumer ApplicationService
```

包形态：`com.nowcoder.community.<domain>` 下按职责创建 `controller`、`application`、`domain`（model/service/repository/event）、`infrastructure`（persistence.mapper/dataobject、event 等）、`api`（query/action/model）、`contracts.event`，以及已批准的 `config` / `exception` / `logging` / `security` 根。层只在职责存在时创建，不要求填满；`interaction`、`profile`、`im` 等域有意从简。

以下红线由 ArchUnit 强制（`backend/community-app/src/test/java/com/nowcoder/community/app/arch/`），违反直接红灯：

- 入站适配器（controller、listener、outbox handler、bridge、enqueuer、job）只进入同域 application 入口——默认 `*ApplicationService`，纯读可以是同域 application 的 `*Query` 接口；不直接碰 domain、infrastructure、mapper/dataobject，不注入/调用外部域 `api.*` / `application.*`。controller 读路径的返回类型可以直接暴露外部 API 的 model 类型，语义相同不建镜像。
- 跨域只有两个入口：同步走 owner `api.query` / `api.action`（核心域同步依赖图必须无环），异步走 owner `contracts.event` + outbox。domain、infrastructure、mapper/dataobject、producer 域内部 event 实现都不是跨域入口。
- `domain` 不依赖 controller / application / infrastructure / Spring / `api.*`。
- `application` 不依赖 MyBatis mapper/dataobject、HTTP 传输类型（`ResponseEntity`、`MultipartFile` 等）、Kafka/broker 包；端口与方法签名不暴露 broker 词汇（topic、offset 等）。application 与 domain 不依赖**外部域**的 `contracts.event`，事件转换由入站适配器承担。
- 事务边界归 application：infrastructure 类不使用 `@Transactional`；read-check-write 不作并发互斥，前提条件（`expectedVersion` 等）传到 repository 由数据库 CAS / 行锁裁决；事务有界，不覆盖无界循环或 OSS / HTTP / MQ 远程 I/O。
- `api.*` 与 `contracts.event` 互不引用；同步与异步字段相同也分别定义 `api.model` 和 event payload。
- `infrastructure.api` 是评审例外面，当前没有任何 adapter：新增必须承担实质的协议/模型转换并登记 `InfraBoundaryArchTest` 的 reviewed 集合，纯转发不进该包。

命名：同域用例入口 `*ApplicationService`；跨实体的领域规则 `*DomainService` / `*Policy`；仓储接口 `*Repository`（`domain.repository`）；MyBatis 实现 `MyBatis*Repository`（`infrastructure.persistence`）；行对象 `*DataObject`（`infrastructure.persistence.dataobject`）。`*UseCase`、`*CommandService`、`*ActionService`、`*FacadeService` 与以域名命名的聚合门面 ApplicationService 都是禁止命名。根级 `service` / `entity` / `mapper` 旧包已全部移除，不得重建。

完整层规则、禁止模式清单与领域包说明见 [docs/handbook/architecture.md](docs/handbook/architecture.md)；修改这些规则时必须同步该文件与 ArchUnit 测试。

## 前端边界

- 浏览器流量统一走 gateway 的 `/api`、`/files`、`/ws/im`；视图和 store 不依赖后端服务内部地址。
- 路由守卫是体验边界，不是授权边界；后端授权才是权威。
- access token 只存 Pinia 内存，refresh token 只存 HttpOnly cookie；两者都不进 JS 可读的持久存储。
- 主站 HTTP 用 `frontend/src/api/http.js`，IM HTTP 用 `frontend/src/api/imCoreHttp.js`；不建页面级私有 client，保持共享的 Result、刷新、endpoint 解析与错误语义。
- API / WebSocket 地址走运行时 config 与 endpoint helper；IM WebSocket 的 `wsUrl` 与 ticket 从 `POST /api/im/sessions` 获取，不写死 IM worker 地址。
- 复杂页面状态收拢到 `frontend/src/views/*State.js` 模块并配同目录测试；组件只负责渲染与交互。
- 同一笔高风险写入的重试必须复用原 `Idempotency-Key`；换新 key 就是新的业务尝试。

## 数据与部署红线

- Compose 入口只有 `./deploy/deployment.sh`；不文档化、不自动化绕开它的直接 `docker compose` 调用（deploy 工具自身需要时除外）。
- `single` 是日常开发拓扑，`cluster` 用于多实例与集群路径验证；observability 对 `infra` / `single` 默认关、对 `cluster` 默认开，用 `--observability` / `--no-observability` 显式覆盖。
- 真实 secret 与本地 `deploy/.env*` 不入库；Nacos seed 只放非敏感配置，凭据与签名密钥留在 env 文件或 secret manager。
- 业务 schema 固定为 `community`、`community_oss`、`im_core`，空库结构以 `deploy/database/business/001_schema.sql` 为准。
- 开发期业务 MySQL 数据可丢弃：schema 变更同步 `001_schema.sql`、H2/MyBatis fixture、schema 契约与 `docs/handbook/data-and-storage.md`，再用 `reset-mysql` 重建目标 volume；在第一个数据必须存活的环境之前建立前向迁移基线。
- `reset-mysql` 与 `docker compose down -v` 是破坏性操作：仅在任务明确要求删除数据且已确认拓扑/项目时执行。

## 变更与验证

- 改动限定在所属模块与既有边界内；聚焦的修复或文档更新不夹带顺手重构。
- 行为变化要补回归覆盖；有聚焦测试时不用"编译通过"代替行为验证。
- 后端：先跑受影响模块的聚焦测试；涉及共享契约、运行时装配或多模块时 `cd backend && mvn test`。
- 前端：能跑聚焦 Vitest 就先跑；涉及共享路由、会话、API 或生产构建时 `cd frontend && npm test && npm run build`。
- 部署、schema、Nacos、观测、k6、Playwright、Mock Data Studio 改动：使用 [docs/handbook/testing.md](docs/handbook/testing.md) 与所属 README 记载的契约/测试套件。
- 文档类改动至少通过 `git diff --check -- AGENTS.md README.md docs frontend/README.md backend/README.md deploy/README.md tools`。
- 不在开发者本地拓扑上做破坏性验证；优先 render、静态契约、单测与一次性容器检查，除非破坏性本身就是任务目标。

架构或包边界变化时，除受影响测试外运行架构守卫：

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest' -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` 让共享模块使用最新构件，避免读到 `~/.m2` 旧版本；末尾的 flag 让没有架构测试的上游模块不因空匹配失败。本地 SNAPSHOT 依赖已安装过时，可用窄形式 `mvn test -pl :community-app -Dtest='*ArchTest'`。

## 文档守卫

- 长期文档只放 `docs/handbook`；根与子项目 README 是导航/操作入口，链接 handbook 而不另立事实来源。顶层模块、前置条件、启动命令、默认端口或文档入口变化时更新 `README.md`。
- 计划性设计/迁移文档也放 `docs/handbook` 并明确标注描述的是计划行为；落地后把行为并入所属 handbook 页面。
- 架构规则（分层、包边界、跨域入口、禁止模式）变化必须同步三处：本文件、[docs/handbook/architecture.md](docs/handbook/architecture.md)（按需含 system-design.md）、`app/arch/` 下的 ArchUnit 测试。
- 行为文档跟随所属代码，按 [docs/handbook/readme.md](docs/handbook/readme.md) 的维护清单分流：业务链路 → `business-logic/` 与 `business-flows.md`，契约 → `integration-contracts.md`，前端 → `frontend.md`，其余各归其页。

## Agent skills

- Issue tracker：GitHub Issues，见 [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md)。
- Triage 标签：默认五角色词汇，见 [docs/agents/triage-labels.md](docs/agents/triage-labels.md)。
- 域文档：single-context 布局，见 [docs/agents/domain.md](docs/agents/domain.md)。
