# deploy/

本目录存放 docker compose 与相关构建/初始化/观测配置，默认推荐“前端直连 gateway”（frontend:12881 / gateway:12882）。

## 文件/目录说明
- `Dockerfile.spring-service`：统一构建 Spring Boot 模块镜像（build arg：`MODULE`）。
- `Dockerfile.frontend`：构建并运行前端（Vite build + preview，对外 `12881`）。
- `docker-compose.yml`：基础全栈（Nacos/MySQL/Redis/Kafka/ES/观测 + 全服务），默认不映射依赖端口到宿主机。
- `docker-compose.frontend-direct.yml`：本地入口覆盖：暴露 `12881/12882` + 启动 `frontend`，并将激活链接默认指向 `http://localhost:12881`。
- `docker-compose.ports.yml`：可选端口映射（仅暴露观测/日志入口：Grafana/Loki/Prometheus/Alertmanager；使用 `12883+` 端口段，避免与 `12881/12882` 冲突）。
- `.env.example`：环境变量示例（复制为 `.env` 使用）。
- `.env`：本地环境变量（不要提交包含敏感信息的版本）。
- `mysql-init/`：MySQL 初始化脚本（建表 + 种子数据）。
- `nacos-config/`：Nacos Config 模板（各服务 yaml + `README.md`）。
- `observability/`：Prometheus/Alertmanager/Loki/Promtail/Grafana provisioning 配置。
- `backups/`：备份说明与产物目录（见 `backups/README.md`）。

## 启动教程（推荐：前端直连 gateway）
1. 准备环境变量：`cp deploy/.env.example deploy/.env`
2. 启动全栈（含前端与网关端口暴露）：
   - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - API（gateway）：`http://localhost:12882`

### 可选：使用本地 Vite dev（HMR），仍保持端口 12881
> 说明：为避免 `Origin` 白名单与端口漂移导致 403，前端开发服务器也固定使用 `12881`。
> 因此当你希望用本地 dev（HMR）时，需要避免同时启动 compose 内的 `frontend` 容器（否则会端口冲突）。

- 启动后端全栈（禁用 frontend 容器）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build --scale frontend=0`
- 启动本地前端 dev：
  - `npm -C frontend run dev`
- 访问：`http://localhost:12881`（建议统一使用 `localhost`，不要用 `127.0.0.1`，否则会触发 Origin 校验失败）

### 可选：开启调试端口映射
> 说明：开启后可在浏览器直接访问观测/日志组件（端口递增，避免与 `12881/12882` 冲突）。

- 启动：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml -f deploy/docker-compose.ports.yml --env-file deploy/.env up -d --build`
- Grafana：`http://localhost:12883`（默认 `admin/admin`，在 Explore 里选 Loki 即可搜日志）
- Loki：`http://localhost:12884`
- Prometheus：`http://localhost:12885`
- Alertmanager：`http://localhost:12886`

### 停止与清理
- 停止：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down`
- 完全重置（清数据卷）：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down -v`
