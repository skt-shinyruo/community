# Community Mock Data Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a localhost-only `mock-data-studio` Node service that auto-fills local demo data after `docker compose up`, provides a simple UI for manual generation and batch deletion, and optionally uses OpenAI/Codex-backed text enhancement only for manual advanced jobs.

**Architecture:** Keep the main community services unchanged and add a separate control-plane service under `tools/mock-data-studio/`. The studio owns job orchestration, batch metadata, UI, and direct database writers for bulk data, while reusing selected existing APIs only for completion hooks such as search reindex. To avoid existing-volume drift, the studio bootstraps its own metadata tables at startup with idempotent DDL and also updates `deploy/mysql-init/010_schema.sql` so fresh volumes match the running system.

**Tech Stack:** Node.js 20 ESM, Express, `mysql2/promise`, native `fetch`, vanilla HTML/CSS/JS UI, OpenAI Node SDK for optional AI enhancement, Docker Compose, MySQL 8, existing community gateway/app/im services, Node built-in test runner + `supertest`

---

## File Structure Map

### Compose and docs

- `deploy/docker-compose.yml`
  - Add the new `mock-data-studio` service, localhost-only port exposure, env injection, dependencies, and healthcheck.
- `deploy/.env.example`
  - Add `mock-data-studio` port, auto-fill defaults, OpenAI/Codex env toggles, and safety limits.
- `deploy/README.md`
  - Document how the new service starts, where the UI lives, and how auto-fill/manual generation behave.
- `docs/DEV_ONLY.md`
  - Document that this service is dev/demo-only and how batch deletion / AI enhancement are gated.
- `docs/DATA_MODEL.md`
  - Document the new demo metadata tables and how generated entities are tracked.

### Tool service scaffold

- `tools/mock-data-studio/package.json`
  - Define runtime/test scripts and dependencies.
- `tools/mock-data-studio/package-lock.json`
  - Lock dependencies.
- `tools/mock-data-studio/Dockerfile`
  - Build the Node service for compose.
- `tools/mock-data-studio/README.md`
  - Local tool usage, env vars, and validation workflow.

### Server and config

- `tools/mock-data-studio/src/server/index.mjs`
  - Process entrypoint and startup sequence.
- `tools/mock-data-studio/src/server/app.mjs`
  - Express app assembly.
- `tools/mock-data-studio/src/config/env.mjs`
  - Env parsing, defaults, and validation.
- `tools/mock-data-studio/src/server/routes/health.mjs`
  - `/health` and lightweight runtime probes.
- `tools/mock-data-studio/src/server/routes/runtimeStatus.mjs`
  - Database/API/OpenAI readiness inspection.
- `tools/mock-data-studio/src/server/routes/jobs.mjs`
  - Start job, inspect job, list jobs, stop request.
- `tools/mock-data-studio/src/server/routes/batches.mjs`
  - Batch list/detail/delete APIs.

### Persistence and batch metadata

- `tools/mock-data-studio/src/db/mysql.mjs`
  - Shared MySQL connection pool helpers.
- `tools/mock-data-studio/src/db/bootstrap.mjs`
  - Idempotent DDL for demo metadata tables at service startup.
- `tools/mock-data-studio/src/batches/batchRepository.mjs`
  - `demo_batch` CRUD.
- `tools/mock-data-studio/src/jobs/jobRepository.mjs`
  - `demo_job` CRUD and status transitions.
- `tools/mock-data-studio/src/batches/targetRepository.mjs`
  - `demo_batch_target` persistence.
- `tools/mock-data-studio/src/batches/entityRefRepository.mjs`
  - `demo_entity_ref` persistence for generated entity tracking and delete planning.
- `deploy/mysql-init/010_schema.sql`
  - Fresh-volume DDL for `demo_batch`, `demo_job`, `demo_batch_target`, `demo_entity_ref`.

### Orchestration and generators

- `tools/mock-data-studio/src/jobs/jobRunner.mjs`
  - Single-flight execution, phase transitions, and stop requests.
- `tools/mock-data-studio/src/jobs/autoFillService.mjs`
  - Default-batch deficit calculation and startup trigger.
- `tools/mock-data-studio/src/generator/scenes/defaults.mjs`
  - Preset scene definitions and entity ratios.
- `tools/mock-data-studio/src/generator/planner.mjs`
  - Convert requested counts to a concrete generation plan.
- `tools/mock-data-studio/src/generator/random.mjs`
  - Seeded random helpers and stable faker-like primitives.
- `tools/mock-data-studio/src/generator/contentGenerator.mjs`
  - Users, posts, comments, tags, follow/like distributions for phase 1.
- `tools/mock-data-studio/src/generator/domainGenerator.mjs`
  - Messages, IM, moderation, growth, rewards for phase 2.

### Writers and completion hooks

- `tools/mock-data-studio/src/writers/communityWriter.mjs`
  - Bulk writes to `community` schema tables and entity-ref recording.
- `tools/mock-data-studio/src/writers/imWriter.mjs`
  - Bulk writes to `im_core` schema tables and entity-ref recording.
- `tools/mock-data-studio/src/writers/deleteBatchService.mjs`
  - Ordered delete by entity type and batch metadata cleanup.
- `tools/mock-data-studio/src/integration/communityApi.mjs`
  - Search reindex call and any selected API-backed sample actions.

### Optional AI enhancement

- `tools/mock-data-studio/src/ai/openaiClient.mjs`
  - OpenAI/Codex-capable client wrapper and timeout/budget control.
- `tools/mock-data-studio/src/ai/aiContentEnhancer.mjs`
  - Prompting and response normalization for manual advanced jobs only.

### UI

- `tools/mock-data-studio/src/ui/index.html`
  - Single-page shell.
- `tools/mock-data-studio/src/ui/styles.css`
  - Minimal localhost admin UI styles.
- `tools/mock-data-studio/src/ui/app.js`
  - Form submission, polling, and batch actions.

### Tests

- `tools/mock-data-studio/test/env.test.mjs`
- `tools/mock-data-studio/test/health.test.mjs`
- `tools/mock-data-studio/test/runtime-status.test.mjs`
- `tools/mock-data-studio/test/batch-repository.test.mjs`
- `tools/mock-data-studio/test/job-runner.test.mjs`
- `tools/mock-data-studio/test/auto-fill-service.test.mjs`
- `tools/mock-data-studio/test/planner.test.mjs`
- `tools/mock-data-studio/test/delete-batch-service.test.mjs`
- `tools/mock-data-studio/test/openai-client.test.mjs`
- `tools/mock-data-studio/test/ui-api-contract.test.mjs`

---

### Task 1: Scaffold The Studio Service And Compose Wiring

**Files:**
- Create: `tools/mock-data-studio/package.json`
- Create: `tools/mock-data-studio/Dockerfile`
- Create: `tools/mock-data-studio/README.md`
- Create: `tools/mock-data-studio/src/server/index.mjs`
- Create: `tools/mock-data-studio/src/server/app.mjs`
- Create: `tools/mock-data-studio/src/config/env.mjs`
- Create: `tools/mock-data-studio/src/server/routes/health.mjs`
- Create: `tools/mock-data-studio/test/env.test.mjs`
- Create: `tools/mock-data-studio/test/health.test.mjs`
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`
- Modify: `deploy/README.md`
- Modify: `docs/DEV_ONLY.md`

- [ ] **Step 1: Create the Node package and test script scaffold**

  Add a minimal manifest before any runtime code:

  ```json
  {
    "name": "mock-data-studio",
    "private": true,
    "type": "module",
    "scripts": {
      "start": "node src/server/index.mjs",
      "test": "node --test"
    }
  }
  ```

- [ ] **Step 2: Write failing env and health-route tests**

  Cover:
  - env defaults for localhost-only port and auto-fill flags
  - missing required DB env handling
  - `/health` returns `{ ok: true, service: "mock-data-studio" }`

  Example:

  ```js
  test("health route returns ok payload", async () => {
    const app = buildApp({ config: fakeConfig() })
    const res = await request(app).get("/health")
    assert.equal(res.status, 200)
    assert.equal(res.body.service, "mock-data-studio")
  })
  ```

- [ ] **Step 3: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio install
  npm --prefix tools/mock-data-studio test -- test/env.test.mjs test/health.test.mjs
  ```

  Expected: FAIL because `env.mjs`, `app.mjs`, and `health.mjs` do not exist yet.

- [ ] **Step 4: Implement the minimal service shell**

  Implement:
  - `env.mjs` that parses env and returns a normalized config object
  - `app.mjs` that mounts JSON middleware and `/health`
  - `index.mjs` that starts the server and logs startup info
  - localhost-only compose service with:
    - `127.0.0.1:${MOCK_DATA_STUDIO_PORT:-12888}:12888`
    - dependency on `mysql`, `community-app`, and `im-core`
    - mount/build pattern consistent with repo compose style

- [ ] **Step 5: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/env.test.mjs test/health.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 6: Validate compose rendering**

  Run:

  ```bash
  docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config
  ```

  Expected: PASS with a rendered `mock-data-studio` service and no missing env/file errors.

- [ ] **Step 7: Checkpoint the scaffold diff**

  Note: do not create a git commit unless the user explicitly asks for one.

---

### Task 2: Add Metadata Tables And Startup Bootstrap

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `docs/DATA_MODEL.md`
- Create: `tools/mock-data-studio/src/db/mysql.mjs`
- Create: `tools/mock-data-studio/src/db/bootstrap.mjs`
- Create: `tools/mock-data-studio/src/batches/batchRepository.mjs`
- Create: `tools/mock-data-studio/src/jobs/jobRepository.mjs`
- Create: `tools/mock-data-studio/src/batches/targetRepository.mjs`
- Create: `tools/mock-data-studio/src/batches/entityRefRepository.mjs`
- Create: `tools/mock-data-studio/test/batch-repository.test.mjs`

- [ ] **Step 1: Write failing tests for metadata bootstrap and repository contracts**

  Cover:
  - generated DDL includes `demo_batch`, `demo_job`, `demo_batch_target`, `demo_entity_ref`
  - repository methods produce the expected state transitions for create/start/finish
  - entity refs can represent both single-id entities and composite-key entities

  Example entity-ref shape:

  ```js
  {
    batchId: 42,
    entityType: "im_room_member",
    entityKey: "9001:17"
  }
  ```

- [ ] **Step 2: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/batch-repository.test.mjs
  ```

  Expected: FAIL because the bootstrap and repositories are not implemented.

- [ ] **Step 3: Implement startup DDL and metadata repositories**

  Implement:
  - startup bootstrap with `CREATE TABLE IF NOT EXISTS ...`
  - fresh-volume DDL in `deploy/mysql-init/010_schema.sql`
  - repositories for:
    - batch rows
    - job rows
    - per-entity targets
    - generated entity references for deletion

  Use a sidecar metadata model instead of adding `demo_batch_id` columns to every business table in phase 1.

- [ ] **Step 4: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/batch-repository.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 5: Wire bootstrap into service startup**

  Ensure `index.mjs` does this order:

  ```js
  const db = await createDb(config)
  await bootstrapDemoSchema(db)
  await startServer({ db, config })
  ```

- [ ] **Step 6: Checkpoint the metadata layer diff**

  Note: do not create a git commit unless the user explicitly asks for one.

---

### Task 3: Implement Job Runner, Runtime Status, And Single-Flight Protection

**Files:**
- Create: `tools/mock-data-studio/src/jobs/jobRunner.mjs`
- Create: `tools/mock-data-studio/src/server/routes/runtimeStatus.mjs`
- Create: `tools/mock-data-studio/src/server/routes/jobs.mjs`
- Create: `tools/mock-data-studio/test/runtime-status.test.mjs`
- Create: `tools/mock-data-studio/test/job-runner.test.mjs`
- Modify: `tools/mock-data-studio/src/server/app.mjs`

- [ ] **Step 1: Write failing tests for runtime status and one-job-at-a-time behavior**

  Cover:
  - runtime status reports DB/API readiness and OpenAI env readiness separately
  - second job submission while one is running returns `409`
  - job phase transitions are persisted in order

- [ ] **Step 2: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/runtime-status.test.mjs test/job-runner.test.mjs
  ```

  Expected: FAIL because the status and runner modules do not exist yet.

- [ ] **Step 3: Implement runtime status and job runner**

  Implement:
  - `/api/runtime-status`
  - `/api/jobs`
  - `/api/jobs/:jobId`
  - in-memory single-flight lock around one active runner
  - persisted phases such as:
    - `bootstrap`
    - `plan`
    - `write-community`
    - `write-im`
    - `reindex`
    - `finalize`

- [ ] **Step 4: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/runtime-status.test.mjs test/job-runner.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 5: Add a stop-request contract without full forced cancellation**

  First version behavior:
  - accept stop intent on the current job
  - only check stop flag between phases
  - do not implement mid-SQL hard interrupts

- [ ] **Step 6: Checkpoint the orchestration diff**

  Note: do not create a git commit unless the user explicitly asks for one.

---

### Task 4: Implement Auto-Fill Planning And Search Reindex Completion Hook

**Files:**
- Create: `tools/mock-data-studio/src/jobs/autoFillService.mjs`
- Create: `tools/mock-data-studio/src/generator/scenes/defaults.mjs`
- Create: `tools/mock-data-studio/src/generator/planner.mjs`
- Create: `tools/mock-data-studio/src/integration/communityApi.mjs`
- Create: `tools/mock-data-studio/test/auto-fill-service.test.mjs`
- Create: `tools/mock-data-studio/test/planner.test.mjs`
- Modify: `tools/mock-data-studio/src/server/index.mjs`
- Modify: `deploy/.env.example`
- Modify: `deploy/README.md`

- [ ] **Step 1: Write failing tests for deficit-based auto-fill planning**

  Cover:
  - default batch deficits are computed as `target - existing`
  - no-op when counts are already satisfied
  - search reindex hook only runs when content-like entities were generated

  Example:

  ```js
  assert.deepEqual(plan.deficits, {
    users: 80,
    posts: 600,
    comments: 2100
  })
  ```

- [ ] **Step 2: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/auto-fill-service.test.mjs test/planner.test.mjs
  ```

  Expected: FAIL because planner and auto-fill services are not implemented.

- [ ] **Step 3: Implement default scene presets and deficit planner**

  Implement:
  - env-backed defaults such as `MOCK_DATA_DEFAULT_USERS`, `...POSTS`, `...COMMENTS`
  - scene presets for:
    - `tech-community-hot-start`
    - `moderation-pressure`
    - `im-busy`
    - `reward-ops-busy`
  - planner output split into community/im/growth/moderation/reward phases

- [ ] **Step 4: Implement search reindex completion hook**

  Use the existing ops endpoint rather than inventing a new one:

  ```js
  await fetch(`${config.communityBaseUrl}/api/ops/search/reindex`, {
    method: "POST",
    headers: { "content-type": "application/json" }
  })
  ```

  Gate it so empty/no-content jobs skip reindex.

- [ ] **Step 5: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/auto-fill-service.test.mjs test/planner.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 6: Wire startup auto-fill behind env flags**

  Required behavior:
  - `MOCK_DATA_STUDIO_ENABLED=true` keeps the UI server alive
  - `MOCK_DATA_AUTO_FILL_ENABLED=true` triggers startup deficit fill
  - startup failures are recorded as job failures, not silent logs only

---

### Task 5: Implement Phase 1 Writers For Users, Content, Social, And Search-Visible Data

**Files:**
- Create: `tools/mock-data-studio/src/generator/random.mjs`
- Create: `tools/mock-data-studio/src/generator/contentGenerator.mjs`
- Create: `tools/mock-data-studio/src/writers/communityWriter.mjs`
- Modify: `tools/mock-data-studio/src/jobs/jobRunner.mjs`
- Create: `tools/mock-data-studio/test/delete-batch-service.test.mjs`
- Modify: `tools/mock-data-studio/test/planner.test.mjs`

- [ ] **Step 1: Write failing tests for phase 1 generation contracts**

  Cover:
  - generated users/posts/comments match requested counts
  - comments attach only to generated or existing posts
  - social likes/follows reference real user/entity ids
  - generated entity refs are recorded for every inserted row set

- [ ] **Step 2: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/planner.test.mjs test/delete-batch-service.test.mjs
  ```

  Expected: FAIL because the generator and writer contracts are not implemented yet.

- [ ] **Step 3: Implement deterministic random helpers and phase 1 generators**

  Generate:
  - user profiles
  - post titles/bodies using rule templates
  - comment trees with bounded depth
  - follow and like graphs with skew toward active users

  Keep generation seedable so the same job parameters can be reproduced.

- [ ] **Step 4: Implement bulk community-schema writers**

  Direct-write these tables in controlled order:
  - `user`
  - `discuss_post`
  - `comment`
  - `social_follow`
  - `social_like`

  Update aggregate fields that the UI reads directly, such as:
  - `discuss_post.comment_count`
  - `user.score` baseline where needed for visible leaderboards

- [ ] **Step 5: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/planner.test.mjs test/delete-batch-service.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 6: Add a narrow compose smoke check for phase 1**

  Run:

  ```bash
  docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build mysql community-app community-gateway mock-data-studio
  curl -fsS http://127.0.0.1:12888/health
  ```

  Expected: PASS and the service reports healthy.

---

### Task 6: Implement Batch History, Batch Detail, And Ordered Batch Deletion

**Files:**
- Create: `tools/mock-data-studio/src/writers/deleteBatchService.mjs`
- Modify: `tools/mock-data-studio/src/server/routes/batches.mjs`
- Modify: `tools/mock-data-studio/src/batches/entityRefRepository.mjs`
- Modify: `tools/mock-data-studio/test/delete-batch-service.test.mjs`
- Create: `tools/mock-data-studio/test/ui-api-contract.test.mjs`

- [ ] **Step 1: Write failing tests for batch listing, detail, and delete ordering**

  Cover:
  - list endpoint groups default batch and manual batches correctly
  - detail endpoint returns target/actual/failure summaries
  - delete path removes dependent entities before parents
  - metadata rows disappear only after business rows are removed

- [ ] **Step 2: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/delete-batch-service.test.mjs test/ui-api-contract.test.mjs
  ```

  Expected: FAIL because batch API and deletion ordering are incomplete.

- [ ] **Step 3: Implement ordered deletion using entity refs**

  Encode and delete in safe order, for example:
  - `social_like`
  - `social_follow`
  - `comment`
  - `discuss_post`
  - `user`
  - then batch metadata

  Use separate order tables for `community` and `im_core` entity types.

- [ ] **Step 4: Implement batch APIs**

  Add:
  - `GET /api/batches`
  - `GET /api/batches/:batchId`
  - `DELETE /api/batches/:batchId`

  Keep delete blocked when the target batch still has a running job.

- [ ] **Step 5: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/delete-batch-service.test.mjs test/ui-api-contract.test.mjs
  ```

  Expected: PASS.

---

### Task 7: Build The Minimal UI For Generate, Status, History, And Detail

**Files:**
- Create: `tools/mock-data-studio/src/ui/index.html`
- Create: `tools/mock-data-studio/src/ui/styles.css`
- Create: `tools/mock-data-studio/src/ui/app.js`
- Modify: `tools/mock-data-studio/src/server/app.mjs`
- Modify: `tools/mock-data-studio/README.md`

- [ ] **Step 1: Write a failing API-contract test for the UI data needs**

  Cover the JSON shapes required by:
  - runtime status panel
  - generate form preview
  - job polling
  - batch list/detail

  This keeps the vanilla UI honest without introducing a front-end framework.

- [ ] **Step 2: Run the targeted test and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/ui-api-contract.test.mjs
  ```

  Expected: FAIL because the route payloads are still incomplete for the UI.

- [ ] **Step 3: Implement the static UI shell**

  Include:
  - mode switch: `自动补数` / `手动生成`
  - scene preset picker
  - count inputs
  - AI enhancement toggle
  - runtime status cards
  - batch history table
  - batch detail drawer/panel

- [ ] **Step 4: Re-run the targeted test and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/ui-api-contract.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 5: Perform a browserless smoke check**

  Run:

  ```bash
  curl -fsS http://127.0.0.1:12888/ | head
  curl -fsS http://127.0.0.1:12888/api/runtime-status
  ```

  Expected: HTML for the UI shell and JSON runtime status.

---

### Task 8: Add Phase 2 Generators For Messages, IM, Moderation, Growth, And Reward Samples

**Files:**
- Create: `tools/mock-data-studio/src/generator/domainGenerator.mjs`
- Create: `tools/mock-data-studio/src/writers/imWriter.mjs`
- Modify: `tools/mock-data-studio/src/writers/communityWriter.mjs`
- Modify: `tools/mock-data-studio/src/jobs/jobRunner.mjs`
- Modify: `tools/mock-data-studio/test/planner.test.mjs`
- Modify: `tools/mock-data-studio/test/delete-batch-service.test.mjs`
- Modify: `docs/DEV_ONLY.md`

- [ ] **Step 1: Write failing tests for phase 2 plan and delete coverage**

  Cover:
  - message/notices samples are generated for the requested counts
  - IM room + private data write paths generate matching entity refs
  - moderation/reward/growth entities are included in batch detail summaries

- [ ] **Step 2: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/planner.test.mjs test/delete-batch-service.test.mjs
  ```

  Expected: FAIL because phase 2 entities are not supported yet.

- [ ] **Step 3: Implement community-schema phase 2 writers**

  Direct-write controlled samples for:
  - `message`
  - `report`
  - `moderation_action`
  - `growth_check_in`
  - `user_task_progress`
  - `reward_item`
  - `reward_order`
  - related reward/growth ledger samples as needed for admin pages

- [ ] **Step 4: Implement IM writers**

  Direct-write controlled samples for:
  - `im_room`
  - `im_room_member`
  - `im_room_message`
  - `im_conversation`
  - `im_private_message`
  - read-state rows only where the UI actually consumes them

- [ ] **Step 5: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/planner.test.mjs test/delete-batch-service.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 6: Smoke the visible pages after generation**

  After one generated batch, verify manually in the browser:
  - `/posts`
  - `/search`
  - `/leaderboard`
  - `/moderation`
  - `/admin/rewards`
  - IM views

  Expected: each page shows non-empty demo data.

---

### Task 9: Add Optional OpenAI/Codex Text Enhancement For Manual Advanced Jobs

**Files:**
- Create: `tools/mock-data-studio/src/ai/openaiClient.mjs`
- Create: `tools/mock-data-studio/src/ai/aiContentEnhancer.mjs`
- Modify: `tools/mock-data-studio/src/config/env.mjs`
- Modify: `tools/mock-data-studio/src/server/routes/runtimeStatus.mjs`
- Modify: `tools/mock-data-studio/src/generator/contentGenerator.mjs`
- Modify: `tools/mock-data-studio/src/generator/domainGenerator.mjs`
- Create: `tools/mock-data-studio/test/openai-client.test.mjs`
- Modify: `deploy/.env.example`
- Modify: `deploy/README.md`

- [ ] **Step 1: Write failing tests for AI env gating and request budgeting**

  Cover:
  - advanced mode rejects requests when required env vars are missing
  - automatic startup jobs never use the AI path
  - large requests are capped/chunked before reaching the provider

- [ ] **Step 2: Run the targeted tests and confirm RED**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/openai-client.test.mjs
  ```

  Expected: FAIL because the AI wrapper and env validation are not implemented.

- [ ] **Step 3: Implement the provider wrapper behind a narrow interface**

  Shape it like:

  ```js
  export async function enhanceTexts({ kind, inputs, config }) {
    // OpenAI SDK call here
    return normalizedOutputs
  }
  ```

  Keep the OpenAI-specific logic isolated to `src/ai/`.

- [ ] **Step 4: Thread AI enhancement into manual jobs only**

  Rules:
  - only when the user chose advanced mode
  - only for textual fields
  - fall back to rule-generated text if the provider fails or times out

- [ ] **Step 5: Re-run the targeted tests and verify GREEN**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test -- test/openai-client.test.mjs
  ```

  Expected: PASS.

- [ ] **Step 6: Document the env contract**

  Update docs and `.env.example` with:
  - enable flag
  - API key
  - model name
  - timeout
  - max AI items per job

---

### Task 10: Final Verification

**Files:**
- Verify only

- [ ] **Step 1: Run the focused studio test suite**

  Run:

  ```bash
  npm --prefix tools/mock-data-studio test
  ```

  Expected: PASS.

- [ ] **Step 2: Validate compose rendering again**

  Run:

  ```bash
  docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config
  ```

  Expected: PASS.

- [ ] **Step 3: Build and start the local stack**

  Run:

  ```bash
  cp deploy/.env.example deploy/.env
  docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
  ```

  Expected: PASS with `mock-data-studio` healthy on `http://127.0.0.1:12888`.

- [ ] **Step 4: Verify startup auto-fill**

  Run:

  ```bash
  curl -fsS http://127.0.0.1:12888/api/batches
  curl -fsS http://127.0.0.1:12888/api/jobs
  ```

  Expected:
  - default batch exists
  - startup job is recorded
  - counts are non-zero after completion

- [ ] **Step 5: Verify manual generation and deletion**

  Run:

  ```bash
  curl -fsS -X POST http://127.0.0.1:12888/api/jobs \
    -H 'content-type: application/json' \
    -d '{"mode":"manual","scene":"tech-community-hot-start","counts":{"users":20,"posts":40,"comments":120},"aiEnhancement":false}'
  ```

  Then:
  - poll the returned job id to success
  - inspect the new batch in `/api/batches`
  - delete that batch and confirm counts/history update correctly

- [ ] **Step 6: Verify visible application pages**

  Confirm in the browser:
  - `http://localhost:12881/#/posts`
  - `http://localhost:12881/#/search`
  - `http://localhost:12881/#/leaderboard`
  - `http://localhost:12881/#/moderation`
  - `http://localhost:12881/#/admin/rewards`

  Expected: non-empty content driven by generated demo data.

- [ ] **Step 7: Checkpoint the finished diff**

  Note: do not create a git commit unless the user explicitly asks for one.
