# Community App Strict DDD Tactical Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `backend/community-app` from mixed Spring service/use-case layering to strict DDD Tactical Layering.

**Architecture:** Each domain converges to `controller -> application -> domain -> infrastructure`. `ApplicationService` is the only same-domain use-case entry, `domain` contains business rules and repository interfaces, `infrastructure` contains MyBatis/Redis/outbox/Spring event details, and owner-domain `api.*` remains only for foreign-domain synchronous collaboration.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, ArchUnit, JUnit 5, Maven.

---

## File Structure Map

### Cross-Cutting Guardrails

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
  Shared ArchUnit helpers for package/domain detection and dependency checks.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
  New executable guardrail for strict DDD packages.
- `docs/ARCHITECTURE.md`, `docs/SYSTEM_DESIGN.md`, `AGENTS.md`
  Architecture decision records and mandatory agent/developer rules.

### First Implementation Slice: `content` Post Publishing

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
  Owner application use-case entry for post create/update/delete.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreatePostCommand.java`
  Application command for post creation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostCreateResult.java`
  Application result returned to controllers and owner API adapters.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostDraft.java`
  Domain model for a post being published.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostPublishingDomainService.java`
  Domain service for publish-time business rules and draft construction.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java`
  Domain repository interface for post persistence.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CategoryRepository.java`
  Domain repository interface for category existence checks.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostTagRepository.java`
  Domain repository interface for post tag binding.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostRepository.java`
  MyBatis-backed post repository implementation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCategoryRepository.java`
  MyBatis-backed category repository implementation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostTagRepository.java`
  MyBatis-backed post tag repository implementation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java`
  Temporary compatibility shim or deletion target after callers migrate to `content.application`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java`
  Deletion target after create-post orchestration is owned by `content.application.PostPublishingApplicationService`.

### Follow-Up Domain Slices

- `user`: user profile/read/registration/moderation application entries, user domain model, user repositories.
- `social`: like/follow/block application entries, domain repositories replacing package-local repository placement.
- `wallet`: wallet application entries, ledger/account domain services, MyBatis repositories.
- `market`: market order/listing/dispute application entries, order/inventory/wallet-action domain services.
- `growth`: task progress and user level application entries, task/reward domain services.
- `notice`, `search`, `analytics`, `auth`, `ops`: convert existing service/repo packages into the required application/domain/infrastructure package shape.

---

## Task 1: Add Strict DDD Package Guardrails

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`

- [ ] **Step 1: Write failing architecture tests for strict DDD packages**

Create `DddLayeringArchTest` with these rules:

```java
package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DddLayeringArchTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..application..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity..",
                            "..dto..",
                            "..api.."
                    );

    @ArchTest
    static final ArchRule application_must_not_depend_on_transport_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity..",
                            "..dto.."
                    );

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_domain_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..domain..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity.."
                    );
}
```

- [ ] **Step 2: Run the architecture test and confirm current drift**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest test
```

Expected: RED until existing `content.domain` bridge/assembler placement and legacy controllers are migrated or baselined.

- [ ] **Step 3: Adjust the rule scope for executable migration**

If the first run shows existing legacy packages that cannot move in the same task, narrow the rule to new strict packages first:

```java
.that().resideInAnyPackage("..domain.model..", "..domain.service..", "..domain.repository..", "..domain.event..")
```

Expected: the guardrail protects all new DDD packages without forcing unrelated legacy packages in the same commit.

- [ ] **Step 4: Re-run the architecture test**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest test
```

Expected: PASS after scope is aligned with the first migration stage.

---

## Task 2: Migrate `content` Post Publishing Create Path

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreatePostCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostCreateResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostDraft.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostPublishingDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CategoryRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostTagRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCategoryRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostTagRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingApplicationServiceTest.java`

- [ ] **Step 1: Write a failing application-layer test**

The new test should prove create-post orchestration lives in `content.application.PostPublishingApplicationService` and depends on domain repositories, not `CreatePostUseCase`.

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=com.nowcoder.community.content.application.PostPublishingApplicationServiceTest test
```

Expected: compile failure until the new application package exists.

- [ ] **Step 2: Implement the domain command/result/model/repository interfaces**

Implement only the create-post types needed by the test:

```text
CreatePostCommand(userId, title, content, categoryId, tags)
PostCreateResult(postId)
PostDraft(userId, title, content, categoryId, createTime)
PostRepository.create(PostDraft)
CategoryRepository.assertExists(UUID)
PostTagRepository.bindTagsToPost(UUID, List<String>)
```

- [ ] **Step 3: Implement the new application service**

`content.application.PostPublishingApplicationService.create(...)` should:

1. validate actor through `UserModerationGuard`
2. validate category through `CategoryRepository`
3. create a `PostDraft` through `PostPublishingDomainService`
4. save through `PostRepository`
5. bind tags through `PostTagRepository`
6. call foreign `UserPointsAwardActionApi` and `GrowthTaskProgressActionApi`
7. publish `PostPublishedDomainEvent`
8. schedule score refresh after commit
9. return `content.application.result.PostCreateResult`

- [ ] **Step 4: Add infrastructure repository implementations**

Wrap existing MyBatis mappers directly. Do not route new DDD code through legacy raw services.

- [ ] **Step 5: Keep temporary compatibility for existing callers**

Until all controllers and API adapters are moved, keep the legacy `content.service.PostPublishingApplicationService` as a thin shim delegating to `content.application.PostPublishingApplicationService`.

- [ ] **Step 6: Run focused tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=PostPublishingApplicationServiceTest,com.nowcoder.community.content.application.PostPublishingApplicationServiceTest,DddLayeringArchTest test
```

Expected: PASS.

---

## Task 3: Migrate Remaining `content` Write Use Cases

**Files:**
- Migrate: `content/app/post/UpdatePostUseCase.java`
- Migrate: `content/app/post/DeleteOwnPostUseCase.java`
- Migrate: `content/app/post/AdminDeletePostUseCase.java`
- Migrate: `content/app/post/TopPostUseCase.java`
- Migrate: `content/app/post/MarkPostWonderfulUseCase.java`
- Migrate: `content/app/moderation/TakeModerationActionUseCase.java`
- Delete legacy tests after replacement.

- [ ] Move each write use case into `content.application` methods and `content.domain` services.
- [ ] Add repository methods to `PostRepository` as each write behavior is migrated.
- [ ] Delete `content.app` after all production references are gone.
- [ ] Run `mvn -pl community-app -Dtest=PostPublishingApplicationServiceTest,PostModerationApplicationServiceTest,TakeModerationActionUseCaseTest,DddLayeringArchTest test`.

---

## Task 4: Migrate Domain Slices In Priority Order

Implement each domain in the same pattern: application entry, domain model/service/repository, infrastructure repository implementation, then delete or shrink legacy service/mapper/entity exposure.

Priority:

1. `content`
2. `user`
3. `social`
4. `wallet`
5. `market`
6. `growth`
7. `notice`
8. `search`
9. `analytics`
10. `auth`
11. `ops`

Each slice must end with:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest,ListenerBoundaryArchTest,DddLayeringArchTest test
```

Expected: PASS for architecture guardrails and the focused domain tests named in that slice.

---

## Self-Review

### Spec Coverage

- Strict DDD package shape: covered by Task 1 and the file structure map.
- `ApplicationService` as use-case entry: covered by Task 2 and repeated per-domain in Task 4.
- Domain rules and repository interfaces: covered by Task 2 and Task 4.
- Infrastructure ownership of MyBatis details: covered by Task 2 and Task 4.
- Owner-domain `api.*` only for foreign callers: preserved by existing boundary tests and Task 1.

### Placeholder Scan

This plan intentionally fully details Task 1 and Task 2, then lists follow-up domains as repeated migration slices. A separate task-level plan should be written for each follow-up domain before implementation because the whole backend conversion is too large for a single safe patch.

### Type Consistency

The first executable slice consistently uses `content.application`, `content.domain`, and `content.infrastructure` packages.

