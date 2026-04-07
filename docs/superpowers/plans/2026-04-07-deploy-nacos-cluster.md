# Deploy Nacos Cluster Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the local single-node `Nacos` deployment with a real three-node cluster backed by `mysql-primary`, while preserving the current `make`-based operator workflow.

**Architecture:** Keep the layered Compose topology and the current infra file split, but rewrite `deploy/compose.infra.nacos.yml` into a three-node cluster plus a one-shot `nacos-db-bootstrap` initializer. Wire runtime defaults to the three cluster addresses, vendor the official `Nacos` MySQL schema into `deploy/mysql-init`, and update the operator docs so the default path consistently describes a three-node discovery plane. Per user instruction, execute and commit directly on `main` rather than using a worktree.

**Tech Stack:** Docker Compose, YAML, Bash, MySQL 8.0 init scripts, Markdown docs, git on `main`.

**Spec:** `docs/superpowers/specs/2026-04-07-deploy-nacos-cluster-design.md`

---

## File Map

### Files To Create

- `deploy/mysql-init/030_nacos_schema.sql`
  - Vendored official MySQL schema matching `nacos/nacos-server:v2.3.2-slim`.
- `deploy/scripts/bootstrap-nacos-db.sh`
  - Idempotent bootstrap script that creates the `nacos` schema, ensures the runtime user exists, grants runtime permissions, and imports `030_nacos_schema.sql` with root credentials.

### Files To Modify

- `deploy/compose.infra.nacos.yml`
  - Replace the single `nacos` service with `nacos-1`, `nacos-2`, `nacos-3`, and `nacos-db-bootstrap`.
- `deploy/compose.runtime.yml`
  - Replace all default `NACOS_SERVER_ADDR` values with the three-address list and update `depends_on` from `nacos` to `nacos-db-bootstrap` plus `nacos-1`.
- `deploy/mysql-init/001_create_databases.sh`
  - Add `NACOS_MYSQL_DATABASE`, `NACOS_MYSQL_USER`, and `NACOS_MYSQL_PASSWORD`; create the `nacos` schema and runtime grants during first-boot initialization.
- `deploy/.env.example`
  - Change `NACOS_SERVER_ADDR` to the three-address default and add `NACOS` MySQL env defaults.
- `deploy/README.md`
  - Replace “单节点 `Nacos`” wording with a three-node cluster description and update the topology / check sections.
- `docs/DEPLOYMENT.md`
  - Update the file breakdown, topology description, and operator verification text for the cluster.
- `docs/ARCHITECTURE.md`
  - Replace the single-node discovery-plane description with a three-node `Nacos` cluster summary.
- `docs/LOCAL_HA.md`
  - Update the runtime checks and troubleshooting text so they refer to the cluster layout and three-address defaults.

### Verification Targets

- `Makefile`
  - No expected changes, but its existing `config*` targets remain the primary render verification path.

---

### Task 1: Add The Nacos Database Assets And Upgrade Path

**Files:**
- Create: `deploy/mysql-init/030_nacos_schema.sql`
- Create: `deploy/scripts/bootstrap-nacos-db.sh`
- Modify: `deploy/mysql-init/001_create_databases.sh`

- [ ] **Step 1: Confirm the baseline has no Nacos DB bootstrap assets**

Run:
```bash
test ! -f deploy/mysql-init/030_nacos_schema.sql
test ! -f deploy/scripts/bootstrap-nacos-db.sh
rg -n 'NACOS_MYSQL_|create database if not exists `nacos`' deploy/mysql-init/001_create_databases.sh
```
Expected:
- Both `test ! -f` commands succeed.
- `rg` exits `1`.

- [ ] **Step 2: Extend `deploy/mysql-init/001_create_databases.sh` with Nacos schema and runtime grants**

Add the new env parsing near the existing IM / XXL-Job variables:
```bash
NACOS_MYSQL_DATABASE="${NACOS_MYSQL_DATABASE:-nacos}"
NACOS_MYSQL_USER="${NACOS_MYSQL_USER:-nacos}"
NACOS_MYSQL_PASSWORD="${NACOS_MYSQL_PASSWORD:-nacospass}"
```

Add the escaped values near the existing `*_ESCAPED` lines:
```bash
NACOS_MYSQL_DATABASE_ESCAPED="$(sql_escape "${NACOS_MYSQL_DATABASE}")"
NACOS_MYSQL_USER_ESCAPED="$(sql_escape "${NACOS_MYSQL_USER}")"
NACOS_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${NACOS_MYSQL_PASSWORD}")"
```

Add the Nacos schema / user block before `flush privileges;`:
```sql
create database if not exists \`${NACOS_MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${NACOS_MYSQL_USER_ESCAPED}'@'%' identified by '${NACOS_MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${NACOS_MYSQL_DATABASE_ESCAPED}\`.* to '${NACOS_MYSQL_USER_ESCAPED}'@'%';
```

- [ ] **Step 3: Vendor the official Nacos schema into `deploy/mysql-init/030_nacos_schema.sql`**

Run:
```bash
curl -fsSL https://raw.githubusercontent.com/alibaba/nacos/2.3.2/distribution/conf/mysql-schema.sql \
  -o deploy/mysql-init/030_nacos_schema.sql
```

Then prepend a short provenance comment so the file starts like this:
```sql
-- Vendored from https://raw.githubusercontent.com/alibaba/nacos/2.3.2/distribution/conf/mysql-schema.sql
-- Matches nacos/nacos-server:v2.3.2-slim used by deploy/compose.infra.nacos.yml.
```

- [ ] **Step 4: Add the idempotent bootstrap script at `deploy/scripts/bootstrap-nacos-db.sh`**

Create the script with this structure:
```bash
#!/usr/bin/env bash
set -euo pipefail

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
NACOS_MYSQL_HOST="${NACOS_MYSQL_HOST:-mysql-primary}"
NACOS_MYSQL_PORT="${NACOS_MYSQL_PORT:-3306}"
NACOS_MYSQL_DATABASE="${NACOS_MYSQL_DATABASE:-nacos}"
NACOS_MYSQL_USER="${NACOS_MYSQL_USER:-nacos}"
NACOS_MYSQL_PASSWORD="${NACOS_MYSQL_PASSWORD:-nacospass}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[nacos-db-bootstrap] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

db_escaped="$(sql_escape "${NACOS_MYSQL_DATABASE}")"
user_escaped="$(sql_escape "${NACOS_MYSQL_USER}")"
password_escaped="$(sql_escape "${NACOS_MYSQL_PASSWORD}")"

mysql --default-character-set=utf8mb4 \
  -h"${NACOS_MYSQL_HOST}" \
  -P"${NACOS_MYSQL_PORT}" \
  -uroot \
  -p"${MYSQL_ROOT_PASSWORD}" <<SQL
create database if not exists \`${db_escaped}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;
create user if not exists '${user_escaped}'@'%' identified by '${password_escaped}';
grant select, insert, update, delete on \`${db_escaped}\`.* to '${user_escaped}'@'%';
flush privileges;
SQL

mysql --default-character-set=utf8mb4 \
  -h"${NACOS_MYSQL_HOST}" \
  -P"${NACOS_MYSQL_PORT}" \
  -uroot \
  -p"${MYSQL_ROOT_PASSWORD}" \
  "${NACOS_MYSQL_DATABASE}" < /bootstrap/030_nacos_schema.sql

echo "[nacos-db-bootstrap] done."
```

- [ ] **Step 5: Validate the new DB bootstrap assets**

Run:
```bash
bash -n deploy/mysql-init/001_create_databases.sh
bash -n deploy/scripts/bootstrap-nacos-db.sh
rg -n 'NACOS_MYSQL_|create database if not exists \\`\\$\\{NACOS_MYSQL_DATABASE|create database if not exists \\`nacos\\`' \
  deploy/mysql-init/001_create_databases.sh deploy/scripts/bootstrap-nacos-db.sh
head -n 5 deploy/mysql-init/030_nacos_schema.sql
```
Expected:
- Both `bash -n` commands exit `0`.
- `rg` prints the new `NACOS` variable / schema lines.
- `head` shows the vendored schema file and provenance comment.

- [ ] **Step 6: Commit**

Run:
```bash
git add deploy/mysql-init/001_create_databases.sh deploy/mysql-init/030_nacos_schema.sql deploy/scripts/bootstrap-nacos-db.sh
git commit -m "feat(deploy): add Nacos database bootstrap assets"
```

---

### Task 2: Replace The Single Nacos Service With A Three-Node Cluster

**Files:**
- Modify: `deploy/compose.infra.nacos.yml`

- [ ] **Step 1: Capture the current single-node baseline**

Run:
```bash
sed -n '1,120p' deploy/compose.infra.nacos.yml
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
  config --services | rg '^nacos$'
```
Expected:
- The file shows a single `nacos` service with `MODE=standalone`.
- `rg '^nacos$'` prints one line: `nacos`.

- [ ] **Step 2: Rewrite `deploy/compose.infra.nacos.yml` into a cluster file**

Replace the file with this structure:
```yaml
x-nacos-common: &nacos-common
  image: nacos/nacos-server:v2.3.2-slim
  mem_limit: ${NACOS_MEM_LIMIT:-768m}
  environment:
    - MODE=cluster
    - NACOS_AUTH_ENABLE=false
    - PREFER_HOST_MODE=hostname
    - NACOS_SERVERS=nacos-1:8848 nacos-2:8848 nacos-3:8848
    - SPRING_DATASOURCE_PLATFORM=mysql
    - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
    - MYSQL_SERVICE_PORT=${NACOS_MYSQL_PORT:-3306}
    - MYSQL_SERVICE_DB_NAME=${NACOS_MYSQL_DATABASE:-nacos}
    - MYSQL_SERVICE_USER=${NACOS_MYSQL_USER:-nacos}
    - MYSQL_SERVICE_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
    - MYSQL_DATABASE_NUM=1
    - JVM_XMS=${NACOS_JVM_XMS:-512m}
    - JVM_XMX=${NACOS_JVM_XMX:-512m}
    - JVM_XMN=${NACOS_JVM_XMN:-256m}
    - JVM_MS=${NACOS_JVM_MS:-128m}
    - JVM_MMS=${NACOS_JVM_MMS:-256m}
  depends_on:
    nacos-db-bootstrap:
      condition: service_completed_successfully

services:
  nacos-db-bootstrap:
    image: mysql:8.0
    entrypoint:
      - /bin/bash
      - /bootstrap-nacos-db.sh
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - NACOS_MYSQL_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
      - NACOS_MYSQL_PORT=${NACOS_MYSQL_PORT:-3306}
      - NACOS_MYSQL_DATABASE=${NACOS_MYSQL_DATABASE:-nacos}
      - NACOS_MYSQL_USER=${NACOS_MYSQL_USER:-nacos}
      - NACOS_MYSQL_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
    volumes:
      - ./mysql-init/030_nacos_schema.sql:/bootstrap/030_nacos_schema.sql:ro
      - ./scripts/bootstrap-nacos-db.sh:/bootstrap-nacos-db.sh:ro
    depends_on:
      mysql-primary:
        condition: service_healthy
    restart: "no"

  nacos-1:
    <<: *nacos-common
    hostname: nacos-1
    environment:
      - MODE=cluster
      - NACOS_AUTH_ENABLE=false
      - PREFER_HOST_MODE=hostname
      - NACOS_SERVERS=nacos-1:8848 nacos-2:8848 nacos-3:8848
      - NACOS_SERVER_IP=nacos-1
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
      - MYSQL_SERVICE_PORT=${NACOS_MYSQL_PORT:-3306}
      - MYSQL_SERVICE_DB_NAME=${NACOS_MYSQL_DATABASE:-nacos}
      - MYSQL_SERVICE_USER=${NACOS_MYSQL_USER:-nacos}
      - MYSQL_SERVICE_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
      - MYSQL_DATABASE_NUM=1
      - JVM_XMS=${NACOS_JVM_XMS:-512m}
      - JVM_XMX=${NACOS_JVM_XMX:-512m}
      - JVM_XMN=${NACOS_JVM_XMN:-256m}
      - JVM_MS=${NACOS_JVM_MS:-128m}
      - JVM_MMS=${NACOS_JVM_MMS:-256m}
    ports:
      - 127.0.0.1:${NACOS_HOST_PORT:-18848}:8848

  nacos-2:
    <<: *nacos-common
    hostname: nacos-2
    environment:
      - MODE=cluster
      - NACOS_AUTH_ENABLE=false
      - PREFER_HOST_MODE=hostname
      - NACOS_SERVERS=nacos-1:8848 nacos-2:8848 nacos-3:8848
      - NACOS_SERVER_IP=nacos-2
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
      - MYSQL_SERVICE_PORT=${NACOS_MYSQL_PORT:-3306}
      - MYSQL_SERVICE_DB_NAME=${NACOS_MYSQL_DATABASE:-nacos}
      - MYSQL_SERVICE_USER=${NACOS_MYSQL_USER:-nacos}
      - MYSQL_SERVICE_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
      - MYSQL_DATABASE_NUM=1
      - JVM_XMS=${NACOS_JVM_XMS:-512m}
      - JVM_XMX=${NACOS_JVM_XMX:-512m}
      - JVM_XMN=${NACOS_JVM_XMN:-256m}
      - JVM_MS=${NACOS_JVM_MS:-128m}
      - JVM_MMS=${NACOS_JVM_MMS:-256m}

  nacos-3:
    <<: *nacos-common
    hostname: nacos-3
    environment:
      - MODE=cluster
      - NACOS_AUTH_ENABLE=false
      - PREFER_HOST_MODE=hostname
      - NACOS_SERVERS=nacos-1:8848 nacos-2:8848 nacos-3:8848
      - NACOS_SERVER_IP=nacos-3
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
      - MYSQL_SERVICE_PORT=${NACOS_MYSQL_PORT:-3306}
      - MYSQL_SERVICE_DB_NAME=${NACOS_MYSQL_DATABASE:-nacos}
      - MYSQL_SERVICE_USER=${NACOS_MYSQL_USER:-nacos}
      - MYSQL_SERVICE_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
      - MYSQL_DATABASE_NUM=1
      - JVM_XMS=${NACOS_JVM_XMS:-512m}
      - JVM_XMX=${NACOS_JVM_XMX:-512m}
      - JVM_XMN=${NACOS_JVM_XMN:-256m}
      - JVM_MS=${NACOS_JVM_MS:-128m}
      - JVM_MMS=${NACOS_JVM_MMS:-256m}
```

Rules for this step:
- Keep `NACOS_HOST_PORT` on `nacos-1` only.
- Do not reintroduce a `nacos` proxy service.
- Use `service_completed_successfully` for `nacos-db-bootstrap`.

- [ ] **Step 3: Validate the cluster compose file**

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
  config --services | rg '^nacos-(db-bootstrap|1|2|3)$'
```
Expected:
- `rg` prints four lines:
  - `nacos-db-bootstrap`
  - `nacos-1`
  - `nacos-2`
  - `nacos-3`

- [ ] **Step 4: Commit**

Run:
```bash
git add deploy/compose.infra.nacos.yml
git commit -m "feat(deploy): switch Nacos infra to cluster mode"
```

---

### Task 3: Update Runtime Defaults And Operator Env Defaults

**Files:**
- Modify: `deploy/compose.runtime.yml`
- Modify: `deploy/.env.example`

- [ ] **Step 1: Capture the pre-change runtime defaults**

Run:
```bash
rg -n 'NACOS_SERVER_ADDR|SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR' deploy/compose.runtime.yml
rg -n '^NACOS_SERVER_ADDR=' deploy/.env.example
rg -n '^[[:space:]]+nacos:$' deploy/compose.runtime.yml
```
Expected:
- Runtime defaults still point to `nacos:8848`.
- `.env.example` contains `NACOS_SERVER_ADDR=nacos:8848`.
- `depends_on` still references `nacos`.

- [ ] **Step 2: Replace all runtime defaults with the three-address cluster list**

In `deploy/compose.runtime.yml`, replace every occurrence of:
```yaml
- NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
- SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
```
with:
```yaml
- NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos-1:8848,nacos-2:8848,nacos-3:8848}
- SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos-1:8848,nacos-2:8848,nacos-3:8848}
```

Also replace every `depends_on` entry:
```yaml
      nacos:
        condition: service_started
```
with:
```yaml
      nacos-db-bootstrap:
        condition: service_completed_successfully
      nacos-1:
        condition: service_started
```

- [ ] **Step 3: Extend `deploy/.env.example` with explicit Nacos cluster defaults**

Replace:
```dotenv
NACOS_SERVER_ADDR=nacos:8848
NACOS_HOST_PORT=18848
```
with:
```dotenv
NACOS_SERVER_ADDR=nacos-1:8848,nacos-2:8848,nacos-3:8848
NACOS_HOST_PORT=18848
NACOS_MYSQL_HOST=mysql-primary
NACOS_MYSQL_PORT=3306
NACOS_MYSQL_DATABASE=nacos
NACOS_MYSQL_USER=nacos
NACOS_MYSQL_PASSWORD=nacospass
```

- [ ] **Step 4: Validate the new defaults**

Run:
```bash
rg -n 'NACOS_SERVER_ADDR=.*nacos:8848|NACOS_SERVER_ADDR:-nacos:8848' deploy/compose.runtime.yml deploy/.env.example
rg -n '^[[:space:]]+nacos:$' deploy/compose.runtime.yml
rg -n 'nacos-db-bootstrap|nacos-1:8848,nacos-2:8848,nacos-3:8848' deploy/compose.runtime.yml deploy/.env.example
```
Expected:
- The first two `rg` commands exit `1`.
- The last `rg` command prints the new bootstrap dependency and the three-address defaults.

- [ ] **Step 5: Commit**

Run:
```bash
git add deploy/compose.runtime.yml deploy/.env.example
git commit -m "feat(deploy): point runtime to the Nacos cluster"
```

---

### Task 4: Update Operator Docs To Describe The Nacos Cluster

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/LOCAL_HA.md`

- [ ] **Step 1: Capture the current single-node wording**

Run:
```bash
rg -n '单节点 .*Nacos|nacos:8848|Nacos 注册检查' \
  deploy/README.md docs/DEPLOYMENT.md docs/ARCHITECTURE.md docs/LOCAL_HA.md
```
Expected:
- `rg` prints the existing single-node wording and the current registration-check sections.

- [ ] **Step 2: Update the docs with the cluster wording and checks**

Apply these concrete changes:

In `deploy/README.md`, replace:
```md
- `compose.infra.nacos.yml`：单节点 `Nacos` 注册中心。
- 服务发现：单节点 `Nacos`，本机检查入口 `http://localhost:18848/nacos`
```
with:
```md
- `compose.infra.nacos.yml`：`Nacos x3` 集群与 `nacos-db-bootstrap`。
- 服务发现：`Nacos x3` 集群，业务默认连接 `nacos-1:8848,nacos-2:8848,nacos-3:8848`；本机检查入口仍为 `http://localhost:18848/nacos`
```

In `docs/DEPLOYMENT.md`, replace:
```md
- `deploy/compose.infra.nacos.yml`
  - 注册发现：单节点 `Nacos`
```
with:
```md
- `deploy/compose.infra.nacos.yml`
  - 注册发现：`Nacos x3` 集群与 `nacos-db-bootstrap`
```

In `docs/ARCHITECTURE.md`, replace the discovery summary with:
```md
> 业务服务之间的注册发现由三节点 `Nacos` 集群承担：`community-gateway` 的 HTTP 路由走 Spring Cloud Gateway `lb://serviceId`，`/ws/im` worker 列表也由 Nacos metadata 提供。
```

In `docs/LOCAL_HA.md`, keep the existing registration-check commands on `http://localhost:18848/...`, but add a short note before that section:
```md
`localhost:18848` 仅映射到 `nacos-1` 作为 operator 检查入口；业务容器默认连接 `nacos-1:8848,nacos-2:8848,nacos-3:8848`。
```

- [ ] **Step 3: Validate the doc migration**

Run:
```bash
rg -n '单节点 .*Nacos|NACOS_SERVER_ADDR=nacos:8848' \
  deploy/README.md docs/DEPLOYMENT.md docs/ARCHITECTURE.md docs/LOCAL_HA.md deploy/.env.example
rg -n 'Nacos x3|nacos-db-bootstrap|nacos-1:8848,nacos-2:8848,nacos-3:8848' \
  deploy/README.md docs/DEPLOYMENT.md docs/ARCHITECTURE.md docs/LOCAL_HA.md
```
Expected:
- The first `rg` exits `1`.
- The second `rg` prints the new cluster wording.

- [ ] **Step 4: Commit**

Run:
```bash
git add deploy/README.md docs/DEPLOYMENT.md docs/ARCHITECTURE.md docs/LOCAL_HA.md
git commit -m "docs(deploy): document the Nacos cluster topology"
```

---

## Verification Checklist

- [ ] **Step 1: Render every supported compose stack**

Run:
```bash
make config
make config-debug
make config-obs
make config-elastic
make config-elastic-json
```
Expected:
- All five commands exit `0`.

- [ ] **Step 2: Verify the rendered service list includes the Nacos cluster**

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
  config --services | rg '^nacos-(db-bootstrap|1|2|3)$'
```
Expected:
- The output contains `nacos-db-bootstrap`, `nacos-1`, `nacos-2`, and `nacos-3`.

- [ ] **Step 3: Smoke-test the cluster directly in an isolated Compose project if Docker resources are available**

Run:
```bash
docker compose -p community-nacos-smoke --env-file deploy/.env \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  up -d mysql-primary nacos-db-bootstrap nacos-1 nacos-2 nacos-3

curl -fsS http://localhost:18848/nacos/v1/ns/operator/servers
```
Expected:
- `up -d` exits `0`.
- The `curl` response shows the `Nacos` cluster server list.

Then clean up:
```bash
docker compose -p community-nacos-smoke --env-file deploy/.env \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  down
```

- [ ] **Step 4: If local resources allow a full-stack run, verify service registration through the cluster**

Run:
```bash
docker compose -p community-nacos-full-smoke --env-file deploy/.env \
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
  up -d --build

curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=community-app"
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-core"
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```
Expected:
- The stack starts successfully.
- Each `curl` response returns registered instances for the corresponding service.

Then clean up:
```bash
docker compose -p community-nacos-full-smoke --env-file deploy/.env \
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
  down
```

- [ ] **Step 5: Final repo sanity check**

Run:
```bash
git status --short
git log --oneline --decorate -5
```
Expected:
- `git status --short` is empty.
- The recent history includes the task commits for DB bootstrap, compose cluster wiring, runtime defaults, and docs.
