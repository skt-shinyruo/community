# Community Drive Design

Date: 2026-05-09

## Status

Design approved in brainstorming. Implementation has not started.

## Context

The repository already has an independent `community-oss` deployable that owns object
metadata, upload sessions, versions, grants, references, public file access, and storage
backend integration. `community-app` consumes it through `community-oss-client` for user
avatars and content media. That boundary should remain intact.

The new requirement is a full user cloud drive:

- every user receives a default 10 GiB drive quota
- files are private by default
- users can create password-protected share links with explicit expiry
- first release includes multi-level folders and recycle bin behavior
- users can upload, download, rename, move, search, share, restore, and permanently delete entries
- frontend should provide a real drive workspace instead of exposing raw OSS behavior

## Goals

- Add a first-class `drive` domain in `backend/community-app`.
- Keep drive business facts in the `community` schema and binary object facts in `community-oss`.
- Enforce per-user ownership, quota, folder tree rules, recycle bin rules, and share link rules.
- Reuse `community-oss-client` for upload sessions, metadata, signed download URLs, and object lifecycle.
- Add a `/drive` frontend workspace in the existing Vue `AppShell`.
- Preserve strict DDD tactical layering and update architecture guardrails where needed.

## Non-Goals

- Do not store file bytes in `community-app`.
- Do not let `drive` read or write OSS tables directly.
- Do not put user drive concepts such as folders, quota, recycle bin, or share extraction codes into `community-oss`.
- Do not make files public by default.
- Do not implement collaborative editing, online document editing, antivirus scanning, global deduplication, or desktop sync in this release.
- Do not introduce a second application entry style such as `UseCase`, `CommandService`, or `FacadeService`.

## Chosen Approach

Use a new `drive` domain in `community-app` and keep OSS as the object storage owner.

```text
DriveController
  -> DriveApplicationService
      -> drive domain model / DomainService / Repository
      -> drive application port
          -> drive infrastructure OSS adapter
              -> community-oss-client
                  -> community-oss
                      -> ObjectStore
```

This keeps product behavior in the owner business domain while reusing the platform storage
service. It also avoids turning `community-oss` into a user-facing file manager.

Rejected alternatives:

- Implement drive directly inside `community-oss`. This would couple generic object storage to user drive rules.
- Start with a flat file list and add folders/trash later. This would make the first schema too weak for the confirmed product scope.

## Backend Package Shape

`backend/community-app/src/main/java/com/nowcoder/community/drive` should follow the repository DDD shape:

```text
com.nowcoder.community.drive
  controller
    dto
  application
    command
    result
    port
  domain
    model
    service
    repository
    event
  infrastructure
    persistence
      mapper
      dataobject
    oss
    event
    job
  api
    query
    action
    model
  contracts
    event
```

Initial same-domain entry points:

- `DriveSpaceApplicationService`
- `DriveEntryApplicationService`
- `DriveUploadApplicationService`
- `DriveShareApplicationService`
- `DriveTrashApplicationService`

Controllers must call only same-domain `*ApplicationService` classes. Application services may use drive domain repositories and drive-owned application ports. The infrastructure OSS adapter implements the drive port through `community-oss-client`. Domain code must remain plain Java and must not depend on Spring, HTTP DTOs, OSS client DTOs, mapper types, or data objects.

## Data Model

Drive data belongs in the `community` schema.

### `drive_space`

One logical drive per user.

- `space_id binary(16)` primary key
- `user_id binary(16)` unique, not null
- `quota_bytes bigint` not null, default `10737418240`
- `used_bytes bigint` not null, default `0`
- `reserved_bytes bigint` not null, default `0`
- `created_at timestamp`
- `updated_at timestamp`

Rules:

- Create lazily on first authenticated drive access.
- Default quota is exactly 10 GiB, or `10 * 1024 * 1024 * 1024` bytes.
- `used_bytes` counts active and trashed files until permanent deletion.
- `reserved_bytes` holds in-progress upload completion reservations; available quota is `quota_bytes - used_bytes - reserved_bytes`.

### `drive_entry`

Represents both folders and files.

- `entry_id binary(16)` primary key
- `space_id binary(16)` not null
- `parent_id binary(16)` nullable for root children
- `type varchar(16)` with `FOLDER` or `FILE`
- `name varchar(255)` not null
- `object_id binary(16)` nullable; set only for files
- `version_id binary(16)` nullable; set only for files
- `size_bytes bigint` not null, default `0`
- `mime_type varchar(128)` not null, default empty string
- `status varchar(16)` with `ACTIVE`, `TRASHED`, or `DELETED`
- `trashed_at timestamp` nullable
- `delete_after timestamp` nullable
- `created_at timestamp`
- `updated_at timestamp`

Rules:

- Active sibling names are unique within the same parent.
- A folder cannot be moved into itself or any descendant.
- Trashed entries cannot be renamed, moved, shared, or downloaded until restored.
- Permanent deletion of a folder recursively deletes its descendants.

### `drive_upload`

Tracks an in-flight upload and makes completion idempotent.

- `upload_id binary(16)` primary key
- `space_id binary(16)` not null
- `parent_id binary(16)` nullable
- `name varchar(255)` not null
- `size_bytes bigint` not null
- `mime_type varchar(128)` not null
- `object_id binary(16)` not null
- `version_id binary(16)` not null
- `oss_session_id binary(16)` not null
- `status varchar(16)` with `PREPARED`, `COMPLETING`, `OBJECT_COMPLETED`, `COMPLETED`, `FAILED`, or `EXPIRED`
- `created_by binary(16)` not null
- `created_at timestamp`
- `expires_at timestamp` not null
- `completed_entry_id binary(16)` nullable

Rules:

- Prepare upload validates parent folder and quota before asking OSS for a session.
- Complete upload claims capacity into `reserved_bytes`, completes OSS outside the DB transaction, then creates exactly one file entry and moves the reservation into `used_bytes`.
- Repeated completion for the same `upload_id` returns the original result.
- If OSS succeeds but local finalization fails, retry or recovery finalizes from `OBJECT_COMPLETED` without re-uploading the object.

### `drive_share`

Represents password-protected share links.

- `share_id binary(16)` primary key
- `entry_id binary(16)` not null
- `share_token varchar(96)` unique, not null
- `password_hash varchar(255)` not null
- `expires_at timestamp` not null
- `status varchar(16)` with `ACTIVE`, `EXPIRED`, or `REVOKED`
- `created_by binary(16)` not null
- `created_at timestamp`
- `updated_at timestamp`

Rules:

- Shares require a password and expiry.
- A share never exposes the OSS object ID, version ID, or raw download URL before password verification.
- Revoking a share invalidates future verification and download URL issuance.

### `drive_share_access`

Audit and rate-limit support for share verification attempts.

- `access_id binary(16)` primary key
- `share_id binary(16)` not null
- `visitor_fingerprint varchar(128)` not null, default empty string
- `success tinyint(1)` not null
- `accessed_at timestamp` not null

## OSS Usage

Drive uploads should use a dedicated OSS usage such as `DRIVE_FILE`.

Default OSS prepare values:

- `usage`: `DRIVE_FILE`
- `ownerService`: `community-app`
- `ownerDomain`: `drive`
- `ownerType`: `drive-entry` or `drive-upload`
- `ownerId`: the drive upload ID until the drive entry exists
- `visibility`: `PRIVATE` or `INTERNAL`
- `fileName`: normalized drive file name
- `contentType`: browser-provided content type normalized by application rules
- `contentLength`: file size

The implementation should seed or provision an OSS usage policy for `DRIVE_FILE` with a per-file max that does not exceed the drive quota. The drive domain still performs the user quota check because OSS usage policy is not user-specific.

## API Design

Authenticated user APIs:

- `GET /api/drive/space`
- `GET /api/drive/entries?parentId=`
- `GET /api/drive/search?q=`
- `POST /api/drive/folders`
- `POST /api/drive/uploads`
- `POST /api/drive/uploads/{uploadId}/complete`
- `POST /api/drive/entries/{entryId}/rename`
- `POST /api/drive/entries/{entryId}/move`
- `POST /api/drive/entries/{entryId}/trash`
- `POST /api/drive/trash/{entryId}/restore`
- `DELETE /api/drive/trash/{entryId}`
- `GET /api/drive/entries/{entryId}/download-url`
- `POST /api/drive/entries/{entryId}/shares`
- `DELETE /api/drive/shares/{shareId}`

Public share APIs:

- `GET /api/drive/shares/{shareToken}`
- `POST /api/drive/shares/{shareToken}/verify`
- `GET /api/drive/shares/{shareToken}/download-url?ticket=`

API response models should be drive-specific result DTOs at the application boundary and controller response DTOs at the HTTP boundary. Application command/result classes must not expose `MultipartFile`, `ResponseEntity`, servlet request/response objects, or other Spring Web transport types.

## Core Flows

### First Access

1. Controller extracts authenticated user ID.
2. `DriveSpaceApplicationService` loads the user's space.
3. If missing, application creates `drive_space` with 10 GiB quota.
4. Application returns quota and root-level summary.

### Folder Creation

1. Validate authenticated owner.
2. Load or create drive space.
3. Validate parent folder is active and owned by the same space.
4. Normalize and validate name.
5. Reject active sibling name conflicts.
6. Save `drive_entry` with type `FOLDER`.

### Upload

1. User requests an upload session with target parent, name, size, and content type.
2. Application validates ownership, active folder, name conflict, and remaining quota.
3. Application calls OSS prepare upload through a drive-owned port backed by `community-oss-client`.
4. Application saves `drive_upload` with OSS object/session identifiers.
5. Frontend executes the returned upload instruction.
6. Frontend calls drive complete.
7. Application atomically claims completion, writes the upload size into `reserved_bytes`, and changes the upload to `COMPLETING`.
8. Application completes OSS outside the DB transaction.
9. Application records `OBJECT_COMPLETED`, creates `drive_entry` type `FILE`, moves the reservation into `used_bytes`, and marks the upload `COMPLETED`.
10. A recovery job scans stale `COMPLETING/OBJECT_COMPLETED` rows and either finalizes confirmed OSS objects or releases reservations for confirmed failed uploads.

### Download

1. User requests download URL for an active file entry.
2. Application validates ownership and status.
3. Application calls OSS for a short-lived signed URL.
4. Controller returns the URL and expiry.

### Trash And Restore

1. Trash marks an entry and descendants as `TRASHED`; quota is unchanged.
2. Restore validates target parent and name conflicts.
3. Permanent delete marks entries `DELETED`, releases file sizes from quota, and requests OSS lifecycle deletion for file objects.

### Share Link

1. User creates a share for an active file or folder with password and expiry.
2. Application stores a generated token and password hash.
3. Public visitor loads share metadata by token.
4. Visitor submits password.
5. Application records access attempt, validates status and expiry, and returns a short-lived share ticket.
6. Visitor exchanges the ticket for a short-lived download URL or file list view.

Folder shares expose a drive-controlled listing and issue download URLs per file after the ticket is validated.

## Frontend Design

Add `frontend/src/views/DriveView.vue` and pure state helpers in `frontend/src/views/driveState.js`. Add `frontend/src/api/services/drive.js` for HTTP calls.

Route:

- `/drive`
- requires auth
- `navGroup: 'me'`
- title: `网盘`

Workspace structure:

- left navigation: `我的文件`, `分享管理`, `回收站`, quota usage
- top toolbar: search, new folder, upload, view mode switch
- main region: current folder breadcrumb and file table/grid
- right panel: selected entry details, share controls, rename, move, download, trash
- trash view: restore and permanent delete actions
- public share page: token metadata, extraction code input, expiry state, download or preview entry point

The first frontend implementation should use the existing restrained product shell and UI primitives. It should avoid decorative landing-page layout and should behave like an operational file manager.

## Security And Privacy

- Drive entries are private by default.
- Authenticated APIs must scope every lookup by `user_id` or `space_id`.
- Public share APIs must not reveal whether an internal object ID exists.
- Password hashes must be stored, never plaintext extraction codes.
- Share tickets must be short-lived and tied to the share token.
- Download URLs must be short-lived and generated on demand.
- Failed password attempts must be logged in `drive_share_access`.
- Recycle bin entries are inaccessible through normal file and share operations.

## Error Handling

Use business errors with stable meanings:

- quota exceeded
- entry not found
- parent folder not found
- duplicate entry name
- invalid folder move
- entry is trashed
- share expired
- share revoked
- invalid share password
- upload session expired
- upload already completed
- OSS session failed

OSS prepare failures must not create a drive file entry or increment quota. OSS complete uncertainty keeps the upload recoverable instead of deleting the reservation eagerly; `used_bytes` only changes when the drive entry is committed. Permanent deletion should release drive quota only after the drive state transition succeeds; OSS lifecycle deletion can be retried by infrastructure job if the synchronous call fails after the drive record is already marked deleted.

## Testing Strategy

Backend tests:

- domain tests for name validation, quota accounting, folder move rules, trash/restore rules, and share expiry/password rules
- application tests for first access lazy space creation, upload prepare, idempotent upload complete, download URL authorization, share verification, and recursive permanent delete
- controller tests for HTTP binding and authenticated-user extraction
- MyBatis repository tests for `drive_space`, `drive_entry`, `drive_upload`, `drive_share`, and `drive_share_access`
- ArchUnit tests ensuring drive controllers only call same-domain application services and drive application code does not depend on mapper/dataobject or HTTP transport types

Frontend tests:

- API service tests for request and response normalization
- `driveState` tests for breadcrumb, selection, upload progress, quota formatting, trash state, share form state, and conflict/error rendering
- route/navigation tests for authenticated `/drive` access if the router guard requires a new case

Verification commands for implementation:

```bash
cd backend
mvn test -pl :community-app -Dtest='*Drive*Test,*ArchTest'
```

```bash
cd frontend
npm test -- src/api/services/drive.test.js src/views/driveState.test.js
```

## Documentation Updates During Implementation

When implementing, update:

- `docs/handbook/business-logic/frontend-surfaces.md`
- `docs/handbook/business-logic/oss.md` to list `drive` as an OSS consumer
- `docs/handbook/architecture.md` if new ArchUnit rules are added
- `docs/handbook/system-design.md` if the deployment or gateway routes change

## Rollout Plan

1. Add backend schema and domain/application tests.
2. Implement backend drive domain, repositories, OSS adapter, and controllers.
3. Add OSS `DRIVE_FILE` usage policy provisioning.
4. Add frontend API service and drive state tests.
5. Implement `/drive` workspace and public share page.
6. Run backend targeted tests, ArchUnit tests, and frontend targeted tests.

## Scope Check

This is a single feature area but spans backend domain, OSS collaboration, schema, frontend API, and UI. It should be implemented from a detailed plan with checkpoints rather than as one unstructured patch.
