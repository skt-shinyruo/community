# Platform Common Split Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove `backend/platform/common` and replace it with capability-based `infra-*` modules, keeping runtime behavior stable.

**Architecture:** Migrate by capability (trace/tx/scheduler/idempotency/kafka/internal-client/web/startup-validation), updating all callers per capability, then delete `platform/common`. Keep each commit small and buildable.

**Tech Stack:** Java 17, Spring Boot 3.2.x, Maven multi-module, Spring Web/WebFlux/Security, Micrometer, Redis, JDBC, Kafka.

---

## Task 0: Baseline Verification (worktree)

**Files:** none

**Step 1: Confirm branch + clean state**

Run: `git status -sb`  
Expected: on `refactor-platform-common-split` with no local changes.

**Step 2: Run backend baseline tests**

Preferred:
- Run: `cd backend && ./mvnw -q test`
- Expected: exit code 0

If Maven wrapper fails to download the distribution (environment issue), fall back to system Maven:
- Run: `cd backend && mvn -q test`
- Expected: exit code 0

**Step 3: Snapshot common inventory**

Run:
- `find backend/platform/common/src/main/java -type f | sort`
- `find backend/platform/common/src/test/java -type f | sort`

Expected: a stable list of ~34 main classes and ~7 tests to migrate.

Commit: none.

---

## Task 1: Add New `infra-*` Modules (empty skeletons)

**Files:**
- Modify: `backend/platform/pom.xml`
- Create: `backend/platform/infra-trace/pom.xml`
- Create: `backend/platform/infra-tx/pom.xml`
- Create: `backend/platform/infra-scheduler-starter/pom.xml`
- Create: `backend/platform/infra-idempotency-starter/pom.xml`
- Create: `backend/platform/infra-kafka-starter/pom.xml`
- Create: `backend/platform/infra-internal-client/pom.xml`
- Create: `backend/platform/infra-web-starter/pom.xml`
- Create: `backend/platform/infra-startup-validation-starter/pom.xml`

**Step 1: Register the new modules in the platform reactor**

Edit `backend/platform/pom.xml` and add modules (order is not critical but keep it readable):

- `infra-trace`
- `infra-tx`
- `infra-scheduler-starter`
- `infra-idempotency-starter`
- `infra-kafka-starter`
- `infra-internal-client`
- `infra-web-starter`
- `infra-startup-validation-starter`

Keep existing modules intact (`contracts-*`, `infra-security-starter`, `infra-outbox`, `common` for now).

**Step 2: Create the eight module POMs**

Each POM should:
- inherit from `backend/platform/pom.xml`
- use `packaging=jar`
- declare only the minimal dependencies needed for compilation (more will be added when code is moved)

Example skeleton:

```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nowcoder.community</groupId>
    <artifactId>platform</artifactId>
    <version>0.0.1-SNAPSHOT</version>
  </parent>
  <artifactId>infra-trace</artifactId>
  <name>infra-trace</name>
  <packaging>jar</packaging>
</project>
```

**Step 3: Compile platform reactor**

Run: `cd backend && mvn -q -pl :platform -am test`  
Expected: exit code 0.

**Step 4: Commit**

```bash
git add backend/platform/pom.xml backend/platform/infra-*/pom.xml
git commit -m "chore(platform): add infra module skeletons"
```

---

## Task 2: Migrate Trace Core → `infra-trace`

**Files:**
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/trace/TraceId.java` -> `backend/platform/infra-trace/src/main/java/com/nowcoder/community/infra/trace/TraceId.java`
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/trace/TraceContext.java` -> `backend/platform/infra-trace/src/main/java/com/nowcoder/community/infra/trace/TraceContext.java`
- Modify (imports): any files that referenced `com.nowcoder.community.platform.trace.*`
- Modify: `backend/platform/infra-trace/pom.xml`

**Step 1: Move files and update package declarations**

Use `git mv` then update package lines:

- from `package com.nowcoder.community.platform.trace;`
- to `package com.nowcoder.community.infra.trace;`

**Step 2: Update callers**

Run: `rg -n "com\\.nowcoder\\.community\\.platform\\.trace\\." backend`  
Then update imports to `com.nowcoder.community.infra.trace.*`.

Known early callers:
- `backend/social-service/.../KafkaSocialEventPublisher.java` (uses `TraceId`)
- `backend/content-service/.../KafkaContentEventPublisher.java` (uses `TraceId`)
- `backend/platform/common/.../KafkaTraceSupport.java` (temporary; will be migrated in Task 6)
- `backend/platform/common/.../TraceIdFilter.java` (temporary; will be migrated in Task 8)

**Step 3: Add minimal dependencies**

`infra-trace` depends on:
- `contracts-core` (for `TraceIdCodec`)
- `slf4j-api` (if needed) — but `TraceContext` uses `org.slf4j.MDC`, so either:
  - add `org.slf4j:slf4j-api` as normal dependency, or
  - rely on existing transitive deps (prefer explicit in this module)

**Step 4: Compile**

Run: `cd backend && mvn -q -pl :infra-trace -am test`  
Expected: success.

**Step 5: Commit**

```bash
git add backend/platform/infra-trace backend/social-service backend/content-service backend/platform/common
git commit -m "refactor(platform): extract trace core to infra-trace"
```

---

## Task 3: Migrate Tx Helper → `infra-tx`

**Files:**
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/tx/AfterCommitExecutor.java` -> `backend/platform/infra-tx/src/main/java/com/nowcoder/community/infra/tx/AfterCommitExecutor.java`
- Move: `backend/platform/common/src/test/java/com/nowcoder/community/platform/tx/AfterCommitExecutorTest.java` -> `backend/platform/infra-tx/src/test/java/com/nowcoder/community/infra/tx/AfterCommitExecutorTest.java`
- Modify: `backend/platform/infra-tx/pom.xml`
- Modify: all import sites of `com.nowcoder.community.platform.tx.AfterCommitExecutor`

**Step 1: `git mv` the class + test and update package**

Change package from `...platform.tx` to `...infra.tx`.

**Step 2: Update callers**

Run: `rg -n "com\\.nowcoder\\.community\\.platform\\.tx\\.AfterCommitExecutor" backend` and update imports.

Known callers:
- `backend/content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`
- `backend/content-service/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- `backend/content-service/src/main/java/com/nowcoder/community/content/event/*EventPublisher.java`
- `backend/social-service/src/main/java/com/nowcoder/community/social/event/KafkaSocialEventPublisher.java`

**Step 3: Add minimal dependency**

If `AfterCommitExecutor` uses Spring transaction synchronization types, add `org.springframework:spring-tx` to `infra-tx`.

**Step 4: Run focused tests**

Run: `cd backend && mvn -q -pl :infra-tx -am test`  
Expected: success.

**Step 5: Commit**

```bash
git add backend/platform/infra-tx backend/content-service backend/social-service backend/platform/common
git commit -m "refactor(platform): extract tx helpers to infra-tx"
```

---

## Task 4: Migrate Scheduler Single-flight → `infra-scheduler-starter`

**Files:**
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/scheduler/SingleFlightTaskGuard.java` -> `backend/platform/infra-scheduler-starter/src/main/java/com/nowcoder/community/infra/scheduler/SingleFlightTaskGuard.java`
- Create: `backend/platform/infra-scheduler-starter/src/main/java/com/nowcoder/community/infra/scheduler/autoconfig/SchedulerInfraAutoConfiguration.java`
- Create: `backend/platform/infra-scheduler-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `backend/platform/infra-scheduler-starter/pom.xml`
- Modify: import sites in callers (e.g. search job)

**Step 1: Move guard class and update package**

`com.nowcoder.community.platform.scheduler` → `com.nowcoder.community.infra.scheduler`

**Step 2: Add auto-config (Redis-present default bean)**

Create `SchedulerInfraAutoConfiguration`:

```java
package com.nowcoder.community.infra.scheduler.autoconfig;

import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class SchedulerInfraAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(StringRedisTemplate.class)
  public SingleFlightTaskGuard singleFlightTaskGuard(StringRedisTemplate redisTemplate) {
    return new SingleFlightTaskGuard(redisTemplate);
  }
}
```

Add `AutoConfiguration.imports` with the fully-qualified class name.

**Step 3: Update callers**

Run: `rg -n "com\\.nowcoder\\.community\\.platform\\.scheduler\\.SingleFlightTaskGuard" backend` and update imports.

Known caller:
- `backend/search-service/src/main/java/com/nowcoder/community/search/kafka/SearchConsumedEventCleanupJob.java`

**Step 4: Build**

Run: `cd backend && mvn -q -pl :infra-scheduler-starter -am test`

**Step 5: Commit**

```bash
git add backend/platform/infra-scheduler-starter backend/search-service backend/platform/common
git commit -m "refactor(platform): extract scheduler single-flight starter"
```

---

## Task 5: Migrate HTTP Idempotency → `infra-idempotency-starter`

**Files:**
- Move (all): `backend/platform/common/src/main/java/com/nowcoder/community/platform/idempotency/*` -> `backend/platform/infra-idempotency-starter/src/main/java/com/nowcoder/community/infra/idempotency/`
- Move test: `backend/platform/common/src/test/java/com/nowcoder/community/platform/idempotency/IdempotencyGuardTtlTest.java` -> `backend/platform/infra-idempotency-starter/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardTtlTest.java`
- Create: `backend/platform/infra-idempotency-starter/src/main/java/com/nowcoder/community/infra/idempotency/autoconfig/IdempotencyAutoConfiguration.java`
- Create: `backend/platform/infra-idempotency-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `backend/platform/infra-idempotency-starter/pom.xml`
- Modify: all import sites of `IdempotencyGuard`

**Step 1: Move the package and update package declarations**

From `com.nowcoder.community.platform.idempotency` → `com.nowcoder.community.infra.idempotency`

**Step 2: Implement auto-config (migrate logic out of `CommonAutoConfiguration`)**

Create `IdempotencyAutoConfiguration` with:
- `@EnableConfigurationProperties(IdempotencyProperties.class)`
- `IdempotencyStore` bean conditional on `http.idempotency.enabled=true`
  - DB store requires `JdbcTemplate`
  - Redis store requires `StringRedisTemplate`
- `IdempotencyGuard` bean conditional on `http.idempotency.enabled=true`

(Copy logic from `backend/platform/common/.../CommonAutoConfiguration.java` and adjust packages.)

**Step 3: Update callers**

Run: `rg -n "com\\.nowcoder\\.community\\.platform\\.idempotency\\." backend`

Known callers:
- `backend/content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`
- `backend/message-service/src/main/java/com/nowcoder/community/message/api/MessageController.java`

**Step 4: Build**

Run: `cd backend && mvn -q -pl :infra-idempotency-starter -am test`

**Step 5: Commit**

```bash
git add backend/platform/infra-idempotency-starter backend/content-service backend/message-service backend/platform/common
git commit -m "refactor(platform): extract http idempotency starter"
```

---

## Task 6: Migrate Kafka Utilities + DLQ → `infra-kafka-starter`

**Files:**
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/kafka/KafkaTraceSupport.java` -> `backend/platform/infra-kafka-starter/src/main/java/com/nowcoder/community/infra/kafka/KafkaTraceSupport.java`
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/kafka/dlq/KafkaDlqPublisher.java` -> `backend/platform/infra-kafka-starter/src/main/java/com/nowcoder/community/infra/kafka/dlq/KafkaDlqPublisher.java`
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/kafka/dlq/KafkaDlqRecord.java` -> `backend/platform/infra-kafka-starter/src/main/java/com/nowcoder/community/infra/kafka/dlq/KafkaDlqRecord.java`
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/autoconfig/KafkaDlqPublisherAutoConfiguration.java` -> `backend/platform/infra-kafka-starter/src/main/java/com/nowcoder/community/infra/kafka/autoconfig/KafkaInfraAutoConfiguration.java`
- Create: `backend/platform/infra-kafka-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `backend/platform/infra-kafka-starter/pom.xml`
- Modify: all import sites of `KafkaTraceSupport` / `KafkaDlqPublisher`

**Step 1: Move classes and update packages**

Suggested package mapping:
- `...platform.kafka` → `...infra.kafka`
- `...platform.kafka.dlq` → `...infra.kafka.dlq`

**Step 2: Rename and keep auto-config behavior**

Create `KafkaInfraAutoConfiguration` equivalent to old `KafkaDlqPublisherAutoConfiguration`:

```java
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaInfraAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public KafkaDlqPublisher kafkaDlqPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
    return new KafkaDlqPublisher(kafkaTemplate, objectMapper);
  }
}
```

**Step 3: Update callers**

Run: `rg -n "com\\.nowcoder\\.community\\.platform\\.kafka\\." backend`

Known callers:
- `backend/search-service/src/main/java/com/nowcoder/community/search/kafka/PostEventConsumer.java`
- `backend/content-service/src/main/java/com/nowcoder/community/content/kafka/SocialEventConsumer.java`
- `backend/*/kafka/KafkaErrorHandlerConfig.java` (content/search/message) for `KafkaDlqPublisher`

**Step 4: Build**

Run: `cd backend && mvn -q -pl :infra-kafka-starter -am test`

**Step 5: Commit**

```bash
git add backend/platform/infra-kafka-starter backend/search-service backend/content-service backend/message-service backend/platform/common
git commit -m "refactor(platform): extract kafka infra starter"
```

---

## Task 7: Migrate Internal Client Support → `infra-internal-client`

**Files:**
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/web/internalclient/InternalClientSupport.java` -> `backend/platform/infra-internal-client/src/main/java/com/nowcoder/community/infra/internalclient/InternalClientSupport.java`
- Modify: `backend/platform/infra-internal-client/pom.xml`
- Modify: all import sites of `InternalClientSupport`

**Step 1: Move the class and update package**

From `...platform.web.internalclient` → `...infra.internalclient`

**Step 2: Update callers**

Run: `rg -n "com\\.nowcoder\\.community\\.platform\\.web\\.internalclient\\.InternalClientSupport" backend`

Known callers:
- `backend/auth-service/.../UserServiceInternalClient.java`
- `backend/content-service/.../*Client.java`
- `backend/user-service/.../SocialServiceClient.java`
- `backend/search-service/.../ContentServiceClient.java`
- `backend/social-service/.../ContentEntityResolver.java`
- `backend/message-service/.../UserModerationClient.java`

**Step 3: Build**

Run: `cd backend && mvn -q -pl :infra-internal-client -am test`

**Step 4: Commit**

```bash
git add backend/platform/infra-internal-client backend/auth-service backend/content-service backend/user-service backend/search-service backend/social-service backend/message-service backend/platform/common
git commit -m "refactor(platform): extract internal client support"
```

---

## Task 8: Migrate Web Cross-cutting → `infra-web-starter`

**Files:**
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/web/*` -> `backend/platform/infra-web-starter/src/main/java/com/nowcoder/community/infra/web/`
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/web/reactive/*` -> `backend/platform/infra-web-starter/src/main/java/com/nowcoder/community/infra/web/reactive/`
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/net/*` -> `backend/platform/infra-web-starter/src/main/java/com/nowcoder/community/infra/web/net/`
- Move tests: `backend/platform/common/src/test/java/com/nowcoder/community/platform/web/GlobalExceptionHandlerTest.java` -> `backend/platform/infra-web-starter/src/test/java/com/nowcoder/community/infra/web/GlobalExceptionHandlerTest.java`
- Move test: `backend/platform/common/src/test/java/com/nowcoder/community/platform/api/ResultTest.java` -> `backend/platform/infra-web-starter/src/test/java/com/nowcoder/community/infra/web/ResultTest.java`
- Create: `backend/platform/infra-web-starter/src/main/java/com/nowcoder/community/infra/web/autoconfig/WebInfraAutoConfiguration.java`
- Create: `backend/platform/infra-web-starter/src/main/java/com/nowcoder/community/infra/web/autoconfig/ServletWebInfraAutoConfiguration.java`
- Create: `backend/platform/infra-web-starter/src/main/java/com/nowcoder/community/infra/web/autoconfig/ReactiveWebInfraAutoConfiguration.java`
- Create: `backend/platform/infra-web-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `backend/platform/infra-web-starter/pom.xml`
- Modify: any imports from `com.nowcoder.community.platform.web.*` / `...platform.net.*`

**Step 1: Move classes and update packages**

Suggested mappings:
- `...platform.web` → `...infra.web`
- `...platform.web.reactive` → `...infra.web.reactive`
- `...platform.net` → `...infra.web.net`

**Step 2: Replace old `ServletOnlyAutoConfiguration` / `ReactiveOnlyAutoConfiguration`**

Create:
- `WebInfraAutoConfiguration` for shared config (e.g., `@Import(CommonJacksonConfig.class)` if needed)
- `ServletWebInfraAutoConfiguration` (servlet-only beans)
- `ReactiveWebInfraAutoConfiguration` (reactive-only beans)

Copy the bean definitions from:
- `backend/platform/common/src/main/java/com/nowcoder/community/platform/autoconfig/ServletOnlyAutoConfiguration.java`
- `backend/platform/common/src/main/java/com/nowcoder/community/platform/autoconfig/ReactiveOnlyAutoConfiguration.java`

Keep all beans `@ConditionalOnMissingBean`.

**Step 3: Update external users**

Known import user:
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/bootstrap/security/CommunitySecurityConfig.java` imports `SecurityExceptionHandler`

Update to the new package and ensure `community-bootstrap` depends on `infra-web-starter`.

**Step 4: Build**

Run: `cd backend && mvn -q -pl :infra-web-starter -am test`

**Step 5: Commit**

```bash
git add backend/platform/infra-web-starter backend/community-bootstrap backend/platform/common
git commit -m "refactor(platform): extract web cross-cutting starter"
```

---

## Task 9: Startup Validation → `infra-startup-validation-starter` + SPI

**Files:**
- Move: `backend/platform/common/src/main/java/com/nowcoder/community/platform/startup/StartupValidation.java` -> `backend/platform/infra-startup-validation-starter/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`
- Replace auto-config:
  - Move and rewrite: `backend/platform/common/src/main/java/com/nowcoder/community/platform/startup/StartupValidationAutoConfig.java`
  - Create: `backend/platform/infra-startup-validation-starter/src/main/java/com/nowcoder/community/infra/startup/autoconfig/StartupValidationAutoConfiguration.java`
- Create SPI: `backend/platform/infra-startup-validation-starter/src/main/java/com/nowcoder/community/infra/startup/StartupValidator.java`
- Create: `backend/platform/infra-startup-validation-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Add auth validator:
  - Create: `backend/auth-service/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`
- Modify: `backend/platform/infra-startup-validation-starter/pom.xml`

**Step 1: Move startup validation code and update package**

From `...platform.startup` → `...infra.startup`

**Step 2: Introduce SPI**

Create `StartupValidator`:

```java
package com.nowcoder.community.infra.startup;

import org.springframework.core.env.Environment;
import java.util.List;

public interface StartupValidator {
  void validate(Environment environment, List<String> errors);
}
```

Update `StartupValidation` to:
- run the current generic validations
- then iterate all `StartupValidator` beans and let them append errors
- keep the same fail-closed error format

**Step 3: Implement auth-specific validator**

Create `AuthStartupValidator` as a Spring component (or `@Bean` in a config) that contains the current auth-specific checks that were previously hard-coded in the `switch(appName)` block.

**Step 4: Build**

Run: `cd backend && mvn -q -pl :infra-startup-validation-starter -am test`

**Step 5: Commit**

```bash
git add backend/platform/infra-startup-validation-starter backend/auth-service backend/platform/common
git commit -m "refactor(platform): extract startup validation starter with SPI"
```

---

## Task 10: Domain Leakage Cleanup (message/content/contracts)

**Files:**
- Move to message:
  - `backend/platform/common/src/main/java/com/nowcoder/community/platform/security/ConversationIdParser.java` -> `backend/message-service/src/main/java/com/nowcoder/community/message/security/ConversationIdParser.java`
  - `backend/platform/common/src/main/java/com/nowcoder/community/platform/security/OwnerGuard.java` -> `backend/message-service/src/main/java/com/nowcoder/community/message/security/OwnerGuard.java`
- Update imports in:
  - `backend/message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
  - message tests importing `OwnerGuard`
- Move to content:
  - `backend/platform/common/src/main/java/com/nowcoder/community/platform/text/HtmlEntityCodec.java` -> `backend/content-service/src/main/java/com/nowcoder/community/content/text/HtmlEntityCodec.java`
  - `backend/platform/common/src/test/java/com/nowcoder/community/platform/text/HtmlEntityCodecTest.java` -> `backend/content-service/src/test/java/com/nowcoder/community/content/text/HtmlEntityCodecTest.java`
  - update `backend/content-service/src/main/java/com/nowcoder/community/content/text/ContentTextCodec.java`
- Move to contracts:
  - `backend/platform/common/src/main/java/com/nowcoder/community/platform/validation/ValidationLimits.java` -> `backend/platform/contracts-core/src/main/java/com/nowcoder/community/contracts/validation/ValidationLimits.java`
  - update all DTO imports using it (auth/content/message)

**Step 1: Move message security helpers**

Use `git mv`, update package names to `com.nowcoder.community.message.security`, and ensure `OwnerGuard` is provided as a bean (simplest: annotate it with `@Component`).

**Step 2: Move content HTML codec**

Use `git mv`, keep behavior unchanged.

**Step 3: Move validation constants into contracts**

Update package to `com.nowcoder.community.contracts.validation`.

**Step 4: Build**

Run: `cd backend && mvn -q test`

**Step 5: Commit**

```bash
git add backend/message-service backend/content-service backend/platform/contracts-core backend/platform/common
git commit -m "refactor: move domain-specific helpers out of platform"
```

---

## Task 11: Replace `common` Dependencies in All Modules

**Files:**
- Modify: `backend/*/pom.xml` (all modules that depend on `common`)
- Modify: `backend/community-bootstrap/pom.xml`

**Step 1: Find all `common` dependencies**

Run: `rg -n \"<artifactId>common</artifactId>\" backend/*/pom.xml`

**Step 2: Replace with explicit infra dependencies**

Per module, add only what it actually uses (guided by `rg -n "com\\.nowcoder\\.community\\.(platform|infra)\\."`).

As a starting point:
- most domains need `contracts-core`
- event consumers/publishers need `contracts-event-core`
- add infra modules as required:
  - `infra-web-starter` (only if module provides web runtime pieces)
  - `infra-internal-client` (if using `InternalClientSupport`)
  - `infra-kafka-starter` (if using `KafkaTraceSupport` or `KafkaDlqPublisher`)
  - `infra-idempotency-starter` (if using `IdempotencyGuard`)
  - `infra-scheduler-starter` (if using `SingleFlightTaskGuard`)
  - `infra-tx` (if using `AfterCommitExecutor`)
  - `infra-trace` (if using `TraceId/TraceContext`)
  - `infra-startup-validation-starter` (only for apps that should fail-closed in prod)

**Step 3: Build**

Run: `cd backend && mvn -q test`

**Step 4: Commit**

```bash
git add backend/*/pom.xml
git commit -m "build: replace platform common dependency with infra modules"
```

---

## Task 12: Delete `backend/platform/common`

**Files:**
- Modify: `backend/platform/pom.xml` (remove `<module>common</module>`)
- Delete: `backend/platform/common/` (entire module directory)

**Step 1: Remove the module from platform reactor**

Edit `backend/platform/pom.xml` to remove `common` from `<modules>`.

**Step 2: Delete the directory**

Delete `backend/platform/common` and ensure no files remain.

**Step 3: Sanity scan for leftover imports**

Run:
- `rg -n "com\\.nowcoder\\.community\\.platform\\." backend || true`
- `rg -n "<artifactId>common</artifactId>" backend || true`

Expected: zero results.

**Step 4: Full test**

Run: `cd backend && mvn -q test`

**Step 5: Commit**

```bash
git add backend/platform/pom.xml
git rm -r backend/platform/common
git commit -m "refactor(platform): remove deprecated common module"
```

---

## Task 13: Documentation Alignment (optional but recommended)

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `backend/README.md`

**Step 1: Update platform section**

Document that `platform/common` is removed and replaced by capability-based `infra-*` modules.

**Step 2: Commit**

```bash
git add docs/ARCHITECTURE.md backend/README.md
git commit -m "docs: document platform infra split"
```

---

## Task 14: Final Verification

**Files:** none

**Step 1: Full backend tests**

Run: `cd backend && mvn test`
Expected: exit code 0.

**Step 2: Ensure only one deployable remains**

Run: `rg -n \"@SpringBootApplication\" backend | head`  
Expected: only `community-bootstrap` (and any explicitly retained legacy modules, if any).

