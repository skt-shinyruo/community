# 可观测性（Elastic / Kibana）

本仓库当前本地 compose 只保留一条观测路径：

- `deploy/compose.observability.yml`
  - 日志：backend structured JSON file appender -> shared `observability_logs` volume -> EDOT collector filelog -> Elastic
  - traces / metrics：OTLP -> EDOT collector gateway -> Elastic
  - UI：Kibana

> 注意：本地 compose 默认仍是 `OTEL_ENABLED=false`。因此只启动 observability overlay 时，logs 链路会工作；应用 traces / metrics 只有在你显式把 `OTEL_ENABLED=true` 打开后才会流入。

> 本地观测组件默认不随业务栈启动；需要时使用：
> - `./deploy/deployment.sh up --observability`

---

## 1. 启动方式

### 1.1 最小启动路径
```bash
./deploy/deployment.sh up --observability
```

这会在基础业务栈之上追加：
- Elasticsearch localhost 入口：`http://localhost:12888`
- Kibana：`http://localhost:12889`
- EDOT collector

### 1.2 显式 layered compose 等价命令
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
  -f deploy/compose.observability.yml \
  up -d --build
```

---

## 2. 数据流与查询面

### 2.1 logs
当前 logs 固定链路：

- backend structured JSON file appender
- shared `observability_logs` volume
- EDOT collector filelog receiver
- Elastic
- Kibana

collector 会解析 JSON log payload，并把这些字段提升为 `logs-*` 里的可检索字段：
- `service.name`
- `service.version`
- `trace.id`
- `span.id`（存在时）
- `community.category`
- `community.action`
- `community.outcome`

### 2.2 traces / metrics
- 只有在 `OTEL_ENABLED=true` 且应用 OTLP signals 实际流入 Elastic 时才有意义
- 当前 runtime wiring 和 Java agent 支持已经在仓库中接好；默认关闭只是为了让本地 compose 保持 opt-in

### 2.3 base compose 行为
- base compose：`SPRING_PROFILES_ACTIVE=dev,volume-log-export`
  - stdout 仍是 text logs
  - 共享 volume 里同时写结构化 JSON 日志

---

## 3. Kibana 资产

仓库内维护的 Kibana 资产位于：
- `deploy/observability/kibana/`

导入方式见：
- `deploy/observability/kibana/README.md`

当前仓库内维护的主要资产：
- `Community Observability Logs (Structured, Phase 1)` data view：`logs-*`
- `Community Observability Traces` data view：`traces-*`
- `Community Observability: Trace By Service`
- `Community Observability: Auth Security Events`
- `Community Observability: Async Retry Dead Events`
- `Community Observability: Service Health Overview`

---

## 4. 推荐查询入口

当前 observability 路径下，推荐直接使用这些字段查询：

- `trace.id : "<32-hex-trace-id>"`
- `service.name : "community-gateway" and community.category : access`
- `service.name : "community-app" and community.category : audit and community.action : http_write_request`
- `community.category : security`
- `community.category : exception`
- `community.category : async and community.outcome : (retry or dead)`
- `community.action : search_reindex`

对 `community.job_id`、`community.event_id`、`community.topic`、`community.source_topic`、`community.dlq_topic`、`community.retry_count`、`community.error_class` 这类当前尚未提升为顶层字段的业务键，继续按 raw token / body 搜索：

- `"community.job_id=<job-id>"`
- `"community.event_id=<event-id>"`
- `"community.topic=<topic>"`
- `"community.source_topic=<topic>"`
- `"community.dlq_topic=<topic>.dlq"`
- `"community.retry_count=<n>"`
- `"community.error_class=<class>"`

---

## 5. 端口与暴露策略

默认不开放宿主机端口的原因：
- 避免与本地已有服务冲突（尤其是 Redis/MySQL/Kafka/ES 常见端口）
- 避免误把依赖暴露给宿主机/局域网，降低安全与误操作风险

当你确实需要浏览器访问本地观测组件时，再开启：
- `./deploy/deployment.sh up --observability`

默认端口：
- Elasticsearch localhost 入口：`http://localhost:12888`
- Kibana：`http://localhost:12889`

---

## 6. 使用边界

- 本地观测当前只保留 Elastic / Kibana 路径，不再提供 Grafana / Loki / Prometheus / Alertmanager overlay
- logs 侧 saved searches 以 compose 路径下共享 volume 里的 fielded JSON logs 为前提
- `Trace By Service` 只在 `OTEL_ENABLED=true` 且 traces 实际流入后有意义；如果 traces 没有流入，仍可在 `logs-*` 里通过 `trace.id` 等字段做日志侧排障
- Kibana 里的视图和搜索是排障入口，不等同于“完整告警平台”
- 如果你的本地 space 已有自定义 data view 命名，导入后可以在 Kibana 中复制并调整查询，而不需要修改仓库文件
