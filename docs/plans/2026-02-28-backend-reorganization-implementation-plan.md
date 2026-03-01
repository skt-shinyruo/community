# Backend Reorganization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Normalize backend package layering, remove proven legacy/dead code, and consolidate Maven module structure while keeping runtime behavior stable.

**Architecture:** Execute in three waves: (1) no-behavior-change cleanup, (2) per-domain package normalization, (3) Maven module consolidation (`domain` + `legacy` aggregators). Each task is independently verifiable and keeps build green.

**Tech Stack:** Java 17, Spring Boot 3.2.x, Maven multi-module, Spring Security, MyBatis, Kafka, Redis, Elasticsearch.

---

## Task 0: Baseline Verification

**Files:** none

**Step 1: Confirm current workspace status**

Run: `git status -sb`
Expected: shows current branch + local changes.

**Step 2: Run backend baseline tests**

Run: `cd backend && ./mvnw test -q`
Expected: exit code 0.

## Task 1: Remove Legacy Domain Entrypoints (`*ServiceApplication`)

**Files:**
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/AuthServiceApplication.java`
- Delete: `backend/user-service/src/main/java/com/nowcoder/community/user/UserServiceApplication.java`
- Delete: `backend/content-service/src/main/java/com/nowcoder/community/content/ContentServiceApplication.java`
- Delete: `backend/social-service/src/main/java/com/nowcoder/community/social/SocialServiceApplication.java`
- Delete: `backend/message-service/src/main/java/com/nowcoder/community/message/MessageServiceApplication.java`
- Delete: `backend/search-service/src/main/java/com/nowcoder/community/search/SearchServiceApplication.java`
- Delete: `backend/analytics-service/src/main/java/com/nowcoder/community/analytics/AnalyticsServiceApplication.java`
- Delete: `backend/ops-service/src/main/java/com/nowcoder/community/ops/OpsServiceApplication.java`

**Step 1: Verify no references exist**

Run: `rg -n "(AuthServiceApplication|UserServiceApplication|ContentServiceApplication|SocialServiceApplication|MessageServiceApplication|SearchServiceApplication|AnalyticsServiceApplication|OpsServiceApplication)" backend`
Expected: references only inside these files (and docs/tests if any).

**Step 2: Delete all legacy entry classes**

Apply deletion for the eight files above.

**Step 3: Compile monolith path**

Run: `cd backend && ./mvnw -q -DskipTests -pl :community-app -am package`
Expected: success.

## Task 2: Remove Duplicated Unused Auth Internal DTOs

**Files:**
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalActivationResponse.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalActivateRequest.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalAuthenticateRequest.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalAuthenticateResponse.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalRefreshTokenRecordResponse.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalRefreshTokenRevokeFamilyRequest.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalRefreshTokenRevokeRequest.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalRefreshTokenStoreRequest.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalRegisterRequest.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalRegisterResponse.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalSessionProfileResponse.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalUpdatePasswordRequest.java`
- Delete: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/dto/UserInternalUserByEmailResponse.java`

**Step 1: Verify these classes are unreferenced**

Run: `rg -n "com\.nowcoder\.community\.auth\.service\.dto\.UserInternal" backend`
Expected: zero import/use sites outside the files themselves.

**Step 2: Delete the duplicate DTO files**

Apply deletion for all listed files.

**Step 3: Run auth module tests**

Run: `cd backend && ./mvnw -q -pl :auth-service -am test`
Expected: success.

## Task 3: Standardize Package Layout in `auth-service` (Pilot Domain)

**Files:**
- Move: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java` -> `backend/auth-service/src/main/java/com/nowcoder/community/auth/application/AuthService.java`
- Move: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java` -> `backend/auth-service/src/main/java/com/nowcoder/community/auth/application/RegistrationService.java`
- Move: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java` -> `backend/auth-service/src/main/java/com/nowcoder/community/auth/application/PasswordResetService.java`
- Move: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java` -> `backend/auth-service/src/main/java/com/nowcoder/community/auth/infra/client/UserServiceInternalClient.java`
- Move: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/*Store*.java` -> `backend/auth-service/src/main/java/com/nowcoder/community/auth/infra/store/`
- Move: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/*MailService*.java` -> `backend/auth-service/src/main/java/com/nowcoder/community/auth/infra/mail/`

**Step 1: Move one class at a time, update package/imports**

Expected: no behavior change.

**Step 2: Compile auth module after each move cluster**

Run: `cd backend && ./mvnw -q -pl :auth-service -am test`
Expected: success after each cluster.

## Task 4: Define Domain Aggregator (`backend/domain`) Without Breaking Builds

**Files:**
- Create: `backend/domain/pom.xml`
- Create: `backend/domain/auth/pom.xml` (temporary aggregator)
- Create: `backend/domain/user/pom.xml`
- Create: `backend/domain/content/pom.xml`
- Create: `backend/domain/social/pom.xml`
- Create: `backend/domain/message/pom.xml`
- Create: `backend/domain/search/pom.xml`
- Create: `backend/domain/analytics/pom.xml`
- Create: `backend/domain/ops/pom.xml`
- Modify: `backend/pom.xml`

**Step 1: Add `domain` aggregator as non-breaking parent layer**

Keep existing modules untouched in first commit.

**Step 2: Include existing module paths through aggregator modules**

Expected: same artifacts, same coordinates.

**Step 3: Validate reactor build**

Run: `cd backend && ./mvnw -q -DskipTests package`
Expected: success.

## Task 5: Isolate Legacy Modules (`legacy` Aggregator)

**Files:**
- Create: `backend/legacy/pom.xml`
- Move module declarations for legacy runtime from `backend/pom.xml` into `legacy/pom.xml`:
  - `gateway`
  - (optional transitional) `platform/infra-dubbo-starter`
- Modify: `backend/pom.xml`

**Step 1: Add legacy aggregator**

Keep legacy modules buildable but explicitly separated.

**Step 2: Ensure default monolith path does not require legacy modules**

Run: `cd backend && ./mvnw -q -DskipTests -pl :community-app -am package`
Expected: success without legacy runtime modules in active path.

## Task 6: Documentation Alignment

**Files:**
- Modify: `backend/README.md`
- Modify: `backend/docs/ARCHITECTURE.md`
- Modify: `backend/docs/SYSTEM_DESIGN.md`

**Step 1: Update module topology sections**

Document `community-bootstrap / platform / domain / legacy` layout.

**Step 2: Update migration-window notes**

Clarify that `legacy/*` is not default runtime path.

## Task 7: Final Verification

**Files:** none

**Step 1: Run focused tests**

Run:
- `cd backend && ./mvnw -q -pl :auth-service -am test`
- `cd backend && ./mvnw -q -pl :community-app -am test`

Expected: success.

**Step 2: Run full backend tests when feasible**

Run: `cd backend && ./mvnw test`
Expected: success or known unrelated failures documented.

**Step 3: Confirm no stale references**

Run:
- `rg -n "@SpringBootApplication" backend`
- `rg -n "com\.nowcoder\.community\.auth\.service\.dto\.UserInternal" backend`

Expected:
- Only `community-app` and explicitly retained legacy app classes remain.
- No references to removed duplicate DTO package.
