# Deploy Compose `-f` Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current `deploy/docker-compose.yml` + `COMPOSE_PROFILES` primary workflow with a layered `-f` compose layout, stable `make` entrypoints, and updated operator docs, while preserving the existing local HA topology and ports.

**Architecture:** Keep runtime behavior unchanged, but move compose content into `deploy/compose.yml`, `deploy/compose.infra.yml`, `deploy/compose.runtime.yml`, and optional overlay files for `debug`, `observability`, `observability-elastic`, and `json-logs`. Execute directly on `main` per user instruction, keep each commit coherent, and avoid worktrees. Introduce the new command path before deleting the legacy compose files so `main` never lands in an unusable intermediate state.

**Tech Stack:** Docker Compose v5, YAML anchors / merge keys, GNU Make, Markdown docs, git on `main`.

**Spec:** `docs/superpowers/specs/2026-04-06-deploy-compose-f-split-design.md`

---

## File Map

### New Compose Entry Points

- `deploy/compose.yml`
  - Top-level `name: community` and all named volumes.
- `deploy/compose.infra.yml`
  - MySQL, Redis, Kafka, Elasticsearch, `nacos`, `xxl-job-admin`, `mailhog`, bootstrap sidecars.
- `deploy/compose.runtime.yml`
  - `frontend`, `nginx`, `community-app-*`, `community-gateway-*`, `im-core-*`, `im-realtime-*`, `mock-data-studio`.
- `deploy/compose.debug.yml`
  - The three localhost debug port sidecars.
- `deploy/compose.observability.yml`
  - Prometheus / Alertmanager / Loki / Promtail / Grafana.
- `deploy/compose.observability-elastic.yml`
  - ES localhost port sidecar, Kibana, EDOT collector.
- `deploy/compose.json-logs.override.yml`
  - `SPRING_PROFILES_ACTIVE=...,json-logs,...` override for all backend replicas.

### Legacy Compose Files To Retire

- `deploy/docker-compose.yml`
  - Remove after the new layered stack and docs are live.
- `deploy/observability-elastic/docker-compose.override.yml`
  - Replace with `deploy/compose.json-logs.override.yml` and delete after references are migrated.

### Command / Operator Entry Points

- `Makefile`
  - New stable operator commands: `up`, `up-debug`, `up-obs`, `up-elastic`, `up-elastic-json`, `down`, `ps`, `logs`, `config*`.

### Docs / Runbooks That Must Be Updated

- `README.md`
- `backend/README.md`
- `deploy/README.md`
- `deploy/observability-elastic/kibana/README.md`
- `docs/DEPLOYMENT.md`
- `docs/OBSERVABILITY.md`
- `docs/ARCHITECTURE.md`
- `docs/LOCAL_HA.md`
- `docs/LOAD_TESTING.md`
- `docs/SECURITY.md`
- `docs/DATA_MODEL.md`
- `tools/im-load/README.md`

---

### Task 1: Repair The Current JSON Logs Override Before Any Split

**Files:**
- Modify: `deploy/observability-elastic/docker-compose.override.yml`
- Test: `deploy/docker-compose.yml`

- [ ] **Step 1: Confirm the current override is broken**

Run:
```bash
docker compose \
  -f deploy/docker-compose.yml \
  -f deploy/observability-elastic/docker-compose.override.yml \
  --env-file deploy/.env \
  config --services
```
Expected: FAIL with `service "community-app" has neither an image nor a build context specified` or the equivalent invalid-project error, proving the override still targets nonexistent service names.

- [ ] **Step 2: Rewrite the override to target the real backend replica services**

Replace `deploy/observability-elastic/docker-compose.override.yml` with:
```yaml
services:
  community-app-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  community-app-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  community-app-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  community-gateway-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  community-gateway-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  community-gateway-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  im-core-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  im-core-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  im-core-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  im-realtime-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  im-realtime-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export

  im-realtime-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
```

- [ ] **Step 3: Re-run compose validation on the old entrypoint**

Run:
```bash
docker compose \
  -f deploy/docker-compose.yml \
  -f deploy/observability-elastic/docker-compose.override.yml \
  --env-file deploy/.env \
  config --services
```
Expected: PASS and list the existing services without adding phantom `community-app` / `community-gateway` / `im-core` / `im-realtime` services.

- [ ] **Step 4: Commit**

```bash
git add deploy/observability-elastic/docker-compose.override.yml
git commit -m "fix(deploy): target real backend replicas in elastic override"
```

---

### Task 2: Introduce The New Layered Compose Files And Stable `make` Entry Points

**Files:**
- Create: `deploy/compose.yml`
- Create: `deploy/compose.infra.yml`
- Create: `deploy/compose.runtime.yml`
- Create: `deploy/compose.debug.yml`
- Create: `deploy/compose.observability.yml`
- Create: `deploy/compose.observability-elastic.yml`
- Create: `deploy/compose.json-logs.override.yml`
- Create: `Makefile`
- Test: `deploy/docker-compose.yml`

- [ ] **Step 1: Confirm the new layered entrypoint does not exist yet**

Run:
```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  config
```
Expected: FAIL with a missing-file error for `deploy/compose.yml`.

- [ ] **Step 2: Create `deploy/compose.yml` with the project name and all named volumes**

Create `deploy/compose.yml` with:
```yaml
name: community

volumes:
  mysql_primary_data: {}
  mysql_replica_1_data: {}
  mysql_replica_2_data: {}
  redis_1_data: {}
  redis_2_data: {}
  redis_3_data: {}
  redis_4_data: {}
  redis_5_data: {}
  redis_6_data: {}
  user_files: {}
  kafka_1_data: {}
  kafka_2_data: {}
  kafka_3_data: {}
  es_1_data: {}
  es_2_data: {}
  es_3_data: {}
  prometheus_data: {}
  grafana_data: {}
  loki_data: {}
  observability_logs: {}
```

- [ ] **Step 3: Create `deploy/compose.debug.yml` with the three localhost-only sidecars**

Create `deploy/compose.debug.yml` with:
```yaml
services:
  community-app-debug-port:
    image: caddy:2.8-alpine
    container_name: community-app-debug-port
    command: ["caddy", "reverse-proxy", "--from", ":8080", "--to", "community-app-1:8080"]
    depends_on:
      community-app-1:
        condition: service_started
    ports:
      - "127.0.0.1:12882:8080"

  im-core-debug-port:
    image: caddy:2.8-alpine
    container_name: im-core-debug-port
    command: ["caddy", "reverse-proxy", "--from", ":18082", "--to", "im-core-1:18082"]
    depends_on:
      im-core-1:
        condition: service_started
    ports:
      - "127.0.0.1:18082:18082"

  im-realtime-debug-port:
    image: caddy:2.8-alpine
    container_name: im-realtime-debug-port
    command: ["caddy", "reverse-proxy", "--from", ":18081", "--to", "im-realtime-1:18081"]
    depends_on:
      im-realtime-1:
        condition: service_started
    ports:
      - "127.0.0.1:18081:18081"
```

- [ ] **Step 4: Create the two observability overlay files**

Create `deploy/compose.observability.yml` with the exact service blocks currently living under the `observability` profile in `deploy/docker-compose.yml`:
```yaml
services:
  prometheus:
    image: prom/prometheus:v2.51.2
    container_name: community-prometheus
    ports:
      - "127.0.0.1:${PROMETHEUS_PORT:-12885}:9090"
    volumes:
      - ./observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./observability/alerts.yml:/etc/prometheus/alerts.yml:ro
      - prometheus_data:/prometheus
    depends_on:
      - community-app-1

  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: community-alertmanager
    ports:
      - "127.0.0.1:${ALERTMANAGER_PORT:-12886}:9093"
    volumes:
      - ./observability/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
    command:
      - "--config.file=/etc/alertmanager/alertmanager.yml"

  loki:
    image: grafana/loki:2.9.4
    container_name: community-loki
    ports:
      - "127.0.0.1:${LOKI_PORT:-12884}:3100"
    volumes:
      - ./observability/loki-config.yml:/etc/loki/config.yml:ro
      - loki_data:/loki
    command: ["-config.file=/etc/loki/config.yml"]

  promtail:
    image: grafana/promtail:2.9.4
    container_name: community-promtail
    volumes:
      - ./observability/promtail-config.yml:/etc/promtail/config.yml:ro
      - observability_logs:/var/log/community:ro
    command: ["-config.file=/etc/promtail/config.yml"]
    depends_on:
      - loki

  grafana:
    image: grafana/grafana:10.4.5
    container_name: community-grafana
    ports:
      - "127.0.0.1:${GRAFANA_PORT:-12883}:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./observability/grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - prometheus
      - loki
```

Create `deploy/compose.observability-elastic.yml` with:
```yaml
services:
  elasticsearch-observability-port:
    image: caddy:2.8-alpine
    container_name: community-elasticsearch-observability-port
    command: ["caddy", "reverse-proxy", "--from", ":9200", "--to", "elasticsearch:9200"]
    depends_on:
      elasticsearch-1:
        condition: service_started
    ports:
      - "127.0.0.1:${ELASTICSEARCH_PORT:-12888}:9200"

  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.2
    container_name: community-kibana
    ports:
      - "127.0.0.1:${KIBANA_PORT:-12889}:5601"
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    depends_on:
      elasticsearch-1:
        condition: service_started

  observability-gateway-edot-collector:
    image: docker.elastic.co/elastic-agent/elastic-otel-collector:9.3.2
    container_name: community-observability-gateway-edot-collector
    user: "0:0"
    command: ["--config=/etc/otelcol/config.yaml"]
    volumes:
      - ./observability-elastic/edot-collector.yml:/etc/otelcol/config.yaml:ro
      - observability_logs:/var/log/community:ro
    depends_on:
      elasticsearch-1:
        condition: service_started
    restart: unless-stopped
```

- [ ] **Step 5: Create `deploy/compose.json-logs.override.yml` as the permanent backend stdout JSON override**

Create `deploy/compose.json-logs.override.yml` with the same service list introduced in Task 1:
```yaml
services:
  community-app-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  community-app-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  community-app-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  community-gateway-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  community-gateway-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  community-gateway-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  im-core-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  im-core-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  im-core-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  im-realtime-1:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  im-realtime-2:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
  im-realtime-3:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},json-logs,volume-log-export
```

- [ ] **Step 6: Create `deploy/compose.infra.yml` by moving the always-on infrastructure blocks out of `deploy/docker-compose.yml`**

Create `deploy/compose.infra.yml` and copy these service blocks verbatim from `deploy/docker-compose.yml`, preserving env vars, health checks, `depends_on`, aliases, ports, and volume mounts:
```yaml
services:
  mysql-primary:   # copy the existing full service block
  mysql-replica-1: # copy the existing full service block
  mysql-replica-2: # copy the existing full service block
  mysql-replication-bootstrap:
  redis-1:
  redis-2:
  redis-3:
  redis-4:
  redis-5:
  redis-6:
  redis-cluster-bootstrap:
  xxl-job-admin-1:
  xxl-job-admin-2:
  nacos:
  mailhog:
  kafka-1:
  kafka-2:
  kafka-3:
  kafka-init:
  elasticsearch-1:
  elasticsearch-2:
  elasticsearch-3:
  es-init:
  mock-data-studio-db-bootstrap:
```
Exact rule: do not change any runtime values in these copied blocks during this step; the only change is file placement.

- [ ] **Step 7: Create `deploy/compose.runtime.yml` with shared anchors and all runtime services**

Create `deploy/compose.runtime.yml`. Put the repeated backend service definitions behind four anchors:
```yaml
x-community-app-base: &community-app-base
  build:
    context: ../backend
    dockerfile: ../deploy/Dockerfile.backend-service
    args:
      MODULE: community-app
      OTEL_JAVA_AGENT_VERSION: ${OTEL_JAVA_AGENT_VERSION:-2.23.0}
  mem_limit: ${COMMUNITY_APP_MEM_LIMIT:-768m}
  volumes:
    - user_files:/data/files
    - observability_logs:/var/log/community

x-community-gateway-base: &community-gateway-base
  build:
    context: ../backend
    dockerfile: ../deploy/Dockerfile.backend-service
    args:
      MODULE: community-gateway
      OTEL_JAVA_AGENT_VERSION: ${OTEL_JAVA_AGENT_VERSION:-2.23.0}
  mem_limit: ${COMMUNITY_GATEWAY_MEM_LIMIT:-512m}
  volumes:
    - observability_logs:/var/log/community

x-im-core-base: &im-core-base
  build:
    context: ../backend
    dockerfile: ../deploy/Dockerfile.backend-service
    args:
      MODULE: im-core
      OTEL_JAVA_AGENT_VERSION: ${OTEL_JAVA_AGENT_VERSION:-2.23.0}
  mem_limit: ${IM_CORE_MEM_LIMIT:-640m}
  volumes:
    - observability_logs:/var/log/community

x-im-realtime-base: &im-realtime-base
  build:
    context: ../backend
    dockerfile: ../deploy/Dockerfile.backend-service
    args:
      MODULE: im-realtime
      OTEL_JAVA_AGENT_VERSION: ${OTEL_JAVA_AGENT_VERSION:-2.23.0}
  mem_limit: ${IM_REALTIME_MEM_LIMIT:-640m}
  volumes:
    - observability_logs:/var/log/community
```

Then move these services from `deploy/docker-compose.yml` into `deploy/compose.runtime.yml`, preserving all runtime env vars, `depends_on`, ports, aliases, and service-specific differences:
```yaml
services:
  frontend:
  nginx:
  community-app-1:
    <<: *community-app-base
  community-app-2:
    <<: *community-app-base
  community-app-3:
    <<: *community-app-base
  community-gateway-1:
    <<: *community-gateway-base
  community-gateway-2:
    <<: *community-gateway-base
  community-gateway-3:
    <<: *community-gateway-base
  im-core-1:
    <<: *im-core-base
  im-core-2:
    <<: *im-core-base
  im-core-3:
    <<: *im-core-base
  im-realtime-1:
    <<: *im-realtime-base
  im-realtime-2:
    <<: *im-realtime-base
  im-realtime-3:
    <<: *im-realtime-base
  mock-data-studio:
```
Replica-specific values that must remain explicit:
- `COMMUNITY_LOGGING_FILE_NAME`
- `XXL_JOB_EXECUTOR_ADDRESS`
- `IM_REALTIME_CONSUMER_GROUP`
- `IM_REALTIME_WORKER_ID`
- the three network aliases on `community-app-1`, `community-gateway-1`, `im-core-1`, `im-realtime-1`

- [ ] **Step 8: Add `Makefile` with stable layered compose commands**

Create `Makefile` with:
```make
COMPOSE_BASE = docker compose --env-file deploy/.env \
	-f deploy/compose.yml \
	-f deploy/compose.infra.yml \
	-f deploy/compose.runtime.yml

.PHONY: up up-debug up-obs up-elastic up-elastic-json down ps logs config config-debug config-obs config-elastic config-elastic-json

up:
	$(COMPOSE_BASE) up -d --build

up-debug:
	$(COMPOSE_BASE) -f deploy/compose.debug.yml up -d --build

up-obs:
	$(COMPOSE_BASE) -f deploy/compose.observability.yml up -d --build

up-elastic:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml up -d --build

up-elastic-json:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml -f deploy/compose.json-logs.override.yml up -d --build

down:
	$(COMPOSE_BASE) down

ps:
	$(COMPOSE_BASE) ps

logs:
	$(COMPOSE_BASE) logs -f --tail=200

config:
	$(COMPOSE_BASE) config

config-debug:
	$(COMPOSE_BASE) -f deploy/compose.debug.yml config

config-obs:
	$(COMPOSE_BASE) -f deploy/compose.observability.yml config

config-elastic:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml config

config-elastic-json:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml -f deploy/compose.json-logs.override.yml config
```

- [ ] **Step 9: Validate the new layered stack without deleting legacy files yet**

Run:
```bash
make config
make config-debug
make config-obs
make config-elastic
make config-elastic-json
```
Expected: all five commands PASS.

Run:
```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  config --services
```
Expected: PASS and list the always-on services without the optional overlay services.

- [ ] **Step 10: Commit**

```bash
git add deploy/compose.yml \
        deploy/compose.infra.yml \
        deploy/compose.runtime.yml \
        deploy/compose.debug.yml \
        deploy/compose.observability.yml \
        deploy/compose.observability-elastic.yml \
        deploy/compose.json-logs.override.yml \
        Makefile
git commit -m "refactor(deploy): add layered compose files and make entrypoints"
```

---

### Task 3: Migrate The Primary Docs To The New Layered Commands

**Files:**
- Modify: `README.md`
- Modify: `backend/README.md`
- Modify: `deploy/README.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/OBSERVABILITY.md`
- Modify: `deploy/observability-elastic/kibana/README.md`
- Test: `Makefile`

- [ ] **Step 1: Capture the current primary docs still pointing at the legacy compose path**

Run:
```bash
rg -n "deploy/docker-compose.yml|COMPOSE_PROFILES=|observability-elastic/docker-compose.override.yml" \
  README.md \
  backend/README.md \
  deploy/README.md \
  docs/DEPLOYMENT.md \
  docs/OBSERVABILITY.md \
  deploy/observability-elastic/kibana/README.md
```
Expected: multiple matches across all six files.

- [ ] **Step 2: Rewrite the primary docs to lead with `make` and layered `-f` commands**

Apply these exact content changes:

- In `README.md`, replace the current quick-start commands with:
```md
```bash
make up
```

可选：

- 观测：`make up-obs`
- Elastic 观测：`make up-elastic`
- Elastic 观测 + backend stdout JSON：`make up-elastic-json`
- 调试直连端口：`make up-debug`
```

- In `backend/README.md`, change:
```md
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```
to:
```md
make up
```

- In `deploy/README.md`, replace:
  - `docker-compose.yml` file description with the new `compose.yml` / `compose.infra.yml` / `compose.runtime.yml` / overlay file descriptions
  - all `COMPOSE_PROFILES=... docker compose -f deploy/docker-compose.yml ...` examples with `make up-*` or explicit layered `-f` commands
  - all references to `deploy/observability-elastic/docker-compose.override.yml` with `deploy/compose.json-logs.override.yml`

- In `docs/DEPLOYMENT.md`, update Section 2 to describe “基础 compose + overlay 文件” instead of “基础 compose + profile”, and replace all start / stop / reset commands with either `make` or the layered `-f` commands.

- In `docs/OBSERVABILITY.md` and `deploy/observability-elastic/kibana/README.md`, replace the old Elastic commands with:
```md
make up-elastic
make up-elastic-json
```
and keep the explicit layered command only as the lower-level equivalent:
```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.yml \
  -f deploy/compose.runtime.yml \
  -f deploy/compose.observability-elastic.yml \
  --env-file deploy/.env \
  up -d --build
```

- [ ] **Step 3: Verify the primary docs no longer advertise the legacy path**

Run:
```bash
rg -n "deploy/docker-compose.yml|COMPOSE_PROFILES=|observability-elastic/docker-compose.override.yml" \
  README.md \
  backend/README.md \
  deploy/README.md \
  docs/DEPLOYMENT.md \
  docs/OBSERVABILITY.md \
  deploy/observability-elastic/kibana/README.md
```
Expected: no matches.

Run:
```bash
make config
make config-elastic-json
```
Expected: PASS, confirming the documented entrypoints are valid.

- [ ] **Step 4: Commit**

```bash
git add README.md \
        backend/README.md \
        deploy/README.md \
        docs/DEPLOYMENT.md \
        docs/OBSERVABILITY.md \
        deploy/observability-elastic/kibana/README.md
git commit -m "docs(deploy): switch primary operator docs to layered compose commands"
```

---

### Task 4: Remove The Legacy Compose Files And Update Remaining References

**Files:**
- Delete: `deploy/docker-compose.yml`
- Delete: `deploy/observability-elastic/docker-compose.override.yml`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/LOCAL_HA.md`
- Modify: `docs/LOAD_TESTING.md`
- Modify: `docs/SECURITY.md`
- Modify: `docs/DATA_MODEL.md`
- Modify: `tools/im-load/README.md`
- Test: `Makefile`

- [ ] **Step 1: Capture the residual references to the legacy files**

Run:
```bash
rg -n "deploy/docker-compose.yml|observability-elastic/docker-compose.override.yml|COMPOSE_PROFILES=" \
  docs/ARCHITECTURE.md \
  docs/LOCAL_HA.md \
  docs/LOAD_TESTING.md \
  docs/SECURITY.md \
  docs/DATA_MODEL.md \
  tools/im-load/README.md
```
Expected: matches in all or most of the listed files.

- [ ] **Step 2: Update the remaining runbooks and references**

Make these exact replacements:

- In `docs/ARCHITECTURE.md`, replace the “`deploy/docker-compose.yml` is the local HA stack” wording with:
```md
`deploy/compose.yml` + `deploy/compose.infra.yml` + `deploy/compose.runtime.yml` 组成默认本地 HA 栈；
`deploy/compose.debug.yml`、`deploy/compose.observability.yml`、`deploy/compose.observability-elastic.yml`、
`deploy/compose.json-logs.override.yml` 作为可选 overlay 叠加。
```

- In `docs/LOCAL_HA.md`, replace every `docker compose -f deploy/docker-compose.yml --env-file deploy/.env ...` with:
```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  ...
```

- In `docs/LOAD_TESTING.md` and `tools/im-load/README.md`, replace the base stack start command with `make up`, and replace the old debug command with `make up-debug`.

- In `docs/SECURITY.md`, replace the `COMPOSE_PROFILES=observability` guidance with `make up-obs`.

- In `docs/DATA_MODEL.md`, replace “由 `deploy/docker-compose.yml` 的 `kafka-init` / `es-init` 创建” with “由默认 layered compose 栈中的 `kafka-init` / `es-init` 创建”.

- [ ] **Step 3: Delete the legacy compose entry files once the docs no longer depend on them**

Delete:
```text
deploy/docker-compose.yml
deploy/observability-elastic/docker-compose.override.yml
```

- [ ] **Step 4: Run the full validation matrix on the final layout**

Run:
```bash
make config
make config-debug
make config-obs
make config-elastic
make config-elastic-json
```

Run:
```bash
rg -n "deploy/docker-compose.yml|observability-elastic/docker-compose.override.yml|COMPOSE_PROFILES=" \
  README.md \
  backend/README.md \
  deploy/README.md \
  deploy/observability-elastic/kibana/README.md \
  docs/DEPLOYMENT.md \
  docs/OBSERVABILITY.md \
  docs/ARCHITECTURE.md \
  docs/LOCAL_HA.md \
  docs/LOAD_TESTING.md \
  docs/SECURITY.md \
  docs/DATA_MODEL.md \
  tools/im-load/README.md
```
Expected: no matches.

Run:
```bash
git status --short
```
Expected: only the intended compose, Makefile, and docs changes remain.

- [ ] **Step 5: Commit**

```bash
git add Makefile \
        deploy/compose.yml \
        deploy/compose.infra.yml \
        deploy/compose.runtime.yml \
        deploy/compose.debug.yml \
        deploy/compose.observability.yml \
        deploy/compose.observability-elastic.yml \
        deploy/compose.json-logs.override.yml \
        README.md \
        backend/README.md \
        deploy/README.md \
        deploy/observability-elastic/kibana/README.md \
        docs/DEPLOYMENT.md \
        docs/OBSERVABILITY.md \
        docs/ARCHITECTURE.md \
        docs/LOCAL_HA.md \
        docs/LOAD_TESTING.md \
        docs/SECURITY.md \
        docs/DATA_MODEL.md \
        tools/im-load/README.md \
        deploy/docker-compose.yml \
        deploy/observability-elastic/docker-compose.override.yml
git commit -m "refactor(deploy): switch repo to layered compose files"
```

---

## Self-Review

### Spec Coverage

- File split covered by Task 2 and Task 4.
- Stable operator entrypoint covered by Task 2 and Task 3.
- JSON logs override fix covered by Task 1 and Task 2.
- Docs and migration covered by Task 3 and Task 4.
- Validation matrix covered by Task 2 and Task 4.

No spec sections are left without a task.

### Placeholder Scan

- No open markers or unnamed files remain.
- Every task names exact files and exact commands.
- Validation commands are concrete and executable.

### Type / Naming Consistency

- Permanent compose files use the final names from the spec:
  - `compose.yml`
  - `compose.infra.yml`
  - `compose.runtime.yml`
  - `compose.debug.yml`
  - `compose.observability.yml`
  - `compose.observability-elastic.yml`
  - `compose.json-logs.override.yml`
- The old Elastic override path is only used in Task 1 because that is the current file being repaired before the new permanent path is introduced.
