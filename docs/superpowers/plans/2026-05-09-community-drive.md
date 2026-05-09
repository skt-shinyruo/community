# Community Drive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a private 10 GiB per-user cloud drive with folders, upload/download, recycle bin, search, and password-protected expiring share links.

**Architecture:** Add a new `drive` domain inside `backend/community-app` using strict DDD tactical layering. Drive owns user file-system facts and quota; `community-oss` remains the binary object owner and is reached only through a drive-owned application port implemented by a drive infrastructure adapter. The frontend adds a `/drive` workspace and public share page using the existing Vue 3 shell, API service pattern, and generic upload-session helper.

**Tech Stack:** Spring Boot MVC, MyBatis, Java records/classes, Maven, `community-oss-client`, Vue 3, Vue Router, Pinia-compatible state helpers, Vitest, existing frontend `executeUploadSession`.

---

## Scope

Spec: `docs/superpowers/specs/2026-05-09-community-drive-design.md`

This is one vertical product feature. Backend tasks land first because frontend tests should normalize against the backend response contract. The upload flow follows the repository's existing provider-free upload-session pattern:

1. `POST /api/drive/uploads` creates a drive upload session after checking ownership, parent folder, sibling name, and quota.
2. The response contains an `upload` instruction with `url: /api/drive/uploads/{uploadId}/complete`.
3. Frontend calls `executeUploadSession`.
4. `DriveUploadController` receives multipart content, proxies it to `community-oss` through `DriveObjectStoragePort`, creates the drive file entry, and increments `used_bytes` exactly once.

## File Structure

Create backend files:

- `backend/community-app/src/main/java/com/nowcoder/community/drive/exception/DriveErrorCode.java`: stable drive business errors.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveSpace.java`: quota owner for one user.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveEntry.java`: folder/file aggregate state.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveEntryStatus.java`: `ACTIVE`, `TRASHED`, `DELETED`.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveEntryType.java`: `FOLDER`, `FILE`.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUpload.java`: in-flight upload session state.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUploadStatus.java`: `PREPARED`, `COMPLETED`, `EXPIRED`.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveShare.java`: share token, password hash, expiry, status.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveShareStatus.java`: `ACTIVE`, `EXPIRED`, `REVOKED`.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/service/DriveEntryDomainService.java`: name normalization and move invariants.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveSpaceRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveEntryRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveUploadRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveShareRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveShareAccessRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/port/DriveObjectStoragePort.java`: drive-owned OSS port.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/port/DrivePasswordHasher.java`: password hash boundary.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/port/DriveShareTicketCodec.java`: short-lived share ticket boundary.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveSpaceApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveEntryApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveTrashApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveShareApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/*.java`: one command record per write use case.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/result/*.java`: drive-specific application results.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/*.java`: MyBatis rows.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/*.java`: MyBatis mapper interfaces.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatis*Repository.java`: repository implementations.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/oss/OssDriveObjectStorageAdapter.java`: `community-oss-client` adapter.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/security/BCryptDrivePasswordHasher.java`: password hashing adapter.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/security/HmacDriveShareTicketCodec.java`: signed ticket adapter.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DriveController.java`: authenticated drive APIs.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DrivePublicShareController.java`: public share APIs.
- `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/*.java`: HTTP request/response DTOs.
- `backend/community-app/src/main/resources/mapper/drive_*.xml`: MyBatis XML mappings.

Modify backend files:

- `deploy/mysql/community/090_schema_drive.sql`: production drive schema.
- `deploy/mysql/community_oss/010_schema.sql`: seed the `DRIVE_FILE` usage policy.
- `backend/community-app/src/test/resources/schema.sql`: H2 test schema for drive tables.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`: keep drive covered by DDD layer rules.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`: add a drive controller same-domain application service guardrail.

Create frontend files:

- `frontend/src/api/services/driveService.js`: drive API client and upload execution helpers.
- `frontend/src/api/services/driveService.test.js`: API normalization tests.
- `frontend/src/views/driveState.js`: pure view state for entries, breadcrumbs, quota, trash, share form, and upload progress.
- `frontend/src/views/driveState.test.js`: pure state tests.
- `frontend/src/views/DriveView.vue`: authenticated drive workspace.
- `frontend/src/views/DriveShareView.vue`: public share page.

Modify frontend and documentation files:

- `frontend/src/router/index.js`: add `/drive` and `/drive/s/:shareToken`.
- `frontend/src/router/navigation.js`: add drive entry under personal navigation.
- `frontend/src/router/navigation.test.js`: guard drive navigation.
- `frontend/src/router/index.test.js`: guard drive route inventory.
- `docs/handbook/business-logic/frontend-surfaces.md`: map `/drive` and `/drive/s/:shareToken`.
- `docs/handbook/business-logic/oss.md`: list drive as an OSS consumer.

## Task 1: Drive Schema

**Files:**
- Create: `deploy/mysql/community/090_schema_drive.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/DriveSchemaResourceTest.java`

- [ ] **Step 1: Write the failing schema resource test**

Create `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/DriveSchemaResourceTest.java`:

```java
package com.nowcoder.community.drive.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DriveSchemaResourceTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionSchemaShouldDefineDriveTablesAndIndexes() throws IOException {
        String sql = Files.readString(REPO_ROOT.resolve("deploy/mysql/community/090_schema_drive.sql"));

        assertThat(sql).contains(
                "create table if not exists drive_space",
                "create table if not exists drive_entry",
                "create table if not exists drive_upload",
                "create table if not exists drive_share",
                "create table if not exists drive_share_access",
                "unique key uk_drive_space_user",
                "unique key uk_drive_entry_active_name",
                "unique key uk_drive_share_token"
        );
    }
}
```

- [ ] **Step 2: Run the schema test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=DriveSchemaResourceTest test
```

Expected: FAIL because `deploy/mysql/community/090_schema_drive.sql` does not exist.

- [ ] **Step 3: Add the production schema**

Create `deploy/mysql/community/090_schema_drive.sql`:

```sql
use community;

create table if not exists drive_space (
  space_id binary(16) primary key,
  user_id binary(16) not null,
  quota_bytes bigint not null default 10737418240,
  used_bytes bigint not null default 0,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_space_user (user_id),
  key idx_drive_space_updated (updated_at)
);

create table if not exists drive_entry (
  entry_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16) null,
  parent_key varchar(32) not null default '',
  active_name varchar(255) null,
  type varchar(16) not null,
  name varchar(255) not null,
  object_id binary(16) null,
  version_id binary(16) null,
  size_bytes bigint not null default 0,
  mime_type varchar(128) not null default '',
  status varchar(16) not null,
  trashed_at timestamp null default null,
  delete_after timestamp null default null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_entry_active_name (space_id, parent_key, active_name),
  key idx_drive_entry_parent_status (space_id, parent_id, status, name),
  key idx_drive_entry_object (object_id, version_id),
  key idx_drive_entry_trash (space_id, status, trashed_at),
  key idx_drive_entry_search (space_id, status, name)
);

create table if not exists drive_upload (
  upload_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16) null,
  name varchar(255) not null,
  size_bytes bigint not null,
  mime_type varchar(128) not null,
  object_id binary(16) not null,
  version_id binary(16) not null,
  oss_session_id binary(16) not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp not null default current_timestamp,
  expires_at timestamp not null,
  completed_entry_id binary(16) null,
  key idx_drive_upload_space_status (space_id, status, expires_at),
  key idx_drive_upload_object (object_id, version_id)
);

create table if not exists drive_share (
  share_id binary(16) primary key,
  entry_id binary(16) not null,
  share_token varchar(96) not null,
  password_hash varchar(255) not null,
  expires_at timestamp not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_share_token (share_token),
  key idx_drive_share_entry_status (entry_id, status),
  key idx_drive_share_expiry (status, expires_at)
);

create table if not exists drive_share_access (
  access_id binary(16) primary key,
  share_id binary(16) not null,
  visitor_fingerprint varchar(128) not null default '',
  success tinyint(1) not null default 0,
  accessed_at timestamp not null default current_timestamp,
  key idx_drive_share_access_share_time (share_id, accessed_at),
  key idx_drive_share_access_fingerprint_time (visitor_fingerprint, accessed_at)
);
```

- [ ] **Step 4: Add matching H2 test schema**

Append equivalent H2-compatible tables to `backend/community-app/src/test/resources/schema.sql`. Use explicit columns instead of generated columns:

```sql
create table if not exists drive_space (
  space_id binary(16) primary key,
  user_id binary(16) not null,
  quota_bytes bigint not null default 10737418240,
  used_bytes bigint not null default 0,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_drive_space_user unique (user_id)
);

create table if not exists drive_entry (
  entry_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16),
  parent_key varchar(32) not null default '',
  active_name varchar(255),
  type varchar(16) not null,
  name varchar(255) not null,
  object_id binary(16),
  version_id binary(16),
  size_bytes bigint not null default 0,
  mime_type varchar(128) not null default '',
  status varchar(16) not null,
  trashed_at timestamp,
  delete_after timestamp,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_drive_entry_active_name unique (space_id, parent_key, active_name)
);

create index if not exists idx_drive_entry_parent_status on drive_entry(space_id, parent_id, status, name);
create index if not exists idx_drive_entry_trash on drive_entry(space_id, status, trashed_at);

create table if not exists drive_upload (
  upload_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16),
  name varchar(255) not null,
  size_bytes bigint not null,
  mime_type varchar(128) not null,
  object_id binary(16) not null,
  version_id binary(16) not null,
  oss_session_id binary(16) not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp default current_timestamp,
  expires_at timestamp not null,
  completed_entry_id binary(16)
);

create index if not exists idx_drive_upload_space_status on drive_upload(space_id, status, expires_at);

create table if not exists drive_share (
  share_id binary(16) primary key,
  entry_id binary(16) not null,
  share_token varchar(96) not null,
  password_hash varchar(255) not null,
  expires_at timestamp not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_drive_share_token unique (share_token)
);

create index if not exists idx_drive_share_entry_status on drive_share(entry_id, status);
create index if not exists idx_drive_share_expiry on drive_share(status, expires_at);

create table if not exists drive_share_access (
  access_id binary(16) primary key,
  share_id binary(16) not null,
  visitor_fingerprint varchar(128) not null default '',
  success boolean not null default false,
  accessed_at timestamp default current_timestamp
);

create index if not exists idx_drive_share_access_share_time on drive_share_access(share_id, accessed_at);
```

- [ ] **Step 5: Run the schema test again**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=DriveSchemaResourceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit schema**

Run:

```bash
git add deploy/mysql/community/090_schema_drive.sql backend/community-app/src/test/resources/schema.sql backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/DriveSchemaResourceTest.java
git commit -m "feat: add drive schema"
```

## Task 2: Domain Model And Rules

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/exception/DriveErrorCode.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveSpace.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveEntry.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveEntryType.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveEntryStatus.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUpload.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUploadStatus.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveShare.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveShareStatus.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/service/DriveEntryDomainService.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/domain/model/DriveSpaceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/domain/model/DriveEntryTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/domain/model/DriveShareTest.java`

- [ ] **Step 1: Write failing domain tests**

Create `DriveSpaceTest`:

```java
package com.nowcoder.community.drive.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveSpaceTest {

    @Test
    void defaultSpaceShouldStartWithTenGiBQuotaAndNoUsage() {
        DriveSpace space = DriveSpace.createDefault(uuid(1), uuid(7), Instant.parse("2026-05-09T00:00:00Z"));

        assertThat(space.quotaBytes()).isEqualTo(10_737_418_240L);
        assertThat(space.usedBytes()).isZero();
        assertThat(space.remainingBytes()).isEqualTo(10_737_418_240L);
    }

    @Test
    void reserveAndReleaseShouldProtectQuotaBounds() {
        DriveSpace space = DriveSpace.createDefault(uuid(1), uuid(7), Instant.parse("2026-05-09T00:00:00Z"));

        DriveSpace reserved = space.reserve(1_024L, Instant.parse("2026-05-09T00:01:00Z"));
        assertThat(reserved.usedBytes()).isEqualTo(1_024L);
        assertThat(reserved.release(512L, Instant.parse("2026-05-09T00:02:00Z")).usedBytes()).isEqualTo(512L);

        assertThatThrownBy(() -> space.reserve(10_737_418_241L, Instant.parse("2026-05-09T00:03:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drive quota exceeded");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

Create `DriveEntryTest`:

```java
package com.nowcoder.community.drive.domain.model;

import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveEntryTest {

    @Test
    void folderAndFileShouldNormalizeNamesAndStatuses() {
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveEntry folder = DriveEntry.folder(uuid(1), uuid(2), null, " Docs ", now);
        DriveEntry file = DriveEntry.file(uuid(3), uuid(2), folder.entryId(), " a.txt ", uuid(4), uuid(5), 8, "text/plain", now);

        assertThat(folder.name()).isEqualTo("Docs");
        assertThat(folder.type()).isEqualTo(DriveEntryType.FOLDER);
        assertThat(folder.status()).isEqualTo(DriveEntryStatus.ACTIVE);
        assertThat(file.name()).isEqualTo("a.txt");
        assertThat(file.type()).isEqualTo(DriveEntryType.FILE);
        assertThat(file.sizeBytes()).isEqualTo(8);
    }

    @Test
    void trashedEntryShouldRejectRenameUntilRestored() {
        DriveEntry file = DriveEntry.file(uuid(3), uuid(2), null, "a.txt", uuid(4), uuid(5), 8, "text/plain", Instant.parse("2026-05-09T00:00:00Z"))
                .trash(Instant.parse("2026-05-09T00:01:00Z"), Instant.parse("2026-06-08T00:01:00Z"));

        assertThat(file.status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThatThrownBy(() -> file.rename("b.txt", Instant.parse("2026-05-09T00:02:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("trashed entry cannot be changed");
    }

    @Test
    void domainServiceShouldRejectMovingFolderIntoDescendant() {
        DriveEntryDomainService service = new DriveEntryDomainService();
        UUID folderId = uuid(10);
        UUID childId = uuid(11);
        UUID grandChildId = uuid(12);

        assertThatThrownBy(() -> service.assertCanMove(folderId, grandChildId, List.of(childId, grandChildId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("folder cannot be moved into itself or descendant");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

Create `DriveShareTest`:

```java
package com.nowcoder.community.drive.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DriveShareTest {

    @Test
    void activeShareShouldRespectExpiryAndRevocation() {
        DriveShare share = DriveShare.active(
                uuid(1),
                uuid(2),
                "share-token",
                "hash",
                Instant.parse("2026-05-10T00:00:00Z"),
                uuid(7),
                Instant.parse("2026-05-09T00:00:00Z")
        );

        assertThat(share.activeAt(Instant.parse("2026-05-09T12:00:00Z"))).isTrue();
        assertThat(share.activeAt(Instant.parse("2026-05-10T00:00:01Z"))).isFalse();
        assertThat(share.revoke(Instant.parse("2026-05-09T13:00:00Z")).activeAt(Instant.parse("2026-05-09T13:00:01Z"))).isFalse();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

- [ ] **Step 2: Run domain tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DriveSpaceTest,DriveEntryTest,DriveShareTest' test
```

Expected: compilation failure because drive domain classes do not exist.

- [ ] **Step 3: Add `DriveErrorCode`**

Create `backend/community-app/src/main/java/com/nowcoder/community/drive/exception/DriveErrorCode.java`:

```java
package com.nowcoder.community.drive.exception;

import com.nowcoder.community.common.exception.ErrorCode;

public enum DriveErrorCode implements ErrorCode {
    DRIVE_SPACE_NOT_FOUND(16001, "网盘空间不存在", 404),
    DRIVE_ENTRY_NOT_FOUND(16002, "网盘条目不存在", 404),
    DRIVE_PARENT_NOT_FOUND(16003, "目标文件夹不存在", 404),
    DRIVE_DUPLICATE_NAME(16004, "同名文件或文件夹已存在", 409),
    DRIVE_QUOTA_EXCEEDED(16005, "网盘容量不足", 409),
    DRIVE_INVALID_MOVE(16006, "不能移动到自身或子目录", 400),
    DRIVE_ENTRY_TRASHED(16007, "回收站条目不可执行该操作", 409),
    DRIVE_SHARE_INVALID(16008, "分享链接不可用", 404),
    DRIVE_SHARE_PASSWORD_INVALID(16009, "提取码错误", 403),
    DRIVE_UPLOAD_INVALID(16010, "上传会话不可用", 409),
    DRIVE_STORAGE_UNAVAILABLE(16011, "网盘存储服务不可用", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    DriveErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
```

- [ ] **Step 4: Add domain enums and models**

Create the enum files:

```java
package com.nowcoder.community.drive.domain.model;

public enum DriveEntryType {
    FOLDER,
    FILE
}
```

```java
package com.nowcoder.community.drive.domain.model;

public enum DriveEntryStatus {
    ACTIVE,
    TRASHED,
    DELETED
}
```

```java
package com.nowcoder.community.drive.domain.model;

public enum DriveUploadStatus {
    PREPARED,
    COMPLETED,
    EXPIRED
}
```

```java
package com.nowcoder.community.drive.domain.model;

public enum DriveShareStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
```

Create `DriveSpace` with these public methods:

```java
public static final long DEFAULT_QUOTA_BYTES = 10L * 1024L * 1024L * 1024L;

public static DriveSpace createDefault(UUID spaceId, UUID userId, Instant now)
public long remainingBytes()
public DriveSpace reserve(long bytes, Instant now)
public DriveSpace release(long bytes, Instant now)
```

Use `IllegalArgumentException` for null IDs and negative byte values. Use `IllegalStateException("drive quota exceeded")` when reservation exceeds quota.

Create `DriveEntry` with these public factory and transition methods:

```java
public static DriveEntry folder(UUID entryId, UUID spaceId, UUID parentId, String name, Instant now)
public static DriveEntry file(UUID entryId, UUID spaceId, UUID parentId, String name, UUID objectId, UUID versionId, long sizeBytes, String mimeType, Instant now)
public boolean folder()
public boolean file()
public DriveEntry rename(String newName, Instant now)
public DriveEntry moveTo(UUID newParentId, Instant now)
public DriveEntry trash(Instant trashedAt, Instant deleteAfter)
public DriveEntry restore(UUID targetParentId, Instant now)
public DriveEntry delete(Instant now)
```

Create `DriveUpload` with:

```java
public static DriveUpload prepared(UUID uploadId, UUID spaceId, UUID parentId, String name, long sizeBytes, String mimeType, UUID objectId, UUID versionId, UUID ossSessionId, UUID createdBy, Instant now, Instant expiresAt)
public boolean expiredAt(Instant now)
public boolean completed()
public DriveUpload complete(UUID entryId, Instant now)
```

Create `DriveShare` with:

```java
public static DriveShare active(UUID shareId, UUID entryId, String shareToken, String passwordHash, Instant expiresAt, UUID createdBy, Instant now)
public boolean activeAt(Instant now)
public DriveShare revoke(Instant now)
```

- [ ] **Step 5: Add `DriveEntryDomainService`**

Create `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/service/DriveEntryDomainService.java`:

```java
package com.nowcoder.community.drive.domain.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DriveEntryDomainService {

    public String normalizeName(String name) {
        String value = Objects.toString(name, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("entry name must not be blank");
        }
        if (value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("entry name is invalid");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("entry name is too long");
        }
        return value;
    }

    public void assertCanMove(UUID entryId, UUID newParentId, List<UUID> descendantIds) {
        if (entryId == null || newParentId == null) {
            return;
        }
        if (entryId.equals(newParentId) || (descendantIds != null && descendantIds.contains(newParentId))) {
            throw new IllegalArgumentException("folder cannot be moved into itself or descendant");
        }
    }
}
```

Use this same normalization from `DriveEntry`.

- [ ] **Step 6: Run domain tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DriveSpaceTest,DriveEntryTest,DriveShareTest' test
```

Expected: PASS.

- [ ] **Step 7: Commit domain model**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/drive backend/community-app/src/test/java/com/nowcoder/community/drive/domain
git commit -m "feat: add drive domain model"
```

## Task 3: Persistence Repositories

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveSpaceRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveEntryRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveUploadRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveShareRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveShareAccessRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/DriveSpaceDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/DriveEntryDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/DriveUploadDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/DriveShareDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/dataobject/DriveShareAccessDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/DriveSpaceMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/DriveEntryMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/DriveUploadMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/DriveShareMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/DriveShareAccessMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveSpaceRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveEntryRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveUploadRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveShareRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveShareAccessRepository.java`
- Create: `backend/community-app/src/main/resources/mapper/drive_space_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/drive_entry_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/drive_upload_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/drive_share_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/drive_share_access_mapper.xml`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveRepositoryTest.java`

- [ ] **Step 1: Write the failing repository integration test**

Create `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveRepositoryTest.java`:

```java
package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveEntryType;
import com.nowcoder.community.drive.domain.model.DriveShare;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveShareRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MyBatisDriveRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DriveSpaceRepository spaceRepository;

    @Autowired
    private DriveEntryRepository entryRepository;

    @Autowired
    private DriveShareRepository shareRepository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from drive_share_access");
        jdbcTemplate.update("delete from drive_share");
        jdbcTemplate.update("delete from drive_upload");
        jdbcTemplate.update("delete from drive_entry");
        jdbcTemplate.update("delete from drive_space");
    }

    @Test
    void repositoriesShouldPersistSpaceEntryAndShare() {
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        UUID userId = uuid(7);
        DriveSpace space = DriveSpace.createDefault(uuid(1), userId, now);
        DriveEntry folder = DriveEntry.folder(uuid(2), space.spaceId(), null, "Docs", now);
        DriveEntry file = DriveEntry.file(uuid(3), space.spaceId(), folder.entryId(), "a.txt", uuid(4), uuid(5), 8, "text/plain", now);
        DriveShare share = DriveShare.active(uuid(6), file.entryId(), "token-a", "hash-a", now.plusSeconds(3600), userId, now);

        spaceRepository.save(space);
        entryRepository.save(folder);
        entryRepository.save(file);
        shareRepository.save(share);

        assertThat(spaceRepository.findByUserId(userId)).contains(space);
        assertThat(entryRepository.listActiveChildren(space.spaceId(), folder.entryId())).extracting(DriveEntry::entryId).containsExactly(file.entryId());
        assertThat(entryRepository.findById(space.spaceId(), file.entryId())).contains(file);
        assertThat(shareRepository.findByToken("token-a")).contains(share);
    }

    @Test
    void repositoryShouldListDescendantIdsAndTrashEntries() {
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        UUID spaceId = uuid(1);
        DriveSpace space = DriveSpace.createDefault(spaceId, uuid(7), now);
        DriveEntry folder = DriveEntry.folder(uuid(2), spaceId, null, "Docs", now);
        DriveEntry child = DriveEntry.folder(uuid(3), spaceId, folder.entryId(), "Nested", now);
        DriveEntry file = DriveEntry.file(uuid(4), spaceId, child.entryId(), "a.txt", uuid(5), uuid(6), 8, "text/plain", now);
        spaceRepository.save(space);
        entryRepository.save(folder);
        entryRepository.save(child);
        entryRepository.save(file);

        List<UUID> descendants = entryRepository.listDescendantIds(spaceId, folder.entryId());
        entryRepository.save(folder.trash(now.plusSeconds(1), now.plusSeconds(30 * 86400L)));

        assertThat(descendants).containsExactly(child.entryId(), file.entryId());
        assertThat(entryRepository.findById(spaceId, folder.entryId()).orElseThrow().status()).isEqualTo(DriveEntryStatus.TRASHED);
    }

    @Test
    void rawRowsShouldUseExpectedStatusAndTypeStrings() {
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        UUID spaceId = uuid(1);
        spaceRepository.save(DriveSpace.createDefault(spaceId, uuid(7), now));
        entryRepository.save(DriveEntry.folder(uuid(2), spaceId, null, "Docs", now));

        String type = jdbcTemplate.queryForObject(
                "select type from drive_entry where entry_id = ?",
                String.class,
                BinaryUuidCodec.toBytes(uuid(2))
        );
        String status = jdbcTemplate.queryForObject(
                "select status from drive_entry where entry_id = ?",
                String.class,
                BinaryUuidCodec.toBytes(uuid(2))
        );

        assertThat(type).isEqualTo(DriveEntryType.FOLDER.name());
        assertThat(status).isEqualTo(DriveEntryStatus.ACTIVE.name());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
```

- [ ] **Step 2: Run the repository test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=MyBatisDriveRepositoryTest test
```

Expected: compilation failure because repository interfaces and implementations do not exist.

- [ ] **Step 3: Add repository interfaces**

Create interfaces with these method sets:

```java
public interface DriveSpaceRepository {
    Optional<DriveSpace> findByUserId(UUID userId);
    Optional<DriveSpace> findById(UUID spaceId);
    void save(DriveSpace space);
}
```

```java
public interface DriveEntryRepository {
    Optional<DriveEntry> findById(UUID spaceId, UUID entryId);
    Optional<DriveEntry> findActiveChildByName(UUID spaceId, UUID parentId, String name);
    List<DriveEntry> listActiveChildren(UUID spaceId, UUID parentId);
    List<DriveEntry> listTrash(UUID spaceId);
    List<DriveEntry> searchActive(UUID spaceId, String keyword, int limit);
    List<UUID> listDescendantIds(UUID spaceId, UUID folderId);
    void save(DriveEntry entry);
}
```

```java
public interface DriveUploadRepository {
    Optional<DriveUpload> findById(UUID uploadId);
    void save(DriveUpload upload);
}
```

```java
public interface DriveShareRepository {
    Optional<DriveShare> findById(UUID shareId);
    Optional<DriveShare> findByToken(String shareToken);
    Optional<DriveShare> findActiveByEntryId(UUID entryId);
    void save(DriveShare share);
}
```

```java
public interface DriveShareAccessRepository {
    void record(UUID accessId, UUID shareId, String visitorFingerprint, boolean success, Instant accessedAt);
}
```

- [ ] **Step 4: Add data objects and mappers**

Create one data object per table with JavaBean getters/setters. Each data object must expose `toDomain()` and `fromDomain(...)` helpers. For `DriveEntryDataObject`, include derived H2 columns:

```java
public static DriveEntryDataObject fromDomain(DriveEntry entry) {
    DriveEntryDataObject row = new DriveEntryDataObject();
    row.setEntryId(entry.entryId());
    row.setSpaceId(entry.spaceId());
    row.setParentId(entry.parentId());
    row.setParentKey(entry.parentId() == null ? "" : entry.parentId().toString().replace("-", "").toUpperCase());
    row.setActiveName(entry.status() == DriveEntryStatus.ACTIVE ? entry.name() : null);
    row.setType(entry.type().name());
    row.setName(entry.name());
    row.setObjectId(entry.objectId());
    row.setVersionId(entry.versionId());
    row.setSizeBytes(entry.sizeBytes());
    row.setMimeType(entry.mimeType());
    row.setStatus(entry.status().name());
    row.setTrashedAt(toDate(entry.trashedAt()));
    row.setDeleteAfter(toDate(entry.deleteAfter()));
    row.setCreatedAt(toDate(entry.createdAt()));
    row.setUpdatedAt(toDate(entry.updatedAt()));
    return row;
}
```

Mapper interfaces should be annotated with `@Mapper` and `@Repository`. Use `@Param` for multi-argument methods. XML IDs must match interface method names.

- [ ] **Step 5: Add MyBatis XML**

Create XML mapper files using `jdbcType=BINARY` for UUID columns. `drive_entry_mapper.xml` must include these queries:

```xml
<select id="selectActiveChildByName" resultType="com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveEntryDataObject">
    select <include refid="fields"/>
    from drive_entry
    where space_id = #{spaceId, jdbcType=BINARY}
      and status = 'ACTIVE'
      and name = #{name}
      and (
        (parent_id is null and #{parentId, jdbcType=BINARY} is null)
        or parent_id = #{parentId, jdbcType=BINARY}
      )
</select>

<select id="selectChildren" resultType="com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveEntryDataObject">
    select <include refid="fields"/>
    from drive_entry
    where space_id = #{spaceId, jdbcType=BINARY}
      and status = #{status}
      and (
        (parent_id is null and #{parentId, jdbcType=BINARY} is null)
        or parent_id = #{parentId, jdbcType=BINARY}
      )
    order by type asc, name asc, entry_id asc
</select>

<select id="selectDescendants" resultType="com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveEntryDataObject">
    with recursive tree as (
      select <include refid="fields"/>
      from drive_entry
      where space_id = #{spaceId, jdbcType=BINARY}
        and parent_id = #{entryId, jdbcType=BINARY}
      union all
      select e.entry_id, e.space_id, e.parent_id, e.parent_key, e.active_name, e.type, e.name,
             e.object_id, e.version_id, e.size_bytes, e.mime_type, e.status,
             e.trashed_at, e.delete_after, e.created_at, e.updated_at
      from drive_entry e
      join tree t on e.parent_id = t.entry_id
      where e.space_id = #{spaceId, jdbcType=BINARY}
    )
    select <include refid="fields"/> from tree order by created_at asc, entry_id asc
</select>
```

Use separate `insert` and `update` mapper methods for each table. Repository `save` methods should call `update` first and call `insert` when `update` returns `0`; this works in H2 tests and MySQL production without dialect-specific upsert syntax.

- [ ] **Step 6: Add repository implementations**

Implement `MyBatisDriveSpaceRepository`, `MyBatisDriveEntryRepository`, `MyBatisDriveUploadRepository`, `MyBatisDriveShareRepository`, and `MyBatisDriveShareAccessRepository`. Convert null mapper lists to `List.of()`. Keep all mapper and data object references inside `drive.infrastructure.persistence`.

`MyBatisDriveEntryRepository#listDescendantIds` should map the descendant rows:

```java
@Override
public List<UUID> listDescendantIds(UUID spaceId, UUID folderId) {
    List<DriveEntryDataObject> rows = mapper.selectDescendants(spaceId, folderId);
    if (rows == null || rows.isEmpty()) {
        return List.of();
    }
    return rows.stream()
            .map(DriveEntryDataObject::getEntryId)
            .filter(Objects::nonNull)
            .toList();
}
```

- [ ] **Step 7: Run repository tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='MyBatisDriveRepositoryTest,DriveSchemaResourceTest' test
```

Expected: PASS.

- [ ] **Step 8: Commit persistence**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence backend/community-app/src/main/resources/mapper/drive_*.xml backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence
git commit -m "feat: persist drive state"
```

## Task 4: Upload Flow And OSS Adapter

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/port/DriveObjectStoragePort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/PrepareDriveUploadCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/CompleteDriveUploadCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/DriveUploadContent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/result/DriveUploadSessionResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/result/DriveEntryResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/oss/OssDriveObjectStorageAdapter.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/oss/OssDriveObjectStorageAdapterTest.java`
- Modify: `deploy/mysql/community_oss/010_schema.sql`

- [ ] **Step 1: Write failing upload application tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadApplicationServiceTest.java` with fake repositories and storage port. The key tests are:

```java
@Test
void prepareUploadShouldCreateSpaceWhenMissingAndReturnProviderFreeInstruction() {
    InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
    InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
    InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
    FakeStoragePort storage = new FakeStoragePort();
    DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
    UUID userId = uuid(7);

    DriveUploadSessionResult result = service.prepareUpload(new PrepareDriveUploadCommand(
            userId,
            null,
            "report.pdf",
            "application/pdf",
            1_024L,
            ""
    ));

    assertThat(spaces.findByUserId(userId)).isPresent();
    assertThat(storage.prepared).hasSize(1);
    assertThat(result.upload().url()).isEqualTo("/api/drive/uploads/" + result.uploadId() + "/complete");
    assertThat(result.upload().method()).isEqualTo("POST");
    assertThat(result.upload().fileField()).isEqualTo("file");
    assertThat(result.constraints().maxBytes()).isEqualTo(10_737_418_240L);
}

@Test
void completeUploadShouldProxyToOssCreateEntryAndReserveQuotaOnce() {
    InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
    InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
    InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
    FakeStoragePort storage = new FakeStoragePort();
    DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
    UUID userId = uuid(7);
    DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));

    DriveEntryResult first = service.completeUpload(new CompleteDriveUploadCommand(
            userId,
            UUID.fromString(session.uploadId()),
            new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes(StandardCharsets.UTF_8)), "application/pdf", 1_024L, "")
    ));
    DriveEntryResult second = service.completeUpload(new CompleteDriveUploadCommand(
            userId,
            UUID.fromString(session.uploadId()),
            new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes(StandardCharsets.UTF_8)), "application/pdf", 1_024L, "")
    ));

    assertThat(first.entryId()).isEqualTo(second.entryId());
    assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(1_024L);
    assertThat(storage.completed).hasSize(1);
}

@Test
void prepareUploadShouldRejectQuotaExceededBeforeCallingOss() {
    InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
    InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
    InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
    FakeStoragePort storage = new FakeStoragePort();
    DriveUploadApplicationService service = service(spaces, entries, uploads, storage);

    assertThatThrownBy(() -> service.prepareUpload(new PrepareDriveUploadCommand(
            uuid(7),
            null,
            "too-large.bin",
            "application/octet-stream",
            10_737_418_241L,
            ""
    ))).isInstanceOf(BusinessException.class)
            .hasMessage("网盘容量不足");
    assertThat(storage.prepared).isEmpty();
}
```

Create nested static classes `InMemoryDriveSpaceRepository`, `InMemoryDriveEntryRepository`, `InMemoryDriveUploadRepository`, and `FakeStoragePort` in the test file. Each repository stores rows in `LinkedHashMap<UUID, DomainType>`. `FakeStoragePort` stores `List<PrepareObject> prepared`, `List<CompleteObject> completed`, and `List<UUID> deletedObjects`, then returns fixed UUIDs from `uuid(101)`, `uuid(102)`, and `uuid(103)`.

- [ ] **Step 2: Run upload application tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=DriveUploadApplicationServiceTest test
```

Expected: compilation failure because upload application classes do not exist.

- [ ] **Step 3: Define upload commands, results, and storage port**

Create:

```java
public record PrepareDriveUploadCommand(
        UUID actorUserId,
        UUID parentId,
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256
) {
}
```

```java
public record CompleteDriveUploadCommand(
        UUID actorUserId,
        UUID uploadId,
        DriveUploadContent content
) {
}
```

```java
public record DriveUploadContent(
        UploadStream uploadStream,
        String contentType,
        long contentLength,
        String checksumSha256
) {
    public InputStream openStream() throws IOException {
        InputStream stream = uploadStream.openStream();
        return stream == null ? InputStream.nullInputStream() : stream;
    }

    @FunctionalInterface
    public interface UploadStream {
        InputStream openStream() throws IOException;
    }
}
```

```java
public interface DriveObjectStoragePort {
    PreparedObject prepareUpload(PrepareObject command);
    StoredObject completeUpload(CompleteObject command);
    SignedDownloadUrl createDownloadUrl(UUID objectId, long ttlSeconds);
    void deleteObject(UUID objectId, String actorId);

    record PrepareObject(String usage, String ownerService, String ownerDomain, String ownerType, String ownerId,
                         String visibility, String fileName, String contentType, long contentLength,
                         String checksumSha256, String actorId) {
    }

    record PreparedObject(UUID sessionId, UUID objectId, UUID versionId, Instant expiresAt) {
    }

    record CompleteObject(UUID sessionId, UUID objectId, UUID versionId, String fileName,
                          String contentType, long contentLength, String checksumSha256,
                          DriveUploadContent content) {
    }

    record StoredObject(UUID objectId, UUID versionId, String publicUrl) {
    }

    record SignedDownloadUrl(String url, Instant expiresAt) {
    }
}
```

Create result records:

```java
public record DriveUploadSessionResult(
        String uploadId,
        String fileKey,
        UploadInstruction upload,
        UploadConstraints constraints,
        Instant expiresAt
) {
    public record UploadInstruction(String url, String method, String fileField, Map<String, String> fields, Map<String, String> headers) {
    }

    public record UploadConstraints(long maxBytes, List<String> mimeTypes) {
    }
}
```

```java
public record DriveEntryResult(
        UUID entryId,
        UUID parentId,
        String type,
        String name,
        long sizeBytes,
        String mimeType,
        String status,
        Instant updatedAt
) {
}
```

Use `fileKey` as an opaque drive key such as `drive/<uploadId>/<normalizedFileName>` and include the same value in `upload.fields` under `fileKey` so the completion endpoint can verify the prepared session.

- [ ] **Step 4: Implement `DriveUploadApplicationService`**

Create `DriveUploadApplicationService` as a Spring `@Service`. Constructor dependencies:

```java
DriveSpaceRepository spaceRepository,
DriveEntryRepository entryRepository,
DriveUploadRepository uploadRepository,
DriveObjectStoragePort objectStoragePort,
Clock clock
```

Core rules:

```java
@Transactional
public DriveUploadSessionResult prepareUpload(PrepareDriveUploadCommand command) {
    UUID actorUserId = requireUser(command.actorUserId());
    Instant now = clock.instant();
    DriveSpace space = spaceRepository.findByUserId(actorUserId)
            .orElseGet(() -> DriveSpace.createDefault(UUID.randomUUID(), actorUserId, now));
    validateParent(command.parentId(), space.spaceId());
    String name = entryDomainService.normalizeName(command.fileName());
    rejectDuplicate(space.spaceId(), command.parentId(), name);
    if (command.contentLength() > space.remainingBytes()) {
        throw new BusinessException(DriveErrorCode.DRIVE_QUOTA_EXCEEDED, "网盘容量不足");
    }

    UUID uploadId = UUID.randomUUID();
    String fileKey = "drive/" + uploadId + "/" + name;
    DriveObjectStoragePort.PreparedObject prepared = objectStoragePort.prepareUpload(new DriveObjectStoragePort.PrepareObject(
            "DRIVE_FILE",
            "community-app",
            "drive",
            "drive-upload",
            uploadId.toString(),
            "PRIVATE",
            name,
            normalizeContentType(command.contentType()),
            command.contentLength(),
            normalize(command.checksumSha256()),
            actorUserId.toString()
    ));
    DriveUpload upload = DriveUpload.prepared(uploadId, space.spaceId(), command.parentId(), name, command.contentLength(),
            normalizeContentType(command.contentType()), prepared.objectId(), prepared.versionId(), prepared.sessionId(),
            actorUserId, now, prepared.expiresAt());
    spaceRepository.save(space);
    uploadRepository.save(upload);
    return toUploadSession(upload, space, fileKey);
}
```

`completeUpload` must check upload owner through the loaded space, call `objectStoragePort.completeUpload`, create a file entry, reserve quota, save `DriveUpload.complete(entryId, now)`, and return the existing entry when the upload is already completed.

`toUploadSession` must build the browser instruction with:

```java
new DriveUploadSessionResult.UploadInstruction(
        "/api/drive/uploads/" + upload.uploadId() + "/complete",
        "POST",
        "file",
        Map.of("fileKey", fileKey),
        Map.of()
)
```

- [ ] **Step 5: Add OSS adapter test**

Create `OssDriveObjectStorageAdapterTest` with a mocked `CommunityOssClient`:

```java
@Test
void adapterShouldMapDrivePortToCommunityOssClient() {
    CommunityOssClient client = mock(CommunityOssClient.class);
    UUID sessionId = uuid(1);
    UUID objectId = uuid(2);
    UUID versionId = uuid(3);
    when(client.prepareUpload(any())).thenReturn(new OssUploadSessionResponse(
            sessionId,
            objectId,
            versionId,
            "PROXY",
            "/api/oss/objects/" + objectId + "/complete",
            Instant.parse("2026-05-09T00:15:00Z")
    ));
    when(client.completeProxyUpload(any())).thenReturn(new OssMetadataResponse(
            objectId,
            versionId,
            "DRIVE_FILE",
            "ACTIVE",
            "application/pdf",
            4,
            "http://localhost:12880/files/" + objectId + "/" + versionId + "/report.pdf"
    ));

    OssDriveObjectStorageAdapter adapter = new OssDriveObjectStorageAdapter(client);
    DriveObjectStoragePort.PreparedObject prepared = adapter.prepareUpload(new DriveObjectStoragePort.PrepareObject(
            "DRIVE_FILE",
            "community-app",
            "drive",
            "drive-upload",
            uuid(7).toString(),
            "PRIVATE",
            "report.pdf",
            "application/pdf",
            4,
            "",
            uuid(7).toString()
    ));
    DriveObjectStoragePort.StoredObject stored = adapter.completeUpload(new DriveObjectStoragePort.CompleteObject(
            sessionId,
            objectId,
            versionId,
            "report.pdf",
            "application/pdf",
            4,
            "",
            new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes(StandardCharsets.UTF_8)), "application/pdf", 4, "")
    ));

    assertThat(prepared.objectId()).isEqualTo(objectId);
    assertThat(stored.publicUrl()).contains("/files/");
    verify(client).prepareUpload(any(OssUploadSessionRequest.class));
    verify(client).completeProxyUpload(any(OssCompleteUploadRequest.class));
}
```

- [ ] **Step 6: Implement `OssDriveObjectStorageAdapter`**

Create `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/oss/OssDriveObjectStorageAdapter.java`:

```java
@Component
public class OssDriveObjectStorageAdapter implements DriveObjectStoragePort {

    private final CommunityOssClient ossClient;

    public OssDriveObjectStorageAdapter(CommunityOssClient ossClient) {
        this.ossClient = ossClient;
    }

    @Override
    public PreparedObject prepareUpload(PrepareObject command) {
        OssUploadSessionResponse response = ossClient.prepareUpload(new OssUploadSessionRequest(
                command.usage(),
                command.ownerService(),
                command.ownerDomain(),
                command.ownerType(),
                command.ownerId(),
                command.visibility(),
                command.fileName(),
                command.contentType(),
                command.contentLength(),
                command.checksumSha256(),
                "",
                command.actorId()
        ));
        return new PreparedObject(response.sessionId(), response.objectId(), response.versionId(), response.expiresAt());
    }

    @Override
    public StoredObject completeUpload(CompleteObject command) {
        OssMetadataResponse response = ossClient.completeProxyUpload(new OssCompleteUploadRequest(
                command.sessionId(),
                command.objectId(),
                command.versionId(),
                command.content()::openStream,
                command.fileName(),
                command.contentType(),
                command.contentLength(),
                command.checksumSha256()
        ));
        return new StoredObject(response.objectId(), response.versionId(), response.publicUrl());
    }
}
```

Also implement `createDownloadUrl` through `ossClient.createSignedDownloadUrl` and `deleteObject` through `ossClient.deleteObject`.

- [ ] **Step 7: Seed `DRIVE_FILE` OSS usage policy**

Add this statement near the end of `deploy/mysql/community_oss/010_schema.sql`:

```sql
insert into oss_usage_policy (
  usage, default_visibility, max_bytes, allowed_mime_types, requires_checksum, requires_scan,
  versioning_enabled, download_ttl_seconds, upload_ttl_seconds, public_cache_control,
  private_cache_control, retention_days, delete_grace_days
) values (
  'DRIVE_FILE', 'PRIVATE', 10737418240, '', 0, 0,
  1, 300, 900, '', 'no-store', 0, 7
) on duplicate key update
  default_visibility = values(default_visibility),
  max_bytes = values(max_bytes),
  download_ttl_seconds = values(download_ttl_seconds),
  upload_ttl_seconds = values(upload_ttl_seconds),
  private_cache_control = values(private_cache_control);
```

- [ ] **Step 8: Run upload and adapter tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DriveUploadApplicationServiceTest,OssDriveObjectStorageAdapterTest' test
```

Expected: PASS.

- [ ] **Step 9: Commit upload flow**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/drive/application backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/oss backend/community-app/src/test/java/com/nowcoder/community/drive/application backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/oss deploy/mysql/community_oss/010_schema.sql
git commit -m "feat: add drive upload flow"
```

## Task 5: Entry, Trash, Download, And Share Application Services

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveSpaceApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveEntryApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveTrashApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveShareApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/CreateDriveFolderCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/RenameDriveEntryCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/MoveDriveEntryCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/CreateDriveShareCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/command/VerifyDriveShareCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/result/DriveSpaceResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/result/DriveShareResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/result/DriveDownloadUrlResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/port/DrivePasswordHasher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/port/DriveShareTicketCodec.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/security/BCryptDrivePasswordHasher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/security/HmacDriveShareTicketCodec.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveEntryApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveTrashApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveShareApplicationServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create focused tests:

```java
@Test
void createFolderShouldRejectDuplicateActiveSibling() {
    TestDriveFixture fixture = TestDriveFixture.create();
    DriveEntryApplicationService service = fixture.entryService();
    UUID userId = uuid(7);

    service.createFolder(new CreateDriveFolderCommand(userId, null, "Docs"));

    assertThatThrownBy(() -> service.createFolder(new CreateDriveFolderCommand(userId, null, "Docs")))
            .isInstanceOf(BusinessException.class)
            .hasMessage("同名文件或文件夹已存在");
}
```

```java
@Test
void trashRestoreAndPermanentDeleteShouldControlQuota() {
    TestDriveFixture fixture = TestDriveFixture.create();
    DriveUploadApplicationService uploadService = fixture.uploadService();
    DriveTrashApplicationService trashService = fixture.trashService();
    UUID userId = uuid(7);
    DriveUploadSessionResult session = uploadService.prepareUpload(new PrepareDriveUploadCommand(userId, null, "a.txt", "text/plain", 8, ""));
    DriveEntryResult file = uploadService.completeUpload(new CompleteDriveUploadCommand(userId, UUID.fromString(session.uploadId()), content("abc", 8)));

    trashService.trash(userId, file.entryId());
    assertThat(fixture.space(userId).usedBytes()).isEqualTo(8);
    trashService.restore(userId, file.entryId(), null);
    assertThat(fixture.entry(file.entryId()).status()).isEqualTo(DriveEntryStatus.ACTIVE);
    trashService.trash(userId, file.entryId());
    trashService.deletePermanently(userId, file.entryId());

    assertThat(fixture.space(userId).usedBytes()).isZero();
    assertThat(fixture.storage().deletedObjects).hasSize(1);
}
```

```java
@Test
void shareVerificationShouldRequirePasswordAndExpiry() {
    TestDriveFixture fixture = TestDriveFixture.create();
    DriveShareApplicationService service = fixture.shareService();
    UUID userId = uuid(7);
    UUID entryId = fixture.createFile(userId, "a.txt", 8);
    DriveShareResult share = service.createShare(new CreateDriveShareCommand(
            userId,
            entryId,
            "1234",
            Instant.parse("2026-05-10T00:00:00Z")
    ));

    assertThatThrownBy(() -> service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "bad", "ip:1")))
            .isInstanceOf(BusinessException.class)
            .hasMessage("提取码错误");

    DriveShareResult verified = service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "1234", "ip:1"));
    assertThat(verified.ticket()).isNotBlank();
}
```

Use a `TestDriveFixture` helper in test sources to avoid repeating fake repository setup.

- [ ] **Step 2: Run service tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DriveEntryApplicationServiceTest,DriveTrashApplicationServiceTest,DriveShareApplicationServiceTest' test
```

Expected: compilation failure because services and commands do not exist.

- [ ] **Step 3: Implement entry and space services**

`DriveSpaceApplicationService#getSpace(UUID actorUserId)` must lazily create a default space and return:

```java
public record DriveSpaceResult(UUID spaceId, UUID userId, long quotaBytes, long usedBytes, long remainingBytes) {
}
```

`DriveEntryApplicationService` must implement:

```java
public DriveEntryResult createFolder(CreateDriveFolderCommand command)
public List<DriveEntryResult> listEntries(UUID actorUserId, UUID parentId)
public List<DriveEntryResult> search(UUID actorUserId, String keyword)
public DriveEntryResult rename(RenameDriveEntryCommand command)
public DriveEntryResult move(MoveDriveEntryCommand command)
public DriveDownloadUrlResult createDownloadUrl(UUID actorUserId, UUID entryId)
```

Every method loads the actor's space first and scopes repository calls by `spaceId`.

- [ ] **Step 4: Implement trash service**

`DriveTrashApplicationService` must implement:

```java
public DriveEntryResult trash(UUID actorUserId, UUID entryId)
public DriveEntryResult restore(UUID actorUserId, UUID entryId, UUID targetParentId)
public void deletePermanently(UUID actorUserId, UUID entryId)
public List<DriveEntryResult> listTrash(UUID actorUserId)
```

Trash recursively marks descendants `TRASHED` and leaves quota unchanged. Permanent delete recursively marks entries `DELETED`, releases the sum of file sizes, and calls `DriveObjectStoragePort.deleteObject` once per file object.

- [ ] **Step 5: Implement share ports and service**

Create ports:

```java
public interface DrivePasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String passwordHash);
}
```

```java
public interface DriveShareTicketCodec {
    String issue(String shareToken, Instant expiresAt);
    boolean valid(String shareToken, String ticket, Instant now);
}
```

`DriveShareApplicationService` must implement:

```java
public DriveShareResult createShare(CreateDriveShareCommand command)
public void revokeShare(UUID actorUserId, UUID shareId)
public DriveShareResult loadPublicShare(String shareToken)
public DriveShareResult verifyShare(VerifyDriveShareCommand command)
public DriveDownloadUrlResult createShareDownloadUrl(String shareToken, String ticket, UUID entryId)
```

`verifyShare` records every attempt through `DriveShareAccessRepository`. It returns a short-lived ticket only when share status is active, expiry is in the future, and password matches.

- [ ] **Step 6: Add infrastructure security adapters**

`BCryptDrivePasswordHasher` should wrap `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`.

`HmacDriveShareTicketCodec` should use an application-local secret from configuration when available and fall back to a non-empty dev secret in tests. Ticket payload format:

```text
base64url(shareToken + ":" + expiresAtEpochSecond + ":" + hmacSha256)
```

Use a default ticket TTL of 10 minutes inside `DriveShareApplicationService`.

- [ ] **Step 7: Run service tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DriveEntryApplicationServiceTest,DriveTrashApplicationServiceTest,DriveShareApplicationServiceTest,DriveUploadApplicationServiceTest' test
```

Expected: PASS.

- [ ] **Step 8: Commit application services**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/drive/application backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/security backend/community-app/src/test/java/com/nowcoder/community/drive/application
git commit -m "feat: add drive application services"
```

## Task 6: HTTP Controllers And Backend Boundaries

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DriveController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DrivePublicShareController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/DriveSpaceResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/DriveEntryResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/DriveUploadSessionResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/CreateDriveFolderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/PrepareDriveUploadRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/RenameDriveEntryRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/MoveDriveEntryRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/CreateDriveShareRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/VerifyDriveShareRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/DriveShareResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/dto/DriveDownloadUrlResponse.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/controller/DriveControllerUnitTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/drive/controller/DrivePublicShareControllerUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `DriveControllerUnitTest` using mocked application services:

```java
@Test
void prepareUploadShouldReturnProviderFreeUploadInstruction() throws Exception {
    UUID userId = uuid(7);
    UUID uploadId = uuid(20);
    String fileKey = "drive/" + uploadId + "/report.pdf";
    when(uploadApplicationService.prepareUpload(any())).thenReturn(new DriveUploadSessionResult(
            uploadId.toString(),
            fileKey,
            new DriveUploadSessionResult.UploadInstruction(
                    "/api/drive/uploads/" + uploadId + "/complete",
                    "POST",
                    "file",
                    Map.of("fileKey", fileKey),
                    Map.of()
            ),
            new DriveUploadSessionResult.UploadConstraints(10_737_418_240L, List.of()),
            Instant.parse("2026-05-09T00:15:00Z")
    ));

    mockMvc.perform(post("/api/drive/uploads")
                    .with(jwt().jwt(jwt -> jwt.subject(userId.toString())).authorities(() -> "ROLE_USER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"parentId":null,"fileName":"report.pdf","contentType":"application/pdf","contentLength":1024,"checksumSha256":""}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.fileKey").value(fileKey))
            .andExpect(jsonPath("$.data.upload.url").value("/api/drive/uploads/" + uploadId + "/complete"))
            .andExpect(jsonPath("$.data.upload.fileField").value("file"))
            .andExpect(jsonPath("$.data.upload.fields.fileKey").value(fileKey));
}
```

Create `DrivePublicShareControllerUnitTest`:

```java
@Test
void verifyShareShouldNotRequireAuthentication() throws Exception {
    when(shareApplicationService.verifyShare(any())).thenReturn(new DriveShareResult(
            uuid(1),
            uuid(2),
            "token-a",
            "a.txt",
            "FILE",
            Instant.parse("2026-05-10T00:00:00Z"),
            "ticket-a"
    ));

    mockMvc.perform(post("/api/drive/shares/token-a/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"1234\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ticket").value("ticket-a"));
}
```

- [ ] **Step 2: Run controller tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DriveControllerUnitTest,DrivePublicShareControllerUnitTest' test
```

Expected: compilation failure because controllers and DTOs do not exist.

- [ ] **Step 3: Implement authenticated controller**

`DriveController` routes:

```java
@GetMapping("/api/drive/space")
@GetMapping("/api/drive/entries")
@GetMapping("/api/drive/search")
@PostMapping("/api/drive/folders")
@PostMapping("/api/drive/uploads")
@PostMapping(value = "/api/drive/uploads/{uploadId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PostMapping("/api/drive/entries/{entryId}/rename")
@PostMapping("/api/drive/entries/{entryId}/move")
@PostMapping("/api/drive/entries/{entryId}/trash")
@PostMapping("/api/drive/trash/{entryId}/restore")
@DeleteMapping("/api/drive/trash/{entryId}")
@GetMapping("/api/drive/entries/{entryId}/download-url")
@PostMapping("/api/drive/entries/{entryId}/shares")
@DeleteMapping("/api/drive/shares/{shareId}")
```

The upload completion method must bind `@PathVariable UUID uploadId`, `@RequestParam("fileKey") String fileKey`, and `@RequestParam("file") MultipartFile file`, then pass a `DriveUploadContent` into `DriveUploadApplicationService.completeUpload`. Every authenticated method must call `CurrentUser.requireUserUuid(authentication)` and then a same-domain drive application service.

- [ ] **Step 4: Implement public share controller**

`DrivePublicShareController` routes:

```java
@GetMapping("/api/drive/shares/{shareToken}")
@PostMapping("/api/drive/shares/{shareToken}/verify")
@GetMapping("/api/drive/shares/{shareToken}/download-url")
```

The controller must not call repositories, OSS client, or foreign APIs. Visitor fingerprint can be a stable string derived from request IP and user agent inside the controller, then passed as a command field.

- [ ] **Step 5: Add ArchUnit assertions for drive**

Add a drive-specific rule equivalent to:

```java
@ArchTest
static final ArchRule drive_controllers_should_only_depend_on_drive_application =
        noClasses()
                .that().resideInAnyPackage("..drive.controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..drive.domain..",
                        "..drive.infrastructure..",
                        "..drive.api..",
                        "..drive.contracts..",
                        "..oss.client.."
                )
                .because("drive controllers must enter through same-domain application services only");
```

- [ ] **Step 6: Run controller and architecture tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DriveControllerUnitTest,DrivePublicShareControllerUnitTest,*ArchTest' test
```

Expected: PASS.

- [ ] **Step 7: Commit controllers**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/drive/controller backend/community-app/src/test/java/com/nowcoder/community/drive/controller backend/community-app/src/test/java/com/nowcoder/community/app/arch
git commit -m "feat: expose drive APIs"
```

## Task 7: Frontend API Service And State

**Files:**
- Create: `frontend/src/api/services/driveService.js`
- Create: `frontend/src/api/services/driveService.test.js`
- Create: `frontend/src/views/driveState.js`
- Create: `frontend/src/views/driveState.test.js`

- [ ] **Step 1: Write failing API service tests**

Create `frontend/src/api/services/driveService.test.js`:

```js
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

vi.mock('../uploadSession', () => ({
  executeUploadSession: vi.fn()
}))

import http from '../http'
import { executeUploadSession } from '../uploadSession'
import {
  createDriveFolder,
  createDriveUploadSession,
  getPublicDriveShare,
  listDriveEntries,
  searchDriveEntries,
  uploadDriveFile,
  verifyDriveShare
} from './driveService'

describe('driveService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listDriveEntries should normalize missing data to an array', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: null, traceId: 'trace-1' } })

    const result = await listDriveEntries({ parentId: 'folder-1' })

    expect(http.get).toHaveBeenCalledWith('/api/drive/entries', { params: { parentId: 'folder-1' } })
    expect(result).toEqual({ data: [], traceId: 'trace-1' })
  })

  it('createDriveFolder should post parent and folder name', async () => {
    http.post.mockResolvedValue({ data: { code: 0, data: { entryId: 'folder-2', name: 'Docs' }, traceId: 'trace-folder' } })

    const result = await createDriveFolder({ parentId: 'root', name: 'Docs' })

    expect(http.post).toHaveBeenCalledWith('/api/drive/folders', { parentId: 'root', name: 'Docs' })
    expect(result.data.entryId).toBe('folder-2')
  })

  it('createDriveUploadSession should send file metadata and normalize upload instruction', async () => {
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })
    http.post.mockResolvedValue({
      data: {
        code: 0,
        traceId: 'trace-2',
        data: {
          uploadId: 'upload-1',
          upload: { url: '/api/drive/uploads/upload-1/complete', method: 'POST', fileField: 'file', fields: {}, headers: {} },
          constraints: { maxBytes: 1024, mimeTypes: [] },
          expiresAt: '2026-05-09T00:15:00Z'
        }
      }
    })

    const result = await createDriveUploadSession({ parentId: '', file })

    expect(http.post).toHaveBeenCalledWith('/api/drive/uploads', {
      parentId: '',
      fileName: 'hello.txt',
      contentType: 'text/plain',
      contentLength: 5,
      checksumSha256: ''
    })
    expect(result.data.uploadId).toBe('upload-1')
    expect(result.data.upload.url).toBe('/api/drive/uploads/upload-1/complete')
  })

  it('uploadDriveFile should delegate multipart execution to generic upload helper', async () => {
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })
    executeUploadSession.mockResolvedValue({ data: { entryId: 'entry-1' }, traceId: 'trace-upload' })

    const result = await uploadDriveFile({ session: { upload: { url: '/u', method: 'POST' } }, file })

    expect(executeUploadSession).toHaveBeenCalledWith({ http, session: { upload: { url: '/u', method: 'POST' } }, file, operation: '上传网盘文件' })
    expect(result.data.entryId).toBe('entry-1')
  })

  it('searchDriveEntries should call the search endpoint with the query string', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: [{ entryId: '1' }], traceId: 'trace-search' } })

    const result = await searchDriveEntries({ keyword: 'report' })

    expect(http.get).toHaveBeenCalledWith('/api/drive/search', { params: { q: 'report' } })
    expect(result.data).toHaveLength(1)
  })

  it('getPublicDriveShare should load share metadata without a password', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: { shareToken: 'token-a', name: 'a.txt' }, traceId: 'trace-share-meta' } })

    const result = await getPublicDriveShare('token-a')

    expect(http.get).toHaveBeenCalledWith('/api/drive/shares/token-a')
    expect(result.data.name).toBe('a.txt')
  })

  it('verifyDriveShare should post extraction code to public endpoint', async () => {
    http.post.mockResolvedValue({ data: { code: 0, data: { ticket: 'ticket-a' }, traceId: 'trace-share' } })

    const result = await verifyDriveShare('token-a', '1234')

    expect(http.post).toHaveBeenCalledWith('/api/drive/shares/token-a/verify', { password: '1234' })
    expect(result.data.ticket).toBe('ticket-a')
  })
})
```

- [ ] **Step 2: Run API service tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/api/services/driveService.test.js
```

Expected: FAIL because `driveService.js` does not exist.

- [ ] **Step 3: Implement `driveService.js`**

Create `frontend/src/api/services/driveService.js` with exports:

```js
import http from '../http'
import { unwrapResultBody } from '../result'
import { executeUploadSession, normalizeUploadSession } from '../uploadSession'

export async function getDriveSpace() {
  const resp = await http.get('/api/drive/space')
  const { data, traceId } = unwrapResultBody(resp.data, '获取网盘空间')
  return { data: data || {}, traceId }
}

export async function listDriveEntries({ parentId = '' } = {}) {
  const resp = await http.get('/api/drive/entries', { params: { parentId } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询网盘文件')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createDriveFolder({ parentId = '', name }) {
  const resp = await http.post('/api/drive/folders', { parentId, name: String(name || '') })
  const { data, traceId } = unwrapResultBody(resp.data, '新建文件夹')
  return { data: data || {}, traceId }
}

export async function searchDriveEntries({ keyword = '' } = {}) {
  const resp = await http.get('/api/drive/search', { params: { q: String(keyword || '') } })
  const { data, traceId } = unwrapResultBody(resp.data, '搜索网盘文件')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createDriveUploadSession({ parentId = '', file, checksumSha256 = '' } = {}) {
  const payload = {
    parentId,
    fileName: String(file?.name || ''),
    contentType: String(file?.type || 'application/octet-stream'),
    contentLength: Number(file?.size || 0),
    checksumSha256
  }
  const resp = await http.post('/api/drive/uploads', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建网盘上传会话')
  return { data: normalizeUploadSession(data || {}), traceId }
}

export async function uploadDriveFile({ session, file } = {}) {
  const { data, traceId } = await executeUploadSession({ http, session, file, operation: '上传网盘文件' })
  return { data: data || {}, traceId }
}

export async function renameDriveEntry(entryId, payload) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/rename`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '重命名网盘条目')
  return { data: data || {}, traceId }
}

export async function moveDriveEntry(entryId, payload) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/move`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '移动网盘条目')
  return { data: data || {}, traceId }
}

export async function trashDriveEntry(entryId) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/trash`)
  const { data, traceId } = unwrapResultBody(resp.data, '删除网盘条目')
  return { data: data || {}, traceId }
}

export async function restoreDriveEntry(entryId, payload = {}) {
  const resp = await http.post(`/api/drive/trash/${encodeURIComponent(entryId)}/restore`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '恢复网盘条目')
  return { data: data || {}, traceId }
}

export async function deleteDriveEntryPermanently(entryId) {
  const resp = await http.delete(`/api/drive/trash/${encodeURIComponent(entryId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '彻底删除网盘条目')
  return { data: data || {}, traceId }
}

export async function getDriveDownloadUrl(entryId) {
  const resp = await http.get(`/api/drive/entries/${encodeURIComponent(entryId)}/download-url`)
  const { data, traceId } = unwrapResultBody(resp.data, '获取网盘下载链接')
  return { data: data || {}, traceId }
}

export async function createDriveShare(entryId, payload) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/shares`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建网盘分享')
  return { data: data || {}, traceId }
}

export async function revokeDriveShare(shareId) {
  const resp = await http.delete(`/api/drive/shares/${encodeURIComponent(shareId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '撤销网盘分享')
  return { data: data || {}, traceId }
}

export async function getPublicDriveShare(shareToken) {
  const resp = await http.get(`/api/drive/shares/${encodeURIComponent(shareToken)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '获取公开分享信息')
  return { data: data || {}, traceId }
}

export async function verifyDriveShare(shareToken, password) {
  const resp = await http.post(`/api/drive/shares/${encodeURIComponent(shareToken)}/verify`, { password: String(password || '') })
  const { data, traceId } = unwrapResultBody(resp.data, '校验分享提取码')
  return { data: data || {}, traceId }
}

export async function getDriveShareDownloadUrl(shareToken, ticket, entryId = '') {
  const resp = await http.get(`/api/drive/shares/${encodeURIComponent(shareToken)}/download-url`, {
    params: { ticket, entryId }
  })
  const { data, traceId } = unwrapResultBody(resp.data, '获取分享下载链接')
  return { data: data || {}, traceId }
}
```

- [ ] **Step 4: Write failing pure state tests**

Create `frontend/src/views/driveState.test.js`:

```js
import { describe, expect, it } from 'vitest'
import {
  buildDriveBreadcrumb,
  formatDriveBytes,
  normalizeDriveEntry,
  normalizeDriveQuota,
  reduceDriveSelection,
  validateShareForm
} from './driveState'

describe('driveState', () => {
  it('formatDriveBytes should use binary units for quota display', () => {
    expect(formatDriveBytes(0)).toBe('0 B')
    expect(formatDriveBytes(1024)).toBe('1 KB')
    expect(formatDriveBytes(10737418240)).toBe('10 GB')
  })

  it('normalizeDriveQuota should calculate percentage without exceeding 100', () => {
    expect(normalizeDriveQuota({ quotaBytes: 100, usedBytes: 150 })).toEqual({
      quotaBytes: 100,
      usedBytes: 150,
      remainingBytes: 0,
      usedPercent: 100,
      label: '150 B / 100 B'
    })
  })

  it('buildDriveBreadcrumb should include root and ancestors', () => {
    expect(buildDriveBreadcrumb([
      { entryId: 'a', name: 'Docs' },
      { entryId: 'b', name: 'Reports' }
    ])).toEqual([
      { entryId: '', name: '我的文件' },
      { entryId: 'a', name: 'Docs' },
      { entryId: 'b', name: 'Reports' }
    ])
  })

  it('normalizeDriveEntry should expose booleans for UI actions', () => {
    const file = normalizeDriveEntry({ entryId: '1', type: 'FILE', status: 'ACTIVE', sizeBytes: 8, name: 'a.txt' })
    const trashed = normalizeDriveEntry({ entryId: '2', type: 'FOLDER', status: 'TRASHED', name: 'Old' })

    expect(file.canDownload).toBe(true)
    expect(file.canShare).toBe(true)
    expect(trashed.canShare).toBe(false)
    expect(trashed.canRestore).toBe(true)
  })

  it('validateShareForm should require password and future expiry', () => {
    expect(validateShareForm({ password: '', expiresAt: '2026-05-10T00:00:00Z' }, new Date('2026-05-09T00:00:00Z'))).toEqual({
      valid: false,
      message: '请输入提取码'
    })
    expect(validateShareForm({ password: '1234', expiresAt: '2026-05-08T00:00:00Z' }, new Date('2026-05-09T00:00:00Z'))).toEqual({
      valid: false,
      message: '有效期必须晚于当前时间'
    })
  })

  it('reduceDriveSelection should clear missing selected entry after refresh', () => {
    expect(reduceDriveSelection('2', [{ entryId: '1' }])).toBe('')
    expect(reduceDriveSelection('1', [{ entryId: '1' }])).toBe('1')
  })
})
```

- [ ] **Step 5: Run state tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/views/driveState.test.js
```

Expected: FAIL because `driveState.js` does not exist.

- [ ] **Step 6: Implement `driveState.js`**

Create `frontend/src/views/driveState.js`:

```js
export function formatDriveBytes(value) {
  const bytes = Math.max(0, Number(value || 0))
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let n = bytes / 1024
  let unit = units[0]
  for (let i = 1; i < units.length && n >= 1024; i += 1) {
    n /= 1024
    unit = units[i]
  }
  return `${Number.isInteger(n) ? n : n.toFixed(1)} ${unit}`
}

export function normalizeDriveQuota(raw = {}) {
  const quotaBytes = Number(raw.quotaBytes || 0)
  const usedBytes = Number(raw.usedBytes || 0)
  const remainingBytes = Math.max(0, Number(raw.remainingBytes ?? quotaBytes - usedBytes))
  const usedPercent = quotaBytes > 0 ? Math.min(100, Math.round((usedBytes / quotaBytes) * 100)) : 0
  return {
    quotaBytes,
    usedBytes,
    remainingBytes,
    usedPercent,
    label: `${formatDriveBytes(usedBytes)} / ${formatDriveBytes(quotaBytes)}`
  }
}

export function buildDriveBreadcrumb(ancestors = []) {
  return [
    { entryId: '', name: '我的文件' },
    ...ancestors.map((it) => ({ entryId: String(it.entryId || ''), name: String(it.name || '') }))
  ]
}

export function normalizeDriveEntry(raw = {}) {
  const status = String(raw.status || 'ACTIVE').toUpperCase()
  const type = String(raw.type || 'FILE').toUpperCase()
  const active = status === 'ACTIVE'
  return {
    ...raw,
    entryId: String(raw.entryId || ''),
    parentId: String(raw.parentId || ''),
    name: String(raw.name || ''),
    type,
    status,
    sizeBytes: Number(raw.sizeBytes || 0),
    isFolder: type === 'FOLDER',
    isFile: type === 'FILE',
    canDownload: active && type === 'FILE',
    canShare: active,
    canRename: active,
    canMove: active,
    canTrash: active,
    canRestore: status === 'TRASHED',
    canDeletePermanently: status === 'TRASHED'
  }
}

export function validateShareForm(form = {}, now = new Date()) {
  if (!String(form.password || '').trim()) return { valid: false, message: '请输入提取码' }
  const expires = new Date(form.expiresAt || '')
  if (!Number.isFinite(expires.getTime()) || expires <= now) return { valid: false, message: '有效期必须晚于当前时间' }
  return { valid: true, message: '' }
}

export function reduceDriveSelection(selectedEntryId, entries = []) {
  const selected = String(selectedEntryId || '')
  return entries.some((it) => String(it.entryId || '') === selected) ? selected : ''
}
```

- [ ] **Step 7: Run frontend service and state tests**

Run:

```bash
cd frontend
npm test -- src/api/services/driveService.test.js src/views/driveState.test.js
```

Expected: PASS.

- [ ] **Step 8: Commit frontend API and state**

Run:

```bash
git add frontend/src/api/services/driveService.js frontend/src/api/services/driveService.test.js frontend/src/views/driveState.js frontend/src/views/driveState.test.js
git commit -m "feat: add drive frontend service state"
```

## Task 8: Frontend Views And Navigation

**Files:**
- Create: `frontend/src/views/DriveView.vue`
- Create: `frontend/src/views/DriveShareView.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/navigation.test.js`
- Create: `frontend/src/views/DriveView.test.js`
- Create: `frontend/src/views/DriveShareView.test.js`

- [ ] **Step 1: Write failing navigation test**

In `frontend/src/router/navigation.test.js`, add:

```js
it('getSidebarNavigation should expose drive under personal workspace for authenticated users', () => {
  const authed = getSidebarNavigation({ authed: true, userId: '8', roles: ['ROLE_USER'] })
  expect(authed.find((g) => g.key === 'personal')?.items.map((it) => it.key)).toContain('drive')
})
```

- [ ] **Step 2: Write failing route inventory test**

In `frontend/src/router/index.test.js`, add:

```js
it('should register authenticated drive route and public share route', () => {
  const drive = router.getRoutes().find((r) => r.name === 'drive')
  const share = router.getRoutes().find((r) => r.name === 'driveShare')

  expect(drive?.path).toBe('/drive')
  expect(drive?.meta?.requiresAuth).toBe(true)
  expect(share?.path).toBe('/drive/s/:shareToken')
  expect(share?.meta?.requiresAuth).toBeFalsy()
})
```

- [ ] **Step 3: Run navigation and router tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js src/router/index.test.js
```

Expected: FAIL because drive navigation and routes do not exist.

- [ ] **Step 4: Add routes**

In `frontend/src/router/index.js`, import:

```js
const DriveView = () => import('../views/DriveView.vue')
const DriveShareView = () => import('../views/DriveShareView.vue')
```

Add routes:

```js
{
  path: '/drive',
  name: 'drive',
  component: DriveView,
  meta: { title: '网盘', subtitle: '管理私有文件、分享链接和回收站。', navGroup: 'me', requiresAuth: true }
},
{
  path: '/drive/s/:shareToken',
  name: 'driveShare',
  component: DriveShareView,
  props: true,
  meta: { title: '网盘分享', subtitle: '输入提取码后访问分享文件。', navGroup: 'public' }
}
```

- [ ] **Step 5: Add navigation item**

In `frontend/src/router/navigation.js`, add drive under the `personal` group before `wallet`:

```js
{
  key: 'drive',
  label: '网盘',
  icon: 'bookmark',
  requiresAuth: true,
  to: () => ({ name: 'drive' }),
  activeNames: ['drive']
}
```

- [ ] **Step 6: Write component smoke tests**

Create `DriveView.test.js`:

```js
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DriveView from './DriveView.vue'

vi.mock('../api/services/driveService', () => ({
  getDriveSpace: vi.fn().mockResolvedValue({ data: { quotaBytes: 10737418240, usedBytes: 0, remainingBytes: 10737418240 }, traceId: '' }),
  listDriveEntries: vi.fn().mockResolvedValue({ data: [], traceId: '' })
}))

describe('DriveView', () => {
  it('renders drive workspace actions', async () => {
    const wrapper = mount(DriveView)
    await Promise.resolve()
    await Promise.resolve()

    expect(wrapper.text()).toContain('我的文件')
    expect(wrapper.text()).toContain('新建文件夹')
    expect(wrapper.text()).toContain('上传')
    expect(wrapper.text()).toContain('回收站')
  })
})
```

Create `DriveShareView.test.js`:

```js
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DriveShareView from './DriveShareView.vue'

vi.mock('../api/services/driveService', () => ({
  getPublicDriveShare: vi.fn().mockResolvedValue({ data: { shareToken: 'token-a', name: 'a.txt', type: 'FILE' }, traceId: '' }),
  verifyDriveShare: vi.fn().mockResolvedValue({ data: { ticket: 'ticket-a' }, traceId: '' })
}))

describe('DriveShareView', () => {
  it('renders extraction code form', async () => {
    const wrapper = mount(DriveShareView, { props: { shareToken: 'token-a' } })
    await Promise.resolve()
    await Promise.resolve()

    expect(wrapper.text()).toContain('提取码')
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
  })
})
```

- [ ] **Step 7: Run component tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/views/DriveView.test.js src/views/DriveShareView.test.js
```

Expected: FAIL because views do not exist.

- [ ] **Step 8: Implement `DriveView.vue`**

Create a functional workspace using existing CSS classes. Required visible controls:

```vue
<template>
  <section class="page-surface drive-view">
    <header class="page-header">
      <div>
        <p class="eyebrow">网盘</p>
        <h1>我的文件</h1>
        <p class="muted">{{ quota.label }}</p>
      </div>
      <div class="toolbar-actions">
        <button class="btn secondary" type="button" @click="openCreateFolder">新建文件夹</button>
        <label class="btn primary">
          上传
          <input class="sr-only" type="file" multiple @change="handleUploadChange">
        </label>
      </div>
    </header>

    <div class="drive-layout">
      <nav class="drive-nav" aria-label="网盘视图">
        <button :class="{ active: viewMode === 'files' }" @click="viewMode = 'files'">我的文件</button>
        <button :class="{ active: viewMode === 'shares' }" @click="viewMode = 'shares'">分享管理</button>
        <button :class="{ active: viewMode === 'trash' }" @click="viewMode = 'trash'">回收站</button>
      </nav>

      <main class="drive-main">
        <div class="drive-toolbar">
          <input v-model="keyword" class="input" type="search" placeholder="搜索文件" @keyup.enter="searchEntries">
        </div>
        <div v-if="entries.length === 0" class="empty-state">暂无文件</div>
        <button v-for="entry in entries" :key="entry.entryId" class="drive-row" type="button" @click="selectEntry(entry.entryId)">
          <span>{{ entry.name }}</span>
          <span>{{ entry.isFolder ? '文件夹' : formatDriveBytes(entry.sizeBytes) }}</span>
        </button>
      </main>

      <aside class="drive-detail">
        <template v-if="selectedEntry">
          <h2>{{ selectedEntry.name }}</h2>
          <button v-if="selectedEntry.canDownload" class="btn secondary" type="button" @click="downloadSelected">下载</button>
          <button v-if="selectedEntry.canShare" class="btn secondary" type="button" @click="openShare">分享</button>
          <button v-if="selectedEntry.canTrash" class="btn danger" type="button" @click="trashSelected">删除</button>
        </template>
        <p v-else class="muted">选择一个文件或文件夹查看详情</p>
      </aside>
    </div>
  </section>
</template>
```

Use `driveService` for all API calls. Keep view state local to the component for this first implementation and delegate formatting and normalization to `driveState.js`.

- [ ] **Step 9: Implement `DriveShareView.vue`**

Create a public page with:

```vue
<template>
  <section class="page-surface drive-share-view">
    <header class="page-header">
      <div>
        <p class="eyebrow">网盘分享</p>
        <h1>{{ share.name || '分享文件' }}</h1>
      </div>
    </header>

    <form class="auth-panel" @submit.prevent="verify">
      <label>
        提取码
        <input v-model="password" type="password" autocomplete="off">
      </label>
      <button class="btn primary" type="submit">访问分享</button>
      <p v-if="message" class="form-message">{{ message }}</p>
    </form>

    <div v-if="ticket" class="share-actions">
      <button class="btn secondary" type="button" @click="download">下载</button>
    </div>
  </section>
</template>
```

Load public metadata on mount, call `verifyDriveShare` on submit, and call share download URL API after ticket is present.

- [ ] **Step 10: Run frontend targeted tests**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js src/router/index.test.js src/api/services/driveService.test.js src/views/driveState.test.js src/views/DriveView.test.js src/views/DriveShareView.test.js
```

Expected: PASS.

- [ ] **Step 11: Commit frontend views**

Run:

```bash
git add frontend/src/views/DriveView.vue frontend/src/views/DriveShareView.vue frontend/src/views/DriveView.test.js frontend/src/views/DriveShareView.test.js frontend/src/router/index.js frontend/src/router/navigation.js frontend/src/router/navigation.test.js frontend/src/router/index.test.js
git commit -m "feat: add drive frontend views"
```

## Task 9: Documentation And Final Verification

**Files:**
- Modify: `docs/handbook/business-logic/frontend-surfaces.md`
- Modify: `docs/handbook/business-logic/oss.md`
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`

- [ ] **Step 1: Update frontend surface docs**

In `docs/handbook/business-logic/frontend-surfaces.md`, add rows:

```markdown
| `DriveView.vue` | drive 网盘空间、目录、文件、上传、下载、回收站、分享管理。 |
| `DriveShareView.vue` | drive 公开分享访问、提取码校验、短时下载入口。 |
```

- [ ] **Step 2: Update OSS business docs**

In `docs/handbook/business-logic/oss.md`, add `drive` under current consumers:

```markdown
当前 live consumers：

- `user` avatar：头像业务授权和头像投影由 user 负责，文件对象由 OSS 负责。
- `content` post media：帖子媒体业务引用由 content 负责，文件对象由 OSS 负责。
- `drive` cloud drive：目录、配额、回收站和分享由 drive 负责，文件对象、版本、签名下载和生命周期由 OSS 负责。
```

- [ ] **Step 3: Update architecture docs**

Add this sentence to `docs/handbook/architecture.md` near the existing DDD guardrail description:

```markdown
`drive` follows the same DDD tactical layering guardrails: controllers call same-domain application services, application services depend on drive domain contracts and application ports, and OSS collaboration is hidden behind drive infrastructure adapters.
```

- [ ] **Step 4: Run backend drive tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='*Drive*Test,*ArchTest' test
```

Expected: PASS.

- [ ] **Step 5: Run frontend drive tests**

Run:

```bash
cd frontend
npm test -- src/api/services/driveService.test.js src/views/driveState.test.js src/views/DriveView.test.js src/views/DriveShareView.test.js src/router/navigation.test.js src/router/index.test.js
```

Expected: PASS.

- [ ] **Step 6: Run final repository status check**

Run:

```bash
git status --short
```

Expected: only intentional documentation changes remain before the final docs commit.

- [ ] **Step 7: Commit docs and verification alignment**

Run:

```bash
git add docs/handbook/business-logic/frontend-surfaces.md docs/handbook/business-logic/oss.md docs/handbook/architecture.md docs/handbook/system-design.md
git commit -m "docs: document drive surfaces"
```

Use the modified documentation paths shown by `git status --short` when staging the docs commit.

## Final Verification Checklist

- [ ] `cd backend && mvn -q -pl :community-app -Dtest='*Drive*Test,*ArchTest' test` passes.
- [ ] `cd frontend && npm test -- src/api/services/driveService.test.js src/views/driveState.test.js src/views/DriveView.test.js src/views/DriveShareView.test.js src/router/navigation.test.js src/router/index.test.js` passes.
- [ ] `git status --short` shows no uncommitted changes after the final commit.
- [ ] Manual smoke path works through gateway: login, open `/drive`, create folder, upload file, download file, create share, verify share with password, move file to trash, restore, permanently delete.
