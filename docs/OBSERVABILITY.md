# 可观测性（日志 / 指标 / 告警）

本项目默认不使用 ELK，而是采用更轻量的组合：
- **日志**：Promtail → Loki → Grafana Explore
- **指标**：Prometheus → Grafana
- **告警**：Prometheus rules → Alertmanager

> 观测/日志组件默认不随业务栈启动；如需使用，请启用 compose profile：`observability`（端口 `12883+`，默认仅绑定到 `127.0.0.1`）。

---

## 1. 日志：Promtail → Loki → Grafana

### 1.1 日志采集来源
- Promtail 读取 Docker 容器 json log 文件：
  - 配置：`deploy/observability/promtail-config.yml`
  - 路径：`/var/lib/docker/containers/*/*-json.log`（以 volume 方式挂载）

### 1.2 如何检索日志（Grafana）
1. 启用 `observability` profile（推荐：在 `deploy/.env` 中添加 `COMPOSE_PROFILES=observability`，再启动 compose）
2. 打开 `http://localhost:12883`（默认 `admin/admin`）
3. 进入 Explore → 选择数据源 `Loki`
4. 推荐从 `{job="docker"}` 开始，再用 `|=` 关键字过滤

### 1.3 推荐的“定位线索”
- **traceId**：community-app 会注入并透传 `X-Trace-Id`（同时生成/补全 `traceparent`），用于跨模块串联日志。
- **审计日志（community-app）**：对非 `GET/OPTIONS` 的 `/api/**` 记录审计日志（跳过 `/api/auth/login`），用于定位写路径与敏感操作。

---

## 2. 指标：Prometheus → Grafana

### 2.1 指标抓取
- Prometheus 抓取 `community-app` 的 `/actuator/prometheus`：
  - 配置：`deploy/observability/prometheus.yml`
  - 目标：`community-app:8080`

### 2.2 常见用法
你可以在 Grafana Explore 里选择 Prometheus：
- 查看服务存活：`up{job="community-app"}`
- 结合时间窗口排查：先看 `up`，再看对应服务日志

### 2.3 Kafka DLQ 指标（P0）
当消费端重试耗尽或出现不可恢复异常时，消息会被投递到 DLQ（`<topic>.dlq`）。  
为避免“默默积压无人知”，P0 增加 DLQ publish 指标：

- 指标：`kafka_dlq_published_total{original_topic="...", error_type="..."}`
- 适用服务：`message-service`、`search-service`（统一在各自的 `KafkaErrorHandlerConfig` recoverer 内递增）

常用查询：
- 近 5 分钟 DLQ 新增：`sum by (job, original_topic) (increase(kafka_dlq_published_total[5m]))`

---

## 3. 告警：Prometheus rules → Alertmanager

### 3.1 规则与触发
告警规则位于：
- `deploy/observability/alerts.yml`

当前包含两类：
1. 服务不可用：`up == 0` 持续一段时间
2. Kafka DLQ：
   - 近 5 分钟 DLQ publish 增量 > 0（提示需要排查与评估是否回放）

### 3.2 Alertmanager
Alertmanager 配置位于：
- `deploy/observability/alertmanager.yml`

### 3.3 DLQ 回放 Runbook（演练/受控窗口）
> ⚠️ 回放会触发消费者再次执行副作用（通知/索引更新等）。强烈建议只在演练环境或受控窗口执行，并使用“限量/限速/dry-run”。

1. 先定位：
   - 查告警或指标，确定 `original_topic`
   - 在 Loki 中按 `job="<service>"` + `original_topic` 检索对应异常堆栈与失败原因
2. 再决定是否回放：
   - 如果是“短暂依赖抖动/下游短暂不可用”导致的失败，一般可回放
   - 如果是“代码 bug/数据不合法”导致的失败，应先修复再回放
3. 回放操作（脚本）：
   - Dry run（默认）：`DRY_RUN=true MAX_MESSAGES=10 backend/scripts/kafka-replay-dlq.sh <topic>.dlq`
   - 真正回推：`DRY_RUN=false MAX_MESSAGES=10 SLEEP_MS=50 backend/scripts/kafka-replay-dlq.sh <topic>.dlq`
   - 脚本约束：
     - topic 必须以 `community.event.` 开头且以 `.dlq` 结尾（白名单前缀）
     - 默认 `MAX_MESSAGES=50`，避免误操作一次性回放过多

---

## 4. 端口与暴露策略（为什么默认不开放）

默认不开放宿主机端口的原因：
- 避免与本地已有服务冲突（尤其是 Redis/MySQL/Kafka/ES 常见端口）
- 避免误把依赖暴露给宿主机/局域网，降低安全与误操作风险

当你确实需要浏览器访问观测组件时，再开启：
- `observability` profile（映射到 `12883+`）
