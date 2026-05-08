# Community OSS Service Design

Date: 2026-05-07

## Status

Accepted for planning.

## Context

Current repository state only has a narrow file-storage implementation inside `community-app` `user` domain for avatar and `/files/**` access. It already supports local filesystem and an S3-compatible remote backend, but the logic is still user-specific and runs inside the main business deployable:

- storage policy lives under `user.avatar.*`
- file access is implemented by `user.controller.FilesController`
- upload orchestration is implemented by `user.application.UserAvatarApplicationService`
- provider selection is implemented in `user.infrastructure.avatar`

That shape is not suitable for a full platform object-storage layer. OSS should be an independent backend service with its own runtime, data schema, storage backend, API surface, permissions, signed URLs, lifecycle, and cleanup semantics.

## Problem Statement

Files are now a cross-cutting platform capability, not a user-only concern and not an implementation detail of `community-app`.

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

The current per-domain file logic would fragment those rules, duplicate storage policy, couple binary I/O to the main business deployable, and make lifecycle management impossible to reason about.

## Goals

- Create an independent `backend/community-oss` service for all OSS-style objects.
- Support upload, download, signed URL issuance, permission checks, and lifecycle management.
- Keep all storage provider details behind infrastructure adapters.
- Make `community-app`, `community-im`, and future services consumers of OSS instead of owners of storage behavior.
- Preserve the existing `/files/**` public file entry while routing it to `community-oss`.
- Route OSS browser APIs through `community-gateway` under `/api/oss/**`.
- Keep `community-app` business domains isolated from OSS persistence and storage provider details.
- Support both self-hosted object storage and local filesystem dev/test backends.
- Keep strict DDD tactical layering inside `community-app`; for `community-oss`, use the same controller/application/domain/infrastructure separation even though it is a separate deployable.

## Non-Goals

- Do not introduce a new frontend UX just for OSS.
- Do not hardcode business meaning into the storage layer.
- Do not deduplicate blobs globally unless a later implementation decides it is worth the added complexity.
- Do not use `app/query`, `app/command`, or `*UseCase` packages.
- Do not let `community-app` or other services read or write OSS tables directly.
- Do not make Garage or any other S3-compatible backend itself the product API; storage backends are infrastructure behind `community-oss`.

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

OSS is a separate Spring Boot deployable in the backend Maven multi-module tree.

```text
Browser / Client
  -> community-gateway
      -> community-oss

community-app / community-im / future services
  -> community-oss HTTP client / internal API
      -> community-oss ApplicationService
          -> community-oss domain model / service / repository / event
          -> community-oss infrastructure storage adapter
              -> Garage (S3-compatible API, first deployment) / Ceph RGW / local filesystem
```

The new backend module should be:

```text
backend/community-oss
  src/main/java/com/nowcoder/community/oss
```

The service package should be:

```text
com.nowcoder.community.oss
  controller
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
    storage
    event
    job
    security
  api
    model
  contracts
    event
```

`community-oss` publishes HTTP APIs and, if useful for Java callers, a small client/contract module:

```text
backend/community-oss-client
  src/main/java/com/nowcoder/community/oss/client
```

The client module must contain DTOs and typed clients only. It must not expose `community-oss` domain, repository, persistence, mapper, or storage classes.

Recommended storage backend shape:

- production: at least three Garage nodes with replicas, health checks, logs, and Prometheus monitoring
- development and tests: Garage single-node or local filesystem backend
- the application layer must not depend on one concrete backend
- `community-app` and `community-im` must not depend on one concrete OSS backend

## Service Boundaries

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
- its own database schema
- its own storage credentials and bucket mapping

OSS does not own:

- what a business object means in `user`, `content`, `market`, `wallet`, or `ops`
- post visibility, market order state, wallet state, or moderation state
- which domain wants to store a file reference
- direct mutation of consumer-domain business tables

The consumer domain owns the business reason to create, retain, or delete a file. OSS owns the file object itself.

Consumer services must interact with OSS through HTTP/internal service APIs from their application layer or infrastructure clients. They must not share OSS database tables or object-store credentials.

## Object Model

### `oss_object`

Logical object record.

Suggested fields:

- `object_id`
- `usage`
- `owner_service`
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
- `owner_service`
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
- `subject_service`
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

Recommended application services inside `community-oss`:

- `ObjectUploadApplicationService`
- `ObjectQueryApplicationService`
- `ObjectAccessApplicationService`
- `ObjectPermissionApplicationService`
- `ObjectReferenceApplicationService`
- `ObjectLifecycleApplicationService`

These services own transaction boundaries, permission checks, upload orchestration, reference binding, signed URL issuance, and lifecycle state changes for the OSS service.

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

### Service API

`community-oss` should expose stable HTTP APIs for browser-facing and service-to-service use. A Java client module may wrap those HTTP APIs for `community-app`, `community-im`, and future Java services.

Query-style APIs should answer read or decision questions:

- resolve object metadata
- resolve current public URL
- resolve signed download URL
- check read access
- check delete access
- inspect current lifecycle state

Action-style APIs should handle state transitions:

- prepare upload session
- complete upload
- bind or release reference
- grant or revoke access
- delete object
- trigger variant creation
- trigger lifecycle transition

The service contract models must be separate from async event contracts. They must not expose domain models, persistence data objects, or storage-provider objects.

### HTTP routes

Planned gateway-facing routes:

- `POST /api/oss/objects/upload-sessions`
- `POST /api/oss/objects/{objectId}/complete`
- `GET /api/oss/objects/{objectId}`
- `GET /api/oss/objects/{objectId}/signed-url`
- `POST /api/oss/objects/{objectId}/grants`
- `DELETE /api/oss/objects/{objectId}/grants/{grantId}`
- `DELETE /api/oss/objects/{objectId}`
- `GET /files/**`

`GET /files/**` remains the stable public download route. Gateway should route it to `community-oss`, not `community-app`.

Canonical public paths should be version-addressed:

```text
/files/{objectId}/{versionId}/{fileName}
```

The route resolver should use `objectId` and `versionId` as the authority. `fileName` is for readability and `Content-Disposition`; it must not be trusted as the lookup key. Legacy paths such as `/files/avatar/{userId}/{uuid}` should resolve through `oss_object_alias`.

### Internal routes

Internal service-to-service routes may live under `/internal/oss/**` when they should not be callable by browsers. They should require internal-scope JWT or equivalent service credentials and should be used for batch reference binding, migration, lifecycle control, and trusted status checks.

## Upload Flow

Recommended default flow is staged upload with finalization:

1. caller application service or browser asks `community-oss` for an upload session through gateway or internal API
2. OSS validates usage policy, file size, mime type, and ownership context
3. OSS returns either a presigned direct-upload URL or a proxy upload token, depending on backend capability and policy
4. caller uploads content
5. caller finalizes the upload
6. OSS validates checksum, content length, and storage presence
7. OSS promotes the version to `ACTIVE`
8. OSS emits object lifecycle events and updates reference or public URL metadata when needed

This flow works for both direct browser uploads and server-side proxy uploads.

For business-owned uploads, the owner service should authorize the business action before asking OSS to prepare the session. OSS should validate the technical policy and the service identity, then record the declared owner context.

## Download and Signed URL Flow

Download should support three access modes:

1. `PUBLIC` objects: anonymous `GET /files/**`
2. `SIGNED` objects: anonymous `GET /files/**` with a valid signed token
3. `OWNER` / `DOMAIN` / `ROLE` / `INTERNAL` objects: authenticated access decided by OSS from stored grants, service identity, JWT claims, or a signed URL previously issued after owner-service authorization

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

OSS should not synchronously call back into `community-app` on every file download. For private business objects, the owner service should either request a signed URL after authorizing the current actor or pre-register explicit grants in OSS.

## Permission Model

Permissions are a combination of visibility and grants.

Rules:

- the owner service decides the initial visibility
- OSS enforces the visibility on every download or signed URL request
- object references do not automatically imply public visibility
- a delete request is allowed only when lifecycle policy and grants permit it
- a locked or retained object cannot be physically purged until hold conditions clear
- internal service calls require service identity, not only end-user JWT identity

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

The `community-oss` infrastructure layer should hide storage implementation details behind a single adapter interface.

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
- `S3CompatibleObjectStore` for Garage or any S3-compatible deployment

The `community-oss` application layer must not know which backend is active. Other backend services must not receive Garage credentials, bucket names, or provider-specific keys. A later Ceph RGW migration should replace only the `ObjectStore` adapter/configuration, not the business API.

## Bucket Strategy

Use logical storage classes rather than leaking bucket details into consumer domains.

Recommended classes:

- public
- private
- temporary

The `community-oss` infrastructure adapter may map those classes to one physical bucket or multiple buckets depending on deployment needs.

The domain model should only know the logical class and policy; it should not hardcode provider-specific bucket naming.

## Consumer Integration

All consumer integration should follow this rule:

```text
consumer controller / listener / handler
  -> consumer ApplicationService
      -> consumer domain rules
      -> community-oss client / HTTP API
```

Controllers, listeners, jobs, and domain models in consumer services must not call OSS directly.

### `user`

User avatars and profile images should move to OSS.

Target shape:

- `UserAvatarApplicationService` authorizes avatar changes and calls the OSS client from the application layer
- user keeps avatar business rules and profile updates
- user stores an OSS object reference or public URL projection, not a storage provider implementation
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

### Future `im`

IM should use OSS for file-like message attachments while keeping message state in `im-core`. IM services should store object references in message records and request signed URLs when rendering attachment payloads.

## Events

OSS should publish lifecycle events through its own `contracts.event` package and a reliable outbox in the OSS service database when downstream consumers need asynchronous knowledge.

Likely event types:

- `ObjectUploaded`
- `ObjectActivated`
- `ObjectReferenceBound`
- `ObjectReferenceReleased`
- `ObjectDeleted`
- `ObjectExpired`
- `ObjectAliasCreated`

Consumers can use these events for cache invalidation, read-model cleanup, or audit trails. Cross-service event delivery should use the repository's established reliable delivery pattern, but the outbox rows belong to `community-oss`, not `community-app`.

## Migration

The existing avatar chain must be treated as a migration surface, not the final architecture.

Migration order:

1. introduce `backend/community-oss`, its database schema, storage adapter, and service security
2. add gateway routes for `/api/oss/**` and `/files/**` to `community-oss`
3. introduce a `community-oss` client for `community-app`
4. move avatar upload and download flows from `user` to `community-oss`
5. backfill legacy avatar objects and aliases
6. migrate content image workflows
7. migrate market media and evidence
8. migrate wallet and ops export files
9. delete the old user-local storage provider classes after the last consumer is moved

Legacy compatibility requirements:

- existing public file URLs should keep working through alias resolution
- old avatar paths should redirect or resolve to canonical OSS objects
- consumer domains should not see storage backend details
- `community-app` should eventually stop mounting the legacy user file volume

## Deployment

`community-oss` and the physical object store should be deployable alongside the existing stack.

Expected deploy changes:

- add `community-oss` as a backend Maven module and Docker-buildable service
- add Garage to the local topology as the OSS blob store
- add bucket bootstrap or initialization logic
- add persistent volume mounts for object storage
- add a separate OSS schema/database initialization path
- add Nacos discovery registration for `community-oss`
- change `community-gateway` routing so `/api/oss/**` and `/files/**` target `community-oss`
- add OSS environment variables to `deploy/.env.single.example` and `deploy/.env.cluster.example`
- add service-to-service auth configuration for `community-app` and future consumers

The deployment model should remain self-hosted friendly and not require a cloud-only provider.

## Testing

Required tests:

- domain unit tests for usage policy, lifecycle, permission, and alias resolution
- application tests for upload finalization, signed URL generation, reference binding, and deletion rules
- controller tests for `/files/**` and the OSS HTTP routes
- storage adapter tests for local filesystem and S3-compatible backends
- migration tests for legacy avatar aliases
- client contract tests between `community-app` and `community-oss`
- gateway route tests for `/api/oss/**` and `/files/**`
- deploy smoke tests for `community-oss` health and Garage connectivity
- ArchUnit tests for `community-app` changes that ensure consumer controllers/listeners/jobs only enter same-domain application services before calling OSS clients

The architecture guardrails must be updated for any new `community-app` client packages and for the removal of user-owned `/files/**`. `oss` should not be added as a `community-app` business domain because it is a separate deployable.

## Documentation Updates

The spec implementation should also update:

- `backend/README.md`
- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md`
- `docs/handbook/data-and-storage.md`
- `docs/handbook/business-logic/README.md`
- a new `docs/handbook/business-logic/oss.md`

These docs must describe `community-oss` as the owner service for file-like content and clarify the migration away from user-local storage logic.

## Final Shape

The final design is an independent `community-oss` service that manages all file-like content as versioned OSS objects with explicit usage policies, access grants, lifecycle rules, alias compatibility, and pluggable storage backends.

That gives the platform one consistent object service instead of multiple domain-specific storage implementations inside unrelated business services.
