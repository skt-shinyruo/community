# Kibana Assets for `observability`

This directory stores the repository-managed Kibana saved objects for the local observability overlay.

## Start The Stack

Choose one topology; observability is enabled by default:

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single
```

or

```bash
cp deploy/.env.cluster.example deploy/.env.cluster
./deploy/deployment.sh up --topology cluster
```

Default UI endpoints:

- Kibana: `http://localhost:12889`
- Elasticsearch: `http://localhost:12888`

## Import Steps

1. Open Kibana at `http://localhost:12889`
2. Go to `Stack Management -> Saved Objects`
3. Choose `Import`
4. Import `deploy/observability/kibana/saved-objects.ndjson`
5. Enable overwrite if you want the repository version to replace local objects

You can also import via API:

```bash
curl -sS -X POST "http://localhost:12889/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@deploy/observability/kibana/saved-objects.ndjson
```

## Baseline Queries

After importing saved objects, start with these core filters and pivots. They
should have data after the local stack has emitted logs and the smoke script has
captured a response trace:

```text
service.namespace : "community"
event.category : runtime
trace.id : "<trace id from response>"
```

The runtime smoke script also checks the broader stability-event buckets, so
these filters are useful when the corresponding code path has emitted events:

```text
event.category : database
event.category : messaging
event.action : http_slow_request
```

Diagnostics queries are expected to return data only after a short diagnostics run:

```text
event.category : runtime_diagnostics
diagnostic.probe : method
diagnostic.probe : thread
```

If a baseline query is empty, first run:

```bash
./deploy/tests/observability_smoke.sh
```

If a conditional filter is still empty after the smoke script, exercise the
related database, messaging, or slow-request path before treating it as a
collector or saved-object issue.

## Notes

- `logs-*` comes from structured backend logs collected by the EDOT Collector from stdout and OTLP logs
- `traces-*` is populated by default when services are started through `deployment.sh`; use `OTEL_ENABLED=false` to keep the overlay but opt out of tracing, or `--no-observability` to disable the overlay
- Use `trace.id` to pivot between logs and spans; use business `requestId` only for idempotency or message acknowledgement questions
- The saved objects are intended as a stable troubleshooting starting point, not as a full alerting solution
