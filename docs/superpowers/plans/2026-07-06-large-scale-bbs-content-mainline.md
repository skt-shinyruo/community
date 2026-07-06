# Large-Scale BBS Content Mainline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current offset-based content mainline with a large-community-ready feed, detail, hotness, counter, and comment architecture built around cursor APIs, Redis-backed read models, durable projections, and a two-level comment model.

**Architecture:** Keep `community-app` as the content owner for synchronous writes, but move homepage and detail read pressure onto Redis-backed feed, summary, detail, and counter caches. Use durable content/social events for hotness and notice projection, and rewrite the comment model around explicit post/root/parent reply relationships instead of the current generic entity tree.

**Tech Stack:** Java 17, Spring Boot 3.2.6, Maven reactor, MyBatis, MySQL, Redis, Spring Kafka, Elasticsearch, Vue 3, Pinia, Vitest, axios-mock-adapter.

## Global Constraints

- target scale: `large community`
- first sub-project: `content mainline`
- homepage model: `public global feed`
- homepage consistency: `detail immediately visible, feed catches up in 3-10 seconds`
- homepage ranking: `hot-first`
- post detail consistency: `new comments immediately visible, counters may lag by 1-5 seconds`
- comment structure: `two-level comments`
- moderation model: `no pre-publication review, but keep governance capabilities`
- hotness production: `near-real-time precomputation`
- information architecture: `fixed boards/categories`
- homepage entry: `global homepage first`
- compatibility stance: `breaking changes are allowed; do not preserve historical compatibility`
- feed APIs should move from `page + offset` toward cursor semantics
- do not route homepage or board ranking through ES
- do not treat ES score as the homepage hotness source
- key notice-producing content events should move onto durable outbox-backed projection
- split derived read-model runtimes before splitting the owner write service
- inbound adapters must call same-domain `*ApplicationService` only
- `application` must not depend directly on MyBatis mapper or dataobject types
- `domain` must not depend on `controller`, `application`, `infrastructure`, MyBatis mapper/dataobject types, HTTP DTOs, Spring framework, or owner-domain `api.*`
- project-related documentation must live under `docs/handbook`; specs and implementation plans must live under `docs/superpowers/specs` and `docs/superpowers/plans`

---

## File Map

- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/FeedController.java`: new public homepage/board cursor feed controller.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`: owner-domain orchestration for global and board hot feed reads.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedCursorCodec.java`: opaque cursor encode/decode for feed and comment pages.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/FeedPageResult.java`: application result for cursor feed pages.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/FeedPageResponse.java`: HTTP response for feed pages.
- `backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java`: public-read authorization for `/api/feed/**`.
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java`: controller contract tests for feed endpoints.
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java`: feed read orchestration tests.
- `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`: public-read security coverage for new endpoints.
- `frontend/src/api/services/postService.js`: frontend feed/detail/comment transport helpers.
- `frontend/src/api/services/postService.test.js`: frontend contract tests for new feed/comment APIs.
- `frontend/src/router/navigation.js`: route query normalization after removing order/filter/subscribed feed semantics.
- `frontend/src/router/navigation.test.js`: route helper tests for the new board-only feed query model.
- `frontend/src/components/posts/FeedToolbar.vue`: remove old order/filter/subscribed controls; keep board selector and refresh.
- `frontend/src/components/posts/FeedToolbar.test.js`: toolbar behavior after UI simplification.
- `frontend/src/views/PostsView.vue`: posts page shell using cursor feed loading.
- `frontend/src/views/posts/usePostsFeed.js`: cursor feed loader and board switching logic.
- `frontend/src/views/PostsView.test.js`: posts page integration tests for cursor loading.

Implementation note:
- Because the current `PostContentRepository.listPosts(...)` contract is page-based, the opaque feed cursor stores traversal page and page size state instead of a raw absolute offset.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java`: Redis-backed ordered ID feed cache port.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostSummaryCache.java`: summary cache port.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostDetailCache.java`: detail shell cache port.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`: Redis sorted-set feed store.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCache.java`: Redis summary cache implementation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCache.java`: Redis detail shell cache implementation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`: post detail read-through cache orchestration.
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java`: detail cache behavior tests.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java`: durable hotness projection orchestrator.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java`: typed projection command.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostHotnessDomainService.java`: score recalculation logic.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java`: durable content/social event listener for feed projection.
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`: hotness projection tests.
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`: listener wiring tests.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/{PostScoreQueue.java,PostScoreRefreshApplicationService.java,PostScoreUpdateApplicationService.java,PostWriteSideEffectScheduler.java}`: legacy score pipeline to retire.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/{job/PostScoreRefresher.java,persistence/RedisPostScoreQueue.java}`: legacy scheduler/queue to delete.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterCache.java`: Redis counter cache port.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterApplicationService.java`: counter read/write orchestration.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/RecordPostViewCommand.java`: typed detail-view signal.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostCounterSnapshot.java`: counter/score snapshot value object.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostCounterSnapshotRepository.java`: durable snapshot repository interface.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostCounterSnapshotRepository.java`: snapshot persistence implementation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCache.java`: Redis counter and dedupe implementation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostCounterSnapshotMapper.java`: MyBatis mapper for counter/score snapshots.
- `backend/community-app/src/main/resources/mapper/post_counter_snapshot_mapper.xml`: SQL for snapshot persistence.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostCounterSnapshotFlushJob.java`: scheduled snapshot flusher.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`: inline request/IP fingerprint helper for post detail views.
- `deploy/mysql/community/040_schema_content_core.sql`: content schema changes for comment shape and snapshot tables.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java`: new root/parent/reply-to comment command shape.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/{CommentPageResult.java,CommentResult.java}`: cursor comment page and item results.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/{CreateCommentRequest.java,CommentPageResponse.java,CommentResponse.java}`: HTTP contract for the new comment APIs.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/{Comment.java,CommentDraft.java,CommentSnapshot.java,CommentDeletionResult.java}`: two-level comment domain model.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/{CommentRepository.java,CommentContentRepository.java}`: repository contracts for the new comment shape.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java`: target resolution and deletion logic for two-level comments.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/{CommentApplicationService.java,CommentReadApplicationService.java}`: new cursor comment orchestration.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/{MyBatisCommentRepository.java,MyBatisCommentContentRepository.java}`: MyBatis write/read implementations for the new schema.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapper.java`: mapper signatures for root/reply cursor queries.
- `backend/community-app/src/main/resources/mapper/comment-mapper.xml`: SQL rewrite for two-level comments.
- `backend/community-app/src/test/java/com/nowcoder/community/content/{application/CommentApplicationServiceTest.java,application/CommentReadApplicationServiceTest.java,infrastructure/persistence/MyBatisCommentRepositoryTest.java,infrastructure/persistence/mapper/CommentMapperPersistenceTest.java,controller/PostControllerUnitTest.java}`: backend regression coverage for comment API and persistence.
- `frontend/src/views/post-detail/usePostDetailLoader.js`: frontend cursor comment loading and reply submission.
- `frontend/src/views/post-detail/PostDetailComments.vue`: detail-page comment list UI for next-cursor loading.
- `frontend/src/views/PostDetailView.test.js`: new detail-page comment flow test.
- `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListener.java`: local best-effort listener to retire.
- `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`: durable content/social notice projection listener to keep and simplify.
- `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/{NoticeProjectionListenerTest.java,NoticeProjectionListenerStructureTest.java,NoticeProjectionKafkaListenerTest.java}`: notice projection regression tests.
- `docs/handbook/business-flows.md`: content and notice flow documentation updates.
- `docs/handbook/data-and-storage.md`: Redis key, snapshot table, and feed cache documentation updates.

---

### Task 1: Ship The New Public Feed Contract End-To-End

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/FeedController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedCursorCodec.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/FeedPageResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/FeedPageResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java`
- Modify: `frontend/src/api/services/postService.js`
- Modify: `frontend/src/api/services/postService.test.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/navigation.test.js`
- Modify: `frontend/src/components/posts/FeedToolbar.vue`
- Modify: `frontend/src/components/posts/FeedToolbar.test.js`
- Modify: `frontend/src/views/PostsView.vue`
- Modify: `frontend/src/views/posts/usePostsFeed.js`
- Modify: `frontend/src/views/PostsView.test.js`

**Interfaces:**
- Consumes: `PostContentRepository.listPosts(int page, int size, int orderMode, UUID categoryId, String tag)`, `PostSummaryAssembler`, existing `PostSummaryResult`.
- Produces:
  - `FeedReadApplicationService.listGlobalHotFeed(UUID currentUserId, String cursor, int size): FeedPageResult`
  - `FeedReadApplicationService.listBoardHotFeed(UUID currentUserId, UUID boardId, String cursor, int size): FeedPageResult`
  - `postService.listGlobalFeed({ cursor?: string, size?: number }): Promise<{ data: { items: any[], nextCursor: string, rankVersion: string }, traceId: string }>`
  - `postService.listBoardFeed(boardId, { cursor?: string, size?: number }): Promise<{ data: { items: any[], nextCursor: string, rankVersion: string }, traceId: string }>`

- [x] **Step 1: Write the failing backend and frontend feed contract tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java` with:

```java
@Test
void globalFeedShouldDelegateCursorRead() throws Exception {
    when(feedReadApplicationService.listGlobalHotFeed(null, "cursor-1", 20))
            .thenReturn(new FeedPageResult(List.of(), "cursor-2", "rank-v1"));

    mockMvc.perform(get("/api/feed/global")
                    .param("cursor", "cursor-1")
                    .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nextCursor").value("cursor-2"))
            .andExpect(jsonPath("$.data.rankVersion").value("rank-v1"));
}
```

Add to `frontend/src/api/services/postService.test.js`:

```js
it('listGlobalFeed should call /api/feed/global with cursor params', async () => {
  mock = new MockAdapter(http)
  mock.onGet('/api/feed/global').reply((config) => {
    expect(config.params).toEqual({ cursor: 'cursor-1', size: 12 })
    return [200, { code: 0, message: '', data: { items: [], nextCursor: 'cursor-2', rankVersion: 'rank-v1' }, traceId: 'trace-feed' }]
  })

  const resp = await listGlobalFeed({ cursor: 'cursor-1', size: 12 })

  expect(resp.traceId).toBe('trace-feed')
  expect(resp.data.nextCursor).toBe('cursor-2')
})
```

- [x] **Step 2: Run the focused tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=FeedControllerTest,FeedReadApplicationServiceTest,PublicReadEndpointSecurityTest
cd ../frontend
npm test -- src/api/services/postService.test.js src/views/PostsView.test.js src/components/posts/FeedToolbar.test.js src/router/navigation.test.js
```

Expected:

- backend FAIL because `FeedController`, `FeedReadApplicationService`, `FeedPageResult`, and security rules do not exist
- frontend FAIL because `listGlobalFeed` / `listBoardFeed` are not exported and the toolbar still expects order/filter/subscribed props

- [x] **Step 3: Implement the new feed contract without deleting the old `/api/posts` list yet**

Add `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/FeedPageResult.java`:

```java
package com.nowcoder.community.content.application.result;

import java.util.List;

public record FeedPageResult(
        List<PostSummaryResult> items,
        String nextCursor,
        String rankVersion
) {
}
```

Add `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedCursorCodec.java`:

```java
package com.nowcoder.community.content.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class FeedCursorCodec {

    public String encodeOffset(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"offset\":" + Math.max(0, offset) + "}").getBytes(StandardCharsets.UTF_8));
    }

    public int decodeOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String numeric = json.replaceAll("\\D+", "");
        return numeric.isBlank() ? 0 : Integer.parseInt(numeric);
    }
}
```

Add the core of `FeedReadApplicationService`:

```java
public FeedPageResult listGlobalHotFeed(UUID currentUserId, String cursor, int size) {
    int limit = Math.min(50, Math.max(1, size <= 0 ? 20 : size));
    int offset = feedCursorCodec.decodeOffset(cursor);
    List<DiscussPost> rows = postContentRepository.listPosts(offset / limit, limit + 1, PostContentRepository.ORDER_HOT, null, null);
    List<PostSummaryResult> items = assembleSummaries(rows.stream().limit(limit).toList());
    String nextCursor = rows.size() > limit ? feedCursorCodec.encodeOffset(offset + limit) : "";
    return new FeedPageResult(items, nextCursor, "db-fallback-v1");
}
```

Add `FeedController`:

```java
@RestController
@RequestMapping("/api")
public class FeedController {

    @GetMapping("/feed/global")
    public Result<FeedPageResponse> global(Authentication authentication,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer size) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        FeedPageResult page = feedReadApplicationService.listGlobalHotFeed(currentUserId, cursor, size == null ? 20 : size);
        return Result.ok(FeedPageResponse.from(page));
    }

    @GetMapping("/boards/{boardId}/feed")
    public Result<FeedPageResponse> board(Authentication authentication,
                                          @PathVariable UUID boardId,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer size) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        FeedPageResult page = feedReadApplicationService.listBoardHotFeed(currentUserId, boardId, cursor, size == null ? 20 : size);
        return Result.ok(FeedPageResponse.from(page));
    }
}
```

Update `frontend/src/api/services/postService.js` to export:

```js
export async function listGlobalFeed({ cursor = '', size = 20 } = {}) {
  const params = {}
  if (cursor) params.cursor = cursor
  if (size != null) params.size = size
  const resp = await http.get('/api/feed/global', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询首页热门流')
  return { data: normalizeFeedPage(data), traceId }
}

export async function listBoardFeed(boardId, { cursor = '', size = 20 } = {}) {
  const bid = requireOpaqueId(boardId, 'boardId')
  const params = {}
  if (cursor) params.cursor = cursor
  if (size != null) params.size = size
  const resp = await http.get(`/api/boards/${bid}/feed`, { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询版块热门流')
  return { data: normalizeFeedPage(data), traceId }
}
```

Update `frontend/src/components/posts/FeedToolbar.vue` props/emits to only keep board and refresh:

```vue
const props = defineProps({
  boardId: { type: [String, Number], default: '' },
  categories: { type: Array, default: () => [] },
  showClear: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:boardId', 'refresh', 'clear'])
```

Update `frontend/src/views/posts/usePostsFeed.js` to use:

```js
const nextCursor = ref('')
const boardId = computed(() => normalizePostsBoardId(route.query?.boardId))

async function loadFeed({ reset = false } = {}) {
  const cursor = reset ? '' : nextCursor.value
  const resp = boardId.value
    ? await listBoardFeed(boardId.value, { cursor, size: size.value })
    : await listGlobalFeed({ cursor, size: size.value })
  nextCursor.value = String(resp?.data?.nextCursor || '')
  hasNext.value = !!nextCursor.value
}
```

- [x] **Step 4: Run the focused tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=FeedControllerTest,FeedReadApplicationServiceTest,PublicReadEndpointSecurityTest
cd ../frontend
npm test -- src/api/services/postService.test.js src/views/PostsView.test.js src/components/posts/FeedToolbar.test.js src/router/navigation.test.js
```

Expected:

- backend PASS with `/api/feed/global` and `/api/boards/{boardId}/feed` public-read coverage
- frontend PASS with cursor-based feed service calls and simplified toolbar contract

- [x] **Step 5: Commit the new public feed contract**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/controller/FeedController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedCursorCodec.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/FeedPageResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/FeedPageResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java \
  frontend/src/api/services/postService.js \
  frontend/src/api/services/postService.test.js \
  frontend/src/router/navigation.js \
  frontend/src/router/navigation.test.js \
  frontend/src/components/posts/FeedToolbar.vue \
  frontend/src/components/posts/FeedToolbar.test.js \
  frontend/src/views/PostsView.vue \
  frontend/src/views/posts/usePostsFeed.js \
  frontend/src/views/PostsView.test.js
git commit -m "feat: add cursor-based public feed contract"
```

---

### Task 2: Add Redis Summary And Detail Read-Through Caches

Implementation note:
- In the current codebase and Task 2 write set, `PostFeedCache` / `RedisPostFeedCache` are not yet part of this task's editable surface. Task 2 therefore introduces summary/detail read-through caching after repository page rows are loaded; feed ID caching remains deferred until the later feed-runtime tasks that explicitly own `PostFeedCache`.

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostSummaryCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostDetailCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java`

**Interfaces:**
- Consumes: `FeedReadApplicationService.listGlobalHotFeed(...)`, `PostReadApplicationService.getPostDetail(...)`, existing `PostSummaryResult` / `PostDetailResult`.
- Produces:
  - `PostSummaryCache.getAll(List<UUID> postIds): Map<UUID, PostSummaryResult>`
  - `PostSummaryCache.putAll(List<PostSummaryResult> summaries): void`
  - `PostDetailCache.get(UUID postId): PostDetailResult`
  - `PostDetailCache.put(UUID postId, PostDetailResult detail): void`
  - `PostDetailCache.evict(UUID postId): void`

- [x] **Step 1: Write the failing cache behavior tests**

Add to `FeedReadApplicationServiceTest.java`:

```java
@Test
void globalFeedShouldBackfillMissingSummariesIntoCache() {
    when(postFeedCache.readGlobalHotIds("", 20)).thenReturn(List.of(uuid(1), uuid(2)));
    when(postSummaryCache.getAll(List.of(uuid(1), uuid(2)))).thenReturn(Map.of(uuid(1), summary(uuid(1))));
    when(postContentRepository.listPostsByIds(List.of(uuid(2)))).thenReturn(List.of(post(uuid(2))));

    FeedPageResult page = service.listGlobalHotFeed(null, "", 20);

    verify(postSummaryCache).putAll(argThat(items -> items.stream().anyMatch(it -> uuid(2).equals(it.id()))));
    assertThat(page.items()).hasSize(2);
}
```

Add to `PostReadApplicationServiceTest.java`:

```java
@Test
void detailShouldReturnCachedShellBeforeFallingBackToRepositories() {
    when(postDetailCache.get(uuid(100))).thenReturn(detail(uuid(100)));

    PostDetailResult result = service.getPostDetail(null, uuid(100));

    assertThat(result.id()).isEqualTo(uuid(100));
    verifyNoInteractions(postContentRepository);
}
```

- [x] **Step 2: Run the cache tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=FeedReadApplicationServiceTest,PostReadApplicationServiceTest
```

Expected: FAIL because `PostSummaryCache`, `PostDetailCache`, and `postFeedCache` dependencies do not exist.

- [x] **Step 3: Implement Redis-backed summary/detail caches and wire them into read services**

Add `PostSummaryCache`:

```java
public interface PostSummaryCache {
    Map<UUID, PostSummaryResult> getAll(List<UUID> postIds);
    void putAll(List<PostSummaryResult> summaries);
    void evictAll(List<UUID> postIds);
}
```

Add `PostDetailCache`:

```java
public interface PostDetailCache {
    PostDetailResult get(UUID postId);
    void put(UUID postId, PostDetailResult detail);
    void evict(UUID postId);
}
```

In `FeedReadApplicationService`, replace direct summary assembly with:

```java
List<UUID> ids = postFeedCache.readGlobalHotIds(cursor, limit);
Map<UUID, PostSummaryResult> cached = postSummaryCache.getAll(ids);
List<UUID> missing = ids.stream().filter(id -> !cached.containsKey(id)).toList();
if (!missing.isEmpty()) {
    List<PostSummaryResult> loaded = assembleSummaries(postContentRepository.listPostsByIds(missing));
    postSummaryCache.putAll(loaded);
    loaded.forEach(item -> cached.put(item.id(), item));
}
List<PostSummaryResult> ordered = ids.stream().map(cached::get).filter(Objects::nonNull).toList();
```

In `PostReadApplicationService.getPostDetail(...)`, front-load the cache:

```java
PostDetailResult cached = postDetailCache.get(postId);
if (cached != null) {
    return applyViewerOverlay(currentUserId, cached);
}
PostDetailResult loaded = loadPostDetailFromRepositories(currentUserId, postId);
postDetailCache.put(postId, loaded);
return loaded;
```

Use JSON Redis values in `RedisPostSummaryCache` and `RedisPostDetailCache` with keys:

```java
private static final String SUMMARY_KEY = "post:summary:";
private static final String DETAIL_KEY = "post:detail:";
```

- [x] **Step 4: Run the cache tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=FeedReadApplicationServiceTest,PostReadApplicationServiceTest
```

Expected: PASS, with feed summaries backfilled into Redis and post detail shells read through cache.

- [x] **Step 5: Commit the read-through cache layer**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/PostSummaryCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostDetailCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java
git commit -m "feat: add redis summary and detail caches"
```

---

### Task 3: Replace The Legacy Score Queue With Durable Hot Feed Projection

Implementation notes:
- In the current codebase, `PostFeedCache` / `RedisPostFeedCache` do not exist yet. Task 3 therefore creates them and also updates the feed read runtime to consume ordered hot IDs from Redis instead of continuing to page directly against `PostContentRepository.ORDER_HOT`.
- `PostCounterCache` is introduced later by Task 4 and is not part of the current editable surface. Task 3 therefore recomputes hotness from the authoritative post row plus existing synchronous counters (`commentCount` on the post row and `LikeQueryPort`) and leaves counter-cache-backed scoring to the later counter task.
- Redis hot-feed zsets are the primary read path, but Task 3 also keeps a cold-read fallback that loads `ORDER_HOT` pages from `PostContentRepository` and repopulates Redis/summary caches when a requested feed page is absent from Redis.
- When `content.events.publisher` or `social.events.publisher` is configured as `local`, Task 3 keeps an after-commit same-process listener so durable hot-feed projection still runs without Kafka delivery.

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostHotnessDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionLocalListener.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/{PostPublishingApplicationService.java,CommentApplicationService.java,PostModerationApplicationService.java}`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/app/config/DomainServiceConfig.java`
- Delete or replace: `backend/community-app/src/main/java/com/nowcoder/community/content/application/SocialInteractionProjectionApplicationService.java`
- Delete or replace: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/{SocialInteractionBackboneKafkaListener.java,SocialInteractionProjectionListener.java}`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/application/{PostScoreQueue.java,PostScoreRefreshApplicationService.java,PostScoreUpdateApplicationService.java,PostWriteSideEffectScheduler.java}`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/{job/PostScoreRefresher.java,persistence/RedisPostScoreQueue.java}`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionLocalListenerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/{CommentApplicationServiceTest.java,PostPublishingApplicationServiceTest.java}`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostModerationApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/event/EventDeliverySemanticsStructureTest.java`
- Delete or replace: `backend/community-app/src/test/java/com/nowcoder/community/content/event/{SocialInteractionBackboneKafkaListenerTest.java,SocialInteractionProjectionListenerTest.java}`
- Delete or update: `backend/community-app/src/test/java/com/nowcoder/community/content/{application/PostScoreRefreshApplicationServiceTest.java,application/PostWriteSideEffectSchedulerTest.java,score/PostScoreRefresherTest.java,score/RedisPostScoreQueueTest.java}`

**Interfaces:**
- Consumes: content and social durable contract events from Kafka topics `content.events` and `social.events`, `PostFeedCache`, `PostSummaryCache`, `PostDetailCache`, `PostContentRepository`.
- Produces:
  - `PostHotFeedProjectionApplicationService.project(ProjectPostHotFeedCommand command): void`
  - `PostFeedCache.upsertGlobalHot(UUID postId, double score, String rankVersion): void`
  - `PostFeedCache.upsertBoardHot(UUID boardId, UUID postId, double score, String rankVersion): void`

- [x] **Step 1: Write the failing hotness projection tests**

Create `PostHotFeedProjectionApplicationServiceTest.java`:

```java
@Test
void commentCreatedShouldRecomputeHotnessAndUpsertBothFeeds() {
    when(postContentRepository.getByIdAllowDeleted(uuid(200))).thenReturn(post(uuid(200), uuid(10), 12.0));
    when(postCounterCache.get(uuid(200))).thenReturn(counter(150, 8, 3));

    service.project(new ProjectPostHotFeedCommand("evt-1", "COMMENT_CREATED", uuid(200), uuid(10), 1.0));

    verify(postFeedCache).upsertGlobalHot(eq(uuid(200)), anyDouble(), eq("hot-v1"));
    verify(postFeedCache).upsertBoardHot(eq(uuid(10)), eq(uuid(200)), anyDouble(), eq("hot-v1"));
}
```

Create `PostHotFeedProjectionKafkaListenerTest.java`:

```java
@Test
void contentListenerShouldMapPublishedPostEventsToProjectionCommands() {
    listener.onContentEvent(contentEvent("evt-pub", ContentEventTypes.POST_PUBLISHED, postPayload(uuid(200), uuid(10))));
    verify(applicationService).project(argThat(cmd ->
            "evt-pub".equals(cmd.sourceEventId()) &&
            "POST_PUBLISHED".equals(cmd.sourceEventType()) &&
            uuid(200).equals(cmd.postId()) &&
            uuid(10).equals(cmd.boardId())));
}
```

- [x] **Step 2: Run the hotness tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=PostHotFeedProjectionApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest
```

Expected: FAIL because the hot projection service/listener do not exist and the legacy score queue is still the active path.

- [x] **Step 3: Implement durable feed projection and retire the scheduled score queue**

Add `ProjectPostHotFeedCommand`:

```java
public record ProjectPostHotFeedCommand(
        String sourceEventId,
        String sourceEventType,
        UUID postId,
        UUID boardId,
        double signalWeight
) {
}
```

Add `PostHotnessDomainService`:

```java
public double recomputeScore(DiscussPost post, PostCounterSnapshot counters, double signalWeight) {
    double engagement = counters.commentCount() * 10.0
            + counters.likeCount() * 3.0
            + counters.bookmarkCount() * 5.0
            + counters.viewCount() * 0.05
            + signalWeight;
    double agePenalty = post.getCreateTime() == null ? 0.0 : ageDecay(post.getCreateTime().toInstant());
    return Math.max(0.0, engagement - agePenalty + operatorBoost(post));
}
```

Add `PostHotFeedProjectionKafkaListener` that maps durable content/social events into typed commands and calls only `PostHotFeedProjectionApplicationService`.

In `PostHotFeedProjectionApplicationService.project(...)`:

```java
DiscussPost post = postContentRepository.getByIdAllowDeleted(command.postId());
if (post == null || post.getStatus() == 2) {
    postFeedCache.remove(command.postId(), command.boardId());
    postSummaryCache.evictAll(List.of(command.postId()));
    postDetailCache.evict(command.postId());
    return;
}
PostCounterSnapshot counters = postCounterCache.get(command.postId());
double score = postHotnessDomainService.recomputeScore(post, counters, command.signalWeight());
postFeedCache.upsertGlobalHot(post.getId(), score, "hot-v1");
postFeedCache.upsertBoardHot(post.getCategoryId(), post.getId(), score, "hot-v1");
postSummaryCache.evictAll(List.of(post.getId()));
postDetailCache.evict(post.getId());
```

Delete the old score pipeline files and remove `PostWriteSideEffectScheduler` constructor fields/usages from `PostPublishingApplicationService` and `CommentApplicationService`.

- [x] **Step 4: Run the hotness tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=PostHotFeedProjectionApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest,PostPublishingApplicationServiceTest,CommentApplicationServiceTest
```

Expected: PASS, with no remaining references to `PostScoreQueue`, `PostScoreRefresher`, or `PostWriteSideEffectScheduler`.

- [x] **Step 5: Commit the durable hot feed runtime**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostHotnessDomainService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/content/application/PostScoreQueue.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostScoreRefreshApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostScoreUpdateApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostWriteSideEffectScheduler.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostScoreRefresher.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostScoreQueue.java
git commit -m "feat: project hot feed from durable events"
```

---

### Task 4: Add Redis Counters, View Signals, And Snapshot Persistence

**Files:**
- Modify: `deploy/mysql/community/040_schema_content_core.sql`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/RecordPostViewCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostCounterSnapshot.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostCounterSnapshotRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostCounterSnapshotRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostCounterSnapshotMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/post_counter_snapshot_mapper.xml`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostCounterSnapshotFlushJob.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostCounterApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`

**Interfaces:**
- Consumes: `PostDetailCache`, `PostHotFeedProjectionApplicationService`, `LikeQueryPort`, `BookmarkRepository`, request data from `PostController`.
- Produces:
  - `PostCounterApplicationService.recordView(RecordPostViewCommand command): void`
  - `PostCounterApplicationService.read(UUID postId): PostCounterSnapshot`
  - `PostCounterSnapshotRepository.upsert(UUID postId, long viewCount, long likeCount, long commentCount, long bookmarkCount, double score): void`

- [x] **Step 1: Write the failing counter/view tests**

Create `PostCounterApplicationServiceTest.java`:

```java
@Test
void recordViewShouldDeduplicateWithinViewerWindow() {
    RecordPostViewCommand command = new RecordPostViewCommand(uuid(300), "viewer:aaa", Instant.parse("2026-07-06T10:00:00Z"));

    service.recordView(command);
    service.recordView(command);

    verify(postCounterCache, times(1)).incrementViewCount(uuid(300));
}
```

Add to `PostControllerUnitTest.java`:

```java
verify(postCounterApplicationService).recordView(argThat(cmd ->
        sameUuid(postId, cmd.postId()) &&
        cmd.viewerKey().startsWith("auth:")));
```

- [x] **Step 2: Run the counter/view tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=PostCounterApplicationServiceTest,PostControllerUnitTest
```

Expected: FAIL because the counter cache/service and snapshot repository do not exist, and `PostController.detail(...)` does not yet accept the request argument.

- [x] **Step 3: Implement Redis counters, view dedupe, and snapshot tables**

In `deploy/mysql/community/040_schema_content_core.sql`, add:

```sql
create table if not exists post_counter_snapshot (
  post_id binary(16) primary key,
  view_count bigint not null default 0,
  like_count bigint not null default 0,
  comment_count bigint not null default 0,
  bookmark_count bigint not null default 0,
  snapshot_time timestamp null default current_timestamp on update current_timestamp
);

create table if not exists post_score_snapshot (
  post_id binary(16) primary key,
  score double not null default 0,
  rank_version varchar(64) not null,
  snapshot_time timestamp null default current_timestamp on update current_timestamp
);
```

Add `RecordPostViewCommand`:

```java
public record RecordPostViewCommand(
        UUID postId,
        String viewerKey,
        Instant viewedAt
) {
}
```

Add `PostCounterApplicationService.recordView(...)`:

```java
public void recordView(RecordPostViewCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!postCounterCache.markViewerSeen(command.postId(), command.viewerKey(), command.viewedAt())) {
        return;
    }
    postCounterCache.incrementViewCount(command.postId());
}
```

Update `PostController.detail(...)`:

```java
public Result<PostDetailResponse> detail(Authentication authentication,
                                         HttpServletRequest request,
                                         @PathVariable UUID postId) {
    UUID currentUserId = CurrentUser.tryUserUuid(authentication);
    PostDetailResult detail = postReadApplicationService.getPostDetail(currentUserId, postId);
    postCounterApplicationService.recordView(new RecordPostViewCommand(
            postId,
            viewerFingerprint(authentication, request),
            Instant.now()
    ));
    return Result.ok(PostDetailResponse.from(detail));
}
```

Add `PostCounterSnapshotFlushJob` to periodically persist Redis counters and the latest projected score into the new snapshot tables.

- [x] **Step 4: Run the counter/view tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=PostCounterApplicationServiceTest,PostControllerUnitTest,PostReadApplicationServiceTest
```

Expected: PASS, with detail requests recording deduplicated views and detail reads sourcing counters from the counter service.

- [x] **Step 5: Commit the counter pipeline**

```bash
git add deploy/mysql/community/040_schema_content_core.sql \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/RecordPostViewCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostCounterSnapshot.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostCounterSnapshotRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostCounterSnapshotRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCache.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostCounterSnapshotMapper.java \
  backend/community-app/src/main/resources/mapper/post_counter_snapshot_mapper.xml \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostCounterSnapshotFlushJob.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostCounterApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java
git commit -m "feat: add post counter and view snapshot pipeline"
```

---

### Task 5: Rewrite Comments Into Cursor-Based Two-Level Threads End-To-End

**Files:**
- Modify: `deploy/mysql/community/040_schema_content_core.sql`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentPageResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/{CreateCommentRequest.java,CommentResponse.java}`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CommentPageResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/{Comment.java,CommentDraft.java,CommentSnapshot.java,CommentDeletionResult.java}`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/{CommentRepository.java,CommentContentRepository.java}`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/{CommentApplicationService.java,CommentReadApplicationService.java}`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/{MyBatisCommentRepository.java,MyBatisCommentContentRepository.java}`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/comment-mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/{application/CommentApplicationServiceTest.java,application/CommentReadApplicationServiceTest.java,infrastructure/persistence/MyBatisCommentRepositoryTest.java,infrastructure/persistence/mapper/CommentMapperPersistenceTest.java,controller/PostControllerUnitTest.java}`
- Modify: `frontend/src/api/services/postService.js`
- Modify: `frontend/src/api/services/postService.test.js`
- Modify: `frontend/src/views/post-detail/usePostDetailLoader.js`
- Modify: `frontend/src/views/post-detail/PostDetailComments.vue`
- Create: `frontend/src/views/PostDetailView.test.js`

**Interfaces:**
- Consumes: `FeedCursorCodec`, `PostController`, `usePostDetailLoader`.
- Produces:
  - `CreateCommentCommand(UUID userId, UUID postId, UUID parentCommentId, UUID replyToUserId, String content)`
  - `CommentReadApplicationService.listRootComments(UUID postId, String cursor, int size): CommentPageResult`
  - `CommentReadApplicationService.listReplies(UUID postId, UUID rootCommentId, String cursor, int size): CommentPageResult`
  - `postService.listComments(postId, { cursor?: string, size?: number }): Promise<{ data: { items: any[], nextCursor: string }, traceId: string }>`
  - `postService.listReplies(postId, rootCommentId, { cursor?: string, size?: number }): Promise<{ data: { items: any[], nextCursor: string }, traceId: string }>`

- [x] **Step 1: Write the failing backend and frontend comment-thread tests**

Add to `CommentApplicationServiceTest.java`:

```java
@Test
void createReplyShouldPersistParentAndReplyTargetInsteadOfGenericEntityFields() {
    service.create("idem-1", new CreateCommentCommand(uuid(7), uuid(100), uuid(200), uuid(9), "reply"));

    verify(commentRepository).create(argThat(draft ->
            uuid(100).equals(draft.postId()) &&
            uuid(200).equals(draft.parentCommentId()) &&
            uuid(9).equals(draft.replyToUserId())));
}
```

Add to `frontend/src/api/services/postService.test.js`:

```js
it('addComment should send parentCommentId and replyToUserId', async () => {
  mock = new MockAdapter(http)
  mock.onPost('/api/posts/aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa/comments').reply((config) => {
    expect(JSON.parse(config.data)).toEqual({
      content: 'reply',
      parentCommentId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      replyToUserId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
    })
    return [200, { code: 0, message: '', data: { commentId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd' }, traceId: 'trace-comment' }]
  })

  await addComment('aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', {
    content: 'reply',
    parentCommentId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
    replyToUserId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
  })
})
```

- [x] **Step 2: Run the comment-thread tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=CommentApplicationServiceTest,CommentReadApplicationServiceTest,MyBatisCommentRepositoryTest,CommentMapperPersistenceTest,PostControllerUnitTest
cd ../frontend
npm test -- src/api/services/postService.test.js src/views/PostDetailView.test.js
```

Expected: FAIL because the new comment command fields, page responses, and cursor reads do not exist.

- [x] **Step 3: Rewrite the schema, repositories, controller contract, and frontend loaders for two-level comments**

In `deploy/mysql/community/040_schema_content_core.sql`, replace the current `comment` table with:

```sql
create table if not exists comment (
  id binary(16) primary key,
  post_id binary(16) not null,
  user_id binary(16) not null,
  root_comment_id binary(16) not null,
  parent_comment_id binary(16) default null,
  reply_to_user_id binary(16) default null,
  content text,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  key idx_comment_post_parent_time (post_id, parent_comment_id, create_time desc, id desc),
  key idx_comment_root_time (root_comment_id, create_time asc, id asc),
  key idx_comment_user_time (user_id, create_time desc, id desc)
);
```

Change `CreateCommentCommand` to:

```java
public record CreateCommentCommand(
        UUID userId,
        UUID postId,
        UUID parentCommentId,
        UUID replyToUserId,
        String content
) {
}
```

Change `CreateCommentRequest` to:

```java
public class CreateCommentRequest {
    @NotBlank
    private String content;
    private UUID parentCommentId;
    private UUID replyToUserId;
}
```

Add cursor page result:

```java
public record CommentPageResult(
        List<CommentResult> items,
        String nextCursor
) {
}
```

Update `CommentReadApplicationService` signatures:

```java
public CommentPageResult listRootComments(UUID postId, String cursor, Integer size) { ... }

public CommentPageResult listReplies(UUID postId, UUID rootCommentId, String cursor, Integer size) { ... }
```

Update `frontend/src/views/post-detail/usePostDetailLoader.js` to use cursor state:

```js
const commentsCursor = ref('')
const repliesCursorByRoot = reactive({})

async function loadComments({ reset = false } = {}) {
  const resp = await apiListComments(postId.value, { cursor: reset ? '' : commentsCursor.value, size: commentsSize.value })
  commentsCursor.value = String(resp?.data?.nextCursor || '')
  comments.value = reset ? resp.data.items : [...comments.value, ...resp.data.items]
}
```

Update `addComment` calls to send `parentCommentId` and `replyToUserId`, never `entityType/entityId/targetId`.

- [x] **Step 4: Run the comment-thread tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=CommentApplicationServiceTest,CommentReadApplicationServiceTest,MyBatisCommentRepositoryTest,CommentMapperPersistenceTest,PostControllerUnitTest
cd ../frontend
npm test -- src/api/services/postService.test.js src/views/PostDetailView.test.js
```

Expected: PASS with:

- root comments loaded by cursor
- replies loaded by root-comment cursor
- reply creation payloads using `parentCommentId` and `replyToUserId`

- [x] **Step 5: Commit the two-level comment rewrite**

```bash
git add deploy/mysql/community/040_schema_content_core.sql \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentPageResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CreateCommentRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CommentPageResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CommentResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/Comment.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentDraft.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentSnapshot.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentDeletionResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentContentRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentContentRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapper.java \
  backend/community-app/src/main/resources/mapper/comment-mapper.xml \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentReadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentRepositoryTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapperPersistenceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java \
  frontend/src/api/services/postService.js \
  frontend/src/api/services/postService.test.js \
  frontend/src/views/post-detail/usePostDetailLoader.js \
  frontend/src/views/post-detail/PostDetailComments.vue \
  frontend/src/views/PostDetailView.test.js
git commit -m "feat: rewrite comments into two-level cursor threads"
```

---

### Task 6: Switch Notices To Durable Projection And Remove Legacy Feed Entry Points

**Files:**
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`
- Delete or update: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/{NoticeProjectionListenerTest.java,NoticeProjectionListenerStructureTest.java}`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- Modify: `frontend/src/api/services/postService.js`
- Modify: `docs/handbook/business-flows.md`
- Modify: `docs/handbook/data-and-storage.md`

**Interfaces:**
- Consumes: durable `content.events` and `social.events` topics, `NoticeProjectionApplicationService.projectContentEventReliably(...)`, `FeedController`.
- Produces:
  - notice projection only through `NoticeProjectionKafkaListener`
  - no remaining frontend or backend callers of the legacy `GET /api/posts` list route

- [x] **Step 1: Write the failing cleanup and notice projection tests**

Add to `NoticeProjectionKafkaListenerTest.java`:

```java
@Test
void contentKafkaListenerShouldProjectCommentCreatedReliably() {
    listener.onContentEvent(contentEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, commentPayload()));
    verify(applicationService).projectContentEventReliably(any(ProjectContentNoticeCommand.class));
}
```

Add to `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java`:

```java
@Test
void legacyPostsListRouteShouldNoLongerBeHandledByPostController() {
    assertThat(Arrays.stream(PostController.class.getDeclaredMethods())
            .noneMatch(method -> "list".equals(method.getName()))).isTrue();
}
```

- [x] **Step 2: Run the cleanup tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=NoticeProjectionKafkaListenerTest,FeedControllerTest,PublicReadEndpointSecurityTest
```

Expected: FAIL because the local notice listener is still present and `PostController.list(...)` still exists.

- [x] **Step 3: Remove the local notice listener, retire the old `/api/posts` feed entry, and update docs**

Delete `NoticeProjectionListener.java` and its tests.

In `NoticeProjectionKafkaListener`, keep only the durable paths:

```java
@KafkaListener(
        topics = "${content.events.kafka-topic:content.events}",
        groupId = "${notice.kafka.consumer.group-id:notice-projection}",
        concurrency = "${notice.kafka.consumer.concurrency:3}"
)
public void onContentEvent(ContentContractEvent event) {
    if (event == null || !isSupportedContentNoticeEvent(event.type())) {
        return;
    }
    noticeProjectionApplicationService.projectContentEventReliably(new ProjectContentNoticeCommand(
            event.eventId(),
            event.type(),
            normalizeContentPayload(event.type(), event.payload())
    ));
}
```

Delete `PostController.list(...)` and remove the old `listPosts(...)` public-feed path from `PostReadApplicationService` callers. Keep repository fallback methods only where still needed internally for feed fallback, profile pages, or projection scans.

Update `docs/handbook/business-flows.md`:

```markdown
- Public homepage feed now enters `GET /api/feed/global` or `GET /api/boards/{boardId}/feed` and is served from Redis-backed hot feed projection with cursor pagination.
- `GET /api/posts` is no longer the public feed entry.
- Notice projection for content/social interactions is durable and Kafka-backed; the old after-commit best-effort listener is retired.
```

Update `docs/handbook/data-and-storage.md`:

```markdown
- Redis feed keys: `feed:global:hot`, `feed:board:{boardId}:hot`, `post:summary:{postId}`, `post:detail:{postId}`, `post:counter:{postId}`.
- New snapshot tables: `post_counter_snapshot`, `post_score_snapshot`.
```

- [x] **Step 4: Run the regression sweep and verify it passes**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest=FeedControllerTest,FeedReadApplicationServiceTest,PostReadApplicationServiceTest,PostHotFeedProjectionApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest,PostCounterApplicationServiceTest,CommentApplicationServiceTest,CommentReadApplicationServiceTest,MyBatisCommentRepositoryTest,CommentMapperPersistenceTest,NoticeProjectionKafkaListenerTest,PublicReadEndpointSecurityTest,'*ArchTest'
cd ../frontend
npm test -- src/api/services/postService.test.js src/views/PostsView.test.js src/views/PostDetailView.test.js src/components/posts/FeedToolbar.test.js src/router/navigation.test.js
```

Expected:

- backend PASS with no references to the legacy scheduled score queue or local notice listener
- frontend PASS on the new feed/detail/comment contracts
- `*ArchTest` PASS with the new controllers/listeners still entering same-domain application services

- [x] **Step 5: Commit the durable notice path and legacy cleanup**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java \
  frontend/src/api/services/postService.js \
  docs/handbook/business-flows.md \
  docs/handbook/data-and-storage.md
git rm backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListener.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListenerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListenerStructureTest.java
git commit -m "refactor: retire legacy feed and local notice projection"
```

---

## Self-Review

### Spec Coverage

- public cursor-based homepage and board feed: Task 1
- Redis summary/detail shells and hot-read pressure reduction: Task 2
- durable hotness projection and feed ranking: Task 3
- view counting, counter lag, and snapshot persistence: Task 4
- two-level comment model and cursor comment pages: Task 5
- durable notice projection and old feed path retirement: Task 6
- docs, security, and regression verification: Tasks 1 and 6

No spec section is left without an implementing task.

### Placeholder Scan

- no `TODO`, `TBD`, or “implement later” text remains
- every task includes exact file paths, named interfaces, concrete commands, and expected outcomes
- every code step includes named classes or methods rather than generic prose

### Type Consistency

- feed APIs consistently use `FeedPageResult` / `FeedPageResponse` and `listGlobalHotFeed` / `listBoardHotFeed`
- counter APIs consistently use `RecordPostViewCommand` and `PostCounterApplicationService.recordView`
- comment APIs consistently use `parentCommentId` / `replyToUserId` and `CommentPageResult`
- durable hotness projection consistently uses `ProjectPostHotFeedCommand`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-06-large-scale-bbs-content-mainline.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
