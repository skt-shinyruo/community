# Community App Architecture Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Converge `backend/community-app` onto one internal collaboration model by keeping `api.query` / `api.action` / `api.model` as cross-domain boundaries, moving same-domain adapters back onto local services, and deleting redundant wrapper-only layers without changing external behavior.

**Architecture:** Do not undo the existing owner-domain API strategy; too much of the codebase, docs, and ArchUnit coverage already assumes it. Instead, narrow its role: foreign domains use owner-domain `api.*`, while controllers and same-domain orchestration classes use local `service` / `app` classes directly. Start with a content-domain pilot that removes dead layers, then sweep representative same-domain misuses in `user` and `search`, and finally lock the new rule in docs and tests.

**Tech Stack:** Java 17, Spring Boot 3, ArchUnit, MyBatis, JUnit 5, Maven

---

## File Structure Map

### Guardrails and documentation

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
  Role: encode the missing distinction between "owner-domain collaboration API" and "same-domain adapter path" so the code stops drifting back into mixed styles.

### Content pilot slice

- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingActionServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
  Role: use one high-traffic slice to prove the convergence pattern: controllers call same-domain services directly, foreign callers keep using `content.api.*`, and dead wrapper layers are removed.

### Representative same-domain cleanup outside content

- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchAdminService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`
  Role: remove same-domain `api.*` usage where it adds no boundary value and standardize the local call path across a second and third domain before broad rollout.

### Verification and rollout notes

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeServiceTest.java`
  Role: ensure the new rules do not regress existing owner-domain guarantees or unrelated slices that are already simple and healthy.

---

### Task 1: Freeze The Target Model In Docs And ArchUnit

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`

- [ ] **Step 1: Write the failing ArchUnit rule for same-domain controller-to-owner-api coupling**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
@ArchTest
static final ArchRule controllers_must_not_depend_on_same_domain_owner_api_packages =
        classes()
                .that().resideInAnyPackage("..controller..")
                .should(ArchitectureRulesSupport.notDependOnSameDomainOwnerApiPackages(
                        Set.of(
                                "com.nowcoder.community.content.controller.PostController",
                                "com.nowcoder.community.user.controller.UserController"
                        )));
```

- [ ] **Step 2: Add the helper that detects same-domain `api.query` / `api.action` / `api.model` imports**

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
            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (!originDomain.equals(domainOf(target))) {
                    continue;
                }
                if (!target.getPackageName().contains(".api.")) {
                    continue;
                }
                events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
            }
        }
    };
}
```

- [ ] **Step 3: Run the architecture tests and confirm the new rule points at real same-domain drift**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- the suite goes RED on current same-domain controller users such as `PostController` and `UserController`
- existing foreign-domain boundary rules remain green

- [ ] **Step 4: Update the architecture docs so the target model is explicit**

```markdown
<!-- docs/ARCHITECTURE.md -->
- 跨域同步协作统一通过 owner-domain `api.query`、`api.action`、`api.model` 完成。
- `controller`、同域 `app`、同域 `service` 不应再把本域 `api.*` 当作默认入口；它们直接依赖本域实现层即可。
- `api.*` 的语义是“owner-domain collaboration boundary”，不是“域内通用 service locator”。

<!-- docs/SYSTEM_DESIGN.md -->
- `community-app` 进程内需要区分两条同步路径：
  1. 同域适配路径：`controller -> app/service/use-case`
  2. 跨域协作路径：`consumer-domain -> owner-domain api.*`
```

- [ ] **Step 5: Re-run the architecture tests with the temporary same-domain controller baseline**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- Maven exits with code `0`
- the new baseline freezes current same-domain controller users in place
- future same-domain `controller -> api.*` additions fail immediately

- [ ] **Step 6: Commit the guardrail and documentation change**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java \
  docs/ARCHITECTURE.md \
  docs/SYSTEM_DESIGN.md
git commit -m "test: freeze same-domain owner api usage"
```

### Task 2: Use Content As The Pilot Slice And Delete Dead Wrapper Layers

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingActionServiceTest.java`

- [ ] **Step 1: Write a controller unit test that documents the new local dependency shape**

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java
@Test
void create_usesSameDomainPublishingService() {
    PostPublishingActionService publishing = mock(PostPublishingActionService.class);
    when(publishing.create(7, "idem-1", "t", "c", 3, List.of("java")))
            .thenReturn(new PostCreateResult(101));

    PostController controller = new PostController(
            mock(PostReadQueryService.class),
            mock(CommentReadQueryService.class),
            publishing,
            mock(PostModerationActionService.class),
            mock(CommentActionApi.class)
    );

    Result<CreatePostResponse> result = controller.create(
            authenticationForUser(7),
            "idem-1",
            request("t", "c", 3, List.of("java"))
    );

    assertThat(result.getData().getPostId()).isEqualTo(101);
    verify(publishing).create(7, "idem-1", "t", "c", 3, List.of("java"));
}
```

- [ ] **Step 2: Run the focused content test set and confirm RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=PostControllerUnitTest,PostPublishingActionServiceTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- compilation or wiring failures show that `PostController` still depends on `content.api.*`
- do not change production code until the failure points are clear

- [ ] **Step 3: Rewire `PostController` onto same-domain services and remove `PostCommandService`**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java
private final PostReadQueryService postReadQueryService;
private final CommentReadQueryService commentReadQueryService;
private final PostPublishingActionService postPublishingActionService;
private final PostModerationActionService postModerationActionService;

public PostController(
        PostReadQueryService postReadQueryService,
        CommentReadQueryService commentReadQueryService,
        PostPublishingActionService postPublishingActionService,
        PostModerationActionService postModerationActionService,
        CommentActionApi commentActionApi
) {
    this.postReadQueryService = postReadQueryService;
    this.commentReadQueryService = commentReadQueryService;
    this.postPublishingActionService = postPublishingActionService;
    this.postModerationActionService = postModerationActionService;
    this.commentActionApi = commentActionApi;
}
```

```java
// delete backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java
// delete backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java
```

- [ ] **Step 4: Keep foreign callers stable by leaving the owner-domain API implementations in place**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java
@Service
public class PostPublishingActionService implements PostPublishingActionApi {
    // unchanged public API for foreign domains
    // controller now calls this class directly because it is the same-domain implementation
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionService.java
@Service
public class PostModerationActionService implements PostModerationActionApi {
    // unchanged public API for foreign domains
}
```

- [ ] **Step 5: Run the focused content verification suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=PostControllerUnitTest,PostPublishingActionServiceTest,PostReadQueryServiceTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- Maven exits with code `0`
- `PostController` no longer appears in the same-domain owner-api baseline
- no production references remain to `PostCommandService`

- [ ] **Step 6: Commit the pilot slice**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingActionServiceTest.java
git rm \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostCommandService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/service/PostCommandServiceLoggingTest.java
git commit -m "refactor: converge content local call path"
```

### Task 3: Sweep Same-Domain `api.*` Misuse In User And Search

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchAdminService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`

- [ ] **Step 1: Write failing tests that cover the representative local-call rewires**

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java
@Test
void resolveByUsername_usesUserQueryServiceDirectly() {
    UserQueryService userQueryService = mock(UserQueryService.class);
    when(userQueryService.getSummaryByUsername("alice"))
            .thenReturn(new UserSummaryView(7, "alice", "/avatar.png", 0));

    UserController controller = new UserController(
            userQueryService,
            mock(GetUserProfilePageQuery.class),
            mock(UserService.class),
            mock(AvatarService.class)
    );

    Result<UserResolveResponse> result = controller.resolveByUsername("alice");

    assertThat(result.getData().getId()).isEqualTo(7);
    verify(userQueryService).getSummaryByUsername("alice");
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java
@Test
void local_search_admin_path_doesNotNeed_owner_api_indirection() {
    SearchReindexExecutionService executionService = mock(SearchReindexExecutionService.class);
    when(executionService.reindex()).thenReturn(new SearchReindexResult("job-1", 12, false, null));

    SearchAdminService adminService = new SearchAdminService(executionService, mock(ReindexJobService.class));

    SearchReindexResponse response = adminService.reindex();

    assertThat(response.getJobId()).isEqualTo("job-1");
    assertThat(response.getIndexedCount()).isEqualTo(12);
}
```

- [ ] **Step 2: Run the focused user/search test set and confirm RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UserControllerUnitTest,UserControllerLoggingTest,SearchReindexExecutionServiceTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- failures point to `UserController` and `SearchAdminService` still depending on same-domain owner APIs

- [ ] **Step 3: Rewire same-domain callers onto local implementations**

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java
private final UserQueryService userQueryService;

public UserController(
        UserQueryService userQueryService,
        GetUserProfilePageQuery getUserProfilePageQuery,
        UserService userService,
        AvatarService avatarService
) {
    this.userQueryService = userQueryService;
    this.getUserProfilePageQuery = getUserProfilePageQuery;
    this.userService = userService;
    this.avatarService = avatarService;
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java
public GetUserProfilePageQuery(
        UserQueryService userQueryService,
        UserSocialProfileService userSocialProfileService,
        PostReadQueryApi postReadQueryApi,
        UserLevelQueryApi userLevelQueryApi
) {
    this.userQueryService = userQueryService;
    this.userSocialProfileService = userSocialProfileService;
    this.postReadQueryApi = postReadQueryApi; // foreign domain API remains valid here
    this.userLevelQueryApi = userLevelQueryApi; // foreign domain API remains valid here
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchAdminService.java
public class SearchAdminService {
    private final SearchReindexExecutionService executionService;

    public SearchAdminService(SearchReindexExecutionService executionService,
                              ReindexJobService reindexJobService) {
        this.executionService = executionService;
        this.reindexJobService = reindexJobService;
    }

    public SearchReindexResponse reindex() {
        SearchReindexResult result = executionService.reindex();
        ...
    }
}
```

- [ ] **Step 4: Shrink the same-domain controller baseline and re-run the architecture suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UserControllerUnitTest,UserControllerLoggingTest,SearchReindexExecutionServiceTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- Maven exits with code `0`
- `UserController` is removed from the same-domain owner-api baseline
- only unresolved domains remain on the migration list

- [ ] **Step 5: Commit the user/search sweep**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/app/query/GetUserProfilePageQuery.java \
  backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchAdminService.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
git commit -m "refactor: converge local owner-domain callers"
```

### Task 4: Finish The Convergence Slice And Publish The Rollout Rule

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeServiceTest.java`

- [ ] **Step 1: Document the rollout rule domain-by-domain**

```markdown
<!-- docs/SYSTEM_DESIGN.md -->
迁移顺序固定为：
1. 先加 ArchUnit baseline，冻结新增漂移
2. 再改同域 `controller` / `app` 到本域实现类
3. 保留 foreign-domain `api.*` 不变，避免跨域调用面抖动
4. 删除死层、未被生产路径使用的 wrapper service 与其测试

完成一个域后，必须把该域从 baseline 中移除，再进入下一域。
```

- [ ] **Step 2: Remove migrated controllers from the temporary baseline**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
Set.of(
    // keep only unresolved controllers here
)
```

- [ ] **Step 3: Run the full focused verification suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest,PostControllerUnitTest,PostPublishingActionServiceTest,PostReadQueryServiceTest,UserControllerUnitTest,UserControllerLoggingTest,SearchReindexExecutionServiceTest,NoticeServiceTest test
```

Expected:

- Maven exits with code `0`
- the content, user, search, and notice slices remain green
- no deleted wrapper class is referenced anywhere in production code

- [ ] **Step 4: Publish the rollout checklist for the remaining domains**

```markdown
Remaining domains after this slice:
- `auth`: keep current simple `controller -> service`, only remove same-domain `api.*` if introduced later
- `notice`: keep current simple `controller -> service`
- `growth` / `wallet` / `market`: apply the same rule only when touching those areas; do not bulk-rewrite untouched flows
```

- [ ] **Step 5: Commit the verification and rollout docs**

```bash
cd /home/feng/code/project/community
git add \
  docs/ARCHITECTURE.md \
  docs/SYSTEM_DESIGN.md \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
git commit -m "docs: publish community-app convergence rule"
```

---

## Self-Review

### Spec coverage

- The plan keeps the current owner-domain `api.*` strategy instead of replacing it, which matches the current green ArchUnit rules and docs.
- The plan directly addresses the observed mixed styles: content controller-to-api layering, user same-domain api usage, search local api indirection, and dead `PostCommandService`.
- The plan intentionally does not broaden into gateway, IM, or deployment topology changes because those are not the current bottleneck.

### Placeholder scan

- No `TODO`, `TBD`, or "implement later" placeholders remain.
- Each task names exact files, commands, and expected outcomes.
- Each production-code step includes concrete code snippets instead of abstract intentions.

### Type consistency

- The plan consistently treats `api.*` as foreign-domain collaboration only.
- The local same-domain call path is consistently described as `controller/app -> local service`.
- The content pilot, user cleanup, and search cleanup all use the same convergence rule instead of competing patterns.
