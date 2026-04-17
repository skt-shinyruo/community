# Deploy Dev/HA Dual Topology Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight single-node `dev` topology alongside the existing local `ha` rehearsal topology, both reachable through `deploy/deployment.sh` with `full` and `infra` scopes.

**Architecture:** Keep `deploy/compose.yml` as the shared base, then split topology-shaped layers into `*.dev.yml` and `*.ha.yml`. Extend `deploy/deployment.sh` to assemble the correct file set, split runtime and nginx wiring by topology, and add explicit Redis standalone support in `community-app` so the `dev` stack can use a single Redis node while `ha` continues to use Redis cluster.

**Tech Stack:** Docker Compose, Bash, Nginx, Spring Boot 3, Spring Data Redis, Maven, JUnit 5, AssertJ

---

## File Structure Map

### Operator and env selection

- `deploy/deployment.sh`
  Role: parse `--topology` and `--scope`, choose the right env file, build the compose file list, and keep `./deploy/deployment.sh up` backward-compatible with the current HA path.
- `deploy/.env.dev.example`
  Role: single-node local defaults for `dev`.
- `deploy/.env.ha.example`
  Role: explicit HA defaults for `ha`.
- `deploy/.env.example`
  Role: legacy compatibility alias that points operators at the new topology-specific examples without breaking existing habits during migration.

### Infra topology files

- `deploy/compose.infra.mysql.dev.yml`
- `deploy/compose.infra.redis.dev.yml`
- `deploy/compose.infra.kafka.dev.yml`
- `deploy/compose.infra.elasticsearch.dev.yml`
- `deploy/compose.infra.nacos.dev.yml`
- `deploy/compose.infra.xxl-job.dev.yml`
  Role: single-node middleware definitions for `dev`.
- `deploy/compose.infra.mysql.ha.yml`
- `deploy/compose.infra.redis.ha.yml`
- `deploy/compose.infra.kafka.ha.yml`
- `deploy/compose.infra.elasticsearch.ha.yml`
- `deploy/compose.infra.nacos.ha.yml`
- `deploy/compose.infra.xxl-job.ha.yml`
  Role: explicit HA files that preserve current behavior.
- `deploy/compose.infra.mock-data-studio-bootstrap.dev.yml`
- `deploy/compose.infra.mock-data-studio-bootstrap.ha.yml`
  Role: topology-specific bootstrap because `depends_on` currently points at concrete MySQL service names.

### Runtime and ingress topology files

- `deploy/compose.runtime.services.dev.yml`
  Role: single-instance `community-app`, `community-gateway`, `im-core`, and `im-realtime`.
- `deploy/compose.runtime.services.ha.yml`
  Role: existing multi-instance runtime wiring, moved into an explicit HA file.
- `deploy/compose.runtime.frontend-nginx.dev.yml`
- `deploy/compose.runtime.frontend-nginx.ha.yml`
  Role: topology-specific frontend + nginx wiring.
- `deploy/compose.runtime.mock-data-studio.dev.yml`
- `deploy/compose.runtime.mock-data-studio.ha.yml`
  Role: topology-specific `depends_on` targets for `mock-data-studio`.
- `deploy/nginx/nginx.dev.conf`
- `deploy/nginx/nginx.ha.conf`
  Role: single-backend vs multi-backend upstream pools.

### Application Redis topology support

- `backend/community-app/src/main/resources/application.yml`
  Role: standalone Redis defaults for `dev`.
- `backend/community-app/src/main/resources/application-redis-cluster.yml`
  Role: Redis cluster properties activated only in HA runtime.
- `backend/community-app/src/test/java/com/nowcoder/community/config/RedisTopologyProfileTest.java`
  Role: regression test proving standalone defaults and cluster profile binding.

### Documentation

- `deploy/README.md`
- `docs/DEPLOYMENT.md`
- `README.md`
- `docs/OBSERVABILITY.md`
- `deploy/observability/kibana/README.md`
- `docs/LOCAL_HA.md`
- `tools/im-load/README.md`
  Role: update commands, file lists, env example names, and `dev`/`ha` guidance.

### Legacy files to retire after the split

- `deploy/compose.infra.mysql.yml`
- `deploy/compose.infra.redis.yml`
- `deploy/compose.infra.kafka.yml`
- `deploy/compose.infra.elasticsearch.yml`
- `deploy/compose.infra.nacos.yml`
- `deploy/compose.infra.xxl-job.yml`
- `deploy/compose.infra.mock-data-studio-bootstrap.yml`
- `deploy/compose.runtime.community-app.yml`
- `deploy/compose.runtime.community-gateway.yml`
- `deploy/compose.runtime.im-core.yml`
- `deploy/compose.runtime.im-realtime.yml`
- `deploy/compose.runtime.frontend-nginx.yml`
- `deploy/compose.runtime.mock-data-studio.yml`
- `deploy/nginx/nginx.conf`
  Role: remove the ambiguous pre-topology file names once the new operator path works.

---

### Task 1: Make `deployment.sh` Topology- And Scope-Aware

**Files:**
- Modify: `deploy/deployment.sh`
- Create: `deploy/.env.dev.example`
- Create: `deploy/.env.ha.example`
- Modify: `deploy/.env.example`

- [ ] **Step 1: Capture the missing CLI behavior before editing**

Run:

```bash
./deploy/deployment.sh config --topology dev
./deploy/deployment.sh --help
```

Expected:

- the first command fails with `unsupported option: --topology`
- the help text only mentions `deploy/.env` and has no `--scope`

- [ ] **Step 2: Extend `deploy/deployment.sh` to parse `--topology` and `--scope`**

Apply this structure inside `deploy/deployment.sh`:

```bash
TOPOLOGY="ha"
SCOPE="full"
ENV_FILE=""

resolve_default_env_file() {
  local topology="$1"
  case "${topology}" in
    dev)
      printf '%s/deploy/.env.dev\n' "${REPO_ROOT}"
      ;;
    ha)
      if [ -f "${REPO_ROOT}/deploy/.env.ha" ]; then
        printf '%s/deploy/.env.ha\n' "${REPO_ROOT}"
      else
        printf '%s/deploy/.env\n' "${REPO_ROOT}"
      fi
      ;;
    *)
      echo "[deployment.sh] unsupported topology: ${topology}" >&2
      exit 1
      ;;
  esac
}

append_topology_files() {
  case "${TOPOLOGY}" in
    dev)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.dev.yml
        deploy/compose.infra.redis.dev.yml
        deploy/compose.infra.kafka.dev.yml
        deploy/compose.infra.elasticsearch.dev.yml
        deploy/compose.infra.nacos.dev.yml
        deploy/compose.infra.xxl-job.dev.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.dev.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.dev.yml
          deploy/compose.runtime.frontend-nginx.dev.yml
          deploy/compose.runtime.mock-data-studio.dev.yml
        )
      fi
      ;;
    ha)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.ha.yml
        deploy/compose.infra.redis.ha.yml
        deploy/compose.infra.kafka.ha.yml
        deploy/compose.infra.elasticsearch.ha.yml
        deploy/compose.infra.nacos.ha.yml
        deploy/compose.infra.xxl-job.ha.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.ha.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.ha.yml
          deploy/compose.runtime.frontend-nginx.ha.yml
          deploy/compose.runtime.mock-data-studio.ha.yml
        )
      fi
      ;;
  esac
}
```

- [ ] **Step 3: Add the new env examples and keep a compatibility breadcrumb**

Create `deploy/.env.dev.example` with single-node defaults:

```dotenv
NGINX_API_PORT=12880
FRONTEND_HOST_PORT=12881
NGINX_XXL_JOB_PORT=12887
FRONTEND_PUBLIC_ORIGIN=http://localhost:12881
GATEWAY_PUBLIC_BASE_URL=http://localhost:12880
IM_WS_PUBLIC_URL=ws://localhost:12880/ws/im
NACOS_SERVER_ADDR=nacos:8848
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
ELASTICSEARCH_URIS=http://elasticsearch:9200
DB_PRIMARY_HOST=mysql
IM_CORE_DB_PRIMARY_HOST=mysql
XXL_JOB_ADMIN_INGRESS_URL=http://nginx:8081/xxl-job-admin
```

Create `deploy/.env.ha.example` by copying the current HA defaults from `deploy/.env.example`.

Replace the top of `deploy/.env.example` with a compatibility note plus the HA copy instruction:

```dotenv
# Compatibility alias during the dev/ha topology migration.
# Prefer:
#   cp deploy/.env.dev.example deploy/.env.dev
#   cp deploy/.env.ha.example deploy/.env.ha
# If you still want the old HA-only path:
#   cp deploy/.env.ha.example deploy/.env
```

- [ ] **Step 4: Re-run the CLI checks and verify the new help surface**

Run:

```bash
./deploy/deployment.sh --help
./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.dev.example >/tmp/community-dev-infra.yml
```

Expected:

- help output contains the literal option docs for `--topology dev|ha` and `--scope full|infra`
- the second command now fails only because the new `*.dev.yml` files do not exist yet

- [ ] **Step 5: Commit the operator scaffolding**

```bash
git add deploy/deployment.sh deploy/.env.dev.example deploy/.env.ha.example deploy/.env.example
git commit -m "refactor(deploy): add topology aware operator options"
```

### Task 2: Split Infra Compose Into `dev` And `ha`

**Files:**
- Create: `deploy/compose.infra.mysql.dev.yml`
- Create: `deploy/compose.infra.redis.dev.yml`
- Create: `deploy/compose.infra.kafka.dev.yml`
- Create: `deploy/compose.infra.elasticsearch.dev.yml`
- Create: `deploy/compose.infra.nacos.dev.yml`
- Create: `deploy/compose.infra.xxl-job.dev.yml`
- Create: `deploy/compose.infra.mock-data-studio-bootstrap.dev.yml`
- Create: `deploy/compose.infra.mysql.ha.yml`
- Create: `deploy/compose.infra.redis.ha.yml`
- Create: `deploy/compose.infra.kafka.ha.yml`
- Create: `deploy/compose.infra.elasticsearch.ha.yml`
- Create: `deploy/compose.infra.nacos.ha.yml`
- Create: `deploy/compose.infra.xxl-job.ha.yml`
- Create: `deploy/compose.infra.mock-data-studio-bootstrap.ha.yml`
- Delete: `deploy/compose.infra.mysql.yml`
- Delete: `deploy/compose.infra.redis.yml`
- Delete: `deploy/compose.infra.kafka.yml`
- Delete: `deploy/compose.infra.elasticsearch.yml`
- Delete: `deploy/compose.infra.nacos.yml`
- Delete: `deploy/compose.infra.xxl-job.yml`
- Delete: `deploy/compose.infra.mock-data-studio-bootstrap.yml`

- [ ] **Step 1: Prove the `dev` infra render is still blocked**

Run:

```bash
./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.dev.example >/tmp/community-dev-infra.yml
```

Expected:

- FAIL because the new infra topology files do not exist yet

- [ ] **Step 2: Add the single-node infra files**

Create `deploy/compose.infra.mysql.dev.yml` around a single MySQL service and keep the stable alias:

```yaml
services:
  mysql:
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
    volumes:
      - mysql_primary_data:/var/lib/mysql
      - ./mysql/primary-init/001_create_databases.sh:/docker-entrypoint-initdb.d/001_create_databases.sh:ro
      - ./mysql/community/010_schema_shared.sql:/docker-entrypoint-initdb.d/010_schema_shared.sql:ro
    networks:
      default:
        aliases:
          - mysql
```

Create `deploy/compose.infra.redis.dev.yml` as standalone Redis:

```yaml
services:
  redis:
    image: redis:7-alpine
    command:
      - redis-server
      - --appendonly
      - "yes"
      - --protected-mode
      - "no"
      - --bind
      - 0.0.0.0
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
```

Create `deploy/compose.infra.kafka.dev.yml` as single-node KRaft:

```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:7.6.1
    environment:
      - KAFKA_NODE_ID=1
      - KAFKA_PROCESS_ROLES=broker,controller
      - KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
      - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
      - KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1
      - KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1
      - KAFKA_MIN_INSYNC_REPLICAS=1
      - KAFKA_DEFAULT_REPLICATION_FACTOR=1
  kafka-init:
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}
    depends_on:
      kafka:
        condition: service_healthy
```

Create `deploy/compose.infra.elasticsearch.dev.yml` as one node:

```yaml
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.2
    environment:
      - node.name=elasticsearch
      - cluster.name=community-es-dev
      - discovery.type=single-node
      - xpack.security.enabled=false
    networks:
      default:
        aliases:
          - elasticsearch
  es-init:
    depends_on:
      elasticsearch:
        condition: service_started
    command: >
      sh -c 'until curl -fsS "http://elasticsearch:9200/_cluster/health?wait_for_status=yellow&timeout=120s" >/dev/null 2>&1; do sleep 2; done'
```

- [ ] **Step 3: Add the single-node discovery and control plane files**

Create `deploy/compose.infra.nacos.dev.yml` with standalone Nacos:

```yaml
services:
  nacos-db-bootstrap:
    environment:
      - NACOS_MYSQL_HOST=${NACOS_MYSQL_HOST:-mysql}
    depends_on:
      mysql:
        condition: service_healthy
  nacos:
    image: nacos/nacos-server:v2.3.2-slim
    environment:
      - MODE=standalone
      - NACOS_AUTH_ENABLE=false
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=${NACOS_MYSQL_HOST:-mysql}
    ports:
      - 127.0.0.1:${NACOS_HOST_PORT:-18848}:8848
```

Create `deploy/compose.infra.xxl-job.dev.yml` with a single admin:

```yaml
services:
  xxl-job-db-bootstrap:
    environment:
      - XXL_JOB_MYSQL_HOST=${DB_PRIMARY_HOST:-mysql}
    depends_on:
      mysql:
        condition: service_healthy
  xxl-job-admin:
    image: xuxueli/xxl-job-admin:3.3.2
    environment:
      - PARAMS=--spring.datasource.url=jdbc:mysql://${DB_PRIMARY_HOST:-mysql}:3306/${XXL_JOB_MYSQL_DATABASE:-xxl_job}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true --spring.datasource.username=${XXL_JOB_MYSQL_USER:-xxl_job} --spring.datasource.password=${XXL_JOB_MYSQL_PASSWORD:-xxljobpass} --xxl.job.accessToken=${XXL_JOB_ACCESS_TOKEN:?XXL_JOB_ACCESS_TOKEN is required}
    networks:
      default:
        aliases:
          - xxl-job-admin
```

Split `mock-data-studio-db-bootstrap` because `depends_on` must target the topology-specific MySQL service:

```yaml
# deploy/compose.infra.mock-data-studio-bootstrap.dev.yml
services:
  mock-data-studio-db-bootstrap:
    environment:
      - DB_PRIMARY_HOST=${DB_PRIMARY_HOST:-mysql}
    depends_on:
      mysql:
        condition: service_healthy
```

Copy the current clustered files into `*.ha.yml`, keeping their current service names and behavior intact.

- [ ] **Step 4: Verify both infra topologies now render**

Run:

```bash
./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.dev.example >/tmp/community-dev-infra.yml
./deploy/deployment.sh config --topology ha --scope infra --env-file deploy/.env.ha.example >/tmp/community-ha-infra.yml
rg -n '^  mysql:$|^  redis:$|^  kafka:$|^  elasticsearch:$|^  nacos:$|^  xxl-job-admin:$' /tmp/community-dev-infra.yml
rg -n '^  mysql-primary:$|^  redis-1:$|^  kafka-1:$|^  elasticsearch-1:$|^  nacos-1:$|^  xxl-job-admin-1:$' /tmp/community-ha-infra.yml
```

Expected:

- both `config` commands pass
- the first `rg` finds only single-node service names
- the second `rg` finds the current HA service names

- [ ] **Step 5: Commit the infra topology split**

```bash
git add deploy/compose.infra.*.dev.yml deploy/compose.infra.*.ha.yml
git rm deploy/compose.infra.mysql.yml deploy/compose.infra.redis.yml deploy/compose.infra.kafka.yml deploy/compose.infra.elasticsearch.yml deploy/compose.infra.nacos.yml deploy/compose.infra.xxl-job.yml deploy/compose.infra.mock-data-studio-bootstrap.yml
git commit -m "feat(deploy): split infra topology into dev and ha"
```

### Task 3: Split Runtime, Nginx, And Mock Data Studio By Topology

**Files:**
- Create: `deploy/compose.runtime.services.dev.yml`
- Create: `deploy/compose.runtime.services.ha.yml`
- Create: `deploy/compose.runtime.frontend-nginx.dev.yml`
- Create: `deploy/compose.runtime.frontend-nginx.ha.yml`
- Create: `deploy/compose.runtime.mock-data-studio.dev.yml`
- Create: `deploy/compose.runtime.mock-data-studio.ha.yml`
- Create: `deploy/nginx/nginx.dev.conf`
- Create: `deploy/nginx/nginx.ha.conf`
- Delete: `deploy/compose.runtime.community-app.yml`
- Delete: `deploy/compose.runtime.community-gateway.yml`
- Delete: `deploy/compose.runtime.im-core.yml`
- Delete: `deploy/compose.runtime.im-realtime.yml`
- Delete: `deploy/compose.runtime.frontend-nginx.yml`
- Delete: `deploy/compose.runtime.mock-data-studio.yml`
- Delete: `deploy/nginx/nginx.conf`

- [ ] **Step 1: Capture the blocked full-stack render before the runtime split**

Run:

```bash
./deploy/deployment.sh config --topology dev --env-file deploy/.env.dev.example >/tmp/community-dev-full.yml
```

Expected:

- FAIL because the new runtime files and nginx configs do not exist yet

- [ ] **Step 2: Create `deploy/compose.runtime.services.dev.yml` with single-instance service names**

Create the dev runtime file with canonical service names and dev middleware addresses:

```yaml
services:
  community-app:
    build:
      context: ../backend
      dockerfile: ../deploy/Dockerfile.backend-service
      args:
        MODULE: community-app
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},volume-log-export
      - DB_PRIMARY_HOST=${DB_PRIMARY_HOST:-mysql}
      - DB_URL=jdbc:mysql://${DB_PRIMARY_HOST:-mysql}:3306/${MYSQL_DATABASE:-community}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
      - SPRING_DATA_REDIS_HOST=${SPRING_DATA_REDIS_HOST:-redis}
      - SPRING_DATA_REDIS_PORT=${SPRING_DATA_REDIS_PORT:-6379}
      - ELASTICSEARCH_URIS=${ELASTICSEARCH_URIS:-http://elasticsearch:9200}
      - NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
      - XXL_JOB_EXECUTOR_ADDRESS=${XXL_JOB_EXECUTOR_ADDRESS:-http://community-app:9999/}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka-init:
        condition: service_completed_successfully
      nacos:
        condition: service_started
      xxl-job-admin:
        condition: service_started
```

Add the other dev services with the same naming pattern:

```yaml
  community-gateway:
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},volume-log-export
      - NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
      - IM_REALTIME_WS_BASE_URL=ws://im-realtime:18081${IM_INTERNAL_WORKER_WS_PATH:-/internal/ws/im}

  im-core:
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},volume-log-export
      - KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}
      - IM_CORE_DB_PRIMARY_HOST=${IM_CORE_DB_PRIMARY_HOST:-mysql}
      - IM_CORE_DB_URL=jdbc:mysql://${IM_CORE_DB_PRIMARY_HOST:-mysql}:3306/${IM_MYSQL_DATABASE:-im_core}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true

  im-realtime:
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},volume-log-export
      - KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}
      - IM_REALTIME_CONSUMER_GROUP=im-realtime
      - IM_REALTIME_WORKER_ID=${IM_REALTIME_WORKER_ID_PREFIX:-im-realtime}
```

- [ ] **Step 3: Create the HA runtime file and add the Redis cluster profile**

Create `deploy/compose.runtime.services.ha.yml` by moving the current `community-app-1..3`, `community-gateway-1..3`, `im-core-1..3`, and `im-realtime-1..3` blocks into one explicit HA file.

For every `community-app-*` service, change the profile and keep the cluster nodes:

```yaml
- SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},redis-cluster,volume-log-export
- SPRING_DATA_REDIS_CLUSTER_NODES=${SPRING_DATA_REDIS_CLUSTER_NODES:-redis-1:6379,redis-2:6379,redis-3:6379,redis-4:6379,redis-5:6379,redis-6:6379}
```

Keep the current HA-only service names, aliases, and `depends_on` targets intact.

- [ ] **Step 4: Split frontend/nginx and mock-data-studio by topology**

Create `deploy/nginx/nginx.dev.conf`:

```nginx
events {}

http {
  upstream community_gateway_pool {
    server community-gateway:8080 max_fails=3 fail_timeout=10s;
  }

  upstream xxl_job_admin_pool {
    server xxl-job-admin:8080 max_fails=3 fail_timeout=10s;
  }
}
```

Create `deploy/nginx/nginx.ha.conf` by copying the current `deploy/nginx/nginx.conf`.

Wire the topology-specific frontend/nginx files:

```yaml
# deploy/compose.runtime.frontend-nginx.dev.yml
services:
  nginx:
    depends_on:
      community-gateway:
        condition: service_started
      xxl-job-admin:
        condition: service_started
    volumes:
      - ./nginx/nginx.dev.conf:/etc/nginx/nginx.conf:ro
```

```yaml
# deploy/compose.runtime.mock-data-studio.dev.yml
services:
  mock-data-studio:
    environment:
      - MOCK_DATA_STUDIO_DB_URL=mysql://${DB_PRIMARY_HOST:-mysql}:3306/${MYSQL_DATABASE:-community}
      - MOCK_DATA_STUDIO_COMMUNITY_APP_BASE_URL=http://community-app:8080
      - MOCK_DATA_STUDIO_IM_CORE_BASE_URL=http://im-core:18082
    depends_on:
      mysql:
        condition: service_healthy
      community-app:
        condition: service_started
      im-core:
        condition: service_started
```

The HA variants should preserve the current `community-app-1` / `im-core-1` / `xxl-job-admin-1..2` wiring.

- [ ] **Step 5: Re-render the full stacks and confirm the topology split**

Run:

```bash
./deploy/deployment.sh config --topology dev --env-file deploy/.env.dev.example >/tmp/community-dev-full.yml
./deploy/deployment.sh config --topology ha --env-file deploy/.env.ha.example >/tmp/community-ha-full.yml
rg -n '^  community-app:$|^  community-gateway:$|^  im-core:$|^  im-realtime:$|^  nginx:$' /tmp/community-dev-full.yml
rg -n '^  community-app-1:$|^  community-gateway-1:$|^  im-core-1:$|^  im-realtime-1:$|^  nginx:$' /tmp/community-ha-full.yml
```

Expected:

- both renders pass
- dev render contains only canonical single service names
- HA render still contains the current numbered runtime services

- [ ] **Step 6: Commit the runtime and ingress split**

```bash
git add deploy/compose.runtime.*.dev.yml deploy/compose.runtime.*.ha.yml deploy/nginx/nginx.dev.conf deploy/nginx/nginx.ha.conf
git rm deploy/compose.runtime.community-app.yml deploy/compose.runtime.community-gateway.yml deploy/compose.runtime.im-core.yml deploy/compose.runtime.im-realtime.yml deploy/compose.runtime.frontend-nginx.yml deploy/compose.runtime.mock-data-studio.yml deploy/nginx/nginx.conf
git commit -m "feat(deploy): split runtime and ingress by topology"
```

### Task 4: Add Redis Standalone Support To `community-app`

**Files:**
- Modify: `backend/community-app/src/main/resources/application.yml`
- Create: `backend/community-app/src/main/resources/application-redis-cluster.yml`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/config/RedisTopologyProfileTest.java`

- [ ] **Step 1: Write the failing Redis topology regression test**

Create `backend/community-app/src/test/java/com/nowcoder/community/config/RedisTopologyProfileTest.java`:

```java
package com.nowcoder.community.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTopologyProfileTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void baseConfigUsesStandaloneRedisDefaults() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        loader.load("application", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));

        RedisProperties properties = Binder.get(environment)
                .bind("spring.data.redis", Bindable.of(RedisProperties.class))
                .orElseThrow();

        assertThat(properties.getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getPort()).isEqualTo(6379);
        assertThat(properties.getCluster().getNodes()).isEmpty();
    }

    @Test
    void redisClusterProfilePublishesClusterNodes() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getSystemProperties().put("SPRING_DATA_REDIS_CLUSTER_NODES", "redis-1:6379,redis-2:6379");
        loader.load("application", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        loader.load("application-redis-cluster", new ClassPathResource("application-redis-cluster.yml"))
                .forEach(source -> environment.getPropertySources().addFirst(source));

        RedisProperties properties = Binder.get(environment)
                .bind("spring.data.redis", Bindable.of(RedisProperties.class))
                .orElseThrow();

        assertThat(properties.getCluster().getNodes()).containsExactly("redis-1:6379", "redis-2:6379");
    }
}
```

- [ ] **Step 2: Run the targeted test and verify the current config fails**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=RedisTopologyProfileTest test
```

Expected:

- FAIL because `application.yml` still binds only `spring.data.redis.cluster.nodes`
- FAIL because `application-redis-cluster.yml` does not exist yet

- [ ] **Step 3: Move Redis cluster settings into a dedicated profile file**

Change `backend/community-app/src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:127.0.0.1}
      port: ${SPRING_DATA_REDIS_PORT:6379}
```

Create `backend/community-app/src/main/resources/application-redis-cluster.yml`:

```yaml
spring:
  config:
    activate:
      on-profile: redis-cluster
  data:
    redis:
      cluster:
        nodes: ${SPRING_DATA_REDIS_CLUSTER_NODES:127.0.0.1:6379}
```

- [ ] **Step 4: Re-run the targeted Redis topology test**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=RedisTopologyProfileTest test
```

Expected:

- PASS

- [ ] **Step 5: Commit the Redis topology support**

```bash
git add backend/community-app/src/main/resources/application.yml backend/community-app/src/main/resources/application-redis-cluster.yml backend/community-app/src/test/java/com/nowcoder/community/config/RedisTopologyProfileTest.java
git commit -m "feat(app): support redis standalone and cluster topologies"
```

### Task 5: Update Operator And Topology Documentation

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/DEPLOYMENT.md`
- Modify: `README.md`
- Modify: `docs/OBSERVABILITY.md`
- Modify: `deploy/observability/kibana/README.md`
- Modify: `docs/LOCAL_HA.md`
- Modify: `tools/im-load/README.md`

- [ ] **Step 1: Capture the stale references before editing docs**

Run:

```bash
rg -n 'deploy/\.env\b|compose\.runtime\.(community-app|community-gateway|im-core|im-realtime|frontend-nginx|mock-data-studio)|compose\.infra\.(mysql|redis|kafka|elasticsearch|nacos|xxl-job)(\.yml)?' \
  deploy/README.md docs/DEPLOYMENT.md README.md docs/OBSERVABILITY.md deploy/observability/kibana/README.md docs/LOCAL_HA.md tools/im-load/README.md
```

Expected:

- multiple hits that still describe the pre-topology file layout

- [ ] **Step 2: Rewrite the main deployment docs around `--topology` and `--scope`**

Update `deploy/README.md` and `docs/DEPLOYMENT.md` to use commands like:

```markdown
- 单机全栈：`./deploy/deployment.sh up --topology dev`
- 单机基础设施：`./deploy/deployment.sh up --topology dev --scope infra`
- HA 全栈：`./deploy/deployment.sh up --topology ha`
- 渲染配置：`./deploy/deployment.sh config --topology dev`
```

Update environment setup instructions to:

```markdown
cp deploy/.env.dev.example deploy/.env.dev
cp deploy/.env.ha.example deploy/.env.ha
```

Update `README.md` and `tools/im-load/README.md` to stop recommending `cp deploy/.env.example deploy/.env` as the primary path.

- [ ] **Step 3: Rewrite the low-level compose examples and HA troubleshooting docs**

Update `docs/OBSERVABILITY.md`, `deploy/observability/kibana/README.md`, and `docs/LOCAL_HA.md` to stop referencing deleted runtime files.

Use explicit topology-specific examples such as:

```bash
./deploy/deployment.sh up --topology ha --observability
./deploy/deployment.sh logs --topology ha community-gateway-1
./deploy/deployment.sh config --topology dev --scope infra
```

For docs that still need raw `docker compose -f ...` examples, switch them to the new file names:

```bash
docker compose --env-file deploy/.env.ha \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.ha.yml \
  -f deploy/compose.infra.redis.ha.yml \
  -f deploy/compose.infra.kafka.ha.yml \
  -f deploy/compose.infra.elasticsearch.ha.yml \
  -f deploy/compose.infra.nacos.ha.yml \
  -f deploy/compose.infra.xxl-job.ha.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.ha.yml \
  -f deploy/compose.runtime.services.ha.yml \
  -f deploy/compose.runtime.frontend-nginx.ha.yml \
  -f deploy/compose.runtime.mock-data-studio.ha.yml
```

- [ ] **Step 4: Re-scan the target docs for stale references**

Run:

```bash
rg -n 'compose\.runtime\.(community-app|community-gateway|im-core|im-realtime|frontend-nginx|mock-data-studio)|deploy/\.env\b' \
  deploy/README.md docs/DEPLOYMENT.md README.md docs/OBSERVABILITY.md deploy/observability/kibana/README.md docs/LOCAL_HA.md tools/im-load/README.md
```

Expected:

- no hits remain in the edited documentation set

- [ ] **Step 5: Commit the doc rewrite**

```bash
git add deploy/README.md docs/DEPLOYMENT.md README.md docs/OBSERVABILITY.md deploy/observability/kibana/README.md docs/LOCAL_HA.md tools/im-load/README.md
git commit -m "docs(deploy): document dev and ha topologies"
```

### Task 6: Verify Targeted Tests, Rendered Configs, And Scope

**Files:**
- Test: `backend/community-app/src/test/java/com/nowcoder/community/config/RedisTopologyProfileTest.java`
- Verify: `deploy/deployment.sh`
- Verify: `deploy/.env.dev.example`
- Verify: `deploy/.env.ha.example`
- Verify: `deploy/compose.infra.*.dev.yml`
- Verify: `deploy/compose.infra.*.ha.yml`
- Verify: `deploy/compose.runtime.*.dev.yml`
- Verify: `deploy/compose.runtime.*.ha.yml`

- [ ] **Step 1: Run the focused application regression test**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=RedisTopologyProfileTest test
```

Expected:

- PASS

- [ ] **Step 2: Render every supported topology/scope combination**

Run:

```bash
./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.dev.example >/tmp/community-dev-infra.yml
./deploy/deployment.sh config --topology dev --scope full --env-file deploy/.env.dev.example >/tmp/community-dev-full.yml
./deploy/deployment.sh config --topology ha --scope infra --env-file deploy/.env.ha.example >/tmp/community-ha-infra.yml
./deploy/deployment.sh config --topology ha --scope full --env-file deploy/.env.ha.example >/tmp/community-ha-full.yml
```

Expected:

- all four renders pass

- [ ] **Step 3: Sanity-check the rendered service names**

Run:

```bash
rg -n '^  mysql:$|^  redis:$|^  kafka:$|^  elasticsearch:$|^  nacos:$|^  xxl-job-admin:$|^  community-app:$|^  community-gateway:$|^  im-core:$|^  im-realtime:$' /tmp/community-dev-full.yml
rg -n '^  mysql-primary:$|^  redis-1:$|^  kafka-1:$|^  elasticsearch-1:$|^  nacos-1:$|^  xxl-job-admin-1:$|^  community-app-1:$|^  community-gateway-1:$|^  im-core-1:$|^  im-realtime-1:$' /tmp/community-ha-full.yml
./deploy/deployment.sh --help | rg -- '--topology|--scope'
```

Expected:

- dev render shows canonical single service names
- HA render shows the current numbered services
- help output exposes the new options

- [ ] **Step 4: Review the final diff stays inside scope**

Run:

```bash
git status --short
git diff --stat
```

Expected:

- only deploy topology files, the Redis config/test, and the intended docs are changed

- [ ] **Step 5: Commit the verified end state**

```bash
git add deploy backend/community-app README.md docs tools/im-load/README.md
git commit -m "feat(deploy): support dev and ha local topologies"
```
