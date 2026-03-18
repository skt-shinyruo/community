# Spring Boot Layered Refactor Design

## Background

The current backend codebase mixes a modular-monolith style with contract-first boundaries:

- `backend/community-bootstrap` uses domain top-level packages such as `auth`, `user`, `content`, `social`, `message`, `search`, and `analytics`
- cross-domain synchronous calls inside the same JVM frequently go through `contracts.internal.api`, `*Access`, `*ApiImpl`, and `ModuleCallSupport`
- `application` packages act as orchestration layers above `service`
- shared concerns live under `com.nowcoder.community.contracts.*`
- the IM stack uses three modules: `im-contracts`, `im-core`, and `im-realtime`

This structure is workable, but it is not the desired style for this project. The target is a more traditional Spring Boot code organization: keep top-level business domains, but use classic layering inside each domain and stop modeling in-process calls as if they were service contracts.

## Goal

Refactor the project so that:

- top-level business domains remain visible
- each domain internally follows a classic Spring Boot layering style
- single-process module interactions use direct service-to-service calls
- shared cross-cutting code moves to a conventional `common` package
- IM keeps only the minimum shared module required by real cross-process boundaries

The refactor does not need to preserve existing external protocols verbatim. Functional correctness is the primary requirement.

## Non-Goals

- preserve current HTTP JSON shapes, Kafka topic names, or internal DTO names exactly
- preserve the existing package names if they no longer fit the target style
- keep the current contract-first organization for in-process code paths
- turn the entire repository into a single global `controller/service/mapper/entity/dto` tree without business-domain top-level packages

## Target Architecture

### Community Bootstrap

`backend/community-bootstrap` will keep domain-first top-level packages, but each domain will adopt classic Spring layering internally.

Target shape:

```text
com.nowcoder.community
  common
    config
    constants
    dto
    exception
    util
    web
    security
    trace
  auth
    controller
    service
    mapper
    entity
    dto
    config
  user
    controller
    service
    mapper
    entity
    dto
    config
  content
    controller
    service
    mapper
    entity
    dto
    config
  social
    controller
    service
    mapper
    entity
    dto
    config
  message
    controller
    service
    mapper
    entity
    dto
    config
  search
    controller
    service
    mapper
    entity
    dto
    config
  analytics
    controller
    service
    mapper
    entity
    dto
    config
  ops
    controller
    service
    dto
```

### IM Modules

The IM stack keeps separate deployable/runtime modules, but each module also shifts toward a classic Spring organization:

- `im-core`: `controller`, `service`, `repository` or `mapper`, `entity`, `dto`, `config`, `kafka`
- `im-realtime`: `ws`, `service`, `client`, `dto`, `config`, `kafka`, `security`
- `im-contracts` is no longer treated as the architectural center; it should be reduced to a normal shared module and renamed to `im-common`

`im-common` should contain only truly shared cross-process artifacts:

- Kafka topic constants
- Kafka command/event DTOs
- small shared enums or utility types required by both `im-core` and `im-realtime`

HTTP-specific DTOs or module-local helper types should move back into the owning module.

## Core Design Decisions

### 1. Remove in-process contract layering inside the monolith

The single JVM backend should stop using internal contract interfaces as the default collaboration mechanism.

The following patterns are considered legacy and should be removed from `community-bootstrap` mainline code:

- `contracts.internal.api.*`
- `*ApiImpl` used only to satisfy internal contracts
- `*Access` wrapper services whose main purpose is adapting internal calls
- `ModuleCallSupport` for ordinary in-process calls

Replacement model:

- one domain service may inject another domain service directly
- service methods return normal objects, DTOs, collections, primitives, or `void`
- service failures are expressed with exceptions, not `Result<T>`
- metrics, fallback, and policy decisions move into explicit business services instead of generic wrappers

### 2. Remove `application` as a core layer

`application` is not part of the target style.

Migration rule:

- orchestration logic that is clearly business logic moves into `service`
- HTTP-only assembly logic moves closer to `controller` or dedicated DTO assemblers if necessary
- classes such as `*ApplicationService` should either disappear or become normal domain service classes with names that reflect the business responsibility

The goal is not mechanical renaming. Responsibilities should be collapsed into a simpler, more conventional service layer.

### 3. Replace `contracts` with `common` for shared cross-cutting code

The current `contracts` package mixes several unrelated categories:

- web response types and error abstractions
- input validation limits and shared constants
- trace headers and trace utility code
- event envelope utilities
- in-process internal APIs and DTOs

Target split:

- `common.exception`: `BusinessException`, `ErrorCode`, common error enums
- `common.web` or `common.dto`: HTTP response wrapper types if still retained
- `common.constants`: shared constants such as entity type values and validation limits
- `common.trace`: trace headers and trace helpers
- `common.event`: shared event envelope utilities if still needed by the monolith

`contracts.internal.*` must be deleted as part of the refactor.

### 4. Separate HTTP response shape from internal service collaboration

If a unified HTTP response wrapper is kept, it belongs to the web layer only.

Rules:

- controllers may return a common response body type
- services must not use `Result<T>` as an internal collaboration protocol
- the global exception handler becomes the single place where exceptions are converted to HTTP responses

This keeps the service layer clean and makes controller/service boundaries look like conventional Spring Boot code.

### 5. Keep real distributed boundaries only where they actually exist

The IM stack is the only place in scope where cross-process shared DTOs remain structurally necessary.

For IM:

- shared Kafka DTOs remain in the shared module
- runtime-local logic and local HTTP DTOs remain in their owning modules
- the repository should stop treating shared contracts as the dominant architectural abstraction

This avoids over-applying single-process refactor rules to truly distributed code.

## Dependency Rules After Refactor

### Allowed

- controller -> service
- service -> mapper or repository
- service -> service in another business domain
- controller or service -> `common.*`
- IM modules -> `im-common` for genuine shared artifacts

### Disallowed

- controller -> controller
- mapper or repository -> service
- one domain directly manipulating another domain's mapper as the normal integration path
- in-process service -> `Result<T>` or internal API wrapper
- new `application` or `contracts.internal` style packages

### Strong Recommendations

- avoid Spring circular dependencies even if technically resolvable
- keep cross-domain service calls business-focused, not data-leaking
- do not expose another domain's persistence layer as a shortcut for integration

## Migration Strategy

This refactor must be staged so the repository stays buildable and testable.

### Phase 1: Establish new shared package destinations

- create `common` package structure
- move or copy cross-cutting types out of `contracts` to their new homes
- update imports in consumers
- keep compatibility shims only if required briefly for compilation

This phase creates the landing zone for the new architecture.

### Phase 2: Migrate monolith-wide cross-cutting code

Move single-process shared code out of `contracts`:

- errors and business exceptions -> `common.exception`
- validation and entity constants -> `common.constants`
- trace helpers -> `common.trace`
- shared HTTP response wrapper if retained -> `common.web` or `common.dto`

After this phase, `contracts` should no longer be the default import source for ordinary monolith code.

### Phase 3: Replace internal call wrappers in `community-bootstrap`

Identify and migrate all chains built on:

- internal API interfaces
- `*ApiImpl`
- `*Access`
- `ModuleCallSupport`

Convert them to direct service injection.

Examples currently present include:

- `auth -> user`
- `social -> content`
- `content -> user`
- `message -> user`

Each path should be migrated fully, not halfway. The end state for a path is:

- no internal API interface
- no `Result<T>` transport
- no wrapper adapter
- direct service call

### Phase 4: Collapse `application` into `service`

Refactor domain by domain:

- move business orchestration into conventional services
- move DTO assembly to controllers or dedicated mapper/assembler helpers if needed
- update tests to target the new service/controller structure
- delete obsolete `application` classes

### Phase 5: Refactor IM modules

For `im-core` and `im-realtime`:

- normalize internal package layout toward classic Spring organization
- keep Kafka-related code where appropriate, but do not let shared contracts dictate local structure

For `im-contracts`:

- rename it to `im-common`
- retain only real shared process-boundary artifacts
- migrate or delete anything that is merely module-local but currently shared for convenience

### Phase 6: Remove dead architectural scaffolding and update docs

Delete remaining obsolete infrastructure:

- `contracts.internal.*`
- `application` packages
- `ModuleCallSupport` and related tests if no longer used
- adapter classes that only existed for the previous architecture

Then update architecture and system design documents so they describe the new default style rather than the old one.

## Testing Strategy

The refactor is structural, so verification must be both behavioral and architectural.

### Behavioral verification

The following functional areas must continue to work:

- auth flows: login, register, refresh, logout, password and activation paths
- user management and avatar/file flows
- content flows: posts, comments, moderation, report handling
- social flows: likes, follows, blocks
- message and notice flows
- search and ops flows
- IM flows: private send, room send, room membership, realtime push, Kafka consumption

### Structural verification

The following conditions should be asserted by repository inspection and build/test success:

- no remaining single-process use of `contracts.internal.api`
- no remaining monolith use of `ModuleCallSupport` for ordinary intra-process business calls
- no remaining `application` package as an architectural layer
- no service-to-service internal usage of `Result<T>`
- no new package additions that recreate the old style under different names

### Test suite migration

Tests should be moved with the architecture:

- former `application` tests become `service` or controller tests
- internal API tests become direct service tests
- `ModuleCallSupport` tests are removed or replaced by business-specific service tests
- IM shared DTO serialization tests stay with the shared IM module

## Risks and Mitigations

### Risk 1: Circular dependencies after direct service injection

Moving from internal APIs to direct services can create Spring dependency cycles.

Mitigation:

- refactor by business path, not only by package
- extract focused services when two domains begin to depend on broad service classes
- keep service responsibilities narrow enough to avoid reciprocal wiring

### Risk 2: HTTP behavior drift when `Result<T>` stops being an internal protocol

The old structure mixes HTTP response semantics with in-process calls.

Mitigation:

- centralize HTTP mapping in the global exception handler and controller response path
- add or update controller integration tests for representative 4xx and 5xx cases

### Risk 3: Large-batch refactor leaves repository broken for too long

Mitigation:

- complete the refactor in staged, compilable increments
- run affected module tests after every stage
- do not combine package migration, semantic rewrites, and doc cleanup into one unverified batch

### Risk 4: IM shared module is over-deleted or under-deleted

Mitigation:

- keep only true cross-process Kafka artifacts shared
- move HTTP/local DTOs back to their owning runtime modules
- verify both `im-core` and `im-realtime` test suites after each IM stage

## Acceptance Criteria

The refactor is complete only when all of the following are true.

### Structure

- `community-bootstrap` uses business-domain top-level packages with classic Spring layering inside each domain
- `application` is gone as a primary architectural layer
- `contracts.internal.*` is gone
- `common.*` is the default home for cross-cutting shared code inside `community-bootstrap`
- `im-contracts` has been reduced to a minimal shared module and renamed to `im-common`

### Dependency behavior

- single-process domain interactions use direct service calls
- service methods do not return `Result<T>` as an internal transport type
- controllers do not call each other
- mappers or repositories are not used as cross-domain integration shortcuts

### Functional behavior

- major business flows continue working end to end
- trace, security, idempotency, and global error handling still function
- IM Kafka and realtime flows still function

### Documentation

- `docs/ARCHITECTURE.md` reflects the layered Spring style rather than the modular-contract style
- `docs/SYSTEM_DESIGN.md` reflects direct service collaboration for in-process paths
- IM-related descriptions no longer present `im-contracts` as the architectural center

### Team-facing coding style

A new feature should now be naturally implemented by:

- adding or updating a controller
- adding or updating services
- using mapper or repository for persistence
- defining DTOs in the owning domain
- using `common` for cross-cutting code

Developers should no longer default to creating:

- `application` services
- internal contract APIs for in-process calls
- `*Access` wrappers
- `*ApiImpl` adapters

## Recommended Execution Order

1. Introduce `common` and migrate cross-cutting types from `contracts`
2. Replace `community-bootstrap` internal API call paths with direct service calls
3. Collapse `application` into `service`
4. Refactor IM modules and rename `im-contracts` to `im-common`
5. Remove dead code and obsolete scaffolding
6. Update docs and finish verification

## Expected Outcome

After this refactor, the repository should read like a conventional Spring Boot codebase with clear business-domain top-level packages, rather than a modular-monolith codebase organized around internal contracts and orchestration layers.
