# 本地部署与启动（docker compose）

> 本文档覆盖“frontend (`12881`) -> `NGINX` (`12880`) -> `community-gateway`（Spring Cloud Gateway）副本池”的本地 HA 演练方案，并解释前端容器如何工作、端口如何暴露、`Nacos` 如何承担服务注册发现、以及为什么默认不暴露 Redis/MySQL/ES/Kafka 等内部依赖端口。

> 约定：本文档中的命令默认从**仓库根目录**执行。

---

## 1. 端口规划（本地）

### 1.1 必要对外端口（默认）
- NGINX 统一业务入口：`http://localhost:12880`
- 前端（Vue3 SPA）：`http://localhost:12881`

### 1.2 可选对外端口（本地辅助）
- MailHog UI（dev mailbox）：`http://localhost:8025`（默认启用；仅绑定到 `127.0.0.1`）
- NGINX XXL-JOB Admin 入口：`http://localhost:12887/xxl-job-admin`（仅绑定到 `127.0.0.1`）
- Mock Data Studio health：`http://localhost:12890/health`（默认由 `MOCK_DATA_STUDIO_HOST_PORT` 驱动；仅绑定到 `127.0.0.1`）
- Nacos 注册检查入口：`http://localhost:18848/nacos`（仅绑定到 `127.0.0.1`）
- `debug` overlay（仅绑定到 `127.0.0.1`，回滚/排障）：
  - community-app（固定回指 `community-app-1`）：`http://localhost:12882`
  - im-realtime internal worker（固定回指 `im-realtime-1`）：`ws://localhost:18081/internal/ws/im`
  - im-core（固定回指 `im-core-1`）：`http://localhost:18082`

> 观测/日志端口通过 overlay 按需映射到宿主机（见下文）。

- Grafana：`http://localhost:12883`（默认 `admin/admin`）
- Loki：`http://localhost:12884`
- Prometheus：`http://localhost:12885`
- Alertmanager：`http://localhost:12886`
- Elasticsearch localhost 入口（Elastic overlay）：`http://localhost:12888`
- Kibana（Elastic overlay）：`http://localhost:12889`

---

## 2. Compose 分层设计（基础 compose + overlay 文件）

本项目采用“基础 compose + 可选 overlay 文件”的方式，目的是：
- 默认只暴露必要端口，减少端口冲突与误暴露风险；
- 通过按需叠加 overlay 开启观测/日志与排障能力，保持默认启动足够简单；
- 保持命令简单、可复制粘贴。

### 2.1 文件分工
- `deploy/compose.yml`
  - 基础元数据与跨层公共定义，是所有 operator 命令的第一层
- `deploy/compose.infra.mysql.yml`
  - MySQL（`1 主 + 2 从`）与 replication bootstrap
- `deploy/compose.infra.redis.yml`
  - Redis Cluster（`6` 节点）与 cluster bootstrap
- `deploy/compose.infra.kafka.yml`
  - Kafka KRaft（`3` 节点）与 topic bootstrap
- `deploy/compose.infra.elasticsearch.yml`
  - Elasticsearch（`3` 节点）与 index bootstrap
- `deploy/compose.infra.nacos.yml`
  - 注册发现：单节点 `Nacos`
- `deploy/compose.infra.xxl-job.yml`
  - 控制面：`xxl-job-admin x2`
- `deploy/compose.infra.mailhog.yml`
  - 辅助：MailHog（dev mailbox，UI `http://localhost:8025`，仅本机）
- `deploy/compose.infra.mock-data-studio-bootstrap.yml`
  - 辅助：`mock-data-studio-db-bootstrap`
- `deploy/compose.runtime.yml`
  - 入口：`NGINX`
  - 业务：`community-gateway x3`、`community-app x3`、`frontend`、IM（`im-core x3` / `im-realtime x3`）
  - **默认仅暴露 `NGINX` 业务入口 `12880`、前端 `12881`、本机 Nacos 检查入口 `18848` 与 `NGINX` admin 入口 `12887`；依赖端口仍不映射到宿主机（fail-closed）**
- `deploy/compose.debug.yml`（可选）
  - 直连排障：`community-app` / `im-core` / `im-realtime` 的 localhost-only 端口映射（`12882/18081/18082`）
- `deploy/compose.observability.yml`（可选）
  - 观测：Prometheus / Alertmanager / Loki / Promtail / Grafana
  - 绑定到 `127.0.0.1` 暴露观测端口（`12883+`），用于浏览器访问 Grafana / Loki / Prometheus / Alertmanager
- `deploy/compose.observability-elastic.yml`（可选）
  - 观测：Elasticsearch localhost 入口 / Kibana / EDOT collector
  - 基础三层下 backend services 默认会把结构化 JSON 日志写入共享 `observability_logs` volume，因此只追加这个 overlay 也能得到 fielded logs
- `deploy/compose.json-logs.override.yml`（可选）
  - 在 Elastic 观测路径上把 backend services 切到 `SPRING_PROFILES_ACTIVE=dev,json-logs,volume-log-export`
  - 作用是让容器 stdout 也切到 JSON，便于 `docker compose logs` 排障

---

## 3. 一键启动（推荐）

### 3.1 准备环境变量
1. 复制示例：`cp deploy/.env.example deploy/.env`
2. 按需修改：
   - `JWT_HMAC_SECRET`：开发环境也建议改成自己的一串 >= 32 字节密钥（auth/login 签发、资源接口验签需要一致）
   - 运行自检：当前仓库未提供 `backend/scripts/doctor.sh`；如需可后续补充一个不输出敏感值的检查脚本。

### 3.2 启动（gateway-first，本地默认）
```bash
make up
```

等价底层命令：

```bash
docker compose --env-file deploy/.env \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  up -d --build
```

访问：
- `http://localhost:12881`
- `http://localhost:12880/actuator/health`
- `http://localhost:18848/nacos`
- `http://localhost:12887/xxl-job-admin`

默认流量路径：
- 浏览器 -> `frontend` (`12881`)
- 前端默认 HTTP / IM HTTP -> `NGINX http://localhost:12880`
- 前端默认 IM WebSocket -> `NGINX ws://localhost:12880/ws/im`
- `NGINX` -> `community-gateway-1..3`
- `community-gateway` -> `lb://community-app` / `lb://im-core`（经 Nacos Discovery）
- `community-gateway` `/ws/im` -> Nacos 发现的 `im-realtime-worker`
- `NGINX :12887` -> `xxl-job-admin-1..2`

保留的直连入口：
- 需追加 `deploy/compose.debug.yml` 后才会暴露到宿主机：
  - `12882`（`community-app-1`）
  - `18082`（`im-core-1`）
  - `18081/internal/ws/im`（`im-realtime-1` internal worker）

### 3.3 本地 HA 拓扑速查
- `community-gateway`：3 副本，由 `NGINX` 统一接入
- `community-app`：3 副本，经 `community-gateway` 的 `lb://community-app` 路由访问
- `im-core`：3 副本，经 `community-gateway` 的 `lb://im-core` 路由和 `im-realtime` service-id client 访问
- `im-realtime`：3 副本，以 `im-realtime-worker` 注册到 Nacos，并由 gateway worker registry 发现
- `Nacos`：单节点服务注册中心，仅承担业务服务发现，不承担 MySQL/Redis/Kafka/ES 集群管理
- MySQL：`mysql-primary` + `mysql-replica-1/2`；当前仍是“单主写入 + 人工切主”
- Redis：`redis-1..6`，由 `redis-cluster-bootstrap` 组装 `3 主 + 3 从`
- Kafka：`kafka-1..3` + `kafka-init`
- Elasticsearch：`elasticsearch-1..3` + `es-init`
- `xxl-job-admin`：2 副本，共享 `xxl_job` schema，经 `NGINX :12887` 暴露
### 3.4 启动 + 额外开放观测端口
```bash
# 旧观测链路
make up-obs

# 直连排障端口
make up-debug

# Elastic Observability（fielded logs + Kibana）
make up-elastic

# Elastic Observability + JSON stdout
make up-elastic-json
```

需要同时叠加多个 overlay 时，直接显式追加对应的 `-f` 文件。例如“旧观测链路 + debug”：

```bash
docker compose --env-file deploy/.env \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  -f deploy/compose.observability.yml \
  -f deploy/compose.debug.yml \
  up -d --build
```

如果要同时开启两套观测链路，则在基础三层后同时追加 `-f deploy/compose.observability.yml` 与 `-f deploy/compose.observability-elastic.yml`；如需让 Elastic 那一侧 stdout 也切到 JSON，再继续追加 `-f deploy/compose.json-logs.override.yml`。

---

## 4. 前端容器与 NGINX 入口

前端容器由 `deploy/Dockerfile.frontend` 构建，采用两段式：
1. build 阶段：`npm ci` + `npm run build`
2. runtime 阶段：使用 `vite preview` 启动静态站点服务

关键点：
- 前端对外监听默认 `12881`（容器 `vite preview`）；本地 `vite dev` 也默认 `12881`，但可通过 env 覆盖（若变更端口，需要同步调整 origin allowlist：CORS + OriginGuard）。
- 前端本身仍由 Vite preview 提供静态内容；`NGINX` 不负责前端静态资源，只负责业务入口和 `xxl-job-admin` 入口。
- 本地 HA 形态下，浏览器不再直接感知 `community-gateway` 副本地址，只感知 `NGINX` 单入口。

---

## 5. 前端如何通过 Gateway 访问后端（API Base URL 策略）

前端 HTTP 客户端（Axios）有三种方式确定 API 基址：
1. 显式配置 `VITE_API_BASE_URL`（优先级最高）
2. 本地 gateway-first 模式：当页面 origin 为 `localhost/127.0.0.1:5173|12881|12890` 时，默认推导为 `http://<host>:12880`
3. 其他情况：使用相对路径（`/api/...`），交给同域 edge / ingress

IM 专用客户端默认策略：
- IM HTTP：`http://<host>:12880`
- IM WebSocket：`ws(s)://<host>:12880/ws/im`
- 如需强制直连调试，可分别覆盖 `VITE_IM_CORE_BASE_URL` 与 `VITE_IM_WS_URL`

本地开发（HMR）场景下：
- 默认浏览器流量已经直接走 `community-gateway:12880`，不再依赖 Vite proxy 才能访问后端；
- 如需自定义目标，可显式配置 `VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL` / `VITE_IM_WS_URL`；
- 直连 `12882/18081/18082` 仅建议用于回滚、排障和链路对照，且默认不暴露；需要时通过 `debug` overlay 临时开启。

---

## 6. 停止与清理

停止：
```bash
make down
```

完全重置（删除数据卷）：
```bash
docker compose --env-file deploy/.env \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  down -v
```

> 如果你启动时叠加了 `debug` / `observability` / `observability-elastic` / `json-logs` overlay，停止或重置时也请追加相同的 `make down-*` 目标或相同的 `-f deploy/compose.*.yml` 组合，避免把 overlay 服务留成 orphan。
