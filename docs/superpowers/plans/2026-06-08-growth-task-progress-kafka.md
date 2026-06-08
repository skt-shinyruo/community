# Growth Task Progress Kafka Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route post, comment, and like growth task triggers through Kafka while keeping `TaskProgressApplicationService` as the single growth use-case boundary.

**Architecture:** Producer-domain contract events are captured by growth outbox enqueuers, persisted in the existing JDBC outbox, published to Kafka by growth infrastructure handlers, then consumed by a growth Kafka listener that adapts payloads into application commands. Task progress calculation, idempotency, row locking, and reward issuing remain inside `TaskProgressApplicationService`.

**Tech Stack:** Spring Boot, Spring Kafka, existing `JdbcOutboxEventStore`, existing `TraceKafkaSender`, Mockito/JUnit tests, ArchUnit guardrails.

---

### Task 1: Kafka Outbox Publishing

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressKafkaOutboxHandler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/PostTaskProgressKafkaOutboxEnqueuer.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/PostTaskProgressKafkaOutboxHandler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxEnqueuer.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxHandler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/*TaskProgress*Test.java`

- [x] **Step 1: Write failing tests**

Assert each outbox handler parses its outbox JSON payload and publishes the corresponding owner-domain payload to the configured Kafka topic using the outbox key.

- [x] **Step 2: Run focused tests to verify RED**

Run: `mvn -q -pl :community-app -am -Dtest='*TaskProgress*Outbox*Test,TaskProgressKafkaListenerTest' test`

Expected: FAIL because the post/like Kafka classes and listener do not exist.

- [x] **Step 3: Implement minimal Kafka outbox handlers**

Use `KafkaTemplate<String, Object>` and `TraceKafkaSender.send(...).join()`. On Kafka send failure, throw `IllegalStateException` so the outbox worker retries.

- [x] **Step 4: Run focused tests to verify GREEN**

Run: `mvn -q -pl :community-app -am -Dtest='*TaskProgress*Outbox*Test,TaskProgressKafkaListenerTest' test`

Expected: PASS.

### Task 2: Growth Kafka Listener

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListener.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListenerTest.java`
- Modify: `backend/community-app/src/main/resources/application.yml`

- [x] **Step 1: Write failing listener tests**

Assert `PostPayload`, `CommentPayload`, and `LikePayload` are adapted into growth application commands and passed to `TaskProgressApplicationService`.

- [x] **Step 2: Run listener test to verify RED**

Run: `mvn -q -pl :community-app -am -Dtest='TaskProgressKafkaListenerTest' test`

Expected: FAIL because `TaskProgressKafkaListener` does not exist.

- [x] **Step 3: Implement minimal listener**

Add three `@KafkaListener` methods under `growth.infrastructure.event`. Each method validates payload fields, derives deterministic source event ids for likes, and calls only `TaskProgressApplicationService`.

- [x] **Step 4: Run listener test to verify GREEN**

Run: `mvn -q -pl :community-app -am -Dtest='TaskProgressKafkaListenerTest' test`

Expected: PASS.

### Task 3: Remove Direct Growth API Calls From Producers

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Modify tests under `backend/community-app/src/test/java/com/nowcoder/community/content/application` and `backend/community-app/src/test/java/com/nowcoder/community/social/application`

- [x] **Step 1: Update failing tests**

Post publishing and like creation no longer require or call `GrowthTaskProgressActionApi`; growth progress is driven from owner-domain events.

- [x] **Step 2: Run affected producer tests**

Run: `mvn -q -pl :community-app -am -Dtest='PostPublishingApplicationServiceTest,LikeApplicationServiceTest,CommentApplicationServiceTest' test`

Expected: PASS.

- [x] **Step 3: Remove direct synchronous task progress calls**

Remove `GrowthTaskProgressActionApi` from producer application services and tests. Keep existing owner-domain event publication intact.

- [x] **Step 4: Run affected tests to verify GREEN**

Run: `mvn -q -pl :community-app -am -Dtest='PostPublishingApplicationServiceTest,LikeApplicationServiceTest,CommentApplicationServiceTest' test`

Expected: PASS.

### Task 4: Guardrails

**Files:**
- Modify: `deploy/nacos/config/community-kafka-policy.yaml`

- [x] **Step 1: Add runtime defaults**

Add growth task Kafka topic names and consumer group defaults to application and Nacos Kafka policy configuration.

- [x] **Step 2: Run focused and architecture tests**

Run:
`mvn -q -pl :community-app -am -Dtest='*TaskProgress*Test,PostPublishingApplicationServiceTest,LikeApplicationServiceTest,CommentApplicationServiceTest,GrowthTaskProgressActionApiAdapterTest' test`

Run:
`mvn -q -pl :community-app -am -Dtest='*ArchTest' test`

Expected: PASS for both commands.
