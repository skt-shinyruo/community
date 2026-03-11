# Observability Profiles Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove `deploy/docker-compose.observability.yml` by merging observability services into `deploy/docker-compose.yml` behind a Compose profile (`observability`), enabled via `COMPOSE_PROFILES=observability` in `deploy/.env`.

**Architecture:** Keep a single base compose file. Observability stack is opt-in via `profiles`, with host ports bound to `127.0.0.1` for safety. Keep all configs under `deploy/observability/` unchanged.

**Tech Stack:** Docker Compose v2 profiles, Prometheus/Grafana/Loki/Promtail/Alertmanager.

---

### Task 1: Merge observability services into base compose under a profile

**Files:**
- Modify: `deploy/docker-compose.yml`

**Step 1: Copy services**

Copy these services from the old overlay into `deploy/docker-compose.yml`:
- `prometheus`
- `alertmanager`
- `loki`
- `promtail`
- `grafana`

**Step 2: Add profiles**

For each service, add:
- `profiles: [observability]`

**Step 3: Add volumes**

Add to `deploy/docker-compose.yml` top-level `volumes:`:
- `prometheus_data: {}`
- `grafana_data: {}`
- `loki_data: {}`

---

### Task 2: Delete the old overlay file

**Files:**
- Delete: `deploy/docker-compose.observability.yml`

---

### Task 3: Update env example and documentation

**Files:**
- Modify: `deploy/.env.example`
- Modify: `README.md`
- Modify: `deploy/README.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/OBSERVABILITY.md`

**Step 1: Document `COMPOSE_PROFILES`**

Add a commented example:
- `# COMPOSE_PROFILES=observability`

**Step 2: Replace overlay instructions**

Replace commands that use `-f deploy/docker-compose.observability.yml` with either:
- “set `COMPOSE_PROFILES=observability` in `deploy/.env` and run the normal command”
or
- add a `--profile observability` example as an alternative.

---

### Task 4: Validate compose config

**Step 1: Validate default config**

Run: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`
Expected: exit code `0`.

**Step 2: Validate observability profile**

Run: `COMPOSE_PROFILES=observability docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`
Expected: exit code `0`.

