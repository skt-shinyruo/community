# Community Backend Production MyBatis Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate selected production MySQL access in `community-app` and `community-im/im-core` from direct `JdbcTemplate` repositories to MyBatis mapper interfaces and XML mapper files.

**Architecture:** Keep existing domain repository interfaces and application behavior stable. Move SQL text from Java repositories into MyBatis XML, expose it through `@Mapper` interfaces, and let `MyBatis*Repository` implementations keep orchestration, exception translation, and domain conversion. Leave `common-outbox`, `common-idempotency`, and test helper SQL unchanged.

**Tech Stack:** Java 17, Spring Boot, MyBatis Spring Boot Starter, MyBatis XML mappers, Maven, JUnit 5, ArchUnit-style boundary tests.

---

## File Structure

Create in `community-app`:

- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/RefreshTokenSessionDataObject.java`  
  Row object for `auth_refresh_token`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/RefreshTokenSessionMapper.java`  
  MyBatis mapper for refresh-token session SQL.
- `backend/community-app/src/main/resources/mapper/refresh-token-session-mapper.xml`  
  MyBatis SQL for token store, lookup, consume, revoke, family revocation, user revocation, and cleanup.

Modify in `community-app`:

- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisRefreshTokenSessionRepository.java`  
  Replace `JdbcTemplate` with `RefreshTokenSessionMapper`.
- `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisRefreshTokenSessionRepositoryTest.java`  
  Keep tests, add assertions if needed for consume/delete paths.

Create in `community-im/im-core`:

- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/ConversationMapper.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/ConversationReadStateMapper.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/PrivateMessageMapper.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/RoomMapper.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/RoomMemberMapper.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/RoomMessageMapper.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/RoomReadStateMapper.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper/UserInboxMapper.java`
- `backend/community-im/im-core/src/main/resources/mapper/*.xml` matching the mapper names above.

Modify or rename in `community-im/im-core`:

- `JdbcConversationRepository.java` -> `MyBatisConversationRepository.java`
- `JdbcConversationReadStateRepository.java` -> `MyBatisConversationReadStateRepository.java`
- `JdbcPrivateMessageRepository.java` -> `MyBatisPrivateMessageRepository.java`
- `JdbcRoomRepository.java` -> `MyBatisRoomRepository.java`
- `JdbcRoomMemberRepository.java` -> `MyBatisRoomMemberRepository.java`
- `JdbcRoomMessageRepository.java` -> `MyBatisRoomMessageRepository.java`
- `JdbcRoomReadStateRepository.java` -> `MyBatisRoomReadStateRepository.java`
- `JdbcUserInboxRepository.java` -> `MyBatisUserInboxRepository.java`
- `JdbcUnreadRepository.java` -> `MyBatisUnreadRepository.java` only if naming consistency is desired; it has no SQL and delegates to `UserInboxRepository`.

Modify configuration and build files:

- `backend/community-im/im-core/pom.xml`  
  Add `mybatis-spring-boot-starter`.
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/ImCoreApplication.java`  
  Add `@MapperScan(annotationClass = Mapper.class, basePackages = "com.nowcoder.community.im.core.infrastructure.persistence.mapper")`.
- `backend/community-im/im-core/src/main/resources/application.yml` and `src/test/resources/application.yml` if mapper XML loading needs explicit `mybatis.mapper-locations`.
- `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/ImCoreControllerBoundaryTest.java`  
  Add production guardrail for direct `JdbcTemplate` use in `src/main/java`, excluding `common-*` because this module test only scans IM core.

## Task 1: Refresh Token Repository MyBatis Migration

**Files:**

- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/RefreshTokenSessionDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/RefreshTokenSessionMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/refresh-token-session-mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisRefreshTokenSessionRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisRefreshTokenSessionRepositoryTest.java`

- [ ] **Step 1: Add mapper-facing data object**

Implement `RefreshTokenSessionDataObject` with JavaBean accessors for:

```java
private String tokenHash;
private UUID userId;
private String familyId;
private Instant expiresAt;
private Instant revokedAt;
```

Add:

```java
public RefreshTokenSession toDomain() {
    return new RefreshTokenSession(tokenHash, userId, familyId, expiresAt, revokedAt);
}
```

- [ ] **Step 2: Add mapper interface**

Create `RefreshTokenSessionMapper` with methods:

```java
int storeIfFamilyActive(@Param("tokenHash") String tokenHash,
                        @Param("userId") UUID userId,
                        @Param("familyId") String familyId,
                        @Param("expiresAt") Instant expiresAt);

RefreshTokenSessionDataObject selectByTokenHash(@Param("tokenHash") String tokenHash);

int consumeActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

int revoke(@Param("tokenHash") String tokenHash);

int markFamilyRevoked(@Param("familyId") String familyId);

int revokeFamilyTokens(@Param("familyId") String familyId);

int markUserFamiliesRevoked(@Param("userId") UUID userId);

int revokeUserTokens(@Param("userId") UUID userId);

int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
```

- [ ] **Step 3: Move SQL into XML**

Create `refresh-token-session-mapper.xml` with namespace
`com.nowcoder.community.user.infrastructure.persistence.mapper.RefreshTokenSessionMapper`.

Use the existing SQL from `MyBatisRefreshTokenSessionRepository` unchanged in
behavior:

- `storeIfFamilyActive`: `insert into auth_refresh_token ... select ... where not exists (...) on duplicate key update ...`
- `selectByTokenHash`: select token row by `token_hash`
- `consumeActive`: update `revoked_at = #{now}` where token is active and unexpired
- `revoke`: update single token `revoked_at = now()`
- `markFamilyRevoked`: insert family revocation marker with duplicate-key update
- `revokeFamilyTokens`: revoke active tokens for a family
- `markUserFamiliesRevoked`: insert distinct family revocation markers for a user
- `revokeUserTokens`: revoke active tokens for a user
- `deleteExpiredBefore`: delete expired tokens

Use `jdbcType=BINARY` for UUID parameters and rely on the existing
`UuidBinaryTypeHandler`.

- [ ] **Step 4: Replace repository internals**

Change `MyBatisRefreshTokenSessionRepository` to inject
`RefreshTokenSessionMapper`. Keep all public methods and input validation. Convert
`RefreshTokenSessionDataObject` to domain in `find`.

Keep this behavior:

```java
if (updated <= 0) {
    throw new IllegalStateException("refresh token family 已被撤销");
}
```

- [ ] **Step 5: Run focused refresh-token tests**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='MyBatisRefreshTokenSessionRepositoryTest,RefreshTokenSessionApplicationServiceTest,RefreshTokenCleanupJobTest,DbRefreshTokenRepositoryTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence \
        backend/community-app/src/main/resources/mapper/refresh-token-session-mapper.xml \
        backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisRefreshTokenSessionRepositoryTest.java
git commit -m "refactor: migrate refresh token persistence to mybatis"
```

## Task 2: IM Core MyBatis Wiring

**Files:**

- Modify: `backend/community-im/im-core/pom.xml`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/ImCoreApplication.java`
- Modify: `backend/community-im/im-core/src/main/resources/application.yml`
- Modify: `backend/community-im/im-core/src/test/resources/application.yml`

- [ ] **Step 1: Add MyBatis dependency**

Add to `im-core/pom.xml` near the JDBC dependency:

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
```

- [ ] **Step 2: Add mapper scanning**

Modify `ImCoreApplication`:

```java
import org.mybatis.spring.annotation.MapperScan;

@MapperScan(
        annotationClass = org.apache.ibatis.annotations.Mapper.class,
        basePackages = "com.nowcoder.community.im.core.infrastructure.persistence.mapper"
)
```

- [ ] **Step 3: Add MyBatis properties**

Add to production and test application YAML:

```yaml
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  type-handlers-package: com.nowcoder.community.im.core.infrastructure.persistence.typehandler
  configuration:
    map-underscore-to-camel-case: true
```

If no IM-specific type handler is needed, omit `type-handlers-package` and use
explicit binary UUID conversion in mapper parameter/result data objects.

- [ ] **Step 4: Run a smoke compile**

Run:

```bash
cd backend
mvn test -pl :im-core -am -DskipTests
```

Expected: compile succeeds.

## Task 3: IM Conversation and Message Repositories

**Files:**

- Create mapper interfaces and XML for conversation, room, private message, and room message.
- Rename/modify:
  - `JdbcConversationRepository.java`
  - `JdbcRoomRepository.java`
  - `JdbcPrivateMessageRepository.java`
  - `JdbcRoomMessageRepository.java`

- [ ] **Step 1: Add `ConversationMapper` and `RoomMapper`**

Mapper methods:

```java
int countByConversationId(String conversationId);
int insertConversation(String conversationId, UUID userA, UUID userB);
Long selectLastSeqForUpdate(String conversationId);
int updateLastSeq(String conversationId, long lastSeq);

int countByRoomId(UUID roomId);
int insertRoom(UUID roomId, String name);
Long selectRoomLastSeqForUpdate(UUID roomId);
int updateRoomLastSeq(UUID roomId, long lastSeq);
```

Keep duplicate-key handling in repository classes, not XML.

- [ ] **Step 2: Add `PrivateMessageMapper` and `RoomMessageMapper`**

Mapper methods:

```java
List<PrivateMessageRecord> selectByIdempotency(String conversationId, UUID fromUserId, String clientMsgId);
int insert(PrivateMessageRecord row);
List<PrivateMessageRecord> selectAfterSeq(String conversationId, long afterSeqExclusive, int limit);

List<RoomMessageRecord> selectByIdempotency(UUID roomId, UUID fromUserId, String clientMsgId);
int insert(RoomMessageRecord row);
List<RoomMessageRecord> selectAfterSeq(UUID roomId, long afterSeqExclusive, int limit);
```

Map UUID columns with binary handling. If direct domain result mapping becomes
fragile, add small persistence data objects with `toDomain()` methods.

- [ ] **Step 3: Rename repositories**

Rename `JdbcConversationRepository` to `MyBatisConversationRepository`, and
likewise for room/private-message/room-message repositories. Inject mapper
interfaces and preserve existing exception behavior.

- [ ] **Step 4: Run focused IM message tests**

Run:

```bash
cd backend
mvn test -pl :im-core -am -Dtest='PrivateMessageApplicationServiceTest,RoomMessageApplicationServiceTest,ConversationApplicationServicePaginationOverflowTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-im/im-core
git commit -m "refactor: migrate im message persistence to mybatis"
```

## Task 4: IM Read State and Room Membership Repositories

**Files:**

- Create mapper interfaces and XML for read state and room membership.
- Rename/modify:
  - `JdbcConversationReadStateRepository.java`
  - `JdbcRoomReadStateRepository.java`
  - `JdbcRoomMemberRepository.java`

- [ ] **Step 1: Add read-state mappers**

Mapper methods:

```java
Long selectConversationLastReadSeq(String conversationId, UUID userId);
int updateConversationLastReadSeqMax(String conversationId, UUID userId, long lastReadSeq);
int insertConversationReadState(String conversationId, UUID userId, long lastReadSeq);

Long selectRoomLastReadSeq(UUID roomId, UUID userId);
int updateRoomLastReadSeqMax(UUID roomId, UUID userId, long lastReadSeq);
int insertRoomReadState(UUID roomId, UUID userId, long lastReadSeq);
```

Keep insert-after-update and duplicate-key retry behavior in repositories.

- [ ] **Step 2: Add room-member mapper**

Mapper methods:

```java
int countMembership(UUID roomId, UUID userId);
int countMembers(UUID roomId);
int insertMember(UUID roomId, UUID userId, int role, long version);
int deleteMember(UUID roomId, UUID userId);
List<UUID> selectRoomIdsByUser(UUID userId, UUID cursorRoomIdExclusive, int limit);
List<RoomMembershipEntry> scanMemberships(UUID roomCursor, UUID userCursor, int limit);
Long selectCurrentMembershipProjectionVersion(int id);
int insertMembershipVersionCounter(int id);
Long selectMembershipVersionForUpdate(int id);
int updateMembershipVersion(int id, long currentVersion);
int insertMembershipVersionLog(long version, UUID roomId, UUID userId, boolean active);
```

- [ ] **Step 3: Rename repositories**

Rename the three JDBC repositories to `MyBatis*Repository`, inject the new
mappers, and preserve:

- idempotent duplicate-key handling for joins and read-state inserts
- version floor calculation in Java
- membership version counter row initialization

- [ ] **Step 4: Run focused IM room tests**

Run:

```bash
cd backend
mvn test -pl :im-core -am -Dtest='RoomApplicationServiceTest,RoomMessageApplicationServiceTest,ReadWatermarkRepositoryTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-im/im-core
git commit -m "refactor: migrate im room state persistence to mybatis"
```

## Task 5: IM User Inbox Repository

**Files:**

- Create: `UserInboxMapper.java`
- Create: `user-inbox-mapper.xml`
- Modify/rename: `JdbcUserInboxRepository.java` -> `MyBatisUserInboxRepository.java`
- Optionally rename: `JdbcUnreadRepository.java` -> `MyBatisUnreadRepository.java`

- [ ] **Step 1: Add `UserInboxMapper`**

Mapper methods should cover every SQL currently in `JdbcUserInboxRepository`:

```java
int insertMissingRoomInboxRows(RoomMessageRecord message);
int updateRoomInboxForMessage(RoomMessageRecord message);
Long selectRoomLastSeq(UUID roomId);
int updateExistingRoomMemberInbox(UUID userId, UUID roomId, long lastSeq, long lastReadSeq);
int deleteRoomMemberInbox(UUID userId, UUID roomId);
int markConversationRead(String conversationId, UUID userId, long lastReadSeq);
int markRoomRead(UUID roomId, UUID userId, long lastReadSeq);
List<ConversationListItem> selectConversations(UUID userId, int limit, long offset);
List<RoomUnreadItem> selectRoomUnread(UUID userId, int limit);
List<ConversationUnreadItem> selectConversationUnread(UUID userId, int limit);
int updateConversationInbox(UUID userId, UUID peerUserId, PrivateMessageRecord message, long lastReadSeq, int senderRow);
int insertConversationInbox(UUID userId, UUID peerUserId, PrivateMessageRecord message, long lastReadSeq, long unreadCount);
int insertRoomInbox(UUID userId, UUID roomId, long lastSeq, RoomLastMessageDataObject lastMessage, long lastReadSeq, long unreadCount, Timestamp sortAt);
List<RoomLastMessageDataObject> selectRoomLastMessage(UUID roomId, long lastSeq);
Long selectConversationReadSeq(String conversationId, UUID userId);
Long selectRoomReadSeq(UUID roomId, UUID userId);
```

Use small persistence data objects if direct mapping to nested domain records is
not reliable.

- [ ] **Step 2: Move inbox SQL into XML**

Move the Java text blocks from `JdbcUserInboxRepository` into
`user-inbox-mapper.xml`. Keep statement logic unchanged, including:

- sender rows get `unread_count = 0`
- non-sender room rows compute unread from `last_seq - last_read_seq`
- conversation inbox updates only overwrite last-message fields when the new
  sequence is current
- list queries preserve `sort_at desc` ordering

- [ ] **Step 3: Update repository**

`MyBatisUserInboxRepository` should contain orchestration and calculations only:

- `applyPrivateMessage` calls upsert twice
- `applyRoomMessage` inserts missing room inbox rows then updates inbox rows
- duplicate-key fallback for `ensureRoomMemberInbox`
- conversion for `RoomLastMessage`

- [ ] **Step 4: Run focused inbox tests**

Run:

```bash
cd backend
mvn test -pl :im-core -am -Dtest='PrivateMessageApplicationServiceTest,RoomMessageApplicationServiceTest,RoomApplicationServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-im/im-core
git commit -m "refactor: migrate im inbox persistence to mybatis"
```

## Task 6: Production JDBC Guardrails

**Files:**

- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/ImCoreControllerBoundaryTest.java`
- Add or modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`

- [ ] **Step 1: Add IM core production JDBC scan**

Add a test that walks `src/main/java/com/nowcoder/community/im/core` and fails
if production code imports `org.springframework.jdbc.core.JdbcTemplate`, except
for no known production exceptions.

Expected violation format:

```text
path/to/File.java imports JdbcTemplate
```

- [ ] **Step 2: Add community-app scoped JDBC guardrail**

Add an ArchUnit or file-scan test that blocks `JdbcTemplate` in
`backend/community-app/src/main/java` infrastructure persistence repositories,
while allowing:

- `common-outbox` and `common-idempotency` because they are outside this module
- test sources
- `java.sql.PreparedStatement` and `ResultSet` in MyBatis type handlers
- transaction classes in application services

- [ ] **Step 3: Run architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest'
mvn test -pl :im-core -am -Dtest='ImCoreControllerBoundaryTest'
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch \
        backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/ImCoreControllerBoundaryTest.java
git commit -m "test: guard production jdbc persistence migration"
```

## Task 7: Final Verification

**Files:** no planned source changes.

- [ ] **Step 1: Search for in-scope production JDBC**

Run:

```bash
rg -n "JdbcTemplate|class Jdbc|org\\.springframework\\.jdbc" \
  backend/community-app/src/main/java \
  backend/community-im/im-core/src/main/java \
  -g '*.java'
```

Expected:

- no `JdbcTemplate` in migrated repositories
- no `class Jdbc*Repository` with SQL access
- only MyBatis type handler JDBC SPI references, if any

- [ ] **Step 2: Run focused test suite**

Run:

```bash
cd backend
mvn test -pl :community-app,:im-core -am -Dtest='MyBatisRefreshTokenSessionRepositoryTest,RefreshTokenSessionApplicationServiceTest,RefreshTokenCleanupJobTest,DbRefreshTokenRepositoryTest,PrivateMessageApplicationServiceTest,RoomMessageApplicationServiceTest,RoomApplicationServiceTest,ConversationApplicationServicePaginationOverflowTest,ImCoreControllerBoundaryTest'
```

Expected: PASS.

- [ ] **Step 3: Run broader architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='*ArchTest'
```

Expected: PASS.

- [ ] **Step 4: Summarize migrated and intentionally retained JDBC usage**

Confirm final scope in the handoff:

- migrated: refresh-token session repository and IM core SQL repositories
- intentionally retained: common outbox, common idempotency, test helper SQL,
  and MyBatis type handler JDBC SPI code

