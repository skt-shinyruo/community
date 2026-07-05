# Community App Command Non-Null Contract Design

## Context

`community-app` uses `application.command` as the input model for many same-domain use-case entry points. This is aligned with the repository's DDD tactical layering rules: inbound adapters translate transport input into application semantics, then enter a same-domain `*ApplicationService`.

However, several `*ApplicationService` command entry points currently handle `null` commands inconsistently through patterns such as:

- `if (command == null) { return; }`
- `if (command == null) { throw ... }`
- `command == null ? null : command.field()`

This weakens the meaning of `Command` as a use-case contract. A caller cannot tell whether `null` is a programmer error, a supported no-op, or a business-invalid request that should be mapped into a domain error.

The codebase currently contains 93 command-taking methods under `backend/community-app/src/main/java`. The null-tolerant patterns that matter for this design are concentrated in same-domain `*ApplicationService` classes, especially in `auth`, `analytics`, `notice`, `market`, `drive`, `content`, `user`, and `wallet`.

## Goals

- Make same-domain `*ApplicationService` command entry points treat `command` itself as non-null by contract.
- Standardize failure behavior so `null command` fails immediately at the application boundary.
- Remove null-tolerant control flow such as `command == null ? ... : ...` and `if (command == null) return` from application command entry points.
- Preserve existing field-level business validation and behavior for non-null commands.

## Non-Goals

- Do not change field-level validation semantics in this batch.
- Do not move required-field checks into command record constructors in this batch.
- Do not modify `controller`, `infrastructure`, `api adapter`, or `domain` command consumers in this batch.
- Do not add a new shared command-validation helper abstraction in this batch.
- Do not change public HTTP response shapes or error-code contracts unless a current test proves they already expose raw programmer errors.

## Current Findings

### Command Nullability Is Inconsistent

Current application entry points mix several incompatible meanings for `null command`:

- fail open / no-op
- fail with `BusinessException`
- fail with `IllegalArgumentException`
- tolerate `null` long enough to derive nullable local variables

Representative examples:

- `auth.application.LoginApplicationService.login(...)`
- `auth.application.LoginApplicationService.refresh(...)`
- `auth.application.PasswordResetApplicationService.*(...)`
- `analytics.application.AnalyticsIngestApplicationService.recordRequest(...)`
- `notice.application.NoticeProjectionApplicationService.*(...)`
- `drive.application.DriveEntryApplicationService.createFolder(...)`

This inconsistency makes the application boundary harder to reason about and weakens test expectations.

### Null-Tolerant Command Handling Hides Boundary Errors

Patterns like:

```java
String username = command == null ? null : command.username();
```

and:

```java
if (command == null) {
    return;
}
```

turn a contract violation into downstream business behavior. This can:

- delay failure until a later validation branch,
- accidentally convert a programmer error into a business-invalid input path,
- hide mistakes in tests or future callers,
- make `Command` feel optional instead of mandatory at the use-case boundary.

### The Project Already Uses Fail-Fast Contracts Elsewhere

The codebase already uses constructor- or method-level fail-fast checks in several places, especially in domain records and some application methods. This design extends that style to the application command boundary rather than introducing a new validation model.

## Chosen Approach

Use explicit fail-fast checks at same-domain `*ApplicationService` command entry points.

Recommended shape:

```java
public void someUseCase(SomeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    ...
}
```

After that guard, the method should access command fields directly and keep existing field-level validation intact.

## Alternatives Considered

### Alternative A: Explicit Entry-Point Fail-Fast

Add `Objects.requireNonNull(command, "command must not be null")` at each affected application command entry point and remove null-tolerant branching.

Pros:

- contract is obvious at the use-case boundary,
- narrow change set,
- no new abstraction,
- fits current repository style.

Cons:

- repeated one-line guard in multiple services.

This is the recommended approach.

### Alternative B: Shared Helper Such As `requireCommand(command)`

Create an application utility/helper to centralize the guard.

Pros:

- one place for the message shape.

Cons:

- adds indirection for trivial logic,
- weak payoff relative to added abstraction,
- does not match the current codebase's mostly direct fail-fast style.

### Alternative C: Record Constructor Validation Only

Move validation into command record compact constructors.

Pros:

- useful for required field invariants,
- can reduce repeated field validation later.

Cons:

- does not protect against `null` method arguments,
- broader semantic change than this batch needs.

This should remain a possible later hardening step, not the chosen implementation for this batch.

## Target Rule

For same-domain `*ApplicationService` methods whose public entry signature is `...(SomeCommand command)`:

- `command` itself is mandatory.
- The first executable statement must fail fast when `command == null`.
- The failure is treated as a programmer error, not a business-domain result.
- Existing field-level checks remain responsible for business-invalid values inside a non-null command.

This means the following patterns are disallowed in those methods:

- `if (command == null) return;`
- `if (command == null) { ... business handling ... }`
- `command == null ? null : command.field()`

## Error Semantics

`null command` is a boundary contract violation, so it should not be translated into a business error code by default.

Recommended mechanism:

```java
Objects.requireNonNull(command, "command must not be null");
```

Expected behavior:

- throw immediately,
- do not continue into business logic,
- do not silently coerce to nullable locals,
- do not treat the call as a valid no-op.

This design intentionally does not standardize every downstream exception type for field validation. It only standardizes the boundary contract for the command object itself.

## Scope Of Code Changes

Included:

- same-domain `*ApplicationService` command entry points under `backend/community-app/src/main/java`

Excluded:

- infrastructure adapters that receive command types,
- owner-domain `api` adapters,
- domain services and domain model records,
- controller request DTO validation,
- field-level command validation refactors.

## Testing Strategy

Follow focused TDD at the application-service level.

Add or update tests for representative services to prove two things:

1. passing `null` command fails immediately,
2. existing behavior for non-null commands remains unchanged.

Recommended first-wave coverage:

- `LoginApplicationService`
- `AnalyticsIngestApplicationService`
- one content or drive write-side command entry point
- one market or notice command entry point with prior `if (command == null)` behavior

After the representative tests are in place, expand coverage to the rest of the touched entry points with minimal null-contract assertions where existing behavioral coverage is already strong.

## Rollout Plan

1. Add failing tests for representative application services that currently tolerate null commands.
2. Change those entry points to explicit fail-fast guards and remove null-tolerant branching.
3. Extend the same pattern across the remaining affected same-domain `*ApplicationService` command entry points.
4. Run focused module tests for touched domains.
5. Review whether any current caller or test was depending on no-op null behavior and either delete that dependency or rewrite the caller to always construct a valid command.

## Risks

- Some existing tests may implicitly rely on null-tolerant no-op behavior and will need to be rewritten.
- A few methods may currently throw `BusinessException` for `null command`; changing them to fail-fast programmer errors is intentional but may require assertion updates.
- Because this batch does not yet move field validation into command constructors, some methods will still contain explicit field checks after the new non-null boundary guard. That duplication is acceptable in this scoped cleanup.

## Success Criteria

- Same-domain `*ApplicationService` command entry points no longer contain `if (command == null) return` or `command == null ? ... : ...` patterns.
- `null command` fails immediately at the entry boundary.
- Non-null command behavior remains stable.
- The resulting code makes the application command boundary visibly stricter and easier to reason about.
