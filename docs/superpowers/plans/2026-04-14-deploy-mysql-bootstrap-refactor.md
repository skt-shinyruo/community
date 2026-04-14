# Deploy MySQL Bootstrap Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mixed `deploy/mysql-init/` layout with explicit subsystem-owned bootstrap assets under `deploy/mysql/`, then rewire compose, tests, and live docs to use the new source of truth.

**Architecture:** Keep MySQL first-boot behavior for `community` and `im_core`, but replace the implicit directory mount with explicit ordered file mounts. Move Nacos and XXL-JOB bootstrap assets into their own directories and bootstrap sidecars so each subsystem owns its own database lifecycle. Treat `deploy/mysql/community/*.sql` as the only live source of truth for fresh-volume community bootstrap and Mock Data Studio replay.

**Tech Stack:** Docker Compose, Bash, MySQL 8 init scripts, SQL DDL, Node test runner, Maven/JUnit 5

---

## File map

### Create
- `deploy/mysql/primary-init/001_create_databases.sh`
  - mysql-primary first-boot database/user bootstrap for `community` and `im_core` only.
- `deploy/mysql/community/001_bootstrap.sh`
  - Explicit replay script for ordered community bootstrap files; used by `mock-data-studio-db-bootstrap`.
- `deploy/mysql/community/010_schema_shared.sql`
  - Shared tables such as outbox and HTTP idempotency.
- `deploy/mysql/community/011_schema_demo_metadata.sql`
  - `demo_*` and `ai_config` metadata tables that must exist on fresh volumes.
- `deploy/mysql/community/020_schema_identity.sql`
  - `user`, `auth_refresh_token`, and identity compatibility DDL.
- `deploy/mysql/community/030_schema_growth_reward.sql`
  - score/reward/grant tables.
- `deploy/mysql/community/031_schema_growth_wallet.sql`
  - wallet, recharge, withdraw, transfer, and admin action tables.
- `deploy/mysql/community/032_schema_growth_market.sql`
  - market listing/order/dispute/shipment tables.
- `deploy/mysql/community/033_schema_growth_task.sql`
  - task progress/event tables and task seed rows.
- `deploy/mysql/community/040_schema_content_core.sql`
  - content/report/moderation tables.
- `deploy/mysql/community/041_schema_content_compat.sql`
  - content compatibility/index backfill DDL.
- `deploy/mysql/community/050_schema_social.sql`
  - social like/follow/block tables.
- `deploy/mysql/community/060_schema_message.sql`
  - station-message schema.
- `deploy/mysql/community/070_schema_im_core.sql`
  - `im_core` schema tables.
- `deploy/mysql/community/080_schema_search.sql`
  - search-owned DB assets.
- `deploy/mysql/community/090_seed_identity.sql`
  - dev/demo identity seed data.
- `deploy/mysql/nacos/001_bootstrap.sh`
  - one-shot Nacos DB bootstrap.
- `deploy/mysql/nacos/010_schema.sql`
  - vendored Nacos schema.
- `deploy/mysql/xxl-job/001_bootstrap.sh`
  - one-shot XXL-JOB DB bootstrap.
- `deploy/mysql/xxl-job/010_schema.sql`
  - vendored XXL-JOB schema.
- `deploy/mysql/xxl-job/020_seed_local.sh`
  - local XXL-JOB seed data replay script.

### Modify
- `deploy/compose.infra.mysql.yml`
  - explicit mysql-primary mounts for `primary-init` and ordered `community/*.sql` files.
- `deploy/compose.infra.mock-data-studio-bootstrap.yml`
  - run `deploy/mysql/community/001_bootstrap.sh` instead of replaying `010_schema.sql`.
- `deploy/compose.infra.nacos.yml`
  - mount Nacos assets from `deploy/mysql/nacos/`.
- `deploy/compose.infra.xxl-job.yml`
  - add `xxl-job-db-bootstrap` and gate admin services on it.
- `tools/mock-data-studio/test/batch-repository.test.mjs`
  - stop reading `deploy/mysql-init/010_schema.sql`; assert against `deploy/mysql/community` sources.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`
  - read concatenated `deploy/mysql/community/*.sql` instead of the deleted bundle.
- `backend/community-app/src/test/java/com/nowcoder/community/market/LegacyVirtualMarketRetirementTest.java`
  - same deploy-schema source-of-truth update.
- `backend/community-app/src/test/java/com/nowcoder/community/support/DeployCommunitySchema.java`
  - shared helper to read ordered deploy schema files for Java tests.
- `deploy/README.md`
  - document the new `deploy/mysql/` layout.
- `docs/DATA_MODEL.md`
  - point MySQL bootstrap references at `deploy/mysql/primary-init` and `deploy/mysql/community`.
- `docs/DEPLOYMENT.md`
  - explain the new bootstrap wiring.
- `docs/DEV_ONLY.md`
  - point demo user seed references at `deploy/mysql/community/090_seed_identity.sql`.

### Delete or move away
- `deploy/mysql-init/001_create_databases.sh`
- `deploy/mysql-init/010_schema.sql`
- `deploy/mysql-init/020_xxl_job_schema.sql`
- `deploy/mysql-init/021_seed_xxl_job.sh`
- `deploy/mysql-init/030_nacos_schema.sql`
- `deploy/scripts/bootstrap-nacos-db.sh`

### Deliberately unchanged
- `deploy/scripts/bootstrap-mysql-replication.sh`
  - replication bootstrap is outside this refactor.
- Historical records under `docs/superpowers/`
  - keep them as archival documents; the live path sweep in this plan intentionally targets active code/config/docs only.

---

### Task 1: Create the new bootstrap tree and split the community SQL bundle

**Files:**
- Create: `deploy/mysql/primary-init/001_create_databases.sh`
- Create: `deploy/mysql/community/010_schema_shared.sql`
- Create: `deploy/mysql/community/011_schema_demo_metadata.sql`
- Create: `deploy/mysql/community/020_schema_identity.sql`
- Create: `deploy/mysql/community/030_schema_growth_reward.sql`
- Create: `deploy/mysql/community/031_schema_growth_wallet.sql`
- Create: `deploy/mysql/community/032_schema_growth_market.sql`
- Create: `deploy/mysql/community/033_schema_growth_task.sql`
- Create: `deploy/mysql/community/040_schema_content_core.sql`
- Create: `deploy/mysql/community/041_schema_content_compat.sql`
- Create: `deploy/mysql/community/050_schema_social.sql`
- Create: `deploy/mysql/community/060_schema_message.sql`
- Create: `deploy/mysql/community/070_schema_im_core.sql`
- Create: `deploy/mysql/community/080_schema_search.sql`
- Create: `deploy/mysql/community/090_seed_identity.sql`

- [ ] **Step 1: Write the failing layout check**

```bash
for path in \
  deploy/mysql/primary-init/001_create_databases.sh \
  deploy/mysql/community/010_schema_shared.sql \
  deploy/mysql/community/011_schema_demo_metadata.sql \
  deploy/mysql/community/020_schema_identity.sql \
  deploy/mysql/community/030_schema_growth_reward.sql \
  deploy/mysql/community/031_schema_growth_wallet.sql \
  deploy/mysql/community/032_schema_growth_market.sql \
  deploy/mysql/community/033_schema_growth_task.sql \
  deploy/mysql/community/040_schema_content_core.sql \
  deploy/mysql/community/041_schema_content_compat.sql \
  deploy/mysql/community/050_schema_social.sql \
  deploy/mysql/community/060_schema_message.sql \
  deploy/mysql/community/070_schema_im_core.sql \
  deploy/mysql/community/080_schema_search.sql \
  deploy/mysql/community/090_seed_identity.sql
  do
  test -f "$path"
done
```

- [ ] **Step 2: Run the check to confirm the new tree does not exist yet**

Run: `for path in deploy/mysql/primary-init/001_create_databases.sh deploy/mysql/community/010_schema_shared.sql deploy/mysql/community/011_schema_demo_metadata.sql deploy/mysql/community/020_schema_identity.sql deploy/mysql/community/030_schema_growth_reward.sql deploy/mysql/community/031_schema_growth_wallet.sql deploy/mysql/community/032_schema_growth_market.sql deploy/mysql/community/033_schema_growth_task.sql deploy/mysql/community/040_schema_content_core.sql deploy/mysql/community/041_schema_content_compat.sql deploy/mysql/community/050_schema_social.sql deploy/mysql/community/060_schema_message.sql deploy/mysql/community/070_schema_im_core.sql deploy/mysql/community/080_schema_search.sql deploy/mysql/community/090_seed_identity.sql; do test -f "$path"; done`
Expected: FAIL on the first missing path.

- [ ] **Step 3: Create the new directories, move the bootstrap shell, and split `010_schema.sql` by exact ranges**

```bash
mkdir -p deploy/mysql/primary-init deploy/mysql/community
git mv deploy/mysql-init/001_create_databases.sh deploy/mysql/primary-init/001_create_databases.sh
python3 - <<'PY'
from pathlib import Path

source_path = Path('deploy/mysql-init/010_schema.sql')
lines = source_path.read_text().splitlines()
sections = {
    'deploy/mysql/community/010_schema_shared.sql': (17, 88),
    'deploy/mysql/community/011_schema_demo_metadata.sql': (89, 152),
    'deploy/mysql/community/020_schema_identity.sql': (153, 263),
    'deploy/mysql/community/030_schema_growth_reward.sql': (264, 336),
    'deploy/mysql/community/031_schema_growth_wallet.sql': (337, 405),
    'deploy/mysql/community/032_schema_growth_market.sql': (406, 545),
    'deploy/mysql/community/033_schema_growth_task.sql': (546, 606),
    'deploy/mysql/community/040_schema_content_core.sql': (607, 842),
    'deploy/mysql/community/041_schema_content_compat.sql': (843, 986),
    'deploy/mysql/community/050_schema_social.sql': (987, 1041),
    'deploy/mysql/community/060_schema_message.sql': (1042, 1083),
    'deploy/mysql/community/070_schema_im_core.sql': (1084, 1232),
    'deploy/mysql/community/080_schema_search.sql': (1233, 1239),
    'deploy/mysql/community/090_seed_identity.sql': (1240, 1252),
}
for dest, (start, end) in sections.items():
    Path(dest).write_text('\n'.join(lines[start - 1:end]) + '\n')
PY
```

- [ ] **Step 4: Run the split verification**

Run: `for path in deploy/mysql/primary-init/001_create_databases.sh deploy/mysql/community/010_schema_shared.sql deploy/mysql/community/011_schema_demo_metadata.sql deploy/mysql/community/020_schema_identity.sql deploy/mysql/community/030_schema_growth_reward.sql deploy/mysql/community/031_schema_growth_wallet.sql deploy/mysql/community/032_schema_growth_market.sql deploy/mysql/community/033_schema_growth_task.sql deploy/mysql/community/040_schema_content_core.sql deploy/mysql/community/041_schema_content_compat.sql deploy/mysql/community/050_schema_social.sql deploy/mysql/community/060_schema_message.sql deploy/mysql/community/070_schema_im_core.sql deploy/mysql/community/080_schema_search.sql deploy/mysql/community/090_seed_identity.sql; do test -f "$path"; done && rg -n '^-- Source:|^create table if not exists|^insert into user' deploy/mysql/community`
Expected: PASS and `rg` prints the new section files with DDL/seed lines.

- [ ] **Step 5: Commit the bootstrap tree split**

```bash
git add deploy/mysql/primary-init/001_create_databases.sh deploy/mysql/community
git commit -m "refactor(deploy): split community mysql bootstrap files"
```

### Task 2: Narrow mysql-primary init responsibility and add explicit community replay

**Files:**
- Modify: `deploy/mysql/primary-init/001_create_databases.sh`
- Create: `deploy/mysql/community/001_bootstrap.sh`

- [ ] **Step 1: Write the failing responsibility checks**

```bash
rg -n 'NACOS_MYSQL_|XXL_JOB_' deploy/mysql/primary-init/001_create_databases.sh
test -x deploy/mysql/community/001_bootstrap.sh
```

- [ ] **Step 2: Run the checks to confirm the copied script is still too broad and the replay script is missing**

Run: `rg -n 'NACOS_MYSQL_|XXL_JOB_' deploy/mysql/primary-init/001_create_databases.sh && test -x deploy/mysql/community/001_bootstrap.sh`
Expected: `rg` prints the Nacos/XXL lines, then `test -x` fails because `001_bootstrap.sh` does not exist yet.

- [ ] **Step 3: Replace the primary init script and add `community/001_bootstrap.sh`**

```bash
cat > deploy/mysql/primary-init/001_create_databases.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

# mysql-primary first-boot database/user bootstrap for community + im_core only.
# The MySQL image entrypoint executes this file from /docker-entrypoint-initdb.d
# only when the data directory is empty.

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-community}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MOCK_DATA_STUDIO_DB_USER="${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}"
MOCK_DATA_STUDIO_DB_PASSWORD="${MOCK_DATA_STUDIO_DB_PASSWORD:-mockdatastudiopass}"
IM_MYSQL_DATABASE="${IM_MYSQL_DATABASE:-im_core}"
IM_MYSQL_USER="${IM_MYSQL_USER:-im_core}"
IM_MYSQL_PASSWORD="${IM_MYSQL_PASSWORD:-imcorepass}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-primary-init] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

MYSQL_DATABASE_ESCAPED="$(sql_escape "${MYSQL_DATABASE}")"
MYSQL_USER_ESCAPED="$(sql_escape "${MYSQL_USER}")"
MYSQL_PASSWORD_ESCAPED="$(sql_escape "${MYSQL_PASSWORD}")"
MOCK_DATA_STUDIO_DB_USER_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_USER}")"
MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_PASSWORD}")"
IM_MYSQL_DATABASE_ESCAPED="$(sql_escape "${IM_MYSQL_DATABASE}")"
IM_MYSQL_USER_ESCAPED="$(sql_escape "${IM_MYSQL_USER}")"
IM_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${IM_MYSQL_PASSWORD}")"

echo "[mysql-primary-init] creating community/im_core databases and users..."

mysql --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
create database if not exists \`${MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${MYSQL_USER_ESCAPED}'@'%' identified by '${MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${MYSQL_DATABASE_ESCAPED}\`.* to '${MYSQL_USER_ESCAPED}'@'%';

create user if not exists '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%' identified by '${MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED}';
grant select, insert, update, delete, create, alter on \`${MYSQL_DATABASE_ESCAPED}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

create database if not exists \`${IM_MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${IM_MYSQL_USER_ESCAPED}'@'%' identified by '${IM_MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${IM_MYSQL_DATABASE_ESCAPED}\`.* to '${IM_MYSQL_USER_ESCAPED}'@'%';
grant select, insert, update, delete on \`${IM_MYSQL_DATABASE_ESCAPED}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

flush privileges;
SQL

echo "[mysql-primary-init] done."
EOF

cat > deploy/mysql/community/001_bootstrap.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-mysql-primary}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-/bootstrap/community}"
SCHEMA_FILES=(
  010_schema_shared.sql
  011_schema_demo_metadata.sql
  020_schema_identity.sql
  030_schema_growth_reward.sql
  031_schema_growth_wallet.sql
  032_schema_growth_market.sql
  033_schema_growth_task.sql
  040_schema_content_core.sql
  041_schema_content_compat.sql
  050_schema_social.sql
  060_schema_message.sql
  070_schema_im_core.sql
  080_schema_search.sql
  090_seed_identity.sql
)

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[community-bootstrap] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

mysql_base_args=(
  --default-character-set=utf8mb4
  "-h${MYSQL_HOST}"
  "-P${MYSQL_PORT}"
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
)

for schema_file in "${SCHEMA_FILES[@]}"; do
  echo "[community-bootstrap] applying ${schema_file}..."
  mysql "${mysql_base_args[@]}" < "${BOOTSTRAP_DIR}/${schema_file}"
done

echo "[community-bootstrap] done."
EOF

chmod +x deploy/mysql/primary-init/001_create_databases.sh deploy/mysql/community/001_bootstrap.sh
```

- [ ] **Step 4: Run the shell validation and scope checks**

Run: `bash -n deploy/mysql/primary-init/001_create_databases.sh && bash -n deploy/mysql/community/001_bootstrap.sh && rg -n 'NACOS_MYSQL_|XXL_JOB_' deploy/mysql/primary-init/001_create_databases.sh`
Expected: both `bash -n` commands PASS and the `rg` command exits `1` with no matches.

- [ ] **Step 5: Commit the narrowed bootstrap scripts**

```bash
git add deploy/mysql/primary-init/001_create_databases.sh deploy/mysql/community/001_bootstrap.sh
git commit -m "refactor(deploy): narrow primary mysql bootstrap scope"
```

### Task 3: Rewire mysql-primary and Mock Data Studio bootstrap to the new community sources

**Files:**
- Modify: `deploy/compose.infra.mysql.yml`
- Modify: `deploy/compose.infra.mock-data-studio-bootstrap.yml`

- [ ] **Step 1: Write the failing compose checks**

```bash
rg -n './mysql-init:/docker-entrypoint-initdb.d:ro|/bootstrap/010_schema.sql|./mysql-init:/bootstrap:ro' \
  deploy/compose.infra.mysql.yml deploy/compose.infra.mock-data-studio-bootstrap.yml
```

- [ ] **Step 2: Run the checks to confirm the old mounts are still active**

Run: `rg -n './mysql-init:/docker-entrypoint-initdb.d:ro|/bootstrap/010_schema.sql|./mysql-init:/bootstrap:ro' deploy/compose.infra.mysql.yml deploy/compose.infra.mock-data-studio-bootstrap.yml`
Expected: PASS with matches showing the mixed directory mount and `010_schema.sql` replay.

- [ ] **Step 3: Overwrite the two compose files with explicit community mounts**

```bash
cat > deploy/compose.infra.mysql.yml <<'EOF'
services:
  mysql-primary:
    image: mysql:8.0
    environment:
    - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
    - MYSQL_ROOT_HOST=%
    - MYSQL_DATABASE=${MYSQL_DATABASE:-community}
    - MYSQL_USER=${MYSQL_USER:-community}
    - MYSQL_PASSWORD=${MYSQL_PASSWORD:-communitypass}
    - IM_MYSQL_DATABASE=${IM_MYSQL_DATABASE:-im_core}
    - IM_MYSQL_USER=${IM_MYSQL_USER:-im_core}
    - IM_MYSQL_PASSWORD=${IM_MYSQL_PASSWORD:-imcorepass}
    - MOCK_DATA_STUDIO_DB_USER=${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}
    - MOCK_DATA_STUDIO_DB_PASSWORD=${MOCK_DATA_STUDIO_DB_PASSWORD:-mockdatastudiopass}
    command:
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --default-time-zone=+00:00
    volumes:
    - mysql_primary_data:/var/lib/mysql
    - ./mysql/primary-init/001_create_databases.sh:/docker-entrypoint-initdb.d/001_create_databases.sh:ro
    - ./mysql/community/010_schema_shared.sql:/docker-entrypoint-initdb.d/010_schema_shared.sql:ro
    - ./mysql/community/011_schema_demo_metadata.sql:/docker-entrypoint-initdb.d/011_schema_demo_metadata.sql:ro
    - ./mysql/community/020_schema_identity.sql:/docker-entrypoint-initdb.d/020_schema_identity.sql:ro
    - ./mysql/community/030_schema_growth_reward.sql:/docker-entrypoint-initdb.d/030_schema_growth_reward.sql:ro
    - ./mysql/community/031_schema_growth_wallet.sql:/docker-entrypoint-initdb.d/031_schema_growth_wallet.sql:ro
    - ./mysql/community/032_schema_growth_market.sql:/docker-entrypoint-initdb.d/032_schema_growth_market.sql:ro
    - ./mysql/community/033_schema_growth_task.sql:/docker-entrypoint-initdb.d/033_schema_growth_task.sql:ro
    - ./mysql/community/040_schema_content_core.sql:/docker-entrypoint-initdb.d/040_schema_content_core.sql:ro
    - ./mysql/community/041_schema_content_compat.sql:/docker-entrypoint-initdb.d/041_schema_content_compat.sql:ro
    - ./mysql/community/050_schema_social.sql:/docker-entrypoint-initdb.d/050_schema_social.sql:ro
    - ./mysql/community/060_schema_message.sql:/docker-entrypoint-initdb.d/060_schema_message.sql:ro
    - ./mysql/community/070_schema_im_core.sql:/docker-entrypoint-initdb.d/070_schema_im_core.sql:ro
    - ./mysql/community/080_schema_search.sql:/docker-entrypoint-initdb.d/080_schema_search.sql:ro
    - ./mysql/community/090_seed_identity.sql:/docker-entrypoint-initdb.d/090_seed_identity.sql:ro
    - ./mysql/conf/primary.cnf:/etc/mysql/conf.d/primary.cnf:ro
    healthcheck:
      test:
      - CMD-SHELL
      - mysqladmin ping -h 127.0.0.1 -uroot -p${MYSQL_ROOT_PASSWORD} --silent
      interval: 5s
      timeout: 3s
      retries: 30
    networks:
      default:
        aliases:
        - mysql
  mysql-replica-1:
    image: mysql:8.0
    environment:
    - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
    - MYSQL_ROOT_HOST=%
    command:
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --default-time-zone=+00:00
    - --server-id=2
    volumes:
    - mysql_replica_1_data:/var/lib/mysql
    - ./mysql/conf/replica.cnf:/etc/mysql/conf.d/replica.cnf:ro
    healthcheck:
      test:
      - CMD-SHELL
      - mysqladmin ping -h 127.0.0.1 -uroot -p${MYSQL_ROOT_PASSWORD} --silent
      interval: 5s
      timeout: 3s
      retries: 30
  mysql-replica-2:
    image: mysql:8.0
    environment:
    - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
    - MYSQL_ROOT_HOST=%
    command:
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --default-time-zone=+00:00
    - --server-id=3
    volumes:
    - mysql_replica_2_data:/var/lib/mysql
    - ./mysql/conf/replica.cnf:/etc/mysql/conf.d/replica.cnf:ro
    healthcheck:
      test:
      - CMD-SHELL
      - mysqladmin ping -h 127.0.0.1 -uroot -p${MYSQL_ROOT_PASSWORD} --silent
      interval: 5s
      timeout: 3s
      retries: 30
  mysql-replication-bootstrap:
    image: mysql:8.0
    entrypoint:
    - /bin/bash
    - /bootstrap-mysql-replication.sh
    environment:
    - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
    - MYSQL_PRIMARY_HOST=${DB_PRIMARY_HOST:-mysql-primary}
    - MYSQL_REPLICA_HOSTS=mysql-replica-1,mysql-replica-2
    - MYSQL_REPLICATION_USER=${MYSQL_REPLICATION_USER:-replicator}
    - MYSQL_REPLICATION_PASSWORD=${MYSQL_REPLICATION_PASSWORD:-replicatorpass}
    volumes:
    - ./scripts/bootstrap-mysql-replication.sh:/bootstrap-mysql-replication.sh:ro
    depends_on:
      mysql-primary:
        condition: service_healthy
      mysql-replica-1:
        condition: service_healthy
      mysql-replica-2:
        condition: service_healthy
    restart: 'no'
EOF

cat > deploy/compose.infra.mock-data-studio-bootstrap.yml <<'EOF'
services:
  mock-data-studio-db-bootstrap:
    image: mysql:8.0
    container_name: community-mock-data-studio-db-bootstrap
    entrypoint:
    - /bin/bash
    - -lc
    command:
    - "MYSQL_HOST=\"$${DB_PRIMARY_HOST:-mysql-primary}\" MYSQL_PORT=\"3306\" MYSQL_ROOT_PASSWORD=\"$${MYSQL_ROOT_PASSWORD}\" BOOTSTRAP_DIR=/bootstrap/community /bootstrap/community/001_bootstrap.sh\nsql_escape() {\n  local value=\"$${1//\\\\/\\\\\\\\}\"\n  value=\"$${value//\\'/\\'\\'}\"\n  printf \"%s\" \"$${value}\"\n}\nMYSQL_DATABASE_ESCAPED=\"$$(sql_escape \"$${MYSQL_DATABASE:-community}\")\"\nIM_MYSQL_DATABASE_ESCAPED=\"$$(sql_escape \"$${IM_MYSQL_DATABASE:-im_core}\")\"\nSTUDIO_USER_ESCAPED=\"$$(sql_escape \"$${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}\")\"\nSTUDIO_PASSWORD_ESCAPED=\"$$(sql_escape \"$${MOCK_DATA_STUDIO_DB_PASSWORD:-mockdatastudiopass}\")\"\nmysql --default-character-set=utf8mb4 -h\"$${DB_PRIMARY_HOST:-mysql-primary}\" -uroot -p\"$${MYSQL_ROOT_PASSWORD}\" <<SQL\ncreate user if not exists '$${STUDIO_USER_ESCAPED}'@'%' identified by '$${STUDIO_PASSWORD_ESCAPED}';\ngrant select, insert, update, delete, create, alter on \\\`$${MYSQL_DATABASE_ESCAPED}\\\`.* to '$${STUDIO_USER_ESCAPED}'@'%';\ngrant select, insert, update, delete on \\\`$${IM_MYSQL_DATABASE_ESCAPED}\\\`.* to '$${STUDIO_USER_ESCAPED}'@'%';\nflush privileges;\nSQL\n"
    environment:
    - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
    - DB_PRIMARY_HOST=${DB_PRIMARY_HOST:-mysql-primary}
    - MYSQL_DATABASE=${MYSQL_DATABASE:-community}
    - IM_MYSQL_DATABASE=${IM_MYSQL_DATABASE:-im_core}
    - MOCK_DATA_STUDIO_DB_USER=${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}
    - MOCK_DATA_STUDIO_DB_PASSWORD=${MOCK_DATA_STUDIO_DB_PASSWORD:-mockdatastudiopass}
    volumes:
    - ./mysql/community:/bootstrap/community:ro
    depends_on:
      mysql-primary:
        condition: service_healthy
EOF
```

- [ ] **Step 4: Render compose and verify the new paths**

Run: `docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.mock-data-studio-bootstrap.yml config > /tmp/community-mysql-bootstrap.yml && rg -n 'docker-entrypoint-initdb.d/010_schema_shared.sql|/bootstrap/community/001_bootstrap.sh' /tmp/community-mysql-bootstrap.yml`
Expected: PASS and `rg` prints the explicit mysql-primary mount plus the Mock Data Studio replay command.

- [ ] **Step 5: Commit the compose rewiring for community bootstrap**

```bash
git add deploy/compose.infra.mysql.yml deploy/compose.infra.mock-data-studio-bootstrap.yml
git commit -m "refactor(deploy): rewire community mysql bootstrap mounts"
```

### Task 4: Move Nacos bootstrap assets under `deploy/mysql/nacos` and rewire Nacos compose

**Files:**
- Create: `deploy/mysql/nacos/001_bootstrap.sh`
- Create: `deploy/mysql/nacos/010_schema.sql`
- Modify: `deploy/compose.infra.nacos.yml`
- Delete or move away: `deploy/scripts/bootstrap-nacos-db.sh`
- Delete or move away: `deploy/mysql-init/030_nacos_schema.sql`

- [ ] **Step 1: Write the failing Nacos asset checks**

```bash
test -f deploy/mysql/nacos/001_bootstrap.sh
test -f deploy/mysql/nacos/010_schema.sql
rg -n './mysql-init/030_nacos_schema.sql|./scripts/bootstrap-nacos-db.sh' deploy/compose.infra.nacos.yml
```

- [ ] **Step 2: Run the checks to confirm Nacos still uses the old locations**

Run: `test -f deploy/mysql/nacos/001_bootstrap.sh && test -f deploy/mysql/nacos/010_schema.sql && rg -n './mysql-init/030_nacos_schema.sql|./scripts/bootstrap-nacos-db.sh' deploy/compose.infra.nacos.yml`
Expected: FAIL because the new files do not exist yet.

- [ ] **Step 3: Move the vendored schema, replace the bootstrap script, and overwrite the compose file**

```bash
mkdir -p deploy/mysql/nacos
git mv deploy/mysql-init/030_nacos_schema.sql deploy/mysql/nacos/010_schema.sql
cat > deploy/mysql/nacos/001_bootstrap.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
NACOS_MYSQL_HOST="${NACOS_MYSQL_HOST:-mysql-primary}"
NACOS_MYSQL_PORT="${NACOS_MYSQL_PORT:-3306}"
NACOS_MYSQL_DATABASE="${NACOS_MYSQL_DATABASE:-nacos}"
NACOS_MYSQL_USER="${NACOS_MYSQL_USER:-nacos}"
NACOS_MYSQL_PASSWORD="${NACOS_MYSQL_PASSWORD:-nacospass}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-/bootstrap}"

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

mysql_base_args=(
  --default-character-set=utf8mb4
  "-h${NACOS_MYSQL_HOST}"
  "-P${NACOS_MYSQL_PORT}"
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
)

echo "[nacos-db-bootstrap] ensuring database and runtime grants..."
mysql "${mysql_base_args[@]}" <<SQL
create database if not exists \`${db_escaped}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;
create user if not exists '${user_escaped}'@'%' identified by '${password_escaped}';
grant select, insert, update, delete on \`${db_escaped}\`.* to '${user_escaped}'@'%';
flush privileges;
SQL

table_exists="$(
  mysql "${mysql_base_args[@]}" -N -B <<SQL
select count(*)
from information_schema.tables
where table_schema = '${db_escaped}'
  and table_name = 'config_info';
SQL
)"

if [[ "${table_exists}" == "0" ]]; then
  echo "[nacos-db-bootstrap] importing Nacos schema..."
  mysql "${mysql_base_args[@]}" "${NACOS_MYSQL_DATABASE}" < "${BOOTSTRAP_DIR}/010_schema.sql"
else
  echo "[nacos-db-bootstrap] schema already initialized; skipping import."
fi

echo "[nacos-db-bootstrap] done."
EOF
chmod +x deploy/mysql/nacos/001_bootstrap.sh
git rm deploy/scripts/bootstrap-nacos-db.sh
cat > deploy/compose.infra.nacos.yml <<'EOF'
x-nacos-common: &nacos-common
  image: nacos/nacos-server:v2.3.2-slim
  mem_limit: ${NACOS_MEM_LIMIT:-768m}
  depends_on:
    nacos-db-bootstrap:
      condition: service_completed_successfully

services:
  nacos-db-bootstrap:
    image: mysql:8.0
    entrypoint:
    - /bin/bash
    - /bootstrap/001_bootstrap.sh
    environment:
    - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
    - NACOS_MYSQL_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
    - NACOS_MYSQL_PORT=${NACOS_MYSQL_PORT:-3306}
    - NACOS_MYSQL_DATABASE=${NACOS_MYSQL_DATABASE:-nacos}
    - NACOS_MYSQL_USER=${NACOS_MYSQL_USER:-nacos}
    - NACOS_MYSQL_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
    - BOOTSTRAP_DIR=/bootstrap
    volumes:
    - ./mysql/nacos:/bootstrap:ro
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
    - SPRING_DATASOURCE_PLATFORM=mysql
    - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
    - MYSQL_SERVICE_PORT=${NACOS_MYSQL_PORT:-3306}
    - MYSQL_SERVICE_DB_NAME=${NACOS_MYSQL_DATABASE:-nacos}
    - MYSQL_SERVICE_USER=${NACOS_MYSQL_USER:-nacos}
    - MYSQL_SERVICE_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
    - MYSQL_SERVICE_DB_PARAM=${NACOS_MYSQL_DB_PARAM:-characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true}
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
    - SPRING_DATASOURCE_PLATFORM=mysql
    - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
    - MYSQL_SERVICE_PORT=${NACOS_MYSQL_PORT:-3306}
    - MYSQL_SERVICE_DB_NAME=${NACOS_MYSQL_DATABASE:-nacos}
    - MYSQL_SERVICE_USER=${NACOS_MYSQL_USER:-nacos}
    - MYSQL_SERVICE_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
    - MYSQL_SERVICE_DB_PARAM=${NACOS_MYSQL_DB_PARAM:-characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true}
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
    - SPRING_DATASOURCE_PLATFORM=mysql
    - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql-primary}
    - MYSQL_SERVICE_PORT=${NACOS_MYSQL_PORT:-3306}
    - MYSQL_SERVICE_DB_NAME=${NACOS_MYSQL_DATABASE:-nacos}
    - MYSQL_SERVICE_USER=${NACOS_MYSQL_USER:-nacos}
    - MYSQL_SERVICE_PASSWORD=${NACOS_MYSQL_PASSWORD:-nacospass}
    - MYSQL_SERVICE_DB_PARAM=${NACOS_MYSQL_DB_PARAM:-characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true}
    - MYSQL_DATABASE_NUM=1
    - JVM_XMS=${NACOS_JVM_XMS:-512m}
    - JVM_XMX=${NACOS_JVM_XMX:-512m}
    - JVM_XMN=${NACOS_JVM_XMN:-256m}
    - JVM_MS=${NACOS_JVM_MS:-128m}
    - JVM_MMS=${NACOS_JVM_MMS:-256m}
EOF
```

- [ ] **Step 4: Validate the Nacos bootstrap assets and compose rendering**

Run: `bash -n deploy/mysql/nacos/001_bootstrap.sh && docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.nacos.yml config > /tmp/community-nacos-bootstrap.yml && rg -n './mysql/nacos:/bootstrap:ro|/bootstrap/001_bootstrap.sh' /tmp/community-nacos-bootstrap.yml`
Expected: PASS and `rg` prints the new mount plus the new script path.

- [ ] **Step 5: Commit the Nacos asset migration**

```bash
git add deploy/mysql/nacos/001_bootstrap.sh deploy/mysql/nacos/010_schema.sql deploy/compose.infra.nacos.yml
git commit -m "refactor(deploy): isolate nacos mysql bootstrap assets"
```

### Task 5: Add an XXL-JOB database bootstrap sidecar and move XXL assets under `deploy/mysql/xxl-job`

**Files:**
- Create: `deploy/mysql/xxl-job/001_bootstrap.sh`
- Create: `deploy/mysql/xxl-job/010_schema.sql`
- Create: `deploy/mysql/xxl-job/020_seed_local.sh`
- Modify: `deploy/compose.infra.xxl-job.yml`

- [ ] **Step 1: Write the failing XXL bootstrap checks**

```bash
test -f deploy/mysql/xxl-job/001_bootstrap.sh
test -f deploy/mysql/xxl-job/010_schema.sql
test -f deploy/mysql/xxl-job/020_seed_local.sh
rg -n 'xxl-job-db-bootstrap|mysql-primary:' deploy/compose.infra.xxl-job.yml
```

- [ ] **Step 2: Run the checks to confirm the sidecar does not exist yet**

Run: `test -f deploy/mysql/xxl-job/001_bootstrap.sh && test -f deploy/mysql/xxl-job/010_schema.sql && test -f deploy/mysql/xxl-job/020_seed_local.sh && rg -n 'xxl-job-db-bootstrap' deploy/compose.infra.xxl-job.yml`
Expected: FAIL before the new assets and sidecar are created.

- [ ] **Step 3: Move the vendor files, add the XXL bootstrap script, and overwrite the compose file**

```bash
mkdir -p deploy/mysql/xxl-job
git mv deploy/mysql-init/020_xxl_job_schema.sql deploy/mysql/xxl-job/010_schema.sql
git mv deploy/mysql-init/021_seed_xxl_job.sh deploy/mysql/xxl-job/020_seed_local.sh
cat > deploy/mysql/xxl-job/001_bootstrap.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
XXL_JOB_MYSQL_HOST="${XXL_JOB_MYSQL_HOST:-mysql-primary}"
XXL_JOB_MYSQL_PORT="${XXL_JOB_MYSQL_PORT:-3306}"
XXL_JOB_MYSQL_DATABASE="${XXL_JOB_MYSQL_DATABASE:-xxl_job}"
XXL_JOB_MYSQL_USER="${XXL_JOB_MYSQL_USER:-xxl_job}"
XXL_JOB_MYSQL_PASSWORD="${XXL_JOB_MYSQL_PASSWORD:-xxljobpass}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-/bootstrap}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[xxl-job-db-bootstrap] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

db_escaped="$(sql_escape "${XXL_JOB_MYSQL_DATABASE}")"
user_escaped="$(sql_escape "${XXL_JOB_MYSQL_USER}")"
password_escaped="$(sql_escape "${XXL_JOB_MYSQL_PASSWORD}")"

mysql_base_args=(
  --default-character-set=utf8mb4
  "-h${XXL_JOB_MYSQL_HOST}"
  "-P${XXL_JOB_MYSQL_PORT}"
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
)

echo "[xxl-job-db-bootstrap] ensuring database and runtime grants..."
mysql "${mysql_base_args[@]}" <<SQL
create database if not exists \`${db_escaped}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;
create user if not exists '${user_escaped}'@'%' identified by '${password_escaped}';
grant select, insert, update, delete on \`${db_escaped}\`.* to '${user_escaped}'@'%';
flush privileges;
SQL

table_exists="$(
  mysql "${mysql_base_args[@]}" -N -B <<SQL
select count(*)
from information_schema.tables
where table_schema = '${db_escaped}'
  and table_name = 'xxl_job_info';
SQL
)"

if [[ "${table_exists}" == "0" ]]; then
  echo "[xxl-job-db-bootstrap] importing XXL-JOB schema..."
  mysql "${mysql_base_args[@]}" "${XXL_JOB_MYSQL_DATABASE}" < "${BOOTSTRAP_DIR}/010_schema.sql"
else
  echo "[xxl-job-db-bootstrap] schema already initialized; skipping import."
fi

echo "[xxl-job-db-bootstrap] applying local XXL-JOB seed..."
MYSQL_HOST="${XXL_JOB_MYSQL_HOST}" \
MYSQL_PORT="${XXL_JOB_MYSQL_PORT}" \
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
XXL_JOB_MYSQL_DATABASE="${XXL_JOB_MYSQL_DATABASE}" \
"${BOOTSTRAP_DIR}/020_seed_local.sh"

echo "[xxl-job-db-bootstrap] done."
EOF

cat > deploy/mysql/xxl-job/020_seed_local.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-mysql-primary}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
XXL_JOB_MYSQL_DATABASE="${XXL_JOB_MYSQL_DATABASE:-xxl_job}"
XXL_JOB_ADMIN_USERNAME="${XXL_JOB_ADMIN_USERNAME:-admin}"
XXL_JOB_ADMIN_PASSWORD="${XXL_JOB_ADMIN_PASSWORD:-dev-local-xxl-admin}"
XXL_JOB_EXECUTOR_APPNAME="${XXL_JOB_EXECUTOR_APPNAME:-community-app}"
XXL_JOB_EXECUTOR_TITLE="${XXL_JOB_EXECUTOR_TITLE:-CommunityApp}"
XXL_JOB_AUTHOR="${XXL_JOB_AUTHOR:-community}"
XXL_JOB_ALARM_EMAIL="${XXL_JOB_ALARM_EMAIL:-}"
XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON="${XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON:-0 0/5 * * * ?}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[xxl-job-seed] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

if [[ -z "${XXL_JOB_ADMIN_USERNAME}" || -z "${XXL_JOB_ADMIN_PASSWORD}" ]]; then
  echo "[xxl-job-seed] missing env: XXL_JOB_ADMIN_USERNAME / XXL_JOB_ADMIN_PASSWORD" >&2
  exit 1
fi

if [[ -z "${XXL_JOB_EXECUTOR_APPNAME}" ]]; then
  echo "[xxl-job-seed] missing env: XXL_JOB_EXECUTOR_APPNAME" >&2
  exit 1
fi

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

ADMIN_PASSWORD_HASH="$(printf '%s' "${XXL_JOB_ADMIN_PASSWORD}" | sha256sum | awk '{print $1}')"
XXL_JOB_ADMIN_USERNAME_ESCAPED="$(sql_escape "${XXL_JOB_ADMIN_USERNAME}")"
XXL_JOB_EXECUTOR_APPNAME_ESCAPED="$(sql_escape "${XXL_JOB_EXECUTOR_APPNAME}")"
XXL_JOB_EXECUTOR_TITLE_ESCAPED="$(sql_escape "${XXL_JOB_EXECUTOR_TITLE}")"
XXL_JOB_AUTHOR_ESCAPED="$(sql_escape "${XXL_JOB_AUTHOR}")"
XXL_JOB_ALARM_EMAIL_ESCAPED="$(sql_escape "${XXL_JOB_ALARM_EMAIL}")"
XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON_ESCAPED="$(sql_escape "${XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON}")"

mysql --default-character-set=utf8mb4 -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
use \`${XXL_JOB_MYSQL_DATABASE}\`;
set names utf8mb4;

insert ignore into xxl_job_lock(lock_name) values ('schedule_lock');

insert into xxl_job_user(username, password, token, role, permission)
values ('${XXL_JOB_ADMIN_USERNAME_ESCAPED}', '${ADMIN_PASSWORD_HASH}', null, 1, null)
on duplicate key update
  password = values(password),
  token = null,
  role = 1,
  permission = null;

set @executor_app_name := '${XXL_JOB_EXECUTOR_APPNAME_ESCAPED}';
set @executor_title := '${XXL_JOB_EXECUTOR_TITLE_ESCAPED}';
set @job_author := '${XXL_JOB_AUTHOR_ESCAPED}';
set @alarm_email := '${XXL_JOB_ALARM_EMAIL_ESCAPED}';
set @cleanup_cron := '${XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON_ESCAPED}';

insert into xxl_job_group(app_name, title, address_type, address_list, update_time)
select @executor_app_name, @executor_title, 0, null, now()
from dual
where not exists (
  select 1 from xxl_job_group where app_name = @executor_app_name
);

update xxl_job_group
set title = @executor_title,
    address_type = 0,
    address_list = null,
    update_time = now()
where app_name = @executor_app_name;

set @job_group_id := (
  select id
  from xxl_job_group
  where app_name = @executor_app_name
  order by id asc
  limit 1
);

update xxl_job_info
set job_desc = 'Pending Registration Cleanup',
    update_time = now(),
    author = @job_author,
    alarm_email = @alarm_email,
    schedule_type = 'CRON',
    schedule_conf = @cleanup_cron,
    misfire_strategy = 'DO_NOTHING',
    executor_route_strategy = 'FIRST',
    executor_handler = 'pendingRegistrationUserCleanup',
    executor_param = '',
    executor_block_strategy = 'SERIAL_EXECUTION',
    executor_timeout = 0,
    executor_fail_retry_count = 0,
    glue_type = 'BEAN',
    glue_source = '',
    glue_remark = 'seeded by deploy',
    glue_updatetime = now(),
    child_jobid = '',
    trigger_status = 1
where job_group = @job_group_id
  and executor_handler = 'pendingRegistrationUserCleanup';

insert into xxl_job_info(
  job_group,
  job_desc,
  add_time,
  update_time,
  author,
  alarm_email,
  schedule_type,
  schedule_conf,
  misfire_strategy,
  executor_route_strategy,
  executor_handler,
  executor_param,
  executor_block_strategy,
  executor_timeout,
  executor_fail_retry_count,
  glue_type,
  glue_source,
  glue_remark,
  glue_updatetime,
  child_jobid,
  trigger_status,
  trigger_last_time,
  trigger_next_time
)
select
  @job_group_id,
  'Pending Registration Cleanup',
  now(),
  now(),
  @job_author,
  @alarm_email,
  'CRON',
  @cleanup_cron,
  'DO_NOTHING',
  'FIRST',
  'pendingRegistrationUserCleanup',
  '',
  'SERIAL_EXECUTION',
  0,
  0,
  'BEAN',
  '',
  'seeded by deploy',
  now(),
  '',
  1,
  0,
  0
from dual
where @job_group_id is not null
  and not exists (
    select 1
    from xxl_job_info
    where job_group = @job_group_id
      and executor_handler = 'pendingRegistrationUserCleanup'
  );

update xxl_job_info
set job_desc = 'Search Reindex',
    update_time = now(),
    author = @job_author,
    alarm_email = @alarm_email,
    schedule_type = 'NONE',
    schedule_conf = '',
    misfire_strategy = 'DO_NOTHING',
    executor_route_strategy = 'FIRST',
    executor_handler = 'searchReindex',
    executor_param = '',
    executor_block_strategy = 'SERIAL_EXECUTION',
    executor_timeout = 0,
    executor_fail_retry_count = 0,
    glue_type = 'BEAN',
    glue_source = '',
    glue_remark = 'seeded by deploy',
    glue_updatetime = now(),
    child_jobid = '',
    trigger_status = 0,
    trigger_last_time = 0,
    trigger_next_time = 0
where job_group = @job_group_id
  and executor_handler = 'searchReindex';

insert into xxl_job_info(
  job_group,
  job_desc,
  add_time,
  update_time,
  author,
  alarm_email,
  schedule_type,
  schedule_conf,
  misfire_strategy,
  executor_route_strategy,
  executor_handler,
  executor_param,
  executor_block_strategy,
  executor_timeout,
  executor_fail_retry_count,
  glue_type,
  glue_source,
  glue_remark,
  glue_updatetime,
  child_jobid,
  trigger_status,
  trigger_last_time,
  trigger_next_time
)
select
  @job_group_id,
  'Search Reindex',
  now(),
  now(),
  @job_author,
  @alarm_email,
  'NONE',
  '',
  'DO_NOTHING',
  'FIRST',
  'searchReindex',
  '',
  'SERIAL_EXECUTION',
  0,
  0,
  'BEAN',
  '',
  'seeded by deploy',
  now(),
  '',
  0,
  0,
  0
from dual
where @job_group_id is not null
  and not exists (
    select 1
    from xxl_job_info
    where job_group = @job_group_id
      and executor_handler = 'searchReindex'
  );
SQL

echo "[xxl-job-seed] done."
EOF
chmod +x deploy/mysql/xxl-job/001_bootstrap.sh deploy/mysql/xxl-job/020_seed_local.sh
cat > deploy/compose.infra.xxl-job.yml <<'EOF'
services:
  xxl-job-db-bootstrap:
    image: mysql:8.0
    entrypoint:
    - /bin/bash
    - /bootstrap/001_bootstrap.sh
    environment:
    - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
    - XXL_JOB_MYSQL_HOST=${DB_PRIMARY_HOST:-mysql-primary}
    - XXL_JOB_MYSQL_PORT=3306
    - XXL_JOB_MYSQL_DATABASE=${XXL_JOB_MYSQL_DATABASE:-xxl_job}
    - XXL_JOB_MYSQL_USER=${XXL_JOB_MYSQL_USER:-xxl_job}
    - XXL_JOB_MYSQL_PASSWORD=${XXL_JOB_MYSQL_PASSWORD:-xxljobpass}
    - XXL_JOB_ADMIN_USERNAME=${XXL_JOB_ADMIN_USERNAME:-admin}
    - XXL_JOB_ADMIN_PASSWORD=${XXL_JOB_ADMIN_PASSWORD:-dev-local-xxl-admin}
    - XXL_JOB_EXECUTOR_APPNAME=${XXL_JOB_EXECUTOR_APPNAME:-community-app}
    - XXL_JOB_EXECUTOR_TITLE=${XXL_JOB_EXECUTOR_TITLE:-CommunityApp}
    - XXL_JOB_AUTHOR=${XXL_JOB_AUTHOR:-community}
    - XXL_JOB_ALARM_EMAIL=${XXL_JOB_ALARM_EMAIL:-}
    - XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON=${XXL_JOB_PENDING_REGISTRATION_CLEANUP_CRON:-0 0/5 * * * ?}
    - BOOTSTRAP_DIR=/bootstrap
    volumes:
    - ./mysql/xxl-job:/bootstrap:ro
    depends_on:
      mysql-primary:
        condition: service_healthy
    restart: "no"
  xxl-job-admin-1:
    image: xuxueli/xxl-job-admin:3.3.2
    mem_limit: ${XXL_JOB_ADMIN_MEM_LIMIT:-384m}
    environment:
    - JAVA_TOOL_OPTIONS=${XXL_JOB_ADMIN_JAVA_TOOL_OPTIONS:--XX:+UseG1GC -XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError}
    - PARAMS=--spring.datasource.url=jdbc:mysql://${DB_PRIMARY_HOST:-mysql-primary}:3306/${XXL_JOB_MYSQL_DATABASE:-xxl_job}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true --spring.datasource.username=${XXL_JOB_MYSQL_USER:-xxl_job} --spring.datasource.password=${XXL_JOB_MYSQL_PASSWORD:-xxljobpass} --xxl.job.accessToken=${XXL_JOB_ACCESS_TOKEN:?XXL_JOB_ACCESS_TOKEN is required}
    depends_on:
      xxl-job-db-bootstrap:
        condition: service_completed_successfully
    networks:
      default:
        aliases:
        - xxl-job-admin
  xxl-job-admin-2:
    image: xuxueli/xxl-job-admin:3.3.2
    mem_limit: ${XXL_JOB_ADMIN_MEM_LIMIT:-384m}
    environment:
    - JAVA_TOOL_OPTIONS=${XXL_JOB_ADMIN_JAVA_TOOL_OPTIONS:--XX:+UseG1GC -XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError}
    - PARAMS=--spring.datasource.url=jdbc:mysql://${DB_PRIMARY_HOST:-mysql-primary}:3306/${XXL_JOB_MYSQL_DATABASE:-xxl_job}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true --spring.datasource.username=${XXL_JOB_MYSQL_USER:-xxl_job} --spring.datasource.password=${XXL_JOB_MYSQL_PASSWORD:-xxljobpass} --xxl.job.accessToken=${XXL_JOB_ACCESS_TOKEN:?XXL_JOB_ACCESS_TOKEN is required}
    depends_on:
      xxl-job-db-bootstrap:
        condition: service_completed_successfully
EOF
```

- [ ] **Step 4: Validate the XXL scripts and rendered compose topology**

Run: `bash -n deploy/mysql/xxl-job/001_bootstrap.sh && bash -n deploy/mysql/xxl-job/020_seed_local.sh && docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.xxl-job.yml config > /tmp/community-xxl-job-bootstrap.yml && rg -n 'xxl-job-db-bootstrap|./mysql/xxl-job:/bootstrap:ro|service_completed_successfully' /tmp/community-xxl-job-bootstrap.yml`
Expected: PASS and `rg` shows the new sidecar service, its mount, and both admin dependencies on `service_completed_successfully`.

- [ ] **Step 5: Commit the XXL-JOB bootstrap isolation**

```bash
git add deploy/mysql/xxl-job/001_bootstrap.sh deploy/mysql/xxl-job/010_schema.sql deploy/mysql/xxl-job/020_seed_local.sh deploy/compose.infra.xxl-job.yml
git commit -m "refactor(deploy): isolate xxl-job mysql bootstrap assets"
```

### Task 6: Update tests to read the new deploy schema source of truth

**Files:**
- Modify: `tools/mock-data-studio/test/batch-repository.test.mjs`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/support/DeployCommunitySchema.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/LegacyVirtualMarketRetirementTest.java`

- [ ] **Step 1: Write the failing test run commands**

```bash
npm --prefix tools/mock-data-studio test -- test/batch-repository.test.mjs
mvn -pl backend/community-app -Dtest=LegacyGrowthSurfaceRetirementTest,LegacyVirtualMarketRetirementTest test
```

- [ ] **Step 2: Run the tests to confirm they still depend on the deleted bundle**

Run: `npm --prefix tools/mock-data-studio test -- test/batch-repository.test.mjs && mvn -pl backend/community-app -Dtest=LegacyGrowthSurfaceRetirementTest,LegacyVirtualMarketRetirementTest test`
Expected: FAIL because `deploy/mysql-init/010_schema.sql` no longer exists.

- [ ] **Step 3: Add a Java helper and replace the old-path assertions in the Node and Java tests**

```bash
cat > backend/community-app/src/test/java/com/nowcoder/community/support/DeployCommunitySchema.java <<'EOF'
package com.nowcoder.community.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DeployCommunitySchema {

    private DeployCommunitySchema() {
    }

    public static String read(Path repoRoot) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (var paths = Files.list(repoRoot.resolve("deploy/mysql/community"))) {
            for (Path path : paths
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                builder.append(Files.readString(path)).append('\n');
            }
        }
        return builder.toString();
    }
}
EOF

python3 - <<'PY'
from pathlib import Path

node_test = Path('tools/mock-data-studio/test/batch-repository.test.mjs')
text = node_test.read_text()
text = text.replace(
    "import { readFileSync } from 'node:fs'\nimport test from 'node:test'\nimport { fileURLToPath } from 'node:url'\n",
    "import { readFileSync } from 'node:fs'\nimport path from 'node:path'\nimport test from 'node:test'\nimport { fileURLToPath } from 'node:url'\n"
)
text = text.replace(
    "const schemaPath = fileURLToPath(new URL('../../../deploy/mysql-init/010_schema.sql', import.meta.url))\n",
    "const communityDir = fileURLToPath(new URL('../../../deploy/mysql/community/', import.meta.url))\nconst demoMetadataSchemaPath = path.join(communityDir, '011_schema_demo_metadata.sql')\nconst communityBootstrapPath = path.join(communityDir, '001_bootstrap.sh')\n"
)
text = text.replace(
    "function extractDemoCreateTableStatements(schemaSql) {\n  return [...schemaSql.matchAll(/create table if not exists demo_[^(]+\\([\\s\\S]*?\\);/giu)].map(\n    (match) => match[0]\n  )\n}\n",
    "function extractDemoCreateTableStatements(schemaSql) {\n  return [...schemaSql.matchAll(/create table if not exists (?:demo_[^(]+|ai_config)\\([\\s\\S]*?\\);/giu)].map(\n    (match) => match[0]\n  )\n}\n\nfunction extractBootstrapOrder(scriptText) {\n  const match = scriptText.match(/SCHEMA_FILES=\\(([\\s\\S]*?)\\)\\n/u)\n  assert.ok(match, 'SCHEMA_FILES array missing from community bootstrap script')\n  return [...match[1].matchAll(/\\s+([0-9]{3}_[A-Za-z0-9_\\-.]+)\\n/gu)].map((entry) => entry[1])\n}\n"
)
text = text.replace(
    "test('bootstrapDemoSchema DDL matches the deploy schema for all metadata tables', async () => {\n  const db = new FakeMetadataDb()\n\n  await bootstrapDemoSchema(db)\n\n  const schemaSql = readFileSync(schemaPath, 'utf8')\n  const schemaStatements = extractDemoCreateTableStatements(schemaSql)\n\n  assert.equal(schemaStatements.length, demoMetadataTableStatements.length)\n  assert.deepEqual(\n    db.ddlStatements.map(normalizeSql),\n    schemaStatements.map(normalizeSql)\n  )\n})\n",
    "test('bootstrapDemoSchema DDL matches the deploy community metadata schema and replay order', async () => {\n  const db = new FakeMetadataDb()\n\n  await bootstrapDemoSchema(db)\n\n  const schemaSql = readFileSync(demoMetadataSchemaPath, 'utf8')\n  const schemaStatements = extractDemoCreateTableStatements(schemaSql)\n  const bootstrapOrder = extractBootstrapOrder(readFileSync(communityBootstrapPath, 'utf8'))\n\n  assert.equal(schemaStatements.length, demoMetadataTableStatements.length)\n  assert.deepEqual(\n    db.ddlStatements.map(normalizeSql),\n    schemaStatements.map(normalizeSql)\n  )\n  assert.deepEqual(bootstrapOrder.slice(0, 2), ['010_schema_shared.sql', '011_schema_demo_metadata.sql'])\n})\n"
)
node_test.write_text(text)

for java_path in [
    Path('backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java'),
    Path('backend/community-app/src/test/java/com/nowcoder/community/market/LegacyVirtualMarketRetirementTest.java'),
]:
    text = java_path.read_text()
    text = text.replace(
        "import org.junit.jupiter.api.Test;\n\nimport java.io.IOException;\nimport java.nio.file.Files;\nimport java.nio.file.Path;\n",
        "import com.nowcoder.community.support.DeployCommunitySchema;\nimport org.junit.jupiter.api.Test;\n\nimport java.io.IOException;\nimport java.nio.file.Files;\nimport java.nio.file.Path;\n"
    )
    text = text.replace(
        "        assertSchemaDoesNotContainRetiredTables(MODULE_ROOT.resolve(\"src/test/resources/schema.sql\"));\n        assertSchemaDoesNotContainRetiredTables(REPO_ROOT.resolve(\"deploy/mysql-init/010_schema.sql\"));\n",
        "        assertSchemaDoesNotContainRetiredTables(Files.readString(MODULE_ROOT.resolve(\"src/test/resources/schema.sql\")));\n        assertSchemaDoesNotContainRetiredTables(DeployCommunitySchema.read(REPO_ROOT));\n"
    )
    text = text.replace(
        "        assertSchemaDoesNotContainVirtualTables(MODULE_ROOT.resolve(\"src/test/resources/schema.sql\"));\n        assertSchemaDoesNotContainVirtualTables(REPO_ROOT.resolve(\"deploy/mysql-init/010_schema.sql\"));\n",
        "        assertSchemaDoesNotContainVirtualTables(Files.readString(MODULE_ROOT.resolve(\"src/test/resources/schema.sql\")));\n        assertSchemaDoesNotContainVirtualTables(DeployCommunitySchema.read(REPO_ROOT));\n"
    )
    text = text.replace(
        "    private void assertSchemaDoesNotContainRetiredTables(Path schemaPath) throws IOException {\n        String schema = Files.readString(schemaPath);\n",
        "    private void assertSchemaDoesNotContainRetiredTables(String schema) {\n"
    )
    text = text.replace(
        "    private void assertSchemaDoesNotContainVirtualTables(Path schemaPath) throws IOException {\n        String schema = Files.readString(schemaPath);\n",
        "    private void assertSchemaDoesNotContainVirtualTables(String schema) {\n"
    )
    java_path.write_text(text)
PY
```

- [ ] **Step 4: Run the targeted Node and Java tests again**

Run: `npm --prefix tools/mock-data-studio test -- test/batch-repository.test.mjs && mvn -pl backend/community-app -Dtest=LegacyGrowthSurfaceRetirementTest,LegacyVirtualMarketRetirementTest test`
Expected: PASS.

- [ ] **Step 5: Commit the test migration**

```bash
git add tools/mock-data-studio/test/batch-repository.test.mjs backend/community-app/src/test/java/com/nowcoder/community/support/DeployCommunitySchema.java backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java backend/community-app/src/test/java/com/nowcoder/community/market/LegacyVirtualMarketRetirementTest.java
git commit -m "test: point deploy schema checks at community sources"
```

### Task 7: Update live docs, sweep active references, and run final verification

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/DATA_MODEL.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `docs/DEV_ONLY.md`
- Verify active code/config/docs under `deploy/`, `tools/`, `backend/`, and the live docs above no longer reference `deploy/mysql-init` or `bootstrap-nacos-db.sh`

- [ ] **Step 1: Write the failing live-reference sweep**

```bash
rg -n 'deploy/mysql-init|bootstrap-nacos-db\.sh' \
  deploy/README.md \
  docs/DATA_MODEL.md \
  docs/DEPLOYMENT.md \
  docs/DEV_ONLY.md \
  deploy/compose.infra.mysql.yml \
  deploy/compose.infra.mock-data-studio-bootstrap.yml \
  deploy/compose.infra.nacos.yml \
  deploy/compose.infra.xxl-job.yml \
  tools/mock-data-studio/test/batch-repository.test.mjs \
  backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/LegacyVirtualMarketRetirementTest.java
```

- [ ] **Step 2: Run the sweep to confirm live files still contain old references**

Run: `rg -n 'deploy/mysql-init|bootstrap-nacos-db\.sh' deploy/README.md docs/DATA_MODEL.md docs/DEPLOYMENT.md docs/DEV_ONLY.md deploy/compose.infra.mysql.yml deploy/compose.infra.mock-data-studio-bootstrap.yml deploy/compose.infra.nacos.yml deploy/compose.infra.xxl-job.yml tools/mock-data-studio/test/batch-repository.test.mjs backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java backend/community-app/src/test/java/com/nowcoder/community/market/LegacyVirtualMarketRetirementTest.java`
Expected: PASS with matches in the live docs until they are updated.

- [ ] **Step 3: Rewrite the live docs to describe `deploy/mysql/` and the new seed locations**

```bash
python3 - <<'PY'
from pathlib import Path

replacements = {
    Path('deploy/README.md'): [
        (
            '- `mysql-init/`：MySQL 初始化脚本（建表 + 种子数据）。',
            '- `mysql/`：MySQL bootstrap assets，按 `primary-init` / `community` / `nacos` / `xxl-job` 分目录管理。'
        ),
        (
            '  - fresh volume：由 `deploy/mysql-init/001_create_databases.sh` 初始化',
            '  - fresh volume：由 `deploy/mysql/primary-init/001_create_databases.sh` + `deploy/mysql/community/*.sql` 初始化'
        ),
    ],
    Path('docs/DATA_MODEL.md'): [
        (
            '- `deploy/mysql-init/001_create_databases.sh`（建库 + 最小权限账号）\n- `deploy/mysql-init/010_schema.sql`（最小表结构 + 本地种子数据；覆盖 `community` + `im_core`）',
            '- `deploy/mysql/primary-init/001_create_databases.sh`（mysql-primary 首次建库 + 最小权限账号）\n- `deploy/mysql/community/*.sql`（最小表结构 + 本地种子数据；覆盖 `community` + `im_core`）'
        ),
        (
            '`deploy/mysql-init/010_schema.sql` 提供演示用户（仅本地开发用途）。',
            '`deploy/mysql/community/090_seed_identity.sql` 提供演示用户（仅本地开发用途）。'
        ),
        (
            '此外，`mock-data-studio` 在启动时会执行一份与 `deploy/mysql-init/010_schema.sql` 对齐的 metadata bootstrap：\n- 新数据卷：由 `deploy/mysql-init/010_schema.sql` 创建 `demo_*` metadata tables\n- 已存在数据卷：由 `tools/mock-data-studio/src/db/bootstrap.mjs` 使用 `CREATE TABLE IF NOT EXISTS` 补齐同样的 sidecar 表',
            '此外，`mock-data-studio` 在启动时会执行一份与 `deploy/mysql/community` 对齐的 metadata bootstrap：\n- 新数据卷：由 `deploy/mysql/community/011_schema_demo_metadata.sql` 创建 `demo_*` / `ai_config` metadata tables\n- 已存在数据卷：由 `tools/mock-data-studio/src/db/bootstrap.mjs` 使用 `CREATE TABLE IF NOT EXISTS` 补齐同样的 sidecar 表'
        ),
    ],
    Path('docs/DEPLOYMENT.md'): [
        (
            '- `deploy/compose.infra.mysql.yml`\n  - MySQL（`1 主 + 2 从`）与 replication bootstrap',
            '- `deploy/compose.infra.mysql.yml`\n  - MySQL（`1 主 + 2 从`）与 `deploy/mysql/primary-init` + `deploy/mysql/community` bootstrap assets'
        ),
        (
            '- `deploy/compose.infra.nacos.yml`\n  - 注册发现：`Nacos x3` 集群与 `nacos-db-bootstrap`',
            '- `deploy/compose.infra.nacos.yml`\n  - 注册发现：`Nacos x3` 集群与 `deploy/mysql/nacos` bootstrap assets'
        ),
        (
            '- `deploy/compose.infra.xxl-job.yml`\n  - 控制面：`xxl-job-admin x2`',
            '- `deploy/compose.infra.xxl-job.yml`\n  - 控制面：`xxl-job-db-bootstrap` + `xxl-job-admin x2`'
        ),
    ],
    Path('docs/DEV_ONLY.md'): [
        (
            '- `deploy/mysql-init/010_schema.sql`（包含本地 dev/demo 种子用户插入）',
            '- `deploy/mysql/community/090_seed_identity.sql`（包含本地 dev/demo 种子用户插入）'
        ),
    ],
}

for path, pairs in replacements.items():
    text = path.read_text()
    for old, new in pairs:
      assert old in text, f'missing text in {path}: {old}'
      text = text.replace(old, new)
    path.write_text(text)
PY
```

- [ ] **Step 4: Run the final verification sweep and targeted validations**

Run: `bash -n deploy/mysql/primary-init/001_create_databases.sh && bash -n deploy/mysql/community/001_bootstrap.sh && bash -n deploy/mysql/nacos/001_bootstrap.sh && bash -n deploy/mysql/xxl-job/001_bootstrap.sh && bash -n deploy/mysql/xxl-job/020_seed_local.sh && docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.nacos.yml -f deploy/compose.infra.xxl-job.yml -f deploy/compose.infra.mock-data-studio-bootstrap.yml config > /tmp/community-mysql-refactor.yml && npm --prefix tools/mock-data-studio test -- test/batch-repository.test.mjs && mvn -pl backend/community-app -Dtest=LegacyGrowthSurfaceRetirementTest,LegacyVirtualMarketRetirementTest test && test ! -e deploy/mysql-init/010_schema.sql && test ! -e deploy/scripts/bootstrap-nacos-db.sh && rg -n 'deploy/mysql-init|bootstrap-nacos-db\.sh' deploy/README.md docs/DATA_MODEL.md docs/DEPLOYMENT.md docs/DEV_ONLY.md deploy/compose.infra.mysql.yml deploy/compose.infra.mock-data-studio-bootstrap.yml deploy/compose.infra.nacos.yml deploy/compose.infra.xxl-job.yml tools/mock-data-studio/test/batch-repository.test.mjs backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java backend/community-app/src/test/java/com/nowcoder/community/market/LegacyVirtualMarketRetirementTest.java`
Expected: every validation command exits `0`, both deleted-path checks PASS, and the final `rg` exits `1` with no matches in live files.

- [ ] **Step 5: Commit the live docs and final verification pass**

```bash
git add deploy/README.md docs/DATA_MODEL.md docs/DEPLOYMENT.md docs/DEV_ONLY.md
git commit -m "docs(deploy): document mysql bootstrap asset split"
```
