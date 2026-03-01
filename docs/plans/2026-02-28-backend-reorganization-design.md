# Backend Reorganization Design (Package + Maven)

**Date:** 2026-02-28
**Status:** Approved (user confirmed both package cleanup and Maven consolidation)

## Context

The backend already runs as a modular monolith with one deployable app (`community-app`), but repository structure still contains historical microservice artifacts and inconsistent internal package conventions.

Main pain points:

1. Legacy entry classes (`*ServiceApplication`) still exist in each domain module and create mental overhead.
2. Package layering is not consistent across modules (`api/service/rpc/dao/entity/kafka/config` differs by domain and intent).
3. Some code appears duplicated or dead (for example `auth/service/dto/UserInternal*` classes that duplicate `user-api` RPC DTO contracts).
4. Maven layout still includes migration-window modules (`gateway`, `infra-dubbo-starter`) in the primary backend reactor path.

## Goals

1. Keep runtime behavior stable while reducing codebase cognitive load.
2. Standardize package boundaries inside each domain module.
3. Remove obvious dead/legacy code in safe batches.
4. Consolidate Maven module structure toward domain-first organization and isolate legacy modules.

## Non-goals (first wave)

1. No API contract changes for frontend (`/api/**`, `/files/**` remain stable).
2. No business rule rewrites.
3. No one-shot large directory rewrite across all modules in one commit.

## Target Structure

## 1) Maven/module level

Target top-level groups:

- `app/community-app`: only runtime entrypoint and global runtime composition.
- `platform/*`: shared contracts + infra + common.
- `domain/*`: business domains (`auth`, `user`, `content`, `social`, `message`, `search`, `analytics`, `ops`).
- `legacy/*`: migration-window components (`gateway`, dubbo-specific runtime helpers if retained temporarily).

Transition rule:

- Keep existing modules buildable while introducing `domain` and `legacy` aggregators incrementally.
- Do not break existing artifact coordinates in one step unless all references are migrated.

## 2) Domain package level (inside each domain module)

Canonical package layout:

- `<domain>.api`: REST controllers and external DTOs.
- `<domain>.application`: use-case orchestration/application services.
- `<domain>.domain`: domain model and business rules.
- `<domain>.infra`: persistence/adapter/event integration (`dao`, `kafka`, gateway adapters, internal client adapters).
- `<domain>.config`: module-local configuration.

Mapping policy:

- Existing `service` package gradually split into `application` + `domain` where intent is clear.
- Existing `rpc` package moved under `infra` (`infra.rpc`) unless class represents stable contract in `*-api` module.
- Existing `dao/entity/kafka` moved under `infra` subpackages.

## 3) Legacy/dead-code policy

Safe-to-remove in wave 1:

1. Per-domain `*ServiceApplication` classes not used by runtime entrypoint.
2. Unused duplicated DTO classes in `auth/service/dto` that are superseded by `user-api` DTOs.
3. Module-level runtime config files that are no longer loaded by Spring Boot default path and have no documented usage.

Deferred to later waves:

1. Full removal of `gateway` code.
2. Full removal of `infra-dubbo-starter` if no legacy profile path remains.

## Migration Strategy

### Wave 1 (safe, no behavior change)

1. Remove legacy `*ServiceApplication` classes.
2. Remove proven unused duplicated DTO classes.
3. Keep tests/build green.

### Wave 2 (package standardization per domain)

1. Move packages to canonical layout domain-by-domain.
2. Keep class behavior unchanged, only structural moves and naming normalization.
3. Update imports/tests per domain batch.

### Wave 3 (Maven consolidation)

1. Introduce `domain` and `legacy` aggregators.
2. Migrate module declarations.
3. Move `gateway` (and possibly dubbo-specific pieces) under `legacy` scope.
4. Ensure default build path excludes/isolates legacy runtime where appropriate.

## Risks and Mitigations

1. **Hidden runtime references to legacy classes**
   Mitigation: remove only items with verified zero references, run module tests after each batch.

2. **Import churn during package moves**
   Mitigation: migrate one domain at a time and keep commits small.

3. **Maven reactor breakage**
   Mitigation: introduce aggregators first, then move module paths in controlled commits.

## Acceptance Criteria

1. Backend remains buildable and testable after each wave.
2. Legacy entrypoint classes are removed from domain modules.
3. Package layout is consistent for migrated domains.
4. Maven structure clearly separates runtime (`app`), platform, domains, and legacy migration window.
