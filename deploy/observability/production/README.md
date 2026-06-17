# Production-Compatible Collector Templates

These files document the target two-layer Collector shape for production-like deployments. They are not loaded by `deploy/deployment.sh` and do not replace the local EDOT Collector config at `deploy/observability/edot-collector.yml`.

Target shape:

```text
service OTLP and stdout
  -> node or sidecar Collector
      -> memory limiter
      -> local resource enrichment
      -> batch
      -> gateway OTLP export
  -> gateway Collector
      -> memory limiter
      -> local resource enrichment
      -> sensitive attribute deletion
      -> trace tail sampling
      -> batch
      -> routing to logs, metrics, and traces backends
```

Application code must remain independent of Elasticsearch, Kibana, Prometheus, Mimir, Grafana, Loki, Tempo, and Jaeger.

The gateway `attributes/drop_sensitive` processor redacts OTLP resource, span, metric, and log attributes. It does not rewrite forbidden fields embedded inside raw Docker stdout JSON log bodies collected by `filelog/docker_stdout`. Before using these templates in production, keep Docker stdout JSON payloads sanitized by application logging policy, or parse JSON body fields into attributes and apply equivalent redaction before export.

Required environment variables when adapting these templates:

- `COMMUNITY_OTEL_GATEWAY_ENDPOINT`: Collector OTLP gRPC endpoint used by the agent `otlp/gateway` exporter, for example `collector-gateway:4317`.
- `COMMUNITY_TRACES_OTLP_ENDPOINT`: trace backend OTLP gRPC endpoint used by the gateway `otlp/traces` exporter, for example `tempo:4317`.
- `COMMUNITY_METRICS_OTLPHTTP_ENDPOINT`: metrics backend OTLP HTTP endpoint used by the gateway `otlphttp/metrics` exporter, for example `http://mimir:9009/otlp`.
- `COMMUNITY_LOGS_ELASTICSEARCH_ENDPOINT`: Elasticsearch HTTP endpoint used by the gateway `elasticsearch/logs` exporter, for example `http://elasticsearch:9200`.
