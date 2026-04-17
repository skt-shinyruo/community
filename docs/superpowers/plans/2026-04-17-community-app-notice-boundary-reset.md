# Community App Notice Boundary Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `notice` to its own DTO/entity/mapper/service types, remove production `notice -> message` dependencies, and lock the new boundary with tests and architecture rules.

**Architecture:** Keep the existing `message` table as storage in this slice, but stop using `message` as a type owner. `notice` will own its read/write models end-to-end, `NoticeService` will own response mapping directly, and the temporary ArchUnit allowlist for `notice -> message` will be removed.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis XML mappers, JUnit 5, AssertJ, Mockito, ArchUnit, Maven

---

### Task 1: Introduce Notice-Owned Persistence And Response Models

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/entity/NoticeRecord.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/dto/NoticeItemResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/dto/NoticeTopicSummaryResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/dto/MarkNoticeReadRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/mapper/NoticeMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/notice_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeItemAssembler.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeItemAssemblerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java`

- [ ] **Step 1: Write the failing tests against notice-owned models**

```java
// backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeServiceTest.java
@Test
void listNoticesShouldReturnNoticeOwnedRecords() {
    insertMessage(0, 2, "comment", "{\"eventId\":\"evt-sentinel\"}", NoticeService.STATUS_UNREAD);
    insertMessage(1, 2, "comment", "{\"eventId\":\"evt-legacy\"}", NoticeService.STATUS_UNREAD);
    insertMessage(1, 2, "1_2", "hello from real user one", NoticeService.STATUS_UNREAD);

    List<NoticeRecord> notices = noticeService.listNotices(2, "comment", 0, 10);

    assertThat(notices)
            .extracting(NoticeRecord::getSenderUserId, NoticeRecord::getRecipientUserId, NoticeRecord::getTopic)
            .containsExactlyInAnyOrder(
                    tuple(0, 2, "comment"),
                    tuple(1, 2, "comment")
            );
}

@Test
void listNoticeItemsShouldReturnNoticeOwnedDtos() {
    insertMessage(0, 9, "comment", "{\"eventId\":\"evt-1\"}", NoticeService.STATUS_UNREAD);

    List<NoticeItemResponse> items = noticeService.listNoticeItems(9, "comment", 0, 10);

    assertThat(items).singleElement().satisfies(item -> {
        assertThat(item.getSenderUserId()).isEqualTo(0);
        assertThat(item.getRecipientUserId()).isEqualTo(9);
        assertThat(item.getTopic()).isEqualTo("comment");
        assertThat(item.getStatus()).isEqualTo(NoticeService.STATUS_UNREAD);
    });
}

// backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java
@Test
void noticeServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
    NoticeMapper noticeMapper = mock(NoticeMapper.class);
    when(noticeMapper.selectNotices(anyInt(), any(), anyInt(), anyInt())).thenReturn(List.of());

    NoticeService service = new NoticeService(noticeMapper);
    service.listNotices(1, "comment", Integer.MAX_VALUE, 50);

    ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(noticeMapper).selectNotices(eq(1), eq("comment"), offsetCaptor.capture(), eq(50));
    assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
}
```

- [ ] **Step 2: Run the targeted tests and confirm they fail for the right reason**

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=NoticeServiceTest,PaginationOffsetOverflowTest test
```

Expected:

- test compilation fails because `NoticeRecord`, `NoticeItemResponse`, and the new `NoticeService(NoticeMapper)` constructor do not exist yet
- no unrelated failures should be chased in this step

- [ ] **Step 3: Add notice-owned models and switch mapper/service contracts to them**

```java
// backend/community-app/src/main/java/com/nowcoder/community/notice/entity/NoticeRecord.java
package com.nowcoder.community.notice.entity;

import java.util.Date;

public class NoticeRecord {
    public static final int SYSTEM_NOTICE_SENDER_ID = 0;
    public static final int LEGACY_NOTICE_SENDER_ID = 1;

    private int id;
    private int senderUserId;
    private int recipientUserId;
    private String topic;
    private String content;
    private int status;
    private Date createTime;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSenderUserId() { return senderUserId; }
    public void setSenderUserId(int senderUserId) { this.senderUserId = senderUserId; }
    public int getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(int recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}

// backend/community-app/src/main/java/com/nowcoder/community/notice/dto/NoticeItemResponse.java
package com.nowcoder.community.notice.dto;

import java.util.Date;

public class NoticeItemResponse {
    private int id;
    private int senderUserId;
    private int recipientUserId;
    private String topic;
    private String content;
    private int status;
    private Date createTime;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSenderUserId() { return senderUserId; }
    public void setSenderUserId(int senderUserId) { this.senderUserId = senderUserId; }
    public int getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(int recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}

// backend/community-app/src/main/java/com/nowcoder/community/notice/dto/NoticeTopicSummaryResponse.java
package com.nowcoder.community.notice.dto;

public class NoticeTopicSummaryResponse {
    private String topic;
    private NoticeItemResponse latest;
    private int noticeCount;
    private int unreadCount;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public NoticeItemResponse getLatest() { return latest; }
    public void setLatest(NoticeItemResponse latest) { this.latest = latest; }
    public int getNoticeCount() { return noticeCount; }
    public void setNoticeCount(int noticeCount) { this.noticeCount = noticeCount; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}

// backend/community-app/src/main/java/com/nowcoder/community/notice/dto/MarkNoticeReadRequest.java
package com.nowcoder.community.notice.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class MarkNoticeReadRequest {
    @NotEmpty
    private List<Integer> ids;

    public List<Integer> getIds() { return ids; }
    public void setIds(List<Integer> ids) { this.ids = ids; }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/notice/mapper/NoticeMapper.java
package com.nowcoder.community.notice.mapper;

import com.nowcoder.community.notice.entity.NoticeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface NoticeMapper {
    int insertNotice(NoticeRecord notice);
    List<NoticeRecord> selectNotices(@Param("userId") int userId, @Param("topic") String topic, @Param("offset") int offset, @Param("limit") int limit);
    int selectNoticeCount(@Param("userId") int userId, @Param("topic") String topic);
    int selectNoticeUnreadCount(@Param("userId") int userId, @Param("topic") String topic);
    int updateNoticesStatusForRecipient(@Param("ids") List<Integer> ids, @Param("status") int status, @Param("userId") int userId);
}

// backend/community-app/src/main/resources/mapper/notice_mapper.xml
<insert id="insertNotice" parameterType="com.nowcoder.community.notice.entity.NoticeRecord" keyProperty="id">
    insert into message(from_id, to_id, conversation_id, content, status, create_time)
    values(#{senderUserId},#{recipientUserId},#{topic},#{content},#{status},#{createTime})
</insert>

<select id="selectNotices" resultType="com.nowcoder.community.notice.entity.NoticeRecord">
    select id,
           from_id as senderUserId,
           to_id as recipientUserId,
           conversation_id as topic,
           content,
           status,
           create_time as createTime
    from message
    where status != 2
      and <include refid="noticePredicate"/>
      and to_id = #{userId}
      and conversation_id = #{topic}
    order by create_time desc
    limit #{offset}, #{limit}
</select>

// backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java
public class NoticeService {
    public static final int SYSTEM_NOTICE_SENDER_ID = NoticeRecord.SYSTEM_NOTICE_SENDER_ID;
    public static final int STATUS_UNREAD = 0;
    public static final int STATUS_READ = 1;

    private final NoticeMapper noticeMapper;

    public NoticeService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    public void createNotice(int toUserId, String topic, String contentJson) {
        NoticeRecord notice = new NoticeRecord();
        notice.setSenderUserId(SYSTEM_NOTICE_SENDER_ID);
        notice.setRecipientUserId(toUserId);
        notice.setTopic(topic);
        notice.setContent(contentJson);
        notice.setStatus(STATUS_UNREAD);
        notice.setCreateTime(new Date());
        noticeMapper.insertNotice(notice);
    }

    public List<NoticeRecord> listNotices(int userId, String topic, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return noticeMapper.selectNotices(userId, topic, Pagination.safeOffset(p, s), s);
    }

    public List<NoticeItemResponse> listNoticeItems(int userId, String topic, int page, int size) {
        return listNotices(userId, topic, page, size).stream()
                .map(this::toNoticeItemResponse)
                .toList();
    }

    public int unreadCount(int userId, String topic) {
        return noticeMapper.selectNoticeUnreadCount(userId, topic);
    }

    public void markRead(int userId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        noticeMapper.updateNoticesStatusForRecipient(ids, STATUS_READ, userId);
    }

    public List<NoticeTopicSummaryResponse> topicSummary(int userId) {
        return List.of("comment", "like", "follow", "moderation").stream().map(topic -> {
            NoticeTopicSummaryResponse response = new NoticeTopicSummaryResponse();
            response.setTopic(topic);
            List<NoticeRecord> latest = noticeMapper.selectNotices(userId, topic, 0, 1);
            response.setLatest(latest.isEmpty() ? null : toNoticeItemResponse(latest.get(0)));
            response.setNoticeCount(noticeMapper.selectNoticeCount(userId, topic));
            response.setUnreadCount(noticeMapper.selectNoticeUnreadCount(userId, topic));
            return response;
        }).toList();
    }

    private NoticeItemResponse toNoticeItemResponse(NoticeRecord notice) {
        NoticeItemResponse response = new NoticeItemResponse();
        response.setId(notice.getId());
        response.setSenderUserId(notice.getSenderUserId());
        response.setRecipientUserId(notice.getRecipientUserId());
        response.setTopic(notice.getTopic());
        response.setContent(notice.getContent());
        response.setStatus(notice.getStatus());
        response.setCreateTime(notice.getCreateTime());
        return response;
    }
}
```

- [ ] **Step 4: Run the focused service tests until they pass**

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=NoticeServiceTest,PaginationOffsetOverflowTest test
```

Expected:

- `NoticeServiceTest` passes
- `PaginationOffsetOverflowTest` passes
- no production reference to `NoticeItemAssembler` remains

- [ ] **Step 5: Commit the service/model refactor**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/notice/entity/NoticeRecord.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/dto/NoticeItemResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/dto/NoticeTopicSummaryResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/dto/MarkNoticeReadRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/mapper/NoticeMapper.java \
  backend/community-app/src/main/resources/mapper/notice_mapper.xml \
  backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeService.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeItemAssembler.java
git rm backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeItemAssemblerTest.java
git commit -m "refactor: move notice service to notice-owned models"
```

### Task 2: Move Notice HTTP Contracts To Notice DTOs And Remove Message Production Types

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/controller/NoticeControllerUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxHandlerTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/LetterItemResponse.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/MarkReadRequest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/dto/NoticeTopicSummaryResponse.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/entity/Message.java`

- [ ] **Step 1: Write the failing controller test against notice-owned HTTP DTOs**

```java
// backend/community-app/src/test/java/com/nowcoder/community/notice/controller/NoticeControllerUnitTest.java
@Test
void listShouldDelegateToNoticeOwnedDtoReturningServiceMethod() {
    NoticeItemResponse item = new NoticeItemResponse();
    item.setId(15);
    item.setTopic("comment");
    when(noticeService.listNoticeItems(7, "comment", 0, 10)).thenReturn(List.of(item));

    Result<List<NoticeItemResponse>> result = controller.list(authentication(7), "comment", null, null);

    assertThat(result.getCode()).isEqualTo(0);
    assertThat(result.getData()).containsExactly(item);
    verify(noticeService).listNoticeItems(7, "comment", 0, 10);
}
```

- [ ] **Step 2: Run the controller and event tests and confirm the controller contract fails first**

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=NoticeControllerUnitTest,NoticeProjectionListenerTest,NoticeOutboxHandlerTest,LegacyMessageApiRemovalTest test
```

Expected:

- `NoticeControllerUnitTest` fails to compile because the controller still exposes `message.dto.*`
- the event tests either stay green or only need import cleanup after the controller change

- [ ] **Step 3: Switch the controller to notice DTOs and delete the message production package**

```java
// backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java
package com.nowcoder.community.notice.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.notice.dto.MarkNoticeReadRequest;
import com.nowcoder.community.notice.dto.NoticeItemResponse;
import com.nowcoder.community.notice.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.notice.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public Result<List<NoticeItemResponse>> list(
            Authentication authentication,
            @RequestParam String topic,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(noticeService.listNoticeItems(userId, topic, p, s));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication, @RequestParam(required = false) String topic) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(noticeService.unreadCount(userId, topic));
    }

    @GetMapping("/summary")
    public Result<List<NoticeTopicSummaryResponse>> summary(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(noticeService.topicSummary(userId));
    }

    @PutMapping("/read")
    public Result<Void> markRead(Authentication authentication, @Valid @RequestBody MarkNoticeReadRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        noticeService.markRead(userId, request.getIds());
        return Result.ok();
    }
}
```

```bash
cd /home/feng/code/project/community
git rm backend/community-app/src/main/java/com/nowcoder/community/message/dto/LetterItemResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/message/dto/MarkReadRequest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/message/dto/NoticeTopicSummaryResponse.java
git rm backend/community-app/src/main/java/com/nowcoder/community/message/entity/Message.java
```

- [ ] **Step 4: Re-run controller and event tests until they pass**

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=NoticeControllerUnitTest,NoticeProjectionListenerTest,NoticeOutboxHandlerTest,LegacyMessageApiRemovalTest test
```

Expected:

- all four tests pass
- no production Java file under `notice/**` imports `com.nowcoder.community.message.*`

- [ ] **Step 5: Commit the HTTP contract and message package cleanup**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/controller/NoticeControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxHandlerTest.java
git commit -m "refactor: move notice api off message types"
```

### Task 3: Remove Architecture Exemptions And Update Docs

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `docs/ARCHITECTURE.md`

- [ ] **Step 1: Rewrite the architecture tests so notice no longer has any shared-message allowance**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java
static final Map<String, Set<String>> TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN = Map.of();

// backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java
@Test
void entityBoundaryShouldNotRequireSharedMessageReuseExceptions() {
    assertThat(LEGACY_FOREIGN_ENTITY_CALLERS).isEmpty();
    assertThat(ArchitectureRulesSupport.TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN).isEmpty();
}

// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
@Test
void dtoBoundaryShouldNotRequireSharedMessageDtoExceptions() {
    assertThat(LEGACY_FOREIGN_DTO_CONTROLLER_CALLERS).isEmpty();
    assertThat(ArchitectureRulesSupport.TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN).isEmpty();
}
```

- [ ] **Step 2: Run the architecture tests and confirm they fail if any message leakage still remains**

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- either both tests pass immediately, or they fail with direct remaining `notice -> message` references
- do not proceed until the failure points to real leftover imports or stale assertions

- [ ] **Step 3: Update the docs and remove the old allowance language**

```markdown
<!-- docs/ARCHITECTURE.md -->
- `com.nowcoder.community.notice`：站内通知对外 API、通知投影与已读语义；`notice` owns its DTO/entity/mapper/service types
- `com.nowcoder.community.message`：legacy package retained only for migration cleanup if present; no longer the owner model for notice inside `community-app`

当前分支上的 `DomainBoundaryArchTest` 与 `ControllerBoundaryArchTest` 已默认绿色，notice/message 临时共享类型白名单已删除。
```

- [ ] **Step 4: Run the full focused verification suite**

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=NoticeServiceTest,NoticeControllerUnitTest,NoticeProjectionListenerTest,NoticeOutboxHandlerTest,PaginationOffsetOverflowTest,LegacyMessageApiRemovalTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected:

- Maven exits with code `0`
- all focused notice and architecture tests are green
- no compilation errors remain from deleted `message` production types

- [ ] **Step 5: Commit the guardrail and documentation cleanup**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java \
  docs/ARCHITECTURE.md
git commit -m "test: remove notice message boundary exemptions"
```

---

## Self-Review

- Spec coverage:
  - `B` owner-type migration is covered by Task 1 and Task 2
  - architecture/doc enforcement is covered by Task 3
  - `C` and `D` are intentionally not implemented in this plan

- Placeholder scan:
  - no `TBD`, `TODO`, or vague “handle appropriately” steps remain

- Type consistency:
  - notice-owned names are consistent across tasks:
    - `NoticeRecord`
    - `NoticeItemResponse`
    - `NoticeTopicSummaryResponse`
    - `MarkNoticeReadRequest`
