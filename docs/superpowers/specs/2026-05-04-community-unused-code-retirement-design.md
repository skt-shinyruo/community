# Community Unused Code Retirement Design

**Date:** 2026-05-04
**Status:** Draft
**Owner:** Codex

---

## 1. Goal

本设计用于删除仓库内已经没有生产调用路径的接口、前端封装、未挂载页面、旧 DTO/command/model 占位类，以及历史兼容 fallback。

触发点是 `/api/auth/captcha/verify` 检查：它有后端入口和前端 service wrapper，但仓库内没有实际页面或运行时代码调用。继续全仓库扫描后，发现同类问题分散在 auth、social、user、subscription、market、search、analytics、growth、IM common 和前端路由工具里。

目标不是“按前端是否调用”粗暴删除所有后端能力，而是把删除分成高置信与需要产品确认的两类：

- 高置信死代码：仓库内无生产调用、无动态入口、无文档承诺的内容，直接退休。
- 后端已实现但 UI 未接入的业务能力：只有在当前产品确认不保留 API-first 能力时删除；否则只移除无用前端 wrapper 或补齐 UI。

---

## 2. Current Findings

### 2.1 High-Confidence Dead Surfaces

These have no production call sites in non-generated source.

| Area | Current Surface | Finding |
|---|---|---|
| Auth captcha | `POST /api/auth/captcha/verify`, `verifyCaptcha()` | 独立校验入口无调用；验证码实际随 login/register/password reset 请求内联校验。 |
| Social block | `GET /api/blocks/status`, `getBlockStatus()` | 无前端调用；拉黑状态当前通过具体用户交互或列表推导，不需要独立入口。 |
| User resolve | `GET /api/users/resolve`, `resolveUserByUsername()` | 无前端调用；当前页面按用户 id 和 batch summary 读取用户。 |
| User like count | `GET /api/likes/users/{userId}/count`, `getUserLikeCount()` | 无前端调用；用户主页使用 profile/summary surface，不调用此 wrapper。 |
| Search reindex | fallback `POST /api/search/internal/reindex` | 后端已迁到 `POST /api/ops/search/reindex`，没有 `/api/search/internal/reindex` controller。 |
| Frontend growth views | `SignInCalendarView.vue`, `TaskCenterView.vue` | 没有 router/import 生产引用，且 growth legacy surface 已有 retirement test。 |
| Frontend router helpers | `normalizePostsQuery`, `buildPostsQuery` | 只有测试引用；生产 feed 直接使用细粒度 normalize helpers。 |
| Analytics debug | `GET /api/analytics/me` | handbook 标注为鉴权联调接口，不是业务主入口；前端无调用。 |
| Analytics DTO/result | `RangeQuery`, `AnalyticsCountResult` | controller 直接接收 `start/end` 并返回 `Long`，这两个类型无生产引用。 |
| Auth domain placeholders | `AuthCredential`, `AuthTokens`, `CaptchaChallenge`, `PasswordResetToken`, `RefreshTokenRecord`, `RegistrationCode`, `RegistrationSession` | 迁移后只剩定义，无生产引用。 |
| Search result placeholder | `ReindexJobResult` | 当前 reindex 使用 `SearchReindexResult`，该 record 无生产引用。 |
| Market commands | `AdminResolveMarketDisputeCommand`, `MarketWalletActionCommand` | 无生产引用。 |
| User internal DTO leftovers | `Internal*Request`, `Internal*Response`, `LeaderboardItemResponse` leftovers | 多数只剩定义；`InternalUpdatePasswordRequest` 只被旧测试引用。 |
| Growth dataobject leftovers | `RewardAccountDataObject`, `RewardGrantRecordDataObject`, `RewardLedgerEntryDataObject`, `UserTaskEventLogDataObject` | 对应 mapper/runtime 已退休或仅 insert，不需要这些 dataobject。 |
| IM common test-only contracts | `OpenImSessionRequest`, `RoomMemberChangedEventV1` | 仅 JSON contract test 引用，无生产使用。 |

### 2.2 Wrapper-Only Dead Code

These frontend functions are unused, but their backend endpoints still have direct production callers or useful current behavior.

| Wrapper | Decision |
|---|---|
| `countFollowees()` / `countFollowers()` | Delete wrappers only. Keep backend count endpoints because `HomeView.vue` calls them directly. |
| `subscribeCategory()` / `unsubscribeCategory()` | Product decision needed. Current UI uses subscribed filtering and list loading, but no subscribe/unsubscribe controls. If category subscription writes are not part of the active product, delete backend endpoints too; otherwise keep endpoints and add UI later. |

### 2.3 Backend API-First Candidates

These backend endpoints are real business capabilities but are not currently wired into frontend production views.

| Area | Current Surface | Recommended Default |
|---|---|---|
| Market listing lifecycle | update / pause / resume / close listing endpoints | Keep unless product explicitly retires seller listing management. |
| Market order lifecycle | deliver / ship / confirm / cancel endpoints | Keep unless product explicitly retires order fulfillment workflow. |
| Market disputes | buyer open dispute; seller accept/reject dispute | Delete only if dispute workflow is removed from the product. Admin dispute resolution UI currently exists, so removing buyer/seller dispute creation makes that admin UI less useful. |
| IM room/unread HTTP API | room create/join/leave/messages/read and unread summary | Keep unless group chat is retired. WebSocket still supports `sendRoomText`, and room update notifications exist. |
| `TraceIdClientHttpRequestInterceptor` | common-web helper with no in-repo production use | Defer deletion unless `community-common` has no external consumers. |

---

## 3. Scope

### 3.1 In Scope For First Retirement Pass

Delete high-confidence dead code:

- `frontend/src/api/services/authService.js`
  - remove `verifyCaptcha`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
  - remove `POST /captcha/verify`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java`
  - remove permitAll for `POST /api/auth/captcha/verify`.
- Auth command/DTO methods used only by that endpoint:
  - `auth/application/command/VerifyCaptchaCommand.java`
  - `auth/controller/dto/CaptchaVerifyRequest.java`
  - `AuthApplicationService.verifyCaptcha(...)`
  - `CaptchaApplicationService.verify(VerifyCaptchaCommand)`
  - keep `CaptchaApplicationService.verify(String, String)` because login/register/reset use it.
- Remove unused frontend wrappers:
  - `blockService.getBlockStatus`
  - `userService.resolveUserByUsername`
  - `socialService.getUserLikeCount`
  - `socialService.countFollowees`
  - `socialService.countFollowers`
- Remove matching high-confidence backend endpoints:
  - `BlockController.status`
  - `UserController.resolveByUsername`
  - `LikeController.userLikeCount`
- Remove search reindex legacy fallback to `/api/search/internal/reindex`.
- Delete unmounted frontend views:
  - `frontend/src/views/SignInCalendarView.vue`
  - `frontend/src/views/TaskCenterView.vue`
- Remove unused router helpers and tests:
  - `normalizePostsQuery`
  - `buildPostsQuery`
  - matching test cases in `frontend/src/router/navigation.test.js`
- Remove analytics debug/dead DTO/result:
  - `AnalyticsController.me`
  - `analytics/controller/dto/RangeQuery.java`
  - `analytics/application/result/AnalyticsCountResult.java`
  - handbook mention of `/api/analytics/me`.
- Delete unused backend placeholder types listed in section 2.1 after verifying they have no production refs.

### 3.2 Conditional Scope

Do this only after explicit product confirmation during implementation planning:

- Delete category subscribe/unsubscribe write endpoints and wrappers, or keep them and add UI.
- Delete market dispute buyer/seller endpoints and service methods, or keep them and add UI.
- Delete market listing/order lifecycle endpoints, or keep them as API-first seller/order workflow.
- Delete IM room/unread HTTP APIs, or keep them as group-chat support.
- Delete `TraceIdClientHttpRequestInterceptor` from `common-web`, or keep it as reusable library API.

### 3.3 Non-Goals

This cleanup must not:

- remove captcha generation or internal captcha verification used by login/register/password reset;
- remove `/api/auth/captcha`;
- remove `GET /api/follows/{userId}/followees/count` or `GET /api/follows/{userId}/followers/count`, because `HomeView.vue` currently calls them directly;
- remove dynamic avatar upload endpoint `/api/users/{userId}/avatar/upload`; it is returned by upload-token and called through `uploadUrl`;
- remove `POST /api/ops/search/reindex`;
- redesign market, social, user, or growth domain models;
- change database schema unless a deleted mapper/resource has a matching already-retired table assertion.

---

## 4. Target Design

### 4.1 Route Surface

The public HTTP surface should only expose routes with one of these justifications:

1. a frontend production caller;
2. a documented external/API-first contract;
3. an internal dynamic caller such as avatar upload-token;
4. a job/admin/operator path documented in handbook;
5. a deliberate future-facing surface with an owner and follow-up plan.

Anything else should either be deleted or moved to a clearly documented deferred list.

### 4.2 Frontend Service Layer

`frontend/src/api/services/*Service.js` should contain only production-used wrappers or wrappers that are intentionally exported as a stable shared API.

For this cleanup:

- delete wrappers with zero production call sites;
- if a direct page call already exists and the wrapper is otherwise unused, prefer deletion over rewiring unless the page is already being touched for behavior;
- remove tests that only protect deleted wrappers.

### 4.3 Backend Application Layer

When removing endpoint-only paths:

- remove controller DTOs and commands that exist only for the retired endpoint;
- remove facade methods that only forward to retired commands;
- preserve application/domain methods used by active business flows;
- update security rules so no permitAll entry remains for deleted routes.

This keeps the DDD layering intact: inbound adapters continue to call same-domain application services only, and deletion should not introduce legacy `Controller -> raw Service` paths.

### 4.4 Retired-Type Cleanup

Dead Java classes should be removed only after a production-source and resource search proves no real use:

```bash
rg -n -w "<ClassName>" backend -g '!**/target/**'
```

For MyBatis data objects, the search must include mapper XML:

```bash
rg -n -w "<ClassName>|fully.qualified.ClassName" backend/community-app/src/main/resources/mapper backend/community-app/src/main/java
```

For shared modules such as `community-common` and `im-common`, deletion requires checking both production source and contract tests. Test-only contract coverage must be removed or rewritten to cover the surviving contract.

---

## 5. File-Level Retirement Plan

### 5.1 Auth Captcha Verify Endpoint

Delete or modify:

- `frontend/src/api/services/authService.js`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/AuthApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/command/VerifyCaptchaCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/CaptchaVerifyRequest.java`
- `docs/handbook/auth-login-session-flow.md`

Keep:

- `IssueCaptchaCommand`
- `CaptchaIssueResponse`
- `CaptchaApplicationService.verify(String, String)`
- internal captcha repository verification behavior.

### 5.2 Social/User Dead Wrappers And Endpoints

Delete or modify:

- `frontend/src/api/services/blockService.js`
- `frontend/src/api/services/userService.js`
- `frontend/src/api/services/socialService.js`
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/BlockController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java`
- relevant controller tests.

Preserve:

- block/unblock/list behavior;
- user profile, recent posts/comments, batch summary;
- like count/status/counts/statuses used by feed/detail hydration.

### 5.3 Search Legacy Fallback

Delete:

- fallback branch in `frontend/src/api/services/searchService.js` that calls `/api/search/internal/reindex`.

Update:

- `frontend/src/api/services/searchService.test.js`, if it asserts the fallback.
- docs only if any handbook still mentions the old route; current handbook uses `/api/ops/search/reindex`.

### 5.4 Frontend Unmounted Growth Views

Delete:

- `frontend/src/views/SignInCalendarView.vue`
- `frontend/src/views/TaskCenterView.vue`

No route removal is expected because these files are not registered in `frontend/src/router/index.js`.

### 5.5 Router Helper Cleanup

Modify:

- `frontend/src/router/navigation.js`
  - remove `normalizePostsQuery`
  - remove `buildPostsQuery`
  - keep `normalizePostsOrder`, `normalizePostsFilter`, `normalizePostsCategoryId`, `normalizePostsTag`, and `normalizePostsSubscribed`
- `frontend/src/router/navigation.test.js`
  - remove tests that only cover deleted helpers.

### 5.6 Analytics Debug Cleanup

Modify/delete:

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`
  - remove `/me`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/dto/RangeQuery.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/application/result/AnalyticsCountResult.java`
- `docs/handbook/business-flows.md`
  - remove or revise mention that `/api/analytics/me` is a current auth debug endpoint.

### 5.7 Backend Placeholder Types

Delete after final `rg -n -w` confirmation:

```text
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthCredential.java
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/AuthTokens.java
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/CaptchaChallenge.java
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PasswordResetToken.java
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RefreshTokenRecord.java
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationCode.java
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/RegistrationSession.java
backend/community-app/src/main/java/com/nowcoder/community/search/application/result/ReindexJobResult.java
backend/community-app/src/main/java/com/nowcoder/community/market/application/command/AdminResolveMarketDisputeCommand.java
backend/community-app/src/main/java/com/nowcoder/community/market/application/command/MarketWalletActionCommand.java
backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionRequest.java
backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChangedEventV1.java
```

Delete user/internal DTO leftovers that still have no production references. Update or remove stale tests that only instantiate those old DTOs.

Delete growth dataobject leftovers only if mapper XML and mapper interfaces do not reference them:

```text
backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardAccountDataObject.java
backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardGrantRecordDataObject.java
backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/RewardLedgerEntryDataObject.java
backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/dataobject/UserTaskEventLogDataObject.java
```

---

## 6. Guardrails

Add or extend retirement assertions where practical.

### 6.1 Backend Retirement Test

Add a focused test, for example:

```text
backend/community-app/src/test/java/com/nowcoder/community/app/retirement/UnusedSurfaceRetirementTest.java
```

It should assert removed classes stay off the classpath:

- `CaptchaVerifyRequest`
- `VerifyCaptchaCommand`
- auth placeholder records
- analytics dead DTO/result
- search dead result
- market dead commands
- user internal DTO leftovers selected for deletion.

If route absence is easy to assert through `MockMvc`, add route-level 404 assertions for:

- `POST /api/auth/captcha/verify`
- `GET /api/blocks/status`
- `GET /api/users/resolve`
- `GET /api/likes/users/{userId}/count`
- `GET /api/analytics/me`

Otherwise rely on controller unit tests plus static search verification.

### 6.2 Static Verification Commands

After deletion, these searches must return no production references except allowed docs/spec history:

```bash
rg -n -S "verifyCaptcha|/api/auth/captcha/verify|/captcha/verify" frontend/src backend/community-app/src/main/java backend/community-app/src/test/java docs/handbook
rg -n -S "getBlockStatus|/api/blocks/status|resolveUserByUsername|/api/users/resolve|getUserLikeCount|/api/likes/users/.*/count" frontend/src backend/community-app/src/main/java backend/community-app/src/test/java
rg -n -S "normalizePostsQuery|buildPostsQuery|SignInCalendarView|TaskCenterView" frontend/src
rg -n -S "/api/search/internal/reindex" frontend/src backend/community-app/src/main/java docs/handbook
rg -n -w "AnalyticsCountResult|RangeQuery|ReindexJobResult|AuthCredential|AuthTokens|CaptchaChallenge|PasswordResetToken|RefreshTokenRecord|RegistrationCode|RegistrationSession" backend -g '!**/target/**'
```

The follow count endpoints are allowed to remain:

```bash
rg -n -S "/api/follows/.*/followees/count|/api/follows/.*/followers/count" frontend/src backend/community-app/src/main/java
```

Expected: `HomeView.vue` and `FollowController`/security rules still reference them.

---

## 7. Testing Strategy

### 7.1 Frontend

Run focused frontend tests affected by deleted wrappers and helpers:

```bash
cd /home/feng/code/project/community/frontend
npm test -- authService.test.js socialService.test.js subscriptionService.test.js searchService.test.js router/navigation.test.js
```

If Vitest does not support passing those names directly in the current script, run:

```bash
cd /home/feng/code/project/community/frontend
npm test
```

Expected:

- no tests import deleted wrappers;
- router navigation tests cover only surviving helpers;
- search reindex test expects only `/api/ops/search/reindex`.

### 7.2 Backend

Run focused controller/application tests for changed areas:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=AuthControllerUnitTest,CaptchaApplicationServiceTest,BlockControllerTest,UserControllerUnitTest,LikeControllerTest,AnalyticsControllerUnitTest,UnusedSurfaceRetirementTest test
```

Run architecture guardrails because controller/security route boundaries changed:

```bash
cd /home/feng/code/project/community/backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

If IM common contracts are deleted, run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-im/im-common test
```

### 7.3 Manual Smoke

Smoke paths that must still work:

- login/register/password reset with captcha;
- posts feed and post detail hydration;
- Home dashboard follow/follower counts;
- avatar upload through Settings;
- ops search reindex;
- analytics UV/DAU page.

---

## 8. Rollout And Risk

### 8.1 Main Risk

The main risk is deleting API-first endpoints that external clients or future UI work still expect.

Mitigation:

- first implementation pass deletes only high-confidence dead surfaces;
- conditional API-first candidates require explicit product approval before removal;
- docs and tests must distinguish removed surfaces from intentionally retained API-first surfaces.

### 8.2 Compatibility

This cleanup intentionally breaks direct clients of retired endpoints:

- `POST /api/auth/captcha/verify`
- `GET /api/blocks/status`
- `GET /api/users/resolve`
- `GET /api/likes/users/{userId}/count`
- `GET /api/analytics/me`

No frontend production code in this repository currently calls them. If external compatibility is required, mark the endpoint deprecated first instead of deleting it.

### 8.3 Rollback

Rollback is source-level:

- restore deleted route/controller/service wrapper files from git;
- restore security permit rules only for routes that are intentionally public;
- re-run focused tests.

Database rollback is not expected for the first pass because no schema change is planned.

---

## 9. Open Decisions Before Implementation

These decisions should be resolved before writing the implementation plan:

1. Should category subscribe/unsubscribe remain as API-first behavior, or be retired with the unused wrappers?
2. Should buyer/seller market dispute APIs remain because admin dispute resolution exists, or should the whole non-admin dispute workflow be retired?
3. Should market listing/order lifecycle endpoints remain API-first until UI catches up?
4. Should IM room/unread HTTP APIs remain for group chat?
5. Is `community-common` treated as internal-only for this repository, allowing deletion of unused helpers such as `TraceIdClientHttpRequestInterceptor`?

Recommended default:

- delete only high-confidence dead surfaces in the first implementation plan;
- keep market lifecycle, market dispute, IM room/unread, and common-web helper decisions out of the first pass unless the product owner explicitly approves retirement.
