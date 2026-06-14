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
- 关闭观测层：`./deploy/deployment.sh up --topology cluster --no-observability`

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
  默认启用的 observability overlay

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
- Nacos：`http://localhost:18848/nacos`，作为服务注册中心和非密钥配置中心。
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

`nacos-config-bootstrap` 会把 `deploy/nacos/config/*.yaml` 发布到 Nacos group
`COMMUNITY`。这些 seed 文件不得包含密码、token、access key、JWT HMAC secret 或
其他密钥。

## 停止与清理

- 停止：`./deploy/deployment.sh down --topology single`
- 完全重置：`./deploy/deployment.sh down --topology cluster -- -v`

`-v` 是传给 `docker compose down` 的参数，要放在 `--` 后面。默认 project name 是 `community-single` / `community-cluster`，默认 volume namespace 是 `community_single` / `community_cluster`，对应的 MySQL 数据卷名分别是 `community_single_mysql_primary_data` / `community_cluster_mysql_primary_data`。

如需给 volume 使用独立前缀，可在命令前设置 `COMMUNITY_VOLUME_NAMESPACE`，例如 `COMMUNITY_VOLUME_NAMESPACE=community_smoke ./deploy/deployment.sh up --topology single`。

如果你启动时带了 `--no-observability`，停止时也请带上相同参数组合。

## 观测层

两套拓扑默认都会启用 observability。普通启动会加载 `deploy/compose.observability.yml`，并默认开启后端 OTel tracing：

- `./deploy/deployment.sh up --topology single`
- `./deploy/deployment.sh up --topology cluster`

需要关闭整个观测 overlay 时使用：

```bash
./deploy/deployment.sh up --topology single --no-observability
```

如需保留观测 overlay 但临时关闭 tracing，在命令前显式设置：

```bash
OTEL_ENABLED=false ./deploy/deployment.sh up --topology single
```

默认端口：

- Elasticsearch：`http://localhost:12888`
- Kibana：`http://localhost:12889`

### Observability Smoke

After the stack is up, verify that logs and traces are queryable:

```bash
./deploy/tests/observability_smoke.sh
```

The script calls `GET /api/runtime-config`, extracts a `traceId` from the response
body or `traceparent` header, and checks Elasticsearch for:

- backend JSON logs in `logs-community-default`
- runtime stability events
- a matching trace document in `traces-*`
- request-correlated logs with the same `trace.id`

For a short diagnostics run, start with `RUNTIME_DIAGNOSTICS_ENABLED=true` and set:

```bash
OBSERVABILITY_EXPECT_DIAGNOSTICS=true ./deploy/tests/observability_smoke.sh
```

日志路径是 backend JSON stdout -> Docker container logs -> EDOT collector -> Elasticsearch / Kibana。更多说明见 `docs/handbook/operations.md`。

### Optional Runtime Diagnostics Agent

Backend images include a generic JVM runtime diagnostics agent at `/otel/runtime-diagnostics-agent.jar`. It is disabled by default. Enable it for a short diagnostic run with:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true RUNTIME_DIAGNOSTICS_INCLUDES='com.nowcoder.community.*' ./deploy/deployment.sh up --topology single
```

The Phase 1 probes are `method`, `exception`, `thread`, and `jvm`. The agent emits `event.category=runtime_diagnostics` logs to the same stdout -> EDOT -> Elasticsearch path as other backend logs. It does not collect method arguments, return values, request bodies, SQL bind values, Redis keys or values, Kafka payloads, JWTs, cookies, or secrets.

Enable dependency probes only for focused diagnostic runs:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true RUNTIME_DIAGNOSTICS_PROBES='method,exception,thread,jvm,http,jdbc,redis,kafka' ./deploy/deployment.sh up --topology single
```

Dependency probes emit summaries and slow-call events. They do not record HTTP bodies, SQL bind values, Redis keys or values, Kafka payloads, cookies, JWTs, or authorization headers.

Tune dependency thresholds with `RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS`, `RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS`, `RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS`, and `RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS`. Matching sample and rate-limit settings use the corresponding `RUNTIME_DIAGNOSTICS_HTTP_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_JDBC_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_REDIS_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_KAFKA_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND`, `RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND`, `RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND`, and `RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND` variables. Kafka topic names remain hashed unless `RUNTIME_DIAGNOSTICS_KAFKA_TOPIC_NAMES_ENABLED=true` is set explicitly.
