# Single-Compose One-Click Startup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `deploy/docker-compose.yml` the *only* required compose file to start the business-required stack (frontend + community-app + im-core + im-realtime + mysql + redis + kafka + elasticsearch), while keeping observability services opt-in via a compose profile.

**Architecture:** Keep a single “business-required” base compose that exposes only the necessary entry ports (`12881/12882/18081/18082`) and keeps infra dependencies (MySQL/Redis/Kafka/ES) internal. Put Prometheus/Grafana/Loki/Promtail/Alertmanager behind the `observability` profile so observability is opt-in (and host ports are bound to `127.0.0.1`).

**Tech Stack:** Docker Compose v2, Dockerfiles in `deploy/` (`deploy/Dockerfile.frontend`, `deploy/Dockerfile.spring-service`).

---

### Task 1: Inspect current deploy compose topology

**Files:**
- Read: `deploy/docker-compose.yml`

**Step 1: Confirm current compose parses**

Run (from repo root): `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`

Expected: exit code `0`.

---

### Task 2: Refactor base compose to include “one-click” business stack

**Files:**
- Modify: `deploy/docker-compose.yml`

**Step 1: Add `frontend` service (copied from the old overlay)**

Add a service:
- `frontend` built from repo root (`context: ..`) with `dockerfile: deploy/Dockerfile.frontend`
- `container_name: community-frontend`
- expose port `12881:12881`
- `depends_on: [community-app]`

**Step 2: Expose required business ports**

Add `ports:` mappings:
- `community-app`: `12882:8080`
- `im-core`: `18082:18082`
- `im-realtime`: `18081:18081`

**Step 3: Remove observability services from the base compose**

Delete these service definitions from `deploy/docker-compose.yml`:
- `prometheus`
- `alertmanager`
- `loki`
- `promtail`
- `grafana`

Also remove their volumes from the base compose:
- `prometheus_data`
- `grafana_data`
- `loki_data`

---

### Task 3: Add optional observability stack via profiles

**Files:**
- Modify: `deploy/docker-compose.yml`

**Step 1: Add observability services under the `observability` profile**

Add these services with `profiles: [observability]`:
- `prometheus`
- `alertmanager`
- `loki`
- `promtail`
- `grafana`

**Step 2: Keep host-only port mappings**

Bind to localhost only:
- `grafana`: `127.0.0.1:${GRAFANA_PORT:-12883}:3000`
- `loki`: `127.0.0.1:${LOKI_PORT:-12884}:3100`
- `prometheus`: `127.0.0.1:${PROMETHEUS_PORT:-12885}:9090`
- `alertmanager`: `127.0.0.1:${ALERTMANAGER_PORT:-12886}:9093`

**Step 3: Declare observability volumes in the base compose**

Add in `deploy/docker-compose.yml`:
- `prometheus_data`
- `grafana_data`
- `loki_data`

---

### Task 4: Remove deprecated overlay compose files

**Files:**
- Ensure removed: legacy “frontend-direct” and “observability ports” overlay compose files

---

### Task 5: Update documentation to use the new compose layout

**Files:**
- Modify: `README.md`
- Modify: `backend/README.md`
- Modify: `deploy/README.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/OBSERVABILITY.md`
- Modify: `docs/LOAD_TESTING.md`
- Modify: `tools/im-load/README.md`

**Step 1: Replace “base + overlay” commands with the single base compose**

Replace any multi-file “local entry” compose invocation (previously used to add frontend + port mappings) with:

With:
- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`

**Step 2: Document observability as opt-in**

Add optional command:
- Set `COMPOSE_PROFILES=observability` in `deploy/.env`, then run: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`

**Step 3: Keep MailHog as an optional overlay**

MailHog command becomes:
- (No longer needed) MailHog is built into `deploy/docker-compose.yml` by default.

---

### Task 6: Validate compose after refactor

**Files:**
- (none)

**Step 1: Validate base compose config**

Run: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`

Expected: exit code `0`.

**Step 2: Validate base + observability**

Run: `COMPOSE_PROFILES=observability docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`

Expected: exit code `0`.

**Step 3: Optional smoke boot (local dev)**

Run: `cp deploy/.env.example deploy/.env`

Then: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`

Optional observability: set `COMPOSE_PROFILES=observability` in `deploy/.env`, then run the normal command.
