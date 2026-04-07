# Deploy Compose Infra Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split `deploy/compose.infra.yml` into eight infra-specific compose files while preserving the current `make`-based operator workflow and runtime behavior.

**Architecture:** Keep the layered compose model introduced on `main`, but replace the single infra layer with eight explicit infra files: MySQL, Redis, Kafka, Elasticsearch, Nacos, XXL Job, MailHog, and Mock Data Studio bootstrap. The operator-facing command names stay unchanged; only `Makefile` and explicit low-level `docker compose -f ...` examples expand to the new infra file list.

**Tech Stack:** Docker Compose v5, YAML, GNU Make, Markdown docs, git on `main`.

**Spec:** `docs/superpowers/specs/2026-04-07-deploy-compose-infra-split-design.md`

---

## File Map

### New Infra Files

- `deploy/compose.infra.mysql.yml`
  - `mysql-primary`, `mysql-replica-1`, `mysql-replica-2`, `mysql-replication-bootstrap`
- `deploy/compose.infra.redis.yml`
  - `redis-1..6`, `redis-cluster-bootstrap`
- `deploy/compose.infra.kafka.yml`
  - `kafka-1..3`, `kafka-init`
- `deploy/compose.infra.elasticsearch.yml`
  - `elasticsearch-1..3`, `es-init`
- `deploy/compose.infra.nacos.yml`
  - `nacos`
- `deploy/compose.infra.xxl-job.yml`
  - `xxl-job-admin-1..2`
- `deploy/compose.infra.mailhog.yml`
  - `mailhog`
- `deploy/compose.infra.mock-data-studio-bootstrap.yml`
  - `mock-data-studio-db-bootstrap`

### Existing Files To Modify

- `Makefile`
  - Replace `-f deploy/compose.infra.yml` with a `COMPOSE_INFRA` list that expands to the eight new infra files.
- `deploy/README.md`
  - Replace the single-file infra description and any explicit `-f deploy/compose.infra.yml` command examples.
- `deploy/.env.example`
  - Replace file-structure comments that still mention `compose.infra.yml`.
- `docs/DEPLOYMENT.md`
  - Replace file-structure references and explicit low-level command examples.
- `docs/ARCHITECTURE.md`
  - Replace the default stack summary so it references the eight-file infra layout instead of `compose.infra.yml`.
- `docs/OBSERVABILITY.md`
  - Replace the explicit low-level `docker compose -f ...` commands to use the expanded infra list.
- `deploy/observability-elastic/kibana/README.md`
  - Replace the explicit low-level `docker compose -f ...` commands to use the expanded infra list.
- `docs/LOCAL_HA.md`
  - Replace every explicit `-f deploy/compose.infra.yml` command in the HA runbook.
- `docs/DATA_MODEL.md`
  - Replace the layered compose path text so it references the eight-file infra layout.

### File To Delete

- `deploy/compose.infra.yml`

---

### Task 1: Split The Infra Services Into Eight Files And Switch `Makefile`

**Files:**
- Create: `deploy/compose.infra.mysql.yml`
- Create: `deploy/compose.infra.redis.yml`
- Create: `deploy/compose.infra.kafka.yml`
- Create: `deploy/compose.infra.elasticsearch.yml`
- Create: `deploy/compose.infra.nacos.yml`
- Create: `deploy/compose.infra.xxl-job.yml`
- Create: `deploy/compose.infra.mailhog.yml`
- Create: `deploy/compose.infra.mock-data-studio-bootstrap.yml`
- Modify: `Makefile`
- Test: `deploy/compose.infra.yml`

- [ ] **Step 1: Verify the current baseline before changing infra composition**

Run:
```bash
test -f deploy/compose.infra.yml
make config
```
Expected:
- `test -f` succeeds.
- `make config` exits `0`.

- [ ] **Step 2: Create the four datastore infra files by copying the current service blocks verbatim**

Create `deploy/compose.infra.mysql.yml` with:
```yaml
services:
  mysql-primary:               # copy the full existing block from deploy/compose.infra.yml
  mysql-replica-1:            # copy the full existing block from deploy/compose.infra.yml
  mysql-replica-2:            # copy the full existing block from deploy/compose.infra.yml
  mysql-replication-bootstrap:# copy the full existing block from deploy/compose.infra.yml
```

Create `deploy/compose.infra.redis.yml` with:
```yaml
services:
  redis-1:                    # copy the full existing block from deploy/compose.infra.yml
  redis-2:
  redis-3:
  redis-4:
  redis-5:
  redis-6:
  redis-cluster-bootstrap:
```

Create `deploy/compose.infra.kafka.yml` with:
```yaml
services:
  kafka-1:                    # copy the full existing block from deploy/compose.infra.yml
  kafka-2:
  kafka-3:
  kafka-init:
```

Create `deploy/compose.infra.elasticsearch.yml` with:
```yaml
services:
  elasticsearch-1:            # copy the full existing block from deploy/compose.infra.yml
  elasticsearch-2:
  elasticsearch-3:
  es-init:
```

Rule for this step:
- Preserve env vars, health checks, `depends_on`, aliases, ports, `ulimits`, restart policy, and volume mounts exactly.
- Do not change any service names.

- [ ] **Step 3: Create the four control-plane infra files by copying the current service blocks verbatim**

Create `deploy/compose.infra.nacos.yml` with:
```yaml
services:
  nacos:                      # copy the full existing block from deploy/compose.infra.yml
```

Create `deploy/compose.infra.xxl-job.yml` with:
```yaml
services:
  xxl-job-admin-1:            # copy the full existing block from deploy/compose.infra.yml
  xxl-job-admin-2:
```

Create `deploy/compose.infra.mailhog.yml` with:
```yaml
services:
  mailhog:                    # copy the full existing block from deploy/compose.infra.yml
```

Create `deploy/compose.infra.mock-data-studio-bootstrap.yml` with:
```yaml
services:
  mock-data-studio-db-bootstrap: # copy the full existing block from deploy/compose.infra.yml
```

Rule for this step:
- Keep `mock-data-studio-db-bootstrap` as its own file.
- Do not move `mysql-replication-bootstrap`, `redis-cluster-bootstrap`, `kafka-init`, or `es-init` out of their owner files.

- [ ] **Step 4: Switch `Makefile` from the single infra file to the eight-file infra list**

Update `Makefile` so the top section becomes:
```make
COMPOSE_INFRA = \
	-f deploy/compose.infra.mysql.yml \
	-f deploy/compose.infra.redis.yml \
	-f deploy/compose.infra.kafka.yml \
	-f deploy/compose.infra.elasticsearch.yml \
	-f deploy/compose.infra.nacos.yml \
	-f deploy/compose.infra.xxl-job.yml \
	-f deploy/compose.infra.mailhog.yml \
	-f deploy/compose.infra.mock-data-studio-bootstrap.yml

COMPOSE_BASE = docker compose --env-file deploy/.env \
	-f deploy/compose.yml \
	$(COMPOSE_INFRA) \
	-f deploy/compose.runtime.yml
```

Keep all existing target names intact:
- `up*`
- `down*`
- `ps*`
- `logs*`
- `config*`

- [ ] **Step 5: Validate the new infra composition before touching docs**

Run:
```bash
make config
make config-debug
make config-obs
make config-elastic
make config-elastic-json
```
Expected: all five commands exit `0`.

Run:
```bash
docker compose --env-file deploy/.env \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  config --services
```
Expected: exits `0` and lists the current default stack services.

- [ ] **Step 6: Commit**

```bash
git add Makefile \
        deploy/compose.infra.mysql.yml \
        deploy/compose.infra.redis.yml \
        deploy/compose.infra.kafka.yml \
        deploy/compose.infra.elasticsearch.yml \
        deploy/compose.infra.nacos.yml \
        deploy/compose.infra.xxl-job.yml \
        deploy/compose.infra.mailhog.yml \
        deploy/compose.infra.mock-data-studio-bootstrap.yml
git commit -m "refactor(deploy): split infra compose into dedicated files"
```

---

### Task 2: Migrate File-Structure Docs And Explicit Infra Command Examples

**Files:**
- Modify: `deploy/README.md`
- Modify: `deploy/.env.example`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/OBSERVABILITY.md`
- Modify: `deploy/observability-elastic/kibana/README.md`
- Modify: `docs/LOCAL_HA.md`
- Modify: `docs/DATA_MODEL.md`

- [ ] **Step 1: Capture the current references to `compose.infra.yml` in operator docs**

Run:
```bash
rg -n "compose\\.infra\\.yml" \
  deploy/README.md \
  deploy/.env.example \
  docs/DEPLOYMENT.md \
  docs/ARCHITECTURE.md \
  docs/OBSERVABILITY.md \
  deploy/observability-elastic/kibana/README.md \
  docs/LOCAL_HA.md \
  docs/DATA_MODEL.md
```
Expected: multiple matches across the listed files.

- [ ] **Step 2: Update the file-structure sections to the new eight-file infra layout**

Apply these concrete content changes:

- In `deploy/README.md`, replace the single `compose.infra.yml` bullet with:
```md
- `compose.infra.mysql.yml`：MySQL 主从与 replication bootstrap。
- `compose.infra.redis.yml`：Redis Cluster 6 节点与 cluster bootstrap。
- `compose.infra.kafka.yml`：Kafka KRaft 3 节点与 topic bootstrap。
- `compose.infra.elasticsearch.yml`：Elasticsearch 3 节点与 index bootstrap。
- `compose.infra.nacos.yml`：单节点 `Nacos` 注册中心。
- `compose.infra.xxl-job.yml`：`xxl-job-admin x2` 控制面。
- `compose.infra.mailhog.yml`：dev mailbox。
- `compose.infra.mock-data-studio-bootstrap.yml`：`mock-data-studio-db-bootstrap` 数据准备 sidecar。
```

- In `docs/DEPLOYMENT.md`, replace the single infra bullet with the same eight-file split, and keep `deploy/compose.runtime.yml` focused on business runtime only.

- In `docs/ARCHITECTURE.md`, replace the summary text:
```md
`deploy/compose.yml` + `deploy/compose.infra.yml` + `deploy/compose.runtime.yml`
```
with:
```md
`deploy/compose.yml` + `deploy/compose.infra.mysql.yml` + `deploy/compose.infra.redis.yml` + `deploy/compose.infra.kafka.yml` + `deploy/compose.infra.elasticsearch.yml` + `deploy/compose.infra.nacos.yml` + `deploy/compose.infra.xxl-job.yml` + `deploy/compose.infra.mailhog.yml` + `deploy/compose.infra.mock-data-studio-bootstrap.yml` + `deploy/compose.runtime.yml`
```

- In `deploy/.env.example`, replace any file-structure/operator comments that still mention `compose.infra.yml` with the new eight-file infra wording.

- [ ] **Step 3: Replace low-level `docker compose -f ...` examples with the expanded infra list**

Use this explicit base command wherever the docs currently show `-f deploy/compose.infra.yml`:

```bash
docker compose --env-file deploy/.env \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml
```

And for overlay variants, continue appending the existing overlay files after that base list.

Files that must be updated this way:
- `docs/DEPLOYMENT.md`
- `docs/OBSERVABILITY.md`
- `deploy/observability-elastic/kibana/README.md`
- `docs/LOCAL_HA.md`

- [ ] **Step 4: Update the layered-stack wording in `docs/DATA_MODEL.md`**

Replace:
```md
默认 layered compose 栈（`deploy/compose.yml` + `deploy/compose.infra.yml` + `deploy/compose.runtime.yml`）
```
with:
```md
默认 layered compose 栈（`deploy/compose.yml` + 8 个 `deploy/compose.infra.*.yml` 文件 + `deploy/compose.runtime.yml`）
```

- [ ] **Step 5: Re-run the infra-file-reference grep**

Run:
```bash
rg -n "compose\\.infra\\.yml" \
  deploy/README.md \
  deploy/.env.example \
  docs/DEPLOYMENT.md \
  docs/ARCHITECTURE.md \
  docs/OBSERVABILITY.md \
  deploy/observability-elastic/kibana/README.md \
  docs/LOCAL_HA.md \
  docs/DATA_MODEL.md
```
Expected: no matches.

- [ ] **Step 6: Commit**

```bash
git add deploy/README.md \
        deploy/.env.example \
        docs/DEPLOYMENT.md \
        docs/ARCHITECTURE.md \
        docs/OBSERVABILITY.md \
        deploy/observability-elastic/kibana/README.md \
        docs/LOCAL_HA.md \
        docs/DATA_MODEL.md
git commit -m "docs(deploy): document split infra compose files"
```

---

### Task 3: Remove The Legacy Infra File And Run Final Verification

**Files:**
- Delete: `deploy/compose.infra.yml`
- Test: `Makefile`

- [ ] **Step 1: Delete the old monolithic infra file**

Delete:
```text
deploy/compose.infra.yml
```

- [ ] **Step 2: Run the final config matrix after removing the legacy file**

Run:
```bash
make config
make config-debug
make config-obs
make config-elastic
make config-elastic-json
```
Expected: all five commands exit `0`.

- [ ] **Step 3: Verify the operator docs no longer reference the removed file**

Run:
```bash
rg -n "compose\\.infra\\.yml" \
  deploy/README.md \
  deploy/.env.example \
  docs/DEPLOYMENT.md \
  docs/ARCHITECTURE.md \
  docs/OBSERVABILITY.md \
  deploy/observability-elastic/kibana/README.md \
  docs/LOCAL_HA.md \
  docs/DATA_MODEL.md \
  Makefile
```
Expected: no matches.

Run:
```bash
git status --short
```
Expected: only the intended new infra files, doc updates, `Makefile`, and the deletion of `deploy/compose.infra.yml` are present before commit.

- [ ] **Step 4: Commit**

```bash
git add Makefile \
        deploy/compose.infra.mysql.yml \
        deploy/compose.infra.redis.yml \
        deploy/compose.infra.kafka.yml \
        deploy/compose.infra.elasticsearch.yml \
        deploy/compose.infra.nacos.yml \
        deploy/compose.infra.xxl-job.yml \
        deploy/compose.infra.mailhog.yml \
        deploy/compose.infra.mock-data-studio-bootstrap.yml \
        deploy/README.md \
        deploy/.env.example \
        docs/DEPLOYMENT.md \
        docs/ARCHITECTURE.md \
        docs/OBSERVABILITY.md \
        deploy/observability-elastic/kibana/README.md \
        docs/LOCAL_HA.md \
        docs/DATA_MODEL.md \
        deploy/compose.infra.yml
git commit -m "refactor(deploy): split infra layer into dedicated files"
```

---

## Self-Review

### Spec Coverage

- Eight-file infra split is covered by Task 1.
- Stable operator entrypoints are preserved by the `Makefile` change in Task 1.
- Doc and command migration are covered by Task 2.
- Removal of the legacy infra file and final verification are covered by Task 3.

No spec section is left without a task.

### Placeholder Scan

- No open markers or unnamed files remain.
- Every task lists exact file paths and validation commands.
- The infra ownership mapping is explicit per file.

### Type / Naming Consistency

- The new infra filenames are consistent everywhere:
  - `compose.infra.mysql.yml`
  - `compose.infra.redis.yml`
  - `compose.infra.kafka.yml`
  - `compose.infra.elasticsearch.yml`
  - `compose.infra.nacos.yml`
  - `compose.infra.xxl-job.yml`
  - `compose.infra.mailhog.yml`
  - `compose.infra.mock-data-studio-bootstrap.yml`
- The plan keeps `compose.runtime.yml`, overlays, service names, and operator target names unchanged.
