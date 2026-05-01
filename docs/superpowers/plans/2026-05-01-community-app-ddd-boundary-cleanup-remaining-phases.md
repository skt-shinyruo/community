# Community App DDD Boundary Cleanup Remaining Phases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Phase Two, Phase Three, and Phase Four from `2026-05-01-community-app-ddd-boundary-cleanup-design.md` after Phase One has already passed.

**Architecture:** Each remaining phase starts with executable guardrails, then performs behavior-preserving refactors inside one subsystem. Content ports move to domain repositories or application-root technical ports; market repositories expose domain semantics instead of mapper names; large post Vue views delegate state/workflow logic to smaller modules while preserving UI and API behavior.

**Tech Stack:** Java 17, Spring Boot 3.2, ArchUnit, JUnit 5, Maven, Vue 3, Vite, Vitest.

---

## Phase Two: Content Package Shape Convergence

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Move/delete package: `backend/community-app/src/main/java/com/nowcoder/community/content/application/port`
- Move package: `backend/community-app/src/main/java/com/nowcoder/community/content/application/assembler`
- Move/rename classes under `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/*Service.java`
- Update content application, infrastructure, controller, and tests that import the moved types.

- [x] **Step 1: Add RED guardrails**

Add rules to `DddLayeringArchTest`:

```java
@ArchTest
static final ArchRule content_application_port_package_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..content.application.port..")
                .because("content persistence contracts belong in domain.repository and technical ports belong in application root")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule content_application_assembler_package_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..content.application.assembler..")
                .because("content application assemblers live in the application root or controller boundary")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule content_infrastructure_persistence_services_must_stay_retired =
        noClasses()
                .that().resideInAnyPackage("..content.infrastructure.persistence..")
                .should().haveSimpleNameEndingWith("Service")
                .because("content persistence implementations use MyBatis*Repository or explicit adapter names")
                .allowEmptyShould(true);
```

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest test
```

Expected: FAIL while old content `application.port`, `application.assembler`, and `infrastructure.persistence.*Service` classes still exist.

- [x] **Step 2: Move content persistence contracts**

Move these persistence-oriented interfaces to `content.domain.repository` and rename imports/usages:

```text
BookmarkContentPort      -> content.domain.repository.BookmarkRepository
CategoryContentPort      -> content.domain.repository.CategoryContentRepository
CommentContentPort       -> content.domain.repository.CommentContentRepository
PostContentPort          -> content.domain.repository.PostContentRepository
ReportContentPort        -> content.domain.repository.ReportContentRepository
SubscriptionContentPort  -> content.domain.repository.SubscriptionRepository
TagContentPort           -> content.domain.repository.TagContentRepository
```

`BookmarkRepository` must expose only:

```java
void add(UUID userId, UUID postId);
void remove(UUID userId, UUID postId);
boolean hasBookmarked(UUID userId, UUID postId);
List<DiscussPost> listBookmarkedPosts(UUID userId, int page, int size);
```

Move technical application ports out of `application.port` into the `content.application` root:

```text
ContentSanitizer       -> content.application.ContentSanitizer
LikeQueryPort          -> content.application.LikeQueryPort
PostScoreQueuePort     -> content.application.PostScoreQueue
ContentModerationPort  -> content.application.ContentModerationGateway
ModerationNoticePort   -> content.application.ModerationNoticePublisher
```

- [x] **Step 3: Move content assemblers**

Move these classes from `content.application.assembler` to `content.application` and update package/imports:

```text
PostSummaryAssembler
PostDetailAssembler
RecentUserCommentAssembler
```

- [x] **Step 4: Rename content persistence services**

Rename implementation classes and update constructor/test imports:

```text
PostService          -> MyBatisPostContentRepository
CommentService       -> MyBatisCommentContentRepository
CategoryService      -> MyBatisCategoryContentRepository
TagService           -> MyBatisTagContentRepository
BookmarkService      -> MyBatisBookmarkRepository
SubscriptionService  -> MyBatisSubscriptionRepository
ReportService        -> MyBatisReportContentRepository
ModerationService    -> MyBatisModerationQueryRepository
```

`MyBatisBookmarkRepository` must not assemble `PostSummaryResult`. `BookmarkApplicationService` owns that result assembly by injecting `CommentContentRepository`, `TagContentRepository`, and `PostSummaryAssembler`.

- [x] **Step 5: Verify content phase**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest,PostReadApplicationServiceTest,BookmarkApplicationServiceTest,CommentServiceTest,TagServiceTest,CategoryServiceTest,ModerationServiceProjectionTest,PaginationOffsetOverflowTest test
```

Expected: PASS.

Commit:

```bash
cd /home/feng/code/project/community
git add backend/community-app/src/main/java backend/community-app/src/test/java
git commit -m "refactor: converge content package shape"
```

## Phase Three: Market Repository Semantics

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/repository/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/persistence/MyBatisMarket*Repository.java`
- Update affected market tests.

- [ ] **Step 1: Add RED guardrails**

Add a rule that checks public methods in `..market.domain.repository..` do not start with mapper verbs:

```java
@ArchTest
static final ArchRule market_domain_repositories_must_use_domain_method_names =
        classes()
                .that().resideInAnyPackage("..market.domain.repository..")
                .should(notDeclareMethodsStartingWith("select", "insert", "update"));
```

Add this helper in `DddLayeringArchTest`:

```java
private static ArchCondition<JavaClass> notDeclareMethodsStartingWith(String... forbiddenPrefixes) {
    return new ArchCondition<>("not declare methods starting with mapper-style prefixes") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            for (JavaMethod method : item.getMethods()) {
                if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                    continue;
                }
                for (String prefix : forbiddenPrefixes) {
                    if (method.getName().startsWith(prefix)) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                method.getFullName() + " starts with " + prefix
                        ));
                    }
                }
            }
        }
    };
}
```

Add this rule and helper for market application field names:

```java
@ArchTest
static final ArchRule market_applications_must_not_name_repositories_as_mappers =
        classes()
                .that().resideInAnyPackage("..market.application..")
                .should(notDeclareFieldsEndingWith("Mapper"));

private static ArchCondition<JavaClass> notDeclareFieldsEndingWith(String forbiddenSuffix) {
    return new ArchCondition<>("not declare fields ending with " + forbiddenSuffix) {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            item.getFields().forEach(field -> {
                if (field.getName().endsWith(forbiddenSuffix)) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            field.getFullName() + " ends with " + forbiddenSuffix
                    ));
                }
            });
        }
    };
}
```

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest test
```

Expected: FAIL on existing market repository method names and application fields.

- [ ] **Step 2: Rename repository method semantics**

Apply these naming mappings only to domain repositories, application callers, and MyBatis repository implementations. Do not rename MyBatis mapper method names.

```text
insert                         -> save
selectById                     -> findById
selectByIdForUpdate            -> lockById
selectByRequestId              -> findByRequestId
selectByRequestIdForUpdate     -> lockByRequestId
selectByBuyerUserIdAndRequestId -> findByBuyerUserIdAndRequestId
selectByBuyerUserIdAndRequestIdForUpdate -> lockByBuyerUserIdAndRequestId
selectByBuyerUserId            -> findByBuyerUserId
selectBySellerUserId           -> findBySellerUserId
selectPublicListings           -> findPublicListings
selectAvailableForUpdate       -> lockAvailable
selectByReservedOrderId        -> findByReservedOrderId
selectByListingId              -> findByListingId
selectByOrderId                -> findByOrderId
selectOpenDisputes             -> findOpenDisputes
selectDue                      -> findDue
selectUnfinishedWithWalletTxn  -> findUnfinishedWithWalletTxn
selectDueForAutoConfirm        -> findDueForAutoConfirm
selectWalletPendingOrders      -> findWalletPendingOrders
updateEditable                 -> saveEditable
updateStatus                   -> changeStatus
```

Keep domain-specific `mark*`, `adjustStock`, `clearDefaultByUserId`, and `releaseReservedByOrderIfNeeded` names.

- [ ] **Step 3: Rename application fields**

Rename fields such as:

```text
marketListingMapper        -> marketListingRepository
marketInventoryUnitMapper  -> marketInventoryRepository
marketOrderMapper          -> marketOrderRepository
marketAddressMapper        -> marketAddressRepository
marketDeliveryMapper       -> marketDeliveryRepository
marketShipmentMapper       -> marketShipmentRepository
marketDisputeMapper        -> marketDisputeRepository
```

- [ ] **Step 4: Verify market phase**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest,Market*Test,*Market*Test test
```

Expected: PASS.

Commit:

```bash
cd /home/feng/code/project/community
git add backend/community-app/src/main/java backend/community-app/src/test/java
git commit -m "refactor: use domain names for market repositories"
```

## Phase Four: Frontend Complexity Reduction

**Files:**
- Modify: `frontend/src/views/PostDetailView.vue`
- Modify: `frontend/src/views/PostsView.vue`
- Create: `frontend/src/views/post-detail/usePostDetailLoader.js`
- Create: `frontend/src/views/post-detail/usePostDetailDrafts.js`
- Create: `frontend/src/views/post-detail/usePostDetailInteractions.js`
- Create: `frontend/src/views/post-detail/usePostDetailModeration.js`
- Create: `frontend/src/views/post-detail/PostDetailComments.vue`
- Create: `frontend/src/views/post-detail/PostDetailActions.vue`
- Create: `frontend/src/views/posts/usePostsFeed.js`
- Create: `frontend/src/views/posts/usePostComposer.js`
- Create: `frontend/src/views/posts/PostsFeedList.vue`
- Create: `frontend/src/views/posts/PostComposerPanel.vue`
- Modify/add Vitest tests under `frontend/src/views`.

- [ ] **Step 1: Add RED complexity test**

Create `frontend/src/views/viewComplexity.test.js`:

```js
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

function lineCount(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8').split('\n').length
}

describe('view complexity guardrails', () => {
  it('keeps post views split into focused modules', () => {
    expect(lineCount('src/views/PostDetailView.vue')).toBeLessThanOrEqual(900)
    expect(lineCount('src/views/PostsView.vue')).toBeLessThanOrEqual(900)
  })
})
```

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- PostDetailView PostsView postDetailState postsViewState postsFeedState
```

Expected: FAIL before the split.

- [ ] **Step 2: Split PostDetailView**

Move post detail workflows into smaller modules/components while preserving DOM-visible behavior:

```text
post-detail/usePostDetailLoader.js
post-detail/usePostDetailDrafts.js
post-detail/usePostDetailInteractions.js
post-detail/usePostDetailModeration.js
post-detail/PostDetailComments.vue
post-detail/PostDetailActions.vue
```

- [ ] **Step 3: Split PostsView**

Move feed workflows into smaller modules/components while preserving DOM-visible behavior:

```text
posts/usePostsFeed.js
posts/usePostComposer.js
posts/PostsFeedList.vue
posts/PostComposerPanel.vue
```

- [ ] **Step 4: Verify frontend phase**

Run:

```bash
cd /home/feng/code/project/community/frontend
npm test -- PostDetailView PostsView postDetailState postsViewState postsFeedState
npm test
```

Expected: PASS.

Commit:

```bash
cd /home/feng/code/project/community
git add frontend/src
git commit -m "refactor: split post view workflows"
```

## Final Verification

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
cd /home/feng/code/project/community/frontend
npm test
```

Expected: both PASS.

Update `docs/superpowers/specs/2026-05-01-community-app-ddd-boundary-cleanup-design.md` implementation record with Phase Two, Three, and Four commits, then commit:

```bash
cd /home/feng/code/project/community
git add docs/superpowers/specs/2026-05-01-community-app-ddd-boundary-cleanup-design.md
git commit -m "docs: close remaining ddd cleanup phases"
```
