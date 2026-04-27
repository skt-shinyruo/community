# Community App Strict DDD Tactical Layering Design

**Date:** 2026-04-27
**Status:** Approved
**Owner:** Codex

---

## 1. Decision

`backend/community-app` MUST converge on strict DDD Tactical Layering:

```text
Controller / Listener / Job
  -> ApplicationService
      -> Domain model / DomainService / Repository interface / Domain event
      -> foreign owner-domain api.query / api.action / api.model
      -> contracts.event
          -> Infrastructure implementation
```

This replaces the previous mixed style where `ApplicationService`, raw `Service`, `UseCase`, `app.*`, and owner-domain `api.*` could all become competing entry points.

The chosen style is not optional for new backend business code.

---

## 2. Required Package Shape

Each business domain should converge to:

```text
com.nowcoder.community.<domain>
  controller
  application
    command
    result
  domain
    model
    service
    repository
    event
  infrastructure
    persistence
      mapper
      dataobject
    event
  api
    query
    action
    model
  contracts
    event
```

`service`, `entity`, `mapper`, and `app` packages that already exist are legacy migration surfaces. They may remain temporarily, but touched code should move toward the required package shape instead of extending those packages as the long-term style.

---

## 3. Layer Responsibilities

### 3.1 Controller / Listener / Job

Inbound adapters only:

- bind HTTP, scheduler, or local event input
- extract authentication context
- map request DTOs into application commands
- map application results into transport responses
- call same-domain `*ApplicationService`

They must not directly call same-domain domain objects, repositories, infrastructure, mapper/dataobject types, raw services, `UseCase`, or same-domain `api.*`.

### 3.2 Application

`*ApplicationService` is the use-case entry.

It owns:

- use-case orchestration
- transaction boundary
- idempotency
- actor/viewer conversion
- command/result assembly
- domain object loading and saving through repository interfaces
- domain service calls
- domain event publication
- foreign-domain synchronous calls through owner-domain `api.*`

It must not depend directly on MyBatis mapper/dataobject types or HTTP DTOs.

### 3.3 Domain

The domain layer owns business concepts and rules:

- aggregate/model behavior
- policies
- domain services
- value objects
- repository interfaces
- domain events

It must not depend on Spring Web, controller DTOs, application services, infrastructure implementations, mapper/dataobject types, or owner-domain `api.*`.

### 3.4 Infrastructure

Infrastructure implements technical details:

- MyBatis mapper calls
- repository implementations
- Redis adapters
- Spring event publisher adapters
- outbox adapters
- external transport adapters

Infrastructure may depend on domain contracts to implement them. Domain must not depend on infrastructure.

### 3.5 API And Contracts

`api.query`, `api.action`, and `api.model` are published synchronous collaboration contracts for foreign domains only.

`contracts.event` is the published asynchronous collaboration contract for foreign domains.

Same-domain callers must use same-domain `ApplicationService`, not same-domain `api.*`.

---

## 4. Naming Rules

Allowed long-term entry and domain names:

- `*ApplicationService`
- `*DomainService`
- `*Policy`
- `*Repository`
- `MyBatis*Repository`
- `*DataObject`
- `*Command`
- `*Result`

Disallowed new application-entry names:

- `*UseCase`
- `*CommandService`
- `*ActionService`
- `*FacadeService`
- `app.query.*`
- `app.command.*`

`ApplicationService` is the use case. Do not create a parallel `UseCase` layer next to it.

---

## 5. Migration Rule

When touching a domain:

1. Identify or create the same-domain `*ApplicationService`.
2. Move inbound adapters to that `ApplicationService`.
3. Move business rules into `domain.model`, `domain.service`, or `domain.*Policy`.
4. Introduce `domain.repository` interfaces when persistence is part of the use case.
5. Move MyBatis mapper/dataobject usage behind `infrastructure.persistence`.
6. Keep foreign-domain collaboration on owner-domain `api.*` or `contracts.event`.
7. Delete or shrink legacy `UseCase`, raw `Service`, `app`, `entity`, and `mapper` surfaces as the touched slice converges.

Do not add new legacy-style code just because adjacent legacy code still exists.

---

## 6. Enforcement

The repository root `AGENTS.md` is the short operational rulebook for agents and developers.

Architecture docs must stay aligned:

- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`

ArchUnit tests under `backend/community-app/src/test/java/com/nowcoder/community/app/arch` should be expanded as the migration proceeds so these rules become executable guardrails.

