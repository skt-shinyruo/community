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
- Nacos 注册检查入口：`http://localhost:18848/nacos`（仅绑定到 `127.0.0.1`）
- `debug` profile（仅绑定到 `127.0.0.1`，回滚/排障）：
  - community-app（固定回指 `community-app-1`）：`http://localhost:12882`
  - im-realtime internal worker（固定回指 `im-realtime-1`）：`ws://localhost:18081/internal/ws/im`
  - im-core（固定回指 `im-core-1`）：`http://localhost:18082`

> 观测/日志端口通过 profile 按需映射到宿主机（见下文）。

- Grafana：`http://localhost:12883`（默认 `admin/admin`）
- Loki：`http://localhost:12884`
- Prometheus：`http://localhost:12885`
- Alertmanager：`http://localhost:12886`
- Elasticsearch localhost 入口（`observability-elastic`）：`http://localhost:12888`
- Kibana（`observability-elastic`）：`http://localhost:12889`

---

## 2. Compose 分层设计（为什么要用 profiles）

本项目采用“基础 compose + 可选 profile”的方式，目的是：
- 默认只暴露必要端口，减少端口冲突与误暴露风险；
- 通过 compose profile 按需开启观测/日志能力，保持默认启动足够简单；
- 保持命令简单、可复制粘贴。

### 2.1 文件分工
- `deploy/docker-compose.yml`（业务必需全栈）
  - 入口：`NGINX`
  - 业务：`community-gateway x3`、`community-app x3`、`frontend`、IM（`im-core x3` / `im-realtime x3`）
  - 注册发现：单节点 `Nacos`
  - 依赖：MySQL（`1 主 + 2 从`）/ Redis Cluster（`6` 节点）/ Zookeeper（`3` 节点）/ Kafka（`3` broker）/ Elasticsearch（`3` 节点）
  - 控制面：`xxl-job-admin x2`
  - 辅助：MailHog（dev mailbox，UI `http://localhost:8025`，仅本机）
  - **默认仅暴露 `NGINX` 业务入口 `12880`、前端 `12881`、本机 Nacos 检查入口 `18848` 与 `NGINX` admin 入口 `12887`；直连 `12882/18081/18082` 仅在 `debug` profile 下按需映射到 `127.0.0.1`；依赖端口仍不映射到宿主机（fail-closed）**
- `debug` profile（可选）
  - 直连排障：`community-app` / `im-core` / `im-realtime` 的 localhost-only 端口映射
- `observability` profile（可选）
  - 观测：Prometheus / Alertmanager / Loki / Promtail / Grafana
  - 绑定到 `127.0.0.1` 暴露观测端口（`12883+`），用于浏览器访问 Grafana/Loki/Prometheus/Alertmanager
- `observability-elastic` profile（可选）
  - 观测：Elasticsearch localhost 入口 / Kibana / EDOT collector
  - base compose 下 backend services 默认会把结构化 JSON 日志写入共享 `observability_logs` volume，因此只启用这个 profile 也能得到 fielded logs
  - 如果额外加载 `deploy/observability-elastic/docker-compose.override.yml`，容器 stdout 也会切到 JSON，便于 `docker compose logs` 排障

---

## 3. 一键启动（推荐）

### 3.1 准备环境变量
1. 复制示例：`cp deploy/.env.example deploy/.env`
2. 按需修改：
   - `JWT_HMAC_SECRET`：开发环境也建议改成自己的一串 >= 32 字节密钥（auth/login 签发、资源接口验签需要一致）
   - 运行自检：当前仓库未提供 `backend/scripts/doctor.sh`；如需可后续补充一个不输出敏感值的检查脚本。

### 3.2 启动（gateway-first，本地默认）
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
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
- 需启用 `debug` profile 后才会暴露到宿主机：
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
# 方式 1（推荐）：在 deploy/.env 中添加：COMPOSE_PROFILES=observability，然后执行下方命令
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build

# 方式 2（临时一次性）：不改文件，直接在命令前加环境变量
# COMPOSE_PROFILES=observability docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build

# 方式 3（排障临时开启直连端口）
# COMPOSE_PROFILES=debug docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d

# 方式 4（同时开启观测 + 调试直连）
# COMPOSE_PROFILES=observability,debug docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d

# 方式 5（开启 Elastic Observability；base compose 已能产出 fielded logs）
# COMPOSE_PROFILES=observability-elastic docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build

# 方式 6（开启 Elastic Observability，并让容器 stdout 也切到 JSON）
# docker compose -f deploy/docker-compose.yml -f deploy/observability-elastic/docker-compose.override.yml --env-file deploy/.env --profile observability-elastic up -d --build
```

> 注意：profile 只影响“本次 up 会包含哪些 services”；如果你曾经启用过 `observability` 或 `debug`，之后去掉 profile 不会自动停止已启动的对应容器，需要手动 stop/remove 或执行 `docker compose ... down`。

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
2. 本地 gateway-first 模式：当页面 origin 为 `localhost/127.0.0.1:5173|12881|12888` 时，默认推导为 `http://<host>:12880`
3. 其他情况：使用相对路径（`/api/...`），交给同域 edge / ingress

IM 专用客户端默认策略：
- IM HTTP：`http://<host>:12880`
- IM WebSocket：`ws(s)://<host>:12880/ws/im`
- 如需强制直连调试，可分别覆盖 `VITE_IM_CORE_BASE_URL` 与 `VITE_IM_WS_URL`

本地开发（HMR）场景下：
- 默认浏览器流量已经直接走 `community-gateway:12880`，不再依赖 Vite proxy 才能访问后端；
- 如需自定义目标，可显式配置 `VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL` / `VITE_IM_WS_URL`；
- 直连 `12882/18081/18082` 仅建议用于回滚、排障和链路对照，且默认不暴露；需要时通过 `debug` profile 临时开启。

---

## 6. 停止与清理

停止：
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down
```

完全重置（删除数据卷）：
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down -v
```
