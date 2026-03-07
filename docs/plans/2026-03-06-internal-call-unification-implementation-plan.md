# Internal Call Unification Implementation Plan
 
> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.
 
**Goal:** Unify “internal call / 进程内 RPC” failure semantics, timeout/connection detection, and metrics tags by moving behavior into `platform/infra-internal-client` and refactoring call sites to use the unified wrapper.
 
**Architecture:** Extend `InternalClientSupport` from a thin helper (`unwrap + record`) into a single, strongly-typed entry point (`call/callResult`) that (1) records metrics with stable, low-cardinality tags, (2) classifies failures consistently, and (3) always throws `BusinessException` for unexpected failures (503 for timeout/connection, 500 otherwise).
 
**Tech Stack:** Java 17, Spring Boot 3.x, Micrometer, JUnit 5, Mockito.
 
---
 
### Task 1: Define unified outcomes + metrics schema
 
**Files:**
- Modify: `backend/platform/infra-internal-client/src/main/java/com/nowcoder/community/infra/internalclient/InternalClientSupport.java`
 
**Steps:**
1. Introduce outcome constants for unified internal calls:
   - `success`, `degraded`, `timeout`, `unavailable`, `error`, `remote_error`, `forbidden`.
2. Change internal call metric schema (breaking change, approved):
   - Counter: `internal_call_requests_total`
   - Timer: `internal_call_latency`
   - Tags: `target`, `api`, `outcome` (remove legacy `client` tag).
3. Keep `unwrap(...)` behavior unchanged.
 
**Notes:**
- Outcome must be **constant-driven** from infra; call sites should not use ad-hoc strings.
 
---
 
### Task 2: Add unified call wrapper API (`call` / `callResult`)
 
**Files:**
- Create: `backend/platform/infra-internal-client/src/main/java/com/nowcoder/community/infra/internalclient/InternalCallOptions.java`
- Modify: `backend/platform/infra-internal-client/src/main/java/com/nowcoder/community/infra/internalclient/InternalClientSupport.java`
 
**Steps:**
1. Add `InternalCallOptions<T>` (fail-open support):
   - `failOpen` flag
   - optional `fallback` supplier
   - optional `warnLogger` callback (keeps infra free of logging deps; callers pass `(msg, ex) -> log.warn(msg, ex)`).
2. Add wrapper methods:
   - `call(...)` for `Supplier<T>`
   - `callResult(...)` for `Supplier<Result<T>>` (unwrap inside wrapper)
3. Enforce unified failure mapping:
   - If fail-open and fallback present: record `degraded`, return fallback
   - If `BusinessException`: rethrow as-is (record outcome derived from httpStatus/code)
   - Else unexpected `RuntimeException`: classify (timeout/connection → 503, else 500), record outcome, throw mapped `BusinessException`
 
---
 
### Task 3: Implement robust timeout/connection classification
 
**Files:**
- Modify: `backend/platform/infra-internal-client/src/main/java/com/nowcoder/community/infra/internalclient/InternalClientSupport.java`
 
**Steps:**
1. Replace weak `isTimeout(Throwable)` implementation with cause-chain scan that detects:
   - `SocketTimeoutException`, `TimeoutException`, `HttpTimeoutException` (and common “timed out” string fallback)
2. Add `isConnectionError(Throwable)` (cause-chain scan):
   - `ConnectException`, `UnknownHostException`, `NoRouteToHostException`, common `SocketException` cases
3. Add `wrapUnexpectedException(String target, Throwable t)` to build:
   - `SERVICE_UNAVAILABLE` for timeout/connection
   - `INTERNAL_ERROR` otherwise
 
---
 
### Task 4: TDD — unit tests for unified wrapper
 
**Files:**
- Create: `backend/platform/infra-internal-client/src/test/java/com/nowcoder/community/infra/internalclient/InternalClientSupportTest.java`
 
**Steps:**
1. Write failing tests for:
   - Success path records `outcome=success` and returns value
   - Timeout → throws `BusinessException` with `SERVICE_UNAVAILABLE`, records `outcome=timeout`
   - Connection error → throws `SERVICE_UNAVAILABLE`, records `outcome=unavailable`
   - Unknown runtime → throws `INTERNAL_ERROR`, records `outcome=error`
   - `BusinessException` (e.g. 403) → rethrown, records `outcome=forbidden` or `remote_error`
   - Fail-open fallback returns fallback and records `outcome=degraded`
2. Run: `mvn -f backend/pom.xml -pl platform/infra-internal-client test`
 
---
 
### Task 5: Refactor internal clients to use unified wrapper
 
**Files (representative set; extend as needed):**
- Modify: `backend/content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`
- Modify: `backend/search-service/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java`
- Modify: `backend/auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java`
- Modify: `backend/message-service/src/main/java/com/nowcoder/community/message/service/UserModerationClient.java`
- Modify: `backend/content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`
- Modify: `backend/message-service/src/main/java/com/nowcoder/community/message/service/SocialBlockClient.java`
- Modify: `backend/social-service/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java`
- Modify: `backend/content-service/src/main/java/com/nowcoder/community/content/like/RpcLikeQueryService.java`
 
**Steps:**
1. Replace per-client try/catch + `record(...)` + custom timeout scanning with `InternalClientSupport.call/callResult`.
2. Remove legacy metric client tags like `"auth-service:user-service"`; unified metrics use `target`.
3. Ensure all unexpected failures now throw `BusinessException` (503/500 per classification).
 
---
 
### Task 6: Update/extend module tests as needed
 
**Candidates:**
- `backend/auth-service/src/test/java/com/nowcoder/community/auth/service/UserServiceInternalClientTest.java`
- `backend/user-service/src/test/java/com/nowcoder/community/user/service/SocialServiceClientTest.java`
 
**Steps:**
1. Add/adjust tests only where behavior changes (e.g., timeout now maps to 503 instead of 500).
2. Keep existing service-local metrics tests stable unless intentionally changed.
 
---
 
### Task 7: Verification
 
**Commands:**
- Unit scope: `mvn -f backend/pom.xml -pl platform/infra-internal-client test`
- Module spot checks (as needed):
  - `mvn -f backend/pom.xml -pl auth-service test`
  - `mvn -f backend/pom.xml -pl message-service test`
  - `mvn -f backend/pom.xml -pl search-service test`
 
**Expected:**
- All updated tests pass.
- New unified internal call metrics exist with tags `target/api/outcome`.
 
