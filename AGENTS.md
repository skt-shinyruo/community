# Repository Instructions

These instructions apply to the whole repository.

## Mandatory Architecture Style

All backend business code in `backend/community-app` MUST use strict DDD Tactical Layering:

```text
Controller / Listener / Handler / Bridge / Enqueuer / Job
  -> ApplicationService
      -> Domain model / DomainService / Repository interface / Domain event
      -> foreign owner-domain api.query / api.action / api.model when cross-domain synchronous collaboration is required
      -> contracts.event when cross-domain asynchronous collaboration is required
          -> Infrastructure implementation
```

The required package shape for each business domain is:

```text
com.nowcoder.community.<domain>
  controller          # inbound HTTP adapter
  application         # use-case orchestration
    command
    result
  domain              # business model, rules, repository interfaces, domain events
    model
    service
    repository
    event
  infrastructure      # MyBatis, Redis, MQ, Spring event, outbox adapters
    persistence
      mapper
      dataobject
    event
  api                 # published synchronous contracts for foreign domains only
    query
    action
    model
  contracts           # published asynchronous contracts for foreign domains only
    event
```

## Layer Rules

- `controller` only handles HTTP binding, authentication extraction, validation handoff, and DTO conversion.
- Inbound adapters include controllers, local event listeners, outbox handlers, event bridges, enqueuers, and scheduled jobs.
- Inbound adapters adapt input and MUST call same-domain `*ApplicationService` only.
- Inbound adapters MUST NOT perform foreign owner-domain `api.*`, foreign `application.*`, same-domain application helper/port, domain model/service/repository, infrastructure, persistence, mapper, or dataobject collaboration before entering the same-domain application boundary.
- `application` owns use-case orchestration, transaction boundaries, idempotency, actor/viewer conversion, command/result assembly, domain calls, domain event publication, and foreign-domain `api.*` calls.
- `application.command`, `application.result`, and application-owned ports express application semantics only. They MUST NOT expose HTTP transport types such as `ResponseEntity`, `ResponseCookie`, `Resource`, `MediaType`, Servlet request/response types, or Spring Web upload types such as `MultipartFile`.
- `application` MUST NOT depend directly on MyBatis mapper or dataobject types. Persistence goes through domain repository interfaces or explicit infrastructure ports.
- `domain` owns business concepts and rules. It MUST NOT depend on `controller`, `application`, `infrastructure`, MyBatis mapper/dataobject types, HTTP DTOs, Spring framework, or owner-domain `api.*`.
- `domain` MUST NOT perform cross-domain orchestration or treat external API/event contracts as internal domain models.
- `infrastructure` owns technical implementation details such as MyBatis mapper calls, Redis adapters, outbox adapters, and Spring event publishers.
- `infrastructure` may implement domain repository interfaces or application-owned technical ports, but MUST NOT leak mapper/dataobject types into the domain.
- `api.query`, `api.action`, and `api.model` are published synchronous collaboration contracts for foreign domains. Same-domain callers MUST NOT use same-domain `api.*` as an internal entry point.
- `contracts.event` is the published asynchronous collaboration contract for foreign domains.
- Synchronous `api.*` contracts MUST NOT import, return, or receive `contracts.event` types. If synchronous and asynchronous payloads share fields, define separate `api.model` and `contracts.event` models.

## Cross-Domain Collaboration

Synchronous cross-domain collaboration MUST follow this shape:

```text
caller ApplicationService
  -> owner-domain api.query / api.action
  -> owner ApplicationService / adapter
  -> owner domain
```

Asynchronous cross-domain collaboration MUST follow this shape:

```text
owner domain event
  -> infrastructure event adapter
  -> owner contracts.event
  -> listener / outbox handler
  -> consumer ApplicationService
```

Do not use these as cross-domain entry points:

- `domain`
- `infrastructure`
- MyBatis mapper / dataobject
- root legacy `service`
- root legacy `entity`
- root legacy `mapper`
- producer-domain internal event implementation

## Prohibited New Patterns

Do not add new code that follows any of these patterns:

- `Controller -> raw Service`
- `Controller -> UseCase`
- `Controller -> same-domain api.*`
- `Controller / Listener / Handler / Bridge / Enqueuer / Job -> foreign api.*`
- `Controller / Listener / Handler / Bridge / Enqueuer / Job -> foreign application.*`
- `Controller / Listener / Handler / Bridge / Enqueuer / Job -> domain repository/service/model`
- `Controller / Listener / Handler / Bridge / Enqueuer / Job -> mapper/dataobject/persistence`
- `ApplicationService -> MyBatis mapper`
- `ApplicationService -> HTTP transport type`
- `Domain -> infrastructure`
- `Domain -> api.*`
- `api.* -> contracts.event`
- `UseCase + ApplicationService` as two competing use-case entry styles
- `CommandService`, `ActionService`, or `FacadeService` as application entry naming
- `app/query`, `app/command`, or new `*UseCase` packages

Existing legacy packages such as `service`, `entity`, `mapper`, and `app` are migration-only surfaces. When touching affected code, move it toward the mandatory DDD Tactical Layering shape instead of extending the legacy style.

## Naming

- Same-domain use-case entry: `*ApplicationService` in the `application` package.
- Domain rule that does not naturally belong to one entity: `*DomainService` or `*Policy` in the `domain` package.
- Domain persistence contract: `*Repository` interface in `domain.repository`.
- MyBatis implementation: `MyBatis*Repository` in `infrastructure.persistence`.
- Persistence row object: `*DataObject` in `infrastructure.persistence.dataobject`.

## Documentation And Guardrails

Project-related documentation MUST live under:

- `docs/handbook`

Specs and implementation plans MUST live under:

- `docs/superpowers/specs`
- `docs/superpowers/plans`

Architecture documentation must stay aligned with this file:

- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md`
- `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`
- `docs/superpowers/specs/2026-05-02-community-app-ddd-boundary-hardening-design.md`

When adding or changing backend architecture rules, update or add ArchUnit tests under:

```text
backend/community-app/src/test/java/com/nowcoder/community/app/arch
```

The active architecture guardrails include:

- `DddLayeringArchTest`
- `ControllerBoundaryArchTest`
- `DomainBoundaryArchTest`
- `DtoBoundaryArchTest`
- `InfraBoundaryArchTest`
- `ListenerBoundaryArchTest`
- `TransactionBoundaryArchTest`

After changing backend architecture rules or package boundaries, run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```
