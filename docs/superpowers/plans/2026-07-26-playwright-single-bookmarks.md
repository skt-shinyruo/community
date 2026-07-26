# Playwright Single 收藏链路 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 让已登录用户的收藏列表接口返回 200，并在社区 Playwright 流程中验证收藏后的帖子摘要真实可见。

**Architecture:** BookmarkController 只提取用户和分页参数；BookmarkApplicationService 通过 BookmarkRepository 读取帖子，再用内容、标签和活动仓储组装 PostSummaryResult；MyBatis SQL 只引用当前 schema 中存在的 discuss_post 字段。数据库异常继续向上失败，不在 HTTP adapter 中转换为空列表。

**Tech Stack:** Spring Boot、MyBatis、H2 test profile、JUnit 5、Mockito、Playwright Test。

## Global Constraints

- 后端业务调用保持 Controller -> BookmarkApplicationService -> BookmarkRepository/内容仓储 -> infrastructure mapper。
- 不向 discuss_post SQL 添加不存在的 content 列；正文预览来自 PostContentBlockRepository 和 PostContentBlockTextProjector。
- 不在 BookmarkController 捕获 DataAccessException，也不把 5xx 转为 200 空数组。
- 保留用户对 tests/playwright-single/tests/02-community.spec.ts 的现有 locator 和其他未提交修改。
- Playwright 收藏断言只接受 HTTP 200 和真实帖子标题；不得保留 expected 503 语义。

---

### Task 1: Add the Failing Real Mapper Projection Test

**Files:**
- Create: backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/BookmarkMapperPersistenceTest.java
- Read: deploy/mysql/community/040_schema_content_core.sql
- Read: backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/BookmarkMapper.java

- [ ] **Step 1: Confirm the schema mismatch**

Run:

~~~bash
rg -n "create table if not exists discuss_post|content |deleted_by|post_bookmark" deploy/mysql/community/040_schema_content_core.sql
rg -n "p\.content|selectBookmarkedPosts" backend/community-app/src/main/resources/mapper/bookmark-mapper.xml
~~~

Expected: discuss_post contains title and deletion/counter fields but no content column, while bookmark-mapper.xml selects p.content.

- [ ] **Step 2: Write the red persistence test**

Create a Spring Boot mapper test using the existing test profile:

~~~java
@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class BookmarkMapperPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookmarkMapper bookmarkMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void cleanRows() {
        jdbcTemplate.update("delete from post_bookmark");
        jdbcTemplate.update("delete from discuss_post");
    }

    @Test
    void selectBookmarkedPostsShouldProjectCurrentDiscussPostSchema() {
        UUID userId = uuid(701);
        UUID postId = uuid(702);
        Date createdAt = Date.from(Instant.parse("2026-07-26T00:00:00Z"));
        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, title, type, status, create_time, comment_count, score) values (?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(postId), BinaryUuidCodec.toBytes(userId),
                "bookmark projection", 0, 0, createdAt, 0, 0.0
        );
        jdbcTemplate.update(
                "insert into post_bookmark(user_id, post_id, create_time) values (?, ?, ?)",
                BinaryUuidCodec.toBytes(userId), BinaryUuidCodec.toBytes(postId), createdAt
        );

        List<DiscussPost> rows = bookmarkMapper.selectBookmarkedPosts(userId, 0, 10);

        assertThat(rows).singleElement().satisfies(post -> {
            assertThat(post.getId()).isEqualTo(postId);
            assertThat(post.getTitle()).isEqualTo("bookmark projection");
            assertThat(post.getStatus()).isZero();
        });
    }
}
~~~

Use the same imports and UUID helper as DiscussPostMapperPersistenceTest. Run:

~~~bash
cd backend
mvn -pl :community-app -Dtest=BookmarkMapperPersistenceTest test
~~~

Expected before the implementation: FAIL with an H2/MySQL-compatible unknown-column error for p.content.

### Task 2: Fix the SQL Projection Without Changing the Domain Contract

**Files:**
- Modify: backend/community-app/src/main/resources/mapper/bookmark-mapper.xml
- Modify: backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/BookmarkServiceTest.java

- [ ] **Step 1: Remove only the invalid column**

Delete this line from the selectBookmarkedPosts projection:

~~~sql
p.content as content,
~~~

Keep these persisted columns because they exist in 040_schema_content_core.sql and are mapped by DiscussPost:

~~~sql
p.id, p.user_id, p.category_id, p.title, p.type, p.status,
p.create_time, p.update_time, p.edit_count,
p.deleted_by, p.deleted_reason, p.deleted_time,
p.comment_count, p.score
~~~

Keep the join, active-post filter, bookmark creation-time ordering, and Pagination.safeOffset-derived limit parameters unchanged.

- [ ] **Step 2: Extend repository-level behavior**

In BookmarkServiceTest, retain the existing mapper delegation test and add a boundary case proving page 2 with size 10 calls selectBookmarkedPosts(userId, 20, 10). Do not make the repository depend on mapper/dataobject types outside infrastructure.

~~~java
@Test
void listBookmarkedPostsShouldConvertPageToSafeOffset() {
    when(bookmarkMapper.selectBookmarkedPosts(userId, 20, 10)).thenReturn(List.of());

    assertThat(service.listBookmarkedPosts(userId, 2, 10)).isEmpty();

    verify(bookmarkMapper).selectBookmarkedPosts(userId, 20, 10);
}
~~~

- [ ] **Step 3: Run the persistence and repository tests**

~~~bash
cd backend
mvn -pl :community-app -Dtest=BookmarkMapperPersistenceTest,BookmarkServiceTest test
~~~

Expected: the SQL executes, the active row is returned, and the page offset is 20.

### Task 3: Lock Application Summary Assembly and Empty Optional Projections

**Files:**
- Modify: backend/community-app/src/test/java/com/nowcoder/community/content/application/BookmarkApplicationServiceTest.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/content/application/BookmarkApplicationService.java
- Read: backend/community-app/src/main/java/com/nowcoder/community/content/application/PostSummaryAssembler.java

- [ ] **Step 1: Add the empty-list application test**

Add a test with bookmarkRepository.listBookmarkedPosts(userId, 0, 10) returning List.of(), assert List.of(), and verifyNoInteractions on commentContentRepository, tagContentRepository, blockRepository, and postSummaryAssembler. This protects the no-post path from issuing invalid optional projection queries.

~~~java
@Test
void listBookmarkedPostSummariesShouldReturnEmptyWithoutOptionalProjectionQueries() {
    when(bookmarkRepository.listBookmarkedPosts(userId, 0, 10)).thenReturn(List.of());

    assertThat(service.listBookmarkedPostSummaries(userId, 0, 10)).isEmpty();

    verifyNoInteractions(commentContentRepository, tagContentRepository,
            blockRepository, postSummaryAssembler);
}
~~~

- [ ] **Step 2: Confirm the populated assembly contract**

Keep the existing test assertion that the post title, tags, latest activity, and post block preview reach PostSummaryAssembler. Optional maps must be read with get(postId) and the assembler must accept null/empty optional values; do not move this assembly into BookmarkController.

- [ ] **Step 3: Run the application and controller tests**

~~~bash
cd backend
mvn -pl :community-app -Dtest=BookmarkApplicationServiceTest,BookmarkControllerTest,BookmarkMapperPersistenceTest,BookmarkServiceTest test
~~~

Expected: controller delegates to BookmarkApplicationService, application returns assembled summaries, empty lists avoid optional repositories, and mapper SQL passes against the test schema.

### Task 4: Put Successful Bookmark Verification in Community E2E

**Files:**
- Modify: tests/playwright-single/tests/02-community.spec.ts
- Read: tests/playwright-single/fixtures/test-data.ts
- Read: frontend/src/views/BookmarksView.vue

- [ ] **Step 1: Add the red E2E assertion before relying on the fix**

Immediately after the existing bookmark button click and before leaving the post flow, add a response assertion:

~~~ts
const bookmarkListResponse = page.waitForResponse((response) => {
  const url = new URL(response.url())
  return response.request().method() === 'GET'
    && url.pathname === '/api/bookmarks'
    && url.searchParams.get('page') === '0'
    && url.searchParams.get('size') === '10'
})
await gotoHash(page, '/bookmarks')
const response = await bookmarkListResponse
expect(response.status()).toBe(200)
const body = await response.json()
expect(body.data).toEqual(expect.arrayContaining([
  expect.objectContaining({ title: expect.stringContaining(data.postTitle) })
]))
await expect(page.getByText(data.postTitle).first()).toBeVisible()
~~~

The exact final placement must preserve the existing user locator:

~~~ts
await page.locator('.posts-feed-compose-strip').click()
~~~

- [ ] **Step 2: Run the focused E2E**

~~~bash
npm --prefix tests/playwright-single run test:regression -- tests/02-community.spec.ts
~~~

Expected before the backend fix: the response assertion fails on HTTP 503 or the mapper error is visible in the audit attachment.

- [ ] **Step 3: Run the fixed E2E and inspect artifacts**

~~~bash
npm --prefix tests/playwright-single run test:regression -- tests/02-community.spec.ts
test ! -e tests/playwright-single/test-results/single-error-audit.json
~~~

Expected: the post title is visible in /bookmarks, the API status is 200, and no page/API error audit attachment exists.

### Task 5: Verify the Slice and Preserve Architecture Guardrails

**Files:**
- Verify: backend/community-app/src/main/java/com/nowcoder/community/content/controller/BookmarkController.java
- Verify: backend/community-app/src/main/java/com/nowcoder/community/content/application/BookmarkApplicationService.java
- Verify: backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/BookmarkRepository.java
- Verify: backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisBookmarkRepository.java
- Verify: backend/community-app/src/main/resources/mapper/bookmark-mapper.xml

- [ ] **Step 1: Run all bookmark checks**

~~~bash
cd backend
mvn -pl :community-app -Dtest=BookmarkApplicationServiceTest,BookmarkControllerTest,BookmarkServiceTest,BookmarkMapperPersistenceTest test
mvn test -pl :community-app -Dtest='*ArchTest'
~~~

- [ ] **Step 2: Scan for forbidden masking**

~~~bash
rg -n "bookmarks.*503|toBe\(503\)|catch.*DataAccessException|DataAccessException" tests/playwright-single backend/community-app/src/main/java/com/nowcoder/community/content
~~~

Expected: no expected-503 Playwright assertion and no Controller-level persistence exception swallowing.

- [ ] **Step 3: Run the final community command**

~~~bash
cd ..
npm --prefix tests/playwright-single run test:regression -- tests/02-community.spec.ts
~~~

Expected: community creation, like, bookmark, comment, bookmark list, and the remaining page checks all pass in one run-scoped flow.
