# 本地开发

本文档合并本地启动、single / cluster 拓扑、端口、dev-only 配置和 Mock Data Studio。观测、压测、reindex、scheduler 排障见 [operations.md](operations.md)。

## 入口命令

统一使用：

```bash
./deploy/deployment.sh <command> [--topology single|cluster] [--scope full|infra] [--observability]
```

常用命令：

```bash
./deploy/deployment.sh up --topology single
./deploy/deployment.sh up --topology single --scope infra
./deploy/deployment.sh up --topology cluster
./deploy/deployment.sh ps --topology cluster
./deploy/deployment.sh logs --topology cluster community-gateway-1
./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example
```

默认值：

- `--topology cluster`
- `--scope full`
- project name：`community-single` 或 `community-cluster`

## 环境文件

本地启动前建议复制：

```bash
cp deploy/.env.single.example deploy/.env.single
cp deploy/.env.cluster.example deploy/.env.cluster
```

`.env` 文件包含本地密钥、端口、浏览器 origin、Mock Data Studio、observability 等配置。不要提交真实 `.env`。

## single 拓扑

`single` 适合日常本地开发和功能联调：

- `mysql`
- `redis`
- `kafka`
- `elasticsearch`
- `nacos`
- `xxl-job-admin`
- `community-app`
- `community-gateway`
- `community-im-gateway`
- `im-core`
- `im-realtime`
- `frontend-nginx`
- `mock-data-studio`
- `mailhog`

单机全栈：

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single
```

只启动基础设施，业务服务在 IDE 里启动：

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single --scope infra
```

## cluster 拓扑

`cluster` 适合本地多副本、服务发现、worker lease、gateway 路由、IM backplane 演练：

- `mysql-primary` + `mysql-replica-1/2`
- `redis-1..6`
- `kafka-1..3`
- `elasticsearch-1..3`
- `nacos-1..3`
- `xxl-job-admin-1/2`
- `community-app-1..3`
- `community-gateway-1..3`
- `community-im-gateway-1..3`
- `im-core-1..3`
- `im-realtime-1..3`

启动：

```bash
cp deploy/.env.cluster.example deploy/.env.cluster
./deploy/deployment.sh up --topology cluster
```

查看状态：

```bash
./deploy/deployment.sh ps --topology cluster
```

查看日志：

```bash
./deploy/deployment.sh logs --topology cluster community-gateway-1
```

渲染最终 compose：

```bash
./deploy/deployment.sh config --topology cluster --env-file deploy/.env.cluster.example
```

## Observability Overlay

single / cluster 都可以叠加 observability：

```bash
./deploy/deployment.sh up --topology single --observability
./deploy/deployment.sh up --topology cluster --observability
```

该 overlay 提供：

- Elasticsearch localhost 入口
- Kibana
- EDOT collector
- backend structured JSON logs -> shared volume -> collector -> Elastic

详细排障和 Kibana 资产见 [operations.md](operations.md)。

## 默认端口

| 组件 | 地址 |
| --- | --- |
| 前端 | `http://localhost:12881` |
| API / files / WS 统一入口 | `http://localhost:12880` |
| IM session bootstrap | gateway：`POST http://localhost:12880/api/im/sessions` |
| IM WebSocket | session response `wsUrl` 默认 `ws://localhost:12880/ws/im` |
| IM HTTP | `http://localhost:12880/api/im/**` |
| Nacos | `http://localhost:18848/nacos` |
| XXL-JOB Admin | `http://localhost:12887/xxl-job-admin` |
| MailHog | `http://localhost:8025` |
| Mock Data Studio | `http://localhost:12890/` |
| Mock Data Studio health | `http://localhost:12890/health` |
| Elasticsearch observability 入口 | `http://localhost:12888` |
| Kibana | `http://localhost:12889` |

默认浏览器流量经 `community-gateway`。IM WebSocket 经 NGINX 到 gateway，再转到 `community-im-gateway`；`community-im-gateway` 负责 session bootstrap 和稳定 `/ws/im`，`im-realtime` 保持 internal worker，不直接暴露给浏览器工作流。除 observability 和本地控制面外，内部依赖端口不应直接暴露给浏览器工作流。

## 前端 API 解析

本地前端通过 `frontend/src/config/endpointResolution.js` 解析 API 入口：

- runtime config 优先。
- 其次使用 Vite env，例如 `VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL`。
- 当页面来自 `localhost:5173`、`12881`、`12890` 或 `12888` 时，默认推断 gateway 为 `http://localhost:12880`。

因此本地 Vite dev server、frontend-nginx、Mock Data Studio 和 observability 页面都应继续通过 gateway 访问业务 API，而不是直接连 `community-app` 或 IM 内部实例。

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

## Compose 文件分层

- `deploy/compose.yml`：共享顶层元数据与 volume。
- `deploy/compose.infra.*.single.yml`：single 基础设施。
- `deploy/compose.infra.*.cluster.yml`：cluster 基础设施。
- `deploy/compose.runtime.services.single.yml` / `deploy/compose.runtime.services.cluster.yml`：业务 runtime。
- `deploy/compose.runtime.frontend-nginx.single.yml` / `deploy/compose.runtime.frontend-nginx.cluster.yml`：前端与入口。
- `deploy/compose.runtime.mock-data-studio.single.yml` / `deploy/compose.runtime.mock-data-studio.cluster.yml`：Mock Data Studio wiring。
- `deploy/compose.observability.yml`：可选观测层。

## 停止与重置

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

如果启动时带了 `--observability`，停止时也带上同一组选项。

Kafka 长时间 `health: starting` 且刚从旧拓扑切换时，优先执行带 `-v` 的 down 后重启。

## 集群演练常用检查

Nacos worker 列表：

```bash
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

网关 502：

```bash
./deploy/deployment.sh ps --topology cluster
./deploy/deployment.sh logs --topology cluster community-gateway-1
./deploy/deployment.sh logs --topology cluster im-realtime-1
```

停止单个服务演练建议优先用 `deployment.sh` 或渲染后的 compose 配置。旧文档里的完整 `docker compose -f ... stop community-gateway-1` 命令本质上等价于 cluster compose 文件列表展开，当前更推荐通过 `./deploy/deployment.sh config --topology cluster --env-file deploy/.env.cluster.example` 确认最终配置。

## Dev-only 账号和开关

本地种子数据来自：

```text
deploy/mysql/community/090_seed_identity.sql
```

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

如需 dev-only 快捷模式，可显式开启：

```text
AUTH_MAIL_ENABLED=false
AUTH_REGISTRATION_EXPOSE_CODE=true
```

prod 下禁止回传注册验证码，并要求启用 SMTP。

## Mock Data Studio

Mock Data Studio 是 dev-only 控制面，用于生成可删除的演示数据。

当前暴露：

- `GET /`
- `GET /health`
- `GET /api/runtime-status`
- `POST /api/jobs`
- `GET /api/jobs/:jobId`
- `GET /api/batches`
- `GET /api/batches/:batchId`
- `DELETE /api/batches/:batchId`

访问：

```text
http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/
http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/health
http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/api/runtime-status
http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/api/jobs
```

默认开关：

```text
MOCK_DATA_STUDIO_ENABLED=true
MOCK_DATA_STUDIO_HOST_PORT=12890
MOCK_DATA_STUDIO_PORT=12888
MOCK_DATA_AUTO_FILL_ENABLED=false
MOCK_DATA_AUTO_FILL_SCENE=tech-community-hot-start
MOCK_DATA_DEFAULT_USERS=100
MOCK_DATA_DEFAULT_POSTS=800
MOCK_DATA_DEFAULT_COMMENTS=2500
```

`MOCK_DATA_STUDIO_HOST_PORT` 是 compose 暴露到宿主机的 localhost-only 端口；`MOCK_DATA_STUDIO_PORT` 是 studio 进程监听端口。

auto-fill scene 当前支持：

- `tech-community-hot-start`
- `moderation-pressure`
- `im-busy`
- `reward-ops-busy`

`tech-community-hot-start` 会补充社区、治理、growth / reward、IM 样例数据。新增行记录在 `demo_entity_ref`，批次支持按依赖顺序删除；批次详情页展示 target / actual / failure summary。

这些开关只影响本地控制面，不改变 prod fail-closed 安全约束。
