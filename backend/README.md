# backend

本目录是后端工程根目录（Java 17 + Spring Boot 3 + Maven 多模块）。
仓库根目录同时包含前端工程：`../frontend/`。

> 约定：仓库级入口（部署/文档）从仓库根目录执行；后端构建/测试从 `backend/` 执行。

## 全栈启动（deployment.sh / layered compose，推荐：gateway-first）

> 从仓库根目录执行（见 `../deploy/README.md` 获取可选 overlay 与显式 layered compose 命令）：

1. 单机开发（推荐）：
   - `cp deploy/.env.single.example deploy/.env.single`
   - `./deploy/deployment.sh up --topology single`
2. 只起基础设施，IDE 本地起服务：
   - `./deploy/deployment.sh up --topology single --scope infra`
3. 本地集群演练：
   - `cp deploy/.env.cluster.example deploy/.env.cluster`
   - `./deploy/deployment.sh up --topology cluster`
4. 访问：
   - 前端：`http://localhost:12881`
   - 统一入口：`http://localhost:12880/api/...`
   - 可观测性（可选）：`./deploy/deployment.sh up --topology single --observability`

## 本地开发 / 测试
- 单测：`mvn test`
- 打包后端单体：`mvn -q -DskipTests -pl :community-app -am package`

## 目录结构（后端）
- `community-app/`：主业务单体与 deployable
- `community-gateway/`：统一 HTTP / WS edge
- `community-im/`：IM 聚合模块（下含 `im-common`、`im-core`、`im-realtime`）

## 仓库级入口
- 部署与 compose：`../deploy/`
- 文档：`../docs/`

## 文档
- `../docs/handbook/readme.md`
- `../docs/handbook/architecture.md`
- `../docs/handbook/system-design.md`
- `../docs/handbook/business-flows.md`
- `../docs/handbook/reliability.md`
- `../docs/handbook/testing.md`
- `../docs/handbook/local-development.md`
- `../deploy/README.md`
- `../docs/handbook/security.md`
- `../docs/handbook/data-and-storage.md`
- `../docs/handbook/operations.md`
