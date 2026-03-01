# Move docs/ + deploy/ to Repo Root Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move `backend/docs` → `docs` and `backend/deploy` → `deploy`, making repository root the default execution directory for docs and docker compose.

**Architecture:** Keep `backend/` as Maven multi-module root and `frontend/` as Vite app. Adjust docker compose build contexts so backend image builds from `backend/` and frontend image builds from repo root.

**Tech Stack:** Docker Compose v2, Maven multi-module (Java 17 / Spring Boot 3), Vite (Node 20).

---

### Task 1: Move documentation directory to repo root

**Files:**
- Move: `backend/docs/` → `docs/`

**Step 1: Move directory**

Run: `git mv backend/docs docs`

**Step 2: Update docs internal paths (repo-root convention)**

- Replace references like `docs/ARCHITECTURE.md` inside `docs/README.md` with repo-root-correct forms.
- Update code-path references from `app/...` to `backend/...`（例如 `backend/community-bootstrap/...`）, `platform/...` to `backend/platform/...`, etc.

**Step 3: Sanity check**

Run: `ls docs`
Expected: `ARCHITECTURE.md`, `DEPLOYMENT.md`, `OBSERVABILITY.md`, `SECURITY.md`, `SYSTEM_DESIGN.md`, `DATA_MODEL.md`, `README.md`, `plans/`

---

### Task 2: Move deploy directory to repo root

**Files:**
- Move: `backend/deploy/` → `deploy/`

**Step 1: Move directory**

Run: `git mv backend/deploy deploy`

**Step 2: Update docker compose paths**

- In `deploy/docker-compose.yml`, set backend image build:
  - `context: ../backend`
  - `dockerfile: ../deploy/Dockerfile.spring-service`
  - build arg `MODULE: community-bootstrap`
- In `deploy/docker-compose.frontend-direct.yml`, set frontend build:
  - `context: ..`
  - `dockerfile: deploy/Dockerfile.frontend`

**Step 3: Sanity check**

Run: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`
Expected: exit code `0`.

---

### Task 3: Update repo READMEs for new global entrypoints

**Files:**
- Modify: `README.md`
- Modify: `backend/README.md`
- Modify: `deploy/README.md`
- Modify: `docs/README.md`

**Step 1: Root quickstart uses repo root**

- Remove `cd backend`
- Use `cp deploy/.env.example deploy/.env`
- Use `docker compose -f deploy/...`

**Step 2: backend README points to root deploy/docs**

- Keep backend build/test instructions in `backend/`
- Refer users to `../deploy` and `../docs`

---

### Task 4: Update ignore rules for local dev secrets

**Files:**
- Modify: `.gitignore`

**Step 1: Add new ignore patterns**

- Add: `deploy/.env`, `deploy/.local/`, `deploy/backups/`
- Keep legacy ignores for `backend/deploy/*` to avoid surfacing existing local files as untracked.

---

### Task 5: Run quick verification commands

**Step 1: Compose config**

Run: `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env.example config >/dev/null`
Expected: exit code `0`.

**Step 2: Backend packaging (skip tests)**

Run: `cd backend && ./mvnw -q -DskipTests -pl :community-bootstrap -am package`
Expected: build succeeds and produces the bootstrap jar.
