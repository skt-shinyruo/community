# Community Consistency-First Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Repository instruction overrides generic plan guidance: do not create git commits unless the user explicitly asks for them.

**Goal:** Replace unsafe and inconsistent behavior across auth, user, social/content, growth, wallet/market, and frontend integration with breaking, consistency-first behavior.

**Architecture:** Keep the current DDD tactical layering. Controllers bind and convert DTOs only; application services coordinate transactions and cross-domain APIs; domain services hold business rules; infrastructure owns MyBatis/Redis details. Breaking DTO and API shape changes are allowed because there is no historical data to preserve.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, Redis, Maven, Vue 3, Pinia, Vue Router, Vitest.

---

## File Map

- Modify `backend/community-app/src/main/resources/application.yml`: remove unsafe password-reset link exposure default.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/auth/**`: password reset response/logging, session revocation, Redis refresh-token tombstones.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/user/**`: shared password policy, user-level refresh-session revocation, public profile response without wallet fields.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/social/**`: unlike deleted content, delete stale likes, deterministic like event ids, block removes follows and follow queries filter blocks.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/content/**`: comment deletion cascade/count/score, content delete like cleanup.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/growth/**`: accept deterministic like source ids and retain idempotency.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/wallet/**`: separate spend-active checks from inbound settlement operations.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/market/**`: recoverable wallet action semantics and amount overflow checks.
- Modify `frontend/src/views/NoticeDetailView.vue`: submit UUID notice ids.
- Modify `frontend/src/views/UserProfileView.vue`: route private messages to canonical conversation id.
- Modify `frontend/src/views/MarketAddressesView.vue` and market address services/DTOs: use `defaultAddress`.
- Modify `frontend/src/views/WalletView.vue`: UUID validation before transfer submit.

## Task 1: Auth Reset Safety And Session Revocation

**Files:**
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/PasswordResetRequestResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/mail/LogMailAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserCredentialActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserCredentialActionApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRefreshTokenSessionActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/RefreshTokenSessionRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java`

- [ ] Add a failing password reset test asserting `resetLink` is always blank and `updatePasswordAndRevokeSessions` is called on confirm.
- [ ] Run `./mvnw -pl community-app -Dtest=PasswordResetApplicationServiceTest test` from `backend`; expect the new assertions to fail.
- [ ] Remove response exposure logic from `PasswordResetApplicationService.requestReset`; always return `new PasswordResetRequestResult(true, "")`.
- [ ] Change `LogMailAdapter.sendPasswordResetMail` to log recipient and subject only, not `resetLink`.
- [ ] Replace `UserCredentialActionApi.updatePassword(UUID,String)` with `updatePasswordAndRevokeSessions(UUID,String)`.
- [ ] Implement user-side revocation by adding `revokeAllByUserId(UUID userId)` to refresh-session API/repository and calling it after password update.
- [ ] Run the focused auth and user session tests; expect pass.

## Task 2: Shared Password Policy

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/PasswordPolicyDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/service/PasswordResetDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetConfirmRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/RegisterRequest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/PasswordPolicyDomainServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java`

- [ ] Add password-policy tests for blank, too short, too long, single-class weak password, and valid password.
- [ ] Run `./mvnw -pl community-app -Dtest=PasswordPolicyDomainServiceTest test`; expect missing class failure.
- [ ] Implement `PasswordPolicyDomainService.validate(String password)` with min length 8, max `ValidationLimits.PASSWORD_MAX`, and at least two character classes.
- [ ] Inject or construct the policy in registration, credential update, and reset confirmation validation.
- [ ] Add `@Size(min = 8, max = ValidationLimits.PASSWORD_MAX)` to reset and registration DTOs.
- [ ] Run focused auth/user tests; expect pass.

## Task 3: Public Profile Privacy

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserProfilePageResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserProfileApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UserProfileResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`

- [ ] Add or update controller test asserting public `GET /api/users/{userId}` JSON has no `walletBalance` or `walletStatus` fields.
- [ ] Run `./mvnw -pl community-app -Dtest=UserControllerUnitTest test`; expect failure while fields are still present.
- [ ] Remove wallet fields from profile result/response mapping.
- [ ] Keep wallet balance/status available through `/api/wallet/**` only.
- [ ] Run focused user controller tests; expect pass.

## Task 4: Social Likes, Blocks, And Growth Idempotency

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/LikeMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/FollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisFollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/FollowMapper.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java`

- [ ] Add unlike test where content resolver throws not found but an existing like is removed successfully.
- [ ] Add block test where existing A->B and B->A follows are removed when A blocks B.
- [ ] Add follow count/list test proving blocked relations are filtered.
- [ ] Add like side-effect test asserting `like-created:{actor}:{entityType}:{entityId}` is used.
- [ ] Run focused social tests; expect failures.
- [ ] Change unlike path to obtain entity owner from stored like relation when possible and delete without resolving deleted content.
- [ ] Add repository methods for deleting likes by entity and recomputing/decrementing entity-owner like counters.
- [ ] Add follow repository method `deleteUserFollowRelationsBetween(UUID a, UUID b)` and invoke it inside block transaction.
- [ ] Change follow queries/counts to filter both directions of `social_block`.
- [ ] Replace random like-created side-effect ids with deterministic ids.
- [ ] Run focused social tests; expect pass.

## Task 5: Content Deletion Consistency

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/moderation/MyBatisContentModerationAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/comment-mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/api/action/SocialLikeCleanupActionApi.java`
- Create or modify adapter under `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/api/`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostModerationApplicationServiceTest.java`

- [ ] Add comment deletion tests asserting parent deletion marks active replies deleted, decrements post `comment_count` by changed rows, and schedules score refresh.
- [ ] Add post deletion test asserting social like cleanup API is invoked for the post.
- [ ] Run focused content tests; expect failures.
- [ ] Route author and moderation comment deletion through `CommentApplicationService.deleteByModeration(...)` style application method.
- [ ] Implement comment repository cascade delete query for direct replies and nested replies supported by current model depth.
- [ ] Update post comment count by negative changed-row count only after successful delete.
- [ ] Call social like cleanup API for deleted comments and posts.
- [ ] Schedule post score refresh after comment deletion and post deletion.
- [ ] Run focused content tests; expect pass.

## Task 6: Redis Refresh Token Reuse Detection

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepositoryTest.java`

- [ ] Add Redis test asserting consumed token reuse after grace revokes the family and prevents storing a new family member.
- [ ] Run `./mvnw -pl community-app -Dtest=RedisRefreshTokenRepositoryTest test`; expect failure.
- [ ] Store revoked/tombstone records with family id, expiry, and revoked time when tokens are consumed or revoked.
- [ ] On `consume`, if active token missing but tombstone exists and outside grace, call `revokeFamily`.
- [ ] Keep tombstones until original token expiry.
- [ ] Run Redis refresh-token tests; expect pass.

## Task 7: Wallet And Market Settlement Semantics

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletAccountApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletMarketApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletMarketApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceTest.java`

- [ ] Add wallet test asserting release/refund can credit a frozen recipient while escrow/purchase still requires active spender.
- [ ] Add processor test asserting release/refund business failures leave recoverable action state and do not silently complete order.
- [ ] Add order test asserting amount overflow or amount above max is rejected before order save.
- [ ] Run focused wallet/market tests; expect failures.
- [ ] Add `requireUserWalletCanSpend(UUID)` and keep `requireUserWalletActive(UUID)` only where true active status is required.
- [ ] Remove active-recipient checks from market `releaseOrder` and `refundOrder`; keep active-spender check in `escrowOrder`.
- [ ] Add safe amount calculation using `Math.multiplyExact` and explicit max amount constant in market domain/application validation.
- [ ] Ensure processor marks non-escrow business failures as failed/recoverable with reason and leaves order pending for recovery.
- [ ] Run focused wallet/market tests; expect pass.

## Task 8: Frontend Breaking Contract Updates

**Files:**
- Modify: `frontend/src/views/NoticeDetailView.vue`
- Modify: `frontend/src/views/UserProfileView.vue`
- Modify: `frontend/src/views/MarketAddressesView.vue`
- Modify: `frontend/src/views/WalletView.vue`
- Modify: `frontend/src/api/services/marketService.js`
- Modify: `frontend/src/utils/opaqueId.js`
- Test: `frontend/src/views/noticeDetailState.test.js` or existing related test file
- Test: `frontend/src/views/conversationDetailState.test.js` or profile route test file
- Test: `frontend/src/views/marketAddressesState.test.js` or existing related test file
- Test: `frontend/src/views/walletState.test.js` or existing related test file

- [ ] Add frontend test for notice IDs preserving UUID strings.
- [ ] Add frontend test for canonical conversation id from current user and profile user.
- [ ] Add frontend test that address payload uses `defaultAddress` and not `isDefault`.
- [ ] Add frontend test rejecting non-UUID transfer targets before API call.
- [ ] Run `npm test` from `frontend`; expect new tests to fail.
- [ ] Change notice mark-read collection to `filter(Boolean)` over id strings.
- [ ] Add helper to build canonical conversation id from two UUIDs and use it in profile message link.
- [ ] Rename address request payload field to `defaultAddress` and update backend DTOs/controllers accordingly.
- [ ] Add UUID validation helper and use it in wallet transfer submit.
- [ ] Run frontend tests; expect pass.

## Task 9: Final Verification

**Files:**
- No production files expected.

- [ ] Run focused backend tests for changed domains from `backend`:
  - `./mvnw -pl community-app -Dtest=PasswordResetApplicationServiceTest,UserControllerUnitTest,LikeApplicationServiceTest,BlockApplicationServiceTest,CommentApplicationServiceTest,PostModerationApplicationServiceTest,RedisRefreshTokenRepositoryTest,WalletMarketApplicationServiceTest,MarketWalletActionProcessorApplicationServiceTest,MarketOrderApplicationServiceTest test`
- [ ] Run full backend module tests if focused tests pass: `./mvnw -pl community-app test`.
- [ ] Run frontend tests from `frontend`: `npm test`.
- [ ] Run frontend build from `frontend`: `npm run build`.
- [ ] Check `git status --short` and report changed files. Do not commit unless the user explicitly requests it.

## Self-Review

- Spec coverage: every acceptance criterion from `2026-05-02-community-consistency-first-hardening-design.md` maps to Tasks 1-9.
- Placeholder scan: no `TBD` or incomplete steps remain.
- Type consistency: breaking DTO rename is consistently `defaultAddress`; deterministic like event id uses `like-created:{actorUserId}:{entityType}:{entityId}`; wallet settlement separates spend checks from inbound settlement.
