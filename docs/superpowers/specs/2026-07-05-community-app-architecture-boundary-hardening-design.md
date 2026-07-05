# Community App Architecture Boundary Hardening Design

## Context

`community-app` is a flattened Spring Boot monolith that relies on strict DDD tactical layering and ArchUnit tests for architectural enforcement. The current codebase is not a traditional three-layer system: most controllers enter same-domain `*ApplicationService` classes, domain code is largely free of Spring and MyBatis, and persistence is mostly hidden behind domain repository interfaces.

The remaining problems are narrower but important:

- The architecture guardrails cannot currently run because `community-app` test compilation fails before ArchUnit executes.
- At least one application component depends on Spring Web utility code, violating the project's own application-layer transport boundary.
- Several application ports and dispatch services expose Kafka/topic/key and outbox JSON concerns as application semantics.
- Some `ApplicationService` classes are used as internal orchestration collaborators, which weakens the meaning of "ApplicationService as use-case boundary."
- Some infrastructure API adapter classes are named `*Service`, which obscures whether a class is an application entry point, domain service, or adapter.
- Transaction self-invocation guardrails exist but cover only a small hard-coded set of classes.

This design hardens those boundaries without splitting the monolith or rewriting all domains.

## Goals

- Restore a trustworthy architecture verification baseline.
- Remove application-layer dependencies on HTTP/Web transport implementation details.
- Move Kafka, topic, key, outbox payload parsing, and dispatch mechanics out of application semantics where they are currently leaked.
- Preserve existing HTTP endpoints, Kafka topics, published API contracts, and database schemas unless a focused migration explicitly requires a compatible extension.
- Clarify how `ApplicationService` classes may collaborate internally without creating ambiguous facade/service layers.
- Tighten guardrails so the same classes do not regress after cleanup.
- Produce a phased implementation plan that can be executed incrementally with focused verification at each stage.

## Non-Goals

- Do not split `community-app` into separate Maven modules or microservices in this remediation batch.
- Do not rewrite all application services or all domain models.
- Do not change public HTTP response shapes.
- Do not rename published synchronous `api.*` interfaces unless a compatibility-preserving adapter remains.
- Do not change Kafka topic names or event contract field names in this batch.
- Do not implement this design as part of this document-only task.

## Current Findings

### Verification Baseline Is Broken

Running:

```bash
cd backend
mvn test -pl :community-app -Dtest=DddLayeringArchTest
```

fails during `testCompile` because `AuthControllerUnitTest` references removed constants:

- `ValidationLimits.REGISTRATION_TOKEN_MAX`
- `ValidationLimits.REGISTRATION_CODE_MAX`
- `ValidationLimits.TOKEN_MAX`

As long as test compilation fails, `*ArchTest` cannot act as a reliable boundary gate.

### Application Depends On Spring Web

`content.application.ContentTextCodec` imports `org.springframework.web.util.HtmlUtils`, while `DddLayeringArchTest.application_must_not_depend_on_web_transport_types` forbids `..application..` from depending on `org.springframework.web..`.

The behavior may be valid, but the placement is not. HTML escaping is a transport/view encoding concern or a technical text codec adapter, not a core application orchestration rule.

### Kafka Semantics Leak Into Application Ports

Examples:

- `ContentEventKafkaDispatchPort.send(String topic, String key, ContentContractEvent event)`
- `SocialEventKafkaDispatchPort.send(String topic, String key, SocialContractEvent event)`
- `TaskProgressKafkaDispatchPort.send(String topic, String key, Object payload)`
- `ImPolicyEventKafkaDispatchPort.send(String topic, String key, Object event)`

These are not application-semantic ports. They expose broker vocabulary and routing details to application code. The application layer should express "dispatch this contract event" or "publish this task-progress integration event"; the infrastructure adapter should decide Kafka topic, key, serialization, and sender mechanics.

### Outbox Dispatch Services Carry Adapter Responsibilities

`ContentEventDispatchApplicationService`, `SocialEventDispatchApplicationService`, `UserEventDispatchApplicationService`, `TaskProgressOutboxDispatchApplicationService`, and `ImPolicyEventDispatchApplicationService` mix several responsibilities:

- outbox payload JSON parsing
- contract envelope reconstruction
- event-type-to-payload mapping
- topic configuration
- key selection
- dispatch through a Kafka-shaped port

Some of this belongs in an inbound outbox handler or infrastructure message adapter. The application boundary should own business validation and use-case orchestration, not low-level transport routing.

### ApplicationService Semantics Are Overloaded

Some services are use-case entries, while others behave like internal workflow processors or saga helpers. For example, `MarketWalletActionProcessorApplicationService` coordinates due action leasing, wallet API calls, saga state advancement, retries, and recovery markers while depending on other same-domain `ApplicationService` classes.

This can be valid if documented as an application process manager, but it should not silently become a generic service layer that reintroduces pre-DDD `Service` semantics under a new suffix.

### Guardrails Are Strong But Not Complete

The architecture tests cover many package rules, but gaps remain:

- transaction self-invocation guard checks only a hard-coded set of three classes
- application-layer technology leak checks caught Spring Web in principle, but they currently cannot run due to test compilation failure
- Kafka-shaped port names and method signatures are not forbidden
- infrastructure API adapter naming is not fully constrained

## Chosen Approach

Use phased hardening, not a one-shot rewrite.

### Alternative A: Minimal Fixes Only

Fix test compilation and the known `ContentTextCodec` Spring Web violation. Leave Kafka dispatch ports and application-service semantics as-is.

Tradeoff: low risk, but it leaves the largest design drift untouched.

### Alternative B: Phased Boundary Hardening

Restore the test baseline, move Web and Kafka transport concerns behind infrastructure/application-semantic ports, document allowed application-process-manager collaboration, rename misleading adapters, and extend ArchUnit guardrails.

Tradeoff: moderate work, strong risk control, and clear verification after each stage.

This is the recommended approach.

### Alternative C: Compile-Time Domain Modularization

Split core domains into separate Maven modules so cross-domain collaboration is constrained by module dependencies rather than only ArchUnit.

Tradeoff: strongest long-term boundary, but high migration cost. It should be a later program after this remediation has stabilized the current monolith.

## Target Architecture

### Layering Shape

Keep the existing DDD tactical layering:

```text
Controller / Listener / Handler / Bridge / Enqueuer / Job
  -> same-domain ApplicationService
      -> domain model / domain service / repository interface / domain event
      -> foreign owner-domain api.query / api.action / api.model
      -> contracts.event for published async contracts
          -> infrastructure implementation
```

Clarify two important interpretations:

- Inbound outbox handlers and Kafka listeners may parse transport records enough to construct same-domain application commands.
- Application services may use application-owned ports, but those ports must be named and shaped around application semantics, not broker or framework APIs.

### Web/Text Encoding Boundary

Move HTML escape/unescape out of `content.application` or hide it behind an application-semantic interface implemented outside the application layer.

Recommended target:

```text
content.application.ContentTextCodecPort
  <- content.infrastructure.text.SpringHtmlContentTextCodec
```

The application service can depend on `ContentTextCodecPort` if the write-side business policy still requires text normalization before persistence. The implementation may use `HtmlUtils` because it lives in infrastructure.

If the team decides HTML escaping is purely response rendering, move escaping to controller DTO assembly instead and keep persisted text unescaped. That is more invasive and should be a separate content behavior decision because existing code may rely on stored escaped text.

### Event Dispatch Boundary

Replace Kafka-shaped application ports with application-semantic ports.

Current shape:

```text
ApplicationService
  -> ContentEventKafkaDispatchPort.send(topic, key, event)
      -> Kafka sender adapter
```

Target shape:

```text
OutboxHandler
  -> ContentEventDispatchApplicationService.dispatch(command)
      -> ContentIntegrationEventDispatcher.dispatch(event)
          -> infrastructure Kafka adapter
```

The application command should carry only source-domain dispatch intent:

```text
DispatchContentEventCommand(
  outboxKey,
  payloadJson
)
```

The application service may validate that the outbox payload is a valid published content contract event if this validation is considered owner-domain responsibility. It must not know the Kafka topic name. Key derivation should be expressed as an event routing policy object if it is business/order-relevant; otherwise it belongs in infrastructure.

For growth task progress, avoid `Object payload` in application ports. Use a typed application command per supported dispatch path or a sealed/explicit application model:

```text
DispatchTaskProgressEventCommand(
  kind,
  sourceKey,
  payloadJson
)
```

Infrastructure maps `kind` to the configured topic.

### ApplicationService Collaboration Semantics

Keep `ApplicationService` as the public use-case boundary, but permit same-domain application process managers when the class name and dependencies make that role explicit.

Rules:

- A controller/listener/job still enters only a same-domain `*ApplicationService`.
- One `ApplicationService` may call another same-domain `ApplicationService` only when it is orchestrating a larger use case or process manager flow.
- The orchestrating class must not be a domain-named facade such as `MarketApplicationService`.
- The class name should include the process it owns, such as `MarketWalletActionProcessorApplicationService`, `MarketOrderSagaApplicationService`, or `DriveUploadRecoveryApplicationService`.
- Transactional boundaries must be explicit. A caller must not rely on proxy-based `@Transactional` behavior when invoking another method on the same instance.

Longer term, heavily reused same-domain application helpers that are not use-case entries should be renamed out of `*ApplicationService`, for example `*Workflow`, `*Coordinator`, or an application-root helper name accepted by ArchUnit. That rename should happen only after each caller is reviewed.

### Infrastructure API Adapter Naming

Rename owner API implementations that currently end in `Service` but behave as infrastructure adapters.

Examples:

- `ContentEntityQueryService` -> `ContentEntityQueryApiAdapter`
- `PostScanService` -> `PostScanQueryApiAdapter`

Rules:

- Classes implementing `api.query` or `api.action` interfaces from `..infrastructure.api..` should end with `ApiAdapter`.
- `*Service` suffix remains reserved for `ApplicationService`, `DomainService`, or framework/common infrastructure where the suffix is already well established and not confused with domain use-case entry points.

### Guardrail Extensions

Extend architecture tests after the code is cleaned:

- application classes must not import `org.springframework.web..`, `org.springframework.http..`, Servlet APIs, Kafka APIs, or `KafkaTemplate`
- application-owned ports must not have simple names containing `Kafka`, `Rabbit`, `Http`, `Redis`, `MyBatis`, or `Jdbc` unless explicitly whitelisted as a technical port with no transport-shaped method signature
- application ports must not expose `topic`, `partition`, `offset`, `key`, `ConsumerRecord`, `ProducerRecord`, `KafkaTemplate`, mapper, or dataobject types
- infrastructure API implementations under `..infrastructure.api..` that implement `..api.query..` or `..api.action..` should end with `ApiAdapter`
- transaction self-invocation checks should apply to all production `*ApplicationService` classes, not only a hard-coded subset

## Phased Implementation Plan

### Phase 0: Restore Verification Baseline

Purpose: make architecture tests executable before changing architecture.

Actions:

- Update stale `AuthControllerUnitTest` validation constant references to current `ValidationLimits` names or introduce the intended constants back if they are still part of the validation contract.
- Run test compilation and focused auth controller tests.
- Run `mvn test -pl :community-app -Dtest='*ArchTest'`.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest=AuthControllerUnitTest
mvn test -pl :community-app -Dtest='*ArchTest'
```

Exit criteria:

- `testCompile` succeeds.
- Existing architecture tests execute and their current failures, if any, are explicitly triaged before proceeding.

### Phase 1: Remove Application Web Dependency

Purpose: fix the concrete application-layer transport violation.

Actions:

- Move `ContentTextCodec` implementation using `HtmlUtils` to `content.infrastructure.text`.
- Introduce an application-owned text codec interface if needed.
- Keep write/read behavior unchanged unless a separate product decision changes stored text semantics.
- Add or update a focused test for content text escaping.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='PostPublishingApplicationServiceTest,PostReadApplicationServiceTest,DddLayeringArchTest'
```

Exit criteria:

- No `org.springframework.web..` dependency remains under `..application..`.
- Content create/read behavior remains unchanged.

### Phase 2: Reshape Event Dispatch Ports

Purpose: remove Kafka vocabulary from application ports without changing Kafka topics or published contracts.

Actions:

- Rename `*KafkaDispatchPort` interfaces to application-semantic dispatch ports.
- Remove `topic` parameters from application port methods.
- Replace `Object payload` dispatch ports with typed commands or explicit application event models.
- Move topic lookup and Kafka sender mechanics into infrastructure adapters.
- Keep outbox handlers entering same-domain application services.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,TaskProgressOutboxDispatchApplicationServiceTest'
mvn test -pl :community-app -Dtest='*ArchTest'
```

Exit criteria:

- Application port names no longer contain `Kafka`.
- Application port method signatures no longer carry topic/key unless key is documented as business ordering identity.
- Kafka sender adapters still publish to the same runtime topics.

### Phase 3: Move Outbox Parsing And Routing To Clear Boundaries

Purpose: separate transport adaptation from application validation.

Actions:

- Decide per domain whether outbox JSON parsing is infrastructure adaptation or owner-domain dispatch validation.
- If parsing stays in application, wrap it in application command/result types and remove topic concerns.
- If parsing moves to infrastructure, handlers parse payload into typed application commands and application services validate business dispatch intent.
- Standardize malformed-payload behavior: fail for retry when data corruption is possible; no-op only for unsupported event types outside the handler's responsibility.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='*OutboxHandlerTest,*EventDispatchApplicationServiceTest,*KafkaSenderAdapterTest'
mvn test -pl :community-app -Dtest='*ArchTest'
```

Exit criteria:

- Outbox handlers remain inbound adapters and call only same-domain application services.
- Application code no longer chooses Kafka topics.
- Failure behavior is covered by tests.

### Phase 4: Clarify ApplicationService Collaboration

Purpose: keep `ApplicationService` from becoming a generic service layer.

Actions:

- Inventory same-domain `ApplicationService -> ApplicationService` dependencies.
- Classify each dependency as process-manager orchestration, reusable helper, or accidental facade.
- Rename or extract reusable helpers that are not use-case boundaries.
- Add documentation in `docs/handbook/architecture.md` describing allowed application process managers.
- Extend ArchUnit rules to prevent domain-named facade application services from delegating to multiple same-domain application services.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='DddLayeringArchTest,TransactionBoundaryArchTest'
```

Exit criteria:

- Each same-domain application-service dependency has an explicit reason.
- No domain-named facade application service is introduced.
- Transaction self-invocation risks are covered broadly.

### Phase 5: Adapter Naming Cleanup

Purpose: reduce ambiguity in the codebase vocabulary.

Actions:

- Rename `ContentEntityQueryService` to `ContentEntityQueryApiAdapter`.
- Rename `PostScanService` to `PostScanQueryApiAdapter`.
- Search for other `..infrastructure.api..*Service` classes that implement published owner APIs and rename them when the role is adapter-like.
- Add an ArchUnit rule for adapter naming.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Exit criteria:

- Infrastructure owner API implementations consistently use `*ApiAdapter`.
- `*Service` suffix is not used for adapter classes that implement owner-domain APIs.

### Phase 6: Full Regression Gate

Purpose: prove the hardened architecture still supports the application behavior.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
mvn test -pl :community-app
```

If full module tests are too slow or require unavailable infrastructure, run the focused suites from earlier phases and document the skipped suites with reasons.

## Risk Management

- **Behavioral risk from text codec movement:** keep the same escape/unescape implementation initially; only move the dependency boundary.
- **Event delivery risk:** do not change topic names, event types, event ids, or payload field names in the port cleanup.
- **Outbox retry risk:** malformed payload handling must be explicit and tested; avoid swallowing source-data corruption silently.
- **Saga risk:** do not refactor market-wallet process logic and event dispatch boundaries in the same commit.
- **ArchUnit churn risk:** add stricter guardrails after code is cleaned, not before, unless the new failure list is deliberately used as the task backlog.

## Rollback Strategy

- Phase 0 is test-only and can be reverted independently.
- Phase 1 should preserve runtime behavior; rollback is restoring the original `ContentTextCodec` class if unexpected text behavior appears.
- Phases 2 and 3 must keep Kafka topics and contract classes unchanged; rollback is restoring old port interfaces and sender adapters.
- Phase 4 should be documentation/rule-heavy; rollback is disabling the new ArchUnit rule while retaining any safe renames.
- Phase 5 renames should be mechanical and can be reverted without data impact.

## Documentation Updates

When implementation proceeds, update:

- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md` if event dispatch diagrams or text encoding responsibilities are described there
- `docs/superpowers/specs/2026-06-09-kafka-event-backbone-design.md` if the final dispatch boundary changes the backbone design vocabulary
- `AGENTS.md` only if the mandatory architecture rules themselves change

## Success Criteria

- `mvn test -pl :community-app -Dtest='*ArchTest'` runs from a clean checkout and passes.
- No production application-layer class depends on Spring Web, Servlet, Kafka, MyBatis mapper, or persistence dataobject APIs.
- No application port exposes Kafka topic or sender implementation details.
- Outbox handlers and Kafka listeners call same-domain application services only.
- Published synchronous `api.*` and asynchronous `contracts.event` boundaries remain separate.
- Infrastructure owner API adapters are named as adapters, not services.
- The architecture documentation explains allowed application process-manager collaboration.

