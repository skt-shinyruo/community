# Default MailHog + SMTP in Base Compose Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make MailHog and SMTP-based onboarding the default local docker compose behavior, so `deploy/docker-compose.yml` starts a usable dev mailbox (`127.0.0.1:8025`) and the app sends activation/reset emails by default (without exposing sensitive links).

**Architecture:** Keep all changes configuration-only (compose/env/docs). Add `mailhog` service to the base compose and update `community-app` default env values to point Spring Mail to `mailhog:1025`. Update docs to reflect the new default and keep “expose link” as an explicit dev-only opt-in.

**Tech Stack:** Docker Compose v2, Spring Boot env binding (`SPRING_MAIL_*`), MailHog.

---

### Task 1: Add MailHog to the base docker compose

**Files:**
- Modify: `deploy/docker-compose.yml`

**Step 1: Add `mailhog` service**

Add a new service:
- `mailhog` with image `mailhog/mailhog:v1.0.1`
- `container_name: community-mailhog`
- Port mapping: `127.0.0.1:8025:8025`

**Step 2: Default-enable SMTP in `community-app`**

In `community-app.environment`:
- Change defaults:
  - `AUTH_MAIL_ENABLED` → default `true`
  - `AUTH_EXPOSE_ACTIVATION_LINK` → default `false`
  - `AUTH_EXPOSE_RESET_LINK` → default `false`
- Add:
  - `SPRING_MAIL_HOST=${SPRING_MAIL_HOST:-mailhog}`
  - `SPRING_MAIL_PORT=${SPRING_MAIL_PORT:-1025}`
  - `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=${SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH:-false}`
  - `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE:-false}`

**Step 3: Add `depends_on` for mailhog**

Add `mailhog` to `community-app.depends_on` (service_started is enough).

---

### Task 2: Update default env example

**Files:**
- Modify: `deploy/.env.example`

**Step 1: Switch onboarding defaults to MailHog mode**

Set:
- `AUTH_MAIL_ENABLED=true`
- `AUTH_EXPOSE_ACTIVATION_LINK=false`
- `AUTH_EXPOSE_RESET_LINK=false`

Optionally document how to flip back for “no SMTP” quick dev.

---

### Task 3: Update docs to match the new default

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DEV_ONLY.md`

**Step 1: Document MailHog UI**

Mention:
- MailHog UI: `http://localhost:8025` (host-only bound)

**Step 2: Replace “MailHog optional overlay” wording**

Update references that say MailHog is an optional overlay, clarifying it’s now built into `deploy/docker-compose.yml` by default.

**Step 3: Keep “expose link” as explicit opt-in**

Add a small section showing the env overrides for link exposure:
- `AUTH_MAIL_ENABLED=false`
- `AUTH_EXPOSE_ACTIVATION_LINK=true`
- `AUTH_EXPOSE_RESET_LINK=true`

---

### Task 4: Validate compose config

**Step 1: Base compose config**

Run (from repo root): `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`

Expected: exit code `0`.

**Step 2 (optional): Base compose up**

Run:
- `cp deploy/.env.example deploy/.env`
- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`

Expected:
- `community-mailhog` container is running
- MailHog UI is reachable at `http://localhost:8025`
