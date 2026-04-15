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

> 观测/日志端口通过 overlay 按需映射到宿主机（见下文）。

- Elasticsearch localhost 入口（observability overlay）：`http://localhost:12888`
- Kibana（observability overlay）：`http://localhost:12889`

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
  - MySQL（`1 主 + 2 从`）与 `deploy/mysql/primary-init` + `deploy/mysql/community` bootstrap assets
- `deploy/compose.infra.redis.yml`
  - Redis Cluster（`6` 节点）与 cluster bootstrap
- `deploy/compose.infra.kafka.yml`
  - Kafka KRaft（`3` 节点）与 topic bootstrap
- `deploy/compose.infra.elasticsearch.yml`
  - Elasticsearch（`3` 节点）与 index bootstrap
- `deploy/compose.infra.nacos.yml`
  - 注册发现：`Nacos x3` 集群与 `deploy/mysql/nacos` bootstrap assets
- `deploy/compose.infra.xxl-job.yml`
  - 控制面：`xxl-job-db-bootstrap` + `xxl-job-admin x2`
- `deploy/compose.infra.mailhog.yml`
  - 辅助：MailHog（dev mailbox，UI `http://localhost:8025`，仅本机）
- `deploy/compose.infra.mock-data-studio-bootstrap.yml`
  - 辅助：`mock-data-studio-db-bootstrap`
- `deploy/compose.runtime.frontend-nginx.yml`
  - 入口：`frontend` + `NGINX`
  - **默认仅暴露 `NGINX` 业务入口 `12880`、前端 `12881`、本机 Nacos 检查入口 `18848` 与 `NGINX` admin 入口 `12887`；依赖端口仍不映射到宿主机（fail-closed）**
- `deploy/compose.runtime.community-app.yml`
  - 业务：`community-app x3`
- `deploy/compose.runtime.community-gateway.yml`
  - 业务：`community-gateway x3`
- `deploy/compose.runtime.im-core.yml`
  - 业务：`im-core x3`
- `deploy/compose.runtime.im-realtime.yml`
  - 业务：`im-realtime x3`
- `deploy/compose.runtime.mock-data-studio.yml`
  - 辅助：`mock-data-studio`
- `deploy/compose.observability.yml`（可选）
  - 观测：Elasticsearch localhost 入口 / Kibana / EDOT collector
  - 基础三层下 backend services 默认会把结构化 JSON 日志写入共享 `observability_logs` volume，因此只追加这个 overlay 也能得到 fielded logs

---

## 3. 一键启动（推荐）

### 3.1 准备环境变量
1. 复制示例：`cp deploy/.env.example deploy/.env`
2. 按需修改：
   - `JWT_HMAC_SECRET`：开发环境也建议改成自己的一串 >= 32 字节密钥（auth/login 签发、资源接口验签需要一致）
   - 运行自检：当前仓库未提供 `backend/scripts/doctor.sh`；如需可后续补充一个不输出敏感值的检查脚本。

### 3.2 启动（gateway-first，本地默认）
```bash
./deploy/deployment.sh up
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
  -f deploy/compose.runtime.community-app.yml \
  -f deploy/compose.runtime.im-core.yml \
  -f deploy/compose.runtime.im-realtime.yml \
  -f deploy/compose.runtime.community-gateway.yml \
  -f deploy/compose.runtime.frontend-nginx.yml \
  -f deploy/compose.runtime.mock-data-studio.yml \
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
- 当前不再提供宿主机直连后端实例入口；排障统一通过 `docker compose logs`、`docker compose exec` 或默认 ingress / gateway 路径完成。

### 3.3 本地 HA 拓扑速查
- `community-gateway`：3 副本，由 `NGINX` 统一接入
- `community-app`：3 副本，经 `community-gateway` 的 `lb://community-app` 路由访问
- `im-core`：3 副本，经 `community-gateway` 的 `lb://im-core` 路由和 `im-realtime` service-id client 访问
- `im-realtime`：3 副本，以 `im-realtime-worker` 注册到 Nacos，并由 gateway worker registry 发现
- `Nacos`：3 节点服务注册集群，业务容器默认连接 `nacos-1:8848,nacos-2:8848,nacos-3:8848`；`localhost:18848` 仅作为 operator 检查入口
- MySQL：`mysql-primary` + `mysql-replica-1/2`；当前仍是“单主写入 + 人工切主”
- Redis：`redis-1..6`，由 `redis-cluster-bootstrap` 组装 `3 主 + 3 从`
- Kafka：`kafka-1..3` + `kafka-init`
- Elasticsearch：`elasticsearch-1..3` + `es-init`
- `xxl-job-admin`：2 副本，共享 `xxl_job` schema，经 `NGINX :12887` 暴露
### 3.4 启动 + 额外开放观测端口
```bash
# observability（fielded logs + Kibana）
./deploy/deployment.sh up --observability

```

需要同时叠加多个 overlay 时，直接显式追加对应的 `-f` 文件；当前本地观测只保留 observability 路径。

---

## 4. 前端容器与 NGINX 入口

前端容器由 `deploy/Dockerfile.frontend` 构建，采用两段式：
1. build 阶段：`npm ci` + `npm run build`
2. runtime 阶段：使用 `vite preview` 启动静态站点服务

关键点：
- 前端对外监听默认 `12881`（容器 `vite preview`）；宿主机端口由 `FRONTEND_HOST_PORT` 控制，本地 `vite dev` 也默认 `12881`（若变更端口，需要同步调整 `FRONTEND_PUBLIC_ORIGIN` 与 `BROWSER_ALLOWED_ORIGINS`）。
- 前端本身仍由 Vite preview 提供静态内容；`NGINX` 不负责前端静态资源，只负责业务入口和 `xxl-job-admin` 入口。
- 本地 HA 形态下，浏览器不再直接感知 `community-gateway` 副本地址，只感知 `NGINX` 单入口。

---

## 5. 前端如何通过 Gateway 访问后端（API Base URL 策略）

前端 HTTP / WS 客户端现在按以下顺序决定入口：
1. 运行时配置 `app-config.js`（容器启动时根据 `GATEWAY_PUBLIC_BASE_URL` / `IM_WS_PUBLIC_URL` 生成）
2. 显式配置 `VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL` / `VITE_IM_WS_URL`
3. same-origin 回退（`/api/**`、`/files/**`、`/ws/im`），由 Vite dev/preview 代理或同域 edge / ingress 处理

IM 专用客户端默认策略：
- 容器运行时默认来自 `app-config.js`
- 本地 dev/preview 默认通过 same-origin + proxy 访问
- 如需强制直连调试，可分别覆盖 `VITE_IM_CORE_BASE_URL` 与 `VITE_IM_WS_URL`

本地开发（HMR）场景下：
- 默认浏览器流量已经直接走 `community-gateway:12880`，不再依赖 Vite proxy 才能访问后端；
- 如需自定义目标，可显式配置 `VITE_API_BASE_URL` / `VITE_IM_CORE_BASE_URL` / `VITE_IM_WS_URL`；
- 当前不再提供宿主机直连后端实例入口；如需排障，请优先通过默认 ingress / gateway 路径配合容器日志与容器内检查完成。

---

## 6. 停止与清理

停止：
```bash
./deploy/deployment.sh down
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
  -f deploy/compose.runtime.community-app.yml \
  -f deploy/compose.runtime.im-core.yml \
  -f deploy/compose.runtime.im-realtime.yml \
  -f deploy/compose.runtime.community-gateway.yml \
  -f deploy/compose.runtime.frontend-nginx.yml \
  -f deploy/compose.runtime.mock-data-studio.yml \
  down -v
```

> 如果你启动时叠加了 `observability` overlay，停止或重置时也请追加相同的 `./deploy/deployment.sh down ...` 参数组合或相同的 `-f deploy/compose.*.yml` 组合，避免把 overlay 服务留成 orphan。
