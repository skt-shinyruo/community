# Drive Upload Integrity and Share Fingerprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 持久化 Drive prepare 的服务端权威 checksum 并在 complete 时原样提交给 OSS，同时把分享访问来源转换为固定长度 SHA-256 摘要。

**Architecture:** `DriveUpload` 聚合持有规范化 checksum，MyBatis 通过 Community V012 持久化该事实，application complete 忽略 multipart 自带 checksum。OSS owner 的 internal complete 只为迁移前空 checksum 调用提供 session expected checksum fallback。HTTP controller 在进入 application boundary 前将原始 IP/User-Agent 哈希为不透明摘要。

**Tech Stack:** Java 21、Spring Boot MVC、MyBatis、Flyway、MySQL 8、H2、SHA-256、JUnit 5、Mockito、Testcontainers、Maven。

## Global Constraints

- `backend/community-app` 遵守严格 DDD Tactical Layering；Controller 只能调用同域 `*ApplicationService`。
- Drive 到 OSS 的同步调用继续通过 application-owned `DriveObjectStoragePort`。
- `DriveUpload.checksumSha256` 是 prepare 后的权威值；multipart 请求不能覆盖它。
- 普通用户 OSS complete 保持严格 checksum 语义；fallback 只存在于 `completeInternalUpload`。
- 原始 IP 和 User-Agent 不得进入 application command、domain、repository 或日志。
- V012 是 forward-only migration；不得修改 V001-V011 或 schema manifest。
- `drive_share_access.visitor_fingerprint varchar(128)` 保持不变。

---

### Task 1: Community V012 Drive Checksum Schema

**Files:**

- Create: `backend/community-db-migrations/src/main/resources/db/migration/community/V012__persist_drive_upload_checksum.sql`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationLayoutTest.java`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`

**Interfaces:**

- Consumes: existing `drive_upload` rows from V001-V011.
- Produces: `drive_upload.checksum_sha256 varchar(128) not null default ''`; existing rows retain all data and receive `''`.

- [ ] **Step 1: Write failing migration assertions**

  Raise expected Community migration count from 11 to 12, latest version from `11` to `12`, and assert column metadata:

  ```java
  assertColumn(connection, "drive_upload", "checksum_sha256", "varchar", false, 128);
  assertThat(queryString(connection,
          "select checksum_sha256 from drive_upload where upload_id = ?", legacyUploadId))
          .isEmpty();
  ```

  Extend the V001 upgrade fixture with one Drive upload and assert its IDs, name, status and timestamps survive V012.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest='CommunityMigrationLayoutTest,CommunityMigrationTest' test
  ```

  Expected: migration count and missing column assertions fail.

- [ ] **Step 3: Add V012 and H2 parity**

  ```sql
  alter table drive_upload
    add column checksum_sha256 varchar(128) not null default '' after mime_type;
  ```

  Add the same non-null column/default to `community-app/src/test/resources/schema.sql`; do not alter the frozen manifest.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest='CommunityMigrationLayoutTest,CommunityMigrationTest' test
  git add backend/community-db-migrations/src/main/resources/db/migration/community/V012__persist_drive_upload_checksum.sql \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationLayoutTest.java \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java \
          backend/community-app/src/test/resources/schema.sql
  git commit -m "feat(migration): persist Drive upload checksums"
  ```

### Task 2: Drive Aggregate and Persistence Round Trip

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUpload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/DriveUploadDataObject.java`
- Modify: `backend/community-app/src/main/resources/mapper/drive_upload_mapper.xml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/domain/model/DriveUploadTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveRepositoryTest.java`

**Interfaces:**

- Consumes: normalized checksum string from `DriveUploadApplicationService.prepareUpload`.
- Produces: `DriveUpload.prepared(..., String checksumSha256, UUID objectId, ...)` and accessor `checksumSha256()` preserved by every state transition and persistence mapping.

- [ ] **Step 1: Write RED domain and persistence tests**

  Build a prepared aggregate with `"sha256:abc"`, move it through `startCompleting`, `markObjectCompleted`, and `completeFinalization`, and assert the checksum never changes. Insert/select through `DriveUploadRepository` and assert the same value round trips.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='DriveUploadTest,MyBatisDriveRepositoryTest' test
  ```

  Expected: tests do not compile because `DriveUpload` has no checksum component.

- [ ] **Step 3: Add the aggregate field and mapper column**

  Place `String checksumSha256` after `mimeType`. Normalize null/blank to `""` in `prepared`, copy it in `withState`, and map it in both directions. Add `checksum_sha256` to the mapper `fields` fragment and insert statement. State-only updates must not rewrite it.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='DriveUploadTest,MyBatisDriveRepositoryTest' test
  git add backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUpload.java \
          backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/DriveUploadDataObject.java \
          backend/community-app/src/main/resources/mapper/drive_upload_mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/drive/domain/model/DriveUploadTest.java \
          backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveRepositoryTest.java
  git commit -m "fix(drive): retain upload checksum in aggregate"
  ```

### Task 3: Drive Complete Uses Persisted Checksum

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/DriveUploadContent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DriveController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/controller/DriveControllerUnitTest.java`

**Interfaces:**

- Consumes: `DriveUpload.checksumSha256()` loaded during the completion claim.
- Produces: `DriveObjectStoragePort.CompleteObject.checksumSha256()` equal to the persisted aggregate value. `DriveUploadContent` contains only `UploadStream uploadStream`, `String contentType`, and `long contentLength`.

- [ ] **Step 1: Write failing checksum authority tests**

  Prepare with a non-empty checksum, complete with multipart content, capture `CompleteObject`, and assert:

  ```java
  assertThat(command.checksumSha256()).isEqualTo("sha256:expected");
  ```

  Add controller reflection/command assertions proving no checksum is synthesized from multipart input.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='DriveUploadApplicationServiceTest,DriveControllerUnitTest' test
  ```

  Expected: current complete sends the empty string from `DriveUploadContent`.

- [ ] **Step 3: Pass the persisted checksum and simplify content command**

  Pass `checksumSha256` into `DriveUpload.prepared`. In complete use:

  ```java
  new DriveObjectStoragePort.CompleteObject(
          claimed.ossSessionId(), claimed.objectId(), claimed.versionId(), claimed.name(),
          normalizeContentType(content.contentType()), actualContentLength,
          claimed.checksumSha256(), content)
  ```

  Remove the checksum component from `DriveUploadContent` and update all constructors/tests. This prevents any transport-provided value from becoming authoritative.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest='DriveUploadApplicationServiceTest,DriveControllerUnitTest' test
  git add backend/community-app/src/main/java/com/nowcoder/community/drive \
          backend/community-app/src/test/java/com/nowcoder/community/drive
  git commit -m "fix(drive): complete uploads with persisted checksum"
  ```

### Task 4: OSS Internal Legacy Checksum Fallback

**Files:**

- Modify: `backend/community-oss/src/main/java/com/nowcoder/community/oss/application/ObjectUploadApplicationService.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/ObjectUploadApplicationServiceTest.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/application/ObjectUploadReliabilityContractTest.java`
- Modify: `backend/community-oss/src/test/java/com/nowcoder/community/oss/controller/InternalOssObjectControllerTest.java`
- Modify: `backend/community-oss-client/src/test/java/com/nowcoder/community/oss/client/HttpCommunityOssClientTest.java`

**Interfaces:**

- Consumes: internal `CompleteObjectUploadCommand` and authorized persisted `OssUploadSession`.
- Produces: an effective internal `ObjectUploadContent` whose checksum is the caller value when nonblank, otherwise `session.expectedChecksumSha256()`; nonblank mismatch still fails in existing `validateContent`.

- [ ] **Step 1: Add RED compatibility tests**

  Cover an internal session with expected checksum and blank caller checksum completing successfully, plus a nonblank different checksum throwing `IllegalArgumentException`. Assert ordinary `completeUpload` with blank checksum still rejects the mismatch.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-oss,:community-oss-client -am \
    -Dtest='ObjectUploadApplicationServiceTest,ObjectUploadReliabilityContractTest,InternalOssObjectControllerTest,HttpCommunityOssClientTest' test
  ```

  Expected: the authorized blank internal checksum fails existing strict validation.

- [ ] **Step 3: Resolve effective content inside owner application**

  After internal ownership checks and before delegating to private complete, construct:

  ```java
  ObjectUploadContent incoming = command.content();
  String effectiveChecksum = normalize(incoming.checksumSha256()).isBlank()
          ? session.expectedChecksumSha256()
          : normalize(incoming.checksumSha256());
  ObjectUploadContent effective = new ObjectUploadContent(
          incoming.contentSupplier(), incoming.contentType(), incoming.contentLength(), effectiveChecksum);
  ```

  Do not put fallback logic in the controller, client, metadata query, or public complete path.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-oss,:community-oss-client -am \
    -Dtest='ObjectUploadApplicationServiceTest,ObjectUploadReliabilityContractTest,InternalOssObjectControllerTest,HttpCommunityOssClientTest' test
  git add backend/community-oss/src/main backend/community-oss/src/test backend/community-oss-client/src/test
  git commit -m "fix(oss): recover internal upload checksum from session"
  ```

### Task 5: Fixed-Length Public Share Fingerprint

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DrivePublicShareController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/controller/DrivePublicShareControllerUnitTest.java`

**Interfaces:**

- Consumes: servlet remote address and `User-Agent` header at the HTTP adapter boundary.
- Produces: exactly 64 lowercase hexadecimal characters in `VerifyDriveShareCommand.visitorFingerprint()`.

- [ ] **Step 1: Write the long-header RED test**

  Send a correct share password with a User-Agent longer than 4,000 characters, capture the command, and assert:

  ```java
  assertThat(command.visitorFingerprint()).matches("[0-9a-f]{64}");
  assertThat(command.visitorFingerprint()).doesNotContain(remoteAddress, longUserAgent);
  ```

  Repeat the same address/header and assert deterministic equality; change either input and assert a different digest.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=DrivePublicShareControllerUnitTest test
  ```

  Expected: captured fingerprint contains the full user-controlled header and exceeds 128 characters.

- [ ] **Step 3: Hash a length-delimited canonical byte sequence**

  Use UTF-8 and SHA-256 with unambiguous lengths:

  ```java
  byte[] ip = Objects.toString(request.getRemoteAddr(), "").getBytes(StandardCharsets.UTF_8);
  byte[] ua = Objects.toString(request.getHeader("User-Agent"), "").getBytes(StandardCharsets.UTF_8);
  MessageDigest digest = MessageDigest.getInstance("SHA-256");
  digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(ip.length).array());
  digest.update(ip);
  digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(ua.length).array());
  return HexFormat.of().formatHex(digest.digest(ua));
  ```

  Convert impossible `NoSuchAlgorithmException` into `IllegalStateException` without logging inputs.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=DrivePublicShareControllerUnitTest test
  git add backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DrivePublicShareController.java \
          backend/community-app/src/test/java/com/nowcoder/community/drive/controller/DrivePublicShareControllerUnitTest.java
  git commit -m "fix(drive): hash public share visitor fingerprints"
  ```

### Task 6: Drive and Migration Regression

**Files:**

- Test: all files changed in Tasks 1-5.

**Interfaces:**

- Consumes: V012, Drive aggregate persistence, OSS fallback, and controller hashing.
- Produces: end-to-end evidence for upload integrity and bounded audit input.

- [ ] **Step 1: Run Drive/OSS/migration suites**

  ```bash
  cd backend
  mvn test -pl :community-db-migrations,:community-app,:community-oss-client,:community-oss -am \
    -Dtest='CommunityMigration*Test,*Drive*Test,*Oss*Test,ObjectUpload*Test,HttpCommunityOssClientTest'
  ```

  Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run architecture guardrails**

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  Expected: all architecture tests pass.

- [ ] **Step 3: Inspect invariants**

  ```bash
  rg -n 'ip \+ "\\|" \+ userAgent|normalize\(content\.checksumSha256\(\)\)' \
    backend/community-app/src/main/java/com/nowcoder/community/drive
  git diff --check
  ```

  Expected: no raw fingerprint concatenation, no multipart checksum authority, and no diff errors.
