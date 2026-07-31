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
- 只重置 MySQL：`./deploy/deployment.sh reset-mysql --topology single`

默认 compose project name：

- `community-single`
- `community-cluster`

如需覆盖，继续使用 `-p` / `--project-name`。自定义 project 必须同时提供独立的 volume namespace、网段、动态地址范围、NGINX/Gateway 静态地址和对应 trusted-proxy CIDR；脚本会在调用 Compose 前拒绝仍复用任一默认拓扑值的配置，避免两个 project 争用相同地址或数据卷。

## 环境文件

推荐使用拓扑专属 env：

- `cp deploy/.env.single.example deploy/.env.single`
- `cp deploy/.env.cluster.example deploy/.env.cluster`

`deployment.sh` 只按白名单读取拓扑变量，不会 `source` env 文件。拓扑值的优先级为当前 shell 环境、env 文件、内置默认值。这样升级前创建、尚未包含新网络键的 `.env.single` / `.env.cluster` 仍可使用默认拓扑；Compose 文件中的必填插值继续保护绕过入口脚本的直接调用。

内置 single 默认值为 `172.30.0.0/24`、动态范围 `172.30.0.128/25`、NGINX `172.30.0.10`、Gateway `172.30.0.20`。cluster 对应为 `172.31.0.0/24`、动态范围 `172.31.0.128/25`、NGINX `172.31.0.10`、三个 Gateway `172.31.0.20` 到 `172.31.0.22`。

自定义 single project 需要覆盖：`COMMUNITY_VOLUME_NAMESPACE`、`COMMUNITY_NETWORK_SUBNET`、`COMMUNITY_NETWORK_DYNAMIC_RANGE`、`NGINX_STATIC_IP`、`COMMUNITY_GATEWAY_STATIC_IP`、`GATEWAY_TRUSTED_PROXY_CIDRS`、`COMMUNITY_APP_TRUSTED_PROXY_CIDRS`。cluster 使用同一组公共键，并把单 Gateway 键替换为 `COMMUNITY_GATEWAY_1_STATIC_IP`、`COMMUNITY_GATEWAY_2_STATIC_IP`、`COMMUNITY_GATEWAY_3_STATIC_IP`。

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
- Nacos 3.1.2：`http://localhost:18848/nacos`，作为服务注册中心和非密钥配置中心。
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
`COMMUNITY`。启动时它接收 `BROWSER_ALLOWED_ORIGINS`、`FRONTEND_PUBLIC_ORIGIN`、
`OSS_PUBLIC_BASE_URL` 和 `IM_GATEWAY_PUBLIC_WS_URL`，将它们渲染到 Gateway、
community-app、community-oss 和 IM 的浏览器 CORS/OriginGuard/公开端点 seed 后再上传；
原始 seed 文件保持只读模板。这样动态端口和本地默认端口走同一条配置链，runtime service
不需要接收 owner-specific CORS 环境变量。这些 seed 文件不得包含密码、token、access
key、JWT HMAC secret 或其他密钥。

Nacos 3.1.2 使用 `/nacos/v3/admin/core/state/readiness` 作为 readiness 接口，Compose
健康检查会同时验证响应中的 `code=0` 和 9848 gRPC 端口。`nacos-db-bootstrap` 会在
新数据库导入 3.1.2 基线；检测到已有 Nacos 2.3.2 数据库时，会保留历史数据并幂等补齐
`config_info_gray` 及 `his_config_info` 的灰度字段。

## 停止与清理

- 停止：`./deploy/deployment.sh down --topology single`
- 只重置 MySQL：`./deploy/deployment.sh reset-mysql --topology single`
- 完全重置：`./deploy/deployment.sh down --topology cluster -- -v`

`reset-mysql` 会先停止完整拓扑，只删除明确命名的 MySQL volumes，并保留 Redis、Kafka、Garage、Elasticsearch 等数据。它只接受 `--scope full`。single 删除 primary volume；cluster 删除 primary 和两个 replica volumes。此操作不可恢复。

`-v` 是传给 `docker compose down` 的参数，要放在 `--` 后面，会删除该拓扑的所有 Compose volumes。默认 project name 是 `community-single` / `community-cluster`，默认 volume namespace 是 `community_single` / `community_cluster`，对应的 MySQL 数据卷名分别是 `community_single_mysql_primary_data` / `community_cluster_mysql_primary_data`。

三个业务 schema 固定为 `community`、`community_oss`、`im_core`，最终结构统一维护在 `mysql/primary-init/010_current_schema.sql`。MySQL entrypoint 只在 primary volume 为空时执行该文件。改表后应修改最终 `CREATE TABLE`、同步测试 schema，运行 `reset-mysql`，再重新 `up`；不要在已有 volume 上重放快照。development 身份数据位于 `mysql/community/090_seed_identity.sql`，不属于当前态 schema。

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

To require specific event categories during a focused scenario run:

```bash
OBSERVABILITY_EXPECT_EVENT_CATEGORIES=runtime,database,messaging ./deploy/tests/observability_smoke.sh
```

Only require categories that the scenario has actually exercised. The default smoke keeps category checks broad so a fresh local stack is not forced to emit every subsystem event.

The script calls `GET /api/runtime-config`, extracts a `traceId` from the response
body or `traceparent` header, and checks Elasticsearch for:

- backend JSON logs in `logs-community-default`
- runtime stability events
- a matching trace document in `traces-*`
- request-correlated logs with the same `trace.id`

For a short YierLoom capture, start the stack with YierLoom enabled as described below, then set:

```bash
OBSERVABILITY_EXPECT_DIAGNOSTICS=true ./deploy/tests/observability_smoke.sh
```

日志路径是 backend JSON stdout / OTLP logs -> EDOT collector logs pipeline -> Elasticsearch / Kibana。更多说明见 `docs/handbook/operations.md`。

### Optional YierLoom Agent

Backend images include YierLoom at `/otel/yierloom-agent.jar`. It is disabled by default and is intended for short, focused troubleshooting sessions. Enable it with a narrow method include:

```bash
YIERLOOM_ENABLED=true \
YIERLOOM_PLUGIN__METHOD__INCLUDES='com.nowcoder.community.*' \
./deploy/deployment.sh up --topology single
```

The built-in `method`, `exception`, `thread`, and `jvm` plugins are enabled when the Agent starts. YierLoom emits `event.category=yierloom` logs with `diagnostic.plugin.id` through the same observability path as other backend logs. Its event queue is bounded; `YIERLOOM_EVENTS_QUEUE_CAPACITY` defaults to `8192`, and a full queue drops new observations or events instead of blocking instrumented application work.

Dependency plugins are opt-in. Enable only the plugin needed for the capture; for example, the HTTP plugin and a two-second slow threshold use duration syntax such as `2s`:

```bash
YIERLOOM_ENABLED=true \
YIERLOOM_PLUGIN__HTTP__ENABLED=true \
YIERLOOM_PLUGIN__HTTP__SLOW_THRESHOLD=2s \
./deploy/deployment.sh up --topology single
```

The equivalent switches are `YIERLOOM_PLUGIN__JDBC__ENABLED=true`, `YIERLOOM_PLUGIN__REDIS__ENABLED=true`, and `YIERLOOM_PLUGIN__KAFKA__ENABLED=true`. Sample rates and per-second limits follow the same plugin-scoped mapping, for example `YIERLOOM_PLUGIN__HTTP__SAMPLE_RATE` and `YIERLOOM_PLUGIN__HTTP__MAX_EVENTS_PER_SECOND`. Kafka topic names are hashed by default; disclose raw names only by explicitly setting `YIERLOOM_PLUGIN__KAFKA__TOPIC_NAMES_ENABLED=true`.

YierLoom must not collect method arguments, return values, request or response bodies, SQL bind values, Redis keys or values, Kafka payloads, credentials, cookies, or headers. Disable it immediately after the capture window and restart the target services:

```bash
YIERLOOM_ENABLED=false ./deploy/deployment.sh up --topology single
```

#### Trusted External Plugins

External plugins are trusted code and must be reviewed before deployment. Build each plugin as one fat JAR with exactly one `YierLoomPlugin` ServiceLoader provider and its private dependencies. Do not bundle YierLoom API, YierLoom SDK, or Byte Buddy classes in that JAR.

Before installation, call the `yierloom-plugin-testkit` Java API `PluginContractVerifier.verifyOrThrow(Path)` against the finished artifact:

```java
PluginContractVerifier.verifyOrThrow(Path.of("/path/to/plugin.jar"));
```

`PluginContractVerifier` has no CLI. After verification, mount or copy the fat JAR into `/opt/yierloom/plugins`, or point `YIERLOOM_PLUGINS_DIR` at another plugin directory, then restart the target JVM. YierLoom does not support hot reload or runtime attach; changing, adding, or removing a plugin always requires a JVM restart.
