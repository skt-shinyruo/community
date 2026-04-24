# Community App Application Layer Style Phase 2 Lightweight Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the already-in-flight controller/application-service rollout outside the original phase-1 `user` + `content` scope by aligning the remaining lightweight same-domain HTTP entrypoints with owner `*ApplicationService` naming and wiring, while preserving legitimate cross-domain `api.*` boundaries.

**Architecture:** Keep the 2026-04-23 application-layer style design as the governing rule: same-domain controllers use owner `*ApplicationService`, foreign-domain collaboration keeps using owner `api.query` / `api.action` / `api.model`. This phase intentionally covers only the extra domains already changed in the worktree: `auth`, `analytics`, `search`, `notice`, lightweight `content` controllers plus score-refresh naming, `user` secondary entrypoints (`admin`, `files`, `avatar`), and `market` / `wallet`. `OpsController` remains on `SearchReindexActionApi` because it is an operations boundary consuming a foreign-domain action API, not a same-domain controller drift case.

**Tech Stack:** Java 17, Spring Boot 3, WebMvcTest, Mockito, ArchUnit, Maven, Markdown docs

---

## File Structure Map

### Lightweight Read / Auth Controller Rollout

- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/controller/SearchController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/analytics/controller/AnalyticsControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/search/controller/SearchControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/notice/controller/NoticeControllerUnitTest.java`
  Role: rename same-domain controller-facing entrypoints into owner `*ApplicationService` without changing foreign-domain collaboration or auth semantics.

### Lightweight Content Controllers And Score Naming

- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/CategoryController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/CategoryApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/SubscriptionController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/SubscriptionApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/TagController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/TagApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreUpdateService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/CategoryControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/SubscriptionControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/TagControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/score/PostScoreRefresherTest.java`
  Role: finish the low-complexity content entrypoints that moved beyond the 2026-04-24 content phase-1 deferral, and rename the post-score write path to an owner `UpdateService` that better describes the transactional responsibility.

### User Secondary Entrypoints

- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserFileApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/dto/AvatarFileResource.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserAvatarApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
  Role: move avatar upload/update policy, file-key validation, and admin role operations behind explicit owner application services so the controllers stay transport-only.

### Wallet / Market Rollout

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/AdminWalletController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/AdminWalletApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/AdminMarketApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/AdminWalletControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`
  Role: rename controller-facing owner entrypoints in the trading and wallet domains while keeping cross-domain `WalletMarketActionApi` stable for foreign callers.

### Docs And Verification

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`
- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
- `docs/business-logic/market-order-dispute-flow.md`
- `docs/business-logic/ops-scheduler-compensation-flow.md`
- `docs/business-logic/wallet-ledger-flow.md`
  Role: document the phase-2 rollout state, keep the same-domain boundary language aligned with the new lightweight application services, and verify the full module still stays green.

---

### Task 1: Align Auth, Analytics, Search, And Notice Controllers With Owner Application Services

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/controller/SearchController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/controller/AnalyticsControllerUnitTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/search/controller/SearchControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/controller/NoticeControllerUnitTest.java`

- [ ] **Step 1: Write the failing controller tests against owner `*ApplicationService` entrypoints**

```java
// backend/community-app/src/test/java/com/nowcoder/community/analytics/controller/AnalyticsControllerUnitTest.java
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerUnitTest {

    @Mock
    private AnalyticsApplicationService analyticsApplicationService;

    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(analyticsApplicationService);
    }

    @Test
    void uvShouldDelegateToAnalyticsApplicationService() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 24);
        when(analyticsApplicationService.calculateUv(start, end)).thenReturn(42L);

        Result<Long> result = controller.uv(start, end);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo(42L);
        verify(analyticsApplicationService).calculateUv(start, end);
    }
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/search/controller/SearchControllerTest.java
@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchApplicationService searchApplicationService;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController(searchApplicationService);
    }

    @Test
    void searchPostsShouldDelegateToSearchApplicationService() {
        UUID categoryId = uuid(3);
        SearchPostItem item = new SearchPostItem();
        item.setPostId(uuid(11));
        item.setTitle("spring");
        when(searchApplicationService.searchPosts("spring", categoryId, "java", 0, 10))
                .thenReturn(List.of(item));

        Result<List<SearchPostItem>> result = controller.searchPosts("spring", categoryId, "java", 0, 10);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(searchApplicationService).searchPosts("spring", categoryId, "java", 0, 10);
    }
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/notice/controller/NoticeControllerUnitTest.java
@Test
void listShouldDelegateToNoticeOwnedDtoReturningServiceMethod() {
    UUID userId = uuid(7);
    NoticeItemResponse item = new NoticeItemResponse();
    item.setId(NOTICE_ID);
    item.setTopic("comment");
    when(noticeApplicationService.listNoticeItems(userId, "comment", null, null)).thenReturn(List.of(item));

    Result<List<NoticeItemResponse>> result = controller.list(authentication(userId), "comment", null, null);

    assertThat(result.getCode()).isEqualTo(0);
    assertThat(result.getData()).containsExactly(item);
    verify(noticeApplicationService).listNoticeItems(userId, "comment", null, null);
}
```

- [ ] **Step 2: Run the focused suite and verify it goes RED before rewiring**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=AuthControllerUnitTest,AnalyticsControllerUnitTest,NoticeControllerUnitTest,SearchControllerTest test
```

Expected:

- compilation or constructor wiring failures mention the missing `*ApplicationService` types
- existing controllers still depend on raw owner services before the rewire

- [ ] **Step 3: Introduce the owner application services and rewire the controllers**

```java
// backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthApplicationService.java
@Service
public class AuthApplicationService {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final RegistrationVerificationService registrationVerificationService;
    private final CaptchaService captchaService;
    private final PasswordResetService passwordResetService;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(
                request.getUsername(),
                request.getPassword(),
                request.getCaptchaId(),
                request.getCaptchaCode(),
                httpRequest
        );
        response.addHeader(HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
        return new LoginResponse(result.accessToken());
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsApplicationService.java
@Service
public class AnalyticsApplicationService {

    private final AnalyticsService analyticsService;

    public long calculateUv(LocalDate start, LocalDate end) {
        return analyticsService.calculateUv(start, end);
    }

    public long calculateDau(LocalDate start, LocalDate end) {
        return analyticsService.calculateDau(start, end);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchApplicationService.java
@Service
public class SearchApplicationService {

    private final PostSearchService postSearchService;

    public List<SearchPostItem> searchPosts(String keyword, UUID categoryId, String tag, Integer page, Integer size) {
        return postSearchService.search(keyword, categoryId, tag, page, size);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeApplicationService.java
@Service
public class NoticeApplicationService {

    private final NoticeService noticeService;

    public List<NoticeItemResponse> listNoticeItems(UUID userId, String topic, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return noticeService.listNoticeItems(userId, topic, p, s);
    }
}
```

- [ ] **Step 4: Re-run the focused suite and confirm GREEN**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=AuthControllerUnitTest,AnalyticsControllerUnitTest,NoticeControllerUnitTest,SearchControllerTest test
```

Expected:

- PASS
- `AuthController`, `AnalyticsController`, `SearchController`, and `NoticeController` depend only on owner `*ApplicationService`
- no cross-domain collaboration API surface changes

- [ ] **Step 5: Checkpoint the lightweight read/auth slice**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java \
  backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/search/controller/SearchController.java \
  backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/analytics/controller/AnalyticsControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/controller/SearchControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/controller/NoticeControllerUnitTest.java
git commit -m "refactor: align lightweight read and auth controllers with application services"
```

### Task 2: Align Lightweight Content Controllers And Score Refresh Naming

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/CategoryController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/CategoryApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/SubscriptionController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/SubscriptionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/TagController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/TagApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreCommandService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreUpdateService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/CategoryControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/SubscriptionControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/TagControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/score/PostScoreRefresherTest.java`

- [ ] **Step 1: Write the failing controller tests for the lightweight content entrypoints**

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/controller/CategoryControllerTest.java
@Test
void listShouldKeepCategoryFieldsAsReturnedByService() {
    CategoryApplicationService categoryApplicationService = mock(CategoryApplicationService.class);
    CategoryResponse category = new CategoryResponse();
    category.setId(uuid(1));
    category.setName("公告");

    when(categoryApplicationService.listCategoryResponses()).thenReturn(List.of(category));

    CategoryController controller = new CategoryController(categoryApplicationService);

    Result<List<CategoryResponse>> result = controller.list();

    assertThat(result.getCode()).isEqualTo(0);
    assertThat(result.getData()).containsExactly(category);
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/controller/SubscriptionControllerTest.java
@Test
void subscribeAndUnsubscribeShouldDelegateToSubscriptionApplicationService() {
    UUID userId = uuid(7);
    UUID categoryId = uuid(3);

    Result<Void> subscribeResult = controller.subscribeCategory(authentication(userId), categoryId);
    Result<Void> unsubscribeResult = controller.unsubscribeCategory(authentication(userId), categoryId);

    assertThat(subscribeResult.getCode()).isEqualTo(0);
    assertThat(unsubscribeResult.getCode()).isEqualTo(0);
    verify(subscriptionApplicationService).subscribeCategory(userId, categoryId);
    verify(subscriptionApplicationService).unsubscribeCategory(userId, categoryId);
}
```

- [ ] **Step 2: Run the focused lightweight-content suite and confirm RED**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=CategoryControllerTest,SubscriptionControllerTest,TagControllerTest,PostScoreRefresherTest test
```

Expected:

- compilation fails until the controller constructors and the new `*ApplicationService` types line up
- `PostScoreRefresher` still references `PostScoreCommandService` before the rename

- [ ] **Step 3: Introduce the lightweight content application services and rename the score write path**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/CategoryApplicationService.java
@Service
public class CategoryApplicationService {

    private final CategoryService categoryService;

    public List<CategoryResponse> listCategoryResponses() {
        return categoryService.listCategoryResponses();
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/SubscriptionApplicationService.java
@Service
public class SubscriptionApplicationService {

    private final SubscriptionService subscriptionService;

    public void subscribeCategory(UUID userId, UUID categoryId) {
        subscriptionService.subscribeCategory(userId, categoryId);
    }

    public List<UUID> listSubscribedCategoryIds(UUID userId) {
        return subscriptionService.listSubscribedCategoryIds(userId);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/TagApplicationService.java
@Service
public class TagApplicationService {

    private final TagService tagService;

    public List<HotTagResponse> listHotTagResponses(Integer limit) {
        return tagService.listHotTagResponses(limit);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreUpdateService.java
@Service
public class PostScoreUpdateService {

    private final PostService postService;
    private final PostDomainEventPublisher domainEventPublisher;

    @Transactional
    public void updateScore(UUID postId, double score) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        postService.updateScore(postId, score);
        domainEventPublisher.postUpdated(postId);
    }
}
```

- [ ] **Step 4: Re-run the focused lightweight-content suite and confirm GREEN**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=CategoryControllerTest,SubscriptionControllerTest,TagControllerTest,PostScoreRefresherTest test
```

Expected:

- PASS
- the light content controllers now depend only on owner `*ApplicationService`
- score refresh writes go through `PostScoreUpdateService`

- [ ] **Step 5: Checkpoint the lightweight content slice**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/CategoryController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/CategoryApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/SubscriptionController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/SubscriptionApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/TagController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/TagApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreUpdateService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/CategoryControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/SubscriptionControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/TagControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/score/PostScoreRefresherTest.java
git commit -m "refactor: align lightweight content controllers with application services"
```

### Task 3: Move User Secondary Entrypoints Behind Owner Application Services

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserFileApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/dto/AvatarFileResource.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserAvatarApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`

- [ ] **Step 1: Write the failing tests for admin, file, and avatar secondary entrypoints**

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java
@Test
void updateRoleShouldResolveActorUserIdAndDelegate() {
    UUID targetUserId = uuid(8);
    UUID actorUserId = uuid(99);
    UpdateUserRoleRequest request = new UpdateUserRoleRequest();
    request.setTargetUserId(targetUserId);

    Result<Void> result = controller.updateRole(authentication(actorUserId), request);

    assertThat(result.getCode()).isEqualTo(0);
    verify(adminUserApplicationService).updateRole(actorUserId, request);
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java
@Test
void shouldServeAvatarFilesViaCurrentProviderWhenStorageIsNotLocal() {
    AvatarStorageRouter router = new AvatarStorageRouter(props, List.of(stub));
    FilesController controller = new FilesController(new UserFileApplicationService(router));

    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/files/avatar/" + userId + "/0123456789abcdef0123456789abcdef");

    ResponseEntity<Resource> resp = controller.get(req);

    assertThat(resp.getStatusCode().value()).isEqualTo(200);
    assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java
@BeforeEach
void setUp() {
    userService = mock(UserService.class);
    avatarService = mock(AvatarService.class);
    controller = new UserController(
            mock(UserReadApplicationService.class),
            mock(UserProfileApplicationService.class),
            new UserAvatarApplicationService(avatarService, userService)
    );
}
```

- [ ] **Step 2: Run the focused user-secondary suite and confirm RED**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=AdminUserControllerUnitTest,FilesControllerStorageRoutingTest,UserControllerLoggingTest test
```

Expected:

- RED until the controllers and tests are wired to the new owner `*ApplicationService` types
- file serving still couples key parsing and controller logic before `UserFileApplicationService` exists

- [ ] **Step 3: Introduce the secondary owner application services and rewire the controllers**

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserApplicationService.java
@Service
public class AdminUserApplicationService {

    private final AdminUserService adminUserService;

    public AdminUserResponse search(UUID userId, String username, String email) {
        return adminUserService.search(userId, username, email);
    }

    public void updateRole(UUID actorUserId, UpdateUserRoleRequest request) {
        adminUserService.updateRole(actorUserId, request);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/UserFileApplicationService.java
@Service
public class UserFileApplicationService {

    public AvatarFileResource loadAvatarOrNull(String requestUri) {
        String key = resolveKey(requestUri);
        if (!StringUtils.hasText(key) || !AVATAR_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        StoredAvatar stored = router.currentProviderOrThrow().loadOrNull(key);
        return stored == null ? null : new AvatarFileResource(stored.resource(), stored.mediaType());
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/UserAvatarApplicationService.java
@Service
public class UserAvatarApplicationService {

    public AvatarUploadTokenResponse createUploadToken(UUID actorUserId, UUID userId) {
        requireSelf(actorUserId, userId);
        return avatarService.createUploadToken(userId);
    }

    public void updateAvatar(UUID actorUserId, UUID userId, String fileName) {
        requireSelf(actorUserId, userId);
        avatarService.assertAndConsumeUploadTicket(userId, fileName);
        userService.updateHeaderUrl(userId, avatarService.buildAvatarUrl(fileName));
    }
}
```

- [ ] **Step 4: Re-run the focused user-secondary suite and confirm GREEN**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=AdminUserControllerUnitTest,FilesControllerStorageRoutingTest,UserControllerLoggingTest test
```

Expected:

- PASS
- avatar policy enforcement and file-key validation live in owner application services instead of the controllers
- `AdminUserController` depends only on `AdminUserApplicationService`

- [ ] **Step 5: Checkpoint the user-secondary slice**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/FilesController.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/UserFileApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/dto/AvatarFileResource.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/UserAvatarApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/FilesControllerStorageRoutingTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java
git commit -m "refactor: align user secondary controllers with application services"
```

### Task 4: Align Wallet And Market Controllers With Owner Application Services

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/AdminWalletController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/AdminWalletApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketActionService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/AdminMarketApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/AdminWalletControllerTest.java`
- Move: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketActionServiceTest.java` -> `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`

- [ ] **Step 1: Write the failing controller/service tests for the wallet and market owner entrypoints**

```java
// backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java
@WebMvcTest(WalletController.class)
@Import({
        WalletController.class,
        WalletApplicationService.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class WalletControllerTest {

    @MockBean
    private WalletQueryService walletQueryService;

    @MockBean
    private RechargeService rechargeService;
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java
@Test
void escrowReleaseAndRefundShouldPostOrderTransactions() {
    WalletMarketTxnView escrow = walletMarketActionApi.escrowOrder("order:1:escrow", firstBuyerId, 2_000L, "virtual-order:1");
    assertThat(escrow.txnType()).isEqualTo("ORDER_ESCROW");
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java
@Test
void resolveRefundShouldDelegateToAdminMarketApplicationService() throws Exception {
    UUID actorUserId = uuid(99);
    UUID disputeId = uuid(1);

    mockMvc.perform(post("/api/admin/market/disputes/" + disputeId + "/resolve-refund")
                    .with(jwt().jwt(jwt -> jwt.subject(actorUserId.toString())).authorities(() -> "ROLE_ADMIN")))
            .andExpect(status().isOk());
}
```

- [ ] **Step 2: Run the focused wallet/market suite and confirm RED**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=WalletControllerTest,AdminWalletControllerTest,WalletMarketApplicationServiceTest,MarketControllerTest,AdminMarketControllerTest test
```

Expected:

- RED until the controller constructors and renamed `WalletMarketApplicationService` line up
- market and wallet controllers still depend on raw owner services before the rewire

- [ ] **Step 3: Introduce the owner application services and rename the same-domain wallet-market implementation**

```java
// backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java
@Service
public class WalletApplicationService {

    public WalletSummaryResponse summary(UUID userId) {
        return walletQueryService.summary(userId);
    }

    public CreateRechargeResponse recharge(UUID userId, CreateRechargeRequest request) {
        return rechargeService.complete(request.getRequestId(), userId, request.getAmount());
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java
@Service
public class MarketApplicationService {

    public List<MarketListingResponse> listPublicListings() {
        return marketQueryService.listPublicListings();
    }

    public MarketListingResponse createListing(UUID sellerUserId, CreateMarketListingRequest request) {
        return marketListingService.createListing(sellerUserId, request, request.getInventory());
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/market/service/AdminMarketApplicationService.java
@Service
public class AdminMarketApplicationService {

    public List<MarketDisputeResponse> listOpenDisputes() {
        return marketDisputeService.listOpenDisputes();
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/wallet/service/AdminWalletApplicationService.java
@Service
public class AdminWalletApplicationService {

    public void freezeWallet(UUID actorUserId, UUID userId, String reason) {
        adminWalletOpsService.freezeWallet(actorUserId, userId, reason);
    }
}
```

- [ ] **Step 4: Re-run the focused wallet/market suite and confirm GREEN**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=WalletControllerTest,AdminWalletControllerTest,WalletMarketApplicationServiceTest,MarketControllerTest,AdminMarketControllerTest test
```

Expected:

- PASS
- wallet and market controllers now depend only on owner `*ApplicationService`
- `WalletMarketActionApi` remains stable for foreign-domain collaborators while the local implementation is renamed to `WalletMarketApplicationService`

- [ ] **Step 5: Checkpoint the wallet/market slice**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/AdminWalletController.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/service/AdminWalletApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/AdminMarketApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/AdminWalletControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java
git commit -m "refactor: align wallet and market controllers with application services"
```

### Task 5: Document The Phase 2 Rollout And Verify The Combined State

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `docs/business-logic/market-order-dispute-flow.md`
- Modify: `docs/business-logic/ops-scheduler-compensation-flow.md`
- Modify: `docs/business-logic/wallet-ledger-flow.md`
- Verify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Verify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Verify: `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`

- [ ] **Step 1: Update the architecture and business-logic docs to reflect the phase-2 rollout**

```markdown
<!-- docs/ARCHITECTURE.md -->
- `auth`、`analytics`、`notice`、`search`、`wallet`、`market` 以及 `user` 的 admin / file / avatar 入口也已统一到 owner `*ApplicationService`。
- `OpsController` 继续保留在 `SearchReindexActionApi` 上，因为它消费的是 search owner 的跨域 action boundary，而不是 same-domain controller drift。
- `WalletMarketActionApi` 继续作为 foreign-domain 协作面保留；同 JVM owner implementation 统一命名为 `WalletMarketApplicationService`。

<!-- docs/SYSTEM_DESIGN.md -->
Phase 2 lightweight rollout covered:
- auth / analytics / search / notice controller-facing entrypoints
- lightweight content controllers: category / subscription / tag
- user admin / files / avatar entrypoints
- wallet / market controller-facing entrypoints
```

- [ ] **Step 2: Run the focused combined suite for the phase-1 + phase-2 surface**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=\
ControllerBoundaryArchTest,\
DomainBoundaryArchTest,\
PublicReadEndpointSecurityTest,\
AuthControllerUnitTest,\
AnalyticsControllerUnitTest,\
SearchControllerTest,\
NoticeControllerUnitTest,\
CategoryControllerTest,\
SubscriptionControllerTest,\
TagControllerTest,\
AdminUserControllerUnitTest,\
FilesControllerStorageRoutingTest,\
UserControllerLoggingTest,\
WalletControllerTest,\
AdminWalletControllerTest,\
WalletMarketApplicationServiceTest,\
MarketControllerTest,\
AdminMarketControllerTest,\
PostScoreRefresherTest \
test
```

Expected:

- PASS
- no new same-domain controller depends on same-domain `api.*`
- the lightweight phase-2 controller entrypoints stay green under the phase-1 ArchUnit lock

- [ ] **Step 3: Run the full module verification**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app test
```

Expected:

- PASS
- module ends with `BUILD SUCCESS`
- current worktree proves the combined phase-1 + phase-2 controller/application-service rollout is internally consistent

- [ ] **Step 4: Record the phase-2 checkpoint**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  docs/ARCHITECTURE.md \
  docs/SYSTEM_DESIGN.md \
  docs/business-logic/market-order-dispute-flow.md \
  docs/business-logic/ops-scheduler-compensation-flow.md \
  docs/business-logic/wallet-ledger-flow.md \
  docs/superpowers/plans/2026-04-24-community-app-application-layer-style-phase2-lightweight-rollout.md
git commit -m "docs: record application layer style phase2 rollout"
```

---

## Self-Review Checklist

### Spec Coverage

- Same-domain owner `*ApplicationService` rule: preserved from the 2026-04-23 design and extended across the extra domains already changed in this worktree
- `user` main profile slice remains phase-1 and is not redefined here; only secondary entrypoints are added in this phase
- `content` main convergence remains owned by phase-1 / content follow-up plans; this phase only captures the lightweight controller wrappers and score-write naming already present
- `wallet` / `market` naming-clarity rule from spec section 8.3: covered by Task 4
- wider untouched domains such as `growth` and `ops` cross-domain action cleanup: still intentionally deferred

### Placeholder Scan

- No `TODO` / `TBD`
- Every task has concrete files, commands, and code excerpts
- No “similar to above” shorthand

### Type Consistency

- same-domain controller entry types are consistently named `*ApplicationService`
- cross-domain collaboration surfaces remain `*QueryApi` / `*ActionApi`
- `WalletMarketActionApi` remains the foreign-domain contract while `WalletMarketApplicationService` is the local owner implementation
- `OpsController` is intentionally excluded from this rollout because its `SearchReindexActionApi` dependency is still the intended foreign-domain boundary
