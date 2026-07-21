# Content Comment Correctness Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将评论 cursor 改为真正的 keyset pagination，生成不超过 240 Unicode code point 的通知预览，并把评论 Redis 副作用隔离到事务提交后。

**Architecture:** Content application 使用领域专用 `CommentCursorCodec` 验证 scope 并把边界传给 repository；旧 page/size 查询保留 offset 兼容。Notice application 在投影时执行展示预览策略，V014 将通知 JSON 列扩大为 `MEDIUMTEXT`。聚焦的 `CommentCacheAfterCommit` 分别注册 counter 与 page eviction，并在各自回调内隔离 Redis 异常。

**Tech Stack:** Java 21、Spring Boot、Spring Transactions、MyBatis、Redis、Flyway、MySQL 8、H2、Jackson JSON、JUnit 5、Mockito、Testcontainers、Maven。

## Global Constraints

- `backend/community-app` 严格遵守 DDD Tactical Layering；Controller 和 Listener 只调用同域 `*ApplicationService`。
- 新评论 cursor 是 Base64URL JSON，包含 version、kind、postId、reply rootId、boundary createTime 和 commentId。
- 非法、跨 kind、跨 post/root cursor 返回稳定参数错误，不能退回第一页。
- 根评论排序 `create_time desc,id desc`；回复排序 `create_time asc,id asc`。
- Content 的 domain/contract event 继续携带完整合法评论；240 code point 截断只属于 Notice 展示投影。
- Redis 不是 comment count SSOT；MySQL 帖子计数仍在主事务内更新。
- counter 和 page eviction 必须分别 after-commit、分别捕获 `RuntimeException`，且日志不得包含评论正文。
- V014 是 forward-only migration；不得修改 V001-V013 或 schema manifest。

---

### Task 1: Comment Cursor Codec

**Files:**

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentCursorCodec.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentCursorCodecTest.java`

**Interfaces:**

- Consumes: `JsonCodec`, expected kind and request scope.
- Produces:

  ```java
  enum Kind { ROOT, REPLY }

  record Boundary(Instant createTime, UUID commentId) {}

  Optional<Boundary> decodeRoot(String cursor, UUID postId);
  Optional<Boundary> decodeReply(String cursor, UUID postId, UUID rootCommentId);
  String encodeRoot(UUID postId, Instant createTime, UUID commentId);
  String encodeReply(UUID postId, UUID rootCommentId, Instant createTime, UUID commentId);
  ```

- [ ] **Step 1: Write RED round-trip and rejection tests**

  Cover blank cursor, valid ROOT/REPLY round trips, malformed Base64, malformed JSON, version other than `1`, missing fields, invalid UUID/time, ROOT used for replies, REPLY used for root, wrong post, and wrong root. Every invalid nonblank input must throw `BusinessException(CommonErrorCode.INVALID_ARGUMENT)`.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=CommentCursorCodecTest test
  ```

  Expected: codec does not exist.

- [ ] **Step 3: Implement structured Base64URL JSON**

  Serialize a private payload with fields `version`, `kind`, `postId`, `rootCommentId`, `createTime`, `commentId` using `JsonCodec`. Use URL encoder without padding. Decode strictly, validate exact scope, and translate JSON/Base64/value errors to one stable invalid-argument error without including the cursor in logs or messages.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=CommentCursorCodecTest test
  git add backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentCursorCodec.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentCursorCodecTest.java
  git commit -m "feat(content): add scoped comment cursor codec"
  ```

### Task 2: Comment Keyset Repository Queries

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/comment-mapper.xml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/CommentServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapperPersistenceTest.java`

**Interfaces:**

- Consumes:

  ```java
  List<Comment> listRootCommentsAfter(
          UUID postId, Date boundaryTime, UUID boundaryId, int limit);
  List<Comment> listRepliesAfter(
          UUID rootCommentId, Date boundaryTime, UUID boundaryId, int limit);
  ```

  Boundary time/ID are both null for the first page and non-null together afterward.
- Produces: ordered keyset rows with no offset argument; existing offset methods remain for legacy page/size callers.

- [ ] **Step 1: Write RED persistence tests**

  Insert root comments and replies sharing the same millisecond timestamp. For roots assert descending UUID tie-break order and predicate `<`; for replies assert ascending UUID order and predicate `>`. Delete one row between page calls and insert a row before the first page boundary; assert all remaining rows after the boundary appear once without gaps.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='CommentServiceTest,CommentMapperPersistenceTest' test
  ```

  Expected: keyset repository/mapper methods do not exist.

- [ ] **Step 3: Add keyset mapper statements**

  Root predicate:

  ```xml
  <if test="boundaryTime != null">
    and (create_time &lt; #{boundaryTime}
      or (create_time = #{boundaryTime} and id &lt; #{boundaryId, jdbcType=BINARY}))
  </if>
  order by create_time desc, id desc
  limit #{limit}
  ```

  Reply predicate uses `>` for both comparisons and `order by create_time asc,id asc`. Repository validates boundary pairs and clamps fetch limit to `1..51`.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='CommentServiceTest,CommentMapperPersistenceTest' test
  git add backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentContentRepository.java \
          backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentContentRepository.java \
          backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapper.java \
          backend/community-app/src/main/resources/mapper/comment-mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/CommentServiceTest.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapperPersistenceTest.java
  git commit -m "fix(content): query comment pages by keyset"
  ```

### Task 3: Comment Read Application Uses Keyset Cursor

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentReadApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentReadApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`

**Interfaces:**

- Consumes: `CommentCursorCodec` and keyset repository methods from Tasks 1-2.
- Produces: cursor HTTP use cases fetch `size+1` and encode the last returned row; `comments(page,size)` and `replies(page,size)` still call legacy offset repository methods directly.

- [ ] **Step 1: Write RED application scope/probe tests**

  Assert initial root/reply calls pass null boundaries and `size+1`; next calls decode the exact last returned comment time/ID. Add invalid/cross-scope cursor tests and assert repository is never invoked. Assert legacy `comments`/`replies` still use page and size rather than converting to a new cursor.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='CommentReadApplicationServiceTest,PostControllerUnitTest' test
  ```

  Expected: current application decodes page/size and issues offset queries.

- [ ] **Step 3: Assemble keyset results**

  Remove `FeedCursorCodec` from the cursor use-case path and inject `CommentCursorCodec`. For a nonempty page with a probe row, encode the boundary from the final returned `Comment`:

  ```java
  Comment boundary = pageRows.get(pageRows.size() - 1);
  String next = root
          ? cursorCodec.encodeRoot(postId, boundary.getCreateTime().toInstant(), boundary.getId())
          : cursorCodec.encodeReply(postId, rootId, boundary.getCreateTime().toInstant(), boundary.getId());
  ```

  Keep first-page cache reads/writes only when the raw cursor is blank.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='CommentReadApplicationServiceTest,PostControllerUnitTest' test
  git add backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentReadApplicationService.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentReadApplicationServiceTest.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java
  git commit -m "fix(content): paginate comments with scoped keysets"
  ```

### Task 4: Invalidate Legacy Comment Page Cache Namespace

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCacheTest.java`

**Interfaces:**

- Consumes: only blank-cursor first-page calls from `CommentReadApplicationService`.
- Produces: key prefix `comment:root-page:v3:`; no read or eviction references `v2` keys.

- [ ] **Step 1: Write RED namespace test**

  Seed a serialized page at the old v2 key and assert `getRootPage` returns null. Store a new page, capture Redis operations, and assert only v3 page/index keys are written and evicted.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=RedisCommentPageCacheTest test
  ```

  Expected: current cache reads v2.

- [ ] **Step 3: Change the constant to v3**

  Replace only the namespace constant; do not scan/delete all old Redis keys during deployment.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=RedisCommentPageCacheTest test
  git add backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCacheTest.java
  git commit -m "chore(content): version comment page cache keys"
  ```

### Task 5: Community V014 Notice Content Capacity

**Files:**

- Create: `backend/community-db-migrations/src/main/resources/db/migration/community/V014__widen_notice_content.sql`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationLayoutTest.java`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`

**Interfaces:**

- Consumes: nullable existing `notice_record.content varchar(4000)` values.
- Produces: nullable `MEDIUMTEXT` with all existing content preserved.

- [ ] **Step 1: Add RED migration assertions**

  Raise migration count/latest version to 14. Insert null and 4,000-character notice payloads before upgrade; assert both survive. Assert MySQL reports `mediumtext` and nullable `YES`.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest='CommunityMigrationLayoutTest,CommunityMigrationTest' test
  ```

  Expected: V014 is missing and type remains varchar.

- [ ] **Step 3: Add V014 and H2 fixture update**

  ```sql
  alter table notice_record
    modify column content mediumtext null;
  ```

  Change the H2 test schema content column to `clob` so application persistence tests have no 4,000-character cap.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest='CommunityMigrationLayoutTest,CommunityMigrationTest' test
  git add backend/community-db-migrations/src/main/resources/db/migration/community/V014__widen_notice_content.sql \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationLayoutTest.java \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java \
          backend/community-app/src/test/resources/schema.sql
  git commit -m "feat(migration): widen notice content storage"
  ```

### Task 6: Notice Comment Preview by Unicode Code Point

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/persistence/NoticeMapperPersistenceTest.java`

**Interfaces:**

- Consumes: full `ProjectNoticeCommand.CommentCreated.content()`.
- Produces: `NoticeProjectionContent.Comment.content` containing at most 240 Unicode code points, never a split surrogate pair.

- [ ] **Step 1: Write RED escaping and Unicode tests**

  Project comments containing 2,000 quotes, 2,000 backslashes, and more than 240 supplementary-plane emoji. Parse the produced JSON and assert `content.codePointCount(0, content.length()) <= 240`; emoji output ends on a valid code point. Assert the Content command itself remains unmodified/full. Persist the generated JSON through `NoticeMapper`.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='NoticeProjectionApplicationServiceTest,NoticeMapperPersistenceTest' test
  ```

  Expected: projection serializes the full comment and the old H2 varchar may reject expanded JSON.

- [ ] **Step 3: Apply preview only in Notice projection**

  Add:

  ```java
  private static String commentPreview(String content) {
      String value = content == null ? "" : content;
      int count = value.codePointCount(0, value.length());
      if (count <= 240) return value;
      return value.substring(0, value.offsetByCodePoints(0, 240));
  }
  ```

  Use it only when constructing `NoticeProjectionContent.Comment`; preserve field name `content` and all other payload fields.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='NoticeProjectionApplicationServiceTest,NoticeMapperPersistenceTest' test
  git add backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java \
          backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java \
          backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/persistence/NoticeMapperPersistenceTest.java
  git commit -m "fix(notice): bound comment previews by code point"
  ```

### Task 7: Best-Effort Comment Cache After Commit

**Files:**

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentCacheAfterCommit.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentCacheAfterCommitTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentDeletionCardinalityContractTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentCreateConcurrencySpringTest.java`

**Interfaces:**

- Consumes: `PostCounterCache`, `CommentPageCache`, `AfterCommitExecutor`.
- Produces:

  ```java
  void incrementCommentCount(UUID postId, long delta);
  void evictCommentPages(UUID postId);
  ```

  Each method registers one independent after-commit callback that catches its own `RuntimeException`.

- [ ] **Step 1: Write RED synchronization and failure-isolation tests**

  Activate `TransactionSynchronizationManager`, register counter and eviction, and assert no cache call occurs before callbacks. Simulate rollback by clearing synchronization without `afterCommit`; assert neither cache is called. On commit, make counter throw and assert eviction still runs with no exception escaping. Reverse failures and assert the same. Verify logs/arguments contain only operation, post ID and delta.

  Update service tests so create/delete verify MySQL `incrementCommentCount` inside the use case but Redis only after commit. Use a real idempotency replay and assert only one counter callback is registered/executed.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='CommentCacheAfterCommitTest,CommentApplicationServiceTest,CommentDeletionCardinalityContractTest,CommentCreateConcurrencySpringTest' test
  ```

  Expected: Redis counter currently executes synchronously inside the transaction and propagates failures.

- [ ] **Step 3: Implement focused after-commit wrapper**

  Each callback uses:

  ```java
  AfterCommitExecutor.runAfterCommit(() -> {
      try {
          action.run();
      } catch (RuntimeException ex) {
          log.warn("[comment-cache] operation={} postId={} delta={} failed",
                  operation, postId, delta, ex);
      }
  });
  ```

  Register create counter `+1` and page eviction inside the idempotency supplier after authoritative DB/event work, so replay registers neither. Delete uses `-result.deletedCount()` and registers both only for `APPLIED`. Update registers page eviction only for `APPLIED`. Remove direct `PostCounterCache`/`CommentPageCache` fields from `CommentApplicationService` and inject `CommentCacheAfterCommit`.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am \
    -Dtest='CommentCacheAfterCommitTest,CommentApplicationServiceTest,CommentDeletionCardinalityContractTest,CommentCreateConcurrencySpringTest' test
  git add backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentCacheAfterCommit.java \
          backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentCacheAfterCommitTest.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentDeletionCardinalityContractTest.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentCreateConcurrencySpringTest.java
  git commit -m "fix(content): update comment caches after commit"
  ```

### Task 8: Content, Notice, Migration and Architecture Regression

**Files:**

- Test: all files changed in Tasks 1-7.

**Interfaces:**

- Consumes: comment keysets, v3 cache, V014, bounded notice projection and after-commit isolation.
- Produces: evidence for all four content/notice correctness invariants.

- [ ] **Step 1: Run focused suites**

  ```bash
  cd backend
  mvn test -pl :community-db-migrations,:community-app -am \
    -Dtest='CommunityMigration*Test,Comment*Test,Notice*Test,RedisCommentPageCacheTest'
  ```

  Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run architecture guardrails**

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  Expected: every architecture test passes.

- [ ] **Step 3: Scan forbidden regressions and diff**

  ```bash
  rg -n 'FeedCursorCodec|incrementCommentCount\(postId, [+-]?[a-zA-Z0-9.]+\)' \
    backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentReadApplicationService.java \
    backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java
  git diff --check
  ```

  Expected: no Feed cursor in comment cursor paths, no direct Redis counter call in `CommentApplicationService`, and no diff errors.

- [ ] **Step 4: Run final combined project acceptance**

  ```bash
  cd backend
  mvn test -pl :community-common-security,:community-db-migrations,:community-im-db-migrations,:community-app,:community-im-gateway,:im-core,:im-realtime -am
  mvn test -pl :community-app -Dtest='*ArchTest'
  cd ../frontend
  npm test
  npm run build
  ```

  Expected: all commands exit `0`.
