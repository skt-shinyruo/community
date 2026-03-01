# Modular Monolith (A-1) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Convert the current multi-service backend into a single deployable Spring Boot application (“modular monolith”), removing Dubbo/Nacos/Gateway, and unifying MySQL to a single schema + single datasource — while keeping external `/api/**` paths stable.

**Architecture:** Add a new `app/community-app` Spring Boot module that depends on existing domain modules as libraries. Centralize runtime configuration and security into `community-app`. Replace Dubbo RPC with in-process Spring beans. Unify DB init scripts to a single `community` schema and resolve shared-table conflicts (`outbox_event`, `http_idempotency`).

**Tech Stack:** Java 17, Spring Boot 3.2.x, Spring Security (JWT resource server), MyBatis, Redis, Kafka, Elasticsearch, Micrometer/Prometheus.

---

## Work Area / Branch

This plan assumes execution inside the git worktree created for the refactor:

- Worktree: `.worktrees/modular-monolith-a1/`
- Branch: `refactor/modular-monolith-a1`

All file paths below are **relative to the repo root**, unless explicitly prefixed with `.worktrees/modular-monolith-a1/`.

---

## Task 0: Preparation (baseline verification)

**Files:** none

**Step 1: Verify current baseline tests pass**

Run (inside the worktree):

- `mvn test -q`

Expected:

- Exit code 0 (PASS). Some WARN logs are acceptable.

**Step 2 (Optional): Record baseline git status**

- `git status -sb`

Expected: clean working tree.

---

## Task 1: Add the new `community-app` Maven module

**Files:**
- Create: `.worktrees/modular-monolith-a1/app/pom.xml`
- Create: `.worktrees/modular-monolith-a1/app/community-app/pom.xml`
- Create: `.worktrees/modular-monolith-a1/app/community-app/src/main/java/com/nowcoder/community/app/CommunityAppApplication.java`
- Create: `.worktrees/modular-monolith-a1/app/community-app/src/main/resources/application.yml`
- Modify: `.worktrees/modular-monolith-a1/pom.xml`

**Step 1: Add `app` module to the root aggregator**

Update `.worktrees/modular-monolith-a1/pom.xml` `<modules>` to include:

- `<module>community-bootstrap</module>`

**Step 2: Create `app/pom.xml` aggregator**

Create a packaging `pom` with:

- parent: `community`
- artifactId: `app`
- modules: `community-app`

**Step 3: Create `app/community-app/pom.xml`**

Create a packaging `jar` module with:

- parent: `community`
- artifactId: `community-app`
- dependencies on the former “service” modules (as libraries):
  - `auth-service`
  - `user/user-service`
  - `content/content-service`
  - `social/social-service`
  - `message/message-service`
  - `search/search-service`
  - `analytics/analytics-service`
  - `ops-service`

Also include:

- `spring-boot-starter-actuator` (if not already transitively present)
- `micrometer-registry-prometheus` (if needed)
- `spring-boot-maven-plugin` (only the app should be boot-repackaged)

**Step 4: Add the unified main class**

Create `CommunityAppApplication`:

- Put it under `com.nowcoder.community.app`
- Enable scheduling: `@EnableScheduling` (needed for outbox relay, score refresh jobs, etc.)
- Component scan:
  - base: `com.nowcoder.community`
  - exclude other `@SpringBootApplication` classes so they don’t get registered as `@Configuration`
    (exclude by annotation: `SpringBootApplication.class`)

**Step 5: Add minimal app config**

Create `app/community-app/src/main/resources/application.yml` with:

- `spring.application.name: community-app`
- `server.port: 8080` (or the existing gateway port you prefer)
- minimal `management.endpoints.web.exposure.include: health,info,prometheus`

Do not migrate full config yet — just enough to boot.

**Step 6: Build and run unit tests**

Run:

- `mvn -pl app/community-app -am test -q`

Expected:

- Build succeeds.

**Step 7 (Optional): Commit checkpoint**

If commits are allowed:

- `git add -A`
- `git commit -m "refactor: add community-app module skeleton"`

---

## Task 2: Ensure dependent modules produce plain JARs (disable Boot repackage)

**Why:** A repackaged Spring Boot “fat jar” places classes under `BOOT-INF/classes`, which **cannot** be used as a normal library dependency. The monolith requires the former service modules to produce **plain jars**.

**Files (modify):**
- `.worktrees/modular-monolith-a1/content/content-service/pom.xml`
- `.worktrees/modular-monolith-a1/social/social-service/pom.xml`
- `.worktrees/modular-monolith-a1/user/user-service/pom.xml`
- `.worktrees/modular-monolith-a1/auth-service/pom.xml`
- `.worktrees/modular-monolith-a1/message/message-service/pom.xml`
- `.worktrees/modular-monolith-a1/search/search-service/pom.xml`
- `.worktrees/modular-monolith-a1/analytics/analytics-service/pom.xml`
- `.worktrees/modular-monolith-a1/ops-service/pom.xml`

**Step 1: Disable spring-boot repackage for library modules**

In each of the above `pom.xml`, set the `spring-boot-maven-plugin` configuration to skip repackage:

```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <configuration>
    <skip>true</skip>
  </configuration>
</plugin>
```

**Step 2: Rebuild `community-app`**

Run:

- `mvn -pl app/community-app -am test -q`

Expected:

- Build still succeeds.

**Step 3 (Optional): Commit checkpoint**

- `git add -A`
- `git commit -m "refactor: make former services build as libraries"`

---

## Task 3: Centralize configuration (single `application*.yml`)

**Goal:** Ensure only `community-app` provides `application.yml` and `application-<profile>.yml`, avoiding Spring’s classpath merge surprises.

**Files:**
- Modify/Create: `.worktrees/modular-monolith-a1/app/community-app/src/main/resources/application.yml`
- Create (optional): `.worktrees/modular-monolith-a1/app/community-app/src/main/resources/application-prod.yml`
- Rename (move away from `application*.yml` names):
  - `.worktrees/modular-monolith-a1/**/src/main/resources/application.yml`
  - `.worktrees/modular-monolith-a1/**/src/main/resources/application-*.yml`

**Step 1: Rename module `application.yml` files**

For each former service module, rename:

- `src/main/resources/application.yml` → `src/main/resources/module-config.yml` (or similar non-`application*` name)

Also rename profile files (important):

- `application-prod.yml` → `module-config-prod.yml`
- `application-dev.yml` → `module-config-dev.yml`

**Step 2: Expand `community-app` config**

Merge the required properties into the app’s `application.yml`:

- Single datasource (`spring.datasource.*`) targeting schema `community`
- Redis (`spring.data.redis.*`) used by auth/content/social/analytics
- Kafka (`spring.kafka.*`) used by outbox relay + consumers
- ES properties used by search
- Shared `security.jwt.hmac-secret`
- Existing `community.metrics.basic-auth.*`

**Step 3: Verify no extra `application*.yml` remain**

Run:

- `find . -path '*/src/main/resources/application*.yml'`

Expected:

- Only `app/community-app/src/main/resources/application.yml` (and optionally `application-prod.yml`) remain.

**Step 4: Test compilation**

Run:

- `mvn -pl app/community-app -am test -q`

Expected:

- Build succeeds.

---

## Task 4: Consolidate security into one filter chain

**Goal:** Remove conflicting `SecurityFilterChain` beans from per-module configs and replace with one consolidated config in `community-app`.

**Files:**
- Create: `.worktrees/modular-monolith-a1/app/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java`
- Modify/Delete (choose one approach and be consistent):
  - `.worktrees/modular-monolith-a1/content/content-service/src/main/java/com/nowcoder/community/content/config/ContentSecurityConfig.java`
  - `.worktrees/modular-monolith-a1/social/social-service/src/main/java/com/nowcoder/community/social/config/SocialSecurityConfig.java`
  - `.worktrees/modular-monolith-a1/auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java`
  - `.worktrees/modular-monolith-a1/user/user-service/src/main/java/com/nowcoder/community/user/config/UserSecurityConfig.java`
  - `.worktrees/modular-monolith-a1/message/message-service/src/main/java/com/nowcoder/community/message/config/MessageSecurityConfig.java`
  - `.worktrees/modular-monolith-a1/search/search-service/src/main/java/com/nowcoder/community/search/config/SearchSecurityConfig.java`
  - `.worktrees/modular-monolith-a1/analytics/analytics-service/src/main/java/com/nowcoder/community/analytics/config/AnalyticsSecurityConfig.java`
  - `.worktrees/modular-monolith-a1/ops-service/src/main/java/com/nowcoder/community/ops/config/OpsSecurityConfig.java`

**Step 1: Create a single `SecurityFilterChain`**

Implement one `SecurityFilterChain` bean that:

- does **not** match `/actuator/**` (actuator chain remains from `infra-security-starter`)
- merges all previous `authorizeHttpRequests` rules by path prefix:
  - `/api/auth/**` rules from auth
  - `/api/posts/**`, `/api/categories/**`, `/api/tags/**`, etc from content
  - `/api/likes/**`, `/api/follows/**`, `/api/blocks/**` from social
  - `/api/messages/**`, `/api/notices/**` from message
  - `/api/search/**` from search
  - `/api/analytics/**` from analytics
  - `/api/ops/**` from ops

**Step 2: Remove per-module security configs**

Preferred approach:

- Delete the per-module `*SecurityConfig` classes (they no longer represent separate boundaries).

Alternative:

- Guard them behind a profile that is never enabled in the monolith (e.g. `@Profile("legacy-microservices")`).

**Step 3: Verify build**

- `mvn -pl app/community-app -am test -q`

---

## Task 5: Unify MySQL schema + resolve shared table conflicts

**Goal:** One schema `community`, one datasource, no duplicate table names (`outbox_event`, `http_idempotency`).

**Files (modify/create):**
- Modify: `.worktrees/modular-monolith-a1/deploy/mysql-init/001_create_databases.sh`
- Create: `.worktrees/modular-monolith-a1/deploy/mysql-init/005_schema_shared.sql`
- Modify: `.worktrees/modular-monolith-a1/deploy/mysql-init/020_schema_content.sql`
- Modify: `.worktrees/modular-monolith-a1/deploy/mysql-init/025_schema_social.sql`
- Modify: `.worktrees/modular-monolith-a1/deploy/mysql-init/030_schema_message.sql`
- Modify: `.worktrees/modular-monolith-a1/deploy/mysql-init/040_schema_search.sql`

**Step 1: Make DB creation single-schema**

Update `001_create_databases.sh` to:

- only create database `community`
- create one app user (or reuse existing `MYSQL_USER/MYSQL_PASSWORD`)
- remove creation of `community_content/community_social/community_message/community_search`

**Step 2: Add shared schema script**

Create `005_schema_shared.sql` (using `use community;`) containing:

- one `outbox_event` table compatible with `platform/infra-outbox` mapper fields
- one `http_idempotency` table used by `JdbcIdempotencyStore`

**Step 3: Update domain schema scripts to `use community;`**

For each of `020/025/030/040`:

- change `use community_xxx;` → `use community;`
- remove duplicate `outbox_event` or `http_idempotency` definitions (now provided by `005_schema_shared.sql`)

**Step 4: Prefix idempotency operations**

Update all `IdempotencyGuard.execute*` calls to use prefixed ops:

- `create_comment` → `content:create_comment`
- message send operations → `message:...`

This avoids cross-domain uniqueness collisions in the shared table.

**Step 5: Verify build**

- `mvn test -q`

---

## Task 6: Remove Dubbo + Nacos + Gateway wiring

**Goal:** No internal RPC framework for module-module calls; no service discovery/config center; no routing gateway module required.

**Files (modify/delete):**
- Replace Dubbo annotations:
  - `.worktrees/modular-monolith-a1/content/**` (`@DubboService`, `@DubboReference`)
  - `.worktrees/modular-monolith-a1/social/**`
  - `.worktrees/modular-monolith-a1/user/**`
  - `.worktrees/modular-monolith-a1/message/**`
  - `.worktrees/modular-monolith-a1/search/**`
  - `.worktrees/modular-monolith-a1/analytics/**`
  - `.worktrees/modular-monolith-a1/ops-service/**`
- Remove module build:
  - `.worktrees/modular-monolith-a1/pom.xml` (remove `<module>gateway</module>`)

**Step 1: Convert `@DubboService` implementations to Spring beans**

Example:

- `@DubboService` → `@Service`

**Step 2: Convert `@DubboReference` to constructor injection**

Replace fields like:

- `@DubboReference private XxxxRpcService rpc;`

with:

- `private final XxxxRpcService rpc;`
- constructor injection

**Step 3: Remove Dubbo config blocks**

Delete `dubbo:` sections from app config.

**Step 4: Remove Nacos config import blocks**

Delete:

- `spring.config.import: "optional:nacos:..."`
- `spring.cloud.nacos.*` blocks

and remove corresponding Maven dependencies once code compiles.

**Step 5: Remove Gateway module from the build**

In `.worktrees/modular-monolith-a1/pom.xml`, remove:

- `<module>gateway</module>`

Optionally archive the `gateway/` directory later, but build removal is the critical step.

**Step 6: Verify build + tests**

- `mvn test -q`

---

## Task 7: Smoke run (manual)

**Goal:** Start the monolith locally and verify key endpoints.

**Step 1: Start dependencies**

Use the existing compose file(s), but only keep MySQL/Redis/Kafka/ES as needed.

**Step 2: Run the app**

- `mvn -pl app/community-app -am spring-boot:run`

Expected:

- app starts successfully on the configured port
- `/actuator/health` returns UP

**Step 3: Basic API checks**

- Auth: `POST /api/auth/login`
- Content: `GET /api/posts`
- Social: `GET /api/likes/count?entityType=...&entityId=...` (or existing endpoints)

---

## Execution Handoff

Plan saved to:

- `.worktrees/modular-monolith-a1/docs/plans/2026-02-27-modular-monolith-a1-implementation-plan.md`

Two execution options:

1) **Subagent-Driven (this session)** — dispatch a subagent per task, review between tasks.
2) **Parallel Session** — open a new session and run with `superpowers:executing-plans`.
