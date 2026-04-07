# 可观测性（日志 / 指标 / 告警）

本项目当前处于观测迁移阶段，本地 compose 在同一套基础三层上同时保留两套 observability overlay：
- **`deploy/compose.observability.yml`（旧观测链路）**：
  - 日志：Promtail -> Loki -> Grafana Explore
  - 指标：Prometheus -> Grafana
  - 告警：Prometheus rules -> Alertmanager
- **`deploy/compose.observability-elastic.yml`（新观测链路）**：
  - 日志：backend structured JSON file appender -> shared `observability_logs` volume -> EDOT collector filelog -> Elastic
  - traces / metrics：OTLP -> EDOT collector gateway -> Elastic
  - UI：Kibana（可导入仓库内 saved views）

> 注意：仓库里的 runtime OTLP wiring 和 Java agent 支持已经接通，但本地 compose 默认仍是 `OTEL_ENABLED=false`。因此只启动 Elastic overlay 时，logs 链路会工作；应用 traces / metrics 只有在你显式把 `OTEL_ENABLED=true` 打开后才会流入。

> 迁移策略：旧 observability overlay 继续保留，方便 Loki/Grafana 与 Elastic/Kibana 并存对照；Phase 1 的服务健康告警仍然以 Prometheus 为权威来源。

> 观测组件默认不随业务栈启动；如需使用，请按需叠加 overlay 或直接使用 Makefile 目标：
> - `make up-obs`：旧的 Grafana / Loki / Prometheus / Alertmanager 链路（端口 `12883+`，默认仅绑定到 `127.0.0.1`）
> - `make up-elastic`：新的 Elasticsearch localhost 入口 + Kibana + EDOT collector
> - `make up-elastic-json`：在 Elastic 路径上再把 backend stdout 切到 JSON

---

## 1. 旧观测链路：Promtail -> Loki -> Grafana

### 1.1 日志采集来源
- Promtail 读取 backend services 写入共享 `observability_logs` volume 的 JSON 日志文件：
  - 配置：`deploy/observability/promtail-config.yml`
  - 路径：`/var/log/community/*.json.log`（named volume 挂载，不依赖宿主机目录）

### 1.2 如何检索日志（Grafana）
1. 启动旧观测链路：`make up-obs`
2. 打开 `http://localhost:12883`（默认 `admin/admin`）
3. 进入 Explore -> 选择数据源 `Loki`
4. 推荐从 `{job="community-filelogs"}` 开始，再用 `|=` 关键字过滤

### 1.3 推荐的“定位线索”
- **traceId**：`X-Trace-Id` / `traceparent` 规范化规则现在由共享基础设施实现。
  - Servlet 服务复用 `community-common-web`
  - WebFlux 服务复用 `community-common-webflux`
  - `community-gateway` 仍是默认浏览器入口，但 trace 规范化不再是 gateway 专属实现
- **审计日志（community-app）**：对非 `GET/OPTIONS` 的 `/api/**` 记录审计日志（跳过 `/api/auth/login`），用于定位写路径与敏感操作。

---

## 2. 新观测链路：Elastic + EDOT collector

### 2.1 启动方式
1. 启动 Elastic 观测链路：
   - `make up-elastic`
2. 如果你希望容器 stdout 也切到 JSON：
   - `make up-elastic-json`
3. 显式 layered compose 等价命令：

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
     -f deploy/compose.runtime.yml \
     -f deploy/compose.observability-elastic.yml \
     up -d --build

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
     -f deploy/compose.runtime.yml \
     -f deploy/compose.observability-elastic.yml \
     -f deploy/compose.json-logs.override.yml \
     up -d --build
   ```
4. 打开 Kibana：`http://localhost:12889`
5. 如果你现在就想把应用 traces / metrics 发到 collector，在 `deploy/.env` 中额外设置 `OTEL_ENABLED=true`；默认保持 `false` 只是不自动开启，并不是后续任务才有的能力
6. 如果你还想同时保留旧观测链路，请显式同时叠加两个 overlay：

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
     -f deploy/compose.runtime.yml \
     -f deploy/compose.observability.yml \
     -f deploy/compose.observability-elastic.yml \
     up -d --build
   ```

   如需让 Elastic 那一侧的容器 stdout 也切到 JSON，再继续追加 `-f deploy/compose.json-logs.override.yml`。

> 重要：基础三层现在默认会给 backend services 追加 `volume-log-export`，把结构化 JSON 日志写入共享 named volume；`deploy/compose.json-logs.override.yml` 只是把容器 stdout 也切到 JSON，方便 `docker compose logs` 与容器侧排障。
>
> 默认 `deploy/.env.example` 已把 `MOCK_DATA_STUDIO_HOST_PORT` 设为 `12890`，因此直接 `cp deploy/.env.example deploy/.env` 后，Elastic localhost `12888` / Kibana `12889` 可以直接使用，不会和 Mock Data Studio 冲突。

### 2.2 Phase 1 固定链路
- logs：`backend structured JSON file appender -> shared observability_logs volume -> EDOT collector filelog -> Elastic`
- traces / metrics：`OTLP -> observability-gateway-edot-collector -> Elastic`
- collector 配置文件：`deploy/observability-elastic/edot-collector.yml`
- compose 基础栈里的 `elasticsearch` 同时承担业务搜索与本地 observability backend；Elastic overlay 会额外打开 localhost 访问入口，并新增 Kibana 和 EDOT collector
- compose 基础栈里的 backend services 默认运行 `SPRING_PROFILES_ACTIVE=dev,volume-log-export`，会继续输出可读 text logs 到 stdout，同时把结构化 JSON 日志写入共享 volume
- `deploy/compose.json-logs.override.yml` 会在新路径里把它们切到 `SPRING_PROFILES_ACTIVE=dev,json-logs,volume-log-export`，让容器 stdout 也改为 JSON
- collector 只读取 backend services 共享的 JSON 文件，不再依赖 Docker daemon 私有目录，也不会再混入依赖容器的日志流
- collector 会解析 JSON log payload，并把 `service.name`、`service.version`、`trace.id`、`span.id`（存在时）、`community.category`、`community.action`、`community.outcome` 等字段提升为 `logs-*` 里的可检索字段
- 如果你只走基础三层 + Elastic overlay，而没有再追加 JSON stdout override，`logs-*` 仍然是 fielded logs；区别只是容器 stdout 继续保持 text logs。直接本地运行则继续以服务 stdout / 本地控制台日志为主

### 2.3 并存与迁移预期
- 旧观测链路不会被新链路立刻替换；迁移期间 Loki/Grafana 与 Elastic/Kibana 可以同时运行。
- Prometheus 仍然是 Phase 1 服务健康和基础告警的权威来源；Elastic 侧先承担统一接入、检索和关联分析。
- `OTEL_EXPORTER_OTLP_ENDPOINT`、`OTEL_ENABLED`、`OTEL_JAVA_AGENT_VERSION`、`SERVICE_VERSION` 已在 `deploy/.env.example` 预置；`OTEL_ENABLED` 默认保持关闭，但现在已经可以按需显式打开，把应用 traces / metrics 发送到 collector。

### 2.4 Kibana 仓库资产（Phase 1）
- 目录：`deploy/observability-elastic/kibana/`
- 导入入口：Kibana -> `Stack Management -> Saved Objects -> Import`
- 导入文件：`deploy/observability-elastic/kibana/saved-objects.ndjson`
- 资产说明见：`deploy/observability-elastic/kibana/README.md`

当前仓库内维护两类 data view：
- `Community Observability Logs (Structured, Phase 1)`：`logs-*`
- `Community Observability Traces`：`traces-*`

当前仓库内维护四个 Discover 视图：
1. `Community Observability: Trace By Service`
   - 面向 `traces-*` 里的 `service.name + trace.id`
   - 只有在 `OTEL_ENABLED=true` 且 traces 实际流入后，才适合作为排障入口
2. `Community Observability: Auth Security Events`
   - 默认查询：`community.category : security`
   - 适合查看登录、注册验证码、密码重置、origin guard、头像相关安全事件
3. `Community Observability: Async Retry Dead Events`
   - 默认查询：`community.category : async and community.outcome : (retry or dead)`
   - 适合围绕 retry / dead 失败面，再追加 `trace.id` 这类已结构化字段，或围绕 `community.event_id=<id>`、`community.job_id=<id>`、`community.topic=<topic>` 这类 raw token / body 搜索继续深挖
4. `Community Observability: Service Health Overview`
   - 默认查询：`community.outcome : (failure or degraded or dead or retry or denied) or community.category : exception`
   - 适合先看 gateway access、community-app audit、community-app exception、im-core exception 等 baseline taxonomy 覆盖到的异常/降级面
   - 这是排障视图，不替代 Prometheus 告警

### 2.5 Phase 1 查询模型（`traces-*` vs `logs-*`）
请把本地运行方式与 Kibana 中的 traces / logs 当作三个不同的查询面：

- base compose / 直接本地运行
  - 默认 compose 路径是 `SPRING_PROFILES_ACTIVE=dev,volume-log-export`
  - backend stdout 仍是 text logs，但共享 volume 里会同时写结构化 JSON 日志
  - 因此只要 Elastic overlay 已启动，`logs-*` 就已经是 fielded logs；如果该 overlay 没启动，再回到服务 stdout / 本地控制台日志排障
- `logs-*`（基础三层 + Elastic overlay，或再叠加 JSON stdout override）
  - base compose：`dev,volume-log-export`
  - JSON stdout override 路径：`dev,json-logs,volume-log-export`
  - collector 会解析 JSON log payload，并把 `service.name`、`service.version`、`trace.id`、可选 `span.id`、`community.category`、`community.action`、`community.outcome` 提升到 `logs-*`
  - 其他业务键当前仍主要留在 message / body；推荐把已结构化字段用于 KQL，把 `community.job_id=<id>` 这类业务键继续当作 raw token 搜索
- `traces-*`
  - 在 `OTEL_ENABLED=true` 且应用 spans 流入后，可直接按顶层字段查询
  - 例如：`service.name`、`trace.id`

当前 Elastic 观测路径下，logs 侧推荐直接使用这些字段查询：

- `trace.id : "<32-hex-trace-id>"`
- `service.name : "community-gateway" and community.category : access`
- `service.name : "community-app" and community.category : audit and community.action : http_write_request`
- `community.category : security`
- `community.category : exception`
- `community.category : async and community.outcome : (retry or dead)`
- `community.action : search_reindex`

对于 `community.job_id`、`community.event_id`、`community.topic`、`community.source_topic`、`community.dlq_topic`、`community.retry_count`、`community.error_class` 这类当前尚未提升为顶层字段的业务键，请继续围绕原始 body token 搜索，例如：

- `"community.job_id=<job-id>"`
- `"community.event_id=<event-id>"`
- `"community.topic=<topic>"`
- `"community.source_topic=<topic>"`
- `"community.dlq_topic=<topic>.dlq"`
- `"community.retry_count=<n>"`
- `"community.error_class=<class>"`

也就是说：
- `trace.id` 仍然是标准观测主键；在 Elastic 观测路径下，它可以直接作为 `logs-*` 与 `traces-*` 的字段查询入口
- `community.category / community.action / community.outcome` 是当前日志 taxonomy 的稳定语义主键；gateway access、community-app audit、community-app exception、im-core exception 都已经按这个模型发日志
- `community.job_id`、`community.event_id`、`community.topic`、`community.source_topic`、`community.dlq_topic`、`community.retry_count`、`community.error_class` 这类业务键当前主要仍留在 message / body 中，应按原始 token 搜索
- 如果你没有启动 Elastic overlay，而是保持 base compose / 直接本地运行，请不要把这些字段型查询外推到纯控制台 text logs 路径

---

## 3. 指标：Prometheus -> Grafana

### 3.1 指标抓取
- Prometheus 抓取 `community-app` 的 `/actuator/prometheus`：
  - 配置：`deploy/observability/prometheus.yml`
  - 目标：`community-app:8080`

### 3.2 常见用法
你可以在 Grafana Explore 里选择 Prometheus：
- 查看服务存活：`up{job="community-app"}`
- 结合时间窗口排查：先看 `up`，再看对应服务日志

### 3.3 Kafka DLQ 指标（IM）
当 IM 消费端出现不可恢复异常时，消息会被投递到 DLQ（`<topic>.dlq`，见 `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaConfig.java`）。

当前仓库说明：
- `community-app` 的投影/通知链路使用本地 DB outbox（不依赖 Spring Kafka）。
- IM 当前未实现统一的 DLQ publish 指标（如需要可在后续补充，例如实现 `kafka_dlq_published_total{original_topic="...", error_type="..."}`）。

如果后续补齐该指标，可用查询：
- 近 5 分钟 DLQ 新增：`sum by (job, original_topic) (increase(kafka_dlq_published_total[5m]))`

---

## 4. 告警：Prometheus rules -> Alertmanager

### 4.1 规则与触发
告警规则位于：
- `deploy/observability/alerts.yml`

当前包含两类：
1. 服务不可用：`up == 0` 持续一段时间
2. Kafka DLQ（可选，需要实现 `kafka_dlq_published_total` 指标）：
   - 近 5 分钟 DLQ publish 增量 > 0（提示需要排查与评估是否回放）

### 4.2 Alertmanager
Alertmanager 配置位于：
- `deploy/observability/alertmanager.yml`

### 4.3 Phase 1 告警归属
当前仓库在 Phase 1 明确保持以下边界：

- **仍由 Prometheus + Alertmanager 负责**
  - 服务可用性 / health：`up == 0`
  - 现有基础健康与 scrape 失败类告警
  - Kafka DLQ 指标告警（前提是后续补齐 `kafka_dlq_published_total`）
- **当前在 Kibana 提供搜索/视图，不作为仓库内置告警来源**
  - `Community Observability: Auth Security Events`
  - `Community Observability: Async Retry Dead Events`
  - `Community Observability: Service Health Overview`
  - `Community Observability: Trace By Service`

换句话说：Phase 1 里，Kibana 负责上下文、关联和 operator workflow；Prometheus 仍然是服务健康和基础告警的权威来源。

### 4.4 `trace.id` 排障 Runbook
1. 如果你已经启用了 `OTEL_ENABLED=true`，并且 traces 实际流入 `traces-*`，先打开 `Community Observability: Trace By Service`
2. 在该视图里直接收窄为：`trace.id : "<32-hex-trace-id>"`
3. 如需先限定入口服务，再追加：`service.name : "community-gateway"`、`service.name : "community-app"` 等
4. 观察同一条 `trace.id` 是否跨越 `community-gateway -> community-app -> im-core / im-realtime`
5. 如果 traces 还没有流入，但你已经启动了 Elastic overlay，就改用 `Community Observability Logs (Structured, Phase 1)`，并搜索：`trace.id : "<32-hex-trace-id>"`
6. 如需进一步限定服务或类型，可继续加字段过滤：`service.name : "community-gateway"`、`community.category : access`、`community.action : gateway_http_access` 等
7. 如果日志消息里还出现 `community.event_id=` 或 `community.job_id=` 这类 token，再继续切到对应 runbook 深挖异步链路
8. 如果你没有启动 Elastic overlay，而是保持 base compose / 直接本地运行，请回到服务 stdout / 本地控制台 text logs 排障

### 4.5 `community.job_id` / `community.event_id` 排障 Runbook
1. 不要从 `Community Observability: Async Retry Dead Events` 起手查 `community.job_id`，因为它会主动过滤掉很多非 retry/dead 记录
2. 先打开 `Community Observability Logs (Structured, Phase 1)`
3. 如果你拿到的是一个任务 ID，直接搜索原始 token：`"community.job_id=<job-id>"`
   - 这能覆盖当前 `search_reindex` 的 `success` / `skipped` / `failure` 记录
4. 如果你拿到的是一个事件 ID，直接搜索原始 token：`"community.event_id=<event-id>"`
5. 从结果里重点核对这些结构化字段与 body token：
   - `community.action`
   - `community.outcome`
   - `community.topic=...`
   - `community.retry_count=...`
   - `community.error_class=...`
6. 如果你已经确认是 retry / dead 失败面，再切到 `Community Observability: Async Retry Dead Events`，或在当前查询上叠加 `community.category : async and community.outcome : (retry or dead)`
7. 如果同一条日志里已有 `trace.id`，再切到 trace runbook，用这个 trace 继续向前后文扩展
8. 如果你没有启动 Elastic overlay，而是保持 base compose / 直接本地运行，请回到服务 stdout / 本地控制台 text logs 排障

### 4.6 DLQ 回放 Runbook（演练/受控窗口）
> ⚠️ 回放会触发消费者再次执行副作用（通知/索引更新等）。强烈建议只在演练环境或受控窗口执行，并使用“限量/限速/dry-run”。

1. 先定位：
   - 对 IM Kafka DLQ 恢复日志，先在 Kibana logs data view 搜索 `community.action : kafka_dlq_recover`
   - 再追加当前仓库真实会发出的 body token：
     - `"community.source_topic=<topic>"`
     - `"community.dlq_topic=<topic>.dlq"`
     - `"community.kafka_partition=<partition>"`
     - `"community.kafka_offset=<offset>"`
   - 对 `community-app` outbox retry / dead，优先搜索：
     - `community.action : outbox_dispatch`
     - `"community.topic=<topic>"`
     - `"community.event_id=<event-id>"`
2. 再决定是否回放：
   - 如果是“短暂依赖抖动/下游短暂不可用”导致的失败，一般可回放
   - 如果是“代码 bug/数据不合法”导致的失败，应先修复再回放
3. 额外说明：
   - `original_topic` 目前是“未来若补齐 Prometheus DLQ 指标时可能出现的 metric label”，不是当前仓库 logs 查询的主键
   - 当前 logs 排障应优先围绕 `community.source_topic=`、`community.dlq_topic=`、`community.topic=` 这些真实已发出的 body token
4. 回放操作：当前仓库未提供 DLQ 回放脚本；建议使用 Kafka 客户端工具在受控窗口内回放，并限量/限速。

---

## 5. 端口与暴露策略（为什么默认不开放）

默认不开放宿主机端口的原因：
- 避免与本地已有服务冲突（尤其是 Redis/MySQL/Kafka/ES 常见端口）
- 避免误把依赖暴露给宿主机/局域网，降低安全与误操作风险

当你确实需要浏览器访问观测组件时，再开启：
- `make up-obs` 或追加 `deploy/compose.observability.yml`（映射到 `12883+`）
- `make up-elastic` / `make up-elastic-json` 或追加 `deploy/compose.observability-elastic.yml`（Elasticsearch 默认 `12888`；Kibana 默认 `12889`）
