# Production-Compatible Collector Templates

These files document the target two-layer Collector shape for production-like deployments. They are not loaded by `deploy/deployment.sh` and do not replace the local EDOT Collector config at `deploy/observability/edot-collector.yml`.

Target shape:

```text
service OTLP and stdout
  -> node or sidecar Collector
      -> memory limiter
      -> batch
      -> local resource enrichment
      -> gateway OTLP export
  -> gateway Collector
      -> trace tail sampling
      -> sensitive attribute deletion
      -> routing to logs, metrics, and traces backends
```

Application code must remain independent of Elasticsearch, Kibana, Prometheus, Mimir, Grafana, Loki, Tempo, and Jaeger.

Required environment variables when adapting these templates:

- `COMMUNITY_OTEL_GATEWAY_ENDPOINT`: Collector OTLP gRPC endpoint used by the agent `otlp/gateway` exporter, for example `collector-gateway:4317`.
- `COMMUNITY_TRACES_OTLP_ENDPOINT`: trace backend OTLP gRPC endpoint used by the gateway `otlp/traces` exporter, for example `tempo:4317`.
- `COMMUNITY_METRICS_OTLPHTTP_ENDPOINT`: metrics backend OTLP HTTP endpoint used by the gateway `otlphttp/metrics` exporter, for example `http://mimir:9009/otlp`.
- `COMMUNITY_LOGS_ELASTICSEARCH_ENDPOINT`: Elasticsearch HTTP endpoint used by the gateway `elasticsearch/logs` exporter, for example `http://elasticsearch:9200`.
