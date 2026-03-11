# deploy/

本目录存放 docker compose 与构建/初始化/观测配置，用于本地/演练环境一键启动全栈（推荐：前端直连后端单体）。

> 约定：本文档中的命令默认从**仓库根目录**执行。

默认 compose project name 固定为 `community`（避免在 Docker Desktop 里显示为 `deploy` 造成歧义）；如需覆盖可使用 `docker compose -p <name>`。

## 文件/目录说明
- `docker-compose.yml`：业务必需全栈（frontend + `community-app` + IM + MySQL/Redis/Kafka/ES + MailHog），默认暴露业务入口端口（`12881/12882/18081/18082`），内部依赖端口不映射到宿主机（fail-closed）。
- `Dockerfile.frontend`：构建并运行前端（Vite build + preview，对外 `12881`）。
- `Dockerfile.spring-service`：统一构建 Spring Boot 模块镜像（build arg：`MODULE`，取 Maven `artifactId`，例如 `community-bootstrap`）。
- `.env.example`：环境变量示例（复制为 `.env` 使用）。
- `.env`：本地环境变量（不要提交包含敏感信息的版本）。
- `mysql-init/`：MySQL 初始化脚本（建表 + 种子数据）。
- `observability/`：Prometheus/Alertmanager/Loki/Promtail/Grafana provisioning 配置。
- `backups/`：备份产物目录（如存在，建议忽略不提交）。

## 一键启动（推荐）
1. 准备环境变量：`cp deploy/.env.example deploy/.env`
   - 建议至少修改 `JWT_HMAC_SECRET`（>= 32 bytes），不要长期用默认值
2. 启动：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - API（backend）：`http://localhost:12882/api/...`
   - MailHog UI（dev mailbox）：`http://localhost:8025`（仅本机）

> 可选：开启观测/日志（Grafana/Loki/Prometheus/Alertmanager）
> - 在 `deploy/.env` 中添加：`COMPOSE_PROFILES=observability`
> - 然后执行同一条启动命令即可（端口 `12883+`，默认仅绑定到 `127.0.0.1`）

## 端口（默认映射到宿主机）
- 前端（Vite preview）：`http://localhost:12881`
- 后端（community-app）：`http://localhost:12882`
- IM Realtime（WebSocket）：`ws://localhost:18081/ws/im`
- IM Core（HTTP）：`http://localhost:18082`
- MailHog UI：`http://localhost:8025`（仅绑定到 `127.0.0.1`）
- 观测/日志（需启用 `observability` profile，均仅绑定到 `127.0.0.1`）：
  - Grafana：`http://localhost:12883`
  - Loki：`http://localhost:12884`
  - Prometheus：`http://localhost:12885`
  - Alertmanager：`http://localhost:12886`

## 常用 docker compose 命令速查（从仓库根目录执行）
- 启动：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
- 查看状态：`docker compose -f deploy/docker-compose.yml ps`
- 查看日志：`docker compose -f deploy/docker-compose.yml logs -f --tail=200`
- 停止：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env down`
- 完全重置（删数据卷）：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env down -v`（⚠️ 谨慎使用）

## Onboarding：注册/激活/找回密码闭环（自托管友好）
- dev 默认启用 MailHog + SMTP，不回传 `activationLink/resetLink`（更贴近生产安全态）
- MailHog UI：`http://localhost:8025`（查看激活/重置邮件）
- 如需 dev-only 快捷模式（无 SMTP 也能跑通闭环），可在 `deploy/.env` 覆盖：
  - `AUTH_MAIL_ENABLED=false`
  - `AUTH_EXPOSE_ACTIVATION_LINK=true`
  - `AUTH_EXPOSE_RESET_LINK=true`

> 提示：prod profile 下禁止回传 activationLink/resetLink，并要求启用 SMTP（由启动期校验 fail-closed）。详见 `docs/SECURITY.md`。

## 更多文档
- 本地启动与端口策略：`docs/DEPLOYMENT.md`
- 观测/日志使用：`docs/OBSERVABILITY.md`
- 安全边界与 fail-closed：`docs/SECURITY.md`
