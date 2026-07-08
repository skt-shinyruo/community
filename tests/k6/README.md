# k6 Load Testing Suite

This suite targets the gateway-first local topology. By default it sends traffic to `http://localhost:12880`, not directly to `community-app`.

## Start Target Stack

From the repository root:

```bash
cp deploy/.env.cluster.example deploy/.env.cluster
./deploy/deployment.sh up --topology cluster
```

For a smaller run, `single` works too:

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single
```

## Run

The runner uses `docker run grafana/k6`, so a local k6 binary is not required.

```bash
cd tests/k6
npm run smoke
npm run api-mix
npm run write-paths
npm run im-ws
npm run soak
npm run stress
npm run spike
```

Results are exported under `temp/k6-results`.

## Hot Path Scenario

Run:

```bash
npm run hot-path
```

The scenario always calls `/api/feed/global?size=<K6_READ_SIZE>`.
Set `K6_BOARD_ID=<uuid>` to include `/api/boards/{boardId}/feed`.
Set `K6_POST_ID=<uuid>` to include `/api/posts/{postId}`.

Use it after prewarm runs and after Redis flush/restart drills to compare hit,
fallback, degraded, and single-flight behavior through
`community_cache_requests_total`.

## Data And Accounts

Local seed data includes activated users. Defaults:

- primary: `aaa / aaa`
- secondary: `bbb / aaa`
- admin: `admin / aaa`

Override them with:

```bash
K6_USERNAME=aaa K6_PASSWORD=aaa npm run smoke
```

Useful environment variables:

- `K6_BASE_URL`: gateway origin, default `http://localhost:12880`.
- `K6_WS_URL`: WebSocket URL, default derived as `/ws/im`.
- `K6_DOCKER_IMAGE`: k6 image for the runner, default `grafana/k6:0.51.0`.
- `K6_WRITE_RATIO`: percentage of iterations that execute write flows in `write-paths`, default `10`.
- `K6_BOARD_ID`: optional board UUID used by `hot-path`.
- `K6_POST_ID`: optional post UUID used by `hot-path`.
- `K6_ALLOW_WRITES`: set `false` to disable write operations.
- `K6_IM_HOLD_SECONDS`: per-connection WebSocket hold time, default `20`.
- `K6_IM_SEND_MESSAGES`: set `true` to send room messages over WebSocket.
- `K6_IM_ROOM_ID`: required when `K6_IM_SEND_MESSAGES=true`.
- `K6_HTTP_FAILED_RATE`, `K6_HTTP_P95_MS`, `K6_HTTP_P99_MS`, `K6_CHECK_RATE`: threshold controls.

## Profiles

- `smoke`: low traffic health, public reads, login, and authenticated probes.
- `api-mix`: mixed public and authenticated read traffic across content, search, market, drive, notice, wallet, and IM history APIs.
- `write-paths`: low-rate stateful writes: post, comment, bookmark, like, drive folder.
- `im-ws`: opens `/api/im/sessions`, connects to `/ws/im`, sends `connect` and periodic `ping` frames.
- `soak`: long-running mixed reads for leak and queue buildup checks.
- `stress`: ramping arrival rate for saturation discovery.
- `spike`: sudden traffic jump for recovery and rate-limit behavior.

## Thresholds

Default threshold intent:

- `http_req_failed` below `1%`.
- API `http_req_duration` p95 below `800ms`, p99 below `1500ms`.
- `checks` above `98%`.
- WebSocket connect p95 below `1000ms`.
- no login failures.

Tune thresholds by exporting `K6_HTTP_FAILED_RATE`, `K6_HTTP_P95_MS`, `K6_HTTP_P99_MS`, and `K6_CHECK_RATE`.

## Observability

Use the observability overlay while running load tests.
The overlay is enabled by default; only add `--no-observability` when you explicitly want to disable it.

- Gateway and services expose `/actuator/prometheus`.
- Kibana is available at `http://localhost:12889` when observability is enabled.
- Elasticsearch is available at `http://localhost:12888`.

Watch p95/p99 latency, HTTP error rate, JVM heap and GC, DB connection pools, Redis latency, Kafka lag, outbox backlog, Elasticsearch query latency, and gateway rate-limit decisions.

## Safety Notes

`write-paths` creates real local records. Keep it pointed at local or disposable environments unless a production-safe data and cleanup plan exists. Destructive deletes are not part of the default suite; keep `K6_ALLOW_DESTRUCTIVE_WRITES=false` unless you add explicit cleanup scenarios.
