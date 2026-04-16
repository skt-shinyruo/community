# 可观测性（Elastic / Kibana）

本仓库当前的本地观测路径是一个共享 overlay：

- `deploy/compose.observability.yml`

它可以叠加在 `dev` 或 `ha` 拓扑之上：

- `./deploy/deployment.sh up --topology dev --observability`
- `./deploy/deployment.sh up --topology ha --observability`

## 1. 默认端口

- Elasticsearch：`http://localhost:12888`
- Kibana：`http://localhost:12889`

## 2. 数据流

### 2.1 logs

- backend structured JSON file appender
- shared `observability_logs` volume
- EDOT collector filelog receiver
- Elastic
- Kibana

### 2.2 traces / metrics

- 继续通过 OTLP -> EDOT collector -> Elastic
- 默认 `OTEL_ENABLED=false`
- 如果需要应用 traces / metrics，显式在对应 env 文件里打开 `OTEL_ENABLED=true`

## 3. 推荐启动方式

### 3.1 单机开发 + 观测

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/deployment.sh up --topology dev --observability
```

### 3.2 HA 演练 + 观测

```bash
cp deploy/.env.ha.example deploy/.env.ha
./deploy/deployment.sh up --topology ha --observability
```

## 4. Kibana 资产

仓库内资产位于：

- `deploy/observability/kibana/saved-objects.ndjson`

导入说明见：

- `deploy/observability/kibana/README.md`

## 5. 说明

- 只追加 observability overlay 时，fielded logs 仍然可用
- `Trace By Service` 只有在 traces 实际流入后才有意义
- 当前不再维护 Grafana / Loki / Prometheus / Alertmanager overlay
