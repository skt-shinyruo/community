# 本地部署与启动（docker compose）

> 本文档覆盖“前端直连 gateway（12881/12882）”的本地开发/联调方案，并解释前端容器如何工作、端口如何暴露、以及为什么默认不暴露 Redis/MySQL/Nacos/ES/Kafka 等内部依赖端口。

---

## 1. 端口规划（本地）

### 1.1 必要对外端口（默认）
- 前端（Vue3 SPA）：`http://localhost:12881`
- 网关（Spring Cloud Gateway）：`http://localhost:12882`

### 1.2 可选对外端口（仅用于观测/日志）
> 仅在开启 `deploy/docker-compose.ports.yml` 时才会映射到宿主机。

- Grafana：`http://localhost:12883`（默认 `admin/admin`）
- Loki：`http://localhost:12884`
- Prometheus：`http://localhost:12885`
- Alertmanager：`http://localhost:12886`

---

## 2. Compose 分层设计（为什么要拆成多个 yml）

本项目采用“基础 compose + 可选覆盖”的方式，目的是：
- 默认只暴露必要端口，减少端口冲突与误暴露风险；
- 通过 overlay 文件切换不同入口策略（本地直连、观测端口等），避免在一个 yml 里堆大量条件分支；
- 保持命令简单、可复制粘贴。

### 2.1 文件分工
- `deploy/docker-compose.yml`
  - 依赖：Nacos / MySQL / Redis / Kafka / Elasticsearch
  - 观测：Prometheus / Alertmanager / Loki / Promtail / Grafana
  - 业务：gateway + 各 `*-service`
  - **默认不把依赖端口映射到宿主机**
- `deploy/docker-compose.frontend-direct.yml`
  - 暴露：gateway `12882:8080`
  - 启动：前端容器（`12881:12881`）
  - 覆盖：激活链接基址（`AUTH_ACTIVATION_BASE_URL=http://localhost:12881`）
- `deploy/docker-compose.ports.yml`
  - 仅暴露观测端口（`12883+`），用于浏览器访问 Grafana/Loki/Prometheus/Alertmanager

---

## 3. 一键启动（推荐）

### 3.1 准备环境变量
1. 复制示例：`cp deploy/.env.example deploy/.env`
2. 按需修改：
   - `JWT_HMAC_SECRET`：开发环境也建议改成自己的一串 >= 32 字节密钥（auth-service 签发、gateway 验签需要一致）
   - `ANALYTICS_INTERNAL_TOKEN` / `SEARCH_INTERNAL_TOKEN`：内部接口 token（本地默认即可）

### 3.2 启动（前端直连网关）
```bash
docker compose -f deploy/docker-compose.yml \
  -f deploy/docker-compose.frontend-direct.yml \
  --env-file deploy/.env up -d --build
```

访问：
- `http://localhost:12881`
- `http://localhost:12882/api/...`

### 3.3 启动 + 额外开放观测端口
```bash
docker compose -f deploy/docker-compose.yml \
  -f deploy/docker-compose.frontend-direct.yml \
  -f deploy/docker-compose.ports.yml \
  --env-file deploy/.env up -d --build
```

---

## 4. 前端容器是怎么工作的（没有 Nginx）

前端容器由 `deploy/Dockerfile.frontend` 构建，采用两段式：
1. build 阶段：`npm ci` + `npm run build`
2. runtime 阶段：使用 `vite preview` 启动静态站点服务

关键点：
- 前端对外监听固定为 `12881`（同时用于 `vite dev` 与 `vite preview`），避免端口漂移导致 CORS/Origin 规则变复杂。
- 不需要 Nginx：本地开发与联调，Vite preview 足够承担“静态站点服务”角色。

---

## 5. 前端如何直连 gateway（API Base URL 策略）

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
docker compose -f deploy/docker-compose.yml \
  -f deploy/docker-compose.frontend-direct.yml \
  --env-file deploy/.env down
```

完全重置（删除数据卷）：
```bash
docker compose -f deploy/docker-compose.yml \
  -f deploy/docker-compose.frontend-direct.yml \
  --env-file deploy/.env down -v
```

