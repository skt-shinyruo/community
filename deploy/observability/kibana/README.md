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

## Notes

- `logs-*` comes from structured JSON logs written into the shared observability logs volume
- `traces-*` is populated by default when services are started through `deployment.sh`; use `OTEL_ENABLED=false` to keep the overlay but opt out of tracing, or `--no-observability` to disable the overlay
- Use `trace.id` to pivot between logs and spans; use business `requestId` only for idempotency or message acknowledgement questions
- The saved objects are intended as a stable troubleshooting starting point, not as a full alerting solution
