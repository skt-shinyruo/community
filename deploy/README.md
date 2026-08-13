# deploy/

本目录支持三个彼此独立的本地 Stack：

- `infra`：单节点基础设施，供宿主机后端开发
- `single`：基础设施、后端、前端一起运行的完整单机 Stack
- `cluster`：多副本 / 多节点完整 Stack，适合集群路径验证

统一入口仍然是 `./deploy/deployment.sh`。

> 约定：本文档中的命令默认从仓库根目录执行。

## 常用命令

- 基础设施：`./deploy/deployment.sh up --stack infra`
- 生成宿主机后端 env：`./deploy/deployment.sh render-backend-env --stack infra`
- 单机全栈：`./deploy/deployment.sh up --stack single`
- 集群全栈：`./deploy/deployment.sh up --stack cluster`
- 查看状态：`./deploy/deployment.sh ps --stack single`
- 查看日志：`./deploy/deployment.sh logs --stack cluster community-gateway-1`
- 渲染配置：`./deploy/deployment.sh config --stack single --env-file deploy/stacks/single/.env.example`
- 关闭观测层：`./deploy/deployment.sh up --stack cluster --no-observability`
- 只重置 MySQL：`./deploy/deployment.sh reset-mysql --stack single`
- 验证全部部署契约：`./deploy/tests/run-contracts.sh`

默认 Compose project name：

- `community-infra`
- `community-single`
- `community-cluster`

三个 Stack 默认使用独立的 volume namespace、网络和宿主机端口，可以并存。自定义 project 仍需使用
`-p` / `--project-name`，并为 volume namespace、网段、动态地址范围、静态地址和宿主机端口提供独立值。

## 环境文件

推荐使用 Stack 专属 env：

- `cp deploy/stacks/infra/.env.example deploy/stacks/infra/.env`
- `cp deploy/stacks/single/.env.example deploy/stacks/single/.env`
- `cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env`

`deployment.sh` 只按白名单读取 Stack 和拓扑变量，不会 `source` env 文件。值的优先级为当前 shell 环境、Stack
env、内置默认值。Compose 文件中的必填插值继续保护绕过入口脚本的直接调用。

内置 single 默认值为 `172.30.0.0/24`、动态范围 `172.30.0.128/25`、NGINX `172.30.0.10`、Gateway `172.30.0.20`。cluster 对应为 `172.31.0.0/24`、动态范围 `172.31.0.128/25`、NGINX `172.31.0.10`、三个 Gateway `172.31.0.20` 到 `172.31.0.22`。

自定义 single project 需要覆盖：`COMMUNITY_VOLUME_NAMESPACE`、`COMMUNITY_NETWORK_SUBNET`、`COMMUNITY_NETWORK_DYNAMIC_RANGE`、`NGINX_STATIC_IP`、`COMMUNITY_GATEWAY_STATIC_IP`、`GATEWAY_TRUSTED_PROXY_CIDRS`、`COMMUNITY_APP_TRUSTED_PROXY_CIDRS`。cluster 使用同一组公共键，并把单 Gateway 键替换为 `COMMUNITY_GATEWAY_1_STATIC_IP`、`COMMUNITY_GATEWAY_2_STATIC_IP`、`COMMUNITY_GATEWAY_3_STATIC_IP`。

## 文件结构

```text
deploy/
  stacks/{infra,single,cluster}/  # 独立入口、env 模板和操作说明
  compose/
    base.yml                     # 共享网络和 volume 定义
    infra/<capability>/          # 基础设施 single/cluster 片段
    runtime/{services,edge,...}/ # 容器化业务运行时片段
    overlays/                    # host access、observability 可选层
  images/{backend,frontend,garage-init}/
  database/{business,mysql,nacos,xxl-job}/
  config/{nacos,nginx,garage}/
  observability/
  scripts/
  tests/{contracts,smoke}/
```

`stacks/*/compose.yml` 是拓扑清单，只负责组合 `compose/` 中的能力片段；`deployment.sh` 是唯一支持的
操作入口。数据库事实源放在 `database/`，容器运行配置放在 `config/`，镜像构建输入放在 `images/`，
避免按技术名词平铺在 `deploy/` 根目录。测试目录的分组和执行方式见 [tests/README.md](tests/README.md)。

## 快速开始

### infra Stack

1. 准备环境文件：
   `cp deploy/stacks/infra/.env.example deploy/stacks/infra/.env`
2. 启动基础设施：
   `./deploy/deployment.sh up --stack infra`
3. 生成宿主机后端配置：
   `./deploy/deployment.sh render-backend-env --stack infra`

infra 使用 MySQL `23306`、Redis `26379`、Kafka `39092`、Elasticsearch `29200`、Nacos
HTTP/gRPC `28848/29848`、Garage `23900/23903`、MailHog `21025/28025` 和 XXL-JOB `22887`，
全部绑定到 `127.0.0.1`。生成的后端 env 和启动顺序见[本地开发手册](../docs/handbook/local-development.md#宿主机启动后端)。
infra 不启动前端或容器化后端；本地 Gateway 默认监听 `12880`。

### single Stack

1. 准备环境文件：
   `cp deploy/stacks/single/.env.example deploy/stacks/single/.env`
2. 启动：
   `./deploy/deployment.sh up --stack single`

single 默认入口：

- 前端：`http://localhost:12881`
- 统一入口：`http://localhost:12880`
- IM session bootstrap：`POST http://localhost:12880/api/im/sessions`
- IM WebSocket：session `wsUrl` 默认 `ws://localhost:12880/ws/im`
- Nacos 3.1.2：`http://localhost:18848/nacos`，作为服务注册中心和非密钥配置中心。
- XXL-JOB：`http://localhost:12887/xxl-job-admin`
- MailHog：`http://localhost:8025`

### cluster Stack

1. 准备环境文件：
   `cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env`
2. 启动：
   `./deploy/deployment.sh up --stack cluster`

cluster 默认使用独立的 `13880` / `13881` / `38848` 等控制面端口，与 single 和 infra 并存。

## Runtime 镜像约束

`images/frontend/Dockerfile` 使用 Node 构建静态资源，最终镜像只保留非 root Nginx，不运行
Vite preview 或携带前端源码依赖。single 和 cluster 拓扑均把前端根文件系统设为只读、
丢弃 Linux capabilities，并只挂载受限的 `/tmp`。Nginx 的 pid、临时文件和启动时生成的
`/app-config.js` 都位于该 tmpfs；版本化 `/assets/` 使用 immutable 缓存，HTML 和运行时配置不缓存。

运行时端点按以下环境变量注入，值会经过 JSON 编码而不是直接拼接到 JavaScript：

- `FRONTEND_RUNTIME_API_BASE_URL`：主站 API base URL。
- `FRONTEND_RUNTIME_IM_HTTP_BASE_URL`：IM HTTP base URL。
- `GATEWAY_PUBLIC_BASE_URL`：上述变量未设置时的共同回退值。

显式设置 `FRONTEND_RUNTIME_API_BASE_URL=` 或 `FRONTEND_RUNTIME_IM_HTTP_BASE_URL=` 可选择
同源相对路径。镜像中的 Nginx 仍代理 `/api/`、`/files/` 和 `/ws/im` 到 Compose ingress，
以兼容同源部署和后端返回的相对公开文件 URL。`images/backend/Dockerfile` 的运行阶段使用
固定 UID/GID `10001`，镜像内 JAR、agent 和启动脚本仅需只读访问；JVM 的 `user.home` 与
`java.io.tmpdir` 固定落在 `/tmp/community-runtime`，只读根文件系统部署时应为 `/tmp`
提供可写的临时挂载。

对应静态契约可独立运行：`./deploy/tests/contracts/images/production_image_contract.sh`。

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

`nacos-config-bootstrap` 会把 `deploy/config/nacos/*.yaml` 发布到 Nacos group
`COMMUNITY`。启动时它接收 `BROWSER_ALLOWED_ORIGINS`、`FRONTEND_PUBLIC_ORIGIN`、
`GATEWAY_PUBLIC_BASE_URL`、`OSS_PUBLIC_BASE_URL` 和 `IM_GATEWAY_PUBLIC_WS_URL`，将它们
渲染到 Gateway、community-app、community-oss、frontend runtime 和 IM 的浏览器
CORS/OriginGuard/公开端点 seed 后再上传；
原始 seed 文件保持只读模板。这样动态端口和本地默认端口走同一条配置链，runtime service
不需要接收 owner-specific CORS 环境变量。这些 seed 文件不得包含密码、token、access
key、JWT HMAC secret 或其他密钥。

Nacos 3.1.2 使用 `/nacos/v3/admin/core/state/readiness` 作为 readiness 接口，Compose
健康检查会同时验证响应中的 `code=0` 和 9848 gRPC 端口。`nacos-db-bootstrap` 会在
新数据库导入 3.1.2 基线；检测到已有 Nacos 2.3.2 数据库时，会保留历史数据并幂等补齐
`config_info_gray` 及 `his_config_info` 的灰度字段。

## 停止与清理

- 停止：`./deploy/deployment.sh down --stack single`
- 只重置 MySQL：`./deploy/deployment.sh reset-mysql --stack single`
- 完全重置：`./deploy/deployment.sh down --stack cluster -- -v`

`reset-mysql` 会先停止目标 Stack，只删除明确命名的 MySQL volumes，并保留 Redis、Kafka、Garage、
Elasticsearch 等数据。infra / single 删除 primary volume；cluster 删除 primary 和两个 replica volumes。此操作不可恢复。

`-v` 是传给 `docker compose down` 的参数，要放在 `--` 后面，会删除该 Stack 的所有 Compose volumes。
三个默认 project name 是 `community-infra` / `community-single` / `community-cluster`，volume namespace 是
`community_infra` / `community_single` / `community_cluster`。

三个业务 schema 固定为 `community`、`community_oss`、`im_core`，空库最终结构统一维护在
`database/business/current-state/010_current_schema.sql`。MySQL entrypoint 只在 primary volume 为空时执行该文件。
`community` 改表还必须追加 `database/business/migrations/VNNN__*.sql`；`community-db-migrations` 使用专用
DDL 账号在 app 前一次性执行，已有 volume 不得重放快照。可丢弃环境可以 reset；保留数据时先备份并按
[operations runbook](../docs/handbook/operations.md#community-前向-schema-迁移) 静默写入、向前升级。
development 身份数据位于 `database/business/seed/090_seed_identity.sql`，不属于当前态 schema。

如需再启动同类 Stack，复制对应 `.env.example`，并同时修改 project name、volume namespace、网段、静态地址和
全部宿主机端口；入口脚本会拒绝复用默认隔离值的自定义 project。

如果你启动时带了 `--no-observability`，停止时也请带上相同参数组合。

## 观测层

两套拓扑默认都会启用 observability。普通启动会加载 `deploy/compose/overlays/observability.yml`，并默认开启后端 OTel tracing：

- `./deploy/deployment.sh up --stack single`
- `./deploy/deployment.sh up --stack cluster`

需要关闭整个观测 overlay 时使用：

```bash
./deploy/deployment.sh up --stack single --no-observability
```

如需保留观测 overlay 但临时关闭 tracing，在命令前显式设置：

```bash
OTEL_ENABLED=false ./deploy/deployment.sh up --stack single
```

默认端口：

- Elasticsearch：`http://localhost:12888`
- Kibana：`http://localhost:12889`

### Observability Smoke

After the stack is up, verify that logs and traces are queryable:

```bash
./deploy/tests/smoke/observability_smoke.sh
```

To require specific event categories during a focused scenario run:

```bash
OBSERVABILITY_EXPECT_EVENT_CATEGORIES=runtime,database,messaging ./deploy/tests/smoke/observability_smoke.sh
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
OBSERVABILITY_EXPECT_DIAGNOSTICS=true ./deploy/tests/smoke/observability_smoke.sh
```

日志路径是 backend JSON stdout / OTLP logs -> EDOT collector logs pipeline -> Elasticsearch / Kibana。更多说明见 `docs/handbook/operations.md`。

### Optional YierLoom Agent

Backend images include YierLoom at `/otel/yierloom-agent.jar`. It is disabled by default and is intended for short, focused troubleshooting sessions. Enable it with a narrow method include:

```bash
YIERLOOM_ENABLED=true \
YIERLOOM_PLUGIN__METHOD__INCLUDES='com.nowcoder.community.*' \
./deploy/deployment.sh up --stack single
```

The built-in `method`, `exception`, `thread`, and `jvm` plugins are enabled when the Agent starts. YierLoom emits `event.category=yierloom` logs with `diagnostic.plugin.id` through the same observability path as other backend logs. Its event queue is bounded; `YIERLOOM_EVENTS_QUEUE_CAPACITY` defaults to `8192`, and a full queue drops new observations or events instead of blocking instrumented application work.

Dependency plugins are opt-in. Enable only the plugin needed for the capture; for example, the HTTP plugin and a two-second slow threshold use duration syntax such as `2s`:

```bash
YIERLOOM_ENABLED=true \
YIERLOOM_PLUGIN__HTTP__ENABLED=true \
YIERLOOM_PLUGIN__HTTP__SLOW_THRESHOLD=2s \
./deploy/deployment.sh up --stack single
```

The equivalent switches are `YIERLOOM_PLUGIN__JDBC__ENABLED=true`, `YIERLOOM_PLUGIN__REDIS__ENABLED=true`, and `YIERLOOM_PLUGIN__KAFKA__ENABLED=true`. Sample rates and per-second limits follow the same plugin-scoped mapping, for example `YIERLOOM_PLUGIN__HTTP__SAMPLE_RATE` and `YIERLOOM_PLUGIN__HTTP__MAX_EVENTS_PER_SECOND`. Kafka topic names are hashed by default; disclose raw names only by explicitly setting `YIERLOOM_PLUGIN__KAFKA__TOPIC_NAMES_ENABLED=true`.

YierLoom must not collect method arguments, return values, request or response bodies, SQL bind values, Redis keys or values, Kafka payloads, credentials, cookies, or headers. Disable it immediately after the capture window and restart the target services:

```bash
YIERLOOM_ENABLED=false ./deploy/deployment.sh up --stack single
```

#### Trusted External Plugins

External plugins are trusted code and must be reviewed before deployment. Build each plugin as one fat JAR with exactly one `YierLoomPlugin` ServiceLoader provider and its private dependencies. Do not bundle YierLoom API, YierLoom SDK, or Byte Buddy classes in that JAR.

Before installation, call the `yierloom-plugin-testkit` Java API `PluginContractVerifier.verifyOrThrow(Path)` against the finished artifact:

```java
PluginContractVerifier.verifyOrThrow(Path.of("/path/to/plugin.jar"));
```

`PluginContractVerifier` has no CLI. After verification, mount or copy the fat JAR into `/opt/yierloom/plugins`, or point `YIERLOOM_PLUGINS_DIR` at another plugin directory, then restart the target JVM. YierLoom does not support hot reload or runtime attach; changing, adding, or removing a plugin always requires a JVM restart.
