# Kibana Assets for `observability`

本目录存放仓库内维护的 Kibana Phase 1 资产，供 `./deploy/deployment.sh up --observability` 启动后手工导入。

当前资产面向 Kibana `8.12.x`，目标是给本地排障和演练环境提供一组稳定的起点。

## 包含内容

- `saved-objects.ndjson`
  - `Community Observability Logs (Structured, Phase 1)` data view：`logs-*`
  - `Community Observability Traces` data view：`traces-*`
  - `Community Observability: Trace By Service`
  - `Community Observability: Auth Security Events`
  - `Community Observability: Async Retry Dead Events`
  - `Community Observability: Service Health Overview`

当前仓库要区分三种运行路径：

- base compose / 直接本地运行
  - compose 下 backend 默认是 `SPRING_PROFILES_ACTIVE=dev,volume-log-export`
  - stdout 仍是 text logs，但共享 volume 里会额外写结构化 JSON 日志
  - 只要 observability overlay 已启动，这些 JSON 文件就属于这里这套 Kibana fielded-log ingestion path
- observability 路径
  - 最小启动路径是：`./deploy/deployment.sh up --observability`
  - logs 链路固定为：`backend structured JSON file appender -> shared observability_logs volume -> EDOT collector filelog -> Elastic`
  - collector 会解析 JSON log payload，并把 `service.name`、`service.version`、`trace.id`、`span.id`（存在时）、`community.category`、`community.action`、`community.outcome` 等字段提升到 `logs-*`
- traces / metrics
  - 仍然只有在 `OTEL_ENABLED=true` 且应用 OTLP signals 实际流入 Elastic 时才有意义
  - logs 路径不依赖 `OTEL_ENABLED=true`

## 导入步骤

1. 先启动 `observability` compose 路径：
   - 最小路径：`./deploy/deployment.sh up --observability`
   - 显式 layered compose 等价命令：

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
2. 如果你现在就想把应用 traces / metrics 发到 collector，再额外把 `OTEL_ENABLED=true` 写入 `deploy/.env`
3. 打开 Kibana：`http://localhost:12889`
4. 进入 `Stack Management -> Saved Objects`
5. 选择 `Import`
6. 导入 `deploy/observability/kibana/saved-objects.ndjson`
7. 勾选 overwrite（如果你要用仓库版本覆盖本地旧对象）

如果你只是在基础三层后追加 observability overlay，Kibana 和 collector 就已经可以启动，而且仍然能从共享 volume 里拿到 fielded logs。

默认 `deploy/.env.example` 已把 `MOCK_DATA_STUDIO_HOST_PORT` 设为 `12890`，因此直接 `cp deploy/.env.example deploy/.env` 后，Elasticsearch localhost `12888` / Kibana `12889` 不需要再额外避让端口。

也可以用 API 导入：

```bash
curl -sS -X POST "http://localhost:12889/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@deploy/observability/kibana/saved-objects.ndjson
```

## 资产说明

### `Community Observability: Trace By Service`

- 数据视图：`traces-*`
- 用途：从 `service.name` 和 `trace.id` 切入，查看同一条 trace 的 span 文档
- 说明：runtime OTLP wiring 和 Java agent 支持已经在仓库中接通，但默认 `OTEL_ENABLED=false`；只有在你显式打开它、并且应用 spans 实际流入 Elastic 后，这个视图才会出现有意义的数据

### `Community Observability: Auth Security Events`

- 数据视图：`logs-*`
- 默认查询：`community.category : security`
- 用途：查看登录、注册验证码、密码重置、origin guard、头像上传等安全相关事件
- 推荐用法：继续加 `service.name`、`community.action`、`community.outcome`、`trace.id` 等字段过滤收窄范围

### `Community Observability: Async Retry Dead Events`

- 数据视图：`logs-*`
- 默认查询：`community.category : async and community.outcome : (retry or dead)`
- 用途：查看 retry / dead 失败面的异步事件
- 推荐用法：继续加 `trace.id` 这类已结构化字段，或围绕 `community.event_id=<id>`、`community.job_id=<id>`、`community.topic=<topic>` 这类 raw token / body 搜索继续过滤
- 边界：它只适合看 retry/dead 失败面，不适合作为 `community.job_id` 的通用起点，因为搜索重建任务还会有 `success` / `skipped` / `failure` 记录

### `Community Observability: Service Health Overview`

- 数据视图：`logs-*`
- 默认查询：`community.outcome : (failure or degraded or dead or retry or denied) or community.category : exception`
- 用途：先看异常/降级面，再从 `service.name`、`community.category`、`community.action`、`trace.id` 继续下钻
- 说明：当前 baseline taxonomy 已覆盖 gateway access logs、community-app audit logs、community-app exception logs 和 im-core exception logs
- 边界：这是排障视图，不是正式告警面板

## 使用边界

- 这里提供的是搜索/视图和 runbook 起点，不是仓库内置 alert rules。
- logs 侧 saved searches 以 compose 路径下共享 volume 里的 fielded JSON logs 为前提；直接本地运行则继续以 stdout / 本地控制台日志为主。
- `Trace By Service` 只在 `OTEL_ENABLED=true` 且 traces 实际流入后有意义；如果 traces 没有流入，仍可在 `logs-*` 里通过 `trace.id` 等字段做日志侧排障。
- 如果你的本地 space 已有自定义 data view 命名，导入后可以在 Kibana 中复制并调整查询，而不需要修改仓库文件。
