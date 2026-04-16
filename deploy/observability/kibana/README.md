# Kibana Assets for `observability`

This directory stores the repository-managed Kibana saved objects for the local observability overlay.

## Start The Stack

Choose one topology, then add `--observability`:

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/deployment.sh up --topology dev --observability
```

or

```bash
cp deploy/.env.ha.example deploy/.env.ha
./deploy/deployment.sh up --topology ha --observability
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

- `logs-*` comes from structured JSON logs written into the shared `observability_logs` volume
- `traces-*` only becomes useful when `OTEL_ENABLED=true` and application spans are actually exported
- The saved objects are intended as a stable troubleshooting starting point, not as a full alerting solution
