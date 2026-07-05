# Community App Architecture Boundary Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore trustworthy `community-app` architecture checks and harden DDD tactical boundaries around Web dependencies, event dispatch ports, outbox routing, application-service collaboration, transaction proxy safety, and infrastructure API adapter naming.

**Architecture:** Keep the flattened Spring Boot monolith and the existing DDD tactical package shape. Move framework and broker details behind infrastructure adapters, keep inbound adapters entering same-domain `*ApplicationService` classes, and add ArchUnit rules only after the current violations are removed.

**Tech Stack:** Java 17, Spring Boot 3.2.6, Maven reactor, ArchUnit 1.2.1, JUnit 5, Mockito, MyBatis, Spring Kafka.

## Global Constraints

- Do not split `community-app` into separate Maven modules or microservices in this remediation batch.
- Do not change public HTTP response shapes.
- Do not rename published synchronous `api.*` interfaces unless a compatibility-preserving adapter remains.
- Do not change Kafka topic names or event contract field names in this batch.
- Inbound adapters must call same-domain `*ApplicationService` only.
- `application.command`, `application.result`, and application-owned ports must not expose HTTP transport types such as `ResponseEntity`, `ResponseCookie`, `Resource`, `MediaType`, Servlet request/response types, or Spring Web upload types such as `MultipartFile`.
- `application` must not depend directly on MyBatis mapper or dataobject types.
- `domain` must not depend on `controller`, `application`, `infrastructure`, MyBatis mapper/dataobject types, HTTP DTOs, Spring framework, or owner-domain `api.*`.
- Application-owned event ports must not expose Kafka topic names, `KafkaTemplate`, `ProducerRecord`, `ConsumerRecord`, partition, offset, or broker-specific send APIs.
- Preserve these runtime topic defaults: `content.events`, `social.events`, `user.events`, `growth.task.post-published`, `growth.task.comment-created`, `growth.task.like-created`, `growth.task.like-removed`, `im.event.user-messaging-policy-changed`, `im.event.user-block-relation-changed`.
- Project-related documentation must live under `docs/handbook`; specs and implementation plans must live under `docs/superpowers/specs` and `docs/superpowers/plans`.

---

## File Map

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentTextCodec.java`: convert from Spring-backed component class to application-owned interface.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/text/SpringHtmlContentTextCodec.java`: new infrastructure implementation using `HtmlUtils`.
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/text/SpringHtmlContentTextCodecTest.java`: focused codec behavior test.
- `backend/community-app/src/main/java/com/nowcoder/community/{content,social,user}/application/command/Dispatch*EventCommand.java`: new application commands for outbox dispatch inputs.
- `backend/community-app/src/main/java/com/nowcoder/community/{content,social,user}/application/*IntegrationEventDispatcher.java`: semantic application ports replacing `*KafkaDispatchPort`.
- `backend/community-app/src/main/java/com/nowcoder/community/{content,social,user}/infrastructure/event/*EventKafkaSenderAdapter.java`: Kafka topic lookup and send mechanics.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/DispatchTaskProgressEventCommand.java`: typed growth outbox dispatch command.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/TaskProgressDispatchKind.java`: supported growth task event kinds.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressIntegrationEventDispatcher.java`: semantic growth task dispatch port.
- `backend/community-app/src/main/java/com/nowcoder/community/im/application/command/DispatchImPolicyEventCommand.java`: typed IM policy outbox dispatch command.
- `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyIntegrationEventDispatcher.java`: semantic IM policy dispatch port.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`: remove public transactional self-invocation wrappers.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`: remove public transactional self-invocation overloads.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/{ContentEntityQueryService.java,ContentEntityQueryApiAdapter.java}`: rename class/file.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/{PostScanService.java,PostScanQueryApiAdapter.java}`: rename class/file.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`: strengthen application technology and port-shape guardrails.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`: add infrastructure API adapter naming guardrail.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/TransactionBoundaryArchTest.java`: apply transaction self-invocation rule to all production application services.
- `docs/handbook/architecture.md`: document reactor-safe architecture verification and allowed same-domain application process-manager collaboration.
- `docs/handbook/system-design.md`: update event dispatch wording only if it currently describes Kafka-shaped application ports.

---

### Task 1: Restore A Reliable Verification Baseline

**Files:**
- Modify: `docs/handbook/architecture.md`
- Read-only check: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java`
- Read-only check: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

**Interfaces:**
- Consumes: existing Maven reactor modules under `backend/pom.xml`.
- Produces: documented verification baseline that distinguishes stale local snapshots from real architecture failures.

- [ ] **Step 1: Confirm the stale local snapshot symptom**

Run from repo root:

```bash
cd backend
mvn test -pl :community-app -Dtest=DddLayeringArchTest
```

Expected: FAIL during `testCompile` with `AuthControllerUnitTest` missing `ValidationLimits.REGISTRATION_TOKEN_MAX`, `ValidationLimits.REGISTRATION_CODE_MAX`, and `ValidationLimits.TOKEN_MAX`.

- [ ] **Step 2: Confirm the reactor-correct baseline**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=DddLayeringArchTest
```

Expected: test compilation succeeds, then `DddLayeringArchTest.application_must_not_depend_on_web_transport_types` fails only because `ContentTextCodec` calls `HtmlUtils.htmlEscape` and `HtmlUtils.htmlUnescape`.

- [ ] **Step 3: Add the architecture verification command note**

In `docs/handbook/architecture.md`, add or update a short section named `Architecture Verification` with this exact command block:

````markdown
## Architecture Verification

Use the Maven reactor form when validating architecture rules from a fresh checkout or after changing shared modules:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest'
```

The narrower command below is still valid after local `0.0.1-SNAPSHOT` dependencies have been installed, but it can read stale artifacts from `~/.m2`:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```
````

- [ ] **Step 4: Refresh local snapshots for the narrow command**

Run:

```bash
cd backend
mvn install -pl :community-app -am -DskipTests
```

Expected: SUCCESS. This installs the current `community-common-core` artifact that already contains the validation constants used by app DTO tests.

- [ ] **Step 5: Re-run the narrow architecture command**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=DddLayeringArchTest
```

Expected: test compilation succeeds, then the same `ContentTextCodec` Spring Web violation fails.

- [ ] **Step 6: Commit the baseline documentation**

```bash
git add docs/handbook/architecture.md
git commit -m "docs: clarify architecture verification baseline"
```

---

### Task 2: Move HTML Text Codec Implementation Out Of Application

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentTextCodec.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/text/SpringHtmlContentTextCodec.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/content/text/ContentTextCodecTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/text/SpringHtmlContentTextCodecTest.java`
- Modify tests that construct `new ContentTextCodec()`: `CommentApplicationServiceTest.java`, `PostPublishingApplicationServiceTest.java`, `PostReadApplicationServiceTest.java`, `ContentPostPayloadAssemblerTest.java`

**Interfaces:**
- Consumes: current application constructors that already depend on `ContentTextCodec`.
- Produces: `ContentTextCodec` as a pure application interface and `SpringHtmlContentTextCodec` as its Spring Web implementation.

- [ ] **Step 1: Write the infrastructure codec test first**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/text/SpringHtmlContentTextCodecTest.java`:

```java
package com.nowcoder.community.content.infrastructure.text;

import com.nowcoder.community.content.application.ContentTextCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringHtmlContentTextCodecTest {

    private final ContentTextCodec codec = new SpringHtmlContentTextCodec();

    @Test
    void codecShouldEscapeOnWriteAndDecodeOnRead() {
        assertThat(codec.escapeOnWrite(null)).isNull();
        assertThat(codec.escapeOnWrite("")).isEqualTo("");
        assertThat(codec.escapeOnWrite("<tag>A&B</tag>")).isEqualTo("&lt;tag&gt;A&amp;B&lt;/tag&gt;");
        assertThat(codec.escapeOnWrite("&lt;")).isEqualTo("&amp;lt;");
        assertThat(codec.decodeOnRead(null)).isNull();
        assertThat(codec.decodeOnRead("")).isEqualTo("");
        assertThat(codec.decodeOnRead("&lt;tag&gt;")).isEqualTo("<tag>");
        assertThat(codec.decodeOnRead("A&amp;B")).isEqualTo("A&B");
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails to compile**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=SpringHtmlContentTextCodecTest
```

Expected: FAIL because `SpringHtmlContentTextCodec` does not exist.

- [ ] **Step 3: Convert `ContentTextCodec` to an application interface**

Replace the entire content of `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentTextCodec.java` with:

```java
package com.nowcoder.community.content.application;

public interface ContentTextCodec {

    String escapeOnWrite(String text);

    String decodeOnRead(String text);
}
```

- [ ] **Step 4: Add the infrastructure implementation**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/text/SpringHtmlContentTextCodec.java`:

```java
package com.nowcoder.community.content.infrastructure.text;

import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class SpringHtmlContentTextCodec implements ContentTextCodec {

    @Override
    public String escapeOnWrite(String text) {
        return text == null ? null : HtmlUtils.htmlEscape(text);
    }

    @Override
    public String decodeOnRead(String text) {
        return text == null ? null : HtmlUtils.htmlUnescape(text);
    }
}
```

- [ ] **Step 5: Remove the obsolete test file**

Delete `backend/community-app/src/test/java/com/nowcoder/community/content/text/ContentTextCodecTest.java`. Its package is `com.nowcoder.community.content.application`, but the new behavior belongs to the infrastructure implementation test created in Step 1.

- [ ] **Step 6: Update application tests to construct the infrastructure implementation**

In each affected test, add:

```java
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
```

Replace each direct construction:

```java
new ContentTextCodec()
```

with:

```java
new SpringHtmlContentTextCodec()
```

For `PostReadApplicationServiceTest`, change the helper to:

```java
private static ContentTextCodec textCodec() {
    return new SpringHtmlContentTextCodec();
}
```

- [ ] **Step 7: Verify behavior and the architecture rule**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='SpringHtmlContentTextCodecTest,CommentApplicationServiceTest,PostPublishingApplicationServiceTest,PostReadApplicationServiceTest,ContentPostPayloadAssemblerTest,DddLayeringArchTest'
```

Expected: PASS. `DddLayeringArchTest.application_must_not_depend_on_web_transport_types` no longer reports `ContentTextCodec`.

- [ ] **Step 8: Commit the codec boundary move**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentTextCodec.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/text/SpringHtmlContentTextCodec.java \
  backend/community-app/src/test/java/com/nowcoder/community/content
git commit -m "refactor: move content text codec implementation to infrastructure"
```

---

### Task 3: Reshape Content, Social, And User Event Dispatch Ports

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/DispatchContentEventCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/application/command/DispatchSocialEventCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/DispatchUserEventCommand.java`
- Rename: `ContentEventKafkaDispatchPort.java` to `ContentIntegrationEventDispatcher.java`
- Rename: `SocialEventKafkaDispatchPort.java` to `SocialIntegrationEventDispatcher.java`
- Rename: `UserEventKafkaDispatchPort.java` to `UserIntegrationEventDispatcher.java`
- Modify: `ContentEventDispatchApplicationService.java`, `SocialEventDispatchApplicationService.java`, `UserEventDispatchApplicationService.java`
- Modify: `ContentEventKafkaSenderAdapter.java`, `SocialEventKafkaSenderAdapter.java`, `UserEventKafkaSenderAdapter.java`
- Modify: `ContentEventKafkaOutboxHandler.java`, `SocialEventKafkaOutboxHandler.java`, `UserEventKafkaOutboxHandler.java`
- Modify tests: `ContentEventDispatchApplicationServiceTest.java`, `SocialEventDispatchApplicationServiceTest.java`, `UserEventDispatchApplicationServiceTest.java`, `OutboxContentEventPublisherTest.java`, `OutboxSocialDomainEventPublisherTest.java`, `OutboxUserPolicyEventPublisherTest.java`

**Interfaces:**
- Consumes: `ContentContractEvent`, `SocialContractEvent`, `UserContractEvent`.
- Produces:
  - `ContentIntegrationEventDispatcher.dispatch(String eventKey, ContentContractEvent event)`
  - `SocialIntegrationEventDispatcher.dispatch(String eventKey, SocialContractEvent event)`
  - `UserIntegrationEventDispatcher.dispatch(String eventKey, UserContractEvent event)`
  - command records carrying outbox key and payload JSON.

- [ ] **Step 1: Add failing tests for semantic dispatchers**

In each dispatch application service test, replace the mock field and constructor shape.

For content:

```java
private final ContentIntegrationEventDispatcher dispatcher = mock(ContentIntegrationEventDispatcher.class);
private final ContentEventDispatchApplicationService service =
        new ContentEventDispatchApplicationService(jsonCodec, dispatcher);
```

Change verifications from:

```java
verify(dispatchPort).send(eq(KAFKA_TOPIC), eq(postId.toString()), eventCaptor.capture());
```

to:

```java
verify(dispatcher).dispatch(eq(postId.toString()), eventCaptor.capture());
```

Change the port failure setup from:

```java
doThrow(failure).when(dispatchPort).send(eq(KAFKA_TOPIC), eq(postId.toString()), any());
```

to:

```java
doThrow(failure).when(dispatcher).dispatch(eq(postId.toString()), any());
```

Repeat the same mechanical shape for social and user with `SocialIntegrationEventDispatcher` and `UserIntegrationEventDispatcher`.

- [ ] **Step 2: Add command usage to outbox handler tests**

In handler or publisher tests that call the application service through outbox handlers, verify the handler constructs these command records:

```java
new DispatchContentEventCommand(event.eventKey(), event.payload())
new DispatchSocialEventCommand(event.eventKey(), event.payload())
new DispatchUserEventCommand(event.eventKey(), event.payload())
```

The expected behavior is unchanged: blank payload and malformed JSON still throw `IllegalStateException` for retry.

- [ ] **Step 3: Run tests and verify compile failure**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,OutboxContentEventPublisherTest,OutboxSocialDomainEventPublisherTest,OutboxUserPolicyEventPublisherTest'
```

Expected: FAIL because new command records and semantic dispatcher interfaces do not exist yet.

- [ ] **Step 4: Add command records**

Create `DispatchContentEventCommand.java`:

```java
package com.nowcoder.community.content.application.command;

public record DispatchContentEventCommand(String eventKey, String payloadJson) {
}
```

Create `DispatchSocialEventCommand.java`:

```java
package com.nowcoder.community.social.application.command;

public record DispatchSocialEventCommand(String eventKey, String payloadJson) {
}
```

Create `DispatchUserEventCommand.java`:

```java
package com.nowcoder.community.user.application.command;

public record DispatchUserEventCommand(String eventKey, String payloadJson) {
}
```

- [ ] **Step 5: Rename the three Kafka-shaped application ports**

Run:

```bash
git mv backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventKafkaDispatchPort.java backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentIntegrationEventDispatcher.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventKafkaDispatchPort.java backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialIntegrationEventDispatcher.java
git mv backend/community-app/src/main/java/com/nowcoder/community/user/application/UserEventKafkaDispatchPort.java backend/community-app/src/main/java/com/nowcoder/community/user/application/UserIntegrationEventDispatcher.java
```

Replace each interface body:

```java
public interface ContentIntegrationEventDispatcher {

    void dispatch(String eventKey, ContentContractEvent event);
}
```

```java
public interface SocialIntegrationEventDispatcher {

    void dispatch(String eventKey, SocialContractEvent event);
}
```

```java
public interface UserIntegrationEventDispatcher {

    void dispatch(String eventKey, UserContractEvent event);
}
```

- [ ] **Step 6: Remove topic injection from application services**

For `ContentEventDispatchApplicationService`, replace fields and constructor parameters with:

```java
private final JsonCodec jsonCodec;
private final ContentIntegrationEventDispatcher dispatcher;

public ContentEventDispatchApplicationService(
        JsonCodec jsonCodec,
        ContentIntegrationEventDispatcher dispatcher
) {
    this.jsonCodec = jsonCodec;
    this.dispatcher = dispatcher;
}
```

Replace the public dispatch method signature with:

```java
public void dispatch(DispatchContentEventCommand command) {
    if (command == null || !StringUtils.hasText(command.payloadJson())) {
        throw new IllegalStateException("content event outbox payload is blank");
    }

    ContentContractEvent contractEvent = parseContractEvent(command.payloadJson());
    if (!StringUtils.hasText(contractEvent.eventId())) {
        throw new IllegalStateException("content event outbox payload missing eventId");
    }
    if (!StringUtils.hasText(contractEvent.type())) {
        throw new IllegalStateException("content event outbox payload missing type");
    }
    dispatcher.dispatch(command.eventKey(), contractEvent);
}
```

Apply the same shape to social and user using their command and event types. Keep each existing `parseContractEvent`, `typedPayload`, `isKnownPayloadType`, and `text` helper behavior unchanged.

- [ ] **Step 7: Move topic lookup into sender adapters**

For `ContentEventKafkaSenderAdapter`, replace the interface and constructor shape with:

```java
public class ContentEventKafkaSenderAdapter implements ContentIntegrationEventDispatcher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String kafkaTopic;

    public ContentEventKafkaSenderAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${content.events.kafka-topic:content.events}") String kafkaTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
    }

    @Override
    public void dispatch(String eventKey, ContentContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, kafkaTopic, eventKey, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("content event kafka publish failed: " + kafkaTopic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("content event kafka publish failed: " + kafkaTopic, e);
        }
    }
}
```

Apply the same shape to social and user with these topic properties:

```java
@Value("${social.events.kafka-topic:social.events}") String kafkaTopic
@Value("${user.events.kafka-topic:user.events}") String kafkaTopic
```

- [ ] **Step 8: Update outbox handlers to pass command records**

In `ContentEventKafkaOutboxHandler.handle`, replace the application call with:

```java
applicationService.dispatch(new DispatchContentEventCommand(
        event == null ? null : event.eventKey(),
        event == null ? null : event.payload()
));
```

Use `DispatchSocialEventCommand` in `SocialEventKafkaOutboxHandler` and `DispatchUserEventCommand` in `UserEventKafkaOutboxHandler`.

- [ ] **Step 9: Verify focused event dispatch tests**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,OutboxContentEventPublisherTest,OutboxSocialDomainEventPublisherTest,OutboxUserPolicyEventPublisherTest'
```

Expected: PASS. These tests should verify event typing and error behavior, not Kafka topic selection inside application services.

- [ ] **Step 10: Commit the content/social/user dispatch reshape**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content \
  backend/community-app/src/main/java/com/nowcoder/community/social \
  backend/community-app/src/main/java/com/nowcoder/community/user \
  backend/community-app/src/test/java/com/nowcoder/community/content \
  backend/community-app/src/test/java/com/nowcoder/community/social \
  backend/community-app/src/test/java/com/nowcoder/community/user
git commit -m "refactor: use semantic content social user event dispatch ports"
```

---

### Task 4: Reshape Growth And IM Outbox Dispatch Ports

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/TaskProgressDispatchKind.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/DispatchTaskProgressEventCommand.java`
- Rename: `TaskProgressKafkaDispatchPort.java` to `TaskProgressIntegrationEventDispatcher.java`
- Modify: `TaskProgressOutboxDispatchApplicationService.java`
- Modify: growth outbox handlers under `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event`
- Modify: `TaskProgressKafkaSenderAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/application/command/DispatchImPolicyEventCommand.java`
- Rename: `ImPolicyEventKafkaDispatchPort.java` to `ImPolicyIntegrationEventDispatcher.java`
- Modify: `ImPolicyEventDispatchApplicationService.java`
- Modify: `ImPolicyKafkaOutboxHandler.java`
- Modify: `ImPolicyEventKafkaSenderAdapter.java`
- Modify tests: `TaskProgressOutboxDispatchApplicationServiceTest.java`, `ImPolicyEventDispatchApplicationServiceTest.java`

**Interfaces:**
- Consumes: `PostPayload`, `CommentPayload`, `LikePayload`, `UserMessagingPolicyChanged`, `UserBlockRelationChanged`.
- Produces:
  - `TaskProgressIntegrationEventDispatcher.dispatchPostPublished(String eventKey, PostPayload payload)`
  - `TaskProgressIntegrationEventDispatcher.dispatchCommentCreated(String eventKey, CommentPayload payload)`
  - `TaskProgressIntegrationEventDispatcher.dispatchLikeCreated(String eventKey, LikePayload payload)`
  - `TaskProgressIntegrationEventDispatcher.dispatchLikeRemoved(String eventKey, LikePayload payload)`
  - `ImPolicyIntegrationEventDispatcher.dispatchUserMessagingPolicyChanged(String eventKey, UserMessagingPolicyChanged event)`
  - `ImPolicyIntegrationEventDispatcher.dispatchUserBlockRelationChanged(String eventKey, UserBlockRelationChanged event)`

- [ ] **Step 1: Update growth tests to expect typed dispatch methods**

In `TaskProgressOutboxDispatchApplicationServiceTest`, replace:

```java
private final TaskProgressKafkaDispatchPort dispatchPort = mock(TaskProgressKafkaDispatchPort.class);
```

with:

```java
private final TaskProgressIntegrationEventDispatcher dispatcher = mock(TaskProgressIntegrationEventDispatcher.class);
```

Construct the service with:

```java
new TaskProgressOutboxDispatchApplicationService(jsonCodec, dispatcher)
```

Change verifications to typed methods. Example:

```java
verify(dispatcher).dispatchPostPublished(eq(expectedKey), payloadCaptor.capture());
verify(dispatcher).dispatchCommentCreated(eq(expectedKey), payloadCaptor.capture());
verify(dispatcher).dispatchLikeCreated(eq(expectedKey), payloadCaptor.capture());
verify(dispatcher).dispatchLikeRemoved(eq(expectedKey), payloadCaptor.capture());
```

- [ ] **Step 2: Update IM tests to expect typed dispatch methods**

In `ImPolicyEventDispatchApplicationServiceTest`, replace:

```java
private final ImPolicyEventKafkaDispatchPort dispatchPort = mock(ImPolicyEventKafkaDispatchPort.class);
```

with:

```java
private final ImPolicyIntegrationEventDispatcher dispatcher = mock(ImPolicyIntegrationEventDispatcher.class);
```

Construct the service with:

```java
new ImPolicyEventDispatchApplicationService(jsonCodec, dispatcher)
```

Change verifications to:

```java
verify(dispatcher).dispatchUserMessagingPolicyChanged(eq(userId.toString()), eventCaptor.capture());
verify(dispatcher).dispatchUserBlockRelationChanged(eq(blockerUserId.toString()), eventCaptor.capture());
```

- [ ] **Step 3: Run tests and verify compile failure**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='TaskProgressOutboxDispatchApplicationServiceTest,ImPolicyEventDispatchApplicationServiceTest'
```

Expected: FAIL because new command records and semantic dispatcher interfaces do not exist yet.

- [ ] **Step 4: Add growth command types**

Create `TaskProgressDispatchKind.java`:

```java
package com.nowcoder.community.growth.application.command;

public enum TaskProgressDispatchKind {
    POST_PUBLISHED,
    COMMENT_CREATED,
    LIKE_CREATED,
    LIKE_REMOVED
}
```

Create `DispatchTaskProgressEventCommand.java`:

```java
package com.nowcoder.community.growth.application.command;

public record DispatchTaskProgressEventCommand(
        TaskProgressDispatchKind kind,
        String eventKey,
        String payloadJson
) {
}
```

- [ ] **Step 5: Add IM command type**

Create `DispatchImPolicyEventCommand.java`:

```java
package com.nowcoder.community.im.application.command;

public record DispatchImPolicyEventCommand(
        String outboxEventId,
        String outboxKey,
        String payloadJson
) {
}
```

- [ ] **Step 6: Rename and reshape growth port**

Run:

```bash
git mv backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressKafkaDispatchPort.java backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressIntegrationEventDispatcher.java
```

Replace the interface body with:

```java
package com.nowcoder.community.growth.application;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;

public interface TaskProgressIntegrationEventDispatcher {

    void dispatchPostPublished(String eventKey, PostPayload payload);

    void dispatchCommentCreated(String eventKey, CommentPayload payload);

    void dispatchLikeCreated(String eventKey, LikePayload payload);

    void dispatchLikeRemoved(String eventKey, LikePayload payload);
}
```

- [ ] **Step 7: Rename and reshape IM port**

Run:

```bash
git mv backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyEventKafkaDispatchPort.java backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyIntegrationEventDispatcher.java
```

Replace the interface body with:

```java
package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;

public interface ImPolicyIntegrationEventDispatcher {

    void dispatchUserMessagingPolicyChanged(String eventKey, UserMessagingPolicyChanged event);

    void dispatchUserBlockRelationChanged(String eventKey, UserBlockRelationChanged event);
}
```

- [ ] **Step 8: Collapse growth service entry points to one command method**

In `TaskProgressOutboxDispatchApplicationService`, remove the topic fields and constructor `@Value` parameters. The constructor becomes:

```java
public TaskProgressOutboxDispatchApplicationService(
        JsonCodec jsonCodec,
        TaskProgressIntegrationEventDispatcher dispatcher
) {
    this.jsonCodec = jsonCodec;
    this.dispatcher = dispatcher;
}
```

Add this public entry point:

```java
public void dispatch(DispatchTaskProgressEventCommand command) {
    if (command == null || command.kind() == null || !StringUtils.hasText(command.payloadJson())) {
        return;
    }
    switch (command.kind()) {
        case POST_PUBLISHED -> dispatchPostPublished(command.eventKey(), command.payloadJson());
        case COMMENT_CREATED -> dispatchCommentCreated(command.eventKey(), command.payloadJson());
        case LIKE_CREATED -> dispatchLikeCreated(command.eventKey(), command.payloadJson());
        case LIKE_REMOVED -> dispatchLikeRemoved(command.eventKey(), command.payloadJson());
    }
}
```

Keep the four existing private parsing methods, but change the final calls:

```java
dispatcher.dispatchPostPublished(dispatchKey(key, payload.getUserId().toString()), payload);
dispatcher.dispatchCommentCreated(dispatchKey(key, payload.getUserId().toString()), payload);
dispatcher.dispatchLikeCreated(dispatchKey(key, payload.getEntityUserId().toString()), payload);
dispatcher.dispatchLikeRemoved(dispatchKey(key, payload.getEntityUserId().toString()), payload);
```

- [ ] **Step 9: Move growth topic selection into `TaskProgressKafkaSenderAdapter`**

Make `TaskProgressKafkaSenderAdapter` implement `TaskProgressIntegrationEventDispatcher`, inject all four existing topic properties, and route each typed method:

```java
public TaskProgressKafkaSenderAdapter(
        KafkaTemplate<String, Object> kafkaTemplate,
        @Value("${growth.task.kafka.topics.post-published:growth.task.post-published}") String postPublishedTopic,
        @Value("${growth.task.kafka.topics.comment-created:growth.task.comment-created}") String commentCreatedTopic,
        @Value("${growth.task.kafka.topics.like-created:growth.task.like-created}") String likeCreatedTopic,
        @Value("${growth.task.kafka.topics.like-removed:growth.task.like-removed}") String likeRemovedTopic
) {
    this.kafkaTemplate = kafkaTemplate;
    this.postPublishedTopic = postPublishedTopic;
    this.commentCreatedTopic = commentCreatedTopic;
    this.likeCreatedTopic = likeCreatedTopic;
    this.likeRemovedTopic = likeRemovedTopic;
}
```

The methods delegate to a private sender:

```java
@Override
public void dispatchPostPublished(String eventKey, PostPayload payload) {
    send(postPublishedTopic, eventKey, payload);
}

@Override
public void dispatchCommentCreated(String eventKey, CommentPayload payload) {
    send(commentCreatedTopic, eventKey, payload);
}

@Override
public void dispatchLikeCreated(String eventKey, LikePayload payload) {
    send(likeCreatedTopic, eventKey, payload);
}

@Override
public void dispatchLikeRemoved(String eventKey, LikePayload payload) {
    send(likeRemovedTopic, eventKey, payload);
}

private void send(String topic, String eventKey, Object payload) {
    try {
        TraceKafkaSender.send(kafkaTemplate, topic, eventKey, payload).join();
    } catch (CompletionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new IllegalStateException("growth task kafka publish failed: " + topic, cause);
    }
}
```

- [ ] **Step 10: Update growth outbox handlers to pass commands**

Examples:

```java
applicationService.dispatch(new DispatchTaskProgressEventCommand(
        TaskProgressDispatchKind.POST_PUBLISHED,
        event == null ? null : event.eventKey(),
        event == null ? null : event.payload()
));
```

Use `COMMENT_CREATED`, `LIKE_CREATED`, and `LIKE_REMOVED` in the corresponding handlers.

- [ ] **Step 11: Collapse IM service entry point to a command**

In `ImPolicyEventDispatchApplicationService`, remove the topic fields and constructor `@Value` parameters. Constructor becomes:

```java
public ImPolicyEventDispatchApplicationService(
        JsonCodec jsonCodec,
        ImPolicyIntegrationEventDispatcher dispatcher
) {
    this.jsonCodec = jsonCodec;
    this.dispatcher = dispatcher;
}
```

Replace the public method with:

```java
public void dispatch(DispatchImPolicyEventCommand command) {
    if (command == null || !StringUtils.hasText(command.payloadJson())) {
        return;
    }

    JsonNode payload;
    try {
        payload = jsonCodec.readTree(command.payloadJson());
    } catch (JsonCodecException e) {
        throw new IllegalStateException("im policy outbox payload 反序列化失败", e);
    }

    String kind = text(payload, "kind");
    if ("USER_POLICY".equalsIgnoreCase(kind) || "MODERATION".equalsIgnoreCase(kind)) {
        publishModerationState(command.outboxEventId(), payload);
        return;
    }
    if ("BLOCK".equalsIgnoreCase(kind)) {
        publishBlockState(command.outboxEventId(), payload);
    }
}
```

Change final dispatch calls to:

```java
dispatcher.dispatchUserMessagingPolicyChanged(userId.toString(), changed);
dispatcher.dispatchUserBlockRelationChanged(blockerUserId.toString(), changed);
```

- [ ] **Step 12: Move IM topic selection into `ImPolicyEventKafkaSenderAdapter`**

Make `ImPolicyEventKafkaSenderAdapter` implement `ImPolicyIntegrationEventDispatcher`, inject the existing topic properties, and route each typed method:

```java
@Override
public void dispatchUserMessagingPolicyChanged(String eventKey, UserMessagingPolicyChanged event) {
    send(userMessagingPolicyChangedTopic, eventKey, event);
}

@Override
public void dispatchUserBlockRelationChanged(String eventKey, UserBlockRelationChanged event) {
    send(userBlockRelationChangedTopic, eventKey, event);
}

private void send(String topic, String eventKey, Object event) {
    try {
        TraceKafkaSender.send(kafkaTemplate, topic, eventKey, event).join();
    } catch (CompletionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new IllegalStateException("im policy kafka publish failed: " + topic, cause);
    }
}
```

- [ ] **Step 13: Update `ImPolicyKafkaOutboxHandler` to pass the command**

```java
applicationService.dispatch(new DispatchImPolicyEventCommand(
        event == null ? null : event.eventId(),
        event == null ? null : event.eventKey(),
        event == null ? null : event.payload()
));
```

- [ ] **Step 14: Verify focused growth and IM tests**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='TaskProgressOutboxDispatchApplicationServiceTest,ImPolicyEventDispatchApplicationServiceTest'
```

Expected: PASS.

- [ ] **Step 15: Commit growth and IM dispatch reshape**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/growth \
  backend/community-app/src/main/java/com/nowcoder/community/im \
  backend/community-app/src/test/java/com/nowcoder/community/growth \
  backend/community-app/src/test/java/com/nowcoder/community/im
git commit -m "refactor: use semantic growth and im event dispatch ports"
```

---

### Task 5: Add Event Port Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`

**Interfaces:**
- Consumes: cleaned application port names and method signatures from Tasks 3 and 4.
- Produces: ArchUnit failures for new Kafka-shaped application ports or application methods exposing broker vocabulary.

- [ ] **Step 1: Add failing guardrail tests**

In `DddLayeringArchTest`, add this rule near the existing application-layer rules:

```java
@ArchTest
static final ArchRule application_must_not_depend_on_broker_transport_types =
        noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.kafka..",
                        "org.apache.kafka..",
                        "..common.kafka.."
                )
                .because("broker transport details belong in infrastructure adapters");

@ArchTest
static final ArchRule application_ports_must_not_expose_transport_vocabulary =
        classes()
                .that().resideInAnyPackage("..application..")
                .and().areInterfaces()
                .should(notExposeTransportVocabularyInApplicationPort());
```

Add this helper method:

```java
private static ArchCondition<JavaClass> notExposeTransportVocabularyInApplicationPort() {
    Set<String> forbiddenNameParts = Set.of("Kafka", "Rabbit", "Redis", "MyBatis", "Jdbc", "Http");
    Set<String> forbiddenParameterNames = Set.of(
            "org.springframework.kafka.core.KafkaTemplate",
            "org.apache.kafka.clients.consumer.ConsumerRecord",
            "org.apache.kafka.clients.producer.ProducerRecord"
    );
    return new ArchCondition<>("not expose broker or infrastructure vocabulary") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            for (String forbiddenNamePart : forbiddenNameParts) {
                if (item.getSimpleName().contains(forbiddenNamePart)) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getFullName() + " contains transport/infrastructure vocabulary: " + forbiddenNamePart
                    ));
                }
            }
            for (JavaMethod method : item.getMethods()) {
                String lowerMethodName = method.getName().toLowerCase();
                if (lowerMethodName.contains("topic")
                        || lowerMethodName.contains("partition")
                        || lowerMethodName.contains("offset")) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " exposes broker routing vocabulary"
                    ));
                }
                for (JavaClass parameterType : method.getRawParameterTypes()) {
                    if (forbiddenParameterNames.contains(parameterType.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName() + " exposes " + parameterType.getName()
                        ));
                    }
                }
            }
        }
    };
}
```

- [ ] **Step 2: Run only the new guardrails**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=DddLayeringArchTest
```

Expected: PASS. If this fails, the output should name an application interface still containing `Kafka`, `Rabbit`, `Redis`, `MyBatis`, `Jdbc`, or `Http`, or a method still exposing topic/partition/offset vocabulary.

- [ ] **Step 3: Search for old port names**

Run:

```bash
rg "KafkaDispatchPort|send\\(String topic|KafkaTemplate" backend/community-app/src/main/java/com/nowcoder/community/*/application
```

Expected: no matches.

- [ ] **Step 4: Commit port guardrails**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java
git commit -m "test: guard application ports from transport vocabulary"
```

---

### Task 6: Remove Public Transactional Self-Invocation And Broaden The Rule

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/TransactionBoundaryArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationServiceTest.java`

**Interfaces:**
- Consumes: existing public application methods.
- Produces: no public `@Transactional` method calls another public `@Transactional` method on the same instance.

- [ ] **Step 1: Add the broad ArchUnit rule first**

Replace `LOCKING_APPLICATION_SERVICES` usage in `TransactionBoundaryArchTest` with a predicate over all production application services:

Ensure this import is present:

```java
import com.tngtech.archunit.core.domain.JavaClass;
```

```java
@Test
void applicationServicesMustNotSelfInvokeTransactionalMethods() {
    List<String> violations = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.nowcoder.community")
            .stream()
            .filter(this::isApplicationService)
            .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
            .filter(this::isSameClassCall)
            .filter(this::targetsTransactionalBoundary)
            .map(this::describe)
            .sorted()
            .toList();

    assertThat(violations)
            .as("@Transactional self-invocation bypasses Spring AOP proxies")
            .isEmpty();
}

private boolean isApplicationService(JavaClass javaClass) {
    return javaClass.getPackageName().contains(".application")
            && javaClass.getSimpleName().endsWith("ApplicationService");
}

private boolean targetsTransactionalBoundary(JavaMethodCall call) {
    boolean methodAnnotated = call.getTarget()
            .resolveMember()
            .filter(member -> member.isAnnotatedWith(Transactional.class))
            .isPresent();
    return methodAnnotated || call.getTargetOwner().isAnnotatedWith(Transactional.class);
}
```

Keep the existing `isSameClassCall` and `describe` helpers.

- [ ] **Step 2: Run the broadened rule and verify known failures**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=TransactionBoundaryArchTest
```

Expected: FAIL listing self-invocations in `CommentApplicationService` and `WalletLedgerApplicationService`.

- [ ] **Step 3: Refactor `CommentApplicationService` wrappers**

Replace the current public `create` wrappers with public methods that delegate to a private non-transactional helper:

```java
@Transactional
public CommentCreateResult create(
        UUID userId,
        String idempotencyKey,
        UUID postId,
        Integer entityType,
        UUID entityId,
        UUID targetId,
        String content
) {
    return createFromCommand(idempotencyKey, new CreateCommentCommand(userId, postId, entityType, entityId, targetId, content));
}

@Transactional
public CommentCreateResult create(String idempotencyKey, CreateCommentCommand command) {
    return createFromCommand(idempotencyKey, command);
}

@Transactional
public UUID addComment(UUID userId, String idempotencyKey, UUID postId, Integer entityType, UUID entityId, UUID targetId, String content) {
    return createFromCommand(idempotencyKey, new CreateCommentCommand(userId, postId, entityType, entityId, targetId, content)).commentId();
}

private CommentCreateResult createFromCommand(String idempotencyKey, CreateCommentCommand command) {
    if (command == null) {
        throw new IllegalArgumentException("command must not be null");
    }
    UUID userId = command.userId();
    UUID postId = command.postId();
    if (userId == null || postId == null) {
        throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
    }
    UUID commentId = idempotencyGuard.executeRequired(
            CREATE_COMMENT_IDEMPOTENCY_SCOPE,
            userId,
            idempotencyKey,
            UUID.class,
            () -> createInsideTransaction(command)
    );
    return new CommentCreateResult(commentId);
}
```

Replace update wrappers with:

```java
@Transactional
public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
    updateFromCommand(new UpdateCommentCommand(userId, postId, commentId, content));
}

@Transactional
public void update(UpdateCommentCommand command) {
    updateFromCommand(command);
}

private void updateFromCommand(UpdateCommentCommand command) {
    if (command == null) {
        throw new IllegalArgumentException("command must not be null");
    }
    UUID userId = command.userId();
    UUID postId = command.postId();
    UUID commentId = command.commentId();
    if (userId == null || postId == null || commentId == null) {
        throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
    }

    moderationGuard.assertCanSpeak(userId);
    postContentPort.getById(postId);
    CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
    CommentSnapshot parent = existing.entityType() == EntityTypes.COMMENT
            ? commentRepository.findSnapshot(existing.entityId()).orElse(null)
            : null;
    Date now = new Date();
    domainService.assertEditableByAuthor(existing, userId, postId, now, parent);
    commentRepository.updateContent(commentId, sanitize(command.content()), now);
}
```

- [ ] **Step 4: Refactor `WalletLedgerApplicationService` overloads**

Replace the three public `post` methods with:

```java
@Transactional
public WalletTxnResult post(String requestId, WalletTxnType txnType, List<WalletPosting> postings) {
    return postInsideTransaction(new WalletLedgerCommand(requestId, txnType, defaultBizType(txnType), requestId, postings));
}

@Transactional
public WalletTxnResult post(String requestId, WalletTxnType txnType, String bizId, List<WalletPosting> postings) {
    return postInsideTransaction(new WalletLedgerCommand(requestId, txnType, defaultBizType(txnType), bizId, postings));
}

@Transactional
public WalletTxnResult post(WalletLedgerCommand command) {
    return postInsideTransaction(command);
}

private WalletTxnResult postInsideTransaction(WalletLedgerCommand command) {
    validateRequest(command);
    String requestId = command.requestId();
    WalletTxnType txnType = command.txnType();
    String normalizedBizType = validateText(command.bizType(), "bizType");
    String normalizedBizId = validateText(command.bizId(), "bizId");
    List<WalletPosting> postings = command.postings();
    domainService.validateBalancedPostings(postings);

    WalletTxn existing = walletLedgerRepository.findTxnByRequestId(requestId);
    if (existing != null) {
        ensureReplayMatches(existing, txnType, normalizedBizType, normalizedBizId, postings);
        return new WalletTxnResult(existing.getTxnId(), existing.getStatus());
    }

    WalletTxn txn = domainService.newTxn(
            idGenerator.next(),
            requestId,
            txnType,
            normalizedBizType,
            normalizedBizId,
            amountOf(postings),
            new Date()
    );
    try {
        walletLedgerRepository.insertTxn(txn);
    } catch (DataIntegrityViolationException ex) {
        WalletTxn duplicated = walletLedgerRepository.findTxnByRequestId(requestId);
        if (duplicated != null) {
            ensureReplayMatches(duplicated, txnType, normalizedBizType, normalizedBizId, postings);
            return new WalletTxnResult(duplicated.getTxnId(), duplicated.getStatus());
        }
        throw ex;
    }

    for (WalletPosting posting : postings) {
        WalletAccount account = walletAccountService.lock(posting.accountId());
        long nextBalance = walletAccountService.apply(account, walletAccountService.deltaOf(account, posting));

        WalletEntry entry = new WalletEntry();
        entry.setEntryId(idGenerator.next());
        entry.setTxnId(txn.getTxnId());
        entry.setAccountId(account.getAccountId());
        entry.setDirection(posting.direction());
        entry.setAmount(posting.amount());
        entry.setBalanceAfter(nextBalance);
        walletLedgerRepository.insertEntry(entry);
    }

    walletLedgerRepository.markTxnSucceeded(txn.getTxnId());
    return new WalletTxnResult(txn.getTxnId(), WalletLedgerDomainService.TXN_STATUS_SUCCEEDED);
}
```

Keep the existing private helper methods called from the old `post(WalletLedgerCommand)` body unchanged.

- [ ] **Step 5: Run focused behavior tests**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='CommentApplicationServiceTest,WalletLedgerApplicationServiceTest'
```

Expected: PASS.

- [ ] **Step 6: Run the broadened transaction boundary rule**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=TransactionBoundaryArchTest
```

Expected: PASS.

- [ ] **Step 7: Commit transaction boundary hardening**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/TransactionBoundaryArchTest.java
git commit -m "test: broaden transaction self invocation guardrail"
```

---

### Task 7: Rename Infrastructure API Adapters And Add Naming Rule

**Files:**
- Rename: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryService.java` to `ContentEntityQueryApiAdapter.java`
- Rename: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanService.java` to `PostScanQueryApiAdapter.java`
- Modify: imports or tests that reference these concrete classes.
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`

**Interfaces:**
- Consumes: existing `ContentEntityQueryApi` and `PostScanQueryApi`.
- Produces: infrastructure API implementations named `*ApiAdapter`.

- [ ] **Step 1: Rename files with git**

```bash
git mv backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryService.java backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryApiAdapter.java
git mv backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanService.java backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanQueryApiAdapter.java
```

- [ ] **Step 2: Rename classes**

In `ContentEntityQueryApiAdapter.java`, change:

```java
public class ContentEntityQueryService implements ContentEntityQueryApi {
```

to:

```java
public class ContentEntityQueryApiAdapter implements ContentEntityQueryApi {
```

Rename the constructor to:

```java
public ContentEntityQueryApiAdapter(ContentEntityResolutionApplicationService applicationService) {
    this.applicationService = applicationService;
}
```

In `PostScanQueryApiAdapter.java`, change:

```java
public class PostScanService implements PostScanQueryApi {
```

to:

```java
public class PostScanQueryApiAdapter implements PostScanQueryApi {
```

Rename the constructor to:

```java
public PostScanQueryApiAdapter(PostReadApplicationService postReadApplicationService) {
    this.postReadApplicationService = postReadApplicationService;
}
```

- [ ] **Step 3: Update concrete references**

Run:

```bash
rg "ContentEntityQueryService|PostScanService" backend/community-app/src
```

Expected before editing: references only to the renamed concrete classes or tests.

Replace references with `ContentEntityQueryApiAdapter` and `PostScanQueryApiAdapter`. Run the same `rg` command again and expect no matches.

- [ ] **Step 4: Add infrastructure API naming guardrail**

In `InfraBoundaryArchTest`, add imports:

```java
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
```

Add this rule:

```java
@ArchTest
static final ArchRule infrastructure_owner_api_implementations_should_be_named_adapters =
        classes()
                .that().resideInAnyPackage("..infrastructure.api..")
                .should(haveApiAdapterNameWhenImplementingOwnerApi());
```

Add this helper:

```java
private static ArchCondition<JavaClass> haveApiAdapterNameWhenImplementingOwnerApi() {
    return new ArchCondition<>("end with ApiAdapter when implementing published owner APIs") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            boolean implementsOwnerApi = item.getAllRawInterfaces().stream()
                    .map(JavaClass::getPackageName)
                    .anyMatch(packageName -> packageName.contains(".api.query")
                            || packageName.contains(".api.action"));
            if (!implementsOwnerApi || item.getSimpleName().endsWith("ApiAdapter")) {
                return;
            }
            events.add(SimpleConditionEvent.violated(
                    item,
                    item.getFullName() + " implements api.query/api.action but is not named *ApiAdapter"
            ));
        }
    };
}
```

- [ ] **Step 5: Verify API adapter naming**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=InfraBoundaryArchTest
```

Expected: PASS.

- [ ] **Step 6: Commit adapter naming cleanup**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java
git commit -m "refactor: name infrastructure api implementations as adapters"
```

---

### Task 8: Document Application Process Manager Collaboration

**Files:**
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md` only when it describes Kafka-shaped application ports.
- Read-only check: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`

**Interfaces:**
- Consumes: existing `domain_named_application_services_must_not_be_facade_entries` rule.
- Produces: documented rule for same-domain `ApplicationService -> ApplicationService` collaboration.

- [ ] **Step 1: Add process-manager documentation**

In `docs/handbook/architecture.md`, add this section under the application layer description:

```markdown
## Application Service Collaboration

`ApplicationService` remains the same-domain use-case entry style. Controllers, listeners, jobs, outbox handlers, bridges, and enqueuers must enter only a same-domain `*ApplicationService`.

Same-domain `ApplicationService -> ApplicationService` collaboration is allowed only when the caller is an explicit process manager or larger use-case orchestrator. The class name must identify the process it owns, for example `MarketWalletActionProcessorApplicationService`, `MarketWalletActionRecoveryApplicationService`, `MarketOrderAutoConfirmApplicationService`, `NoticeProjectionApplicationService`, or `SearchPostProjectionApplicationService`.

Domain-named facade services such as `MarketApplicationService`, `WalletApplicationService`, or `ContentApplicationService` must not delegate to multiple same-domain application services. Reusable application helpers that are not use-case entries should use focused names such as `*Issuer`, `*Assembler`, `*Scheduler`, `*Coordinator`, or `*Component` and must stay in the application package only when they express application semantics.

Transactional methods must not rely on self-invocation for Spring proxy behavior. Public `@Transactional` overloads should delegate to a private non-annotated helper when they share an implementation.
```

- [ ] **Step 2: Update event dispatch wording in architecture docs**

Search:

```bash
rg "KafkaDispatchPort|Kafka-shaped|topic.*application|application.*topic" docs/handbook docs/superpowers/specs
```

Expected: matches in the new design spec and possibly handbook/system design. Update handbook/system design text so it describes semantic application dispatch ports and infrastructure Kafka adapters. Do not edit the approved spec unless the implemented terminology differs from it.

- [ ] **Step 3: Verify existing facade guardrail still passes**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=DddLayeringArchTest
```

Expected: PASS.

- [ ] **Step 4: Commit documentation sync**

```bash
git add docs/handbook/architecture.md docs/handbook/system-design.md
git commit -m "docs: clarify application process manager collaboration"
```

---

### Task 9: Full Architecture And Regression Gate

**Files:**
- No source edits expected.
- Verification output to inspect: `backend/community-app/target/surefire-reports`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: final proof that architecture and focused behavior checks pass.

- [ ] **Step 1: Confirm no old Kafka-shaped application ports remain**

Run:

```bash
rg "KafkaDispatchPort|send\\(String topic|String topic|KafkaTemplate|ProducerRecord|ConsumerRecord" backend/community-app/src/main/java/com/nowcoder/community/*/application
```

Expected: no matches.

- [ ] **Step 2: Confirm application has no Spring Web dependency**

Run:

```bash
rg "org\\.springframework\\.web|jakarta\\.servlet|org\\.springframework\\.http" backend/community-app/src/main/java/com/nowcoder/community/*/application
```

Expected: no matches.

- [ ] **Step 3: Run all architecture tests with reactor dependencies**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest'
```

Expected: PASS.

- [ ] **Step 4: Run the required narrow architecture command**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS after Task 1 refreshed local snapshots.

- [ ] **Step 5: Run focused behavior suites touched by this plan**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='SpringHtmlContentTextCodecTest,CommentApplicationServiceTest,PostPublishingApplicationServiceTest,PostReadApplicationServiceTest,ContentPostPayloadAssemblerTest,ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,TaskProgressOutboxDispatchApplicationServiceTest,ImPolicyEventDispatchApplicationServiceTest,WalletLedgerApplicationServiceTest'
```

Expected: PASS.

- [ ] **Step 6: Run the full module test suite**

Run:

```bash
cd backend
mvn test -pl :community-app -am
```

Expected: PASS. If environment-backed tests require unavailable infrastructure, record the exact failing test class, failing dependency, and the focused suites from Step 5 that still passed.

- [ ] **Step 7: Inspect git status**

Run:

```bash
git status --short
```

Expected: clean after all task commits.

- [ ] **Step 8: Final implementation summary**

Prepare a summary with:

```markdown
Implemented:
- Application Web dependency moved behind `SpringHtmlContentTextCodec`.
- Kafka-shaped application event ports replaced with semantic dispatch ports.
- Outbox dispatch inputs represented as application command records.
- Transaction self-invocation guard broadened and known self-calls removed.
- Infrastructure API adapters renamed and protected by ArchUnit.
- Architecture docs updated for verification and process-manager collaboration.

Verification:
- `mvn test -pl :community-app -am -Dtest='*ArchTest'`
- `mvn test -pl :community-app -Dtest='*ArchTest'`
- focused behavior suites
- full `mvn test -pl :community-app -am`
```
