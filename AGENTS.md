# Repository Instructions

These instructions apply to the whole repository.

## Architecture Style

Backend business code in `backend/community-app` uses lightweight domain layering. The goal is to keep ownership,
transaction, and infrastructure boundaries explicit without requiring one file for every tactical DDD role.

Use the smallest flow that preserves the required boundary:

```text
simple query: Controller -> ApplicationService -> query port / Repository
local write: Controller -> ApplicationService -> Domain model / DomainService / Repository
synchronous cross-domain: caller ApplicationService -> owner api -> owner ApplicationService
durable asynchronous: owner ApplicationService -> contracts.event + outbox -> listener -> consumer ApplicationService
```

The available package shape for a business domain is shown below. Packages and types are created only when the
responsibility exists; empty layers, standalone command/result files, domain events, and adapters are not mandatory.

```text
com.nowcoder.community.<domain>
  controller          # inbound HTTP adapter
  application         # use-case orchestration
    command           # optional; use nested records for use-case-local values
    result            # optional; use nested records for use-case-local values
  domain              # business model, rules, repository interfaces, domain events
    model
    service
    repository
    event             # optional internal domain events
  infrastructure      # MyBatis, Redis, MQ, Spring event, outbox adapters
    persistence
      mapper
      dataobject
    event             # broker, outbox, or local-event adapters
  api                 # published synchronous contracts for foreign domains only
    query
    action
    model             # optional; API-local request/result may be nested in the API
  contracts           # published asynchronous contracts for foreign domains only
    event
```

## Layer Rules

- `controller` only handles HTTP binding, authentication extraction, validation handoff, and DTO conversion when the
  transport shape differs. It MAY return a transport-safe application result directly when the shapes and semantics match.
- Inbound adapters include controllers, local event listeners, outbox handlers, event bridges, enqueuers, and scheduled jobs.
- Controllers call a same-domain `*ApplicationService`. Other inbound adapters call one public same-domain application entry; this is normally an `*ApplicationService`, but a focused application `*Handler`, `*Scheduler`, or `*Publisher` is allowed when it owns real application semantics rather than forwarding calls.
- Inbound adapters MUST NOT perform foreign owner-domain `api.*`, foreign `application.*`, domain model/service/repository, infrastructure, persistence, mapper, or dataobject collaboration before entering the same-domain application boundary.
- `application` owns use-case orchestration, transaction boundaries, idempotency, actor/viewer conversion, command/result assembly, domain calls, domain event publication, and foreign-domain `api.*` calls.
- Application entry classes MAY implement their own domain's published `api.query` / `api.action` interface directly. Add an infrastructure API adapter only when protocol or model conversion is substantive.
- `infrastructure.api` is a reviewed exception surface. A new adapter MUST document the conversion or policy it owns and update the reviewed adapter guard in `InfraBoundaryArchTest`; delegation alone is not sufficient.
- Commands and results that belong to one use case SHOULD be nested records in the application entry or owner API. Use standalone `application.command` / `application.result` types only when they are reused, independently meaningful, or large enough to improve readability.
- Application values and application-owned ports express application semantics only. They MUST NOT expose HTTP transport types such as `ResponseEntity`, `ResponseCookie`, `Resource`, `MediaType`, Servlet request/response types, or Spring Web upload types such as `MultipartFile`.
- `application` MUST NOT depend directly on MyBatis mapper or dataobject types. Persistence goes through domain repository interfaces or explicit infrastructure ports.
- Simple read models MAY use an application-owned query port and return application results directly; a domain model is not required when no domain rule is involved.
- `domain` owns business concepts and rules. It MUST NOT depend on `controller`, `application`, `infrastructure`, MyBatis mapper/dataobject types, HTTP DTOs, Spring framework, or owner-domain `api.*`.
- `domain` MUST NOT perform cross-domain orchestration or treat external API/event contracts as internal domain models.
- `infrastructure` owns technical implementation details such as MyBatis mapper calls, Redis adapters, outbox adapters, and broker clients.
- `infrastructure` may implement domain repository interfaces or application-owned technical ports, but MUST NOT leak mapper/dataobject types into the domain.
- `api.query` and `api.action` are published synchronous entry contracts for foreign domains. Same-domain callers MUST NOT inject or call them as internal entry points. An `api.model` MAY also be returned by the same-domain ApplicationService/controller path when its semantics and lifecycle are identical; do not create a mirrored application result only for package purity.
- `contracts.event` is the published asynchronous collaboration contract for foreign domains.
- Synchronous `api.*` contracts MUST NOT import, return, or receive `contracts.event` types. If synchronous and asynchronous payloads share fields, define separate `api.model` and `contracts.event` models.
- A domain event and local Spring event bridge are optional. For one durable external reaction, the owner ApplicationService SHOULD write the contract event through an outbox port in the same transaction. Use a local domain event only when there are independent local subscribers.

## Cross-Domain Collaboration

Synchronous cross-domain collaboration MUST follow this shape:

```text
caller ApplicationService
  -> owner-domain api.query / api.action
  -> owner ApplicationService implementing the API, or a substantive adapter
  -> owner domain
```

Asynchronous cross-domain collaboration MUST follow this shape:

```text
owner ApplicationService
  -> owner contracts.event + outbox
  -> outbox handler
  -> broker
  -> listener
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
- pass-through API adapters, event bridges, or ApplicationServices that exist only to satisfy a naming rule
- mirrored command/result/API models with identical semantics and no independent evolution boundary

Existing legacy packages such as `service`, `entity`, `mapper`, and `app` are migration-only surfaces. When touching affected code, move it toward the lightweight domain layering boundaries instead of extending the legacy style.

## Naming

- Same-domain use-case entry: `*ApplicationService` in the `application` package. Focused application helpers use their actual role, such as `*Assembler`, `*Scheduler`, or `*Publisher`.
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
- `docs/superpowers/specs/2026-07-27-community-app-lightweight-domain-layering-design.md`

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
