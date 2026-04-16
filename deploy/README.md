# deploy/

本目录现在同时支持两套本地拓扑：

- `dev`：单机开发拓扑，适合本地调试、联调、功能验证
- `ha`：本地 HA 演练拓扑，适合多副本和集群路径验证

统一入口仍然是 `./deploy/deployment.sh`。

> 约定：本文档中的命令默认从仓库根目录执行。

## 常用命令

- 单机全栈：`./deploy/deployment.sh up --topology dev`
- 单机基础设施：`./deploy/deployment.sh up --topology dev --scope infra`
- HA 全栈：`./deploy/deployment.sh up --topology ha`
- 查看状态：`./deploy/deployment.sh ps --topology dev`
- 查看日志：`./deploy/deployment.sh logs --topology ha community-gateway-1`
- 渲染配置：`./deploy/deployment.sh config --topology dev`
- 追加观测层：`./deploy/deployment.sh up --topology ha --observability`

默认 compose project name：

- `community-dev`
- `community-ha`

如需覆盖，继续使用 `-p` / `--project-name`。

## 环境文件

推荐使用拓扑专属 env：

- `cp deploy/.env.dev.example deploy/.env.dev`
- `cp deploy/.env.ha.example deploy/.env.ha`

兼容路径仍然保留：

- `cp deploy/.env.example deploy/.env`
- 然后执行 `./deploy/deployment.sh up`

这条兼容路径等价于旧的 HA 默认行为。

## 文件结构

- `compose.yml`
  共享顶层元数据与 volume 定义
- `compose.infra.*.dev.yml`
  `dev` 单机基础设施
- `compose.infra.*.ha.yml`
  `ha` 多节点基础设施
- `compose.infra.mailhog.yml`
  共享 MailHog
- `compose.infra.mock-data-studio-bootstrap.dev.yml`
- `compose.infra.mock-data-studio-bootstrap.ha.yml`
  拓扑专属 MySQL bootstrap sidecar
- `compose.runtime.services.dev.yml`
  单机 `community-app` / `community-gateway` / `im-core` / `im-realtime`
- `compose.runtime.services.ha.yml`
  多副本 runtime 服务
- `compose.runtime.frontend-nginx.dev.yml`
- `compose.runtime.frontend-nginx.ha.yml`
  拓扑专属前端和 Nginx 入口
- `compose.runtime.mock-data-studio.dev.yml`
- `compose.runtime.mock-data-studio.ha.yml`
  拓扑专属 studio wiring
- `nginx/nginx.dev.conf`
- `nginx/nginx.ha.conf`
  拓扑专属 ingress upstream
- `compose.observability.yml`
  可选 observability overlay

## 快速开始

### 单机开发拓扑

1. 准备环境文件：
   `cp deploy/.env.dev.example deploy/.env.dev`
2. 启动全栈：
   `./deploy/deployment.sh up --topology dev`
3. 或者只启动基础设施：
   `./deploy/deployment.sh up --topology dev --scope infra`

默认入口：

- 前端：`http://localhost:12881`
- 统一入口：`http://localhost:12880`
- Nacos：`http://localhost:18848/nacos`
- XXL-JOB：`http://localhost:12887/xxl-job-admin`
- MailHog：`http://localhost:8025`

### 本地 HA 演练拓扑

1. 准备环境文件：
   `cp deploy/.env.ha.example deploy/.env.ha`
2. 启动：
   `./deploy/deployment.sh up --topology ha`

默认入口与 `dev` 保持一致，但后端与中间件是多副本/多节点形态。

## 拓扑速览

### `dev`

- MySQL：`mysql`
- Redis：`redis`
- Kafka：`kafka`
- Elasticsearch：`elasticsearch`
- Nacos：`nacos`
- XXL-JOB：`xxl-job-admin`
- Runtime：`community-app` / `community-gateway` / `im-core` / `im-realtime`

### `ha`

- MySQL：`mysql-primary` + `mysql-replica-1/2`
- Redis：`redis-1..6` + `redis-cluster-bootstrap`
- Kafka：`kafka-1..3` + `kafka-init`
- Elasticsearch：`elasticsearch-1..3` + `es-init`
- Nacos：`nacos-1..3` + `nacos-db-bootstrap`
- XXL-JOB：`xxl-job-admin-1/2`
- Runtime：`community-app-1..3` / `community-gateway-1..3` / `im-core-1..3` / `im-realtime-1..3`

## 停止与清理

- 停止：`./deploy/deployment.sh down --topology dev`
- 完全重置：`./deploy/deployment.sh down --topology ha -v`

如果你启动时叠加了 `--observability`，停止时也请带上相同参数组合。

## 观测层

两套拓扑都能追加 observability：

- `./deploy/deployment.sh up --topology dev --observability`
- `./deploy/deployment.sh up --topology ha --observability`

默认端口：

- Elasticsearch：`http://localhost:12888`
- Kibana：`http://localhost:12889`

更多说明见 [docs/OBSERVABILITY.md](/home/feng/code/project/community/.worktrees/deploy-dev-ha-dual-topology/docs/OBSERVABILITY.md)。
