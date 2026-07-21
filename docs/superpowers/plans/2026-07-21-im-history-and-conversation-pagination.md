# IM History and Conversation Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增稳定的会话 keyset 页面和向前加载私信历史接口，并让前端默认展示最新消息、可加载完整历史和超过 20 个会话。

**Architecture:** 保留旧 `page/size` 与 `afterSeq` API，新 endpoint 进入同一个 `ConversationApplicationService`。会话 cursor 编码 `(sortAt, conversationId)`，repository 用现有 inbox 索引查询 `size+1`；历史按 `seq desc` 读取 `limit+1` 后在 application 恢复升序。前端使用新接口并按 conversation/message identity 去重。

**Tech Stack:** Java 21、Spring Boot MVC、MyBatis、MySQL 8、H2、JUnit 5、MockMvc、Vue 3、Axios、Vitest、Vite。

## Global Constraints

- 保留 `GET /api/im/conversations?page=&size=` 和 `GET /api/im/conversations/{id}/messages?afterSeq=&limit=` 的响应语义。
- 新接口固定为 `/api/im/conversations/page` 和 `/api/im/conversations/{id}/messages/history`。
- 会话排序为 `sort_at desc, conversation_id asc`；历史响应始终 `seq asc`。
- cursor 非法必须返回参数错误，不能静默退回第一页。
- Controller 只绑定 HTTP、提取认证和转换 DTO；所有用例进入 `ConversationApplicationService`。
- 不新增 IM migration；现有 `(user_id, sort_at, conversation_id)` 和 `(conversation_id, seq)` 索引必须被复用。
- 前端合并必须按 conversation ID、message ID、seq 和 client message identity 去重。

---

### Task 1: Conversation Cursor Codec and Domain Boundary

**Files:**

- Create: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/application/ConversationCursorCodec.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/application/ConversationCursorCodecTest.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/domain/model/ConversationListItem.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/dataobject/ConversationInboxDataObject.java`

**Interfaces:**

- Consumes: `JsonCodec` and a page boundary `Instant sortAt`, `String conversationId`.
- Produces:

  ```java
  String encode(Instant sortAt, String conversationId);
  Optional<ConversationCursorCodec.Cursor> decode(String cursor);

  public record Cursor(int version, Instant sortAt, String conversationId) {}
  ```

  `ConversationListItem` gains `Instant sortAt` as its final component.

- [ ] **Step 1: Write RED codec tests**

  Assert a round trip preserves nanosecond-safe ISO-8601 text and conversation ID. Blank cursor returns `Optional.empty()`. Invalid Base64, malformed JSON, version other than `1`, blank ID and missing time throw `BusinessException(CommonErrorCode.INVALID_ARGUMENT)`.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :im-core -am -Dtest=ConversationCursorCodecTest test
  ```

  Expected: codec does not exist.

- [ ] **Step 3: Implement Base64URL JSON codec**

  Encode a private payload record through `JsonCodec`, then Base64 URL without padding. Decode UTF-8 JSON through `JsonCodec`, validate every field, and translate decode/validation exceptions to the stable invalid-argument business error. Do not expose cursor internals in controller DTOs.

  Add `sortAt` to the inbox data object and map it into the domain list item; old HTTP response mapping continues to omit this internal boundary.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :im-core -am -Dtest=ConversationCursorCodecTest test
  git add backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/application/ConversationCursorCodec.java \
          backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/domain/model/ConversationListItem.java \
          backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/dataobject/ConversationInboxDataObject.java \
          backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/application/ConversationCursorCodecTest.java
  git commit -m "feat(im-core): add conversation page cursor"
  ```

### Task 2: Keyset Conversation Repository and Application Page

**Files:**

- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/domain/repository/UserInboxRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/MyBatisUserInboxRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/UserInboxMapper.java`
- Modify: `backend/community-im/im-core/src/main/resources/mapper/user_inbox_mapper.xml`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/application/result/ConversationResults.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/application/ConversationApplicationService.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/application/ConversationApplicationServiceCursorPaginationTest.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/infrastructure/persistence/ImCoreMySqlMigrationRepositoryContractTest.java`

**Interfaces:**

- Consumes:

  ```java
  List<ConversationListItem> listConversationsBefore(
          UUID userId, Instant beforeSortAt, String afterConversationId, int limit);
  ```

  Both boundary values are null for the first page and non-null together afterward.
- Produces:

  ```java
  ConversationResults.Page listConversationPage(UUID viewerId, String cursor, int size);

  public record Page(List<ListItem> items, String nextCursor, boolean hasMore) {}
  ```

- [ ] **Step 1: Write RED stable-page tests**

  Insert at least four inbox rows, including two with identical `sort_at`. Request size 2, follow `nextCursor`, and assert every conversation appears exactly once in `sort_at desc, conversation_id asc` order. Insert a newer conversation between calls and assert it does not displace or duplicate rows after the existing boundary.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :im-core -am \
    -Dtest='ConversationApplicationServiceCursorPaginationTest,ImCoreMySqlMigrationRepositoryContractTest' test
  ```

  Expected: keyset repository/application methods do not exist.

- [ ] **Step 3: Add keyset query and page assembly**

  Include `sort_at` in `conversationInboxResultMap` and select list. Add query predicate:

  ```xml
  <if test="beforeSortAt != null">
    and (sort_at &lt; #{beforeSortAt}
      or (sort_at = #{beforeSortAt} and conversation_id &gt; #{afterConversationId}))
  </if>
  order by sort_at desc, conversation_id asc
  limit #{limit}
  ```

  Application clamps size to `1..200`, fetches `size+1`, returns only `size`, and encodes the last returned item's `sortAt/conversationId` when `hasMore` is true. Keep legacy offset method unchanged.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :im-core -am \
    -Dtest='ConversationApplicationServiceCursorPaginationTest,ImCoreMySqlMigrationRepositoryContractTest,ConversationApplicationServicePaginationOverflowTest' test
  git add backend/community-im/im-core/src/main backend/community-im/im-core/src/test
  git commit -m "feat(im-core): page conversations with keyset cursor"
  ```

### Task 3: Backward Message History Repository and Application

**Files:**

- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/domain/repository/PrivateMessageRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/MyBatisPrivateMessageRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/PrivateMessageMapper.java`
- Modify: `backend/community-im/im-core/src/main/resources/mapper/private_message_mapper.xml`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/application/result/ConversationResults.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/application/ConversationApplicationService.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/application/ConversationHistoryApplicationServiceTest.java`

**Interfaces:**

- Consumes:

  ```java
  List<PrivateMessageRecord> listBeforeSeq(String conversationId, Long beforeSeqExclusive, int limit);
  ```

- Produces:

  ```java
  ConversationResults.History listMessageHistory(
          UUID viewerId, String conversationId, Long beforeSeq, int limit);

  public record History(
          String conversationId, List<MessageItem> items,
          Long nextBeforeSeq, boolean hasMore, long lastReadSeq) {}
  ```

- [ ] **Step 1: Write RED history tests**

  Store 75 messages. With no `beforeSeq` and limit 50, assert returned seq is `26..75` ascending, `hasMore=true`, `nextBeforeSeq=26`. Request before 26 and assert `1..25`, `hasMore=false`, no duplicate or gap. Also cover nonexistent conversation, membership denial, and invalid non-positive `beforeSeq` as parameter error.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :im-core -am -Dtest=ConversationHistoryApplicationServiceTest test
  ```

  Expected: history use case and descending repository query do not exist.

- [ ] **Step 3: Implement descending probe and ascending response**

  Mapper query:

  ```xml
  where conversation_id = #{conversationId}
  <if test="beforeSeqExclusive != null">
    and seq &lt; #{beforeSeqExclusive}
  </if>
  order by seq desc
  limit #{limit}
  ```

  Application fetches `limit+1`, drops the probe, reverses the retained rows, maps results, and sets `nextBeforeSeq` to the minimum returned seq only when more data exists. Read-state lookup remains same-domain repository collaboration.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :im-core -am -Dtest=ConversationHistoryApplicationServiceTest test
  git add backend/community-im/im-core/src/main backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/application/ConversationHistoryApplicationServiceTest.java
  git commit -m "feat(im-core): load latest private message history"
  ```

### Task 4: Compatible HTTP Endpoints

**Files:**

- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/ConversationController.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/ImCoreApiControllerTest.java`

**Interfaces:**

- Consumes: `listConversationPage` and `listMessageHistory` application methods.
- Produces:

  ```text
  GET /api/im/conversations/page?cursor=&size=
  GET /api/im/conversations/{conversationId}/messages/history?beforeSeq=&limit=
  ```

  Responses contain `items,nextCursor,hasMore` and `conversationId,items,nextBeforeSeq,hasMore,lastReadSeq` respectively.

- [ ] **Step 1: Write RED MockMvc contracts**

  Assert authenticated calls bind optional cursor/beforeSeq, pass current user UUID into application, and expose every response field. Retain existing assertions for old endpoints byte-for-byte at the JSON shape level.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :im-core -am -Dtest=ImCoreApiControllerTest test
  ```

  Expected: both paths return `404`.

- [ ] **Step 3: Add controller-only DTO conversion**

  Add `@GetMapping("/page")` before the `/{conversationId}` route and `@GetMapping("/{conversationId}/messages/history")`. Keep all cursor parsing and membership rules out of the controller.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :im-core -am -Dtest=ImCoreApiControllerTest test
  git add backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/ConversationController.java \
          backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/ImCoreApiControllerTest.java
  git commit -m "feat(im-core): expose cursor and history endpoints"
  ```

### Task 5: Frontend API and Merge Semantics

**Files:**

- Modify: `frontend/src/api/services/imCoreChatService.js`
- Modify: `frontend/src/api/services/imCoreChatService.test.js`
- Modify: `frontend/src/views/conversationDetailState.js`
- Modify: `frontend/src/views/conversationDetailState.test.js`

**Interfaces:**

- Produces:

  ```javascript
  listImConversationPage({ cursor = '', size = 20 })
  listImConversationHistory(conversationId, { beforeSeq, limit = 50 })
  mergeConversations(currentItems, incomingItems)
  mergeConversationMessages(currentItems, incomingItems)
  ```

- [ ] **Step 1: Write RED API and identity tests**

  Assert exact URLs/query params and standard Result envelope unwrapping. Add duplicate inputs sharing seq, message ID, or `clientMsgId`; assert one chronological item remains. Add repeated conversation IDs and assert latest incoming representation replaces the prior item without changing stable list order unexpectedly.

- [ ] **Step 2: Run RED**

  ```bash
  cd frontend
  npm test -- src/api/services/imCoreChatService.test.js src/views/conversationDetailState.test.js
  ```

  Expected: new functions are undefined and client message identity is not retained by mapping.

- [ ] **Step 3: Implement API methods and dedupe keys**

  Preserve `clientMsgId` in `mapConversationMessage`. Merge messages through indexes for positive seq, normalized ID, and nonblank client message ID so a match on any identity replaces the existing item. Keep final sort chronological. Export a conversation merge keyed by normalized `conversationId`.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd frontend
  npm test -- src/api/services/imCoreChatService.test.js src/views/conversationDetailState.test.js
  git add frontend/src/api/services/imCoreChatService.js frontend/src/api/services/imCoreChatService.test.js \
          frontend/src/views/conversationDetailState.js frontend/src/views/conversationDetailState.test.js
  git commit -m "feat(frontend): add IM cursor and history clients"
  ```

### Task 6: Conversation List Loads More Pages

**Files:**

- Modify: `frontend/src/views/ConversationsView.vue`
- Modify: `frontend/src/views/ConversationsView.test.js`

**Interfaces:**

- Consumes: `listImConversationPage` and `mergeConversations` from Task 5.
- Produces: initial refresh resets cursor/items; “加载更多” appends deduplicated rows until `hasMore=false`.

- [ ] **Step 1: Write RED component tests**

  Mock two pages with one repeated conversation. Assert mount calls cursor endpoint, load-more sends first `nextCursor`, renders all unique conversations, disables the control while loading, and removes it when no more pages remain. Assert refresh restarts with empty cursor.

- [ ] **Step 2: Run RED**

  ```bash
  cd frontend
  npm test -- src/views/ConversationsView.test.js
  ```

  Expected: view still calls fixed legacy page 0/size 20 and has no load-more state.

- [ ] **Step 3: Implement page state**

  Track `nextCursor`, `hasMore`, `loadingMore`, and a request generation. Render a clear `UiButton` command after the unframed list; do not introduce nested cards. Initial/refresh load replaces items, load-more merges by conversation ID.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd frontend
  npm test -- src/views/ConversationsView.test.js
  git add frontend/src/views/ConversationsView.vue frontend/src/views/ConversationsView.test.js
  git commit -m "fix(frontend): paginate the IM conversation list"
  ```

### Task 7: Conversation Detail Loads Latest and Prepends History

**Files:**

- Modify: `frontend/src/views/ConversationDetailView.vue`
- Modify: `frontend/src/views/ConversationDetailView.test.js`

**Interfaces:**

- Consumes: `listImConversationHistory`, `mergeConversationMessages`, `nextBeforeSeq`, and realtime append events.
- Produces: latest-first initial load, “加载更早消息” prepend with scroll anchor preservation, and mark-read at the maximum successfully loaded seq.

- [ ] **Step 1: Write RED component tests**

  Assert mount calls history without `beforeSeq`, renders the latest page, marks its max seq read, and scrolls to bottom. On older-page load, mock `scrollHeight` before/after and assert `scrollTop` increases by the height delta so the prior first visible message stays fixed. Emit a realtime duplicate by seq/message/client ID and assert it is not rendered twice.

- [ ] **Step 2: Run RED**

  ```bash
  cd frontend
  npm test -- src/views/ConversationDetailView.test.js
  ```

  Expected: view calls legacy `afterSeq=0`, shows the oldest page, and has no older-history control.

- [ ] **Step 3: Implement history state and anchor restoration**

  Track `nextBeforeSeq`, `hasMoreHistory`, and `loadingHistory`. Before loading older rows capture `chatArea.scrollHeight` and `scrollTop`; after merge and `nextTick`, set:

  ```javascript
  chatArea.value.scrollTop = previousTop + (chatArea.value.scrollHeight - previousHeight)
  ```

  A refresh resets history and scrolls to bottom; realtime messages merge and scroll only for new tail messages, not history prepends.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd frontend
  npm test -- src/views/ConversationDetailView.test.js
  git add frontend/src/views/ConversationDetailView.vue frontend/src/views/ConversationDetailView.test.js
  git commit -m "fix(frontend): show latest IM messages and load history"
  ```

### Task 8: IM Pagination Regression and Build

**Files:**

- Test: all files changed in Tasks 1-7.

**Interfaces:**

- Consumes: both compatible backend contracts and frontend workflows.
- Produces: backend, UI and production build evidence.

- [ ] **Step 1: Run IM backend tests**

  ```bash
  cd backend
  mvn test -pl :im-core -am
  ```

  Expected: `BUILD SUCCESS` including MySQL repository contracts when Docker is available.

- [ ] **Step 2: Run frontend tests and build**

  ```bash
  cd frontend
  npm test
  npm run build
  ```

  Expected: Vitest passes and Vite exits `0`.

- [ ] **Step 3: Inspect endpoint compatibility and diff**

  ```bash
  rg -n '@GetMapping|listConversations|listMessages|listMessageHistory|listConversationPage' \
    backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/ConversationController.java
  git diff --check
  ```

  Expected: old and new endpoints coexist and diff checks are clean.
