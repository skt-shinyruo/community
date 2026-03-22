# IM Layered Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the IM modules so that `im-core` and `im-realtime` follow clearer Spring Boot layering while the shared Kafka boundary is reduced to a minimal `im-common` module.

**Architecture:** Treat the IM refactor as a distributed-boundary cleanup, not as a monolith-only rename. First rename and stabilize the shared module and all dependent runtime/test/config references, then normalize `im-core` package naming, and finally align `im-realtime` with the new shared boundary while preserving Kafka and WebSocket behavior.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, Spring WebFlux, Spring Kafka, JdbcTemplate, Maven, JUnit 5

---

### Task 1: Rename `im-contracts` To `im-common` Across Build, Packages, And Runtime Config

**Files:**
- Modify: `backend/community-im/pom.xml`
- Modify: `backend/community-im/im-contracts/pom.xml`
- Modify: `backend/community-im/im-core/pom.xml`
- Modify: `backend/community-im/im-realtime/pom.xml`
- Modify: `backend/community-im/im-core/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/BackendFlatteningArchTest.java`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`

- [ ] **Step 1: Rename the Maven module and artifact references**

  Implement:
  - `im-contracts` module directory -> `im-common`
  - parent module list updated
  - child POM dependencies updated from `im-contracts` to `im-common`

- [ ] **Step 2: Update Java package ownership for the shared DTOs**

  Move:
  - `com.nowcoder.community.im.contracts.*` -> `com.nowcoder.community.im.common.*`

- [ ] **Step 3: Update runtime configuration that still trusts the old package**

  Update:
  - Kafka `spring.json.trusted.packages`
  - any package-name-based JSON config or test harness assumptions

- [ ] **Step 4: Update architecture tests and docs in the same batch**

  Verify:
  - `BackendFlatteningArchTest` expects `im-common`
  - docs no longer describe `im-contracts` as the center of the IM architecture

- [ ] **Step 5: Run the shared-module and architecture checks**

  Run:
  - `mvn -pl backend/community-im/im-common test`
  - `mvn -pl backend/community-app -Dtest=BackendFlatteningArchTest test`

- [ ] **Step 6: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Rewire IM Core And Realtime To The New Shared Module

**Files:**
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImTopics.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/command/SendPrivateTextCommandV1.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/command/SendRoomTextCommandV1.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/PrivateMessagePersistedEventV1.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMessagePersistedEventV1.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChangedEventV1.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/CommandConsumers.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/EventProducer.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaRoomMemberChangePublisher.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/service/PrivateMessageService.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/service/RoomMessageService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/CommandProducer.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/PrivatePushService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Test: `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/kafka/ImCoreKafkaIntegrationTest.java`
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java`

Note:
- the `im-common` paths listed in this task are the post-Task-1 locations created by the shared-module rename and package move

- [ ] **Step 1: Move the shared DTO tests with the shared package rename**

  Implement:
  - rename `JsonContractsTest` package imports
  - keep round-trip serialization assertions intact

- [ ] **Step 2: Update every IM runtime import to the new shared package**

  Cover:
  - Kafka consumers and producers
  - service command/event handling
  - WebSocket send-command creation
  - integration tests

- [ ] **Step 3: Re-run focused cross-module messaging tests**

  Run:
  - `mvn -pl backend/community-im/im-common -Dtest=JsonContractsTest test`
  - `mvn -pl backend/community-im/im-core -Dtest=ImCoreKafkaIntegrationTest test`
  - `mvn -pl backend/community-im/im-realtime -Dtest=ImRealtimeWebSocketIntegrationTest test`

- [ ] **Step 4: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Normalize `im-core` To Conventional Layer Naming

**Files:**
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/api/ConversationController.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/api/RoomController.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/api/UnreadController.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/api/InternalRealtimeBootstrapController.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/api/Result.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/api/ErrorCode.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/api/CommonErrorCode.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/ConversationRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/ConversationReadStateRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/PrivateMessageRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/RoomRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/RoomMemberRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/RoomMessageRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/RoomReadStateRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/db/SeqAllocator.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/api/ImCoreApiControllerTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/service/PrivateMessageServiceTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/service/RoomMessageServiceTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/api/ImCoreApiControllerTest.java`

- [ ] **Step 1: Rename `api` packages by responsibility**

  Implement:
  - controllers -> `controller`
  - IM-core local `Result/ErrorCode/CommonErrorCode` -> `web` or `exception` package based on actual role

- [ ] **Step 2: Rename `db` packages to `repository`**

  Implement:
  - move persistence classes from `db` to `repository`
  - update service imports and any package-private assumptions

- [ ] **Step 3: Keep controller-service-repository boundaries explicit**

  Verify:
  - controllers only orchestrate HTTP concerns
  - services keep business logic
  - Kafka components still depend on services, not controllers

- [ ] **Step 4: Re-run focused IM core tests**

  Run:
  - `mvn -pl backend/community-im/im-core -Dtest=PrivateMessageServiceTest,RoomMessageServiceTest,ImCoreApiControllerTest test`

- [ ] **Step 5: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Align `im-realtime` To The New Shared Boundary And Stable Runtime Shape

**Files:**
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/WebSocketConfig.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/CommandProducer.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/ImCoreClient.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClient.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/JwtVerifier.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java`

- [ ] **Step 1: Update realtime code to the renamed shared DTO package**

  Cover:
  - command send path
  - event consume path
  - WebSocket integration tests

- [ ] **Step 2: Verify the package layout still matches the intended runtime responsibilities**

  Rule:
  - keep `ws`, `client`, `security`, `kafka`, `push`, and `presence` where they already align with responsibility
  - only rename or split files that clearly violate the target layering

- [ ] **Step 3: Re-run focused realtime tests**

  Run:
  - `mvn -pl backend/community-im/im-realtime -Dtest=ImRealtimeWebSocketIntegrationTest test`

- [ ] **Step 4: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 5: Final IM Verification And Documentation Cleanup

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Verify only: `backend/community-im/im-common`
- Verify only: `backend/community-im/im-core`
- Verify only: `backend/community-im/im-realtime`

- [ ] **Step 1: Run focused module tests for all touched IM areas**

  Run:
  - `mvn -pl backend/community-im/im-common test`
  - `mvn -pl backend/community-im/im-core -Dtest=PrivateMessageServiceTest,RoomMessageServiceTest,ImCoreKafkaIntegrationTest,ImCoreApiControllerTest test`
  - `mvn -pl backend/community-im/im-realtime -Dtest=ImRealtimeWebSocketIntegrationTest test`

- [ ] **Step 2: Run broad module-level verification**

  Run:
  - `mvn -pl backend/community-im/im-core test`
  - `mvn -pl backend/community-im/im-realtime test`

- [ ] **Step 3: Re-check the IM end state against the approved spec**

  Verify:
  - shared module is now `im-common`
  - runtime configs no longer trust the old package name
  - `im-core` no longer uses `api/db` naming as the primary architecture
  - `im-realtime` still preserves Kafka and WebSocket behavior

- [ ] **Step 4: Update final doc language to reflect the reduced shared boundary**

  Rewrite:
  - IM shared module naming
  - ownership of Kafka DTOs
  - runtime role split between `im-core` and `im-realtime`

- [ ] **Step 5: Prepare the final review summary for the IM track**

  Include:
  - exact tests run
  - any runtime migration notes for environments still using the old module name or trusted package values
