# Community OSS Domain Design

Date: 2026-05-07

## Status

Accepted for planning.

## Context

Current repository state only has a narrow file-storage implementation inside `user` for avatar and `/files/**` access. It already supports local filesystem and an S3-compatible remote backend, but the logic is still user-specific:

- storage policy lives under `user.avatar.*`
- file access is implemented by `user.controller.FilesController`
- upload orchestration is implemented by `user.application.UserAvatarApplicationService`
- provider selection is implemented in `user.infrastructure.avatar`

That shape is not suitable for a full platform object-storage layer. The system needs one owner for all object-like content, with consistent upload, download, permissions, signed URLs, lifecycle, and cleanup semantics.

## Problem Statement

Files are now a cross-cutting platform capability, not a user-only concern.

The platform needs to support:

- public media
- private media
- signed downloads
- reference tracking
- versioning
- lifecycle cleanup
- derived variants
- upload and download policies by usage
- reuse across `user`, `content`, `market`, `wallet`, `ops`, and future `im` attachments

The current per-domain file logic would fragment those rules, duplicate storage policy, and make lifecycle management impossible to reason about.

## Goals

- Create one owner domain for all OSS-style objects inside `backend/community-app`.
- Support upload, download, signed URL issuance, permission checks, and lifecycle management.
- Keep all storage provider details behind infrastructure adapters.
- Make `user`, `content`, `market`, `wallet`, and `ops` consumers of OSS instead of owners of storage behavior.
- Preserve the existing `/files/**` public file entry while moving ownership to the new OSS domain.
- Support both self-hosted object storage and local filesystem dev/test backends.
- Keep strict DDD tactical layering and existing architecture guardrails intact.

## Non-Goals

- Do not split OSS into a separate microservice.
- Do not introduce a new frontend UX just for OSS.
- Do not hardcode business meaning into the storage layer.
- Do not deduplicate blobs globally unless a later implementation decides it is worth the added complexity.
- Do not use `app/query`, `app/command`, or `*UseCase` packages.

## Eligible Content Classes

OSS should own every content class that is file-like, binary, or versioned and is better stored outside the main relational domain tables.

| Domain | Suitable content | Default access shape | Notes |
| --- | --- | --- | --- |
| `user` | avatars, profile covers, profile pictures | public or signed | updateable and versioned |
| `content` | post images, inline media, attachments, previews | public for public posts, signed for restricted content | can generate derived variants |
| `market` | listing images, evidence images, delivery attachments, dispute attachments | public for catalog media, signed for evidence | evidence usually needs retention |
| `wallet` | statements, receipts, export files, audit snapshots | signed | short retention and audit trail |
| `ops` | admin exports, moderation exports, investigation files | signed or internal | admin-only visibility is common |
| `system` | temporary uploads, preview artifacts, transcode intermediates | internal or signed | short-lived and aggressively cleaned |
| future `im` | chat images, voice, video attachments | signed or private | not in the initial code path, but the model should support it |

The rule is simple: if the content is a file, has lifecycle, needs access control, or needs repeatable download semantics, OSS should own it.

## Architecture

OSS is a new owner domain inside `community-app`, not a separate deployable.

```text
Controller / Listener / Handler / Bridge / Enqueuer / Job
  -> OssApplicationService
      -> Oss domain model / service / repository / event
      -> foreign owner-domain api.query / api.action / api.model when other domains need OSS synchronously
      -> contracts.event when other domains need OSS asynchronously
          -> Infrastructure implementation
```

The new domain package should be:

```text
com.nowcoder.community.oss
  controller
  application
    command
    result
  domain
    model
    service
    repository
    event
  infrastructure
    persistence
      mapper
      dataobject
    storage
    event
    job
  api
    query
    action
    model
  contracts
    event
```

Recommended storage backend shape:

- production: MinIO or another S3-compatible object store
- development and tests: local filesystem backend
- the application must not depend on one concrete backend

## Domain Boundaries

OSS owns:

- object registry
- object versions
- upload sessions
- access grants
- aliases and compatibility routes
- reference tracking
- lifecycle transitions
- signed URL generation
- cleanup scheduling
- cache headers and download disposition policy

OSS does not own:

- what a business object means in `user`, `content`, `market`, `wallet`, or `ops`
- post visibility, market order state, wallet state, or moderation state
- which domain wants to store a file reference

The consumer domain owns the business reason to create, retain, or delete a file. OSS owns the file object itself.

## Object Model

### `oss_object`

Logical object record.

Suggested fields:

- `object_id`
- `usage`
- `owner_domain`
- `owner_type`
- `owner_id`
- `visibility`
- `status`
- `current_version_id`
- `latest_file_name`
- `latest_content_type`
- `latest_content_length`
- `latest_checksum_sha256`
- `retention_until`
- `delete_after`
- `legal_hold_until`
- `created_by`
- `created_at`
- `updated_at`

### `oss_object_version`

Immutable physical version record.

Suggested fields:

- `version_id`
- `object_id`
- `version_no`
- `storage_backend`
- `storage_bucket`
- `storage_key`
- `status`
- `file_name`
- `content_type`
- `content_length`
- `checksum_sha256`
- `etag`
- `cache_control`
- `content_disposition`
- `source_object_id` for derived variants
- `variant_type`
- `created_at`
- `activated_at`
- `expired_at`
- `purged_at`

### `oss_upload_session`

Short-lived upload orchestration record.

Suggested fields:

- `session_id`
- `object_id`
- `version_id`
- `upload_mode`
- `owner_domain`
- `owner_type`
- `owner_id`
- `expected_file_name`
- `expected_content_type`
- `expected_content_length`
- `expected_checksum_sha256`
- `status`
- `expires_at`
- `created_by`
- `created_at`
- `completed_at`

### `oss_access_grant`

Object-level access control.

Suggested fields:

- `grant_id`
- `object_id`
- `version_id` nullable
- `principal_type`
- `principal_value`
- `permission`
- `expires_at`
- `created_by`
- `created_at`
- `revoked_at`

### `oss_object_reference`

Reference tracking between OSS objects and owner-domain business facts.

Suggested fields:

- `reference_id`
- `object_id`
- `version_id` nullable
- `subject_domain`
- `subject_type`
- `subject_id`
- `reference_role`
- `status`
- `retain_until`
- `created_at`
- `released_at`

### `oss_usage_policy`

Per-usage policy and operational defaults.

Suggested fields:

- `usage`
- `default_visibility`
- `max_bytes`
- `allowed_mime_types`
- `requires_checksum`
- `requires_scan`
- `versioning_enabled`
- `download_ttl_seconds`
- `upload_ttl_seconds`
- `public_cache_control`
- `private_cache_control`
- `retention_days`
- `delete_grace_days`

### `oss_object_alias`

Compatibility and legacy route mapping.

Suggested fields:

- `alias_key`
- `object_id`
- `version_id`
- `status`
- `expires_at`
- `created_at`

This table is important for keeping legacy URLs and migrated references working while the platform moves from user-local storage paths to canonical OSS object IDs.

## States

### Object states

- `STAGED`
- `UPLOADING`
- `ACTIVE`
- `LOCKED`
- `EXPIRED`
- `DELETE_PENDING`
- `PURGED`

### Upload session states

- `CREATED`
- `READY`
- `UPLOADING`
- `COMPLETED`
- `EXPIRED`
- `CANCELLED`

### Reference states

- `ACTIVE`
- `RELEASED`
- `ORPHANED`

### Access visibility

- `PUBLIC`
- `SIGNED`
- `OWNER`
- `DOMAIN`
- `ROLE`
- `INTERNAL`

The owning domain chooses visibility at object creation time. OSS enforces it.

## Application Services

Recommended application services:

- `ObjectUploadApplicationService`
- `ObjectQueryApplicationService`
- `ObjectAccessApplicationService`
- `ObjectPermissionApplicationService`
- `ObjectReferenceApplicationService`
- `ObjectLifecycleApplicationService`

These services own transaction boundaries, permission checks, upload orchestration, reference binding, signed URL issuance, and lifecycle state changes.

### Command / result examples

Commands:

- `PrepareObjectUploadCommand`
- `CompleteObjectUploadCommand`
- `CreateSignedUrlCommand`
- `GrantObjectAccessCommand`
- `RevokeObjectAccessCommand`
- `BindObjectReferenceCommand`
- `ReleaseObjectReferenceCommand`
- `DeleteObjectCommand`
- `RunObjectLifecycleSweepCommand`

Results:

- `ObjectUploadSessionResult`
- `ObjectMetadataResult`
- `ObjectSignedUrlResult`
- `ObjectAccessDecisionResult`
- `ObjectReferenceResult`
- `ObjectLifecycleResult`

## API Surface

### Synchronous API

`oss.api.query` should answer read or decision questions:

- resolve object metadata
- resolve current public URL
- resolve signed download URL
- check read access
- check delete access
- inspect current lifecycle state

`oss.api.action` should handle state transitions:

- prepare upload session
- complete upload
- bind or release reference
- grant or revoke access
- delete object
- trigger variant creation
- trigger lifecycle transition

`oss.api.model` should carry OSS-specific contract models only. It must not reuse `contracts.event`.

### HTTP routes

Planned browser-facing or public-facing routes:

- `POST /api/oss/objects/upload-sessions`
- `POST /api/oss/objects/{objectId}/complete`
- `GET /api/oss/objects/{objectId}`
- `GET /api/oss/objects/{objectId}/signed-url`
- `POST /api/oss/objects/{objectId}/grants`
- `DELETE /api/oss/objects/{objectId}/grants/{grantId}`
- `DELETE /api/oss/objects/{objectId}`
- `GET /files/**`

`GET /files/**` remains the stable public download route. It should be owned by OSS, not `user`.

Canonical public paths should be version-addressed:

```text
/files/{objectId}/{versionId}/{fileName}
```

The route resolver should use `objectId` and `versionId` as the authority. `fileName` is for readability and `Content-Disposition`; it must not be trusted as the lookup key. Legacy paths such as `/files/avatar/{userId}/{uuid}` should resolve through `oss_object_alias`.

## Upload Flow

Recommended default flow is staged upload with finalization:

1. caller application service asks OSS for an upload session
2. OSS validates usage policy, file size, mime type, and ownership context
3. OSS returns either a presigned direct-upload URL or a proxy upload token, depending on backend capability and policy
4. caller uploads content
5. caller finalizes the upload
6. OSS validates checksum, content length, and storage presence
7. OSS promotes the version to `ACTIVE`
8. OSS emits object lifecycle events and updates reference or public URL metadata when needed

This flow works for both direct browser uploads and server-side proxy uploads.

## Download and Signed URL Flow

Download should support three access modes:

1. `PUBLIC` objects: anonymous `GET /files/**`
2. `SIGNED` objects: anonymous `GET /files/**` with a valid signed token
3. `OWNER` / `DOMAIN` / `ROLE` / `INTERNAL` objects: authenticated access decided by OSS and, when needed, by the owning domain through `oss.api.*`

Signed URL generation should return:

- canonical URL
- HTTP method
- expiry time
- required headers when applicable
- cache semantics

The downloaded response should preserve:

- `Content-Type`
- `Content-Length`
- `ETag`
- `Content-Disposition`
- `Cache-Control`
- `X-Content-Type-Options: nosniff`

For version-addressed public assets, the cache policy can be long-lived and immutable. For private assets, the cache policy should be short-lived or `no-store`.

## Permission Model

Permissions are a combination of visibility and grants.

Rules:

- the owner domain decides the initial visibility
- OSS enforces the visibility on every download or signed URL request
- object references do not automatically imply public visibility
- a delete request is allowed only when lifecycle policy and grants permit it
- a locked or retained object cannot be physically purged until hold conditions clear

Recommended principal types for grants:

- `USER`
- `ROLE`
- `DOMAIN`
- `SERVICE`
- `PUBLIC`

## Lifecycle

Lifecycle is a first-class OSS concern.

Recommended transitions:

```text
STAGED -> UPLOADING -> ACTIVE -> DELETE_PENDING -> PURGED
STAGED -> EXPIRED -> PURGED
ACTIVE -> LOCKED -> ACTIVE
ACTIVE -> EXPIRED
ACTIVE -> DELETE_PENDING
```

Lifecycle rules should support:

- TTL cleanup for temporary objects
- retention windows for evidence and exports
- legal hold for compliance-sensitive objects
- alias cleanup after migration
- version replacement without mutating old blob content

Deletion should be two-step when references exist:

1. mark object or version as delete pending
2. purge physical blob only after reference release and retention expiry

## Variants

Derived files such as thumbnails, previews, web-friendly transcodes, or watermarked copies should be represented as ordinary OSS objects with a `source_object_id` and `variant_type`.

This keeps lifecycle and permission logic uniform:

- source object owns the business meaning
- derived object owns the binary copy
- both can be cached and purged independently

## Storage Backends

The infrastructure layer should hide storage implementation details behind a single adapter interface.

Required backend capabilities:

- put object
- get object
- head object
- delete object
- presign upload
- presign download
- multipart upload support
- stream download

Planned backend implementations:

- `LocalFilesystemObjectStore` for dev and tests
- `S3CompatibleObjectStore` for MinIO or any S3-compatible deployment

The application layer must not know which backend is active.

## Bucket Strategy

Use logical storage classes rather than leaking bucket details into consumer domains.

Recommended classes:

- public
- private
- temporary

The infrastructure adapter may map those classes to one physical bucket or multiple buckets depending on deployment needs.

The domain model should only know the logical class and policy; it should not hardcode provider-specific bucket naming.

## Consumer Integration

### `user`

User avatars and profile images should move to OSS.

Target shape:

- `UserAvatarApplicationService` becomes an OSS consumer
- user keeps avatar business rules and profile updates
- user stores an OSS object reference, not a storage provider implementation
- `user.headerUrl` can remain a derived read field during transition, but the canonical reference should be the OSS object ID

### `content`

Posts, comments, and rich text media should use OSS for:

- inline images
- attachments
- thumbnails
- moderation evidence

### `market`

Market should use OSS for:

- listing images
- dispute evidence
- delivery attachments
- generated preview media

### `wallet`

Wallet should use OSS for:

- statements
- exports
- receipts
- audit snapshots

### `ops`

Ops should use OSS for:

- admin exports
- investigation attachments
- maintenance artifacts

## Events

OSS should publish lifecycle events through `contracts.event` and the existing outbox pattern when downstream consumers need asynchronous knowledge.

Likely event types:

- `ObjectUploaded`
- `ObjectActivated`
- `ObjectReferenceBound`
- `ObjectReferenceReleased`
- `ObjectDeleted`
- `ObjectExpired`
- `ObjectAliasCreated`

Consumers can use these events for cache invalidation, read-model cleanup, or audit trails.

## Migration

The existing avatar chain must be treated as a migration surface, not the final architecture.

Migration order:

1. introduce OSS domain, schema, and storage adapter
2. move `GET /files/**` ownership to OSS
3. move avatar upload and download flows from `user` to OSS
4. backfill legacy avatar objects and aliases
5. migrate content image workflows
6. migrate market media and evidence
7. migrate wallet and ops export files
8. delete the old user-local storage provider classes after the last consumer is moved

Legacy compatibility requirements:

- existing public file URLs should keep working through alias resolution
- old avatar paths should redirect or resolve to canonical OSS objects
- consumer domains should not see storage backend details

## Deployment

The physical object store should be deployable alongside the existing stack.

Expected deploy changes:

- add MinIO or another S3-compatible service to the local topology
- add bucket bootstrap or initialization logic
- add persistent volume mounts for object storage
- add OSS environment variables to `deploy/.env.single.example` and `deploy/.env.cluster.example`
- keep gateway routing for `/files/**`

The deployment model should remain self-hosted friendly and not require a cloud-only provider.

## Testing

Required tests:

- domain unit tests for usage policy, lifecycle, permission, and alias resolution
- application tests for upload finalization, signed URL generation, reference binding, and deletion rules
- controller tests for `/files/**` and the OSS HTTP routes
- storage adapter tests for local filesystem and S3-compatible backends
- migration tests for legacy avatar aliases
- ArchUnit tests for new `oss` package boundaries

The architecture guardrails must be updated to include `oss` in the documented domain lists and package checks.

## Documentation Updates

The spec implementation should also update:

- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md`
- `docs/handbook/data-and-storage.md`
- `docs/handbook/business-logic/README.md`
- a new `docs/handbook/business-logic/oss.md`

These docs must describe OSS as the owner domain for file-like content and clarify the migration away from user-local storage logic.

## Final Shape

The final design is a single owner domain inside `community-app` that manages all file-like content as versioned OSS objects with explicit usage policies, access grants, lifecycle rules, alias compatibility, and pluggable storage backends.

That gives the platform one consistent object model instead of multiple domain-specific storage hacks.
