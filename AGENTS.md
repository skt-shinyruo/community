# Repository Instructions

These instructions apply to the whole repository.

## Repository Map And Sources Of Truth

- `backend/` is the Java 17 / Spring Boot 3 Maven reactor. Run backend build and test commands from this directory.
- `frontend/` is the Vue 3 / Vite SPA. Run frontend npm commands from this directory.
- `deploy/` owns the supported local Compose topologies, schema snapshots, forward migrations, Nacos seeds,
  observability assets, and deployment contract tests.
- `tools/mock-data-studio/`, `tests/k6/`, and `tests/playwright-single/` are independently tested Node-based tools or
  suites; do not assume the frontend dependency installation covers them.
- `docs/handbook` is the source of truth for current project behavior. Root and subproject READMEs are concise entry
  points, while `docs/superpowers/specs` and `docs/superpowers/plans` provide design history unless a document is
  explicitly identified below as an active architecture contract.
- Before changing behavior, read the owning module, its tests, and the relevant handbook page. Do not use an old spec
  or plan to override current code and handbook behavior without explicitly reconciling them.

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
- Controllers call a same-domain `*ApplicationService`. A same-domain application `*Query` interface is also allowed when it is a pure read contract: no business rule, cross-domain orchestration, write transaction, idempotency, or transport type, and it returns a transport-free read model. Other inbound adapters call one public same-domain application entry; this is normally an `*ApplicationService`, but a focused application `*Handler`, `*Scheduler`, or `*Publisher` is allowed when it owns real application semantics rather than forwarding calls.
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

## Frontend Boundaries

- Browser traffic uses the gateway for `/api`, `/files`, and `/ws/im`; do not make views or stores depend on internal
  backend service addresses.
- Route guards are an experience boundary, not an authorization boundary. Backend authorization remains authoritative.
- Access tokens stay in Pinia memory and refresh tokens stay in HttpOnly cookies. Do not copy either credential into
  JavaScript-readable persistent storage.
- Main-site HTTP calls use `frontend/src/api/http.js`; IM HTTP calls use `frontend/src/api/imCoreHttp.js`. Preserve the
  shared Result, refresh, endpoint-resolution, and error semantics instead of creating page-local clients.
- Resolve API and WebSocket endpoints through the existing runtime config and endpoint helpers. IM WebSocket clients
  obtain `wsUrl` and a ticket from `POST /api/im/sessions`; do not hard-code an IM worker address.
- Keep complex page state in focused `frontend/src/views/*State.js` modules with colocated tests. Components should own
  rendering and interaction, not duplicate transport or session policy.
- A retry of the same high-risk write attempt must reuse its `Idempotency-Key`; generating a new key changes the business
  attempt.

## Data And Deployment Rules

- Use `./deploy/deployment.sh` as the supported Compose entry point. Do not document or automate a partial direct
  `docker compose` invocation unless the deploy tooling itself requires it.
- `single` is the normal development topology; `cluster` is for multi-instance and cluster-path validation.
  Observability is enabled by default and is disabled with `--no-observability`.
- Never commit real secrets or local `deploy/.env*` files. Nacos config seeds contain non-secret configuration only;
  credentials and signing keys stay in env files or a secret manager.
- The fixed business schemas are `community`, `community_oss`, and `im_core`. Empty-volume current state lives in
  `deploy/mysql/primary-init/010_current_schema.sql`.
- A `community` schema change updates the current-state snapshot and appends a new
  `deploy/mysql/community-migrations/VNNN__*.sql` forward migration. It also updates the applicable H2/MyBatis fixtures,
  schema contracts, and `docs/handbook/data-and-storage.md`. Never rewrite an already released migration.
- Treat `reset-mysql` and `docker compose down -v` as destructive. Run them only when the task explicitly requires data
  removal and the exact topology/project has been confirmed.

## Change And Verification Workflow

- Keep changes scoped to the owning module and existing boundaries. Do not mix opportunistic refactors into a focused
  fix or documentation update.
- Add regression coverage for behavior changes. Scale verification to the affected surface; do not substitute compilation
  for behavior tests when a focused test exists.
- Backend changes: run focused module tests first, then `cd backend && mvn test` when shared contracts, runtime wiring, or
  multiple modules are affected.
- Frontend changes: run focused Vitest files when possible, then `cd frontend && npm test && npm run build` for shared
  routing, session, API, or production-build changes.
- Architecture or package-boundary changes: run the ArchUnit command in the guardrail section below in addition to the
  affected backend tests.
- Deployment, schema, Nacos, observability, k6, Playwright, and Mock Data Studio changes use the matching contract or test
  suites documented in `docs/handbook/testing.md` and in the owning README.
- Documentation-only changes must at least pass
  `git diff --check -- AGENTS.md README.md docs frontend/README.md backend/README.md deploy/README.md tools`.
- Do not run destructive validation against a developer's local topology. Prefer render, static contract, unit, and
  disposable-container checks unless destructive behavior is the explicit subject of the task.

## Documentation And Guardrails

Long-lived project documentation MUST live under:

- `docs/handbook`

Root and subproject READMEs remain navigational and operational entry points; they MUST link to handbook detail instead
of becoming competing sources of truth. Update `README.md` when top-level modules, prerequisites, canonical startup
commands, default ports, or primary document entry points change.

Specs and implementation plans MUST live under:

- `docs/superpowers/specs`
- `docs/superpowers/plans`

Architecture documentation must stay aligned with this file:

- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md`
- `docs/superpowers/specs/2026-07-27-community-app-lightweight-domain-layering-design.md`

Behavioral documentation must stay aligned with the owning code:

- Business workflows: `docs/handbook/business-logic`, `docs/handbook/business-flows.md`, and
  `docs/handbook/core-logic-index.md`
- HTTP, synchronous API, and asynchronous event contracts: `docs/handbook/integration-contracts.md`
- Frontend routes, session, endpoint, HTTP, realtime, and page-state behavior: `docs/handbook/frontend.md`
- Security, reliability, storage, observability, operations, local development, and tests: their matching pages under
  `docs/handbook`

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
