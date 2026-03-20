# 本地部署与启动（docker compose）

> 本文档覆盖“gateway-first（12881 -> 12880）”的本地开发/联调方案，并解释前端容器如何工作、端口如何暴露、以及为什么默认不暴露 Redis/MySQL/ES/Kafka 等内部依赖端口。

> 约定：本文档中的命令默认从**仓库根目录**执行。

---

## 1. 端口规划（本地）

### 1.1 必要对外端口（默认）
- Community Gateway（统一入口）：`http://localhost:12880`
- 前端（Vue3 SPA）：`http://localhost:12881`
- 后端（community-app，回滚/诊断）：`http://localhost:12882`
- IM Realtime（internal worker，回滚/诊断）：`ws://localhost:18081/internal/ws/im`
- IM Core（回滚/诊断）：`http://localhost:18082`

### 1.2 可选对外端口（本地辅助）
- MailHog UI（dev mailbox）：`http://localhost:8025`（默认启用；仅绑定到 `127.0.0.1`）

> 观测/日志端口仅在启用 `observability` profile 时才会映射到宿主机（见下文）。

- Grafana：`http://localhost:12883`（默认 `admin/admin`）
- Loki：`http://localhost:12884`
- Prometheus：`http://localhost:12885`
- Alertmanager：`http://localhost:12886`

---

## 2. Compose 分层设计（为什么要用 profiles）

本项目采用“基础 compose + 可选 profile”的方式，目的是：
- 默认只暴露必要端口，减少端口冲突与误暴露风险；
- 通过 compose profile 按需开启观测/日志能力，保持默认启动足够简单；
- 保持命令简单、可复制粘贴。

### 2.1 文件分工
- `deploy/docker-compose.yml`（业务必需全栈）
  - 依赖：MySQL / Redis / Kafka / Elasticsearch
  - 业务：`community-gateway` + `community-app` + `frontend` + IM（`im-core` / `im-realtime`）
  - 辅助：MailHog（dev mailbox，UI `http://localhost:8025`，仅本机）
  - **过渡期暴露统一入口 `12880`，同时保留 `12881/12882/18081/18082` 便于联调与回滚；依赖端口仍不映射到宿主机（fail-closed）**
- `observability` profile（可选）
  - 观测：Prometheus / Alertmanager / Loki / Promtail / Grafana
  - 绑定到 `127.0.0.1` 暴露观测端口（`12883+`），用于浏览器访问 Grafana/Loki/Prometheus/Alertmanager

---

## 3. 一键启动（推荐）

### 3.1 准备环境变量
1. 复制示例：`cp deploy/.env.example deploy/.env`
2. 按需修改：
   - `JWT_HMAC_SECRET`：开发环境也建议改成自己的一串 >= 32 字节密钥（auth/login 签发、资源接口验签需要一致）
   - 运行自检：当前仓库未提供 `backend/scripts/doctor.sh`；如需可后续补充一个不输出敏感值的检查脚本。

### 3.2 启动（前端直连后端）
```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

访问：
- `http://localhost:12881`
- `http://localhost:12880/actuator/health`

默认流量路径：
- 浏览器 -> `frontend` (`12881`)
- 前端默认 HTTP / IM HTTP -> `http://localhost:12880`
- 前端默认 IM WebSocket -> `ws://localhost:12880/ws/im`

保留的直连入口：
- `12882`（`community-app`）
- `18082`（`im-core`）
- `18081/internal/ws/im`（`im-realtime` internal worker）

### 3.3 启动 + 额外开放观测端口
```bash
# 方式 1（推荐）：在 deploy/.env 中添加：COMPOSE_PROFILES=observability，然后执行下方命令
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build

# 方式 2（临时一次性）：不改文件，直接在命令前加环境变量
# COMPOSE_PROFILES=observability docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

> 注意：profile 只影响“本次 up 会包含哪些 services”；如果你曾经启用过 `observability`，之后去掉 profile 不会自动停止已启动的观测容器，需要手动 stop/remove 或执行 `docker compose ... down`。

---

## 4. 前端容器是怎么工作的（没有 Nginx）

前端容器由 `deploy/Dockerfile.frontend` 构建，采用两段式：
1. build 阶段：`npm ci` + `npm run build`
2. runtime 阶段：使用 `vite preview` 启动静态站点服务

关键点：
- 前端对外监听默认 `12881`（容器 `vite preview`）；本地 `vite dev` 也默认 `12881`，但可通过 env 覆盖（若变更端口，需要同步调整 origin allowlist：CORS + OriginGuard）。
- 不需要 Nginx：本地开发与联调，Vite preview 足够承担“静态站点服务”角色。

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
- 直连 `12882/18081/18082` 仅建议用于回滚、排障和链路对照。

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
