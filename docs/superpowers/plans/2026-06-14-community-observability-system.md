# Community Observability System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable local/demo stability observability baseline with verified OTel traces, JSON stdout logs, runtime stability events, safe runtime diagnostics defaults, Elasticsearch smoke checks, and operator runbooks.

**Architecture:** Keep the existing Elastic/Kibana + EDOT Collector path as the local observability boundary. OTel Java Agent owns request/async traces and metrics, `community-common-observability` owns always-on runtime stability logs, and `runtime-diagnostics-agent` remains default-off for short focused troubleshooting sessions.

**Tech Stack:** Bash deploy tests, Docker Compose rendered config, EDOT Collector config, Elasticsearch HTTP APIs, Spring Boot shared observability module, Logback JSON stdout, Maven tests, repository handbook docs.

---

## File Map

- Modify: `deploy/tests/observability_otel_default.sh`
  - Extend static config coverage for collector/logback/stdout/runtime diagnostics defaults.
- Create: `deploy/tests/observability_smoke.sh`
  - Runtime smoke test that checks Elasticsearch, traces, logs, runtime events, and optional diagnostics through HTTP APIs.
- Modify: `deploy/README.md`
  - Document local observability smoke commands and diagnostics run modes.
- Modify: `docs/handbook/operations.md`
  - Add scenario-oriented stability observability runbooks and production compatibility notes.
- Modify: `deploy/observability/kibana/README.md`
  - Add saved-object and query validation notes for the local baseline.
- Optional modify: `deploy/observability/edot-collector.yml`
  - Keep logs/traces/metrics pipelines aligned with the spec.
- Optional modify: `backend/community-common/common-observability/**`
  - Fill missing always-on stability events or field names without adding business code.

## Task 1: Static Observability Contract Test

**Files:**
- Modify: `deploy/tests/observability_otel_default.sh`
- Reference: `deploy/observability/edot-collector.yml`
- Reference: `backend/community-common/common-observability/src/main/resources/logback/community-observability.xml`
- Reference: `deploy/compose.runtime.services.single.yml`
- Reference: `deploy/compose.runtime.services.cluster.yml`

- [ ] **Step 1: Add failing checks for collector/logback/static contracts**

Append these checks near the end of `deploy/tests/observability_otel_default.sh`, before the final `OTEL_ENABLED=true ... --no-observability` block if possible so config failures are reported before override checks:

```bash
collector_config="deploy/observability/edot-collector.yml"
logback_config="backend/community-common/common-observability/src/main/resources/logback/community-observability.xml"

if ! rg -n 'filelog/docker_stdout:' "${collector_config}" >/dev/null; then
  echo "expected collector to read Docker stdout logs through filelog/docker_stdout" >&2
  exit 1
fi

if ! rg -n 'receivers: \\[filelog/docker_stdout\\]' "${collector_config}" >/dev/null; then
  echo "expected collector logs pipeline to receive Docker stdout logs" >&2
  exit 1
fi

if ! rg -n 'receivers: \\[otlp\\]' "${collector_config}" >/dev/null; then
  echo "expected collector traces and metrics pipelines to receive OTLP" >&2
  exit 1
fi

if ! rg -n 'logs_index: logs-community-default' "${collector_config}" >/dev/null; then
  echo "expected collector logs exporter to write logs-community-default" >&2
  exit 1
fi

if ! rg -n 'service.namespace' "${collector_config}" >/dev/null; then
  echo "expected collector to upsert service.namespace" >&2
  exit 1
fi

if ! rg -n 'CONSOLE_JSON' "${logback_config}" >/dev/null; then
  echo "expected shared logback config to define CONSOLE_JSON" >&2
  exit 1
fi

if ! rg -n 'service.name' "${logback_config}" >/dev/null; then
  echo "expected shared logback JSON to include service.name" >&2
  exit 1
fi

if ! rg -n 'trace\\.id|trace.id' "${logback_config}" >/dev/null; then
  echo "expected shared logback config to preserve trace.id correlation" >&2
  exit 1
fi
```

- [ ] **Step 2: Run the test and verify the current baseline**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
```

Expected:

```text
no output and exit code 0
```

If it fails, inspect the printed message. Only change collector or logback config if the failure reflects a real contract gap.

- [ ] **Step 3: Fix any true static contract gaps**

If `trace.id` check fails because the Logback config keeps MDC fields dynamically rather than explicitly naming them, update the check to assert the `<mdc>` provider exists and that legacy `traceId` is excluded:

```bash
if ! rg -n '<mdc>' "${logback_config}" >/dev/null; then
  echo "expected shared logback config to include MDC fields for trace.id/span.id" >&2
  exit 1
fi

if ! rg -n '<excludeMdcKeyName>traceId</excludeMdcKeyName>' "${logback_config}" >/dev/null; then
  echo "expected shared logback config to exclude legacy traceId MDC key" >&2
  exit 1
fi
```

Do not hard-code `trace.id` in Logback if the existing MDC provider already emits it.

- [ ] **Step 4: Re-run static deploy tests**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
bash deploy/tests/topology_single_cluster.sh
bash deploy/tests/oss_topology.sh
```

Expected:

```text
each command exits 0
```

- [ ] **Step 5: Commit static contract coverage**

Run:

```bash
git add deploy/tests/observability_otel_default.sh deploy/observability/edot-collector.yml backend/community-common/common-observability/src/main/resources/logback/community-observability.xml
git commit -m "test: harden observability static contract"
```

If collector/logback files were not modified, `git add` may include only the test file.

## Task 2: Runtime Observability Smoke Script

**Files:**
- Create: `deploy/tests/observability_smoke.sh`
- Modify: `deploy/README.md`
- Reference: `docs/superpowers/specs/2026-06-14-community-observability-system-design.md`

- [ ] **Step 1: Create a failing runtime smoke script**

Create `deploy/tests/observability_smoke.sh` with this content:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${repo_root}"

es_url="${ELASTICSEARCH_URL:-http://localhost:12888}"
gateway_url="${COMMUNITY_GATEWAY_URL:-http://localhost:12880}"
timeout_seconds="${OBSERVABILITY_SMOKE_TIMEOUT_SECONDS:-90}"
sleep_seconds="${OBSERVABILITY_SMOKE_POLL_SECONDS:-5}"

fail() {
  echo "observability smoke failed: $*" >&2
  exit 1
}

json_escape() {
  sed 's/\\/\\\\/g; s/"/\\"/g'
}

wait_for_elasticsearch() {
  local deadline=$((SECONDS + timeout_seconds))
  until curl -fsS "${es_url}/_cluster/health" >/dev/null; do
    if [ "${SECONDS}" -ge "${deadline}" ]; then
      fail "Elasticsearch did not become available at ${es_url}"
    fi
    sleep "${sleep_seconds}"
  done
}

search_count() {
  local index="$1"
  local query_json="$2"
  curl -fsS -H 'Content-Type: application/json' \
    "${es_url}/${index}/_count" \
    -d "{\"query\":${query_json}}" |
    sed -n 's/.*"count"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p'
}

require_count() {
  local label="$1"
  local index="$2"
  local query_json="$3"
  local deadline=$((SECONDS + timeout_seconds))
  local count="0"
  until [ "${count:-0}" -gt 0 ]; do
    count="$(search_count "${index}" "${query_json}")"
    count="${count:-0}"
    if [ "${count}" -gt 0 ]; then
      echo "${label}: found ${count}"
      return 0
    fi
    if [ "${SECONDS}" -ge "${deadline}" ]; then
      fail "${label} not found in ${index}; query=${query_json}"
    fi
    sleep "${sleep_seconds}"
  done
}

request_trace_id() {
  local headers_file
  local body_file
  headers_file="$(mktemp)"
  body_file="$(mktemp)"
  trap 'rm -f "${headers_file}" "${body_file}"' RETURN

  curl -fsS -D "${headers_file}" -o "${body_file}" "${gateway_url}/api/runtime-config" >/dev/null ||
    fail "could not call ${gateway_url}/api/runtime-config"

  local trace_id
  trace_id="$(sed -n 's/.*"traceId"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F]\{32\}\)".*/\1/p' "${body_file}" | head -n 1)"
  if [ -z "${trace_id}" ]; then
    trace_id="$(awk 'BEGIN{IGNORECASE=1} /^traceparent:/ {print $2}' "${headers_file}" |
      tr -d '\r' |
      sed -n 's/^00-\([0-9a-fA-F]\{32\}\)-[0-9a-fA-F]\{16\}-[0-9a-fA-F]\{2\}$/\1/p' |
      head -n 1)"
  fi

  if [ -z "${trace_id}" ]; then
    echo "response body:" >&2
    cat "${body_file}" >&2
    echo "response headers:" >&2
    cat "${headers_file}" >&2
    fail "could not extract trace id from runtime-config response"
  fi

  printf '%s\n' "${trace_id}"
}

wait_for_elasticsearch
trace_id="$(request_trace_id)"
escaped_trace_id="$(printf '%s' "${trace_id}" | json_escape)"
echo "trace.id=${trace_id}"

require_count "backend JSON logs" "logs-community-default" \
  '{"bool":{"filter":[{"exists":{"field":"service.name"}},{"term":{"service.namespace":"community"}}]}}'

require_count "runtime stability events" "logs-community-default" \
  '{"bool":{"filter":[{"exists":{"field":"event.category"}},{"terms":{"event.category":["runtime","database","messaging","access","cache","http_client","job","security","logging"]}}]}}'

require_count "request trace" "traces-*" \
  "{\"term\":{\"trace.id\":\"${escaped_trace_id}\"}}"

require_count "request-correlated logs" "logs-community-default" \
  "{\"term\":{\"trace.id\":\"${escaped_trace_id}\"}}"

if [ "${OBSERVABILITY_EXPECT_DIAGNOSTICS:-false}" = "true" ]; then
  require_count "runtime diagnostics events" "logs-community-default" \
    '{"term":{"event.category":"runtime_diagnostics"}}'
fi
```

- [ ] **Step 2: Make the script executable**

Run:

```bash
chmod +x deploy/tests/observability_smoke.sh
```

- [ ] **Step 3: Run the script without a stack and verify it fails clearly**

Run:

```bash
ELASTICSEARCH_URL=http://127.0.0.1:9 OBSERVABILITY_SMOKE_TIMEOUT_SECONDS=1 ./deploy/tests/observability_smoke.sh
```

Expected:

```text
observability smoke failed: Elasticsearch did not become available at http://127.0.0.1:9
```

- [ ] **Step 4: Document runtime smoke usage in deploy README**

Add this section under `## 观测层` in `deploy/README.md` after the default ports:

````markdown
### Observability Smoke

After the stack is up, verify that logs and traces are queryable:

```bash
./deploy/tests/observability_smoke.sh
```

The script calls `GET /api/runtime-config`, extracts a `traceId` from the response
body or `traceparent` header, and checks Elasticsearch for:

- backend JSON logs in `logs-community-default`
- runtime stability events
- a matching trace document in `traces-*`
- request-correlated logs with the same `trace.id`

For a short diagnostics run, start with `RUNTIME_DIAGNOSTICS_ENABLED=true` and set:

```bash
OBSERVABILITY_EXPECT_DIAGNOSTICS=true ./deploy/tests/observability_smoke.sh
```
````

- [ ] **Step 5: Run shell syntax checks**

Run:

```bash
bash -n deploy/tests/observability_smoke.sh
bash -n deploy/tests/observability_otel_default.sh
```

Expected:

```text
no output and exit code 0
```

- [ ] **Step 6: Commit the smoke script and README**

Run:

```bash
git add deploy/tests/observability_smoke.sh deploy/README.md
git commit -m "test: add observability smoke script"
```

## Task 3: Runtime Stability Event Gap Audit

**Files:**
- Modify if needed: `backend/community-common/common-observability/src/main/java/com/nowcoder/community/common/observability/**`
- Modify if needed: `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/**`
- Reference: `docs/superpowers/specs/2026-06-14-community-observability-system-design.md`
- Reference: `docs/handbook/operations.md`

- [ ] **Step 1: Run existing common observability tests**

Run:

```bash
mvn -q -pl :community-common-observability test
```

Expected:

```text
exit code 0
```

- [ ] **Step 2: Audit existing event names**

Run:

```bash
rg -n 'RuntimeLogEvent\\.builder\\("' backend/community-common/common-observability/src/main/java
```

Expected categories include at least:

```text
runtime
database
cache
messaging
access
http_client
job
security
logging
```

Expected actions include at least:

```text
jvm_startup
jvm_memory_pressure
jvm_gc_pause_threshold
executor_pressure
hikari_pool_pressure
sql_slow_query
redis_command_slow
kafka_consumer_lag_threshold
http_slow_request
http_client_slow
scheduled_job_slow
process_fd_pressure
disk_space_pressure
cpu_load_threshold
```

- [ ] **Step 3: Add tests for any missing required stability group**

If one of the required groups has no test, add a focused test in the matching test class. For example, if process CPU threshold coverage is missing, add this to `backend/community-common/common-observability/src/test/java/com/nowcoder/community/common/observability/system/ProcessResourceRuntimeLoggerTest.java`:

```java
@Test
void shouldLogCpuLoadThresholdWhenLoadExceedsThreshold() {
    try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.process-resource-runtime")) {
        RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
        properties.getSystem().setCpuLoadThresholdPercent(1);
        ProcessResourceRuntimeLogger logger = new ProcessResourceRuntimeLogger(capture.writer(), properties);

        assertThat(logger.logCpuLoad(95)).isTrue();

        assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.EVENT_ACTION, "cpu_load_threshold")
                .containsEntry(RuntimeLogFields.EVENT_OUTCOME, "threshold")
                .containsEntry("process.cpu.load.percent", "95");
    }
}
```

Adapt the method name only to the real public API in the logger class. If no public method exists to trigger the event, add one minimal package-visible method for tests and call it from the existing production scan path.

- [ ] **Step 4: Implement only missing event coverage**

If a required event is missing, implement it with the existing `RuntimeLogEvent.builder(category, action, outcome, message)` pattern. Example shape:

```java
logWriter.warn(RuntimeLogEvent.builder("runtime", "cpu_load_threshold", "threshold", "cpu load threshold")
        .field(RuntimeLogFields.THRESHOLD_PERCENT, properties.getSystem().getCpuLoadThresholdPercent())
        .field("process.cpu.load.percent", loadPercent)
        .build());
```

Rules:

- Do not add business-domain calls.
- Do not log payloads, raw keys, request bodies, SQL bind values, object keys, tokens, or credentials.
- Prefer threshold/state-change events over per-operation logs.

- [ ] **Step 5: Run common observability tests**

Run:

```bash
mvn -q -pl :community-common-observability test
```

Expected:

```text
exit code 0
```

- [ ] **Step 6: Commit event coverage only if code changed**

If Step 4 changed code or tests, run:

```bash
git add backend/community-common/common-observability/src/main/java backend/community-common/common-observability/src/test/java
git commit -m "feat: complete runtime stability event coverage"
```

If no code changed, do not create an empty commit.

## Task 4: Scenario Runbooks

**Files:**
- Modify: `docs/handbook/operations.md`
- Reference: `docs/superpowers/specs/2026-06-14-community-observability-system-design.md`

- [ ] **Step 1: Add scenario-oriented runbook section**

Add a new `## Stability Observability Runbooks` section after the existing `### Runtime Diagnostics Agent` section in `docs/handbook/operations.md`:

````markdown
## Stability Observability Runbooks

Use these runbooks in Kibana with the local observability overlay. Prefer
structured fields over message text.

### JVM Or Memory Pressure

Query:

```text
service.namespace : "community" and event.category : "runtime" and
event.action : ("jvm_memory_pressure" or "jvm_direct_memory_pressure" or "jvm_gc_pause_threshold")
```

Inspect: `service.name`, `jvm.memory.area`, `jvm.memory.used.percent`,
`jvm.gc.name`, `jvm.gc.pause.ms`, `threshold.percent`, `threshold.ms`.

Interpretation: repeated memory pressure or GC pause threshold events mean the
service is under runtime pressure, even if request traces only show downstream
latency.

Next action: check `process.cpu.load.percent`, recent deploy version, traffic
shape, and whether one service produces most pressure events. Enable
`runtime-diagnostics-agent` `thread,jvm` probes only if the standard runtime logs
do not show enough detail.

### Thread Pool Or Scheduler Saturation

Query:

```text
service.namespace : "community" and event.category : "runtime" and
event.action : ("executor_pressure" or "scheduled_job_slow" or "scheduled_job_skipped" or "scheduled_job_error")
```

Inspect: `executor.name`, `executor.active`, `executor.pool.size`,
`executor.queue.size`, `duration.ms`, `threshold.ms`, `job.name`.

Interpretation: executor pressure means request, Kafka, or scheduled work may be
queued before it appears slow in traces.

Next action: identify the executor or job name, then pivot by `service.name` and
time range to request traces and downstream dependency events.

### Database Pool Pressure

Query:

```text
service.namespace : "community" and event.category : "database" and
event.action : ("hikari_pool_pressure" or "sql_slow_query")
```

Inspect: `db.pool.name`, `db.pool.active`, `db.pool.idle`, `db.pool.pending`,
`db.mybatis.statement`, `db.operation`, `duration.ms`, `threshold.ms`.

Interpretation: Hikari pending count indicates pool wait. Slow SQL events show
statement identity without bind values.

Next action: compare with traces for the same time window. Enable diagnostics
`jdbc` only for a short run if traces and SQL slow events are not enough.

### Redis Instability Or Slow Operations

Query:

```text
service.namespace : "community" and event.category : "cache" and
event.action : ("redis_connection_pressure" or "redis_command_slow" or "cache_hit_ratio_low")
```

Inspect: `cache.system`, `cache.operation`, `cache.pool.active`,
`cache.pool.pending`, `cache.hit.ratio.percent`, `duration.ms`, `threshold.ms`.

Interpretation: Redis slow operations or pool pressure can cause application
latency before database metrics change.

Next action: check whether the issue is isolated to one service and compare with
request traces. Enable diagnostics `redis` only for a short run; raw keys and
values must remain absent.

### Kafka Lag Or Rebalance

Query:

```text
service.namespace : "community" and event.category : "messaging" and
event.action : ("kafka_consumer_lag_threshold" or "kafka_rebalance" or "kafka_producer_error")
```

Inspect: `messaging.destination.name`, `messaging.kafka.consumer.group`,
`messaging.kafka.partition`, `messaging.kafka.consumer.lag`, `error.type`.

Interpretation: lag and rebalance events explain delayed projections, IM fanout,
and outbox delivery even when HTTP traces look healthy.

Next action: check outbox state and consumer services. Enable diagnostics
`kafka` only for short producer/consumer investigation; payloads must remain
absent.

### Slow HTTP Requests

Query:

```text
service.namespace : "community" and event.category : "access" and
event.action : "http_slow_request"
```

Inspect: `trace.id`, `service.name`, `http.request.method`, `http.route`,
`url.path`, `http.response.status_code`, `duration.ms`, `threshold.ms`.

Interpretation: use `trace.id` to pivot into traces and request-correlated logs.

Next action: if traces identify a dependency, inspect the matching database,
cache, messaging, or HTTP client event category for the same time window.

### When To Enable Runtime Diagnostics

Enable diagnostics only after always-on traces and runtime logs do not explain
the symptom. Keep includes narrow:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true \
RUNTIME_DIAGNOSTICS_INCLUDES='com.nowcoder.community.*' \
RUNTIME_DIAGNOSTICS_PROBES='method,exception,thread,jvm' \
./deploy/deployment.sh up --topology single
```

Query:

```text
event.category : runtime_diagnostics and diagnostic.probe : *
```

Use dependency probes only for focused short sessions:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true \
RUNTIME_DIAGNOSTICS_PROBES='method,exception,thread,jvm,http,jdbc,redis,kafka' \
./deploy/deployment.sh up --topology single
```
````

- [ ] **Step 2: Add production compatibility note**

Add this paragraph at the end of the new runbook section:

````markdown
### Production Compatibility

Phase 1 intentionally keeps Elastic/Kibana as the local UI and does not add
Prometheus, Grafana, Loki, or Alertmanager. Production alerting can later use the
same signal split: traces for timelines, metrics for trends and SLOs, runtime
logs for discrete stability events, and runtime diagnostics for short deep dives.
Candidate SLOs are HTTP availability/latency, Kafka lag, database pool pending,
Redis error/slow-operation rate, JVM memory/GC pressure, executor saturation, and
outbox backlog or dead-letter rate.
```
````

- [ ] **Step 3: Check the doc for incomplete markers and broken headings**

Run:

```bash
rg -n 'T''BD|TO''DO|FIX''ME' docs/handbook/operations.md
rg -n '^## Stability Observability Runbooks|^### JVM Or Memory Pressure|^### Production Compatibility' docs/handbook/operations.md
```

Expected:

```text
first command exits 1 with no matches
second command prints the new headings
```

- [ ] **Step 4: Commit runbooks**

Run:

```bash
git add docs/handbook/operations.md
git commit -m "docs: add stability observability runbooks"
```

## Task 5: Saved Object and Query Documentation

**Files:**
- Modify: `deploy/observability/kibana/README.md`
- Modify if needed: `deploy/observability/kibana/saved-objects.ndjson`

- [ ] **Step 1: Add query validation notes**

Add this section before `## Notes` in `deploy/observability/kibana/README.md`:

````markdown
## Baseline Queries

After importing saved objects, these Kibana filters should return data in a
healthy local stack:

```text
service.namespace : "community"
event.category : runtime
event.category : database
event.category : messaging
event.action : http_slow_request
trace.id : "<trace id from response>"
```

Diagnostics queries are expected to return data only after a short diagnostics
run:

```text
event.category : runtime_diagnostics
diagnostic.probe : method
diagnostic.probe : thread
```

If a baseline query is empty, first run:

```bash
./deploy/tests/observability_smoke.sh
```
````

- [ ] **Step 2: Inspect saved objects for baseline data views**

Run:

```bash
rg -n 'logs-\\*|traces-\\*|logs-community-default|trace\\.id|event.category|service.name' deploy/observability/kibana/saved-objects.ndjson
```

Expected:

```text
matches for logs/traces data views and core fields
```

If there are no `logs-*` or `traces-*` matches, update saved objects manually through Kibana export in a running stack and replace `deploy/observability/kibana/saved-objects.ndjson`. Do not hand-edit complex saved-object JSON unless the change is a simple data-view title correction.

- [ ] **Step 3: Commit Kibana docs or assets**

Run:

```bash
git add deploy/observability/kibana/README.md deploy/observability/kibana/saved-objects.ndjson
git commit -m "docs: document observability kibana baseline"
```

If saved objects were unchanged, only the README should be staged.

## Task 6: End-To-End Local Smoke Verification

**Files:**
- Runtime only unless defects are found.
- Modify defect files only if a smoke command exposes a real issue.

- [ ] **Step 1: Run fast static checks**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
bash -n deploy/tests/observability_smoke.sh
mvn -q -pl :community-common-observability test
mvn -q -pl :runtime-diagnostics-agent test
```

Expected:

```text
all commands exit 0
```

- [ ] **Step 2: Start the single topology**

Run:

```bash
./deploy/deployment.sh up --topology single
```

Expected:

```text
docker compose starts the stack and exits 0
```

If this fails due to dependency download or image pull network restrictions, rerun with the required escalation approval in the active execution environment.

- [ ] **Step 3: Run runtime observability smoke**

Run:

```bash
./deploy/tests/observability_smoke.sh
```

Expected output includes:

```text
trace.id=<32 hex chars>
backend JSON logs: found <n>
runtime stability events: found <n>
request trace: found <n>
request-correlated logs: found <n>
```

- [ ] **Step 4: Run short diagnostics smoke**

Run:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true \
RUNTIME_DIAGNOSTICS_INCLUDES='com.nowcoder.community.*' \
RUNTIME_DIAGNOSTICS_PROBES='method,exception,thread,jvm' \
./deploy/deployment.sh up --topology single
```

Then run:

```bash
OBSERVABILITY_EXPECT_DIAGNOSTICS=true ./deploy/tests/observability_smoke.sh
```

Expected output includes:

```text
runtime diagnostics events: found <n>
```

- [ ] **Step 5: Stop the stack**

Run:

```bash
./deploy/deployment.sh down --topology single
```

Expected:

```text
docker compose stops the stack and exits 0
```

- [ ] **Step 6: Fix any real runtime defects**

If smoke fails, use the failure label to scope the fix:

- `backend JSON logs`: inspect shared Logback profile and collector log filter.
- `runtime stability events`: inspect `common-observability` auto-configuration and startup/periodic summary settings.
- `request trace`: inspect OTel agent startup and `OTEL_EXPORTER_OTLP_*`.
- `request-correlated logs`: inspect trace MDC/log correlation.
- `runtime diagnostics events`: inspect `RUNTIME_DIAGNOSTICS_ENABLED`, agent jar path, and probe includes.

After fixing, rerun Steps 1 through 5.

- [ ] **Step 7: Commit runtime fixes if any**

If Step 6 changed files, run:

```bash
git add deploy backend docs
git commit -m "fix: complete observability smoke baseline"
```

Do not commit generated logs, temporary files, or unrelated untracked documents.

## Task 7: Final Verification and Handoff

**Files:**
- No new files expected.

- [ ] **Step 1: Run final verification commands**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
bash -n deploy/tests/observability_smoke.sh
mvn -q -pl :community-common-observability test
mvn -q -pl :runtime-diagnostics-agent test
```

Expected:

```text
all commands exit 0
```

- [ ] **Step 2: Check git status**

Run:

```bash
git status --short
```

Expected:

```text
only unrelated pre-existing untracked Kafka docs may remain
```

- [ ] **Step 3: Summarize completion**

Report:

- Which commits were created.
- Whether the full runtime smoke was run.
- If runtime smoke could not run, state the exact blocker.
- Any remaining production-compatibility follow-up, such as Prometheus/Grafana alerting, as future work.
