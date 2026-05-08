# Community OSS Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a standalone `community-oss` service and typed client that own OSS objects end-to-end, route `/api/oss/**` and `/files/**` through that service, and move the current user avatar/file path onto OSS without exposing storage-provider details to business code.

**Architecture:** `community-oss` becomes a separate Spring Boot deployable with the same controller/application/domain/infrastructure layering used elsewhere in the repo. OSS owns object metadata, versions, grants, lifecycle, aliases, and reference tracking; storage is hidden behind one `ObjectStore` port with local filesystem and S3-compatible backends. `community-app` becomes a consumer through a typed client, so the existing avatar/file path migrates without coupling business code back to Garage, Ceph RGW, or any other object-store implementation.

**Tech Stack:** Spring Boot 3.2, Spring Web, Spring Security, MyBatis, MySQL, Redis, Nacos Discovery, AWS SDK v2 S3 client, Docker Compose, JUnit 5, Mockito, ArchUnit, Testcontainers/H2, Bash.

---

## Scope

This plan covers the OSS spec in `docs/superpowers/specs/2026-05-07-community-oss-service-design.md`.

The repo currently has one live file-storage consumer: user avatars and `/files/**` inside `community-app`. There are no current content/market/wallet/ops file flows to migrate, so this implementation focuses on:

- the generic OSS service contract that can serve all owner domains later
- the concrete `community-app` avatar/file migration that already exists in code
- the deployment and guardrail wiring needed to make OSS a real standalone service

## Current Files And Responsibilities

Modify:

- `backend/pom.xml`: add `community-oss` and `community-oss-client` modules.
- `backend/community-app/pom.xml`: add the OSS client dependency and remove direct storage-provider coupling.
- `backend/community-gateway/src/main/resources/application.yml`: add `/api/oss/**` and `/files/**` routing to `community-oss`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/**`: migrate avatar/file behavior to the OSS client boundary.
- `backend/community-app/src/main/resources/application.yml`: replace user-local avatar/file storage settings with OSS client wiring.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/**`: update guardrails for the new OSS client packages and remove legacy file-storage assumptions.
- `deploy/compose*.yml`, `deploy/.env.single.example`, `deploy/.env.cluster.example`: add `community-oss` and Garage topology wiring.
- `deploy/mysql/primary-init/001_create_databases.sh`: add OSS database/user bootstrap.
- `docs/handbook/architecture.md`, `docs/handbook/system-design.md`, `docs/handbook/data-and-storage.md`, `docs/handbook/business-logic/README.md`: document OSS ownership and the migration path.

Create:

- `backend/community-oss/**`: the new OSS service module.
- `backend/community-oss-client/**`: typed HTTP client and DTOs for consumers.
- `backend/community-oss/src/test/java/com/nowcoder/community/oss/arch/**`: OSS-side boundary tests for the new deployable.
- `deploy/mysql/community_oss/**`: OSS schema bootstrap and seed scripts.
- `deploy/tests/oss_topology.sh`: config smoke test for OSS + Garage wiring.
- `docs/handbook/business-logic/oss.md`: OSS ownership and contract summary.

## Task 1: Scaffold The OSS Modules

**Files:**

- Create: `backend/community-oss/pom.xml`
- Create: `backend/community-oss-client/pom.xml`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/OssApplication.java`
- Create: `backend/community-oss/src/main/resources/application.yml`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/OssApplicationSmokeTest.java`
- Create: `backend/community-oss-client/src/test/java/com/nowcoder/community/oss/client/OssClientSmokeTest.java`
- Modify: `backend/pom.xml`

- [ ] **Step 1: Write the failing module smoke tests**

Create a trivial application-context test for the new service and a client-module compile smoke test:

```java
@SpringBootTest
class OssApplicationSmokeTest {

    @Test
    void contextLoads() {
    }
}
```

```java
class OssClientSmokeTest {

    @Test
    void clientTypesCompile() {
        assertThat(CommunityOssClient.class).isNotNull();
    }
}
```

- [ ] **Step 2: Run the module build and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :community-oss,:community-oss-client -am test
```

Expected: FAIL because the modules and application classes do not exist yet.

- [ ] **Step 3: Add the minimal Maven and Spring Boot skeleton**

Add the two new module poms, wire them into `backend/pom.xml`, and add the smallest possible Spring Boot entrypoint and `application.yml` so both modules compile.

```java
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.nowcoder.community")
@MapperScan(basePackages = "com.nowcoder.community", annotationClass = Mapper.class)
public class OssApplication {
    public static void main(String[] args) {
        SpringApplication.run(OssApplication.class, args);
    }
}
```

- [ ] **Step 4: Re-run the module build and verify it passes**

Run:

```bash
cd backend
mvn -q -pl :community-oss,:community-oss-client -am test
```

Expected: PASS.

- [ ] **Step 5: Commit the scaffold**

```bash
git add backend/pom.xml backend/community-oss backend/community-oss-client
git commit -m "feat: scaffold community oss modules"
```

## Task 2: Implement The OSS Core Model And Storage

**Files:**

- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/**`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/domain/**`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/persistence/**`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/infrastructure/storage/**`
- Create: `backend/community-oss/src/main/resources/mapper/**`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/domain/**`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/**`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/infrastructure/storage/**`
- Create: `deploy/mysql/community_oss/001_bootstrap.sh`
- Create: `deploy/mysql/community_oss/010_schema.sql`

- [ ] **Step 1: Write the failing lifecycle, permission, and storage tests**

Start with one failing test per major behavior:

```java
@Test
void activatingAnUploadedObjectShouldCreateAnActiveVersionAndKeepAliasLookupWorking() {
    // arrange, act, assert
}
```

```java
@Test
void garageAdapterShouldPutAndReadObjectsThroughTheS3CompatiblePort() {
    // arrange, act, assert
}
```

```java
@Test
void localFilesystemAdapterShouldStreamAndHeadObjects() {
    // arrange, act, assert
}
```

- [ ] **Step 2: Run the OSS core tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :community-oss -Dtest='*Test' test
```

Expected: FAIL because the domain, repository, and storage classes are still missing.

- [ ] **Step 3: Implement the OSS aggregate and repositories**

Add the schema and MyBatis persistence for:

- object registry
- object versions
- upload sessions
- access grants
- object references
- usage policies
- aliases

Keep the persistence layer behind repository interfaces in `domain.repository`, and keep storage-provider details inside `infrastructure.storage`.

- [ ] **Step 4: Implement the minimal object-store port and adapters**

Add one `ObjectStore` interface and two adapters:

- `LocalFilesystemObjectStore`
- `GarageS3CompatibleObjectStore` or `S3CompatibleObjectStore` backed by the Garage S3 API

The domain and application layers must only depend on the port.

- [ ] **Step 5: Re-run the OSS core tests and verify they pass**

Run:

```bash
cd backend
mvn -q -pl :community-oss -Dtest='*Test' test
```

Expected: PASS.

- [ ] **Step 6: Commit the OSS core**

```bash
git add backend/community-oss deploy/mysql/community_oss
git commit -m "feat: add oss core model and storage"
```

## Task 3: Expose OSS HTTP APIs And Client Contracts

**Files:**

- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/controller/**`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/api/**`
- Create: `backend/community-oss/src/main/java/com/nowcoder/community/oss/contracts/**`
- Create: `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/**`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/http/HttpRoutingIntegrationTest.java`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/controller/**`
- Create: `backend/community-oss-client/src/test/java/com/nowcoder/community/oss/client/**`
- Create: `deploy/tests/oss_topology.sh`

- [ ] **Step 1: Write the failing API and routing tests**

Write tests for:

- OSS upload-session creation
- OSS object metadata lookup
- OSS signed-url lookup
- `/files/**` public download routing
- gateway routing for `/api/oss/**` and `/files/**`

Example controller test:

```java
@WebMvcTest(OssObjectController.class)
class OssObjectControllerTest {

    @Test
    void getMetadataShouldReturnObjectDetails() {
        // arrange, act, assert
    }
}
```

- [ ] **Step 2: Run the OSS API tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :community-oss -Dtest='*ControllerTest,*ApiTest,*ClientTest' test
```

Expected: FAIL because the controllers, DTOs, and client types are not implemented yet.

- [ ] **Step 3: Implement the HTTP controllers and typed client**

Add the public OSS controller surface in `community-oss` and a small typed client module in `community-oss-client` with request/response DTOs only.

The client should cover the same use cases the service exposes:

- prepare upload session
- complete upload
- resolve metadata
- resolve signed URLs
- bind and release references
- delete objects

- [ ] **Step 4: Wire gateway routing and deployment smoke checks**

Route `/api/oss/**` and `/files/**` to `community-oss`, then add a deploy smoke test that renders compose config and checks that the OSS service and Garage appear in the expected topology.

- [ ] **Step 5: Re-run the OSS API and routing tests and verify they pass**

Run:

```bash
cd backend
mvn -q -pl :community-oss -Dtest='*ControllerTest,*ApiTest,*ClientTest' test

cd ../deploy
./tests/oss_topology.sh
```

Expected: PASS.

- [ ] **Step 6: Commit the API and client layer**

```bash
git add backend/community-oss backend/community-oss-client backend/community-gateway deploy/tests/oss_topology.sh
git commit -m "feat: expose oss api and client contracts"
```

## Task 4: Migrate The Existing User File Flow

**Current implemented shape:**

- `UserController` keeps HTTP binding for avatar upload token, upload, and update.
- `UserAvatarApplicationService` owns authorization and header URL projection updates.
- `user.infrastructure.oss.OssAvatarStorageAdapter` implements `AvatarStoragePort` through `community-oss-client`.
- `/files/**` is owned by `community-oss` and routed by `community-gateway`.
- `OssAvatarStorageMigrationRetirementTest` guards against reintroducing retired `community-app` avatar storage classes and environment settings.

- [ ] **Step 1: Verify the avatar flow uses the OSS client boundary**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='UserAvatarApplicationServiceTest,OssAvatarStorageMigrationRetirementTest,UserControllerLoggingTest' test
```

Expected: PASS.

- [ ] **Step 2: Verify frontend avatar upload accepts the OSS provider**

Run:

```bash
cd frontend
npm test -- SettingsView.test.js
```

Expected: PASS.

- [ ] **Step 3: Commit the migration**

```bash
git add backend/community-app frontend/src/views/SettingsView.vue frontend/src/views/SettingsView.test.js
git commit -m "feat: migrate user avatar flow to oss"
```

## Task 5: Wire Deployment, Guardrails, And Documentation

**Files:**

- Modify: `deploy/compose.yml`
- Modify: `deploy/compose.infra.mysql.single.yml`
- Modify: `deploy/compose.infra.mysql.cluster.yml`
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Create: `deploy/compose.infra.garage.single.yml`
- Create: `deploy/compose.infra.garage.cluster.yml`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/.env.cluster.example`
- Modify: `deploy/mysql/primary-init/001_create_databases.sh`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/**`
- Create: `backend/community-oss/src/test/java/com/nowcoder/community/oss/arch/**`
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `docs/handbook/data-and-storage.md`
- Modify: `docs/handbook/business-logic/README.md`
- Create: `docs/handbook/business-logic/oss.md`

- [ ] **Step 1: Write the failing deployment and guardrail tests**

Add config smoke checks that assert the rendered compose config contains:

- `community-oss`
- Garage in the single-node topology
- Garage replication in the cluster topology
- `/api/oss/**` and `/files/**` routes targeting `community-oss`

Also add OSS ArchUnit tests in the new service module so the service enforces its own controller/application/domain/infrastructure boundary.

- [ ] **Step 2: Run the deployment and architecture checks and verify they fail**

Run:

```bash
cd deploy
./tests/topology_single_cluster.sh
./tests/observability_otel_default.sh

cd ../backend
mvn -q -pl :community-app,:community-oss -Dtest='*ArchTest' test
```

Expected: FAIL until the Garage service, OSS module, and guardrails are wired.

- [ ] **Step 3: Implement the deployment wiring and guardrails**

Add the Garage compose files, OSS runtime service definitions, environment variables, MySQL bootstrap changes, and both OSS and `community-app` arch guardrails.

Keep the deployment model self-hosted and S3-compatible so a future Ceph RGW swap only changes the `ObjectStore` adapter and config.

- [ ] **Step 4: Update the handbook documentation**

Update the handbook docs so they describe:

- `community-oss` as the storage owner service
- the Garage-first deployment model
- the current user-file migration path
- the generic OSS contract for future consumer domains

- [ ] **Step 5: Re-run the deployment, architecture, and documentation checks and verify they pass**

Run:

```bash
cd deploy
./tests/topology_single_cluster.sh
./tests/observability_otel_default.sh

cd ../backend
mvn -q -pl :community-app,:community-oss -Dtest='*ArchTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit the deployment and docs work**

```bash
git add deploy backend/community-app/src/test/java/com/nowcoder/community/app/arch backend/community-oss/src/test/java/com/nowcoder/community/oss/arch docs/handbook
git commit -m "feat: wire oss deployment and guardrails"
```

## Verification Gate

This implementation is complete only when all of the following are true:

- `backend/community-oss` builds and its tests pass.
- `backend/community-oss-client` builds and its tests pass.
- `community-app` user avatar/file tests pass against the OSS client path.
- `/api/oss/**` and `/files/**` route to `community-oss`.
- Garage appears in the rendered deployment topology for single-node and cluster modes.
- ArchUnit tests cover the new OSS module and the updated `community-app` client boundary.
- The handbook docs explain the new ownership and deployment model.
