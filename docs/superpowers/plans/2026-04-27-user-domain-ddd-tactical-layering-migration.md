# User Domain DDD Tactical Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the `user` domain from mixed `service`/DTO/mapper-centered orchestration to strict DDD Tactical Layering.

**Architecture:** `UserController`, `AdminUserController`, and `FilesController` call same-domain `user.application.*ApplicationService` only. Application services use `user.domain.model`, `user.domain.service`, `user.domain.repository`, and foreign owner-domain `api.*`; MyBatis, Redis, avatar storage, and Spring event publication sit behind `user.infrastructure` adapters. Same-domain `user.api.*` implementations remain as thin foreign-caller adapters and must not be used by user controllers.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, Redis, ArchUnit, JUnit 5, Mockito, Maven.

---

## File Structure Map

### Application Entries

- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java`
  Same-domain and controller-facing read entry for resolve, batch summary, profile lookup, and existence checks.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserProfileApplicationService.java`
  Page-level profile orchestration using user repository data plus social/content/growth/wallet foreign APIs.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
  Avatar upload-token, upload, and avatar update use cases.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserFileApplicationService.java`
  File serving entry for avatar resources.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
  Admin user search and role update use cases.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
  Owner entry for user mute/ban/unmute/unban and moderation-state queries.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
  Pending registration, activation, and cleanup owner entry.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
  Authentication, password update, and authority projection owner entry.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserPointsApplicationService.java`
  User points/reward projection orchestration behind the foreign points award API.

### Application Commands And Results

- `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/UpdateUserRoleCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/ApplyUserModerationCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserSummaryResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserProfileResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserProfilePageResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserResolveResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AdminUserResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarUploadTokenResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/AvatarFileResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserModerationStateResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/PendingRegistrationUserResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserCredentialResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserAuthenticationResult.java`

### Application Ports

- `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/AvatarStoragePort.java`
  Application-facing avatar storage and ticket contract.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserAuditLogPort.java`
  Admin audit logging contract.

### Domain

- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserSummary.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserProfile.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserModerationStatus.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserReadDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRoleDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserModerationDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserCredentialDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/event/UserPolicyEventPublisher.java`

### Infrastructure

- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/UserMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/UserDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarStorageProvider.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/AvatarStorageRouter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/LocalAvatarStorageProvider.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/R2AvatarStorageProvider.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/StoredAvatar.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/LocalUserPolicyEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/audit/Slf4jUserAuditLogAdapter.java`

### Foreign API Adapters

- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserReadQueryApiAdapter.java`
  Implements `UserLookupQueryApi` and `UserProfileQueryApi`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationApiAdapter.java`
  Implements `UserModerationActionApi` and `UserModerationQueryApi`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserRegistrationApiAdapter.java`
  Implements `UserRegistrationActionApi` and `UserPendingRegistrationQueryApi`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserCredentialApiAdapter.java`
  Implements `UserCredentialQueryApi` and `UserCredentialActionApi`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserPointsAwardApiAdapter.java`
  Implements `UserPointsAwardActionApi` and delegates to `UserPointsApplicationService`.

---

## Task 1: Establish User Domain Repository And Model Foundation

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserSummary.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserProfile.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserModerationStatus.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepository.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/user/mapper/UserMapper.java` -> `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/UserMapper.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/user/entity/User.java` -> `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/UserDataObject.java`
- Modify: `backend/community-app/src/main/resources/mapper/user_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepositoryTest.java`

- [x] **Step 1: Write repository contract tests**

Create `MyBatisUserRepositoryTest` with coverage for summary lookup, profile lookup, batch summaries, header update, role update, moderation timestamp update, and moderation scan.

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.infrastructure.persistence.MyBatisUserRepositoryTest test
```

Expected: compile failure until `UserRepository`, domain models, and `MyBatisUserRepository` exist.

- [x] **Step 2: Add domain model records**

Create immutable records:

```java
public record UserSummary(UUID id, String username, String headerUrl, int type) {}
public record UserProfile(UUID id, String username, String headerUrl, int type, int status, Date createTime, int score) {}
public record UserModerationStatus(UUID userId, Instant muteUntil, Instant banUntil) {}
```

Create `UserAccount` as the persistence-facing aggregate snapshot with id, username, encoded password, salt, email, type, status, headerUrl, createTime, score, muteUntil, and banUntil.

- [x] **Step 3: Add repository interface**

Create `UserRepository` with these methods:

```java
Optional<UserAccount> findById(UUID userId);
Optional<UserAccount> findByUsername(String username);
Optional<UserAccount> findByEmail(String email);
Optional<UserProfile> findProfileById(UUID userId);
List<UserSummary> listSummariesByIds(List<UUID> userIds);
void updateHeaderUrl(UUID userId, String headerUrl);
void updateRole(UUID userId, int type);
void updatePassword(UUID userId, String encodedPassword);
void updateModerationUntil(UUID userId, Instant muteUntil, Instant banUntil);
List<UserModerationStatus> scanModerationStatesAfterId(UUID afterUserId, int limit);
```

- [x] **Step 4: Move mapper/dataobject to infrastructure**

Use `git mv` for the mapper and entity files. Rename `User` to `UserDataObject`, update `user_mapper.xml` namespace/resultType values, and keep the SQL ids unchanged.

- [x] **Step 5: Implement `MyBatisUserRepository`**

Map between `UserDataObject` and domain records. Throw `BusinessException(INTERNAL_ERROR, "...失败")` when update methods affect no rows, preserving existing Chinese messages:

```text
updateHeaderUrl -> 更新头像失败
updateRole -> 更新用户角色失败
updatePassword -> 更新密码失败
updateModerationUntil -> 更新处罚状态失败
```

- [x] **Step 6: Run foundation tests and guardrails**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.infrastructure.persistence.MyBatisUserRepositoryTest,DddLayeringArchTest,DomainBoundaryArchTest test
```

Expected: PASS.

---

## Task 2: Move User Read And Profile Entries To `user.application`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserProfileApplicationService.java`
- Create: result records listed in the File Structure Map
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserReadDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserReadQueryApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserReadApplicationService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfileApplicationService.java`
- Delete after replacement or move: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserProfileApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`

- [x] **Step 1: Write application tests**

Move the existing read/profile service tests to `user.application`. Update assertions to use `UserSummaryResult`, `UserProfileResult`, `UserResolveResult`, and `UserProfilePageResult` instead of HTTP DTOs or same-domain `api.model` types.

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserReadApplicationServiceTest,com.nowcoder.community.user.application.UserProfileApplicationServiceTest test
```

Expected: compile failure until the new application services and result records exist.

- [x] **Step 2: Implement read application service**

`UserReadApplicationService` depends on `UserRepository` and `UserReadDomainService`. It provides:

```java
UserSummaryResult getSummaryById(UUID userId);
UserSummaryResult getSummaryByUsername(String username);
UserSummaryResult findSummaryByEmailOrNull(String email);
List<UserSummaryResult> listSummariesByIds(List<UUID> userIds);
UserProfileResult getProfile(UUID userId);
void requireExistingUser(UUID userId);
UserResolveResult resolveByUsername(String username);
List<UserSummaryResult> listSummaryResultsByIds(List<UUID> rawUserIds);
```

Preserve current behavior: null id rejects with `userId 非法`, blank username rejects with `username 不能为空`, `resolveByUsername` throws `USER_NOT_FOUND` for missing user, batch summary deduplicates, caps at 200, and preserves request order.

- [x] **Step 3: Implement profile application service**

`UserProfileApplicationService` depends on `UserReadApplicationService`, `SocialLikeQueryApi`, `SocialFollowQueryApi`, `PostReadQueryApi`, `UserLevelQueryApi`, and `WalletAccountQueryApi`. It must not depend on `user.service.UserSocialProfileService`, `user.dto.*`, or same-domain `user.api.*`.

Methods:

```java
UserProfilePageResult get(UUID viewerId, UUID userId);
List<UserProfilePageResult.RecentPostSummaryResult> listRecentPosts(UUID userId, Integer page, Integer size);
List<UserProfilePageResult.RecentCommentItemResult> listRecentComments(UUID userId, Integer page, Integer size);
```

- [x] **Step 4: Add foreign API adapter**

Create `UserReadQueryApiAdapter` in `user.service` implementing `UserLookupQueryApi` and `UserProfileQueryApi`. It delegates to `user.application.UserReadApplicationService` and maps application results to `user.api.model` records.

- [x] **Step 5: Rewire controller and tests**

Update `UserController` to import `user.application.UserReadApplicationService`, `user.application.UserProfileApplicationService`, and application result records. Controller maps application results to HTTP DTOs.

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=UserControllerUnitTest,com.nowcoder.community.user.application.UserReadApplicationServiceTest,com.nowcoder.community.user.application.UserProfileApplicationServiceTest,ControllerBoundaryArchTest,DddLayeringArchTest test
```

Expected: PASS.

Verified on 2026-04-28:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=UserControllerUnitTest,com.nowcoder.community.user.application.UserReadApplicationServiceTest,com.nowcoder.community.user.application.UserProfileApplicationServiceTest,ControllerBoundaryArchTest,DddLayeringArchTest test
mvn -pl community-app -am -Dtest=PublicReadEndpointSecurityTest,UserControllerLoggingTest,DtoBoundaryArchTest test
```

Result: PASS.

---

## Task 3: Move Avatar And File Use Cases To `user.application`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserFileApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/AvatarStoragePort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/avatar/UserAvatarStorageAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserAvatarApplicationService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserFileApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserFileApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java`

- [x] **Step 1: Write avatar application tests**

Cover:

```text
createUploadToken rejects non-self actor with FORBIDDEN
createUploadToken delegates to AvatarStoragePort and returns AvatarUploadTokenResult
upload rejects non-self actor
upload delegates to AvatarStoragePort
updateAvatar consumes upload ticket, builds URL, and updates UserRepository header URL
```

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserAvatarApplicationServiceTest test
```

Expected: compile failure until the new application service and port exist.

- [x] **Step 2: Add avatar storage port and adapter**

`AvatarStoragePort` exposes:

```java
AvatarUploadTokenResult createUploadToken(UUID userId);
void upload(UUID userId, String fileName, MultipartFile file);
void assertAndConsumeUploadTicket(UUID userId, String fileName);
String buildAvatarUrl(String fileName);
AvatarFileResult loadAvatarOrNull(String requestUri);
```

`UserAvatarStorageAdapter` wraps the current `AvatarService` and storage router behavior. The application package must not depend on `AvatarStorageRouter`, `StringRedisTemplate`, or provider implementations.

- [x] **Step 3: Implement avatar/file application services**

Move self-checking into `UserAvatarApplicationService` and preserve the message `只能操作自己的头像`. Move file-key validation into `UserFileApplicationService` and preserve `fileKey 非法`.

- [x] **Step 4: Rewire controllers and run tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=UserControllerUnitTest,FilesControllerStorageRoutingTest,com.nowcoder.community.user.application.UserAvatarApplicationServiceTest,com.nowcoder.community.user.application.UserFileApplicationServiceTest,DddLayeringArchTest test
```

Expected: PASS.

Verified on 2026-04-28:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserAvatarApplicationServiceTest,com.nowcoder.community.user.application.UserFileApplicationServiceTest test
mvn -pl community-app -am -Dtest=UserControllerUnitTest,FilesControllerStorageRoutingTest,com.nowcoder.community.user.application.UserAvatarApplicationServiceTest,com.nowcoder.community.user.application.UserFileApplicationServiceTest,DddLayeringArchTest test
mvn -pl community-app -am -Dtest=UserControllerLoggingTest,PublicReadEndpointSecurityTest,ControllerBoundaryArchTest,DtoBoundaryArchTest test
```

Result: PASS.

---

## Task 4: Move Admin Role Use Cases To `user.application + user.domain`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/UpdateUserRoleCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRoleDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserAuditLogPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/audit/Slf4jUserAuditLogAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserApplicationService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserRoleDomainServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java`

- [x] **Step 1: Write domain and application tests**

Cover these existing rules:

```text
search requires one of userId/username/email
search trims username/email and returns null when target missing
role update rejects null request command
role update requires confirm=true
role update requires nonblank reason
role update requires targetUserId
role update rejects missing target user with 目标用户不存在
role update rejects admin self-downgrade with 不允许降级自己的管理员权限
role update returns without write when role is unchanged
role update persists changed role and writes audit log
```

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.AdminUserApplicationServiceTest,com.nowcoder.community.user.domain.service.UserRoleDomainServiceTest test
```

Expected: compile failure until the new command, domain service, application service, and audit port exist.

- [x] **Step 2: Implement command and domain service**

`UpdateUserRoleCommand` fields:

```java
UUID actorUserId;
UUID targetUserId;
int type;
String reason;
boolean confirm;
```

`UserRoleDomainService` validates command shape and self-downgrade using `UserAccount` target snapshot.

- [x] **Step 3: Implement admin application service**

`AdminUserApplicationService` depends on `UserRepository`, `UserRoleDomainService`, and `UserAuditLogPort`. It returns `AdminUserResult`, not `AdminUserResponse`, and takes `UpdateUserRoleCommand`, not `UpdateUserRoleRequest`.

- [x] **Step 4: Rewire controller and run tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=AdminUserControllerUnitTest,com.nowcoder.community.user.application.AdminUserApplicationServiceTest,com.nowcoder.community.user.domain.service.UserRoleDomainServiceTest,ControllerBoundaryArchTest,DddLayeringArchTest test
```

Expected: PASS.

Verified on 2026-04-28:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.AdminUserApplicationServiceTest,com.nowcoder.community.user.domain.service.UserRoleDomainServiceTest test
mvn -pl community-app -am -Dtest=AdminUserControllerUnitTest,com.nowcoder.community.user.application.AdminUserApplicationServiceTest,com.nowcoder.community.user.domain.service.UserRoleDomainServiceTest,ControllerBoundaryArchTest,DddLayeringArchTest test
mvn -pl community-app -am -Dtest=DtoBoundaryArchTest,PublicReadEndpointSecurityTest test
```

Result: PASS.

---

## Task 5: Move User Moderation To `user.application + user.domain`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/ApplyUserModerationCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserModerationDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/event/UserPolicyEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/LocalUserPolicyEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationApiAdapter.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationApplicationService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserModerationDomainServiceTest.java`

- [x] **Step 1: Write moderation application and domain tests**

Preserve existing behavior:

```text
getModerationState projects muteUntil and banUntil
scan clamps afterUserId null to zero UUID and limit to 1..500
mute sets muteUntil and preserves banUntil
ban sets banUntil and preserves muteUntil
unmute clears muteUntil
unban clears banUntil
blank action -> action 不能为空
unsupported action -> action 非法
missing user -> USER_NOT_FOUND
successful write publishes UserPolicyChangedPayload
```

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserModerationApplicationServiceTest,com.nowcoder.community.user.domain.service.UserModerationDomainServiceTest test
```

Expected: compile failure until new classes exist.

- [x] **Step 2: Implement domain decision logic**

`UserModerationDomainService` normalizes `mute`, `ban`, `unmute`, `unban`, clamps duration seconds to `0..31536000`, and computes the next `UserModerationStatus` from the current status and `Instant now`.

- [x] **Step 3: Implement application service and API adapter**

`UserModerationApplicationService` depends on `UserRepository`, `UserModerationDomainService`, and `UserPolicyEventPublisher`. `UserModerationApiAdapter` implements `UserModerationActionApi` and `UserModerationQueryApi` for foreign callers.

- [x] **Step 4: Run focused moderation tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserModerationApplicationServiceTest,com.nowcoder.community.user.domain.service.UserModerationDomainServiceTest,DddLayeringArchTest,DomainBoundaryArchTest test
```

Expected: PASS.

Verified on 2026-04-28:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserModerationApplicationServiceTest,com.nowcoder.community.user.domain.service.UserModerationDomainServiceTest,com.nowcoder.community.user.infrastructure.event.LocalUserPolicyEventPublisherTest,com.nowcoder.community.user.service.UserModerationApiAdapterTest test
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserModerationApplicationServiceTest,com.nowcoder.community.user.domain.service.UserModerationDomainServiceTest,com.nowcoder.community.user.infrastructure.event.LocalUserPolicyEventPublisherTest,com.nowcoder.community.user.service.UserModerationApiAdapterTest,DddLayeringArchTest,DomainBoundaryArchTest test
mvn -pl community-app -am -Dtest=ImPolicySnapshotControllerTest,ImPolicySnapshotServiceTest,ModerationApplicationServiceTest,UserModerationGuardTest test
```

Result: PASS.

---

## Task 6: Move Registration And Credential APIs To Application Entries

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserCredentialDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserRegistrationApiAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserCredentialApiAdapter.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserRegistrationService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserCredentialService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserCredentialApplicationServiceTest.java`

- [x] **Step 1: Move registration tests**

Move existing registration service tests to application package and assert the same public behavior for pending registration, expiration cleanup, activation, duplicate username/email handling, and policy event publication.

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserRegistrationApplicationServiceTest test
```

Expected: compile failure until the new registration application service exists.

- [x] **Step 2: Move credential tests**

Move existing credential service tests to application package and preserve authentication, legacy MD5-to-bcrypt upgrade, disabled-user result, password update, and authority mapping behavior.

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserCredentialApplicationServiceTest test
```

Expected: compile failure until the new credential application service exists.

- [x] **Step 3: Implement application services and adapters**

Application services depend on `UserRepository`, domain services, `UuidV7Generator`, `UserPolicyEventPublisher`, and password encoder helpers. API adapters implement the existing `user.api.action/query` interfaces and map application results to `user.api.model` records.

- [x] **Step 4: Run registration and auth callers**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserRegistrationApplicationServiceTest,com.nowcoder.community.user.application.UserCredentialApplicationServiceTest,AuthServiceLoginTest,RefreshTokenServiceTest,DddLayeringArchTest test
```

Expected: PASS.

Verified on 2026-04-28:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserRegistrationApplicationServiceTest test
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserCredentialApplicationServiceTest test
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserRegistrationApplicationServiceTest,com.nowcoder.community.user.application.UserCredentialApplicationServiceTest test
mvn -pl community-app -am -Dtest=com.nowcoder.community.user.application.UserRegistrationApplicationServiceTest,com.nowcoder.community.user.application.UserCredentialApplicationServiceTest,AuthServiceLoginTest,RefreshTokenServiceTest,DddLayeringArchTest test
mvn -pl community-app -am -Dtest=RegistrationServiceTest,RegistrationVerificationServiceTest,PasswordResetServiceTest,PendingRegistrationUserCleanupJobTest,PendingRegistrationUserCleanupHandlerTest,UserRegistrationServiceIntegrationTest,MyBatisUserRepositoryTest test
```

Result: PASS after marking the production `UserRegistrationApplicationService` constructor as the Spring injection constructor; the extra package-private constructor remains test-only.

---

## Task 7: Retire Legacy User Service/Mapper/Entity Surfaces

**Files:**
- Delete or shrink to API adapter only: `backend/community-app/src/main/java/com/nowcoder/community/user/service`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/entity`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/mapper`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Delete: `backend/community-app/src/main/resources/mapper/user_score_log_mapper.xml`

- [x] **Step 1: Scan for forbidden user-domain references**

Run:

```bash
cd /home/feng/code/project/community
rg -n "com\\.nowcoder\\.community\\.user\\.(entity|mapper)|user/service/(User(Read|Profile|Avatar|File|Registration|Credential|Moderation|Query|Service|AdminUserService)|AdminUserApplicationService)" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected before deletion: only adapter classes, old service tests, or files being deleted remain.

Actual after cleanup: no forbidden import/name references remain, and `user.service` production files are only `*ApiAdapter` classes.

- [x] **Step 2: Delete retired classes and old tests**

Delete old raw services after replacement coverage passes. Keep only API adapter classes in `user.service` until a later global adapter-package cleanup is planned.

- [x] **Step 3: Tighten ArchUnit for the user domain**

Add a focused ArchUnit rule that no class in `..user.controller..` depends on `..user.service..`, `..user.mapper..`, `..user.entity..`, or `..user.infrastructure..`.

- [x] **Step 4: Run focused and full suites**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=UserControllerUnitTest,AdminUserControllerUnitTest,FilesControllerStorageRoutingTest,UserRegistrationApiAdapterIntegrationTest,UserMapperPersistenceTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,DddLayeringArchTest test
```

Then run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected:

```text
Tests run: 578 or higher, Failures: 0, Errors: 0
BUILD SUCCESS
```

Verified on 2026-04-28:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DddLayeringArchTest test
mvn -pl community-app -am -Dtest=UserControllerUnitTest,AdminUserControllerUnitTest,FilesControllerStorageRoutingTest,UserRegistrationApiAdapterIntegrationTest,UserMapperPersistenceTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,DddLayeringArchTest test
mvn -pl community-app -am -Dtest=R2AvatarStorageProviderUnitTest,UserPointsAwardApiAdapterTest,UserPointsApplicationServiceIntegrationTest,UserConsumedEventSchemaPersistenceTest,DtoBoundaryArchTest,PublicReadEndpointSecurityTest test
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Result: PASS. Full suite result: `Tests run: 586, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

---

## Self-Review

### Spec Coverage

- Same-domain controller entry through `user.application.*ApplicationService`: Tasks 2, 3, and 4.
- Domain business rules in `user.domain.service`: Tasks 2, 4, 5, and 6.
- Persistence hidden behind `user.domain.repository.UserRepository`: Task 1.
- MyBatis and row objects under `user.infrastructure.persistence`: Task 1.
- Redis/avatar storage behind application ports and infrastructure adapters: Task 3.
- Foreign-domain synchronous contracts kept in `user.api.*` and implemented by adapters: Tasks 2, 5, and 6.
- Legacy `service`, `entity`, and `mapper` surfaces retired or reduced to adapters: Task 7.

### Placeholder Scan

No task uses placeholder language. Each task names concrete files, behavior, verification commands, and expected outcomes.

### Type Consistency

The plan consistently uses `user.application`, `user.domain`, `user.infrastructure`, application command/result records, `UserRepository`, and API adapter classes for foreign-domain contracts.
