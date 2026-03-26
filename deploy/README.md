# deploy/

本目录存放 docker compose 与构建/初始化/观测配置，用于本地/演练环境一键启动全栈（推荐：gateway-first；直连排障口通过 `debug` profile 按需开启）。其中也包含 dev-only 的 `mock-data-studio` 控制面骨架服务，端口仅绑定到宿主机 `127.0.0.1`。

> 约定：本文档中的命令默认从**仓库根目录**执行。

默认 compose project name 固定为 `community`（避免在 Docker Desktop 里显示为 `deploy` 造成歧义）；如需覆盖可使用 `docker compose -p <name>`。

## 文件/目录说明
- `docker-compose.yml`：业务必需全栈（frontend + `community-gateway` + `community-app` + IM + MySQL/Redis/Kafka/ES + MailHog + `xxl-job-admin` + `mock-data-studio`），默认仅暴露统一入口（`12880/12881`）、MailHog UI（`8025`）、XXL-JOB Admin UI（`12887`，仅本机）以及 `mock-data-studio`（默认主机端口 `12888`，仅本机）；`debug` profile 才会额外映射 `12882/18081/18082` 到宿主机，内部依赖端口仍不映射（fail-closed）。
- `Dockerfile.frontend`：构建并运行前端（Vite build + preview，对外 `12881`）。
- `Dockerfile.backend-service`：统一构建 Spring Boot 模块镜像（build arg：`MODULE`，取 Maven `artifactId`，例如 `community-app`）。
- `.env.example`：环境变量示例（复制为 `.env` 使用）。
- `.env`：本地环境变量（不要提交包含敏感信息的版本）。
- `mysql-init/`：MySQL 初始化脚本（建表 + 种子数据）。
- `observability/`：Prometheus/Alertmanager/Loki/Promtail/Grafana provisioning 配置。
- `backups/`：备份产物目录（如存在，建议忽略不提交）。

## 一键启动（推荐）
1. 准备环境变量：`cp deploy/.env.example deploy/.env`
   - 建议至少修改 `JWT_HMAC_SECRET`（>= 32 bytes）；示例默认值现在可直接启动本地 compose，但不要长期使用
2. 启动：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - 统一入口（API / files / IM）：`http://localhost:12880`
   - MailHog UI（dev mailbox）：`http://localhost:8025`（仅本机）
   - XXL-JOB Admin UI：`http://localhost:12887/xxl-job-admin`（仅本机）
   - Mock Data Studio health：`http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12888}/health`（仅本机）

> 可选：开启观测/日志（Grafana/Loki/Prometheus/Alertmanager）
> - 在 `deploy/.env` 中添加：`COMPOSE_PROFILES=observability`
> - 然后执行同一条启动命令即可（端口 `12883+`，默认仅绑定到 `127.0.0.1`）
>
> 可选：开启直连排障端口（localhost only）
> - 临时一次性：`COMPOSE_PROFILES=debug docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`
> - 若需要和观测一起开启：`COMPOSE_PROFILES=observability,debug docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`

## 端口（默认映射到宿主机）
- Community Gateway（统一入口）：`http://localhost:12880`
- 前端（Vite preview）：`http://localhost:12881`
- MailHog UI：`http://localhost:8025`（仅绑定到 `127.0.0.1`）
- XXL-JOB Admin UI：`http://localhost:12887/xxl-job-admin`（仅绑定到 `127.0.0.1`）
- Mock Data Studio：`http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12888}/health`（仅绑定到 `127.0.0.1`）
- `debug` profile（仅绑定到 `127.0.0.1`，用于回滚/诊断）：
  - community-app：`http://localhost:12882`
  - IM Realtime internal worker：`ws://localhost:18081/internal/ws/im`
  - IM Core：`http://localhost:18082`
- 观测/日志（需启用 `observability` profile，均仅绑定到 `127.0.0.1`）：
  - Grafana：`http://localhost:12883`
  - Loki：`http://localhost:12884`
  - Prometheus：`http://localhost:12885`
  - Alertmanager：`http://localhost:12886`

## 常用 docker compose 命令速查（从仓库根目录执行）
- 启动：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
- 启动 + 直连排障端口：`COMPOSE_PROFILES=debug docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`
- 查看状态：`docker compose -f deploy/docker-compose.yml ps`
- 查看日志：`docker compose -f deploy/docker-compose.yml logs -f --tail=200`
- 停止：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env down`
- 完全重置（删数据卷）：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env down -v`（⚠️ 谨慎使用）

## Onboarding：注册验证码/找回密码闭环（自托管友好）
- dev 默认启用 MailHog + SMTP，不回传注册验证码/`resetLink`（更贴近生产安全态）
- MailHog UI：`http://localhost:8025`（查看注册验证码/重置邮件）
- 如需 dev-only 快捷模式（无 SMTP 也能跑通闭环），可在 `deploy/.env` 覆盖：
  - `AUTH_MAIL_ENABLED=false`
  - `AUTH_REGISTRATION_EXPOSE_CODE=true`
  - `AUTH_EXPOSE_RESET_LINK=true`

> 提示：prod profile 下禁止回传注册验证码/`resetLink`，并要求启用 SMTP（由启动期校验 fail-closed）。详见 `docs/SECURITY.md`。

## Mock Data Studio（dev-only shell）
- `MOCK_DATA_STUDIO_ENABLED=true` 时服务保持启动；设为 `false` 时 studio 进程直接退出。
- `MOCK_DATA_STUDIO_PORT` 始终表示 studio 进程自身的监听端口。
- `MOCK_DATA_STUDIO_HOST_PORT` 表示 compose 暴露到宿主机 `127.0.0.1` 的端口；只想改宿主机访问端口时，优先改这个值。
- compose 内会将 `MOCK_DATA_STUDIO_BIND_HOST` 覆盖为 `0.0.0.0`，便于容器接收流量；脱离 compose 直接运行时，默认 bind host 是 `127.0.0.1`，保持 fail-closed。
- compose 会为 studio 使用单独的 MySQL 账号：`MOCK_DATA_STUDIO_DB_USER` / `MOCK_DATA_STUDIO_DB_PASSWORD`。
  - 权限范围：
    - `community` schema：`select/insert/update/delete/create`
    - `im_core` schema：`select/insert/update/delete`
  - 原因：既要支持 startup `CREATE TABLE IF NOT EXISTS` bootstrap，也要支持 Phase 2 直接写 IM 样例表
  - fresh volume：由 `deploy/mysql-init/001_create_databases.sh` 初始化
  - existing volume：由 `mock-data-studio-db-bootstrap` sidecar 在每次 compose 启动时补齐账号和授权
- 当前阶段若需要连通 `community-app` / `im-core` / MySQL，**compose 是主支持路径**；直接在宿主机运行仅适合做本地壳层开发，除非你已经自行提供可达的 MySQL，并显式覆盖：
  - `MOCK_DATA_STUDIO_DB_URL`
  - `MOCK_DATA_STUDIO_DB_USER`
  - `MOCK_DATA_STUDIO_DB_PASSWORD`
  - `MOCK_DATA_STUDIO_COMMUNITY_APP_BASE_URL`
  - `MOCK_DATA_STUDIO_IM_CORE_BASE_URL`
- startup auto-fill 当前由以下变量控制：
  - `MOCK_DATA_AUTO_FILL_ENABLED=true`：服务启动后自动提交一次 deficit-fill job
  - `MOCK_DATA_AUTO_FILL_SCENE`：默认 `tech-community-hot-start`；可选 `moderation-pressure`、`im-busy`、`reward-ops-busy`
  - `MOCK_DATA_DEFAULT_USERS` / `MOCK_DATA_DEFAULT_POSTS` / `MOCK_DATA_DEFAULT_COMMENTS`：默认场景的目标规模
- 可选 AI 文本增强（仅手动 job 生效；startup auto-fill 永不启用）：
  - `MOCK_DATA_STUDIO_AI_ENABLED`：默认 `false`
  - `MOCK_DATA_STUDIO_OPENAI_API_KEY`（为空时会回退读取 `OPENAI_API_KEY`）
  - `MOCK_DATA_STUDIO_OPENAI_MODEL`：默认 `gpt-4.1-mini`
  - `MOCK_DATA_STUDIO_OPENAI_TIMEOUT_MS`：默认 `8000`
  - `MOCK_DATA_STUDIO_AI_MAX_ITEMS_PER_JOB`：默认 `20`（超过预算的文本保留规则生成文案）
- planner 使用 sidecar metadata 批次记录目标与已生成实体引用；当前已会把以下新增行写回 `demo_entity_ref`：
  - 社区：`user` / `discuss_post` / `comment` / `social_follow` / `social_like`
  - message/moderation：`message`（私信 + notices）/ `report` / `moderation_action`
  - growth/reward：`growth_check_in` / `user_task_progress` / `reward_account` / `reward_ledger` / `reward_grant_record` / `reward_item` / `reward_order`
  - IM：`im_core.im_room` / `im_room_member` / `im_room_message` / `im_conversation` / `im_private_message`
- startup auto-fill 的 `write-community` / `write-im` 当前都具备真实写入能力：默认场景会生成 seedable 的社区、治理、奖励和 IM 可见样例。
- 只有生成了 content-like 实体（当前为 `posts` / `comments`）时才会调用 `POST /api/ops/search/reindex`。
- 即便手动 job 勾选 AI 增强，若 AI 未配置、超时或 provider 异常，也只会回退规则文案，不会让整批写入失败。

## XXL-JOB（本地分布式任务控制面）
- compose 会额外启动 `xuxueli/xxl-job-admin:3.3.2`，元数据落在独立 schema `xxl_job`。
- `community-app` 作为 phase 1 唯一 executor，通过 `XXL_JOB_EXECUTOR_APPNAME=community-app` 注册。
- 本地默认会 seed 两个任务：
  - `pendingRegistrationUserCleanup`
  - `searchReindex`
- `pendingRegistrationUserCleanup` 是 CRON 任务；`searchReindex` 是手工任务（`schedule_type=NONE`）。
- 这份 compose 栈默认就是 XXL-enabled 路径，因此会对 `community-app` 直接注入 `AUTH_REGISTRATION_PENDING_USER_LOCAL_SCHEDULER_ENABLED=false`，避免 cleanup 本地 `@Scheduled` 与 XXL 双跑。
- 管理员账号与 access token 由 `deploy/.env` 驱动；请至少检查并按需修改：
  - `XXL_JOB_ADMIN_USERNAME`
  - `XXL_JOB_ADMIN_PASSWORD`
  - `XXL_JOB_ACCESS_TOKEN`

## 更多文档
- 本地启动与端口策略：`docs/DEPLOYMENT.md`
- 观测/日志使用：`docs/OBSERVABILITY.md`
- 安全边界与 fail-closed：`docs/SECURITY.md`
