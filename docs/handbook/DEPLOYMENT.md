# 本地部署与启动

本文档说明当前仓库的本地 `docker compose` 启动方式。当前本地部署入口统一为两套拓扑：

- `single`：单机开发拓扑
- `cluster`：本地多副本 / 集群演练拓扑

## 1. 入口命令

统一使用：

```bash
./deploy/deployment.sh <command> [--topology single|cluster] [--scope full|infra] [--observability]
```

常用命令：

- `./deploy/deployment.sh up --topology single`
- `./deploy/deployment.sh up --topology single --scope infra`
- `./deploy/deployment.sh up --topology cluster`
- `./deploy/deployment.sh ps --topology cluster`
- `./deploy/deployment.sh logs --topology cluster community-gateway-1`
- `./deploy/deployment.sh config --topology single`

默认值：

- `--topology cluster`
- `--scope full`
- project name：`community-single` 或 `community-cluster`

## 2. 环境文件

推荐复制：

```bash
cp deploy/.env.single.example deploy/.env.single
cp deploy/.env.cluster.example deploy/.env.cluster
```

## 3. 拓扑说明

### 3.1 `single`

`single` 适合本地测试开发：

- `mysql`
- `redis`
- `kafka`
- `elasticsearch`
- `nacos`
- `xxl-job-admin`
- `community-app`
- `community-gateway`
- `im-core`
- `im-realtime`

### 3.2 `cluster`

`cluster` 适合本地演练多副本行为：

- `mysql-primary` + `mysql-replica-1/2`
- `redis-1..6`
- `kafka-1..3`
- `elasticsearch-1..3`
- `nacos-1..3`
- `xxl-job-admin-1/2`
- `community-app-1..3`
- `community-gateway-1..3`
- `im-core-1..3`
- `im-realtime-1..3`

## 4. 典型启动方式

### 4.1 单机全栈

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single
```

### 4.2 单机基础设施

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single --scope infra
```

适用于你在 IDE 里单独启动 `community-app` / `im-core` 等服务。

### 4.3 集群全栈

```bash
cp deploy/.env.cluster.example deploy/.env.cluster
./deploy/deployment.sh up --topology cluster
```

### 4.4 追加 observability

```bash
./deploy/deployment.sh up --topology single --observability
./deploy/deployment.sh up --topology cluster --observability
```

## 5. 默认端口

- 前端：`http://localhost:12881`
- API / files / WS 入口：`http://localhost:12880`
- Nacos：`http://localhost:18848/nacos`
- XXL-JOB：`http://localhost:12887/xxl-job-admin`
- MailHog：`http://localhost:8025`
- Mock Data Studio：`http://localhost:12890/health`
- Elasticsearch（observability）：`http://localhost:12888`
- Kibana（observability）：`http://localhost:12889`

## 6. 文件分层

- `deploy/compose.yml`
  共享顶层元数据与 volume
- `deploy/compose.infra.*.single.yml`
  单机基础设施
- `deploy/compose.infra.*.cluster.yml`
  集群基础设施
- `deploy/compose.runtime.services.single.yml`
- `deploy/compose.runtime.services.cluster.yml`
  业务 runtime
- `deploy/compose.runtime.frontend-nginx.single.yml`
- `deploy/compose.runtime.frontend-nginx.cluster.yml`
  前端与入口
- `deploy/compose.runtime.mock-data-studio.single.yml`
- `deploy/compose.runtime.mock-data-studio.cluster.yml`
  Studio wiring
- `deploy/compose.observability.yml`
  可选观测层

## 7. 停止与重置

停止：

```bash
./deploy/deployment.sh down --topology single
./deploy/deployment.sh down --topology cluster
```

删除数据卷：

```bash
./deploy/deployment.sh down --topology single -v
./deploy/deployment.sh down --topology cluster -v
```

如果启动时用了 `--observability`，停止时也带上同一组参数。
