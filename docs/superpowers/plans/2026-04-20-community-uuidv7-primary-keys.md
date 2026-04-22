# Community UUIDv7 Primary Keys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace numeric surrogate primary keys with application-generated UUIDv7 identifiers across schema, MyBatis, services, API contracts, Redis encodings, search documents, and IM persistence.

**Architecture:** Introduce one shared UUIDv7 generator and one shared `BINARY(16)` codec. Convert every surrogate-key creation path from DB-generated identity to application-generated UUIDv7. Keep semantic keys and composite keys unchanged.

**Tech Stack:** Spring Boot, MyBatis, MySQL/H2, Redis, Elasticsearch, Kafka, Java 17, JUnit 5.

---

### Task 1: Update DDL and test schema

**Files:**
- Modify: `deploy/mysql/community/010_schema_shared.sql`
- Modify: `deploy/mysql/community/020_schema_identity.sql`
- Modify: `deploy/mysql/community/030_schema_growth_reward.sql`
- Modify: `deploy/mysql/community/031_schema_growth_wallet.sql`
- Modify: `deploy/mysql/community/032_schema_growth_market.sql`
- Modify: `deploy/mysql/community/033_schema_growth_task.sql`
- Modify: `deploy/mysql/community/040_schema_content_core.sql`
- Modify: `deploy/mysql/community/050_schema_social.sql`
- Modify: `deploy/mysql/community/060_schema_message.sql`
- Modify: `deploy/mysql/community/070_schema_im_core.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-im/im-core/src/test/resources/schema.sql`

- [ ] Convert every surrogate PK column and its referencing columns from numeric types to `binary(16)`.
- [ ] Remove `auto_increment` from converted columns.
- [ ] Replace seeded numeric IDs in test schema files with UUID literals encoded for the target database.

### Task 2: Add shared UUIDv7 generation and codec

**Files:**
- Create: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/id/UuidV7Generator.java`
- Create: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/id/BinaryUuidCodec.java`
- Create: `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/id/UuidV7GeneratorTest.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/support/IdGenerator.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/support/IdGeneratorConfig.java`

- [ ] Write a failing generator test for UUID format, version, uniqueness, and sortable time-order.
- [ ] Implement UUIDv7 generation and `UUID <-> byte[16]` conversion.
- [ ] Replace IM numeric ID generation with the shared UUIDv7 generator.

### Task 3: Convert mapper insert paths

**Files:**
- Modify: `backend/community-app/src/main/resources/mapper/user_mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/comment-mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/tag-mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/report-mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/moderationaction-mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/notice_mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/wallet_*.xml`
- Modify: `backend/community-app/src/main/resources/mapper/market_*.xml`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/repository/*.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketPersistenceTest.java`

- [ ] Write a failing persistence test proving inserts no longer rely on `useGeneratedKeys`.
- [ ] Remove `useGeneratedKeys` / `keyProperty` from converted insert mappers.
- [ ] Bind UUID parameters and results through one consistent codec or type handler.

### Task 4: Convert domain, service, and contract types

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/**/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/**/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/**/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/**/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/**/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/**/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/**/*.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/**/*.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/service/UserRegistrationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/app/post/CreatePostUseCaseTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/service/PrivateMessageServiceTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/service/RoomMembershipServiceTest.java`

- [ ] Write failing tests that assert created entity IDs are UUIDs.
- [ ] Replace `int`/`long` surrogate IDs with one consistent UUID boundary type.
- [ ] Replace numeric sort/parse assumptions with `createdAt` ordering or explicit UUID parsing where needed.

### Task 5: Update HTTP, Redis, search, and websocket boundaries

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/**/controller/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/Redis*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/score/RedisPostScoreQueue.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/**/*.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/repo/EsPostDocument.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/dto/SearchPostItem.java`
- Modify: `backend/community-im/im-common/src/main/java/**/*.java`
- Modify: `backend/community-im/im-realtime/src/main/java/**/*.java`

- [ ] Replace `@PathVariable int/long` and numeric parsing with UUID parsing.
- [ ] Update Redis payloads and keys that store numeric IDs.
- [ ] Update search and websocket payloads to carry UUID text identifiers.

### Task 6: Verify module behavior

**Files:**
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/service/UserRegistrationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingActionServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketPersistenceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeServiceTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/service/PrivateMessageServiceTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/kafka/ImCoreKafkaIntegrationTest.java`

- [ ] Run focused red-green cycles for generator, user creation, content creation, market persistence, notice persistence, and IM creation.
- [ ] Run `mvn -pl backend/community-app -Dtest=UserRegistrationServiceTest,CreatePostUseCaseTest,CommentServiceTest,MarketPersistenceTest,NoticeServiceTest test`.
- [ ] Run `mvn -pl backend/community-im/im-core -Dtest=RoomMembershipServiceTest,PrivateMessageServiceTest,ImCoreKafkaIntegrationTest test`.
- [ ] Run one broader module verification pass after the targeted suites are green.
