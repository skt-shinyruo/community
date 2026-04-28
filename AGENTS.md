# Repository Instructions

These instructions apply to the whole repository.

## Mandatory Architecture Style

All backend business code in `backend/community-app` MUST use strict DDD Tactical Layering:

```text
Controller / Listener / Job
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
- `controller`, local listeners, and local jobs MUST call same-domain `*ApplicationService` only.
- `application` owns use-case orchestration, transaction boundaries, idempotency, actor/viewer conversion, command/result assembly, domain calls, domain event publication, and foreign-domain `api.*` calls.
- `domain` owns business concepts and rules. It MUST NOT depend on `controller`, `application`, `infrastructure`, MyBatis mapper/dataobject types, HTTP DTOs, or owner-domain `api.*`.
- `infrastructure` owns technical implementation details such as MyBatis mapper calls, Redis adapters, outbox adapters, and Spring event publishers.
- `api.query`, `api.action`, and `api.model` are published synchronous collaboration contracts for foreign domains. Same-domain callers MUST NOT use same-domain `api.*` as an internal entry point.
- `contracts.event` is the published asynchronous collaboration contract for foreign domains.

## Prohibited New Patterns

Do not add new code that follows any of these patterns:

- `Controller -> raw Service`
- `Controller -> UseCase`
- `Controller -> same-domain api.*`
- `ApplicationService -> MyBatis mapper`
- `Domain -> infrastructure`
- `Domain -> api.*`
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

Architecture documentation must stay aligned with this file:

- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
- `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`

When adding or changing backend architecture rules, update or add ArchUnit tests under:

```text
backend/community-app/src/test/java/com/nowcoder/community/app/arch
```
