# Community Unused Code Retirement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the first-pass high-confidence unused code surfaces documented in `docs/superpowers/specs/2026-05-04-community-unused-code-retirement-design.md`.

**Architecture:** This is a deletion-first cleanup that preserves the current DDD tactical boundaries by removing unused inbound HTTP adapters, unused frontend API wrappers, stale DTO/record shells, and legacy frontend fallback code. The plan intentionally keeps shared application/domain behavior that still has owner-domain API callers, such as `CaptchaApplicationService.verify(String, String)`, `BlockApplicationService.hasBlocked(...)`, `LikeApplicationService.userLikeCount(...)`, and follow count endpoints.

**Tech Stack:** Java 17, Spring Boot 3, Maven, JUnit 5, AssertJ, Vue 3, Vite, Vitest, MyBatis, ArchUnit.

---

## File Map

Create:
- `backend/community-app/src/test/java/com/nowcoder/community/app/retirement/UnusedSurfaceRetirementTest.java`: classpath assertions that first-pass retired backend app classes stay absent.
- `frontend/src/api/services/unusedSurfaceRetirement.test.js`: static frontend assertions that deleted wrappers, legacy routes, helper functions, and unmounted views stay absent.
- `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/ImCommonContractRetirementTest.java`: classpath assertions for retired IM common contracts.

Modify:
- `frontend/src/api/services/authService.js`: remove `verifyCaptcha`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`: remove `POST /captcha/verify`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java`: remove permit rule for `POST /api/auth/captcha/verify`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/AuthApplicationService.java`: remove `verifyCaptcha(...)`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java`: remove the `VerifyCaptchaCommand` overload only.
- `docs/handbook/auth-login-session-flow.md`: remove the deleted captcha verify route from the current endpoint list.
- `frontend/src/api/services/blockService.js`: remove `getBlockStatus`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/BlockController.java`: remove `GET /api/blocks/status`.
- `frontend/src/api/services/userService.js`: remove `resolveUserByUsername`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`: remove `GET /api/users/resolve` and its DTO mapper.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java`: remove `resolveByUsername(...)`.
- `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserReadApplicationServiceTest.java`: remove the resolve-only test/import.
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`: remove resolve-only assertions and the stale internal password DTO test.
- `frontend/src/api/services/socialService.js`: remove `getUserLikeCount`, `countFollowees`, and `countFollowers`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java`: remove the unused HTTP endpoint `GET /api/likes/users/{userId}/count`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/security/SocialSecurityRules.java`: remove only the public matcher for `/api/likes/users/*/count`.
- `frontend/src/api/services/searchService.js`: remove fallback to `/api/search/internal/reindex`; keep `/api/ops/search/reindex`.
- `frontend/src/api/services/searchService.test.js`: add tests for current reindex behavior and no legacy fallback.
- `frontend/src/router/navigation.js`: remove `normalizePostsQuery` and `buildPostsQuery`.
- `frontend/src/router/navigation.test.js`: remove tests/imports for deleted router helpers.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`: remove `GET /api/analytics/me`.
- `docs/handbook/business-flows.md`: remove the current-debug-route mention of `/api/analytics/me`.
- `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java`: remove tests/imports for retired IM common contracts.

Delete:
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/command/VerifyCaptchaCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/CaptchaVerifyRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserResolveResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UserResolveResponse.java`
- `frontend/src/views/SignInCalendarView.vue`
- `frontend/src/views/TaskCenterView.vue`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/dto/RangeQuery.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/application/result/AnalyticsCountResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/application/result/ReindexJobResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthCredential.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthTokens.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/CaptchaChallenge.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PasswordResetToken.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RefreshTokenRecord.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationCode.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationSession.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/command/AdminResolveMarketDisputeCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/command/MarketWalletActionCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalActivationResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalAuthenticateRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalAuthenticateResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalBatchUserSummaryRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalModerationApplyRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalModerationStatusResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRecordResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRevokeFamilyRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRevokeRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenStoreRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRegisterRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalSessionProfileResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUpdatePasswordRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUserByEmailResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUserSummaryResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/LeaderboardItemResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/InternalUserProfileStatsResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardAccountDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardGrantRecordDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardLedgerEntryDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/UserTaskEventLogDataObject.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionRequest.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChangedEventV1.java`

Do not delete in this plan:
- `GET /api/auth/captcha`, `IssueCaptchaCommand`, `CaptchaIssueResponse`, and internal captcha verification used by login, registration, resend, and password reset flows.
- `BlockApplicationService.hasBlocked(...)`, `BlockRepository.hasBlocked(...)`, and `SocialBlockQueryApi`, because cross-domain/block filtering still uses them.
- `LikeApplicationService.userLikeCount(...)`, `LikeRepository.getUserLikeCount(...)`, and `SocialLikeQueryApi`, because user profile aggregation still uses the owner-domain query API.
- `GET /api/follows/{userId}/followees/count` and `GET /api/follows/{userId}/followers/count`, because profile summary/list behavior still exposes follow counts.
- `POST /api/ops/search/reindex`, `SearchReindexResult`, `SearchReindexResponse` under `ops`, and `SearchReindexActionApi`.
- Market listing/order/dispute lifecycle APIs, category subscribe/unsubscribe APIs, IM room/unread HTTP APIs, and `TraceIdClientHttpRequestInterceptor`.

### Task 1: Add Backend App Retirement Test

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/retirement/UnusedSurfaceRetirementTest.java`

- [ ] **Step 1: Create the classpath retirement test**

```java
package com.nowcoder.community.app.retirement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnusedSurfaceRetirementTest {

    @Test
    void firstPassUnusedAppClassesShouldStayRetired() {
        assertClassRetired("com.nowcoder.community.auth.application.command.VerifyCaptchaCommand");
        assertClassRetired("com.nowcoder.community.auth.controller.dto.CaptchaVerifyRequest");
        assertClassRetired("com.nowcoder.community.user.application.result.UserResolveResult");
        assertClassRetired("com.nowcoder.community.user.controller.dto.UserResolveResponse");
        assertClassRetired("com.nowcoder.community.analytics.controller.dto.RangeQuery");
        assertClassRetired("com.nowcoder.community.analytics.application.result.AnalyticsCountResult");
        assertClassRetired("com.nowcoder.community.search.application.result.ReindexJobResult");
        assertClassRetired("com.nowcoder.community.market.application.command.AdminResolveMarketDisputeCommand");
        assertClassRetired("com.nowcoder.community.market.application.command.MarketWalletActionCommand");
    }

    @Test
    void authPlaceholderDomainModelsShouldStayRetired() {
        assertClassRetired("com.nowcoder.community.auth.domain.model.AuthCredential");
        assertClassRetired("com.nowcoder.community.auth.domain.model.AuthTokens");
        assertClassRetired("com.nowcoder.community.auth.domain.model.CaptchaChallenge");
        assertClassRetired("com.nowcoder.community.auth.domain.model.PasswordResetToken");
        assertClassRetired("com.nowcoder.community.auth.domain.model.RefreshTokenRecord");
        assertClassRetired("com.nowcoder.community.auth.domain.model.RegistrationCode");
        assertClassRetired("com.nowcoder.community.auth.domain.model.RegistrationSession");
    }

    @Test
    void staleUserAndSocialDtosShouldStayRetired() {
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalActivationResponse");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalAuthenticateRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalAuthenticateResponse");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalBatchUserSummaryRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalModerationApplyRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalModerationStatusResponse");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenRecordResponse");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenRevokeFamilyRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenRevokeRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenStoreRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalRegisterRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalSessionProfileResponse");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalUpdatePasswordRequest");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalUserByEmailResponse");
        assertClassRetired("com.nowcoder.community.user.controller.dto.InternalUserSummaryResponse");
        assertClassRetired("com.nowcoder.community.user.controller.dto.LeaderboardItemResponse");
        assertClassRetired("com.nowcoder.community.social.controller.dto.InternalUserProfileStatsResponse");
    }

    @Test
    void staleGrowthDataObjectsShouldStayRetired() {
        assertClassRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.RewardAccountDataObject");
        assertClassRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.RewardGrantRecordDataObject");
        assertClassRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.RewardLedgerEntryDataObject");
        assertClassRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserTaskEventLogDataObject");
    }

    private void assertClassRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the test and confirm it is red**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UnusedSurfaceRetirementTest test
```

Expected: FAIL because the retired classes still exist before deletion.

- [ ] **Step 3: Commit the red test in the dedicated worktree**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/retirement/UnusedSurfaceRetirementTest.java
git commit -m "test: lock unused app surface retirement"
```

### Task 2: Add Frontend Retirement Test

**Files:**
- Create: `frontend/src/api/services/unusedSurfaceRetirement.test.js`

- [ ] **Step 1: Create the static frontend retirement test**

```javascript
import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const srcRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')

function source(relativePath) {
  return readFileSync(resolve(srcRoot, relativePath), 'utf8')
}

function exists(relativePath) {
  return existsSync(resolve(srcRoot, relativePath))
}

describe('unused frontend surface retirement', () => {
  it('retired service wrappers should stay removed', () => {
    expect(source('api/services/authService.js')).not.toContain('verifyCaptcha')
    expect(source('api/services/blockService.js')).not.toContain('getBlockStatus')
    expect(source('api/services/userService.js')).not.toContain('resolveUserByUsername')
    expect(source('api/services/socialService.js')).not.toContain('getUserLikeCount')
    expect(source('api/services/socialService.js')).not.toContain('countFollowees')
    expect(source('api/services/socialService.js')).not.toContain('countFollowers')
  })

  it('retired routes and frontend helpers should stay removed', () => {
    expect(source('api/services/authService.js')).not.toContain('/api/auth/captcha/verify')
    expect(source('api/services/blockService.js')).not.toContain('/api/blocks/status')
    expect(source('api/services/userService.js')).not.toContain('/api/users/resolve')
    expect(source('api/services/socialService.js')).not.toContain('/api/likes/users/')
    expect(source('api/services/searchService.js')).not.toContain('/api/search/internal/reindex')
    expect(source('router/navigation.js')).not.toContain('normalizePostsQuery')
    expect(source('router/navigation.js')).not.toContain('buildPostsQuery')
  })

  it('unmounted growth views should stay removed', () => {
    expect(exists('views/SignInCalendarView.vue')).toBe(false)
    expect(exists('views/TaskCenterView.vue')).toBe(false)
  })
})
```

- [ ] **Step 2: Run the test and confirm it is red**

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- unusedSurfaceRetirement.test.js
```

Expected: FAIL because the wrappers, helper functions, legacy route string, and unmounted view files still exist.

- [ ] **Step 3: Commit the red test in the dedicated worktree**

```bash
git add frontend/src/api/services/unusedSurfaceRetirement.test.js
git commit -m "test: lock unused frontend surface retirement"
```

### Task 3: Add IM Common Retirement Test

**Files:**
- Create: `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/ImCommonContractRetirementTest.java`

- [ ] **Step 1: Create the IM common classpath retirement test**

```java
package com.nowcoder.community.im.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImCommonContractRetirementTest {

    @Test
    void retiredImCommonContractsShouldStayAbsent() {
        assertClassRetired("com.nowcoder.community.im.common.session.OpenImSessionRequest");
        assertClassRetired("com.nowcoder.community.im.common.event.RoomMemberChangedEventV1");
    }

    private void assertClassRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the test and confirm it is red**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-im/im-common -Dtest=ImCommonContractRetirementTest test
```

Expected: FAIL because both IM common contracts still exist before deletion.

- [ ] **Step 3: Commit the red test in the dedicated worktree**

```bash
git add backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/ImCommonContractRetirementTest.java
git commit -m "test: lock unused im common contract retirement"
```

### Task 4: Retire Auth Captcha Verify Endpoint

**Files:**
- Modify: `frontend/src/api/services/authService.js`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/AuthApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java`
- Modify: `docs/handbook/auth-login-session-flow.md`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/command/VerifyCaptchaCommand.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/CaptchaVerifyRequest.java`

- [ ] **Step 1: Remove the frontend wrapper**

Delete this export from `frontend/src/api/services/authService.js`:

```javascript
export async function verifyCaptcha(captchaId, code) {
  const resp = await http.post('/api/auth/captcha/verify', { captchaId, code })
  const { data, traceId } = unwrapResultBody(resp.data, '验证码校验')
  return { data: !!data, traceId }
}
```

- [ ] **Step 2: Remove the controller endpoint**

Delete these imports from `AuthController.java`:

```java
import com.nowcoder.community.auth.application.command.VerifyCaptchaCommand;
import com.nowcoder.community.auth.controller.dto.CaptchaVerifyRequest;
```

Delete this method from `AuthController.java`:

```java
@PostMapping("/captcha/verify")
public Result<Boolean> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
    return Result.ok(authApplicationService.verifyCaptcha(new VerifyCaptchaCommand(request.getCaptchaId(), request.getCode())));
}
```

- [ ] **Step 3: Remove the facade and command overload**

Delete this import and method from `AuthApplicationService.java`:

```java
import com.nowcoder.community.auth.application.command.VerifyCaptchaCommand;

public boolean verifyCaptcha(VerifyCaptchaCommand command) {
    return captchaApplicationService.verify(command);
}
```

Delete this import and overload from `CaptchaApplicationService.java`:

```java
import com.nowcoder.community.auth.application.command.VerifyCaptchaCommand;

public boolean verify(VerifyCaptchaCommand command) {
    return command != null && verify(command.captchaId(), command.code());
}
```

Keep this method in `CaptchaApplicationService.java`:

```java
public boolean verify(String captchaId, String code) {
    if (isBlank(captchaId) || isBlank(code)) {
        return false;
    }
    int ttlSeconds = Math.max(1, properties.getTtlSeconds());
    int maxFailures = Math.max(1, properties.getMaxFailures());
    try {
        CaptchaRepository.VerifyResult verifyResult = captchaStore.verifyAndConsume(captchaId, captchaDomainService.normalizeCode(code));
        if (verifyResult == CaptchaRepository.VerifyResult.MATCHED) {
            return true;
        }
        if (verifyResult == CaptchaRepository.VerifyResult.NOT_FOUND) {
            return false;
        }
        int failures = captchaStore.incrementFailures(captchaId, Duration.ofSeconds(ttlSeconds));
        if (failures >= maxFailures) {
            captchaStore.delete(captchaId);
        }
    } catch (RuntimeException e) {
        throw captchaUnavailable(e);
    }
    return false;
}
```

- [ ] **Step 4: Remove the security allow-list entry**

Change `AuthSecurityRules.apply(...)` so the captcha section contains only the issue endpoint:

```java
auth.requestMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll();
auth.requestMatchers(HttpMethod.POST, "/api/auth/password/reset/request", "/api/auth/password/reset/confirm").permitAll();
```

- [ ] **Step 5: Delete the unused command and DTO files**

Run:

```bash
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/application/command/VerifyCaptchaCommand.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/CaptchaVerifyRequest.java
```

- [ ] **Step 6: Update auth handbook route list**

In `docs/handbook/auth-login-session-flow.md`, remove this current route entry:

```markdown
- `POST /api/auth/captcha/verify`
```

Keep this current route entry:

```markdown
- `GET /api/auth/captcha`
```

- [ ] **Step 7: Verify the auth deletion slice**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UnusedSurfaceRetirementTest,CaptchaApplicationServiceTest,AuthControllerUnitTest test
```

Expected: `UnusedSurfaceRetirementTest` still fails because later tasks have not deleted every listed class. `CaptchaApplicationServiceTest` and `AuthControllerUnitTest` pass.

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- authService.test.js unusedSurfaceRetirement.test.js
```

Expected: `authService.test.js` passes. `unusedSurfaceRetirement.test.js` still fails until later frontend deletions finish.

- [ ] **Step 8: Commit the auth slice**

```bash
git add frontend/src/api/services/authService.js \
  backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/AuthApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java \
  docs/handbook/auth-login-session-flow.md
git add -u backend/community-app/src/main/java/com/nowcoder/community/auth/application/command/VerifyCaptchaCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/CaptchaVerifyRequest.java
git commit -m "refactor: retire standalone captcha verify endpoint"
```

### Task 5: Retire Social And User Dead HTTP Surfaces

**Files:**
- Modify: `frontend/src/api/services/blockService.js`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/controller/BlockController.java`
- Modify: `frontend/src/api/services/userService.js`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserReadApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`
- Modify: `frontend/src/api/services/socialService.js`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/security/SocialSecurityRules.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserResolveResult.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UserResolveResponse.java`

- [ ] **Step 1: Remove block status wrapper and endpoint**

Delete this export from `frontend/src/api/services/blockService.js`:

```javascript
export async function getBlockStatus(userId) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.get('/api/blocks/status', { params: { userId: uid } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询屏蔽状态')
  return { data: !!data, traceId }
}
```

Delete this method from `BlockController.java`:

```java
@GetMapping("/status")
public Result<Boolean> status(Authentication authentication, @RequestParam UUID userId) {
    UUID actorId = CurrentUser.requireUserUuid(authentication);
    return Result.ok(blockApplicationService.hasBlocked(actorId, userId));
}
```

Keep `BlockApplicationService.hasBlocked(...)` for `SocialBlockQueryApiAdapter`.

- [ ] **Step 2: Remove user resolve wrapper, endpoint, result, response, and tests**

Delete this export from `frontend/src/api/services/userService.js`:

```javascript
export async function resolveUserByUsername(username) {
  const resp = await http.get('/api/users/resolve', { params: { username } })
  const { data, traceId } = unwrapResultBody(resp.data, '按用户名查询用户')
  return { ...data, _traceId: traceId }
}
```

In `UserController.java`, delete these imports:

```java
import com.nowcoder.community.user.application.result.UserResolveResult;
import com.nowcoder.community.user.controller.dto.UserResolveResponse;
```

Delete this endpoint method:

```java
@GetMapping("/resolve")
public Result<UserResolveResponse> resolveByUsername(@RequestParam String username) {
    return Result.ok(toUserResolveResponse(userReadApplicationService.resolveByUsername(username)));
}
```

Delete this mapper method:

```java
private static UserResolveResponse toUserResolveResponse(UserResolveResult user) {
    UserResolveResponse response = new UserResolveResponse();
    response.setId(user.id());
    response.setUsername(user.username());
    response.setHeaderUrl(user.headerUrl());
    return response;
}
```

In `UserReadApplicationService.java`, delete this import and method:

```java
import com.nowcoder.community.user.application.result.UserResolveResult;

public UserResolveResult resolveByUsername(String username) {
    UserSummaryResult user = getSummaryByUsername(username);
    if (user == null || user.id() == null) {
        throw new BusinessException(USER_NOT_FOUND);
    }
    return new UserResolveResult(user.id(), user.username(), user.headerUrl());
}
```

Delete the resolve-only test method from `UserReadApplicationServiceTest.java`:

```java
@Test
void resolveByUsernameShouldMapSummaryAndRaiseWhenMissing() {
    UUID userId = uuid(7);
    when(userRepository.findByUsername("alice"))
            .thenReturn(Optional.of(new UserAccount(userId, "alice", "alice@example.com", "h7", null, 0, 0, null)));
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    UserResolveResult result = service.resolveByUsername("alice");
    assertThat(result).isEqualTo(new UserResolveResult(userId, "alice", "h7"));
    assertThatThrownBy(() -> service.resolveByUsername("ghost"))
            .isInstanceOf(BusinessException.class);
}
```

Delete the resolve-only test method from `UserControllerUnitTest.java`:

```java
@Test
void resolveByUsernameShouldUseControllerFacingApplicationServiceResponse() {
    UUID userId = uuid(7);
    when(userReadApplicationService.resolveByUsername("alice"))
            .thenReturn(new UserResolveResult(userId, "alice", "h7"));

    Result<UserResolveResponse> result = controller.resolveByUsername("alice");

    assertThat(result.getCode()).isEqualTo(0);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getId()).isEqualTo(userId);
    assertThat(result.getData().getUsername()).isEqualTo("alice");
    assertThat(result.getData().getHeaderUrl()).isEqualTo("h7");
    verify(userReadApplicationService).resolveByUsername("alice");
}
```

Delete these files:

```bash
git rm backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserResolveResult.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UserResolveResponse.java
```

- [ ] **Step 3: Remove unused social wrappers**

Delete these exports from `frontend/src/api/services/socialService.js`:

```javascript
export async function getUserLikeCount(userId) {
  const resp = await http.get(`/api/likes/users/${userId}/count`)
  const { data, traceId } = unwrapResultBody(resp.data, '查询用户获赞')
  return { data: Number(data || 0), traceId }
}

export async function countFollowees(userId, { entityType = 3 } = {}) {
  const resp = await http.get(`/api/follows/${userId}/followees/count`, { params: { entityType } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询关注数')
  return { data: Number(data || 0), traceId }
}

export async function countFollowers(userId, { entityType = 3 } = {}) {
  const resp = await http.get(`/api/follows/${userId}/followers/count`, { params: { entityType } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询粉丝数')
  return { data: Number(data || 0), traceId }
}
```

Keep the backend follow count endpoints and `SocialSecurityRules` follow count public matchers.

- [ ] **Step 4: Remove unused user-like HTTP endpoint**

In `LikeController.java`, delete this endpoint:

```java
@GetMapping("/users/{userId}/count")
public Result<Long> userLikeCount(@PathVariable UUID userId) {
    return Result.ok(likeApplicationService.userLikeCount(userId));
}
```

Remove the `PathVariable` import from `LikeController.java` when no other method uses it:

```java
import org.springframework.web.bind.annotation.PathVariable;
```

Keep `LikeApplicationService.userLikeCount(...)` because `SocialLikeQueryApiAdapter` still delegates to it.

- [ ] **Step 5: Tighten social public security matcher**

Change `SocialSecurityRules.apply(...)` from:

```java
auth.requestMatchers(HttpMethod.GET, "/api/likes/count", "/api/likes/counts", "/api/likes/users/*/count").permitAll();
auth.requestMatchers(HttpMethod.GET, "/api/follows/*/followees", "/api/follows/*/followers").permitAll();
auth.requestMatchers(HttpMethod.GET, "/api/follows/*/followees/count", "/api/follows/*/followers/count").permitAll();
```

to:

```java
auth.requestMatchers(HttpMethod.GET, "/api/likes/count", "/api/likes/counts").permitAll();
auth.requestMatchers(HttpMethod.GET, "/api/follows/*/followees", "/api/follows/*/followers").permitAll();
auth.requestMatchers(HttpMethod.GET, "/api/follows/*/followees/count", "/api/follows/*/followers/count").permitAll();
```

- [ ] **Step 6: Verify the social/user deletion slice**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UnusedSurfaceRetirementTest,BlockControllerTest,LikeControllerTest,UserReadApplicationServiceTest,UserControllerUnitTest,SocialBlockQueryApiAdapterTest,SocialLikeQueryApiAdapterTest test
```

Expected: controller, application, and API adapter tests pass. `UnusedSurfaceRetirementTest` still fails until placeholder classes are removed in Task 8.

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- socialService.test.js userService.test.js unusedSurfaceRetirement.test.js
```

Expected: service tests pass. `unusedSurfaceRetirement.test.js` still fails until all frontend removals finish.

- [ ] **Step 7: Commit the social/user slice**

```bash
git add frontend/src/api/services/blockService.js \
  frontend/src/api/services/userService.js \
  frontend/src/api/services/socialService.js \
  backend/community-app/src/main/java/com/nowcoder/community/social/controller/BlockController.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/security/SocialSecurityRules.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/application/UserReadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java
git add -u backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserResolveResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UserResolveResponse.java
git commit -m "refactor: retire unused social and user http surfaces"
```

### Task 6: Retire Frontend Fallbacks, Helpers, And Unmounted Views

**Files:**
- Modify: `frontend/src/api/services/searchService.js`
- Modify: `frontend/src/api/services/searchService.test.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/navigation.test.js`
- Delete: `frontend/src/views/SignInCalendarView.vue`
- Delete: `frontend/src/views/TaskCenterView.vue`

- [ ] **Step 1: Add reindex behavior tests**

Modify `frontend/src/api/services/searchService.test.js` imports from:

```javascript
import { searchPosts } from './searchService'
```

to:

```javascript
import { reindex, searchPosts } from './searchService'
```

Add these tests inside the existing `describe('api/services/searchService', ...)` block:

```javascript
it('reindex should call the ops route', async () => {
  mock = new MockAdapter(http)
  mock.onPost('/api/ops/search/reindex').reply(200, {
    code: 0,
    message: '',
    data: {
      jobId: 'job-1',
      indexedCount: 42
    },
    traceId: 'trace-reindex'
  })

  const resp = await reindex()

  expect(resp.traceId).toBe('trace-reindex')
  expect(resp.data).toEqual({
    jobId: 'job-1',
    indexedCount: 42
  })
})

it('reindex should not fall back to the retired internal route', async () => {
  mock = new MockAdapter(http)
  mock.onPost('/api/ops/search/reindex').reply(404, {
    code: 404,
    message: 'Not Found'
  })
  mock.onPost('/api/search/internal/reindex').reply(200, {
    code: 0,
    message: '',
    data: {
      jobId: 'legacy-job',
      indexedCount: 1
    },
    traceId: 'trace-legacy'
  })

  await expect(reindex()).rejects.toMatchObject({
    response: {
      status: 404
    }
  })
})
```

- [ ] **Step 2: Run the reindex fallback test and confirm it is red**

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- searchService.test.js
```

Expected: FAIL because the current implementation falls back to `/api/search/internal/reindex` after a 404.

- [ ] **Step 3: Remove the reindex fallback**

Replace `reindex()` in `frontend/src/api/services/searchService.js` with:

```javascript
export async function reindex() {
  const resp = await http.post('/api/ops/search/reindex', null)
  const { data, traceId } = unwrapResultBody(resp.data, '重建索引')
  return { data, traceId }
}
```

- [ ] **Step 4: Remove dead router helpers**

Delete these functions from `frontend/src/router/navigation.js`:

```javascript
export function normalizePostsQuery(query) {
  const q = query && typeof query === 'object' ? query : {}
  return {
    order: normalizePostsOrder(q.order),
    filter: normalizePostsFilter(q.type),
    categoryId: normalizePostsCategoryId(q.categoryId),
    tag: normalizePostsTag(q.tag),
    subscribed: normalizePostsSubscribed(q.subscribed)
  }
}

export function buildPostsQuery({ order, filter, categoryId, tag, subscribed } = {}) {
  const normalizedOrder = normalizePostsOrder(order)
  const normalizedFilter = normalizePostsFilter(filter)
  const normalizedCategoryId = normalizePostsCategoryId(categoryId)
  const normalizedTag = normalizePostsTag(tag)
  const normalizedSubscribed = normalizePostsSubscribed(subscribed)

  const next = {}
  if (normalizedOrder !== POSTS_ORDER.LATEST) next.order = normalizedOrder
  if (normalizedFilter) next.type = normalizedFilter
  if (normalizedCategoryId) next.categoryId = String(normalizedCategoryId)
  if (normalizedTag) next.tag = normalizedTag
  if (normalizedSubscribed) next.subscribed = '1'
  return next
}
```

- [ ] **Step 5: Remove router helper tests and imports**

In `frontend/src/router/navigation.test.js`, remove these imports:

```javascript
buildPostsQuery,
normalizePostsQuery
```

Delete these test blocks:

```javascript
it('normalizePostsQuery should parse order/type from query', () => {
  expect(normalizePostsQuery({ order: 'hot', type: 'top' })).toEqual({
    order: POSTS_ORDER.HOT,
    filter: POSTS_FILTER.TOP,
    categoryId: '',
    tag: '',
    subscribed: false
  })

  expect(normalizePostsQuery({ order: 'unknown', type: 'unknown' })).toEqual({
    order: POSTS_ORDER.LATEST,
    filter: POSTS_FILTER.ALL,
    categoryId: '',
    tag: '',
    subscribed: false
  })
})

it('buildPostsQuery should omit defaults', () => {
  const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
  expect(buildPostsQuery()).toEqual({})
  expect(buildPostsQuery({ order: 'latest', filter: '' })).toEqual({})
  expect(buildPostsQuery({ order: 'hot', filter: '' })).toEqual({ order: 'hot' })
  expect(buildPostsQuery({ order: 'latest', filter: 'top' })).toEqual({ type: 'top' })
  expect(buildPostsQuery({ order: 'hot', filter: 'wonderful' })).toEqual({ order: 'hot', type: 'wonderful' })
  expect(buildPostsQuery({ subscribed: true })).toEqual({ subscribed: '1' })
  expect(buildPostsQuery({ categoryId })).toEqual({ categoryId })
})
```

- [ ] **Step 6: Delete unmounted growth views**

Run:

```bash
git rm frontend/src/views/SignInCalendarView.vue
git rm frontend/src/views/TaskCenterView.vue
```

- [ ] **Step 7: Verify the frontend cleanup slice**

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- searchService.test.js router/navigation.test.js unusedSurfaceRetirement.test.js
```

Expected: PASS.

- [ ] **Step 8: Commit the frontend cleanup slice**

```bash
git add frontend/src/api/services/searchService.js \
  frontend/src/api/services/searchService.test.js \
  frontend/src/router/navigation.js \
  frontend/src/router/navigation.test.js
git add -u frontend/src/views/SignInCalendarView.vue frontend/src/views/TaskCenterView.vue
git commit -m "refactor: retire unused frontend helpers and fallback"
```

### Task 7: Retire Analytics Debug Endpoint And Dead Types

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`
- Modify: `docs/handbook/business-flows.md`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/dto/RangeQuery.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/analytics/application/result/AnalyticsCountResult.java`

- [ ] **Step 1: Remove the analytics debug endpoint**

In `AnalyticsController.java`, delete these imports:

```java
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
```

Delete this endpoint:

```java
@GetMapping("/me")
public Result<String> me(Authentication authentication) {
    return Result.ok(CurrentUser.requireJwt(authentication).getSubject());
}
```

Keep the `/uv` and `/dau` methods unchanged.

- [ ] **Step 2: Delete dead analytics types**

Run:

```bash
git rm backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/dto/RangeQuery.java
git rm backend/community-app/src/main/java/com/nowcoder/community/analytics/application/result/AnalyticsCountResult.java
```

- [ ] **Step 3: Update analytics handbook text**

In `docs/handbook/business-flows.md`, remove this line:

```markdown
- `/api/analytics/me`：鉴权联调接口，不是业务埋点主入口。
```

Keep the current analytics query surface line:

```markdown
- `/api/analytics/**`：查询面，ADMIN / MODERATOR。
```

- [ ] **Step 4: Verify the analytics slice**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UnusedSurfaceRetirementTest,AnalyticsControllerUnitTest,AnalyticsApplicationServiceTest test
```

Expected: analytics controller/application tests pass. `UnusedSurfaceRetirementTest` still fails until Task 8 removes all placeholder classes.

- [ ] **Step 5: Commit the analytics slice**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java \
  docs/handbook/business-flows.md
git add -u backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/dto/RangeQuery.java \
  backend/community-app/src/main/java/com/nowcoder/community/analytics/application/result/AnalyticsCountResult.java
git commit -m "refactor: retire analytics debug endpoint"
```

### Task 8: Delete Placeholder Backend Types And IM Contracts

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`
- Modify: `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java`
- Delete every placeholder class listed in this task.

- [ ] **Step 1: Confirm placeholder type references are self-only or test-only**

Run:

```bash
cd /home/feng/code/project/community
rg -n -w "AuthCredential|AuthTokens|CaptchaChallenge|PasswordResetToken|RefreshTokenRecord|RegistrationCode|RegistrationSession|ReindexJobResult|AdminResolveMarketDisputeCommand|MarketWalletActionCommand|OpenImSessionRequest|RoomMemberChangedEventV1|InternalUpdatePasswordRequest|InternalUserProfileStatsResponse|LeaderboardItemResponse|RewardAccountDataObject|RewardGrantRecordDataObject|RewardLedgerEntryDataObject|UserTaskEventLogDataObject" backend -g '!**/target/**'
```

Expected before deletion: definitions only, except `InternalUpdatePasswordRequest` in `UserControllerUnitTest` and the two IM common contracts in `JsonContractsTest`.

Run:

```bash
cd /home/feng/code/project/community
rg -n -S "InternalActivationResponse|InternalAuthenticateRequest|InternalAuthenticateResponse|InternalBatchUserSummaryRequest|InternalModerationApplyRequest|InternalModerationStatusResponse|InternalRefreshTokenRecordResponse|InternalRefreshTokenRevokeFamilyRequest|InternalRefreshTokenRevokeRequest|InternalRefreshTokenStoreRequest|InternalRegisterRequest|InternalSessionProfileResponse|InternalUserByEmailResponse|InternalUserSummaryResponse" backend/community-app/src/main/java backend/community-app/src/test/java -g '!**/target/**'
```

Expected before deletion: definitions only.

- [ ] **Step 2: Remove stale user controller DTO test**

In `UserControllerUnitTest.java`, delete this import:

```java
import com.nowcoder.community.user.controller.dto.InternalUpdatePasswordRequest;
```

Delete this test method:

```java
@Test
void internalUpdatePasswordRequestShouldRejectTooShortPassword() {
    InternalUpdatePasswordRequest request = new InternalUpdatePasswordRequest();
    request.setNewPassword("Abc123!");

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(validator.validate(request)).isNotEmpty();
}
```

Remove these imports when they become unused:

```java
import jakarta.validation.Validation;
import jakarta.validation.Validator;
```

- [ ] **Step 3: Delete backend app placeholder classes**

Run:

```bash
git rm backend/community-app/src/main/java/com/nowcoder/community/search/application/result/ReindexJobResult.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthCredential.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthTokens.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/CaptchaChallenge.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PasswordResetToken.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RefreshTokenRecord.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationCode.java
git rm backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationSession.java
git rm backend/community-app/src/main/java/com/nowcoder/community/market/application/command/AdminResolveMarketDisputeCommand.java
git rm backend/community-app/src/main/java/com/nowcoder/community/market/application/command/MarketWalletActionCommand.java
```

- [ ] **Step 4: Delete stale user and social DTO classes**

Run:

```bash
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalActivationResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalAuthenticateRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalAuthenticateResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalBatchUserSummaryRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalModerationApplyRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalModerationStatusResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRecordResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRevokeFamilyRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRevokeRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenStoreRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRegisterRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalSessionProfileResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUpdatePasswordRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUserByEmailResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUserSummaryResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/LeaderboardItemResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/InternalUserProfileStatsResponse.java
```

- [ ] **Step 5: Delete stale growth dataobject classes**

Run:

```bash
git rm backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardAccountDataObject.java
git rm backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardGrantRecordDataObject.java
git rm backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardLedgerEntryDataObject.java
git rm backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/UserTaskEventLogDataObject.java
```

- [ ] **Step 6: Remove IM common contract tests and imports**

In `JsonContractsTest.java`, delete these imports:

```java
import com.nowcoder.community.im.common.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.common.session.OpenImSessionRequest;
```

Delete the two test methods that instantiate `RoomMemberChangedEventV1` and `OpenImSessionRequest`.

- [ ] **Step 7: Delete IM common contract classes**

Run:

```bash
git rm backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionRequest.java
git rm backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChangedEventV1.java
```

- [ ] **Step 8: Verify placeholder deletion**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UnusedSurfaceRetirementTest,UserControllerUnitTest test
mvn -pl community-im/im-common -Dtest=JsonContractsTest,ImCommonContractRetirementTest test
```

Expected: PASS.

- [ ] **Step 9: Commit placeholder deletion**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java \
  backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java
git add -u backend/community-app/src/main/java/com/nowcoder/community/search/application/result/ReindexJobResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthCredential.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthTokens.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/CaptchaChallenge.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PasswordResetToken.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RefreshTokenRecord.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationCode.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationSession.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/application/command/AdminResolveMarketDisputeCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/application/command/MarketWalletActionCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalActivationResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalAuthenticateRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalAuthenticateResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalBatchUserSummaryRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalModerationApplyRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalModerationStatusResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRecordResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRevokeFamilyRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenRevokeRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRefreshTokenStoreRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalRegisterRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalSessionProfileResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUpdatePasswordRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUserByEmailResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/InternalUserSummaryResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/LeaderboardItemResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/InternalUserProfileStatsResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardAccountDataObject.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardGrantRecordDataObject.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardLedgerEntryDataObject.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/UserTaskEventLogDataObject.java \
  backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionRequest.java \
  backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChangedEventV1.java
git commit -m "refactor: delete unused backend placeholder types"
```

### Task 9: Final Static And Test Verification

**Files:**
- Verify only.

- [ ] **Step 1: Run static absence checks**

Run:

```bash
cd /home/feng/code/project/community
rg -n -S "verifyCaptcha|/api/auth/captcha/verify|/captcha/verify" frontend/src backend/community-app/src/main/java backend/community-app/src/test/java docs/handbook
rg -n -S "getBlockStatus|/api/blocks/status|resolveUserByUsername|/api/users/resolve|getUserLikeCount|/api/likes/users/.*/count" frontend/src backend/community-app/src/main/java backend/community-app/src/test/java
rg -n -S "normalizePostsQuery|buildPostsQuery|SignInCalendarView|TaskCenterView" frontend/src
rg -n -S "/api/search/internal/reindex" frontend/src backend/community-app/src/main/java docs/handbook
rg -n -w "AnalyticsCountResult|RangeQuery|ReindexJobResult|AuthCredential|AuthTokens|CaptchaChallenge|PasswordResetToken|RefreshTokenRecord|RegistrationCode|RegistrationSession|UserResolveResult|UserResolveResponse|AdminResolveMarketDisputeCommand|MarketWalletActionCommand|OpenImSessionRequest|RoomMemberChangedEventV1" backend -g '!**/target/**'
```

Expected: no output from each command.

- [ ] **Step 2: Confirm intentionally preserved follow count endpoints remain**

Run:

```bash
cd /home/feng/code/project/community
rg -n -S "/api/follows/.*/followees/count|/api/follows/.*/followers/count|followees/count|followers/count" frontend/src backend/community-app/src/main/java -g '!frontend/dist/**' -g '!**/target/**'
```

Expected: backend controller/security references remain. Frontend wrapper references are absent after Task 5.

- [ ] **Step 3: Run focused backend app tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UnusedSurfaceRetirementTest,CaptchaApplicationServiceTest,AuthControllerUnitTest,BlockControllerTest,LikeControllerTest,UserReadApplicationServiceTest,UserControllerUnitTest,AnalyticsControllerUnitTest,AnalyticsApplicationServiceTest,SocialBlockQueryApiAdapterTest,SocialLikeQueryApiAdapterTest test
```

Expected: PASS.

- [ ] **Step 4: Run architecture guardrails**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS.

- [ ] **Step 5: Run focused IM common tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-im/im-common -Dtest=JsonContractsTest,ImCommonContractRetirementTest test
```

Expected: PASS.

- [ ] **Step 6: Run focused frontend tests**

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- authService.test.js socialService.test.js userService.test.js searchService.test.js router/navigation.test.js unusedSurfaceRetirement.test.js
```

Expected: PASS.

- [ ] **Step 7: Run broader module tests when the focused suites pass**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app test
mvn -pl community-im/im-common test
cd /home/feng/code/project/community/frontend
npm test
```

Expected: PASS.

- [ ] **Step 8: Commit final verification notes or fixes**

If a verification-only fix changed files, commit it:

```bash
git add backend/community-app frontend backend/community-im docs/handbook
git commit -m "test: verify unused surface retirement"
```

If no files changed during final verification, leave the branch at the Task 8 commit.

## Scope Boundaries For Future Specs

These candidates were found during the repository-wide scan but are deliberately outside this first implementation plan:
- Category subscribe/unsubscribe wrappers and endpoints.
- Market dispute, listing lifecycle, and order lifecycle endpoints.
- IM room/unread HTTP APIs.
- `TraceIdClientHttpRequestInterceptor` in `community-common`.

Create separate product/API retirement specs before deleting any of those surfaces.
