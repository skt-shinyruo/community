# 本地开发

本文档合并 infra / single / cluster Stack、本地启动、端口、dev-only 配置和 Mock Data Studio。观测、压测、reindex、scheduler 排障见 [operations.md](operations.md)。

## 入口命令

统一使用：

```bash
./deploy/deployment.sh <command> --stack infra|single|cluster [--observability|--no-observability]
```

常用命令：

```bash
./deploy/deployment.sh up --stack infra
./deploy/deployment.sh render-backend-env --stack infra
./deploy/deployment.sh up --stack single
./deploy/deployment.sh up --stack cluster
./deploy/deployment.sh ps --stack cluster
./deploy/deployment.sh logs --stack cluster community-gateway-1
./deploy/deployment.sh config --stack single --env-file deploy/stacks/single/.env.example
./deploy/deployment.sh mock-data --stack single -- generate --seed demo
```

默认值：

- `infra`：单节点基础设施，供宿主机后端使用；默认不加载 observability
- `single`：完整单节点 Docker Stack；默认不加载 observability，可用 `--observability` 开启
- `cluster`：完整多节点 Docker Stack；默认加载 observability
- project name：`community-infra`、`community-single`、`community-cluster`

部署 CLI 只接受 `--stack` 选择正式 Stack，不再维护 topology、scope 或 host-access 的兼容参数。

## 环境文件

本地启动前建议复制：

```bash
cp deploy/stacks/infra/.env.example deploy/stacks/infra/.env
cp deploy/stacks/single/.env.example deploy/stacks/single/.env
cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env
```

每个目录内的 `.env` 只属于对应 Stack，包含本地密钥、端口、浏览器 origin、Mock Data Studio 和
observability 等配置。不要提交真实 `.env`。`deployment.sh` 不执行 env 文件，只安全读取白名单键；值按
shell 环境、Stack env、内置默认值的顺序解析。

三个 Stack 默认使用不同的 project、volume namespace、Docker network 和宿主机端口，可以并存。使用
`-p` / `--project-name` 再启动同类 Stack 时，仍必须提供独立的 volume namespace、网络与宿主机端口。

## single Stack

`single` 适合日常本地开发和功能联调：

- `mysql`
- MySQL entrypoint 业务 schema 初始化（仅空卷首次启动）
- `community-dev-seed`（仅 development，按开关执行）
- `redis`
- `kafka`
- `elasticsearch`
- `nacos`
- `community-app`
- `community-gateway`
- `community-im-gateway`
- `im-core`
- `im-realtime`
- `frontend-nginx`
- `mailhog`

单机全栈：

```bash
cp deploy/stacks/single/.env.example deploy/stacks/single/.env
./deploy/deployment.sh up --stack single
```

## infra Stack

`infra` 使用 single-node 基础设施实现，但它不是 `single` 的部分启动状态。它拥有独立的
`community-infra` project、`community_infra` 数据卷、`172.32.0.0/24` 网络和宿主机端口，仅供本地后端使用。

启动基础设施并生成本地后端 env：

```bash
cp deploy/stacks/infra/.env.example deploy/stacks/infra/.env
./deploy/deployment.sh up --stack infra
./deploy/deployment.sh render-backend-env --stack infra
```

所有依赖只绑定宿主机 `127.0.0.1`。Kafka 同时保留容器内 listener 与 HOST listener，避免宿主机客户端
收到无法解析的 `kafka:9092`。不要修改 Spring Boot 主配置或使用容器 IP。

宿主机入口如下：

| 依赖 | 地址 |
| --- | --- |
| MySQL | `127.0.0.1:23306` |
| Redis | `127.0.0.1:26379` |
| Kafka | `127.0.0.1:39092` |
| Elasticsearch | `http://127.0.0.1:29200` |
| Nacos HTTP / gRPC | `127.0.0.1:28848` / `127.0.0.1:29848` |
| Garage S3 / admin | `127.0.0.1:23900` / `127.0.0.1:23903` |
| MailHog SMTP / UI | `127.0.0.1:21025` / `http://127.0.0.1:28025` |

这些端口可以在 `deploy/stacks/infra/.env` 中覆盖。修改后重新执行 `render-backend-env` 即可，生成器会保证
Spring Boot 连接地址与 Compose 端口一致；不需要手工同步六份服务配置。

### 宿主机启动后端

`render-backend-env` 从 `deploy/stacks/infra/.env` 生成六个最小权限服务 env 到
`backend/env/generated/`。生成目录已被 `.gitignore` 排除。在 IDE 的 Spring Boot Run Configuration 中加载
对应文件；不要直接加载 Stack env，其中仍包含 Compose 容器使用的 DNS 地址。

完整后端的启动顺序如下：

| 顺序 | Main Class | 生成 env | 端口 |
| --- | --- | --- | --- |
| 1 | `CommunityAppApplication` | `community-app.env` | `18080` |
| 2 | `OssApplication` | `community-oss.env` | `18090` |
| 3 | `ImCoreApplication` | `im-core.env` | `18082` |
| 4 | `ImRealtimeApplication` | `im-realtime.env` | `18081` |
| 5 | `CommunityImGatewayApplication` | `community-im-gateway.env` | `18083` |
| 6 | `CommunityGatewayApplication` | `community-gateway.env` | `12880` |

先从 `backend/` 安装 reactor 依赖：

```bash
cd backend
mvn -q -DskipTests install
```

不使用 IDE 时，可以在独立终端加载服务自己的本地 env 后运行对应模块。例如启动 `community-app`：

```bash
cd backend
set -a
. env/generated/community-app.env
set +a
mvn -pl :community-app spring-boot:run
```

其他服务将模块参数和 env 文件名替换为表中的对应项。每个服务应使用独立终端，不能把六份 env 合并成一个
进程环境，否则会混淆端口和不必要地扩大密钥可见范围。

服务通过宿主机 Nacos 注册 `127.0.0.1`，Gateway 因而可以发现并调用本地进程。只调试主站 HTTP 时至少启动
`community-app`、`community-oss` 和 `community-gateway`；IM session、WebSocket 和历史消息需要六个服务全部启动。

启动后可检查：

```bash
curl -fsS http://127.0.0.1:18080/actuator/health
curl -fsS http://127.0.0.1:12880/actuator/health
curl -fsS http://127.0.0.1:12880/api/runtime-config
```

停止基础设施时必须保留同一组选项：

```bash
./deploy/deployment.sh down --stack infra
```

## cluster Stack

`cluster` 适合本地多副本、服务发现、worker lease、gateway 路由、IM backplane 演练：

- `mysql-primary` + `mysql-replica-1/2`
- primary 业务 schema 初始化 + GTID replica bootstrap
- `community-dev-seed`（仅 development，按开关执行）
- `redis-1..6`
- `kafka-1..3`
- `elasticsearch-1..3`
- `nacos-1..3`
- `community-app-1..3`
- `community-gateway-1..3`
- `community-im-gateway-1..3`
- `im-core-1..3`
- `im-realtime-1..3`

启动：

```bash
cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env
./deploy/deployment.sh up --stack cluster
```

查看状态：

```bash
./deploy/deployment.sh ps --stack cluster
```

查看日志：

```bash
./deploy/deployment.sh logs --stack cluster community-gateway-1
```

渲染最终 compose：

```bash
./deploy/deployment.sh config --stack cluster --env-file deploy/stacks/cluster/.env.example
```

## Observability Overlay

single 默认关闭 observability，显式开启时使用：

```bash
./deploy/deployment.sh up --stack single --observability
```

cluster 默认开启；需要显式关闭时使用：

```bash
./deploy/deployment.sh up --stack cluster --no-observability
```

infra 不支持完整 observability overlay，传入 `--observability` 会快速失败。`--no-observability` 可用于显式确认关闭状态。

如需保留观测 overlay 但临时关闭 tracing，在命令前显式设置：

```bash
OTEL_ENABLED=false ./deploy/deployment.sh up --stack single --observability
```

该 overlay 提供：

- Elasticsearch localhost 入口
- Kibana
- EDOT collector
- backend structured JSON stdout -> Docker container logs -> collector -> Elastic

详细排障和 Kibana 资产见 [operations.md](operations.md)。

## single 默认端口

| 组件 | 地址 |
| --- | --- |
| 前端 | `http://localhost:12881` |
| API / files / WS 统一入口 | `http://localhost:12880` |
| IM session bootstrap | gateway：`POST http://localhost:12880/api/im/sessions` |
| IM WebSocket | session response `wsUrl` 默认 `ws://localhost:12880/ws/im` |
| IM HTTP | `http://localhost:12880/api/im/**` |
| Nacos | `http://localhost:18848/nacos` |
| MailHog | `http://localhost:8025` |
| Elasticsearch observability 入口 | `http://localhost:12888` |
| Kibana | `http://localhost:12889` |

默认浏览器流量经 `community-gateway`。IM WebSocket 经 NGINX 到 gateway，再转到 `community-im-gateway`；`community-im-gateway` 负责 session bootstrap 和稳定 `/ws/im`，`im-realtime` 保持 internal worker，不直接暴露给浏览器工作流。除 observability 和本地控制面外，内部依赖端口不应直接暴露给浏览器工作流。

## Nacos Config And Discovery

Nacos is both the local service registry and non-secret configuration center.
`nacos-db-bootstrap` initializes the Nacos MySQL schema, then
`nacos-config-bootstrap` publishes YAML dataIds from `deploy/config/nacos`.

Local services import config with optional `nacos:` imports so IDE startup can still
fall back to packaged defaults. Production-like runs set required imports through
`NACOS_CONFIG_IMPORT_SHARED` and `NACOS_CONFIG_IMPORT_SERVICE`.

Secrets do not live in Nacos Config. Keep access JWT RSA private keys, service JWT HMAC secrets, database passwords,
object-store access keys and Nacos credentials in `.env` or a
secret manager.

## 前端 API 解析

本地前端通过 `frontend/src/config/endpointResolution.js` 解析 API 入口：

- runtime config 优先。
- 其次使用 Vite env，例如 `VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL`。
- 未显式配置时使用同源相对路径；本地 Vite dev / preview 通过 proxy 将 `/api`、`/files` 和 `/ws/im` 转发到 `http://localhost:12880`。

因此本地 Vite dev server、frontend-nginx 和 observability 页面都应继续通过 gateway 访问业务 API，而不是直接连 `community-app` 或 IM 内部实例。

## 本地构建和验证

完整测试策略见 [testing.md](testing.md)。常用命令如下。

后端从 `backend/` 执行：

```bash
cd backend
mvn test
mvn -q -DskipTests -pl :community-app -am package
```

前端从 `frontend/` 执行：

```bash
cd frontend
npm test
npm run build
```

handbook 文档变更从仓库根目录执行：

```bash
git diff --check -- docs/handbook
```

### 本地数据库结构

`deploy/database/business/001_schema.sql` 是 `community`、`community_oss`、`im_core` 的唯一业务结构文件。MySQL entrypoint 只在 primary volume 为空时执行；已有 volume 不会自动升级。三个 schema 名固定，所有 runtime 和 Mock Data Studio 账号均为 DML-only。

`infra` Stack 会完成单节点主库初始化和最小权限账号创建，因此这些步骤成功后可以从 IDE 启动业务 runtime。`single` 与 `cluster` 使用相同的初始化契约，cluster 另外完成 GTID replica bootstrap。

schema 校验与 Compose 契约：

```bash
./deploy/deployment.sh config --stack infra --env-file deploy/stacks/infra/.env.example
./deploy/deployment.sh config --stack single --env-file deploy/stacks/single/.env.example --no-observability
./deploy/deployment.sh config --stack cluster --env-file deploy/stacks/cluster/.env.example --no-observability
./deploy/tests/run-contracts.sh database compose
```

修改业务表时直接更新 `001_schema.sql` 中的最终定义，并同步适用的 H2/MyBatis fixture、契约和文档，然后执行 `reset-mysql` 重建目标 Stack。开发期不保留旧 volume 数据，也不在旧 volume 上手工重放 schema。

## Compose 文件分层

- `deploy/stacks/infra|single|cluster/compose.yml`：三个独立 Stack 的入口清单。
- `deploy/compose/base.yml`：共享顶层元数据与 volume。
- `deploy/compose/infra/<capability>/single.yml`：single 基础设施。
- `deploy/compose/infra/<capability>/cluster.yml`：cluster 基础设施。
- `deploy/compose/runtime/services/common.yml`：业务 runtime 的共享构建、资源与环境不变量。
- `deploy/compose/runtime/services/single.yml` / `deploy/compose/runtime/services/cluster.yml`：通过 Compose `extends` 补充 endpoint、依赖、实例和网络等 topology 差异。
- `deploy/compose/runtime/edge/single.yml` / `deploy/compose/runtime/edge/cluster.yml`：前端与入口。
- `deploy/compose/runtime/mock-data-studio/single.yml` / `deploy/compose/runtime/mock-data-studio/cluster.yml`：按需 Mock Data Studio CLI runner。
- `deploy/compose/overlays/observability.yml`：由 Stack 默认值或显式 CLI 参数选择的观测层。

## 停止与重置

停止：

```bash
./deploy/deployment.sh down --stack infra
./deploy/deployment.sh down --stack single
./deploy/deployment.sh down --stack cluster
```

只删除 MySQL 数据卷并保留其他中间件数据：

```bash
./deploy/deployment.sh reset-mysql --stack infra
./deploy/deployment.sh reset-mysql --stack single
./deploy/deployment.sh reset-mysql --stack cluster
```

`reset-mysql` 会先停止目标 Stack，然后删除 infra / single 的 primary volume，或 cluster 的 primary 和两个
replica volumes。此操作会永久删除目标 Stack 中的 MySQL 数据。

删除该拓扑的所有数据卷：

```bash
./deploy/deployment.sh down --stack infra -- -v
./deploy/deployment.sh down --stack single -- -v
./deploy/deployment.sh down --stack cluster -- -v
```

如果启动时显式带了 `--observability` 或 `--no-observability`，停止时也带上同一组选项。
`-v` 是透传给 `docker compose down` 的参数，要放在 `--` 后面。
默认 project name 为 `community-infra` / `community-single` / `community-cluster`，默认 volume namespace 为
`community_infra` / `community_single` / `community_cluster`。
自定义 project 必须同时覆盖 volume namespace、完整网络拓扑和该 Stack 暴露的全部宿主机端口，不能只改
其中一项。

Kafka 长时间 `health: starting` 且刚从旧拓扑切换时，优先执行带 `-v` 的 down 后重启。

## 集群演练常用检查

Nacos worker 列表：

```bash
curl -fsS "http://localhost:38848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

网关 502：

```bash
./deploy/deployment.sh ps --stack cluster
./deploy/deployment.sh logs --stack cluster community-gateway-1
./deploy/deployment.sh logs --stack cluster im-realtime-1
```

停止单个服务演练建议优先用 `deployment.sh` 或渲染后的 compose 配置。需要确认最终配置时使用
`./deploy/deployment.sh config --stack cluster --env-file deploy/stacks/cluster/.env.example`。

## Dev-only 账号和开关

本地身份种子来自独立 SQL，不属于业务 schema：

```text
deploy/database/business/seed/090_seed_identity.sql
```

example env 默认设置 `COMMUNITY_DEV_SEED_ENABLED=true`。Compose 的 `community-dev-seed` 使用 `mysql:8.0` 客户端和 DML-only community 账号执行该文件；只有 `DEPLOYMENT_ENVIRONMENT` 精确为 `development` 时才运行，生产环境即使误开 seed 开关也会 fail-closed。

默认演示账号：

- 普通用户：`aaa/aaa`
- 管理员：`admin/aaa`

这些账号只适用于本地 dev / 演示环境。共享环境、公网环境或生产环境禁止复用默认口令。

## 验证码和邮件调试

开发环境支持固定验证码：

- 配置项：`auth.captcha.fixed-code`
- 默认未在主配置或测试配置中启用，只有显式设置后才生效。
- prod 下禁止固定验证码，`AuthStartupValidator` 会 fail-closed 阻断误配。

本地默认通过 MailHog 收邮件闭环：

- MailHog UI：`http://localhost:8025`
- `AUTH_MAIL_FROM`、`AUTH_MAIL_ENABLED` 和 `AUTH_REGISTRATION_EXPOSE_CODE` 由 Nacos bootstrap 渲染。
- `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`、SMTP auth、STARTTLS/SSL 与 timeout 变量直接注入 `community-app`；example env 的默认值连接 MailHog。

如需 dev-only 快捷模式，可显式开启：

```text
AUTH_MAIL_ENABLED=false
AUTH_REGISTRATION_EXPOSE_CODE=true
```

prod 下禁止回传注册验证码，并要求启用 SMTP。启用 SMTP auth 时必须同时提供凭据与 TLS；生产密码应通过 Secret 注入，不要提交到 env 文件。

## Mock Data Studio

Mock Data Studio 是 dev-only 同步 CLI，用于生成并按批次删除演示数据。完整 Stack 启动后执行：

```bash
./deploy/deployment.sh mock-data --stack single -- generate --seed demo
./deploy/deployment.sh mock-data --stack single -- delete <batch-id>
```

scene 当前支持：

- `tech-community-hot-start`
- `moderation-pressure`
- `im-busy`

`tech-community-hot-start` 会补充社区、治理、growth task progress、IM 样例数据。新增行记录在 `demo_entity_ref`；`generate` 的 JSON 输出包含 `batchId`，供 `delete` 按依赖顺序清理。CLI 没有 HTTP 端口、UI、后台 job 或自动填充循环。
