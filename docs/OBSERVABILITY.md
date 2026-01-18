# 可观测性（日志 / 指标 / 告警）

本项目默认不使用 ELK，而是采用更轻量的组合：
- **日志**：Promtail → Loki → Grafana Explore
- **指标**：Prometheus → Grafana
- **告警**：Prometheus rules → Alertmanager

> 默认情况下这些组件不暴露到宿主机；如需浏览器访问，请额外启用 `deploy/docker-compose.ports.yml`（端口 `12883+`）。

---

## 1. 日志：Promtail → Loki → Grafana

### 1.1 日志采集来源
- Promtail 读取 Docker 容器 json log 文件：
  - 配置：`deploy/observability/promtail-config.yml`
  - 路径：`/var/lib/docker/containers/*/*-json.log`（以 volume 方式挂载）

### 1.2 如何检索日志（Grafana）
1. 启动时额外加上 `deploy/docker-compose.ports.yml`
2. 打开 `http://localhost:12883`（默认 `admin/admin`）
3. 进入 Explore → 选择数据源 `Loki`
4. 推荐从 `{job="docker"}` 开始，再用 `|=` 关键字过滤

### 1.3 推荐的“定位线索”
- **traceId**：gateway 会注入并透传 `X-Trace-Id`（同时生成/补全 `traceparent`），用于跨服务串联日志。
- **审计日志（gateway）**：对非 `GET/OPTIONS` 的 `/api/**` 记录审计日志（跳过 `/api/auth/login`），用于定位写路径与敏感操作。

---

## 2. 指标：Prometheus → Grafana

### 2.1 指标抓取
- Prometheus 抓取各服务的 `/actuator/prometheus`：
  - 配置：`deploy/observability/prometheus.yml`
  - 目标：gateway 与所有 `*-service`（容器名:端口）

### 2.2 常见用法
你可以在 Grafana Explore 里选择 Prometheus：
- 查看服务存活：`up{job="gateway"}` / `up{job="auth-service"}` 等
- 结合时间窗口排查：先看 `up`，再看对应服务日志

---

## 3. 告警：Prometheus rules → Alertmanager

### 3.1 规则与触发
告警规则位于：
- `deploy/observability/alerts.yml`

当前包含两类：
1. 服务不可用：`up == 0` 持续一段时间
2. 网关安全相关：
   - 限流拦截异常增高（可能是爆破/刷接口）
   - 限流 Redis 异常（提示检查 Redis 与网络）

### 3.2 Alertmanager
Alertmanager 配置位于：
- `deploy/observability/alertmanager.yml`

---

## 4. 端口与暴露策略（为什么默认不开放）

默认不开放宿主机端口的原因：
- 避免与本地已有服务冲突（尤其是 Redis/MySQL/Kafka/ES 常见端口）
- 避免误把依赖暴露给宿主机/局域网，降低安全与误操作风险

当你确实需要浏览器访问观测组件时，再开启：
- `deploy/docker-compose.ports.yml`（映射到 `12883+`）

