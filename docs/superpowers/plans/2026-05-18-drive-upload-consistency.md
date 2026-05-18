# Drive Upload Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make drive upload completion resilient when OSS succeeds but local DB finalization fails, while keeping high-concurrency paths short and idempotent.

**Architecture:** `DriveUploadApplicationService` splits completion into short DB claim/finalization transactions around transaction-free OSS completion. `DriveUpload` becomes a small state machine with `COMPLETING`, `OBJECT_COMPLETED`, `COMPLETED`, and `FAILED` states; retry paths can finalize or clean up based on persisted state. A drive outbox handler/job can later call the same application service recovery method without crossing DDD boundaries.

**Tech Stack:** Java 17, Spring transaction templates, MyBatis mapper CAS updates, existing `DriveObjectStoragePort`, existing `common-outbox` handler pattern, JUnit 5/AssertJ tests.

---

### Task 1: Domain State Machine

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUploadStatus.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/model/DriveUpload.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/drive/domain/model/DriveUploadTest.java`

- [x] Add statuses `COMPLETING`, `OBJECT_COMPLETED`, and `FAILED`.
- [x] Add domain methods `startCompleting(UUID entryId, Instant now)`, `markObjectCompleted(Instant now)`, `completeFinalization(Instant now)`, and `failCompletion(Instant now)`.
- [x] Keep existing `complete(UUID, Instant)` behavior compatible for expired uploads and completed uploads.
- [x] Add tests proving entry id is generated before OSS, object completion preserves the entry id, and final completion is idempotent.

### Task 2: Repository CAS And Recovery Queries

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/repository/DriveUploadRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveUploadRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/persistence/mapper/DriveUploadMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/drive_upload_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/drive/infrastructure/persistence/MyBatisDriveRepositoryTest.java`
- Test schema: `backend/community-app/src/test/resources/schema.sql`

- [x] Add repository methods for atomic status transitions from expected statuses.
- [x] Add query for stale uploads in recovery states, backed by an index on `(status, updated_at, upload_id)`.
- [x] Verify CAS prevents two concurrent completion requests from both owning the same upload.

### Task 3: Application Completion Flow

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadApplicationServiceTest.java`

- [x] Add failing tests for “OSS succeeds then entry save fails”: upload must remain recoverable as `OBJECT_COMPLETED`, quota must stay reserved until finalization, and delete should not be the only recovery mechanism.
- [x] Change `completeUpload()` to short-transaction claim, transaction-free OSS complete, and short-transaction finalization.
- [x] Make retries idempotent: `COMPLETING` retries return processing/invalid without repeating OSS; `OBJECT_COMPLETED` retries finalize from DB; `COMPLETED` returns existing entry.
- [x] Reserve quota during claim and move it to used bytes only in finalization.

### Task 4: Recovery Adapter

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadRecoveryApplicationService.java` or add recovery method to `DriveUploadApplicationService` if smaller.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/event/DriveUploadRecoveryOutboxHandler.java` if outbox integration is needed now.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadRecoveryApplicationServiceTest.java`

- [x] Add an application entry for finalizing `OBJECT_COMPLETED` uploads and cleaning failed stale uploads.
- [x] Keep any outbox handler in `drive.infrastructure.event` and make it call only the drive application service.
- [x] Make recovery idempotent and bounded by repository batch limits.

### Task 5: Documentation And Verification

**Files:**
- Modify: `docs/handbook/business-logic/drive.md`

- [x] Update the documented upload flow to describe short DB claim, OSS completion, finalization, and recovery.
- [x] Run focused tests with `-Djdk.attach.allowAttachSelf=true` if needed for this environment.
- [x] Run drive architecture tests if package boundaries changed.
