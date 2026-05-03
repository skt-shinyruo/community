# deploy/

本目录现在同时支持两套本地拓扑：

- `single`：单机开发拓扑，适合本地调试、联调、功能验证
- `cluster`：本地多副本 / 集群演练拓扑，适合多实例和集群路径验证

统一入口仍然是 `./deploy/deployment.sh`。

> 约定：本文档中的命令默认从仓库根目录执行。

## 常用命令

- 单机全栈：`./deploy/deployment.sh up --topology single`
- 单机基础设施：`./deploy/deployment.sh up --topology single --scope infra`
- 集群全栈：`./deploy/deployment.sh up --topology cluster`
- 查看状态：`./deploy/deployment.sh ps --topology single`
- 查看日志：`./deploy/deployment.sh logs --topology cluster community-gateway-1`
- 渲染配置：`./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example`
- 追加观测层：`./deploy/deployment.sh up --topology cluster --observability`

默认 compose project name：

- `community-single`
- `community-cluster`

如需覆盖，继续使用 `-p` / `--project-name`。

## 环境文件

推荐使用拓扑专属 env：

- `cp deploy/.env.single.example deploy/.env.single`
- `cp deploy/.env.cluster.example deploy/.env.cluster`

## 文件结构

- `compose.yml`
  共享顶层元数据与 volume 定义
- `compose.infra.*.single.yml`
  `single` 单机基础设施
- `compose.infra.*.cluster.yml`
  `cluster` 多节点基础设施
- `compose.infra.mailhog.yml`
  共享 MailHog
- `compose.infra.mock-data-studio-bootstrap.single.yml`
- `compose.infra.mock-data-studio-bootstrap.cluster.yml`
  拓扑专属 MySQL bootstrap sidecar
- `compose.runtime.services.single.yml`
  单机 `community-app` / `community-gateway` / `community-im-gateway` / `im-core` / `im-realtime`
- `compose.runtime.services.cluster.yml`
  多副本 runtime 服务
- `compose.runtime.frontend-nginx.single.yml`
- `compose.runtime.frontend-nginx.cluster.yml`
  拓扑专属前端和 Nginx 入口
- `compose.runtime.mock-data-studio.single.yml`
- `compose.runtime.mock-data-studio.cluster.yml`
  拓扑专属 studio wiring
- `nginx/nginx.single.conf`
- `nginx/nginx.cluster.conf`
  拓扑专属 ingress upstream
- `compose.observability.yml`
  可选 observability overlay

## 快速开始

### 单机开发拓扑

1. 准备环境文件：
   `cp deploy/.env.single.example deploy/.env.single`
2. 启动全栈：
   `./deploy/deployment.sh up --topology single`
3. 或者只启动基础设施：
   `./deploy/deployment.sh up --topology single --scope infra`

默认入口：

- 前端：`http://localhost:12881`
- 统一入口：`http://localhost:12880`
- IM session bootstrap：`POST http://localhost:12880/api/im/sessions`
- IM WebSocket：session `wsUrl` 默认 `ws://localhost:12880/ws/im`
- Nacos：`http://localhost:18848/nacos`
- XXL-JOB：`http://localhost:12887/xxl-job-admin`
- MailHog：`http://localhost:8025`

### 本地集群演练拓扑

1. 准备环境文件：
   `cp deploy/.env.cluster.example deploy/.env.cluster`
2. 启动：
   `./deploy/deployment.sh up --topology cluster`

默认入口与 `single` 保持一致，但后端与中间件是多副本 / 多节点形态。

## 拓扑速览

### `single`

- MySQL：`mysql`
- Redis：`redis`
- Kafka：`kafka`
- Elasticsearch：`elasticsearch`
- Nacos：`nacos`
- XXL-JOB：`xxl-job-admin`
- Runtime：`community-app` / `community-gateway` / `community-im-gateway` / `im-core` / `im-realtime`

### `cluster`

- MySQL：`mysql-primary` + `mysql-replica-1/2`
- Redis：`redis-1..6` + `redis-cluster-bootstrap`
- Kafka：`kafka-1..3` + `kafka-init`
- Elasticsearch：`elasticsearch-1..3` + `es-init`
- Nacos：`nacos-1..3` + `nacos-db-bootstrap`
- XXL-JOB：`xxl-job-admin-1/2`
- Runtime：`community-app-1..3` / `community-gateway-1..3` / `community-im-gateway-1..3` / `im-core-1..3` / `im-realtime-1..3`

## 停止与清理

- 停止：`./deploy/deployment.sh down --topology single`
- 完全重置：`./deploy/deployment.sh down --topology cluster -v`

如果你启动时叠加了 `--observability`，停止时也请带上相同参数组合。

## 观测层

两套拓扑都能追加 observability：

- `./deploy/deployment.sh up --topology single --observability`
- `./deploy/deployment.sh up --topology cluster --observability`

默认端口：

- Elasticsearch：`http://localhost:12888`
- Kibana：`http://localhost:12889`

更多说明见 `docs/handbook/operations.md`。
