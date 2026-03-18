# 本地部署与启动（docker compose）

> 本文档覆盖“前端直连后端单体（12881/12882）”的本地开发/联调方案，并解释前端容器如何工作、端口如何暴露、以及为什么默认不暴露 Redis/MySQL/ES/Kafka 等内部依赖端口。

> 约定：本文档中的命令默认从**仓库根目录**执行。

---

## 1. 端口规划（本地）

### 1.1 必要对外端口（默认）
- 前端（Vue3 SPA）：`http://localhost:12881`
- 后端（community-app）：`http://localhost:12882`
- IM Realtime（WebSocket）：`ws://localhost:18081/ws/im`
- IM Core（HTTP）：`http://localhost:18082`

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
  - 业务：`community-app` + `frontend` + IM（`im-core` / `im-realtime`）
  - 辅助：MailHog（dev mailbox，UI `http://localhost:8025`，仅本机）
  - **暴露业务入口端口（`12881/12882/18081/18082`），但不把依赖端口映射到宿主机（fail-closed）**
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
- `http://localhost:12882/api/...`

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

## 5. 前端如何直连后端（API Base URL 策略）

前端 HTTP 客户端（Axios）有三种方式确定 API 基址：
1. 显式配置 `VITE_API_BASE_URL`（优先级最高）
2. 本地直连模式：当页面 origin 为 `localhost/127.0.0.1:12881` 时，默认推导为 `http://<host>:12882`
3. 其他情况：使用相对路径（`/api/...`）

本地开发（HMR）场景下：
- `frontend/vite.config.js` 配置了 `server.proxy['/api']`，默认转发到 `http://localhost:12882`；
- 如需自定义转发目标可设置 `VITE_DEV_PROXY_TARGET`。

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
