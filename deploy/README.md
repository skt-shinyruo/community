# deploy/

本目录存放 docker compose 与相关构建/初始化/观测配置，默认推荐“前端直连 gateway”（frontend:12881 / gateway:12882）。

默认 compose project name 固定为 `community`（避免在 Docker Desktop 里显示为 `deploy` 造成歧义）；如需覆盖可使用 `docker compose -p <name>`。

## 文件/目录说明
- `Dockerfile.spring-service`：统一构建 Spring Boot 模块镜像（build arg：`MODULE`）。
- `Dockerfile.frontend`：构建并运行前端（Vite build + preview，对外 `12881`）。
- `docker-compose.yml`：基础全栈（Nacos/MySQL/Redis/Kafka/ES/观测 + 全服务），默认仅映射必要入口端口；Nacos 控制台端口默认绑定到宿主机 `127.0.0.1:8848`（可用 `NACOS_UI_PORT` 覆盖），其余依赖端口不映射到宿主机。
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
   - 必填项：`SPRING_PROFILES_ACTIVE`（本地建议 `dev`；生产必须 `prod`）
   - 必填项：`JWT_HMAC_SECRET`（>=32 bytes）、各服务 internal token（`USER_INTERNAL_TOKEN`/`CONTENT_INTERNAL_TOKEN`/`SOCIAL_INTERNAL_TOKEN`/`SEARCH_INTERNAL_TOKEN`/`ANALYTICS_INTERNAL_TOKEN`）
   - 说明：本项目默认不再使用全局 `INTERNAL_TOKEN` 兜底，避免 token 泄露扩大爆炸半径
   - 用户体验闭环（dev 推荐）：`AUTH_EXPOSE_ACTIVATION_LINK=true`、`AUTH_EXPOSE_RESET_LINK=true`（无 SMTP 也能跑通注册/找回密码）
   - 头像自托管（dev 默认）：`USER_AVATAR_STORAGE=local` + `USER_FILES_BASE_DIR=/data/files` + `USER_PUBLIC_BASE_URL=http://localhost:12881`
2. 启动全栈（含前端与网关端口暴露）：
   - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - API（gateway）：`http://localhost:12882`

## 常用 docker compose 命令速查（从项目根目录执行）
- 启动前配置：确认 `deploy/.env` 已存在；若没有先复制：`cp deploy/.env.example deploy/.env`
- 最简单启动（仅基础全栈，不额外暴露入口端口）：
  - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
- 推荐启动（前端直连 gateway，暴露 `12881/12882`）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
- 按需叠加 overlay（示例：开启观测端口 + MailHog）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml -f deploy/docker-compose.ports.yml -f deploy/docker-compose.mailhog.yml --env-file deploy/.env up -d --build`
- 查看状态/日志：
  - `docker compose -f deploy/docker-compose.yml ps`
  - `docker compose -f deploy/docker-compose.yml logs -f --tail=200`
- 停止与清理：
  - 停止：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down`
  - 完全重置：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down -v`（⚠️ 会删除数据卷/本地数据，谨慎使用）

## Onboarding：注册/激活/找回密码闭环（自托管友好）

### dev（无 SMTP 也可用）
- 默认 `SPRING_PROFILES_ACTIVE=dev` 时：
  - 注册会回传 `activationLink`（前端注册页可直接打开激活页）
  - 找回密码会回传 `resetLink`（前端找回密码页可直接打开重置页）
- `deploy/.env.example` 已默认开启：
  - `AUTH_EXPOSE_ACTIVATION_LINK=true`
  - `AUTH_EXPOSE_RESET_LINK=true`

> 提示：dev profile 下验证码固定为 `0000`（用于冒烟/联调），对应脚本见 `scripts/smoke-i0-auth.sh`（设置 `SMOKE_ONBOARDING=true`）。

### staging/演练（更贴近生产体验）
使用 MailHog 接收邮件，并关闭 link 回传（建议）：
- 启动（追加 overlay）：  
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml -f deploy/docker-compose.mailhog.yml --env-file deploy/.env up -d --build`
- 访问 MailHog UI：`http://localhost:8025`（查看激活/重置邮件）

### prod（默认安全态，fail-closed）
- `prod` 环境默认：
  - 禁止回传 `activationLink/resetLink`
  - 必须启用 SMTP 邮件发送（否则会导致敏感链接落到日志/或用户无法完成闭环）
- `common` 的 `StartupValidation` 会在 `prod` profile 下做强校验（不满足则阻断启动）。

## 角色与运维入口

### Ops Console（仅管理员）
- 前端入口：`http://localhost:12881/#/ops`
- 需要管理员权限（ROLE_ADMIN）；执行 reindex 等高风险动作通常需要额外 `X-Ops-Token` + allowlist + enabled（break-glass）。

### 用户管理（仅管理员）
- 前端入口：`http://localhost:12881/#/admin/users`
- 支持搜索用户并授予/回收角色（USER/MODERATOR/ADMIN），要求填写 reason，且禁止自降级避免锁死管理入口。

### 无管理员时的 bootstrap
- 可用脚本直连 MySQL 修改 user.type：`scripts/bootstrap-admin.sh`
  - 示例：`./scripts/bootstrap-admin.sh --username aaa --set admin --reason "initial bootstrap"`

### 可选：使用本地 Vite dev（HMR），默认端口 12881（可改）
> 说明：默认推荐保持 `12881`，避免 `Origin` allowlist 与端口漂移导致 403；若改为其它端口，需要同步调整 gateway allowlist（CORS + OriginGuard）。
> 因此当你希望用本地 dev（HMR）时，需要避免同时启动 compose 内的 `frontend` 容器（否则会端口冲突）。

- 启动后端全栈（禁用 frontend 容器）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build --scale frontend=0`
- 启动本地前端 dev：
  - `npm -C frontend run dev`
- 访问：`http://localhost:<前端端口>`（建议统一使用 `localhost`，不要用 `127.0.0.1`，否则会触发 Origin 校验失败）

## 前端 UI / 样式与静态资源说明

### 样式入口与分层（SSOT）

前端样式使用单一入口 `frontend/src/styles/index.css`，并按职责分层导入：
- `variables.css`：Design Tokens（颜色/间距/字号/圆角/阴影等）
- `base.css`：基础 reset 与全局行为（字体、背景、focus-visible、滚动条等）
- `utils.css`：通用工具类（row/stack/truncate 等）
- `layout.css`：布局骨架（AppShell/Sidebar/Topbar/RightPanel/AuthShell + 响应式）
- `components.css`：可复用组件样式（按钮/输入/卡片/弹窗/分页等）
- `pages.css`：页面级通用结构样式（非业务逻辑，例如帖子卡片结构等）

建议：页面内联样式只用于极少量一次性场景；通用结构优先沉淀到 `pages.css` 或组件样式，避免“每个页面一套样式”导致的碎片化。

### 构建产物与缓存

- `npm -C frontend run build` 产物位于 `frontend/dist/`，默认会生成带 hash 的静态资源（`dist/assets/*`），浏览器可安全缓存。
- `npm -C frontend run preview` 用于本地模拟生产静态资源服务（端口 `12881`）。

### 文件资源（头像等）访问

头像等静态文件通常由 gateway 暴露 `/files/<fileName>`：
- edge/同源模式（前端与网关同域）：前端可直接访问相对路径 `/files/<fileName>`
- 本地“前端直连 gateway”模式（`12881 -> 12882`）：前端会基于 `VITE_API_BASE_URL` 或端口推导得到网关基址，并拼接为 `${baseURL}/files/<fileName>`

排查建议：
- 若头像显示异常：优先确认网关是否暴露 `/files/*`，以及前端页面地址是否为 `http://localhost:12881`（避免 Origin 与端口不一致）。

### 可选：开启调试端口映射
> 说明：开启后可在浏览器直接访问观测/日志组件（端口递增，避免与 `12881/12882` 冲突）。

- 启动：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml -f deploy/docker-compose.ports.yml --env-file deploy/.env up -d --build`
- Grafana：`http://localhost:12883`（默认 `admin/admin`，在 Explore 里选 Loki 即可搜日志）
- Loki：`http://localhost:12884`
- Prometheus：`http://localhost:12885`
- Alertmanager：`http://localhost:12886`

### 访问 Nacos 控制台（UI）
> 说明：Nacos 控制台端口默认仅绑定到宿主机 `127.0.0.1:8848`（仅建议本地/测试使用）；若端口冲突可在 `deploy/.env` 设置 `NACOS_UI_PORT` 覆盖。

- 启动：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
- 可以直接打开：`http://localhost:8848/nacos` 进入 Nacos UI 修改配置
- 配置示例：`deploy/nacos-config/README.md`

### 停止与清理
- 停止：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down`
- 完全重置（清数据卷）：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down -v`
