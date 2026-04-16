# 本地部署与启动

本文档说明当前仓库的本地 `docker compose` 启动方式。部署入口已经从“单一 HA 默认栈”扩展为两套拓扑：

- `dev`：单机开发拓扑
- `ha`：本地 HA 演练拓扑

## 1. 入口命令

统一使用：

```bash
./deploy/deployment.sh <command> [--topology dev|ha] [--scope full|infra] [--observability]
```

常用命令：

- `./deploy/deployment.sh up --topology dev`
- `./deploy/deployment.sh up --topology dev --scope infra`
- `./deploy/deployment.sh up --topology ha`
- `./deploy/deployment.sh ps --topology ha`
- `./deploy/deployment.sh logs --topology ha community-gateway-1`
- `./deploy/deployment.sh config --topology dev`

默认值：

- `--topology ha`
- `--scope full`
- project name：`community-dev` 或 `community-ha`

## 2. 环境文件

推荐复制：

```bash
cp deploy/.env.dev.example deploy/.env.dev
cp deploy/.env.ha.example deploy/.env.ha
```

兼容旧路径：

```bash
cp deploy/.env.example deploy/.env
./deploy/deployment.sh up
```

这条旧路径等价于 HA 默认启动。

## 3. 拓扑说明

### 3.1 `dev`

`dev` 适合本地测试开发：

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

### 3.2 `ha`

`ha` 适合本地演练多副本行为：

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
cp deploy/.env.dev.example deploy/.env.dev
./deploy/deployment.sh up --topology dev
```

### 4.2 单机基础设施

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/deployment.sh up --topology dev --scope infra
```

适用于你在 IDE 里单独启动 `community-app` / `im-core` 等服务。

### 4.3 HA 全栈

```bash
cp deploy/.env.ha.example deploy/.env.ha
./deploy/deployment.sh up --topology ha
```

### 4.4 追加 observability

```bash
./deploy/deployment.sh up --topology dev --observability
./deploy/deployment.sh up --topology ha --observability
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
- `deploy/compose.infra.*.dev.yml`
  单机基础设施
- `deploy/compose.infra.*.ha.yml`
  HA 基础设施
- `deploy/compose.runtime.services.dev.yml`
- `deploy/compose.runtime.services.ha.yml`
  业务 runtime
- `deploy/compose.runtime.frontend-nginx.dev.yml`
- `deploy/compose.runtime.frontend-nginx.ha.yml`
  前端与入口
- `deploy/compose.runtime.mock-data-studio.dev.yml`
- `deploy/compose.runtime.mock-data-studio.ha.yml`
  Studio wiring
- `deploy/compose.observability.yml`
  可选观测层

## 7. 停止与重置

停止：

```bash
./deploy/deployment.sh down --topology dev
./deploy/deployment.sh down --topology ha
```

删除数据卷：

```bash
./deploy/deployment.sh down --topology dev -v
./deploy/deployment.sh down --topology ha -v
```

如果启动时用了 `--observability`，停止时也带上同一组参数。
