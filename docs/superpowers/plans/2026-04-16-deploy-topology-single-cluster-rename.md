# Deploy Topology `single`/`cluster` Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hard-rename the local deployment topology vocabulary from `dev` / `ha` to `single` / `cluster` across the operator, topology-specific deploy files, env examples, nginx configs, and active user-facing docs.

**Architecture:** Keep the current two topology shapes exactly as they are today, but rename every operator-facing and file-level concept to `single` and `cluster`. Add a shell regression script that proves the new topology names work and the old names fail, then update docs and startup hints so the repository exposes only one vocabulary.

**Tech Stack:** Bash, Docker Compose, Git worktrees, Nginx, Spring Boot 3, Maven, JUnit 5, ripgrep

---

## File Structure Map

### Operator and regression coverage

- `deploy/tests/topology_single_cluster.sh`
  Role: shell regression script that proves `single` / `cluster` work and `dev` / `ha` fail.
- `deploy/deployment.sh`
  Role: parse `--topology single|cluster`, pick the new env files, pick the renamed compose files, and stop accepting the old topology values.

### Env examples

- `deploy/.env.single.example`
  Role: single-node local defaults for the `single` topology.
- `deploy/.env.cluster.example`
  Role: clustered local defaults for the `cluster` topology.
- `deploy/.env.example`
  Role: remove this compatibility-era alias so the repository no longer advertises the old path.

### Topology-specific deploy assets to rename

- `deploy/compose.infra.mysql.single.yml`
- `deploy/compose.infra.mysql.cluster.yml`
- `deploy/compose.infra.redis.single.yml`
- `deploy/compose.infra.redis.cluster.yml`
- `deploy/compose.infra.kafka.single.yml`
- `deploy/compose.infra.kafka.cluster.yml`
- `deploy/compose.infra.elasticsearch.single.yml`
- `deploy/compose.infra.elasticsearch.cluster.yml`
- `deploy/compose.infra.nacos.single.yml`
- `deploy/compose.infra.nacos.cluster.yml`
- `deploy/compose.infra.xxl-job.single.yml`
- `deploy/compose.infra.xxl-job.cluster.yml`
- `deploy/compose.infra.mock-data-studio-bootstrap.single.yml`
- `deploy/compose.infra.mock-data-studio-bootstrap.cluster.yml`
- `deploy/compose.runtime.services.single.yml`
- `deploy/compose.runtime.services.cluster.yml`
- `deploy/compose.runtime.frontend-nginx.single.yml`
- `deploy/compose.runtime.frontend-nginx.cluster.yml`
- `deploy/compose.runtime.mock-data-studio.single.yml`
- `deploy/compose.runtime.mock-data-studio.cluster.yml`
- `deploy/nginx/nginx.single.conf`
- `deploy/nginx/nginx.cluster.conf`
  Role: keep the existing topology behavior, but rename the file-level surface so it matches the new operator vocabulary.

### Runtime and user-facing hints

- `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`
  Role: point the startup validation fix guide at `deploy/.env.single` and `deploy/.env.cluster`.
- `deploy/mysql/nacos/010_schema.sql`
  Role: keep the embedded comment consistent with the new compose file names.

### Active documentation to update

- `README.md`
- `backend/README.md`
- `deploy/README.md`
- `docs/DEPLOYMENT.md`
- `docs/OBSERVABILITY.md`
- `docs/LOAD_TESTING.md`
- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
- `docs/SECURITY.md`
- `tools/im-load/README.md`
- `deploy/observability/kibana/README.md`
- `docs/LOCAL_CLUSTER.md`
  Role: present only `single` / `cluster` commands, file names, and project names.

### Legacy path to retire

- `docs/LOCAL_HA.md`
  Role: rename to `docs/LOCAL_CLUSTER.md` so even the document path stops exposing the old vocabulary.

---

### Task 1: Guard The Hard Rename With A Shell Regression Script

**Files:**
- Create: `deploy/tests/topology_single_cluster.sh`

- [ ] **Step 1: Write the failing regression script**

Create `deploy/tests/topology_single_cluster.sh` with this exact content:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

help_output="$(./deploy/deployment.sh --help 2>&1)"
printf '%s\n' "${help_output}" | grep -F -- '--topology <single|cluster>'
if printf '%s\n' "${help_output}" | grep -F -- '--topology <dev|ha>' >/dev/null 2>&1; then
  echo "old topology help text is still visible" >&2
  exit 1
fi

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
dev_err="$(mktemp)"
ha_err="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}" "${dev_err}" "${ha_err}"' EXIT

./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example >"${cluster_full}"

grep -F 'name: community-single' "${single_infra}"
grep -E '^  mysql:$' "${single_infra}"
grep -E '^  community-gateway:$' "${single_full}"

grep -F 'name: community-cluster' "${cluster_infra}"
grep -E '^  mysql-primary:$' "${cluster_infra}"
grep -E '^  community-gateway-1:$' "${cluster_full}"

if ./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.single.example >/dev/null 2>"${dev_err}"; then
  echo "expected old topology dev to fail" >&2
  exit 1
fi
grep -F 'unsupported topology: dev' "${dev_err}"

if ./deploy/deployment.sh config --topology ha --scope infra --env-file deploy/.env.cluster.example >/dev/null 2>"${ha_err}"; then
  echo "expected old topology ha to fail" >&2
  exit 1
fi
grep -F 'unsupported topology: ha' "${ha_err}"
```

- [ ] **Step 2: Run the script to verify the current repository still fails**

Run:

```bash
bash deploy/tests/topology_single_cluster.sh
```

Expected:

- FAIL at the help-text check because `deploy/deployment.sh --help` still advertises `dev|ha`
- no deploy files are changed yet

---

### Task 2: Rename Deploy Assets And Rewire The Operator

**Files:**
- Modify: `deploy/deployment.sh`
- Rename: `deploy/.env.dev.example` -> `deploy/.env.single.example`
- Rename: `deploy/.env.ha.example` -> `deploy/.env.cluster.example`
- Delete: `deploy/.env.example`
- Rename: `deploy/compose.infra.mysql.dev.yml` -> `deploy/compose.infra.mysql.single.yml`
- Rename: `deploy/compose.infra.mysql.ha.yml` -> `deploy/compose.infra.mysql.cluster.yml`
- Rename: `deploy/compose.infra.redis.dev.yml` -> `deploy/compose.infra.redis.single.yml`
- Rename: `deploy/compose.infra.redis.ha.yml` -> `deploy/compose.infra.redis.cluster.yml`
- Rename: `deploy/compose.infra.kafka.dev.yml` -> `deploy/compose.infra.kafka.single.yml`
- Rename: `deploy/compose.infra.kafka.ha.yml` -> `deploy/compose.infra.kafka.cluster.yml`
- Rename: `deploy/compose.infra.elasticsearch.dev.yml` -> `deploy/compose.infra.elasticsearch.single.yml`
- Rename: `deploy/compose.infra.elasticsearch.ha.yml` -> `deploy/compose.infra.elasticsearch.cluster.yml`
- Rename: `deploy/compose.infra.nacos.dev.yml` -> `deploy/compose.infra.nacos.single.yml`
- Rename: `deploy/compose.infra.nacos.ha.yml` -> `deploy/compose.infra.nacos.cluster.yml`
- Rename: `deploy/compose.infra.xxl-job.dev.yml` -> `deploy/compose.infra.xxl-job.single.yml`
- Rename: `deploy/compose.infra.xxl-job.ha.yml` -> `deploy/compose.infra.xxl-job.cluster.yml`
- Rename: `deploy/compose.infra.mock-data-studio-bootstrap.dev.yml` -> `deploy/compose.infra.mock-data-studio-bootstrap.single.yml`
- Rename: `deploy/compose.infra.mock-data-studio-bootstrap.ha.yml` -> `deploy/compose.infra.mock-data-studio-bootstrap.cluster.yml`
- Rename: `deploy/compose.runtime.services.dev.yml` -> `deploy/compose.runtime.services.single.yml`
- Rename: `deploy/compose.runtime.services.ha.yml` -> `deploy/compose.runtime.services.cluster.yml`
- Rename: `deploy/compose.runtime.frontend-nginx.dev.yml` -> `deploy/compose.runtime.frontend-nginx.single.yml`
- Rename: `deploy/compose.runtime.frontend-nginx.ha.yml` -> `deploy/compose.runtime.frontend-nginx.cluster.yml`
- Rename: `deploy/compose.runtime.mock-data-studio.dev.yml` -> `deploy/compose.runtime.mock-data-studio.single.yml`
- Rename: `deploy/compose.runtime.mock-data-studio.ha.yml` -> `deploy/compose.runtime.mock-data-studio.cluster.yml`
- Rename: `deploy/nginx/nginx.dev.conf` -> `deploy/nginx/nginx.single.conf`
- Rename: `deploy/nginx/nginx.ha.conf` -> `deploy/nginx/nginx.cluster.conf`
- Modify: `deploy/mysql/nacos/010_schema.sql`

- [ ] **Step 1: Rename every topology-specific deploy file**

Run:

```bash
git mv deploy/.env.dev.example deploy/.env.single.example
git mv deploy/.env.ha.example deploy/.env.cluster.example
git rm deploy/.env.example

git mv deploy/compose.infra.mysql.dev.yml deploy/compose.infra.mysql.single.yml
git mv deploy/compose.infra.mysql.ha.yml deploy/compose.infra.mysql.cluster.yml
git mv deploy/compose.infra.redis.dev.yml deploy/compose.infra.redis.single.yml
git mv deploy/compose.infra.redis.ha.yml deploy/compose.infra.redis.cluster.yml
git mv deploy/compose.infra.kafka.dev.yml deploy/compose.infra.kafka.single.yml
git mv deploy/compose.infra.kafka.ha.yml deploy/compose.infra.kafka.cluster.yml
git mv deploy/compose.infra.elasticsearch.dev.yml deploy/compose.infra.elasticsearch.single.yml
git mv deploy/compose.infra.elasticsearch.ha.yml deploy/compose.infra.elasticsearch.cluster.yml
git mv deploy/compose.infra.nacos.dev.yml deploy/compose.infra.nacos.single.yml
git mv deploy/compose.infra.nacos.ha.yml deploy/compose.infra.nacos.cluster.yml
git mv deploy/compose.infra.xxl-job.dev.yml deploy/compose.infra.xxl-job.single.yml
git mv deploy/compose.infra.xxl-job.ha.yml deploy/compose.infra.xxl-job.cluster.yml
git mv deploy/compose.infra.mock-data-studio-bootstrap.dev.yml deploy/compose.infra.mock-data-studio-bootstrap.single.yml
git mv deploy/compose.infra.mock-data-studio-bootstrap.ha.yml deploy/compose.infra.mock-data-studio-bootstrap.cluster.yml
git mv deploy/compose.runtime.services.dev.yml deploy/compose.runtime.services.single.yml
git mv deploy/compose.runtime.services.ha.yml deploy/compose.runtime.services.cluster.yml
git mv deploy/compose.runtime.frontend-nginx.dev.yml deploy/compose.runtime.frontend-nginx.single.yml
git mv deploy/compose.runtime.frontend-nginx.ha.yml deploy/compose.runtime.frontend-nginx.cluster.yml
git mv deploy/compose.runtime.mock-data-studio.dev.yml deploy/compose.runtime.mock-data-studio.single.yml
git mv deploy/compose.runtime.mock-data-studio.ha.yml deploy/compose.runtime.mock-data-studio.cluster.yml
git mv deploy/nginx/nginx.dev.conf deploy/nginx/nginx.single.conf
git mv deploy/nginx/nginx.ha.conf deploy/nginx/nginx.cluster.conf
```

- [ ] **Step 2: Rewrite `deploy/deployment.sh` to accept only `single|cluster`**

Update the option help and topology handling to this shape:

```bash
usage() {
  cat <<'EOF'
Usage:
  ./deploy/deployment.sh <command> [options] [compose-args...]

Options:
  --topology <single|cluster>  Choose topology (default: cluster)
  --scope <full|infra>         Choose compose scope (default: full)
EOF
}

resolve_default_env_file() {
  local topology="$1"
  case "${topology}" in
    single)
      printf '%s/deploy/.env.single\n' "${REPO_ROOT}"
      ;;
    cluster)
      printf '%s/deploy/.env.cluster\n' "${REPO_ROOT}"
      ;;
    *)
      echo "[deployment.sh] unsupported topology: ${topology}" >&2
      exit 1
      ;;
  esac
}

resolve_default_project_name() {
  case "${TOPOLOGY}" in
    single) printf 'community-single\n' ;;
    cluster) printf 'community-cluster\n' ;;
    *)
      echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
      exit 1
      ;;
  esac
}

append_topology_files() {
  case "${TOPOLOGY}" in
    single)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.single.yml
        deploy/compose.infra.redis.single.yml
        deploy/compose.infra.kafka.single.yml
        deploy/compose.infra.elasticsearch.single.yml
        deploy/compose.infra.nacos.single.yml
        deploy/compose.infra.xxl-job.single.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.single.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.single.yml
          deploy/compose.runtime.frontend-nginx.single.yml
          deploy/compose.runtime.mock-data-studio.single.yml
        )
      fi
      ;;
    cluster)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.cluster.yml
        deploy/compose.infra.redis.cluster.yml
        deploy/compose.infra.kafka.cluster.yml
        deploy/compose.infra.elasticsearch.cluster.yml
        deploy/compose.infra.nacos.cluster.yml
        deploy/compose.infra.xxl-job.cluster.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.cluster.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.cluster.yml
          deploy/compose.runtime.frontend-nginx.cluster.yml
          deploy/compose.runtime.mock-data-studio.cluster.yml
        )
      fi
      ;;
    *)
      echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
      exit 1
      ;;
  esac
}

TOPOLOGY="cluster"

case "${TOPOLOGY}" in
  single|cluster)
    ;;
  *)
    echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
    exit 1
    ;;
esac
```

- [ ] **Step 3: Update renamed deploy assets so their internal references match the new names**

Make these exact text changes:

```bash
rg -n 'nginx\.dev\.conf|nginx\.ha\.conf|\.env\.dev|\.env\.ha|community-dev|community-ha|compose\..*\.dev\.yml|compose\..*\.ha\.yml' \
  deploy/.env.single.example \
  deploy/.env.cluster.example \
  deploy/compose.runtime.frontend-nginx.single.yml \
  deploy/compose.runtime.frontend-nginx.cluster.yml \
  deploy/mysql/nacos/010_schema.sql \
  deploy/deployment.sh
```

Then update the files to these target strings:

```text
deploy/.env.single.example:
- `deploy/.env.single`
- `./deploy/deployment.sh up --topology single`
- `./deploy/deployment.sh up --topology single --scope infra`
- `./deploy/deployment.sh up --topology single --observability`
- `docker compose --env-file deploy/.env.single -p community-single ...`

deploy/.env.cluster.example:
- `deploy/.env.cluster`
- `./deploy/deployment.sh up --topology cluster`
- `./deploy/deployment.sh up --topology cluster --observability`
- `docker compose --env-file deploy/.env.cluster -p community-cluster ...`

deploy/compose.runtime.frontend-nginx.single.yml:
- mount `./nginx/nginx.single.conf:/etc/nginx/nginx.conf:ro`

deploy/compose.runtime.frontend-nginx.cluster.yml:
- mount `./nginx/nginx.cluster.conf:/etc/nginx/nginx.conf:ro`

deploy/mysql/nacos/010_schema.sql:
- comment mentions `deploy/compose.infra.nacos.single.yml` and `deploy/compose.infra.nacos.cluster.yml`
```

- [ ] **Step 4: Re-run the regression script and verify it now passes**

Run:

```bash
bash deploy/tests/topology_single_cluster.sh
./deploy/deployment.sh --help | rg -- '--topology|--scope'
```

Expected:

- the shell regression script exits `0`
- help output only shows `single|cluster`

- [ ] **Step 5: Commit the operator and deploy rename**

```bash
git add deploy/tests/topology_single_cluster.sh deploy/deployment.sh deploy/.env.single.example deploy/.env.cluster.example deploy deploy/nginx deploy/mysql/nacos/010_schema.sql
git commit -m "refactor(deploy): rename topologies to single and cluster"
```

---

### Task 3: Rewrite Active Docs And Runtime Hints To The New Vocabulary

**Files:**
- Modify: `README.md`
- Modify: `backend/README.md`
- Modify: `deploy/README.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/OBSERVABILITY.md`
- Modify: `docs/LOAD_TESTING.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `docs/SECURITY.md`
- Modify: `tools/im-load/README.md`
- Modify: `deploy/observability/kibana/README.md`
- Rename: `docs/LOCAL_HA.md` -> `docs/LOCAL_CLUSTER.md`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`

- [ ] **Step 1: Prove the active docs still expose the old names**

Run:

```bash
rg -n --glob '!docs/superpowers/**' \
  'LOCAL_HA|\.env\.dev|\.env\.ha|--topology dev|--topology ha|community-dev|community-ha|compose\..*\.(dev|ha)\.yml|nginx\.(dev|ha)\.conf' \
  README.md backend/README.md deploy/README.md docs tools deploy/observability/kibana/README.md
```

Expected:

- many matches in the active docs and user-facing instructions

- [ ] **Step 2: Rename the HA runbook path**

Run:

```bash
git mv docs/LOCAL_HA.md docs/LOCAL_CLUSTER.md
```

- [ ] **Step 3: Rewrite the active docs and startup hint to the new strings**

Use these exact replacements as the target state:

```text
README.md:
- `cp deploy/.env.single.example deploy/.env.single`
- `./deploy/deployment.sh up --topology single`
- `./deploy/deployment.sh up --topology single --scope infra`
- `./deploy/deployment.sh up --topology cluster`
- `./deploy/deployment.sh up --topology single --observability`

backend/README.md:
- single-node commands use `.env.single.example` + `--topology single`
- cluster commands use `.env.cluster.example` + `--topology cluster`

deploy/README.md and docs/DEPLOYMENT.md:
- topology names are `single` and `cluster`
- default project names are `community-single` and `community-cluster`
- file patterns use `*.single.yml` and `*.cluster.yml`
- nginx config names are `nginx.single.conf` and `nginx.cluster.conf`
- env examples are `.env.single.example` and `.env.cluster.example`

docs/LOCAL_CLUSTER.md:
- title and body describe the `cluster` topology only
- explicit compose example uses `deploy/.env.cluster`, `community-cluster`, and all `*.cluster.yml` file names

docs/OBSERVABILITY.md, docs/LOAD_TESTING.md, tools/im-load/README.md, deploy/observability/kibana/README.md:
- replace every single-node command from `dev` to `single`
- replace every clustered command from `ha` to `cluster`

docs/SYSTEM_DESIGN.md:
- local compose env paths become `deploy/.env.single` and `deploy/.env.cluster`

docs/SECURITY.md:
- env example bullets become `deploy/.env.single.example` and `deploy/.env.cluster.example`

backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java:
- fixGuide line becomes:
  ` - 检查 deploy/.env.single / deploy/.env.cluster 与部署平台 Secret/ConfigMap 是否已注入对应环境变量`
```

- [ ] **Step 4: Re-run the doc sweep and confirm active docs are clean**

Run:

```bash
rg -n --glob '!docs/superpowers/**' \
  'LOCAL_HA|\.env\.dev|\.env\.ha|--topology dev|--topology ha|community-dev|community-ha|compose\..*\.(dev|ha)\.yml|nginx\.(dev|ha)\.conf' \
  README.md backend/README.md deploy/README.md docs tools deploy/observability/kibana/README.md
```

Expected:

- no matches in active docs or runtime hint files

- [ ] **Step 5: Commit the doc and hint rename**

```bash
git add README.md backend/README.md deploy/README.md docs/DEPLOYMENT.md docs/OBSERVABILITY.md docs/LOAD_TESTING.md docs/ARCHITECTURE.md docs/SYSTEM_DESIGN.md docs/SECURITY.md tools/im-load/README.md deploy/observability/kibana/README.md docs/LOCAL_CLUSTER.md backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java
git commit -m "docs(deploy): rewrite topology guidance to single and cluster"
```

---

### Task 4: Run Full Verification On The Renamed Surface

**Files:**
- Verify: `deploy/tests/topology_single_cluster.sh`
- Verify: `deploy/deployment.sh`
- Verify: `deploy/.env.single.example`
- Verify: `deploy/.env.cluster.example`
- Verify: `backend/community-app/src/test/java/com/nowcoder/community/config/RedisTopologyProfileTest.java`

- [ ] **Step 1: Run the deploy regression script**

Run:

```bash
bash deploy/tests/topology_single_cluster.sh
```

Expected:

- PASS

- [ ] **Step 2: Run the application regression test**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am \
  -Dtest=RedisTopologyProfileTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected:

- `BUILD SUCCESS`

- [ ] **Step 3: Render the four supported compose combinations explicitly**

Run:

```bash
./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example >/tmp/community-single-infra.yml
./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example >/tmp/community-single-full.yml
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example >/tmp/community-cluster-infra.yml
./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example >/tmp/community-cluster-full.yml
```

Expected:

- all four commands exit `0`

- [ ] **Step 4: Verify representative service names and old-name rejection**

Run:

```bash
rg -n '^  mysql:$|^  community-gateway:$' /tmp/community-single-full.yml
rg -n '^  mysql-primary:$|^  community-gateway-1:$' /tmp/community-cluster-full.yml

if ./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.single.example >/dev/null 2>/tmp/dev-topology.err; then
  exit 1
fi
grep -F 'unsupported topology: dev' /tmp/dev-topology.err

if ./deploy/deployment.sh config --topology ha --scope infra --env-file deploy/.env.cluster.example >/dev/null 2>/tmp/ha-topology.err; then
  exit 1
fi
grep -F 'unsupported topology: ha' /tmp/ha-topology.err
```

Expected:

- representative service names appear in the rendered files
- both old topology invocations fail with the expected error text

- [ ] **Step 5: Final status check**

Run:

```bash
git status --short
git log --oneline -n 3
```

Expected:

- working tree is clean
- the top commits include:
  - `refactor(deploy): rename topologies to single and cluster`
  - `docs(deploy): rewrite topology guidance to single and cluster`
