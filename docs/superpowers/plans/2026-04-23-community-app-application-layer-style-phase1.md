# Community App Application Layer Style Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Converge the first two drifted domains in `backend/community-app` onto the approved application-layer style by making same-domain controllers use owner `*ApplicationService`, keeping cross-domain `api.query` / `api.action` stable, and deleting dead transitional layers.

**Architecture:** This first slice does not try to refactor every domain at once. It freezes the rule in ArchUnit and docs, then applies it to `user` and `content`, the two domains currently showing the clearest drift: `user` still uses `app/query` plus incomplete page aggregation, while `content` still mixes controller-to-`api.*`, `UseCase`, and an unused `PostCommandService`. The implementation keeps owner-domain collaboration APIs for foreign callers but rewires same-domain controllers onto concrete owner application services.

**Tech Stack:** Java 17, Spring Boot 3, ArchUnit, MyBatis, JUnit 5, Mockito, Maven

---

## File Structure Map

### Guardrails and docs

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- `docs/ARCHITECTURE.md`
  Role: encode the distinction between same-domain application entry points and cross-domain owner APIs so new drift fails at compile/test time instead of surviving as convention.

### User slice

- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserReadApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfileApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/service/UserReadApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/service/UserProfileApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`
  Role: replace the half-finished `app/query` page aggregator with a real same-domain application service, keep cross-domain read APIs stable, and stop returning hardcoded wallet/social/level placeholders.

### Content slice

- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/BookmarkController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentActionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/BookmarkApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingActionServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostReadQueryServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/BookmarkControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`
  Role: rename the current same-domain entry beans into explicit application services, rewire `PostController` to concrete owner services, and remove the dead `PostCommandService` branch.

### Final verification

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
  Role: tighten the temporary baselines back down after `user` and `content` are migrated.

---

### Task 1: Freeze The Same-Domain Application-Service Rule

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `docs/ARCHITECTURE.md`

- [ ] **Step 1: Write the failing ArchUnit rule for same-domain controller-to-owner-api coupling**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
private static final Set<String> LEGACY_SAME_DOMAIN_OWNER_API_CONTROLLER_CALLERS = Set.of();

@ArchTest
static final ArchRule controllers_must_not_depend_on_same_domain_owner_apis =
        classes()
                .that().resideInAnyPackage("..controller..")
                .should(ArchitectureRulesSupport.notDependOnSameDomainOwnerApiPackages(
                        LEGACY_SAME_DOMAIN_OWNER_API_CONTROLLER_CALLERS
                ));
```

- [ ] **Step 2: Run the architecture tests to capture the current drift**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=ControllerBoundaryArchTest test
```

Expected:

- RED
- compilation or ArchUnit failures mention `PostController` and `UserController` depending on same-domain `api.*`

- [ ] **Step 3: Add the helper, baseline the two known offenders, and document the rule**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java
static ArchCondition<JavaClass> notDependOnSameDomainOwnerApiPackages(Set<String> legacyOriginWhitelist) {
    return new ArchCondition<>("not depend on same-domain owner api packages") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            if (isWhitelisted(item, legacyOriginWhitelist)) {
                return;
            }
            String originDomain = domainOf(item);
            if (originDomain.isEmpty()) {
                return;
            }
            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (!originDomain.equals(domainOf(target))) {
                    continue;
                }
                if (!residesInPackagePrefixes(target, Set.of("api.query", "api.action", "api.model"))) {
                    continue;
                }
                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
            }
        }
    };
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
private static final Set<String> LEGACY_SAME_DOMAIN_OWNER_API_CONTROLLER_CALLERS = Set.of(
        "com.nowcoder.community.content.controller.PostController",
        "com.nowcoder.community.content.controller.BookmarkController",
        "com.nowcoder.community.user.controller.UserController"
);
```

```markdown
<!-- docs/ARCHITECTURE.md -->
- 同域同步入口统一走 owner `*ApplicationService`，不再把 same-domain `api.query` / `api.action` 当 controller 默认入口。
- `api.query` / `api.action` / `api.model` 的职责固定为跨域协作边界，不是域内 service locator。
```

- [ ] **Step 4: Re-run the guardrail tests and confirm the baseline is the only remaining exception**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=ControllerBoundaryArchTest test
```

Expected:

- PASS
- the new rule is active
- only `PostController`, `BookmarkController`, and `UserController` remain temporarily baselined

- [ ] **Step 5: Commit the guardrail change**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java \
  docs/ARCHITECTURE.md
git commit -m "test: freeze same-domain application service rule"
```

### Task 2: Replace `user.app.query` With A Real User Application Service

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserReadApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/user/app/query/UserProfilePageView.java` -> `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfilePageView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfileApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/user/service/UserProfileApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/user/service/UserReadApplicationServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/app/query/GetUserProfilePageQueryTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`

- [ ] **Step 1: Write the failing tests that define the new `user` application entry**

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/service/UserProfileApplicationServiceTest.java
@Test
void getShouldAssembleProfileFromOwnerAndForeignCollaborators() {
    UserReadApplicationService userReadApplicationService = mock(UserReadApplicationService.class);
    UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
    PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
    UserLevelQueryApi userLevelQueryApi = mock(UserLevelQueryApi.class);
    UserProfileApplicationService service = new UserProfileApplicationService(
            userReadApplicationService,
            userSocialProfileService,
            postReadQueryApi,
            userLevelQueryApi
    );
    UUID userId = uuid(7);
    UUID viewerId = uuid(42);
    Date createTime = new Date();

    when(userReadApplicationService.getProfile(userId))
            .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));
    UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
    stats.setLikeCount(12L);
    stats.setFolloweeCount(5L);
    stats.setFollowerCount(8L);
    stats.setHasFollowed(true);
    when(userSocialProfileService.userProfileStats(userId, viewerId)).thenReturn(stats);
    when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(new UserLevelSummaryView(2, 13, 100, 12, 88, true));

    UserProfilePageView page = service.get(authentication(viewerId), userId);

    assertThat(page.walletBalance()).isEqualTo(900L);
    assertThat(page.walletStatus()).isEqualTo("ACTIVE");
    assertThat(page.likeCount()).isEqualTo(12L);
    assertThat(page.followeeCount()).isEqualTo(5L);
    assertThat(page.followerCount()).isEqualTo(8L);
    assertThat(page.hasFollowed()).isTrue();
    assertThat(page.userLevelEnabled()).isTrue();
    assertThat(page.userLevel()).isEqualTo(2);
    assertThat(page.signInDaysInWindow()).isEqualTo(13);
    assertThat(page.socialDegraded()).isFalse();
    verify(userReadApplicationService).getProfile(userId);
    verify(userSocialProfileService).userProfileStats(userId, viewerId);
    verify(userLevelQueryApi).evaluateLevel(userId);
}

@Test
void recentPostsAndCommentsShouldDelegateToContentAfterUserExistenceCheck() {
    UserReadApplicationService userReadApplicationService = mock(UserReadApplicationService.class);
    UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
    PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
    UserLevelQueryApi userLevelQueryApi = mock(UserLevelQueryApi.class);
    UserProfileApplicationService service = new UserProfileApplicationService(
            userReadApplicationService,
            userSocialProfileService,
            postReadQueryApi,
            userLevelQueryApi
    );
    UUID userId = uuid(7);
    Date createTime = new Date();

    when(userReadApplicationService.getProfile(userId))
            .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));
    when(postReadQueryApi.listPostsByUser(userId, 1, 5)).thenReturn(List.of(
            new PostSummaryView(uuid(11), userId, "first", 0, 0, createTime, 4, 9.5, uuid(3), List.of("java"), uuid(8), createTime, createTime, "reply")
    ));
    when(postReadQueryApi.listRecentCommentsByUser(userId, 2, 10)).thenReturn(List.of(
            new RecentUserCommentView(uuid(21), userId, 1, uuid(31), uuid(41), uuid(51), "post title", "reply body", createTime)
    ));

    assertThat(service.listRecentPosts(userId, 1, 5)).hasSize(1);
    assertThat(service.listRecentComments(userId, 2, 10)).hasSize(1);
    verify(userReadApplicationService).getProfile(userId);
    verify(postReadQueryApi).listPostsByUser(userId, 1, 5);
    verify(postReadQueryApi).listRecentCommentsByUser(userId, 2, 10);
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/service/UserReadApplicationServiceTest.java
@Test
void getProfileShouldProjectWalletStateFromWalletQueryApi() {
    UserReadApplicationService service = new UserReadApplicationService(new UserQueryService(userMapper, walletAccountService));
    UUID userId = uuid(6);
    User user = user(userId, "bob");
    Date createTime = new Date();
    user.setHeaderUrl("h6");
    user.setType(2);
    user.setStatus(1);
    user.setScore(120);
    user.setCreateTime(createTime);
    when(userMapper.selectById(userId)).thenReturn(user);
    when(walletAccountService.balanceOfUser(userId)).thenReturn(900L);
    when(walletAccountService.statusOfUser(userId)).thenReturn("ACTIVE");

    UserProfileView profile = service.getProfile(userId);

    assertThat(profile.walletBalance()).isEqualTo(900L);
    assertThat(profile.walletStatus()).isEqualTo("ACTIVE");
    verify(walletAccountService).balanceOfUser(userId);
    verify(walletAccountService).statusOfUser(userId);
}
```

- [ ] **Step 2: Run the focused `user` suite and confirm RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=UserProfileApplicationServiceTest,UserReadApplicationServiceTest,UserControllerUnitTest,UserControllerLoggingTest,PublicReadEndpointSecurityTest test
```

Expected:

- RED
- `UserProfileApplicationService` does not exist yet
- controller tests still reference `GetUserProfilePageQuery`
- wallet projection tests still fail on hardcoded `0L` / `"UNKNOWN"` values

- [ ] **Step 3: Implement the new application service and delete the old `app/query` entry**

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfilePageView.java
package com.nowcoder.community.user.service;

// Move the existing record from user.app.query into user.service unchanged.
// Keep the nested RecentPostSummaryView and RecentCommentItemView records unchanged.
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/UserReadApplicationService.java
@Service
public class UserReadApplicationService implements UserLookupQueryApi, UserProfileQueryApi {

    private final UserQueryService userQueryService;

    public UserReadApplicationService(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @Override
    public UserSummaryView getSummaryById(UUID userId) {
        return userQueryService.getSummaryById(userId);
    }

    @Override
    public UserSummaryView getSummaryByUsername(String username) {
        return userQueryService.getSummaryByUsername(username);
    }

    @Override
    public UserSummaryView findSummaryByEmailOrNull(String email) {
        return userQueryService.findSummaryByEmailOrNull(email);
    }

    @Override
    public List<UserSummaryView> listSummariesByIds(List<UUID> userIds) {
        return userQueryService.listSummariesByIds(userIds);
    }

    @Override
    public UserProfileView getProfile(UUID userId) {
        return userQueryService.getProfile(userId);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfileApplicationService.java
@Service
public class UserProfileApplicationService {

    private final UserReadApplicationService userReadApplicationService;
    private final UserSocialProfileService userSocialProfileService;
    private final PostReadQueryApi postReadQueryApi;
    private final UserLevelQueryApi userLevelQueryApi;

    public UserProfileApplicationService(
            UserReadApplicationService userReadApplicationService,
            UserSocialProfileService userSocialProfileService,
            PostReadQueryApi postReadQueryApi,
            UserLevelQueryApi userLevelQueryApi
    ) {
        this.userReadApplicationService = userReadApplicationService;
        this.userSocialProfileService = userSocialProfileService;
        this.postReadQueryApi = postReadQueryApi;
        this.userLevelQueryApi = userLevelQueryApi;
    }

    public UserProfilePageView get(Authentication authentication, UUID userId) {
        UserProfileView user = userReadApplicationService.getProfile(userId);
        UUID viewerId = CurrentUser.tryUserUuid(authentication);
        UserSocialProfileService.UserProfileStats stats = userSocialProfileService.userProfileStats(userId, viewerId);
        UserLevelSummaryView levelSummary = userLevelQueryApi.evaluateLevel(userId);
        boolean userLevelEnabled = levelSummary != null && levelSummary.enabled();
        return new UserProfilePageView(
                user.userId(),
                user.username(),
                user.headerUrl(),
                user.type(),
                user.status(),
                user.createTime(),
                user.score(),
                user.level(),
                user.walletBalance(),
                user.walletStatus(),
                userLevelEnabled,
                userLevelEnabled ? levelSummary.userLevel() : null,
                userLevelEnabled ? levelSummary.signInDaysInWindow() : null,
                stats.getLikeCount(),
                stats.getFolloweeCount(),
                stats.getFollowerCount(),
                stats.isHasFollowed(),
                false
        );
    }

    public List<UserProfilePageView.RecentPostSummaryView> listRecentPosts(UUID userId, Integer page, Integer size) {
        userReadApplicationService.getProfile(userId);
        return postReadQueryApi.listPostsByUser(userId, page, size).stream()
                .map(UserProfileApplicationService::toRecentPostSummaryView)
                .toList();
    }

    public List<UserProfilePageView.RecentCommentItemView> listRecentComments(UUID userId, Integer page, Integer size) {
        userReadApplicationService.getProfile(userId);
        return postReadQueryApi.listRecentCommentsByUser(userId, page, size).stream()
                .map(UserProfileApplicationService::toRecentCommentItemView)
                .toList();
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java
private UserProfileView toProfileView(User user) {
    if (user == null || user.getId() == null) {
        return null;
    }
    return new UserProfileView(
            user.getId(),
            user.getUsername(),
            user.getHeaderUrl(),
            user.getType(),
            user.getStatus(),
            user.getCreateTime(),
            user.getScore(),
            levelForScore(user.getScore()),
            walletAccountQueryApi.balanceOfUser(user.getId()),
            walletAccountQueryApi.statusOfUser(user.getId())
    );
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java
private final UserReadApplicationService userReadApplicationService;
private final UserProfileApplicationService userProfileApplicationService;

public UserController(
        UserReadApplicationService userReadApplicationService,
        UserProfileApplicationService userProfileApplicationService,
        UserService userService,
        AvatarService avatarService
) {
    this.userReadApplicationService = userReadApplicationService;
    this.userProfileApplicationService = userProfileApplicationService;
    this.userService = userService;
    this.avatarService = avatarService;
}
```

```java
// delete backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java
```

- [ ] **Step 4: Update controller and security tests to the new local dependency shape**

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java
@Mock
private UserReadApplicationService userReadApplicationService;

@Mock
private UserProfileApplicationService userProfileApplicationService;

@BeforeEach
void setUp() {
    controller = new UserController(
            userReadApplicationService,
            userProfileApplicationService,
            userService,
            avatarService
    );
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java
controller = new UserController(
        mock(UserReadApplicationService.class),
        mock(UserProfileApplicationService.class),
        userService,
        avatarService
);
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java
@MockBean
private UserProfileApplicationService userProfileApplicationService;

@MockBean
private UserReadApplicationService userReadApplicationService;

@Test
void unauthenticatedRecentActivityEndpointsShouldBeAllowed() throws Exception {
    when(userProfileApplicationService.listRecentPosts(eq(USER_ID), any(), any())).thenReturn(List.of());
    when(userProfileApplicationService.listRecentComments(eq(USER_ID), any(), any())).thenReturn(List.of());

    mockMvc.perform(get("/api/users/" + USER_ID + "/recent-posts"))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/users/" + USER_ID + "/recent-comments"))
            .andExpect(status().isOk());
}
```

- [ ] **Step 5: Run the focused `user` suite and confirm GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=UserProfileApplicationServiceTest,UserReadApplicationServiceTest,UserControllerUnitTest,UserControllerLoggingTest,PublicReadEndpointSecurityTest test
```

Expected:

- PASS
- profile page tests now verify real social/level/recent-content composition
- wallet values come from `WalletAccountQueryApi` instead of `0L` / `"UNKNOWN"`

- [ ] **Step 6: Commit the `user` slice**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/UserReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfilePageView.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/UserProfileApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/service/UserReadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/service/UserProfileApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java
git rm backend/community-app/src/main/java/com/nowcoder/community/user/app/query/UserProfilePageView.java
git rm backend/community-app/src/test/java/com/nowcoder/community/user/app/query/GetUserProfilePageQueryTest.java
git commit -m "refactor: introduce user profile application service"
```

### Task 3: Rename The `content` Same-Domain Entry Beans Into Application Services

**Files:**
- Move: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadQueryService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadQueryService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentActionService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/BookmarkApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/BookmarkController.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/BookmarkControllerTest.java`
- Move: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingActionServiceTest.java` -> `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingApplicationServiceTest.java`
- Move: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostReadQueryServiceTest.java` -> `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostReadApplicationServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`

- [ ] **Step 1: Write the failing controller unit test for the new content dependency shape**

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java
@ExtendWith(MockitoExtension.class)
class PostControllerUnitTest {

    @Mock
    private PostReadApplicationService postReadApplicationService;

    @Mock
    private CommentReadApplicationService commentReadApplicationService;

    @Mock
    private PostPublishingApplicationService postPublishingApplicationService;

    @Mock
    private PostModerationApplicationService postModerationApplicationService;

    @Mock
    private CommentApplicationService commentApplicationService;

    @Test
    void createShouldDelegateToPostPublishingApplicationService() {
        UUID postId = uuid(101);
        when(postPublishingApplicationService.create(eq(uuid(7)), eq("idem-1"), eq("t"), eq("c"), eq(uuid(3)), eq(List.of("java"))))
                .thenReturn(new PostCreateResult(postId));

        PostController controller = new PostController(
                postReadApplicationService,
                commentReadApplicationService,
                postPublishingApplicationService,
                postModerationApplicationService,
                commentApplicationService
        );

        Result<CreatePostResponse> result = controller.create(authentication(uuid(7)), "idem-1", request("t", "c", uuid(3), List.of("java")));

        assertThat(result.getData().getPostId()).isEqualTo(postId);
        verify(postPublishingApplicationService).create(uuid(7), "idem-1", "t", "c", uuid(3), List.of("java"));
    }
}
```

- [ ] **Step 2: Run the focused `content` suite and confirm RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=PostControllerUnitTest,PostPublishingActionServiceTest,PostReadQueryServiceTest,PublicReadEndpointSecurityTest test
```

Expected:

- RED
- renamed application-service classes do not exist yet
- `PostController` still depends on `content.api.*`

- [ ] **Step 3: Rename the current owner implementations and rewire `PostController` to concrete application services**

```bash
cd /home/feng/code/project/community
git mv backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java \
       backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java
git mv backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionService.java \
       backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationApplicationService.java
git mv backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadQueryService.java \
       backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java
git mv backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadQueryService.java \
       backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadApplicationService.java
git mv backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentActionService.java \
       backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentApplicationService.java
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java
private final PostReadApplicationService postReadApplicationService;
private final CommentReadApplicationService commentReadApplicationService;
private final PostPublishingApplicationService postPublishingApplicationService;
private final PostModerationApplicationService postModerationApplicationService;
private final CommentApplicationService commentApplicationService;
private final BookmarkApplicationService bookmarkApplicationService;

public PostController(
        PostReadApplicationService postReadApplicationService,
        CommentReadApplicationService commentReadApplicationService,
        PostPublishingApplicationService postPublishingApplicationService,
        PostModerationApplicationService postModerationApplicationService,
        CommentApplicationService commentApplicationService
) {
    this.postReadApplicationService = postReadApplicationService;
    this.commentReadApplicationService = commentReadApplicationService;
    this.postPublishingApplicationService = postPublishingApplicationService;
    this.postModerationApplicationService = postModerationApplicationService;
    this.commentApplicationService = commentApplicationService;
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java
@Service
public class PostPublishingApplicationService implements PostPublishingActionApi {

    private final SensitiveFilter sensitiveFilter;
    private final CreatePostUseCase createPostUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeleteOwnPostUseCase deleteOwnPostUseCase;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;

    @Override
    public PostCreateResult create(UUID userId, String idempotencyKey, String title, String content, UUID categoryId, List<String> tags) {
        return idempotencyGuard.executeRequired("content:create_post", userId, idempotencyKey, PostCreateResult.class, () -> {
            String safeTitle = sanitize(title);
            String safeContent = sanitize(content);
            UUID postId = createPostUseCase.createPost(userId, safeTitle, safeContent, categoryId, tags);
            return new PostCreateResult(postId);
        });
    }

    @Override
    public void updatePost(UUID userId, UUID postId, String title, String content, UUID categoryId, List<String> tags) {
        updatePostUseCase.updatePost(userId, postId, sanitize(title), sanitize(content), categoryId, tags);
    }

    @Override
    public void deleteByAuthor(UUID userId, UUID postId) {
        deleteOwnPostUseCase.deletePostByAuthor(userId, postId);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java
@Service
public class PostReadApplicationService implements PostReadQueryApi {

    @Override
    public List<PostSummaryView> listPosts(UUID currentUserId, String order, UUID categoryId, String tag, Boolean subscribed, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        int orderMode = "hot".equalsIgnoreCase(order) ? PostService.ORDER_HOT : PostService.ORDER_LATEST;
        List<DiscussPost> posts = Boolean.TRUE.equals(subscribed)
                ? postService.listSubscribedPosts(currentUserId, subscriptionService.listSubscribedCategoryIds(currentUserId), p, s, orderMode, categoryId, tag)
                : postService.listPosts(p, s, orderMode, categoryId, tag);
        return assembleSummaries(posts);
    }

    @Override
    public List<PostSummaryView> listPostsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        return assembleSummaries(postService.listPostsByUser(userId, p, s));
    }

    @Override
    public List<RecentUserCommentView> listRecentCommentsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        List<Comment> comments = commentService.listRecentCommentsByUser(userId, p, s);
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        return comments.stream()
                .map(this::toRecentComment)
                .filter(Objects::nonNull)
                .toList();
    }
}
```

- [ ] **Step 4: Remove the dead write-path branch and update security/test wiring**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/controller/BookmarkController.java
private final BookmarkApplicationService bookmarkApplicationService;

public BookmarkController(BookmarkApplicationService bookmarkApplicationService) {
    this.bookmarkApplicationService = bookmarkApplicationService;
}

@GetMapping("/bookmarks")
public Result<List<PostSummaryResponse>> list(
        Authentication authentication,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
) {
    UUID userId = CurrentUser.requireUserUuid(authentication);
    int p = page == null ? 0 : Math.max(0, page);
    int s = size == null ? 10 : Math.min(50, Math.max(1, size));
    return Result.ok(bookmarkApplicationService.listBookmarkedPostSummaryResponses(userId, p, s));
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/BookmarkApplicationService.java
@Service
public class BookmarkApplicationService {

    private final BookmarkService bookmarkService;

    public BookmarkApplicationService(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    public void add(UUID userId, UUID postId) {
        bookmarkService.add(userId, postId);
    }

    public void remove(UUID userId, UUID postId) {
        bookmarkService.remove(userId, postId);
    }

public List<PostSummaryResponse> listBookmarkedPostSummaryResponses(UUID userId, int page, int size) {
    return bookmarkService.listBookmarkedPostSummaries(userId, page, size).stream()
            .map(this::toPostSummaryResponse)
            .toList();
}

private PostSummaryResponse toPostSummaryResponse(PostSummaryView view) {
    PostSummaryResponse response = new PostSummaryResponse();
    response.setId(view.id());
    response.setUserId(view.userId());
    response.setTitle(view.title());
    response.setType(view.type());
    response.setStatus(view.status());
    response.setCreateTime(view.createTime());
    response.setCommentCount(view.commentCount());
    response.setScore(view.score());
    response.setCategoryId(view.categoryId());
    response.setTags(view.tags());
    response.setLastReplyUserId(view.lastReplyUserId());
    response.setLastReplyTime(view.lastReplyTime());
    response.setLastActivityTime(view.lastActivityTime());
    response.setLastReplyPreview(view.lastReplyPreview());
    return response;
}
}
```

```java
// delete backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java
// delete backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java
@MockBean
private PostReadApplicationService postReadApplicationService;

@MockBean
private CommentReadApplicationService commentReadApplicationService;

@MockBean
private PostPublishingApplicationService postPublishingApplicationService;

@MockBean
private PostModerationApplicationService postModerationApplicationService;

@MockBean
private CommentApplicationService commentApplicationService;
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingApplicationServiceTest.java
class PostPublishingApplicationServiceTest {
    @Test
    void createShouldEscapeFilterAndDelegateCommandThroughIdempotencyGuard() {
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        CreatePostUseCase createPostUseCase = mock(CreatePostUseCase.class);
        UpdatePostUseCase updatePostUseCase = mock(UpdatePostUseCase.class);
        DeleteOwnPostUseCase deleteOwnPostUseCase = mock(DeleteOwnPostUseCase.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(createPostUseCase.createPost(eq(userId), eq("title"), eq("content"), eq(categoryId), eq(List.of("java")))).thenReturn(postId);
        when(idempotencyGuard.executeRequired(eq("content:create_post"), eq(userId), eq("idem-1"), eq(PostCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(4).get());

        PostPublishingApplicationService service = new PostPublishingApplicationService(
                sensitiveFilter,
                createPostUseCase,
                updatePostUseCase,
                deleteOwnPostUseCase,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties())
        );

        PostCreateResult response = service.create(userId, "idem-1", "<title>", "<content>", categoryId, List.of("java"));

        assertThat(response.postId()).isEqualTo(postId);
        verify(createPostUseCase).createPost(userId, "title", "content", categoryId, List.of("java"));
        verify(idempotencyGuard).executeRequired(eq("content:create_post"), eq(userId), eq("idem-1"), eq(PostCreateResult.class), any());
    }
}
```

- [ ] **Step 5: Run the focused `content` suite and confirm GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=PostControllerUnitTest,PostPublishingApplicationServiceTest,PostReadApplicationServiceTest,PublicReadEndpointSecurityTest test
```

Expected:

- PASS
- `PostController` no longer imports `content.api.*`
- `BookmarkController` no longer imports `content.api.model.PostSummaryView`
- the dead `PostCommandService` branch is gone

- [ ] **Step 6: Commit the `content` slice**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/BookmarkController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/BookmarkService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/BookmarkApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/BookmarkControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/service/PostReadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java
git rm backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java
git commit -m "refactor: align content controllers with application services"
```

### Task 4: Remove The Temporary Baseline And Lock The New Shape

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `docs/ARCHITECTURE.md`

- [ ] **Step 1: Write the failing cleanup assertions**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
@Test
void sameDomainOwnerApiControllerBaselineShouldBeEmpty() {
    assertThat(LEGACY_SAME_DOMAIN_OWNER_API_CONTROLLER_CALLERS).isEmpty();
}

@ArchTest
static final ArchRule production_code_must_not_reside_in_legacy_app_query_packages =
        noClasses()
                .should()
                .resideInAnyPackage("..app.query..");
```

- [ ] **Step 2: Run the architecture and focused domain suites to capture the final cleanup work**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=ControllerBoundaryArchTest,UserProfileApplicationServiceTest,UserControllerUnitTest,PostControllerUnitTest,PostPublishingApplicationServiceTest,PostReadApplicationServiceTest test
```

Expected:

- RED while the baseline still contains `PostController`, `BookmarkController`, and `UserController`
- RED if any `..app.query..` package survived the `user` migration

- [ ] **Step 3: Remove the baseline entries and update the architecture doc to the final wording**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
private static final Set<String> LEGACY_SAME_DOMAIN_OWNER_API_CONTROLLER_CALLERS = Set.of();
```

```markdown
<!-- docs/ARCHITECTURE.md -->
- 同域 controller、job、listener 的默认入口是 owner `*ApplicationService`。
- 跨域同步协作的默认入口仍是 owner-domain `api.query` / `api.action` / `api.model`。
- `..app.query..` 属于迁移期遗留结构，不再允许新增或保留。
```

- [ ] **Step 4: Run the full focused verification suite and confirm GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest,UserProfileApplicationServiceTest,UserReadApplicationServiceTest,UserControllerUnitTest,UserControllerLoggingTest,PostControllerUnitTest,PostPublishingApplicationServiceTest,PostReadApplicationServiceTest,PublicReadEndpointSecurityTest test
```

Expected:

- PASS
- no same-domain controller depends on same-domain `api.*`
- no production class remains under `..app.query..`
- `user` and `content` stay green under the tightened rule

- [ ] **Step 5: Commit the locked-in rule**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java \
  docs/ARCHITECTURE.md
git commit -m "test: lock application service entry shape"
```

---

## Self-Review Checklist

### Spec Coverage

- Same-domain `ApplicationService` rule: covered by Task 1 and Task 4
- `user` slice replacing incomplete `app/query`: covered by Task 2
- `content` slice removing mixed same-domain `api.*` and dead command layer: covered by Task 3
- Final lock-in of the new shape: covered by Task 4
- Wider rollout to `search`, `growth`, `market`, and other remaining domains: intentionally deferred to a follow-up plan so this first slice stays independently shippable

### Placeholder Scan

- No `TODO` / `TBD`
- Every task has exact files, commands, and at least one concrete code block per code-changing step
- No “similar to previous task” references

### Type Consistency

- Same-domain controller entry name used consistently as `*ApplicationService`
- Cross-domain APIs remain `*QueryApi` / `*ActionApi`
- `GetUserProfilePageQuery` is replaced by `UserProfileApplicationService`
- `PostCommandService` is removed instead of retained as a parallel write path
