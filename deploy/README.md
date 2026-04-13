# deploy/

本目录存放 docker compose 与构建/初始化/观测配置，用于本地/演练环境一键启动全栈。当前默认拓扑已经升级为“本地 HA 演练栈”：浏览器统一走 `NGINX` 入口，后面挂 `community-gateway` / `community-app` / `im-core` / `im-realtime` 多副本，以及 MySQL / Redis / Kafka KRaft / Elasticsearch 的原生多节点形态。业务服务之间的静态 URL pool 已经移除，改由 `Nacos x3` 集群提供服务发现；`mock-data-studio` 仍是 dev-only 控制面，端口仅绑定到宿主机 `127.0.0.1`。

> 约定：本文档中的命令默认从**仓库根目录**执行。

默认 compose project name 固定为 `community`（避免在 Docker Desktop 里显示为 `deploy` 造成歧义）；如需覆盖可使用 `./deploy/deployment.sh up -p <name>`。

## 文件/目录说明
- `compose.yml`：基础元数据与跨层公共定义，作为所有 operator 命令的第一层。
- `compose.infra.mysql.yml`：MySQL 主从与 replication bootstrap。
- `compose.infra.redis.yml`：Redis Cluster 6 节点与 cluster bootstrap。
- `compose.infra.kafka.yml`：Kafka KRaft 3 节点与 topic bootstrap。
- `compose.infra.elasticsearch.yml`：Elasticsearch 3 节点与 index bootstrap。
- `compose.infra.nacos.yml`：`Nacos x3` 集群与 `nacos-db-bootstrap`。
- `compose.infra.xxl-job.yml`：`xxl-job-admin x2` 控制面。
- `compose.infra.mailhog.yml`：dev mailbox（MailHog）。
- `compose.infra.mock-data-studio-bootstrap.yml`：`mock-data-studio-db-bootstrap` 数据准备 sidecar。
- `compose.runtime.frontend-nginx.yml`：`frontend` + `NGINX` 入口层，对外暴露 `12880` / `12881` / `12887`。
- `compose.runtime.community-app.yml`：`community-app x3`。
- `compose.runtime.community-gateway.yml`：`community-gateway x3`。
- `compose.runtime.im-core.yml`：`im-core x3`。
- `compose.runtime.im-realtime.yml`：`im-realtime x3`。
- `compose.runtime.mock-data-studio.yml`：`mock-data-studio`（默认主机端口 `12890`，仅本机）。
- `compose.observability.yml`：追加 Kibana / EDOT collector / Elasticsearch localhost 入口。
- `deployment.sh`：本地 operator 入口脚本，统一拼接基础层与可选 overlay。
- `Dockerfile.frontend`：构建并运行前端（Vite build + preview，对外 `12881`）。
- `Dockerfile.backend-service`：统一构建 Spring Boot 模块镜像（build arg：`MODULE`，取 Maven `artifactId`，例如 `community-app`）。
- `.env.example`：环境变量示例（复制为 `.env` 使用）。
- `.env`：本地环境变量（不要提交包含敏感信息的版本）。
- `mysql-init/`：MySQL 初始化脚本（建表 + 种子数据）。
- `mysql/conf/`：MySQL 主从节点的额外配置（binlog / GTID / relay log / 只读约束）。
- `scripts/`：Redis Cluster、MySQL replication、Kafka topics 的 bootstrap 脚本。
- `nginx/`：本地唯一外部入口配置；`8080` 承接业务流量，`8081` 承接 `xxl-job-admin` 控制面。
- `observability/`：EDOT Collector 配置（OTLP + shared-volume filelog -> Elastic）以及 `kibana/` 下的仓库内 saved objects。
- `backups/`：备份产物目录（如存在，建议忽略不提交）。

## 脚本入口
推荐直接使用 `./deploy/deployment.sh`，不再依赖根目录 `Makefile`。

支持的主命令：
- `./deploy/deployment.sh up`：等价于 `docker compose ... up -d --build`
- `./deploy/deployment.sh down`：停止当前组合
- `./deploy/deployment.sh ps`：查看状态
- `./deploy/deployment.sh logs`：等价于 `docker compose ... logs -f --tail=200`
- `./deploy/deployment.sh config`：渲染最终 compose 配置

支持的 overlay / 选项：
- `--observability`：追加 `deploy/compose.observability.yml`
- `--env-file <path>`：覆盖默认的 `deploy/.env`
- `-p <name>` / `--project-name <name>`：覆盖 compose project name

常见例子：
- `./deploy/deployment.sh up`
- `./deploy/deployment.sh up -p community-dev`
- `./deploy/deployment.sh logs community-gateway-1`
- `./deploy/deployment.sh down -v`
- `./deploy/deployment.sh config`

## 一键启动（推荐）
1. 准备环境变量：`cp deploy/.env.example deploy/.env`
   - 建议至少修改 `JWT_HMAC_SECRET`（>= 32 bytes）；示例默认值现在可直接启动本地 compose，但不要长期使用
   - 如果你之前跑过 ZooKeeper 版 Kafka，请先清掉旧的 `zookeeper_*` / `kafka_*` 数据卷，再启动当前 KRaft 分层栈
2. 启动：`./deploy/deployment.sh up`
   - 等价底层命令：

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
3. 访问：
   - 前端：`http://localhost:12881`
   - 统一入口（API / files / IM，由 `NGINX` 代理到 `community-gateway (Spring Cloud Gateway)` 副本池）：`http://localhost:12880`
   - Nacos 注册检查：`http://localhost:18848/nacos`
   - MailHog UI（dev mailbox）：`http://localhost:8025`（仅本机）
   - XXL-JOB Admin UI（由 `NGINX` 代理到 `xxl-job-admin` 双副本）：`http://localhost:12887/xxl-job-admin`（仅本机）
   - Mock Data Studio health：`http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/health`（仅本机）

## 本地 HA 拓扑速览
- 入口：`NGINX` 暴露 `12880`（业务）和 `12887`（XXL-JOB Admin）
- 业务服务：`community-gateway x3`、`community-app x3`、`im-core x3`、`im-realtime x3`
- 服务发现：`Nacos x3` 集群，业务默认连接 `nacos-1:8848,nacos-2:8848,nacos-3:8848`；本机检查入口仍为 `http://localhost:18848/nacos`
- 路由模型：`community-gateway` HTTP 平面使用 Spring Cloud Gateway `lb://serviceId`；`/ws/im` worker 列表来自 Nacos metadata
- 中间件：`mysql-primary + mysql-replica-1/2`、`redis-1..6`、`kafka-1..3 (KRaft combined mode)`、`elasticsearch-1..3`
- 控制面：`xxl-job-admin-1/2` 共用 `xxl_job` schema，由 `NGINX` 暴露单一入口
- 非目标：`frontend`、MailHog、`mock-data-studio`、各类 bootstrap sidecar、observability 组件仍不纳入 HA 范围
- 资源提示：这是重型本地演练拓扑，建议预留至少 `16GB` 内存和多核 CPU；首次 `--build` 与 cluster 收敛会明显慢于旧单节点 compose

> 可选：开启 observability
> - 最小启动命令：`./deploy/deployment.sh up --observability`
> - base compose 默认会让 backend services 以 `SPRING_PROFILES_ACTIVE=dev,volume-log-export` 运行：stdout 继续是 text logs，但会同时把结构化 JSON 日志写入共享 `observability_logs` volume
> - Kibana 默认 `127.0.0.1:12889`，Elasticsearch localhost 入口默认 `127.0.0.1:12888`
> - `deploy/.env.example` 默认把 `MOCK_DATA_STUDIO_HOST_PORT` 设为 `12890`，因此直接 `cp deploy/.env.example deploy/.env` 后，Elastic localhost 入口与 Kibana 不需要再改端口
> - Phase 1 的日志链路固定为：backend structured JSON file appender -> shared `observability_logs` volume -> EDOT collector filelog -> Elastic
> - collector 会解析 JSON log payload，并把 `service.name`、`service.version`、`trace.id`、`span.id`（存在时）、`community.category`、`community.action`、`community.outcome` 等字段提升到 `logs-*`
> - runtime OTLP wiring 与 Java agent 支持已经在仓库中接好；`OTEL_ENABLED` 在 `deploy/.env.example` 中默认是 `false`，如果你现在就想让应用 traces / metrics 流入 Elastic，可以直接显式打开
> - Kibana 仓库资产位于 `deploy/observability/kibana/`；导入后会得到 `Trace By Service`、`Auth Security Events`、`Async Retry Dead Events`、`Service Health Overview` 四个 Discover 视图
> - 需要注意：如果你只使用基础三层 + observability overlay，而没有再追加 JSON stdout override，backend stdout 仍然是 text logs；但 collector 仍会从共享 volume 读取结构化 JSON 文件，所以 `logs-*` 依然是 fielded logs
> - `Trace By Service` 只在 `OTEL_ENABLED=true` 且 traces 实际流入后有意义；logs 侧 fielded 排障则以 `service.name`、`trace.id`、`community.category`、`community.action`、`community.outcome` 等字段为主
>
## 端口（默认映射到宿主机）
- `NGINX` 统一业务入口：`http://localhost:12880`
- 前端（Vite preview）：`http://localhost:12881`
- Nacos 注册检查：`http://localhost:18848/nacos`（仅绑定到 `127.0.0.1`）
- MailHog UI：`http://localhost:8025`（仅绑定到 `127.0.0.1`）
- `NGINX` XXL-JOB Admin 入口：`http://localhost:12887/xxl-job-admin`（仅绑定到 `127.0.0.1`）
- Mock Data Studio：`http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/health`（仅绑定到 `127.0.0.1`）
- observability（需追加 `deploy/compose.observability.yml`，均仅绑定到 `127.0.0.1`）：
  - Elasticsearch：`http://localhost:12888`
  - Kibana：`http://localhost:12889`

## 常用 operator 命令速查（从仓库根目录执行）
- 启动基础栈：`./deploy/deployment.sh up`
- 启动 + observability：`./deploy/deployment.sh up --observability`
- 查看状态：`./deploy/deployment.sh ps`、`./deploy/deployment.sh ps --observability`
- 查看日志：`./deploy/deployment.sh logs`、`./deploy/deployment.sh logs --observability`
- 检查渲染后的 compose：`./deploy/deployment.sh config`、`./deploy/deployment.sh config --observability`
- 停止：`./deploy/deployment.sh down`、`./deploy/deployment.sh down --observability`
- 完全重置（删数据卷）：对你实际启动的层组合执行同一条 `./deploy/deployment.sh down ... -v`（⚠️ 谨慎使用）

## 观测路径
- `compose.observability.yml`：新增 Elasticsearch localhost 入口、Kibana 和 EDOT collector，复用 compose 基础栈里的 Elasticsearch，并承接当前已经接通的 `OTEL_*` / Java agent runtime wiring。
- 应用发往 collector 的 OTLP traces / metrics 已经可以通过 `OTEL_ENABLED=true` 显式开启；默认保持关闭只是为了让本地 compose 保持 opt-in。
- Kibana 仓库资产的导入说明见 `deploy/observability/kibana/README.md`；Phase 1 不在仓库里预置 Kibana alert rules，而是先提供搜索视图与 operator runbook。
- base compose / 直接本地运行默认仍是 dev text logging 到 stdout，但 compose 路径会额外把结构化 JSON 日志写入共享 volume；只要追加 observability overlay，Kibana logs 资产就能读取这些 fielded logs

## Onboarding：注册验证码/找回密码闭环（自托管友好）
- dev 默认启用 MailHog + SMTP，不回传注册验证码/`resetLink`（更贴近生产安全态）
- MailHog UI：`http://localhost:8025`（查看注册验证码/重置邮件）
- 本地 compose 默认将 `AUTH_PASSWORD_RESET_BASE_URL` 指向 `http://localhost:12881`，用于生成找回密码邮件中的前端重置链接
- 如需 dev-only 快捷模式（无 SMTP 也能跑通闭环），可在 `deploy/.env` 覆盖：
  - `AUTH_MAIL_ENABLED=false`
  - `AUTH_REGISTRATION_EXPOSE_CODE=true`
  - `AUTH_EXPOSE_RESET_LINK=true`

> 提示：prod profile 下禁止回传注册验证码/`resetLink`，并要求启用 SMTP（由启动期校验 fail-closed）。详见 `docs/SECURITY.md`。

## Mock Data Studio（dev-only shell）
- `MOCK_DATA_STUDIO_ENABLED=true` 时服务保持启动；设为 `false` 时 studio 进程直接退出。
- `MOCK_DATA_STUDIO_PORT` 始终表示 studio 进程自身的监听端口。
- `MOCK_DATA_STUDIO_HOST_PORT` 表示 compose 暴露到宿主机 `127.0.0.1` 的端口；默认值是 `12890`，只想改宿主机访问端口时，优先改这个值。
- compose 内会将 `MOCK_DATA_STUDIO_BIND_HOST` 覆盖为 `0.0.0.0`，便于容器接收流量；脱离 compose 直接运行时，默认 bind host 是 `127.0.0.1`，保持 fail-closed。
- compose 会为 studio 使用单独的 MySQL 账号：`MOCK_DATA_STUDIO_DB_USER` / `MOCK_DATA_STUDIO_DB_PASSWORD`。
  - 权限范围：
    - `community` schema：`select/insert/update/delete/create`
    - `im_core` schema：`select/insert/update/delete`
  - 原因：既要支持 startup `CREATE TABLE IF NOT EXISTS` bootstrap，也要支持 Phase 2 直接写 IM 样例表
  - fresh volume：由 `deploy/mysql-init/001_create_databases.sh` 初始化
  - existing volume：由 `mock-data-studio-db-bootstrap` sidecar 在每次 compose 启动时补齐 schema、账号和授权
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
  - `MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET`：可选；为空时回退 `JWT_HMAC_SECRET`
  - `MOCK_DATA_STUDIO_REINDEX_JWT_ISSUER`：默认 `community-auth`
  - `MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS`：默认 `120`
- `mock-data-studio` 在本地 dev 中会用短时 `ROLE_ADMIN` JWT 调用 `POST /api/ops/search/reindex`；不放开 ops 匿名访问。
- planner 使用 sidecar metadata 批次记录目标与已生成实体引用；当前已会把以下新增行写回 `demo_entity_ref`：
  - 社区：`user` / `discuss_post` / `comment` / `social_follow` / `social_like`
  - message/moderation：`message`（私信 + notices）/ `report` / `moderation_action`
  - growth/reward：`growth_check_in` / `user_task_progress` / `reward_account` / `reward_ledger` / `reward_grant_record` / `reward_item` / `reward_order`
  - IM：`im_core.im_room` / `im_room_member` / `im_room_message` / `im_conversation` / `im_private_message`
- startup auto-fill 的 `write-community` / `write-im` 当前都具备真实写入能力：默认场景会生成 seedable 的社区、治理、奖励和 IM 可见样例。
- 只有生成了 content-like 实体（当前为 `posts` / `comments`）时才会调用 `POST /api/ops/search/reindex`。
- 即便手动 job 勾选 AI 增强，若 AI 未配置、超时或 provider 异常，也只会回退规则文案，不会让整批写入失败。

## XXL-JOB（本地分布式任务控制面）
- compose 会额外启动 `xuxueli/xxl-job-admin:3.3.2` 双副本，元数据落在独立 schema `xxl_job`，并由 `NGINX` 统一暴露到 `http://localhost:12887/xxl-job-admin`。
- `community-app` 仍沿用 `XXL_JOB_EXECUTOR_APPNAME=community-app`；多副本 executor 通过各自的 `XXL_JOB_EXECUTOR_ADDRESS` 注册到同一个 admin 入口。
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
