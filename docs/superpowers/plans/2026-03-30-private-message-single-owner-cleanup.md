# Private Message Single-Owner Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the legacy `/api/messages/**` private-message system so `community-im` is the only private-message owner, while keeping notice delivery and IM governance intact.

**Architecture:** Cut the legacy frontend and backend private-message entry points first, then shrink `community-app` down to two surviving responsibilities: notice projection and IM governance. After the old code is gone, move notice behavior into a `notice` namespace and move private-message governance into an `im.governance` namespace so package boundaries match the intended ownership model.

**Tech Stack:** Vue 3 + Vite + Vitest, Spring Boot 3.2, Spring Security, MyBatis, JUnit 5, Mockito, ArchUnit, Maven

---

## File Map

### Frontend files

- Delete: `frontend/src/api/services/messageService.js`
  - Remove the unused legacy `/api/messages/**` client.
- Modify: `frontend/src/api/http.js`
  - Remove the `/api/messages` `Idempotency-Key` branch so only posts/comments keep HTTP idempotency.
- Modify: `frontend/src/api/http.test.js`
  - Add a regression test that proves the removed legacy endpoint no longer receives automatic idempotency headers.

### Backend legacy private-message files

- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/controller/MessageController.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/ListConversationItemsQuery.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/ListLettersQuery.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/SendPrivateMessageUseCase.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/MarkMessagesReadUseCase.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/MessageRecipientResolver.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/security/OwnerGuard.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/security/ConversationIdParser.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/exception/MessageErrorCode.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/ConversationItemResponse.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/SendMessageRequest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/UserSummaryResponse.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/service/dto/ConversationStats.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/mapper/MessageMapper.java`
  - Temporary intermediate step: prune all private-message methods so only notice persistence/query methods remain.
- Modify: `backend/community-app/src/main/resources/mapper/message_mapper.xml`
  - Temporary intermediate step: prune all letter/conversation SQL so only notice SQL remains.

### Backend notice-boundary files

- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeItemAssembler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/mapper/NoticeMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java`
- Create: `backend/community-app/src/main/resources/mapper/notice_mapper.xml`
- Delete: the old `com.nowcoder.community.message.*` notice controller/service/event/mapper classes after the new namespace is wired.

### Backend governance-boundary files

- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/UserModerationGuard.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/action/PrivateMessageGovernanceActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java`
  - Repoint imports to the new governance package.
- Delete: the old `com.nowcoder.community.message.*` governance classes after the new package compiles.

### Tests and docs

- Create: `backend/community-app/src/test/java/com/nowcoder/community/message/controller/LegacyMessageApiRemovalTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/notice/NoticeModuleArchTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/im/architecture/PrivateMessageOwnershipArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java`
- Modify: existing notice/governance tests to import the new packages.
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `docs/SECURITY.md`
- Modify: `docs/business-logic/im-private-message-flow.md`

## Task 1: Remove the frontend legacy private-message client

**Files:**
- Delete: `frontend/src/api/services/messageService.js`
- Modify: `frontend/src/api/http.js`
- Test: `frontend/src/api/http.test.js`

- [ ] **Step 1: Write the failing frontend regression test**

Add this test to `frontend/src/api/http.test.js`:

```js
it('should not attach Idempotency-Key to removed legacy private message endpoint', async () => {
  const mock = new MockAdapter(http)
  mock.onPost('/api/messages').reply((config) => {
    return [200, { idem: config.headers?.['Idempotency-Key'] || '' }]
  })

  const resp = await http.post('/api/messages', { toId: 9, content: 'hello' })

  expect(resp.data.idem).toBe('')
  mock.restore()
})
```

- [ ] **Step 2: Run the frontend test and verify it fails**

Run:

```bash
npm --prefix frontend test -- src/api/http.test.js
```

Expected: FAIL because `frontend/src/api/http.js` still adds an `Idempotency-Key` for `POST /api/messages`.

- [ ] **Step 3: Remove the legacy client and HTTP idempotency branch**

Delete `frontend/src/api/services/messageService.js`.

Update `frontend/src/api/http.js` so `shouldAttachIdempotencyKey` becomes:

```js
function shouldAttachIdempotencyKey(config) {
  const method = String(config?.method || '').toLowerCase()
  const url = String(config?.url || '')
  if (method !== 'post') return false

  if (url === '/api/posts') return true
  if (/^\/api\/posts\/[^/]+\/comments$/.test(url)) return true

  return false
}
```

- [ ] **Step 4: Run focused verification**

Run:

```bash
npm --prefix frontend test -- src/api/http.test.js src/api/imCoreHttp.test.js
rg -n "/api/messages|messageService" frontend/src
```

Expected:

- Vitest PASS for both files
- `rg` prints no matches in `frontend/src`

- [ ] **Step 5: Commit the frontend hard cut**

Run:

```bash
git add frontend/src/api/http.js frontend/src/api/http.test.js frontend/src/api/services/messageService.js
git commit -m "refactor: remove legacy private message frontend client"
```

## Task 2: Delete the legacy backend private-message HTTP surface

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/message/controller/LegacyMessageApiRemovalTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/controller/MessageController.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/ListConversationItemsQuery.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/ListLettersQuery.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/SendPrivateMessageUseCase.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/MarkMessagesReadUseCase.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/app/MessageRecipientResolver.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/security/OwnerGuard.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/security/ConversationIdParser.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/exception/MessageErrorCode.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/ConversationItemResponse.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/SendMessageRequest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/UserSummaryResponse.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/service/dto/ConversationStats.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/mapper/MessageMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/message_mapper.xml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java`
- Delete: private-message-only tests under `backend/community-app/src/test/java/com/nowcoder/community/message/`

- [ ] **Step 1: Add a failing backend regression test for route removal**

Create `backend/community-app/src/test/java/com/nowcoder/community/message/controller/LegacyMessageApiRemovalTest.java`:

```java
package com.nowcoder.community.message.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyMessageApiRemovalTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void legacyPrivateMessageRoutesShouldBeGone() throws Exception {
        mockMvc.perform(get("/api/messages/conversations").with(jwt().jwt(jwt -> jwt.subject("7"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/messages")
                        .with(jwt().jwt(jwt -> jwt.subject("7")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toId": 9,
                                  "content": "hello"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the backend regression test and verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -Dtest=LegacyMessageApiRemovalTest test
```

Expected: FAIL because `/api/messages/**` is still mapped by `MessageController`.

- [ ] **Step 3: Delete the legacy HTTP stack and prune the mapper**

Delete every legacy private-message class listed in this task’s file list.

Replace `backend/community-app/src/main/java/com/nowcoder/community/message/mapper/MessageMapper.java` with the notice-only interface below:

```java
package com.nowcoder.community.message.mapper;

import com.nowcoder.community.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface MessageMapper {

    int insertMessage(Message message);

    List<Message> selectNotices(@Param("userId") int userId, @Param("topic") String topic, @Param("offset") int offset, @Param("limit") int limit);

    int selectNoticeCount(@Param("userId") int userId, @Param("topic") String topic);

    int selectNoticeUnreadCount(@Param("userId") int userId, @Param("topic") String topic);

    int updateNoticesStatusForRecipient(@Param("ids") List<Integer> ids, @Param("status") int status, @Param("userId") int userId);
}
```

Reduce `backend/community-app/src/main/resources/mapper/message_mapper.xml` to the surviving notice SQL only:

```xml
<mapper namespace="com.nowcoder.community.message.mapper.MessageMapper">
    <sql id="selectFields">
        id, from_id, to_id, conversation_id, content, status, create_time
    </sql>

    <sql id="insertFields">
        from_id, to_id, conversation_id, content, status, create_time
    </sql>

    <sql id="noticePredicate">
        (
            from_id = 0
            or (
                from_id = 1
                and conversation_id in ('comment', 'like', 'follow', 'moderation')
            )
        )
    </sql>

    <insert id="insertMessage" parameterType="com.nowcoder.community.message.entity.Message" keyProperty="id">
        insert into message(<include refid="insertFields"></include>)
        values(#{fromId},#{toId},#{conversationId},#{content},#{status},#{createTime})
    </insert>

    <select id="selectNotices" resultType="com.nowcoder.community.message.entity.Message">
        select <include refid="selectFields"></include>
        from message
        where status != 2
          and <include refid="noticePredicate"/>
          and to_id = #{userId}
          and conversation_id = #{topic}
        order by create_time desc
        limit #{offset}, #{limit}
    </select>

    <select id="selectNoticeCount" resultType="int">
        select count(id) from message
        where status != 2
          and <include refid="noticePredicate"/>
          and to_id = #{userId}
          and conversation_id = #{topic}
    </select>

    <select id="selectNoticeUnreadCount" resultType="int">
        select count(id) from message
        where status = 0
          and <include refid="noticePredicate"/>
          and to_id = #{userId}
          <if test="topic!=null">
              and conversation_id = #{topic}
          </if>
    </select>

    <update id="updateNoticesStatusForRecipient">
        update message set status = #{status}
        where <include refid="noticePredicate"/>
          and to_id = #{userId}
          and id in
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </update>
</mapper>
```

Delete the obsolete `privateMessageServiceShouldNotPassNegativeOffsetWhenPageIsHuge` test method from `backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java`.

- [ ] **Step 4: Run backend verification after the delete**

Run:

```bash
mvn -f backend/pom.xml -pl community-app test
rg -n "/api/messages" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected:

- Maven PASS for `community-app`
- `rg` only finds the new removal test name or historical docs, not a mapped controller

- [ ] **Step 5: Commit the backend private-message removal**

Run:

```bash
git add backend/community-app/src/main/java backend/community-app/src/main/resources/mapper/message_mapper.xml backend/community-app/src/test/java
git commit -m "refactor: remove legacy private message backend api"
```

## Task 3: Move surviving notice behavior into a dedicated notice namespace

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/notice/NoticeModuleArchTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/mapper/NoticeMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/notice_mapper.xml`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeItemAssembler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java`
- Delete: the old `com.nowcoder.community.message.*` notice controller/service/event classes
- Modify: existing notice tests to import the new package names

- [ ] **Step 1: Add a failing architecture test that bans notice owners from the `message` namespace**

Create `backend/community-app/src/test/java/com/nowcoder/community/notice/NoticeModuleArchTest.java`:

```java
package com.nowcoder.community.notice;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class NoticeModuleArchTest {

    @Test
    void messageNamespaceShouldNotOwnNoticeBehavior() {
        var classes = new ClassFileImporter().importPackages("com.nowcoder.community");

        noClasses()
                .that().resideInAnyPackage(
                        "..message.controller..",
                        "..message.service..",
                        "..message.event..",
                        "..message.mapper.."
                )
                .should().haveSimpleNameStartingWith("Notice")
                .check(classes);
    }
}
```

- [ ] **Step 2: Run the architecture test and verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -Dtest=NoticeModuleArchTest test
```

Expected: FAIL because `NoticeController`, `NoticeService`, `NoticeProjectionService`, `NoticeProjectionListener`, `NoticeOutboxHandler`, and `NoticeOutboxEnqueuer` still live under `com.nowcoder.community.message`.

- [ ] **Step 3: Create the dedicated notice mapper and assembler**

Create `backend/community-app/src/main/java/com/nowcoder/community/notice/mapper/NoticeMapper.java`:

```java
package com.nowcoder.community.notice.mapper;

import com.nowcoder.community.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface NoticeMapper {

    int insertMessage(Message message);

    List<Message> selectNotices(@Param("userId") int userId, @Param("topic") String topic, @Param("offset") int offset, @Param("limit") int limit);

    int selectNoticeCount(@Param("userId") int userId, @Param("topic") String topic);

    int selectNoticeUnreadCount(@Param("userId") int userId, @Param("topic") String topic);

    int updateNoticesStatusForRecipient(@Param("ids") List<Integer> ids, @Param("status") int status, @Param("userId") int userId);
}
```

Create `backend/community-app/src/main/resources/mapper/notice_mapper.xml` by moving the surviving notice SQL from `message_mapper.xml` into the new namespace:

```xml
<mapper namespace="com.nowcoder.community.notice.mapper.NoticeMapper">
    <sql id="selectFields">
        id, from_id, to_id, conversation_id, content, status, create_time
    </sql>

    <sql id="insertFields">
        from_id, to_id, conversation_id, content, status, create_time
    </sql>

    <sql id="noticePredicate">
        (
            from_id = 0
            or (
                from_id = 1
                and conversation_id in ('comment', 'like', 'follow', 'moderation')
            )
        )
    </sql>

    <insert id="insertMessage" parameterType="com.nowcoder.community.message.entity.Message" keyProperty="id">
        insert into message(<include refid="insertFields"></include>)
        values(#{fromId},#{toId},#{conversationId},#{content},#{status},#{createTime})
    </insert>

    <select id="selectNotices" resultType="com.nowcoder.community.message.entity.Message">
        select <include refid="selectFields"></include>
        from message
        where status != 2
          and <include refid="noticePredicate"/>
          and to_id = #{userId}
          and conversation_id = #{topic}
        order by create_time desc
        limit #{offset}, #{limit}
    </select>

    <select id="selectNoticeCount" resultType="int">
        select count(id) from message
        where status != 2
          and <include refid="noticePredicate"/>
          and to_id = #{userId}
          and conversation_id = #{topic}
    </select>

    <select id="selectNoticeUnreadCount" resultType="int">
        select count(id) from message
        where status = 0
          and <include refid="noticePredicate"/>
          and to_id = #{userId}
          <if test="topic!=null">
              and conversation_id = #{topic}
          </if>
    </select>

    <update id="updateNoticesStatusForRecipient">
        update message set status = #{status}
        where <include refid="noticePredicate"/>
          and to_id = #{userId}
          and id in
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </update>
</mapper>
```

- [ ] **Step 4: Move the notice controller/service/event classes to the new package**

Move these files with `git mv`, then update their `package` lines and imports:

```bash
git mv backend/community-app/src/main/java/com/nowcoder/community/message/controller/NoticeController.java backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java
git mv backend/community-app/src/main/java/com/nowcoder/community/message/service/NoticeService.java backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java
git mv backend/community-app/src/main/java/com/nowcoder/community/message/service/NoticeProjectionService.java backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionService.java
git mv backend/community-app/src/main/java/com/nowcoder/community/message/event/NoticeProjectionListener.java backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java
git mv backend/community-app/src/main/java/com/nowcoder/community/message/event/NoticeOutboxHandler.java backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java
git mv backend/community-app/src/main/java/com/nowcoder/community/message/event/NoticeOutboxEnqueuer.java backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java
git mv backend/community-app/src/main/java/com/nowcoder/community/message/service/MessageItemAssembler.java backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeItemAssembler.java
```

After the move, `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java` should look like:

```java
package com.nowcoder.community.notice.service;

import com.nowcoder.community.infra.pagination.Pagination;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.notice.mapper.NoticeMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class NoticeService {

    public static final int SYSTEM_NOTICE_SENDER_ID = Message.SYSTEM_NOTICE_SENDER_ID;
    public static final int STATUS_UNREAD = 0;
    public static final int STATUS_READ = 1;

    private final NoticeMapper noticeMapper;
    private final NoticeItemAssembler noticeItemAssembler;

    public NoticeService(NoticeMapper noticeMapper, NoticeItemAssembler noticeItemAssembler) {
        this.noticeMapper = noticeMapper;
        this.noticeItemAssembler = noticeItemAssembler;
    }

    // keep the existing methods and JSON contract intact, only the package + mapper + assembler change
}
```

After the move, `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeItemAssembler.java` should look like:

```java
package com.nowcoder.community.notice.service;

import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.entity.Message;
import org.springframework.stereotype.Service;

@Service
public class NoticeItemAssembler {

    public LetterItemResponse toLetterItem(Message message) {
        if (message == null) {
            return null;
        }
        LetterItemResponse response = new LetterItemResponse();
        response.setId(message.getId());
        response.setFromId(message.getFromId());
        response.setToId(message.getToId());
        response.setConversationId(message.getConversationId());
        response.setContent(message.getContent());
        response.setStatus(message.getStatus());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}
```

Delete the old `MessageMapper` and `message_mapper.xml` once every notice import points at `NoticeMapper`.

- [ ] **Step 5: Run the notice-focused tests**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -Dtest=NoticeModuleArchTest,NoticeServiceTest,NoticeControllerUnitTest,NoticeProjectionListenerTest,NoticeOutboxHandlerTest,NoticeOutboxEnqueuerTest,PaginationOffsetOverflowTest test
```

Expected: PASS for the notice module and the new ArchUnit fence.

- [ ] **Step 6: Commit the notice boundary cleanup**

Run:

```bash
git add backend/community-app/src/main/java backend/community-app/src/main/resources/mapper backend/community-app/src/test/java
git commit -m "refactor: move notice behavior into notice module"
```

## Task 4: Move IM governance out of the legacy message namespace

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/im/architecture/PrivateMessageOwnershipArchTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/action/PrivateMessageGovernanceActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/UserModerationGuard.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java`
- Modify: governance tests to import the new package
- Delete: the old governance files under `com.nowcoder.community.message`

- [ ] **Step 1: Add a failing architecture test for private-message ownership**

Create `backend/community-app/src/test/java/com/nowcoder/community/im/architecture/PrivateMessageOwnershipArchTest.java`:

```java
package com.nowcoder.community.im.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PrivateMessageOwnershipArchTest {

    @Test
    void messageNamespaceShouldNotContainPrivateMessageOwners() {
        var classes = new ClassFileImporter().importPackages("com.nowcoder.community");

        noClasses()
                .that().resideInAPackage("..message..")
                .should().haveSimpleNameContaining("PrivateMessage")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..message..")
                .should().haveSimpleName("UserModerationGuard")
                .check(classes);
    }
}
```

- [ ] **Step 2: Run the architecture test and verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -Dtest=PrivateMessageOwnershipArchTest test
```

Expected: FAIL because the governance API/service/guard still live under `com.nowcoder.community.message`.

- [ ] **Step 3: Create the new governance package and rewire the controller**

Create `backend/community-app/src/main/java/com/nowcoder/community/im/governance/action/PrivateMessageGovernanceActionApi.java`:

```java
package com.nowcoder.community.im.governance.action;

public interface PrivateMessageGovernanceActionApi {

    void validateCanSendPrivateMessage(int fromUserId, int toUserId);
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/im/governance/UserModerationGuard.java`:

```java
package com.nowcoder.community.im.governance;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service("imPrivateMessageUserModerationGuard")
public class UserModerationGuard {

    private final UserModerationQueryApi userModerationQueryApi;

    public UserModerationGuard(UserModerationQueryApi userModerationQueryApi) {
        this.userModerationQueryApi = userModerationQueryApi;
    }

    public void assertCanSendMessage(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }

        UserModerationStateView status = userModerationQueryApi.getModerationState(userId);
        Instant now = Instant.now();

        if (status != null && status.banUntil() != null && status.banUntil().isAfter(now)) {
            throw new BusinessException(FORBIDDEN, "账号已被封禁，无法发送私信");
        }
        if (status != null && status.muteUntil() != null && status.muteUntil().isAfter(now)) {
            throw new BusinessException(FORBIDDEN, "你已被禁言，暂时无法发送私信");
        }
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceService.java`:

```java
package com.nowcoder.community.im.governance;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.im.governance.action.PrivateMessageGovernanceActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.springframework.stereotype.Service;

@Service
public class PrivateMessageGovernanceService implements PrivateMessageGovernanceActionApi {

    private final UserLookupQueryApi userLookupQueryApi;
    private final UserModerationGuard moderationGuard;
    private final SocialBlockQueryApi blockQueryApi;

    public PrivateMessageGovernanceService(
            UserLookupQueryApi userLookupQueryApi,
            UserModerationGuard moderationGuard,
            SocialBlockQueryApi blockQueryApi
    ) {
        this.userLookupQueryApi = userLookupQueryApi;
        this.moderationGuard = moderationGuard;
        this.blockQueryApi = blockQueryApi;
    }

    @Override
    public void validateCanSendPrivateMessage(int fromUserId, int toUserId) {
        if (fromUserId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "fromUserId 非法");
        }
        if (toUserId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "toUserId 非法");
        }
        if (fromUserId == toUserId) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "不能给自己发送私信");
        }

        userLookupQueryApi.requireSummaryById(fromUserId);
        moderationGuard.assertCanSendMessage(fromUserId);
        userLookupQueryApi.requireSummaryById(toUserId);
        if (blockQueryApi != null && blockQueryApi.isEitherBlocked(fromUserId, toUserId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "双方存在拉黑关系，无法发送私信");
        }
    }
}
```

Update `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java` imports to:

```java
import com.nowcoder.community.im.governance.action.PrivateMessageGovernanceActionApi;
```

Delete the old governance files from `com.nowcoder.community.message`.

- [ ] **Step 4: Run governance verification**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -Dtest=PrivateMessageOwnershipArchTest,PrivateMessageGovernanceServiceTest,ImGovernanceControllerTest test
```

Expected: PASS with all imports pointing at the new `com.nowcoder.community.im.governance` package.

- [ ] **Step 5: Commit the governance boundary move**

Run:

```bash
git add backend/community-app/src/main/java backend/community-app/src/test/java
git commit -m "refactor: move private message governance into im module"
```

## Task 5: Update docs and run the full single-owner verification

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `docs/SECURITY.md`
- Modify: `docs/business-logic/im-private-message-flow.md`

- [ ] **Step 1: Update the architecture docs to remove the old owner story**

Apply these content changes:

`docs/ARCHITECTURE.md`

```md
| 消息域（message） | `community-app`：`/api/notices/**` | notice 投影（MySQL `message` 表，仅通知语义） | `community-app` SecurityFilterChain |
| IM 私信域 | `community-im`：`/api/im/**`、`/ws/im` | `im-core`（`im_conversation` / `im_private_message` / `im_conversation_read_state`） | gateway + IM auth |
| IM 治理 | `community-app`：`/api/im-governance/private-messages/validate` | 社区主站治理规则 | `/api/**` JWT 鉴权 |
```

`docs/SYSTEM_DESIGN.md`

```md
- 发送私信：WebSocket `sendPrivateText` -> `im-realtime` -> Kafka -> `im-core`
- 不再提供 `POST /api/messages`
```

`docs/SECURITY.md`

```md
- 私信发送入口：`/ws/im`，发送前必须经过 `POST /api/im-governance/private-messages/validate`
- `community-app` 不再暴露 legacy `/api/messages/**`
```

`docs/business-logic/im-private-message-flow.md`

```md
- `community-app` 仅提供私信治理校验接口，不再提供私信读写 API
- 对外推荐入口只有 `http://localhost:12880/api/im/**` 与 `ws://localhost:12880/ws/im`
```

- [ ] **Step 2: Run repository-level verification commands**

Run:

```bash
rg -n "/api/messages" frontend/src backend/community-app/src/main/java
rg -n "messageService" frontend/src
rg -n "PrivateMessageService|MessageController|SendPrivateMessageUseCase|ListConversationItemsQuery|ListLettersQuery|MarkMessagesReadUseCase" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected:

- no source hits for `/api/messages`
- no `messageService` file/import in `frontend/src`
- no legacy private-message owner classes left in `community-app`

- [ ] **Step 3: Run the full verification suite**

Run:

```bash
npm --prefix frontend test
mvn -f backend/pom.xml -pl community-app test
```

Expected:

- full frontend test suite PASS
- full `community-app` test suite PASS

- [ ] **Step 4: Commit docs and verification follow-up**

Run:

```bash
git add docs/ARCHITECTURE.md docs/SYSTEM_DESIGN.md docs/SECURITY.md docs/business-logic/im-private-message-flow.md
git commit -m "docs: document im as the single private message owner"
```

## Self-Review Checklist

- Spec coverage:
  - single owner: Task 2 + Task 4 + Task 5
  - legacy `/api/messages/**` removal: Task 1 + Task 2
  - notice boundary cleanup: Task 3
  - governance boundary cleanup: Task 4
  - docs and verification: Task 5
- Placeholder scan:
  - no `TODO` / `TBD`
  - every task includes exact files, commands, and code snippets
- Type consistency:
  - notice module ends at `com.nowcoder.community.notice.*`
  - governance module ends at `com.nowcoder.community.im.governance.*`
  - IM route remains `/api/im/**` + `/ws/im`
