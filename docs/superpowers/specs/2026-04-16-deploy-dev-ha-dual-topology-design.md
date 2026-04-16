# Deploy Dev/HA Dual Topology Design Spec

**Date:** 2026-04-16
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Background

The current `deploy/` layout is optimized for a local HA rehearsal stack:

- infrastructure is modeled as clustered or replicated by default
- runtime services are modeled as explicit multi-instance services
- the operator entrypoint in [deploy/deployment.sh](/home/feng/code/project/community/deploy/deployment.sh) always assembles the HA-oriented compose set
- ingress config in [deploy/nginx/nginx.conf](/home/feng/code/project/community/deploy/nginx/nginx.conf) is hard-wired to `community-gateway-1..3` and `xxl-job-admin-1..2`

This is useful for topology rehearsal, but it is too heavy for day-to-day local development and test loops. The repository now needs to support both:

1. a lightweight single-node local development topology
2. the existing local HA rehearsal topology

The user explicitly confirmed that both topologies must coexist and that the single-node variant must include single-node versions of:

- `Nacos`
- `MySQL`
- `Redis`
- `Kafka`
- `Elasticsearch`
- application runtime services

The repository also needs two operator scopes:

- full stack: infra + runtime + frontend/ingress
- infra only: infra for local IDE-driven service startup

---

## 2. Goals

### 2.1 Primary goals

- Keep the current HA deployment path available
- Add a first-class single-node `dev` topology for local development
- Support both `full` and `infra` scopes for each topology
- Keep `deploy/deployment.sh` as the single operator entrypoint
- Avoid making topology choice depend on hidden manual file selection
- Minimize drift between `dev` and `ha` definitions by keeping common metadata shared
- Make the topology choice visible in docs, env examples, and command help

### 2.2 Compatibility goals

- Keep existing HA service names and behavior unless explicitly split into topology-specific files
- Preserve current HA bootstrap flows for MySQL replication, Redis cluster bootstrap, Kafka topic bootstrap, Elasticsearch index bootstrap, Nacos cluster bootstrap, and XXL-JOB bootstrap
- Ensure the `dev` topology uses the same application code paths where possible, differing mainly in deployment wiring and environment values

---

## 3. Non-goals

- Do not introduce Kubernetes, Helm, or another orchestration layer
- Do not redesign the business service discovery model
- Do not remove the local HA topology
- Do not make compose dynamically generate arbitrary replica counts
- Do not unify cluster and standalone middleware definitions into a single unreadable compose file full of conditional env logic
- Do not change application-level business behavior unrelated to deployment topology
- Do not add a long-lived dual source of truth where both old and new operator models remain active

---

## 4. Current-state facts

### 4.1 Operator assembly is HA-only

[deploy/deployment.sh](/home/feng/code/project/community/deploy/deployment.sh) currently assembles a fixed list of compose files that always point at the HA layout.

### 4.2 Runtime ingress is topology-specific today

[deploy/nginx/nginx.conf](/home/feng/code/project/community/deploy/nginx/nginx.conf) statically references:

- `community-gateway-1`
- `community-gateway-2`
- `community-gateway-3`
- `xxl-job-admin-1`
- `xxl-job-admin-2`

This cannot be reused unchanged for a single-instance topology.

### 4.3 Most middleware clients already accept single-node addresses

The main runtime configuration already reads external addresses from env-backed properties:

- Nacos server address in:
  - [backend/community-app/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-app/src/main/resources/application.yml)
  - [backend/community-gateway/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-gateway/src/main/resources/application.yml)
  - [backend/community-im/im-core/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-im/im-core/src/main/resources/application.yml)
  - [backend/community-im/im-realtime/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-im/im-realtime/src/main/resources/application.yml)
- MySQL JDBC URLs in:
  - [backend/community-app/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-app/src/main/resources/application.yml)
  - [backend/community-im/im-core/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-im/im-core/src/main/resources/application.yml)
- Kafka bootstrap servers in:
  - [backend/community-im/im-core/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-im/im-core/src/main/resources/application.yml)
- Elasticsearch URIs in:
  - [backend/community-app/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-app/src/main/resources/application.yml)
- XXL-JOB admin ingress URL in:
  - [backend/community-app/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-app/src/main/resources/application.yml)

These are naturally compatible with both:

- a multi-address list for HA
- a single address for dev

### 4.4 Redis is the main application-side incompatibility

[backend/community-app/src/main/resources/application.yml](/home/feng/code/project/community/backend/community-app/src/main/resources/application.yml) currently exposes only:

- `spring.data.redis.cluster.nodes`

That means the runtime is wired for Redis cluster only. A single-node Redis compose service is not sufficient by itself; the application config must also support Redis standalone properties.

### 4.5 Existing env defaults are HA-oriented

[deploy/.env.example](/home/feng/code/project/community/deploy/.env.example) currently defaults to HA-style values such as:

- `NACOS_SERVER_ADDR=nacos-1:8848,nacos-2:8848,nacos-3:8848`
- `SPRING_DATA_REDIS_CLUSTER_NODES=redis-1:6379,...,redis-6:6379`
- `KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092`
- `ELASTICSEARCH_URIS=http://elasticsearch-1:9200,http://elasticsearch-2:9200,http://elasticsearch-3:9200`

The current file cannot serve as a clean single-node default.

---

## 5. Options considered

### 5.1 Option A: keep one compose topology and vary only replica counts

Approach:

- keep the current files
- introduce `*_REPLICA_COUNT`
- try to collapse clustered middleware to single-node by env substitution

Pros:

- low file-count growth
- minimal operator surface changes

Cons:

- does not fit topology-shaped differences such as Redis cluster bootstrap, Kafka controller quorum, Elasticsearch discovery, Nacos cluster membership, or nginx upstream targets
- would produce dense, conditional compose files that are difficult to review
- still would not solve the Redis standalone application config gap cleanly

Conclusion:

- reject

### 5.2 Option B: maintain fully separate `deploy/dev` and `deploy/ha` trees

Approach:

- split all files into two directories
- each directory owns its own env examples, nginx config, and operator composition

Pros:

- clear mental model for each topology
- low ambiguity about which files belong to which stack

Cons:

- duplicates common definitions aggressively
- increases drift risk
- makes later shared edits more expensive

Conclusion:

- reject

### 5.3 Option C: shared base + topology-specific overlays with one operator

Approach:

- keep shared base metadata in `deploy/`
- split topology-sensitive layers into `*.dev.yml` and `*.ha.yml`
- let `deploy/deployment.sh` assemble files based on `--topology` and `--scope`
- split env examples and nginx config by topology

Pros:

- clean separation where topology truly differs
- keeps one operator entrypoint
- limits duplication to topology-sensitive files
- matches the repository's existing layered compose direction

Cons:

- requires coordinated changes across operator script, docs, env files, and runtime wiring
- introduces more compose filenames

Conclusion:

- adopt

---

## 6. Proposed design

### 6.1 Operator interface

[deploy/deployment.sh](/home/feng/code/project/community/deploy/deployment.sh) will support explicit topology and scope selection:

```bash
./deploy/deployment.sh up --topology dev
./deploy/deployment.sh up --topology dev --scope infra
./deploy/deployment.sh up --topology ha
./deploy/deployment.sh config --topology dev
```

New options:

- `--topology <dev|ha>`
- `--scope <full|infra>`

Defaults:

- `--topology ha`
- `--scope full`

Rationale:

- defaulting to `ha` preserves existing behavior for current operators
- `infra` enables local IDE-driven service startup without maintaining a second script

### 6.2 Project naming

Default compose project name will become topology-aware:

- `community-dev` for `--topology dev`
- `community-ha` for `--topology ha`

The existing `-p` / `--project-name` override remains supported.

Rationale:

- prevents volume and network collisions between dev and HA stacks
- avoids cross-topology reuse of stale Kafka, Elasticsearch, Redis, or Nacos state

### 6.3 File layout

The deployment layout will evolve toward:

```text
deploy/
  compose.yml
  compose.infra.mysql.dev.yml
  compose.infra.mysql.ha.yml
  compose.infra.redis.dev.yml
  compose.infra.redis.ha.yml
  compose.infra.kafka.dev.yml
  compose.infra.kafka.ha.yml
  compose.infra.elasticsearch.dev.yml
  compose.infra.elasticsearch.ha.yml
  compose.infra.nacos.dev.yml
  compose.infra.nacos.ha.yml
  compose.infra.xxl-job.dev.yml
  compose.infra.xxl-job.ha.yml
  compose.infra.mailhog.yml
  compose.infra.mock-data-studio-bootstrap.yml
  compose.runtime.services.dev.yml
  compose.runtime.services.ha.yml
  compose.runtime.frontend-nginx.dev.yml
  compose.runtime.frontend-nginx.ha.yml
  nginx/nginx.dev.conf
  nginx/nginx.ha.conf
  .env.dev.example
  .env.ha.example
```

Notes:

- `compose.yml`, `compose.infra.mailhog.yml`, and `compose.infra.mock-data-studio-bootstrap.yml` stay shared unless topology-specific behavior appears later
- the current HA files can either be renamed to `*.ha.yml` or retained temporarily and wrapped by thin `*.ha.yml` aliases during migration; the final steady state should use topology-explicit names

### 6.4 Scope assembly

`deployment.sh` will build the compose file list in this order:

1. shared base:
   - `deploy/compose.yml`
2. topology-specific infra:
   - when `topology=dev`, use the `*.dev.yml` infra files
   - when `topology=ha`, use the `*.ha.yml` infra files
3. shared infra:
   - `deploy/compose.infra.mailhog.yml`
   - `deploy/compose.infra.mock-data-studio-bootstrap.yml`
4. runtime layers when `scope=full`:
   - when `topology=dev`, use:
     - `deploy/compose.runtime.services.dev.yml`
     - `deploy/compose.runtime.frontend-nginx.dev.yml`
   - when `topology=ha`, use:
     - `deploy/compose.runtime.services.ha.yml`
     - `deploy/compose.runtime.frontend-nginx.ha.yml`
5. observability overlay when requested

This keeps operator behavior deterministic and explicit.

---

## 7. Topology definitions

### 7.1 Dev topology

`dev` is a single-node local development stack:

- MySQL: `mysql`
- Redis: `redis`
- Kafka: `kafka`
- Elasticsearch: `elasticsearch`
- Nacos: `nacos`
- XXL-JOB admin: `xxl-job-admin`
- runtime services:
  - `community-app`
  - `community-gateway`
  - `im-core`
  - `im-realtime`
- ingress:
  - `nginx`
  - `frontend`

Behavioral rules:

- no replication bootstrap
- no Redis cluster bootstrap
- no Kafka multi-controller quorum
- no Elasticsearch multi-node discovery
- no Nacos cluster membership list
- no multi-target nginx upstream pools

### 7.2 HA topology

`ha` preserves the existing local rehearsal behavior:

- MySQL primary + replicas
- Redis cluster + cluster bootstrap
- Kafka three-node KRaft cluster + topic bootstrap
- Elasticsearch three-node cluster + index bootstrap
- Nacos three-node cluster + DB bootstrap
- XXL-JOB admin dual instances
- runtime services with explicit multi-instance services
- nginx load-balancing to gateway and XXL-JOB admin pools

No functional topology changes are intended for `ha` beyond renaming files and moving assembly logic into `--topology ha`.

---

## 8. Environment model

### 8.1 Split env examples

Replace the single HA-oriented example with:

- [deploy/.env.dev.example](/home/feng/code/project/community/deploy/.env.dev.example)
- [deploy/.env.ha.example](/home/feng/code/project/community/deploy/.env.ha.example)

`deployment.sh` default env file resolution:

- `deploy/.env.dev` when `--topology dev`
- `deploy/.env.ha` when `--topology ha`

`--env-file` still overrides either default.

### 8.2 Topology-specific defaults

Examples of expected defaults:

- `dev`
  - `NACOS_SERVER_ADDR=nacos:8848`
  - `SPRING_DATA_REDIS_HOST=redis`
  - `SPRING_DATA_REDIS_PORT=6379`
  - `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`
  - `ELASTICSEARCH_URIS=http://elasticsearch:9200`
  - `DB_PRIMARY_HOST=mysql`
  - `IM_CORE_DB_PRIMARY_HOST=mysql`
  - `XXL_JOB_ADMIN_INGRESS_URL=http://nginx:8081/xxl-job-admin`
- `ha`
  - retain the current multi-address and clustered defaults

### 8.3 Stable application env names

Where possible, runtime env names should remain stable across topologies.

Examples:

- `NACOS_SERVER_ADDR`
- `KAFKA_BOOTSTRAP_SERVERS`
- `ELASTICSEARCH_URIS`
- `DB_URL`
- `IM_CORE_DB_URL`
- `XXL_JOB_ADMIN_INGRESS_URL`

This limits code changes to the true compatibility gap: Redis standalone support.

---

## 9. Redis compatibility change

### 9.1 Problem

The application currently only publishes `spring.data.redis.cluster.nodes`, which is appropriate for HA but not for single-node Redis.

### 9.2 Design

Refactor Redis runtime configuration to support both standalone and cluster modes cleanly.

Recommended approach:

- move Redis cluster-specific properties into a dedicated profile file such as `application-redis-cluster.yml`
- leave the base `application.yml` compatible with standalone Redis defaults
- activate the Redis cluster profile only in HA runtime compose files
- in dev runtime compose files, provide standalone Redis env such as:
  - `SPRING_DATA_REDIS_HOST`
  - `SPRING_DATA_REDIS_PORT`

Rationale:

- keeps standalone as the simple default
- avoids mixing cluster and standalone settings in a single opaque property block
- keeps HA-specific behavior explicit in deployment wiring

### 9.3 Expected application behavior

After this change:

- dev topology connects to single-node Redis without code-side conditional branching
- HA topology keeps using Redis cluster semantics
- components based on `StringRedisTemplate` continue to work unchanged because they depend on Spring Boot's `RedisConnectionFactory`, not on cluster-only APIs

---

## 10. Runtime and ingress split

### 10.1 Runtime services

The current runtime files with explicit `-1`, `-2`, `-3` service names should be split into:

- `compose.runtime.services.dev.yml`
- `compose.runtime.services.ha.yml`

`dev` will use canonical single service names:

- `community-app`
- `community-gateway`
- `im-core`
- `im-realtime`

`ha` will keep current explicit instance naming.

### 10.2 Nginx config

Ingress must be topology-specific:

- [deploy/nginx/nginx.dev.conf](/home/feng/code/project/community/deploy/nginx/nginx.dev.conf)
- [deploy/nginx/nginx.ha.conf](/home/feng/code/project/community/deploy/nginx/nginx.ha.conf)

`dev` upstreams point to:

- `community-gateway:8080`
- `xxl-job-admin:8080`

`ha` upstreams keep the current multi-backend pools.

### 10.3 Frontend runtime wiring

`compose.runtime.frontend-nginx.dev.yml` and `compose.runtime.frontend-nginx.ha.yml` will continue to expose the same browser-facing ports unless the user overrides them. This avoids changing the developer-facing URLs between topologies.

---

## 11. Documentation changes

Update [deploy/README.md](/home/feng/code/project/community/deploy/README.md) to describe:

- the meaning of `dev` vs `ha`
- the meaning of `full` vs `infra`
- default env files per topology
- expected resource differences
- example commands for:
  - single-node full stack
  - single-node infra only
  - HA full stack
  - HA infra only if supported

The README should make it explicit that:

- `dev` is the default recommendation for local feature work and debugging
- `ha` is the heavier rehearsal topology for cluster-path validation

---

## 12. Migration strategy

### 12.1 Execution order

Implement in this order:

1. operator script support for topology and scope
2. env example split
3. topology-specific infra compose files
4. topology-specific runtime and nginx files
5. Redis standalone compatibility in application config
6. README updates

### 12.2 Compatibility during migration

During the refactor, keep the external command:

```bash
./deploy/deployment.sh up
```

functionally equivalent to the current HA full-stack behavior.

This avoids breaking existing workflows while enabling the new `dev` path incrementally.

### 12.3 Volume isolation

Because middleware state layout differs between `dev` and `ha`, the migration must not attempt to share named volumes across the two topologies.

Topology-aware project names are sufficient; no extra volume renaming is required if compose project names differ.

---

## 13. Testing and verification strategy

### 13.1 Operator verification

At minimum:

- render merged config for `dev/full`
- render merged config for `dev/infra`
- render merged config for `ha/full`
- ensure resulting service names and `depends_on` targets are valid

### 13.2 Runtime verification

For `dev/full`:

- all single-node middleware becomes healthy
- `community-gateway`, `community-app`, `im-core`, `im-realtime` register into single-node Nacos
- frontend reaches API through nginx
- XXL-JOB admin ingress is reachable

For `ha/full`:

- current behavior remains intact
- nginx upstream targets remain valid

### 13.3 Application verification

Redis-specific verification is mandatory:

- dev runtime works against standalone Redis
- HA runtime still works against Redis cluster

This is the most likely regression surface because it changes application-level wiring rather than pure deploy topology.

---

## 14. Risks

### 14.1 Drift between topologies

Mitigation:

- keep shared env names and shared base compose metadata
- duplicate only truly topology-shaped files

### 14.2 Hidden `depends_on` or hostname assumptions

Mitigation:

- render compose configs and inspect service references before runtime testing
- update service aliases deliberately where runtime services expect stable logical names

### 14.3 Redis config regression

Mitigation:

- implement standalone support explicitly rather than relying on accidental Spring fallback behavior
- verify both topologies after the change

---

## 15. Recommendation

Adopt the shared-base, topology-specific overlay model with one operator entrypoint.

This gives the repository:

- a fast single-node developer path
- a preserved HA rehearsal path
- a clear operator interface
- explicit topology ownership in compose, nginx, and env files

The only required application-level compatibility change is Redis standalone support; the rest of the runtime configuration is already structurally compatible with both single-node and HA address models.
