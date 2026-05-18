# Backend Architecture Governance Design

## Context

The backend is a multi-module Spring Boot system. `community-app` is a flattened monolith with strict DDD tactical layering enforced by ArchUnit. The current architecture mostly follows the intended boundaries, but several issues weaken enforcement or leave bypass paths:

- `InfraBoundaryArchTest` checks `..infra..` but most domain infrastructure packages use `..infrastructure..`.
- IM projection adapters live under `im.projection` instead of the canonical inbound/infrastructure package shape.
- Some content synchronous API implementations under `content.infrastructure.api` directly use MyBatis mappers instead of entering the owner application boundary.
- Some touched content models still expose persistence-shaped state instead of small domain behaviors.

## Goals

- Strengthen architecture guardrails without changing external API behavior.
- Move IM projection code toward the mandated DDD package shape.
- Ensure content owner API adapters delegate through `Content` application services before domain or persistence collaboration.
- Improve only the domain model behavior directly touched by this work; avoid broad model rewrites.

## Non-Goals

- Do not split `community-app` into separate services.
- Do not rewrite all domain models.
- Do not change HTTP endpoints, Kafka topics, API contracts, or persistence schemas.
- Do not introduce backward-compatibility shims unless tests or existing runtime wiring require them.

## Design

### Architecture Guardrails

Update ArchUnit coverage so infrastructure rules apply to both shared `infra` and domain `infrastructure` packages. Extend inbound adapter coverage to include the new IM projection package shape after moving code.

### IM Package Shape

Move IM HTTP projection controller to `com.nowcoder.community.im.controller`.

Move IM outbox/listener/publisher technical adapters to `com.nowcoder.community.im.infrastructure.event`.

Keep `ImPolicySnapshotApplicationService` as the application boundary. If it currently depends on an IM projection helper that calls foreign APIs, move that helper behind the application boundary or inline the orchestration into the application service so foreign API calls happen from application code.

### Content API Adapters

Keep public interfaces such as `PostScanQueryApi` and `ContentEntityQueryApi` unchanged.

Change their infrastructure adapters so they call same-domain content `*ApplicationService` methods. Add minimal application service methods where needed for post scanning and content entity resolution. Mapper and repository collaboration remains behind application/domain boundaries.

### Domain Model Improvements

For the content model paths touched by API adapter refactoring, add small behavior methods only when they remove duplicated status/null checks or clarify invariants. Do not perform a full `DiscussPost` or `Comment` rewrite.

## Testing

- Run `mvn test -pl :community-app -Dtest='*ArchTest'` from `backend`.
- Run focused `community-app` tests that cover changed content and IM classes if present.
- If no focused tests exist, rely on architecture tests plus compile/test execution for affected module scope.

## Risks

- Tightening ArchUnit may reveal existing violations that need small follow-up moves.
- Moving packages can break component scanning or tests if references are package-specific.
- Refactoring API adapters through application services can expose missing application-level result models; keep additions minimal.
