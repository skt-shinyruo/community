# XXL-JOB Distributed Tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce XXL-JOB as the first distributed task control plane for `community-app`, migrate pending registration cleanup and search reindex into executor handlers, and keep the rest of the system on its current local worker model.

**Architecture:** Add a small `infra.job` adapter layer inside `community-app` that owns XXL-JOB properties, executor bootstrapping, and handler entrypoints. Keep business logic in existing services by routing cleanup to `InternalUserService.cleanupExpiredPendingUsers(...)` and reindex through a shared orchestration service that both the HTTP ops endpoint and XXL handler can reuse. For local compose, seed `xxl_job` schema metadata so the admin UI comes up with one executor group and the two first-phase jobs already visible.

**Tech Stack:** Java 17, Spring Boot 3.2, official `xxl-job-core` 3.x dependency, Spring scheduling, MyBatis, MySQL 8 init scripts, Docker Compose, JUnit 5, Mockito, Maven

---

## File Structure Map

### Backend executor integration

- `backend/community-app/pom.xml`
  Role: add the pinned `xxl-job-core` dependency used by the executor.
- `backend/community-app/src/main/resources/application.yml`
- `backend/community-app/src/test/resources/application.yml`
  Role: define default `xxl.job.*` settings, keep XXL disabled by default, and control the local cleanup scheduler switch in tests.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobProperties.java`
  Role: bind the executor/admin settings the app owns explicitly.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobAutoConfiguration.java`
  Role: conditionally create the XXL executor bean, validate required config when enabled, and let job-handler beans register only in enabled environments.

### Pending registration cleanup migration

- `backend/community-app/src/main/java/com/nowcoder/community/auth/config/RegistrationProperties.java`
  Role: add the local scheduler enable/disable switch under `auth.registration.pending-user`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJob.java`
  Role: remain as the local-dev fallback scheduler only when the switch is on.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/PendingRegistrationUserCleanupHandler.java`
  Role: XXL handler that delegates to the existing cleanup service and emits job logs/results.

### Search reindex migration

- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
  Role: shared orchestration for try-start / run / finish semantics that returns an execution result usable by both HTTP and XXL entrypoints.
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchAdminService.java`
  Role: keep HTTP-facing behavior, but delegate to the shared execution service and preserve conflict-as-error semantics for `/api/ops/search/reindex`.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/SearchReindexHandler.java`
  Role: XXL handler that reuses the same execution service and treats “already running” as skip instead of failure.

### Local compose admin control plane

- `deploy/docker-compose.yml`
  Role: add `xxl-job-admin`, wire the executor env into `community-app`, and keep the admin UI reachable from the host.
- `deploy/.env.example`
  Role: document the new XXL env knobs and secure local defaults.
- `deploy/README.md`
  Role: explain how to start, access, and verify the new admin service.
- `deploy/mysql-init/001_create_databases.sh`
  Role: create the `xxl_job` schema and least-privilege DB user alongside existing schemas.
- `deploy/mysql-init/020_xxl_job_schema.sql`
  Role: vendor the upstream XXL table DDL without keeping the upstream weak default seed data.
- `deploy/mysql-init/021_seed_xxl_job.sh`
  Role: seed one admin user, one executor group, one scheduled cleanup job, and one manual reindex job using compose env values instead of upstream weak defaults.
- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
  Role: record that the project now mixes XXL-driven discrete jobs with retained local outbox/post-score workers by design.

### Tests

- `backend/community-app/src/test/java/com/nowcoder/community/infra/job/XxlJobAutoConfigurationTest.java`
  Role: prove the executor stays off when disabled and fails fast or boots correctly when enabled.
- `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/PendingRegistrationUserCleanupHandlerTest.java`
  Role: prove the XXL cleanup handler delegates to the existing service with TTL-derived duration.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJobTest.java`
  Role: extend local fallback coverage so the scheduled path is a no-op when disabled.
- `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`
  Role: prove acquired/skip/failure/release semantics for the shared reindex orchestration.
- `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/SearchReindexHandlerTest.java`
  Role: prove the XXL handler reports skip for conflict and success for actual reindex runs.
- `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OpsControllerTest.java`
  Role: keep the existing HTTP ops contract stable after the reindex orchestration refactor.

---

## Task 1: Add The XXL Executor Dependency, Properties, And Conditional Auto-Configuration

**Files:**
- Modify: `backend/community-app/pom.xml`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/resources/application.yml`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobProperties.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/XxlJobAutoConfiguration.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/XxlJobAutoConfigurationTest.java`

- [ ] **Step 1: Write failing auto-configuration tests for the disabled and enabled paths**

  Cover:
  - `xxl.job.enabled=false` keeps the executor bean out of the context
  - `xxl.job.enabled=true` plus missing required admin/appname config fails fast
  - `xxl.job.enabled=true` plus complete config creates the executor bean without needing a live admin server

- [ ] **Step 2: Run the targeted auto-configuration test and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=XxlJobAutoConfigurationTest test`

- [ ] **Step 3: Add the dependency and minimal XXL config surface**

  Implement:
  - pin the official `xxl-job-core` 3.x dependency in `backend/community-app/pom.xml`
  - add `xxl.job.enabled`, `xxl.job.admin.addresses`, `xxl.job.admin.accessToken`, `xxl.job.executor.appname`, and `xxl.job.executor.address` support
  - keep executor-only implementation details such as port/log path as app defaults unless compose must override them
  - create `XxlJobAutoConfiguration` that validates required properties only when XXL is enabled

- [ ] **Step 4: Re-run the targeted auto-configuration test and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=XxlJobAutoConfigurationTest test`

- [ ] **Step 5: Checkpoint the diff for executor bootstrapping**

  Note: do not create a git commit unless the user explicitly asks for one.

## Task 2: Migrate Pending Registration Cleanup To A Switchable Local Scheduler Plus XXL Handler

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/config/RegistrationProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJob.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/PendingRegistrationUserCleanupHandler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/PendingRegistrationUserCleanupHandlerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJobTest.java`

- [ ] **Step 1: Write failing tests for XXL delegation and the local scheduler switch**

  Cover:
  - the XXL cleanup handler uses the configured pending-user TTL and delegates exactly once to `InternalUserService.cleanupExpiredPendingUsers(...)`
  - the local scheduled job becomes a no-op when `local-scheduler-enabled=false`
  - the existing local scheduled path still delegates when the switch is true

- [ ] **Step 2: Run the focused cleanup tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=PendingRegistrationUserCleanupJobTest,PendingRegistrationUserCleanupHandlerTest test`

- [ ] **Step 3: Implement the scheduler switch and the XXL cleanup handler**

  Implement:
  - `auth.registration.pending-user.local-scheduler-enabled` in `RegistrationProperties`
  - a guard in `PendingRegistrationUserCleanupJob.cleanup()` so local fallback only runs when explicitly enabled
  - `PendingRegistrationUserCleanupHandler` with the XXL job name `pendingRegistrationUserCleanup`
  - handler logging/result text that includes the deleted-count for admin visibility

- [ ] **Step 4: Re-run the focused cleanup tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=PendingRegistrationUserCleanupJobTest,PendingRegistrationUserCleanupHandlerTest test`

- [ ] **Step 5: Checkpoint the diff for cleanup migration**

  Note: do not create a git commit unless the user explicitly asks for one.

## Task 3: Extract A Shared Reindex Execution Path And Add The XXL Reindex Handler

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchAdminService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/SearchReindexHandler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/SearchReindexHandlerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OpsControllerTest.java`

- [ ] **Step 1: Write failing tests for acquired, skipped, and failed reindex execution**

  Cover:
  - when `ReindexJobService.tryStart()` acquires the slot, the shared execution service runs `PostSearchService.clearAndReindexFromContentService()` and always releases the job id
  - when `tryStart()` reports an existing job, the shared execution service returns a skipped/conflict result instead of throwing
  - the XXL handler maps skipped/conflict to a non-failing job outcome and maps true business failures to failure
  - the HTTP ops path still returns the same success payload and still treats conflict as an error

- [ ] **Step 2: Run the targeted reindex tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=SearchReindexExecutionServiceTest,SearchReindexHandlerTest,OpsControllerTest test`

- [ ] **Step 3: Implement the shared orchestration service and XXL handler**

  Implement:
  - `SearchReindexExecutionService` that owns `tryStart -> execute -> finish`
  - a small execution result model that carries `jobId`, `indexedCount`, and `skipped`/`reason`
  - `SearchAdminService.reindex()` rewritten as an adapter over the shared execution service so HTTP behavior stays unchanged
  - `SearchReindexHandler` with the XXL job name `searchReindex`, using skip semantics for “already running”

- [ ] **Step 4: Re-run the targeted reindex tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=SearchReindexExecutionServiceTest,SearchReindexHandlerTest,OpsControllerTest,ReindexJobServiceTest test`

- [ ] **Step 5: Checkpoint the diff for reindex migration**

  Note: do not create a git commit unless the user explicitly asks for one.

## Task 4: Add The Local XXL Admin Control Plane, Seeded Jobs, And Operator Documentation

**Files:**
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`
- Modify: `deploy/README.md`
- Modify: `deploy/mysql-init/001_create_databases.sh`
- Create: `deploy/mysql-init/020_xxl_job_schema.sql`
- Create: `deploy/mysql-init/021_seed_xxl_job.sh`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`

- [ ] **Step 1: Add the admin schema and secure seed scripts**

  Implement:
  - create the `xxl_job` schema and DB user in `001_create_databases.sh`
  - vendor the upstream XXL table DDL into `020_xxl_job_schema.sql`
  - strip upstream weak default user/group/job seed data from the vendored SQL
  - add `021_seed_xxl_job.sh` that inserts:
    - one admin account using env-driven credentials instead of upstream `admin/123456`
    - one executor group using the same app name as the `community-app` executor
    - one CRON-driven `pendingRegistrationUserCleanup` job
    - one manual `searchReindex` job with `schedule_type=NONE`

- [ ] **Step 2: Wire compose and env defaults for the admin plus executor registration**

  Implement:
  - add `xxl-job-admin` to `deploy/docker-compose.yml`
  - wire `community-app` env for `XXL_JOB_ENABLED`, `XXL_JOB_ADMIN_ADDRESSES`, `XXL_JOB_EXECUTOR_APPNAME`, `XXL_JOB_EXECUTOR_ADDRESS`, and `XXL_JOB_ACCESS_TOKEN`
  - add secure local defaults to `.env.example`, including admin login, access token, and cleanup CRON
  - bind the admin UI to a localhost-only port and keep executor traffic on the compose network

- [ ] **Step 3: Verify the deployment artifacts before touching docs**

  Run:
  - `bash -n deploy/mysql-init/001_create_databases.sh`
  - `bash -n deploy/mysql-init/021_seed_xxl_job.sh`
  - `cp deploy/.env.example deploy/.env`
  - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env config >/tmp/community-xxl-job-compose.yaml`

- [ ] **Step 4: Update deployment and architecture docs**

  Document:
  - where the admin UI is exposed
  - how the seeded cleanup and reindex jobs appear in the admin
  - why `PostScoreRefresher` and `OutboxWorkerScheduler` still remain local workers
  - that only `community-app` is an executor in phase 1

- [ ] **Step 5: Checkpoint the diff for deployment and docs wiring**

  Note: do not create a git commit unless the user explicitly asks for one.

## Task 5: Run Cross-Layer Verification And Local Compose Smoke Checks

**Files:**
- Verify only

- [ ] **Step 1: Run the focused backend suites for XXL integration**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=XxlJobAutoConfigurationTest,PendingRegistrationUserCleanupJobTest,PendingRegistrationUserCleanupHandlerTest,SearchReindexExecutionServiceTest,SearchReindexHandlerTest,OpsControllerTest,ReindexJobServiceTest test`

- [ ] **Step 2: Run the broader backend regression suite**

  Run:
  - `cd backend && mvn -pl community-app test`

- [ ] **Step 3: Start the local compose stack and verify executor registration**

  Run:
  - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
  - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps xxl-job-admin community-app`
  - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs --tail=200 xxl-job-admin community-app`

  Check:
  - `xxl-job-admin` is healthy and reachable from the host
  - `community-app` starts with XXL enabled and logs executor registration

- [ ] **Step 4: Manually verify the two phase-1 jobs through the admin UI**

  Check:
  - the admin UI shows the seeded executor group and both job definitions
  - `pendingRegistrationUserCleanup` has a CRON schedule and can be triggered manually
  - `searchReindex` is visible as a manual job and triggering it does not break the existing HTTP ops endpoint semantics

- [ ] **Step 5: Record verification notes and checkpoint the full diff**

  Note:
  - capture any manual steps needed to log into the admin or adjust local env values
  - do not create a git commit unless the user explicitly asks for one.
