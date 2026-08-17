# Community

一个覆盖社区内容、社交互动、实时 IM、搜索、成长体系、钱包与交易场景的全栈项目。仓库采用
monorepo，包含 Vue 3 SPA、Java 17 / Spring Boot 4 后端、开发工具、测试套件，以及 infra / single /
cluster 三套彼此独立的 Docker Compose Stack。

根 README 只提供项目入口。当前行为与维护约定以 [开发者手册](docs/handbook/readme.md)、代码、部署配置和
架构守卫测试为准。

## 系统形态

```text
Browser / Client
  -> community-gateway
      -> community-app          主站业务与跨域编排
      -> community-oss          对象元数据、签名 URL、文件下载
      -> community-im-gateway   IM session 与稳定 WebSocket edge
      -> im-core                IM 历史、未读、房间权威状态

owner ApplicationService
  -> outbox -> Kafka -> consumer ApplicationService
```

`community-app` 是按业务包治理边界的 package-scoped monolith。域内请求进入同域
`ApplicationService`；同步跨域协作走 owner-domain `api.*`，异步跨域协作走 owner-domain
`contracts.event` 与 outbox。IM 独立为 `im-realtime` 和 `im-core` 两个运行时服务。

## 仓库结构

| 路径 | 说明 |
| --- | --- |
| `frontend/` | Vue 3、Pinia、Vue Router、Vite、Vitest 构成的 SPA。 |
| `backend/community-app/` | 主站业务 owner，采用轻量领域分层。 |
| `backend/community-gateway/` | 浏览器 HTTP / WebSocket 统一入口。 |
| `backend/community-im-gateway/` | IM session bootstrap 与稳定 `/ws/im` edge。 |
| `backend/community-im/` | `im-core`、`im-realtime`、共享 IM contract 与 session ticket 协议。 |
| `backend/community-oss/` | 对象存储 owner；typed client 位于 `backend/community-oss-client/`。 |
| `backend/community-common/` | 错误协议、安全、Web、幂等、outbox、可观测性等共享基础设施。 |
| `deploy/` | 本地拓扑、Nacos seed、业务 schema、观测配置与部署契约测试。 |
| `tools/mock-data-studio/` | 仅用于本地开发的同步测试数据 CLI。 |
| `tests/k6/` | k6 性能测试场景与结构契约。 |
| `tests/playwright-single/` | 面向已启动 single 拓扑的浏览器验收套件。 |
| `docs/handbook/` | 当前架构、业务、开发、测试与运维文档的 SSOT。 |

## 环境要求

运行完整本地拓扑需要 Docker Engine 与 Docker Compose plugin。直接开发各工程时还需要：

- JDK 17、Maven 3.8+
- Node.js >= 20.19（或 >= 22.12）与 npm（与 Vite 8、CI 环境一致）
- k6（仅运行性能场景时需要）

## 快速开始

推荐先启动 single 全栈：

```bash
cp deploy/stacks/single/.env.example deploy/stacks/single/.env
./deploy/deployment.sh up --stack single
```

single 默认不加载 observability overlay。需要 Elasticsearch、Kibana 与 OTel collector 时显式开启：

```bash
./deploy/deployment.sh up --stack single --observability
```

常用操作：

```bash
./deploy/deployment.sh ps --stack single
./deploy/deployment.sh logs --stack single community-app
./deploy/deployment.sh down --stack single
```

如果 single 启动时使用了 `--observability`，停止时也要带上相同参数。cluster 默认加载观测层；集群演练使用
`deploy/stacks/cluster/.env` 和 `--stack cluster`。完整矩阵、Stack 隔离与数据清理说明见
[部署文档](deploy/README.md)。

## 默认入口

| 能力 | 地址 |
| --- | --- |
| 前端 | `http://localhost:12881` |
| API / files / WebSocket gateway | `http://localhost:12880` |
| IM session bootstrap | `POST http://localhost:12880/api/im/sessions` |
| Nacos | `http://localhost:18848/nacos` |
| MailHog | `http://localhost:8025` |
| Elasticsearch | `http://localhost:12888`（observability 启用时） |
| Kibana | `http://localhost:12889`（observability 启用时） |

## 本地开发

只启动基础设施，业务服务从 IDE 或命令行运行：

```bash
cp deploy/stacks/infra/.env.example deploy/stacks/infra/.env
./deploy/deployment.sh up --stack infra
./deploy/deployment.sh render-backend-env --stack infra
```

`infra` 使用独立的 Compose project、network、volume 和宿主机端口。生成的六个后端服务 env 位于
`backend/env/generated/`，端口分配和 IDE 启动顺序见[本地开发](docs/handbook/local-development.md#宿主机启动后端)。

后端构建与测试从 `backend/` 执行：

```bash
cd backend
mvn test
mvn -q -DskipTests -pl :community-app -am package
```

前端开发与验证从 `frontend/` 执行：

```bash
cd frontend
npm ci
npm run dev
npm test
npm run build
```

修改后端架构规则或包边界时，额外运行：

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

完整测试分层、数据库契约、Playwright、k6 与 Mock Data Studio 验证命令见
[测试策略](docs/handbook/testing.md)。

## 文档入口

- [开发者手册](docs/handbook/readme.md)：所有长期维护文档的索引与同步清单。
- [项目概览](docs/handbook/overview.md)：运行时边界、主请求路径与推荐源码阅读顺序。
- [架构规则](docs/handbook/architecture.md)：模块所有权、轻量领域分层与跨域协作。
- [系统设计](docs/handbook/system-design.md)：同步 API、事件、投影、最终一致与失败语义。
- [业务逻辑](docs/handbook/business-logic/README.md)：按业务域组织的详细实现说明。
- [前端核心逻辑](docs/handbook/frontend.md)：路由、会话、HTTP、IM realtime 与页面状态。
- [本地开发](docs/handbook/local-development.md)：拓扑、端口、环境文件与本地联调。
- [测试策略](docs/handbook/testing.md)：后端、前端、架构、可靠性与端到端测试。
- [运行排障](docs/handbook/operations.md)：observability、scheduler、outbox 与故障恢复。
- [安全模型](docs/handbook/security.md)：JWT、cookie、Origin、internal scope 与 fail-closed。
- [后端工程](backend/README.md)、[前端工程](frontend/README.md)、[部署入口](deploy/README.md)。
