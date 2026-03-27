# Kibana Assets for `observability-elastic`

本目录存放仓库内维护的 Kibana Phase 1 资产，供 `observability-elastic` 路径启动后手工导入。

当前资产面向 Kibana `8.12.x`，目标是给本地排障和演练环境提供一组稳定的起点，而不是替代 Prometheus 告警体系。

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
  - 只要 `observability-elastic` profile 已启动，这些 JSON 文件就属于这里这套 Kibana fielded-log ingestion path
- `observability-elastic` compose 路径
  - 最小启动路径是：`COMPOSE_PROFILES=observability-elastic docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
  - 如果额外加载 `deploy/observability-elastic/docker-compose.override.yml`，backend services 会以 `SPRING_PROFILES_ACTIVE=dev,json-logs,volume-log-export` 运行，容器 stdout 也会切到 JSON
  - logs 链路固定为：`backend structured JSON file appender -> shared observability_logs volume -> EDOT collector filelog -> Elastic`
  - collector 会解析 JSON log payload，并把 `service.name`、`service.version`、`trace.id`、`span.id`（存在时）、`community.category`、`community.action`、`community.outcome` 等字段提升到 `logs-*`
- traces / metrics
  - 仍然只有在 `OTEL_ENABLED=true` 且应用 OTLP signals 实际流入 Elastic 时才有意义
  - logs 路径不依赖 `OTEL_ENABLED=true`

## 导入步骤

1. 先启动 `observability-elastic` compose 路径：
   - 最小路径：`COMPOSE_PROFILES=observability-elastic docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
   - 如需让容器 stdout 也切到 JSON：`docker compose -f deploy/docker-compose.yml -f deploy/observability-elastic/docker-compose.override.yml --env-file deploy/.env --profile observability-elastic up -d --build`
2. 如果你现在就想把应用 traces / metrics 发到 collector，再额外把 `OTEL_ENABLED=true` 写入 `deploy/.env`
3. 打开 Kibana：`http://localhost:12889`
4. 进入 `Stack Management -> Saved Objects`
5. 选择 `Import`
6. 导入 `deploy/observability-elastic/kibana/saved-objects.ndjson`
7. 勾选 overwrite（如果你要用仓库版本覆盖本地旧对象）

如果你只是在 base compose 上设置 `COMPOSE_PROFILES=observability-elastic`，Kibana 和 collector 就已经可以启动，而且仍然能从共享 volume 里拿到 fielded logs；额外加载 override 的区别只是让 backend stdout 也切到 JSON。

也可以用 API 导入：

```bash
curl -sS -X POST "http://localhost:12889/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@deploy/observability-elastic/kibana/saved-objects.ndjson
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
- 边界：这是 Phase 1 的排障视图，不是正式告警面板；服务可用性和基础健康告警仍然以 Prometheus 为权威来源

## 使用边界

- Phase 1 没有把 Prometheus 告警迁到 Kibana；这里提供的是搜索/视图和 runbook 起点，不是仓库内置 alert rules。
- logs 侧 saved searches 以 compose 路径下共享 volume 里的 fielded JSON logs 为前提；override 只影响容器 stdout 是否也切到 JSON。直接本地运行则继续以 stdout / 本地控制台日志为主。
- `Trace By Service` 只在 `OTEL_ENABLED=true` 且 traces 实际流入后有意义；如果 traces 没有流入，仍可在 `logs-*` 里通过 `trace.id` 等字段做日志侧排障。
- 如果你的本地 space 已有自定义 data view 命名，导入后可以在 Kibana 中复制并调整查询，而不需要修改仓库文件。
