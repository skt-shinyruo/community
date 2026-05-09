# Community Block Post Media Publishing Design

Date: 2026-05-09

## Status

Accepted for planning.

The user chose the block editor direction for posts and explicitly confirmed that this project has no deployed environment and no historical data to preserve. This design intentionally contains no legacy compatibility path, no migration fallback, and no old text/Markdown post rendering mode.

## Context

Current post publishing is text-only: `POST /api/posts` accepts title, content, optional category, and tags. Post detail returns a `content` string and the frontend renders it through `UiMarkdown`.

The target product experience is direct post composition with images, videos, and file attachments. The chosen UX is a block editor: a post body is an ordered list of content blocks, not a Markdown string with media bolted on.

The repository already has an independent `community-oss` service and `community-oss-client`. Content should use OSS through content application/infrastructure boundaries, never by touching OSS persistence or storage provider details directly.

## Goals

- Replace post body text with structured post content blocks.
- Support creating posts with paragraph, image, video, file, and code blocks.
- Upload image, video, and file assets immediately while composing a post.
- Bind draft media assets to the post when the post is published.
- Render post details from blocks.
- Model video processing status and playback metadata.
- Keep the first implementation free of real FFmpeg/transcoding; the worker is a placeholder with explicit states.
- Preserve strict DDD tactical layering in `backend/community-app`.

## Non-Goals

- No compatibility with old `discuss_post.content` post bodies.
- No old Markdown post renderer for posts.
- No historical data migration.
- No deployed-data preservation.
- No real video transcoding pipeline in this scope.
- No media support for comments in this scope.
- No direct browser calls to storage-provider-specific APIs.
- No direct controller/listener/job calls to OSS client APIs.

## Product Shape

The post editor is a block editor.

Supported first-scope blocks:

- `paragraph`: rich/plain text paragraph.
- `image`: uploaded image with optional caption.
- `video`: uploaded video with processing status, optional poster, and eventual playback sources.
- `file`: downloadable attachment with display name.
- `code`: code block with optional language.

Users can add, remove, reorder, and edit blocks. Image, video, and file blocks start upload immediately after file selection. Publish is allowed only when all referenced assets are uploaded and owned by the current user. Video transcoding does not block publish; videos can appear as `PENDING_TRANSCODE` in the published post.

## Backend Architecture

### Layering

Inbound HTTP adapters remain thin:

```text
content.controller
  -> content.application.*ApplicationService
      -> content.domain model/service/repository
      -> content application-owned media storage port
          -> content.infrastructure OSS adapter
              -> community-oss-client
```

Controllers must not call the OSS client, content repositories, domain services, mappers, or data objects directly.

The content application layer owns:

- use-case orchestration
- transactions
- idempotency
- media asset ownership checks
- block validation
- post/media binding
- foreign OSS collaboration through an application-owned port
- domain event publication

The content domain owns:

- post aggregate rules
- block ordering and supported block types
- media asset lifecycle rules
- video processing state transitions

Infrastructure owns:

- MyBatis mappers/data objects
- OSS client adapter implementation
- media upload proxy implementation
- future outbox/job adapters for video processing

### Application Services

New or changed application services:

- `PostPublishingApplicationService`
  - accepts `CreatePostCommand` with `List<PostContentBlockCommand>` instead of `content`
  - creates post metadata
  - saves ordered blocks
  - binds referenced media assets
  - emits post domain events

- `PostMediaApplicationService`
  - prepares upload sessions for post media
  - completes proxy uploads
  - marks assets uploaded
  - releases draft assets

- `PostReadApplicationService`
  - returns post details with structured blocks
  - returns summaries with a generated text preview from blocks

- Future `VideoProcessingApplicationService`
  - claims pending video assets
  - updates processing status
  - records poster/playback variants
  - initially implemented as a placeholder/no-op flow

### Domain Model

Core content concepts:

- `Post`
  - id, author, title, category, status, type, timestamps, counters, score

- `PostContentBlock`
  - id, postId, blockIndex, type
  - text fields for paragraph/code/caption/displayName
  - optional media asset id
  - metadata value object for type-specific data

- `PostMediaAsset`
  - id, ownerUserId, postId nullable until bound
  - OSS object identity
  - file name, content type, content length
  - media kind: `IMAGE`, `VIDEO`, `FILE`
  - lifecycle: `DRAFT`, `UPLOADED`, `BOUND`, `RELEASED`, `DELETED`
  - video state: `NONE`, `PENDING_TRANSCODE`, `PROCESSING`, `READY`, `FAILED`

- `PostMediaVariant`
  - future derived media object for poster images and playback renditions
  - variant type, dimensions, duration, content type, OSS object identity

Domain rules:

- A post must have a non-empty title.
- A post must have at least one meaningful block.
- Blocks have contiguous ordering starting from zero.
- Media blocks must reference assets of the matching kind.
- Draft media can only be bound by its owner.
- A draft asset can be bound to one post only.
- File blocks are first-class blocks, not a separate legacy attachment list.
- Video assets start in `PENDING_TRANSCODE` after binding unless already marked failed.

## Database Design

Because no compatibility is required, the schema should be reshaped directly.

`discuss_post` should no longer store post body content:

```sql
create table if not exists discuss_post (
  id binary(16) primary key,
  user_id binary(16) not null,
  category_id binary(16) default null,
  title varchar(255) not null,
  type int default 0,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  comment_count int default 0,
  score double default 0
);
```

`post_content_block` stores the ordered body:

```sql
create table if not exists post_content_block (
  id binary(16) primary key,
  post_id binary(16) not null,
  block_index int not null,
  block_type varchar(32) not null,
  text_content text null,
  language varchar(64) default null,
  media_asset_id binary(16) default null,
  caption varchar(512) default '',
  display_name varchar(255) default '',
  metadata_json json default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  unique key uk_post_block_index (post_id, block_index),
  key idx_post_content_block_post (post_id),
  key idx_post_content_block_media (media_asset_id)
);
```

`post_media_asset` stores upload and binding state:

```sql
create table if not exists post_media_asset (
  id binary(16) primary key,
  owner_user_id binary(16) not null,
  post_id binary(16) default null,
  oss_object_id binary(16) not null,
  oss_version_id binary(16) default null,
  upload_session_id binary(16) default null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content_length bigint not null,
  media_kind varchar(32) not null,
  lifecycle varchar(32) not null,
  video_state varchar(32) not null default 'NONE',
  failure_reason varchar(512) default '',
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  key idx_post_media_asset_owner_lifecycle (owner_user_id, lifecycle),
  key idx_post_media_asset_post (post_id),
  key idx_post_media_asset_video_state (video_state)
);
```

`post_media_variant` is included for the video processing model even if the initial worker is a placeholder:

```sql
create table if not exists post_media_variant (
  id binary(16) primary key,
  asset_id binary(16) not null,
  variant_type varchar(32) not null,
  oss_object_id binary(16) not null,
  oss_version_id binary(16) default null,
  content_type varchar(128) not null,
  width int default null,
  height int default null,
  duration_millis bigint default null,
  create_time timestamp null default current_timestamp,
  unique key uk_post_media_variant_type (asset_id, variant_type),
  key idx_post_media_variant_asset (asset_id)
);
```

## HTTP API

### Prepare Media Upload

```http
POST /api/posts/media/upload-sessions
```

Request:

```json
{
  "fileName": "reproduction.mp4",
  "contentType": "video/mp4",
  "contentLength": 10485760,
  "mediaKind": "VIDEO",
  "checksumSha256": ""
}
```

Response:

```json
{
  "assetId": "...",
  "uploadId": "...",
  "upload": {
    "url": "/api/posts/media/{assetId}/upload",
    "method": "POST",
    "fileField": "file",
    "fields": {
      "uploadId": "..."
    },
    "headers": {}
  },
  "constraints": {
    "maxBytes": 104857600,
    "mimeTypes": ["image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm", "application/pdf", "application/zip"]
  },
  "expiresAt": "..."
}
```

### Complete Media Upload

```http
POST /api/posts/media/{assetId}/upload
Content-Type: multipart/form-data
```

The controller adapts the multipart input and calls `PostMediaApplicationService`. The application service completes the OSS upload through a content-owned storage port and marks the asset `UPLOADED`.

### Create Post

```http
POST /api/posts
```

Request:

```json
{
  "title": "一次排查 Redis 延迟尖刺的过程",
  "categoryId": "...",
  "tags": ["Redis", "排障"],
  "blocks": [
    {
      "type": "paragraph",
      "text": "线上 Redis 在 14:20 后出现延迟尖刺。"
    },
    {
      "type": "image",
      "assetId": "...",
      "caption": "延迟曲线"
    },
    {
      "type": "video",
      "assetId": "...",
      "caption": "复现步骤"
    },
    {
      "type": "file",
      "assetId": "...",
      "displayName": "logs.zip"
    }
  ]
}
```

There is no `content` field.

### Update Post

```http
PUT /api/posts/{postId}
```

Uses the same block request shape as create. The application service replaces the ordered block set, binds newly referenced draft assets, and releases assets no longer referenced by the post.

### Get Post Detail

```http
GET /api/posts/{postId}
```

Response includes structured blocks:

```json
{
  "id": "...",
  "userId": "...",
  "title": "一次排查 Redis 延迟尖刺的过程",
  "categoryId": "...",
  "tags": ["Redis", "排障"],
  "blocks": [
    {
      "id": "...",
      "type": "paragraph",
      "text": "线上 Redis 在 14:20 后出现延迟尖刺。"
    },
    {
      "id": "...",
      "type": "image",
      "assetId": "...",
      "url": "/files/...",
      "caption": "延迟曲线"
    },
    {
      "id": "...",
      "type": "video",
      "assetId": "...",
      "status": "PENDING_TRANSCODE",
      "posterUrl": null,
      "sources": [],
      "caption": "复现步骤",
      "downloadUrl": "/files/..."
    },
    {
      "id": "...",
      "type": "file",
      "assetId": "...",
      "displayName": "logs.zip",
      "downloadUrl": "/files/..."
    }
  ]
}
```

## Publishing Flow

1. User adds blocks in the frontend editor.
2. For media blocks, frontend requests a content media upload session.
3. Content application authorizes the actor and creates a draft media asset.
4. Content infrastructure asks OSS for an upload session.
5. Frontend uploads through the returned content route.
6. Content completes the OSS upload and marks the asset `UPLOADED`.
7. User publishes the post with ordered blocks.
8. Content application validates title, category, tags, block order, block payloads, and media ownership.
9. Content application creates the post, saves blocks, binds media assets, and updates video state.
10. Content publishes post domain events and schedules existing side effects.

## Video Processing

Initial implementation models the pipeline but does not run real transcoding.

States:

- `NONE`: non-video assets.
- `PENDING_TRANSCODE`: video asset has been bound to a post and awaits processing.
- `PROCESSING`: future worker has claimed the asset.
- `READY`: poster and playback variants are available.
- `FAILED`: processing failed; detail still exposes original download URL.

The first implementation may include a no-op job/application service that can list pending videos and mark explicit test transitions, but it must not pretend to generate real playback variants.

Future real transcoding should produce derived OSS objects for posters and playback sources, recorded in `post_media_variant`.

## Frontend Design

New components:

- `PostBlockEditor`
  - owns ordered block editing
  - supports add/remove/reorder
  - emits blocks payload

- `PostBlockToolbar`
  - insert paragraph, image, video, file, code

- `PostMediaUploadBlock`
  - handles upload state for image/video/file blocks
  - shows progress, uploaded, failed, and video processing states

- `PostBlockRenderer`
  - renders post detail from `blocks`
  - no `UiMarkdown` post body path

- `PostMediaAssetClient`
  - wraps upload session and multipart upload calls

Existing post edit modal should not be used for posts after this change. It can remain for comments, because comment media is out of scope.

## Validation

Backend validation:

- title required and length-limited
- blocks required and count-limited
- block order normalized or rejected if duplicate/non-contiguous
- paragraph/code text required for text blocks
- image/video/file blocks require `assetId`
- asset must exist, be uploaded, be owned by actor, and be draft or already bound to the edited post
- asset kind must match block type
- file name/display name length-limited
- content type and size constrained before upload session creation

Frontend validation:

- cannot publish while referenced media upload is still in progress
- can publish with video `PENDING_TRANSCODE` after upload is complete
- clearly marks failed uploads and failed video processing
- blocks payload never contains old `content`

## Error Handling

- Invalid upload type or size: reject at upload-session creation.
- Upload failure: keep the block in failed state and do not include it in publish payload unless retried.
- Referencing another user's asset: reject publish with forbidden/business error.
- Referencing missing, released, or deleted asset: reject publish.
- Referencing non-uploaded asset: reject publish.
- Video processing pending: allow detail rendering with processing state.
- Video processing failed: show failed state and original download action.
- Post deletion: soft-delete post and release OSS references for bound media after commit.

## Search And Projections

Search should no longer index `discuss_post.content`.

Content read/application code should derive a plain text projection from blocks:

- paragraph text
- code text
- image/video captions
- file display names

Search outbox consumers continue to receive post events and backread content owner state by post id. The projection assembler should use blocks as the source of truth.

Post summaries should derive previews from blocks rather than storing legacy body content.

## Documentation Updates During Implementation

Implementation should update:

- `docs/handbook/business-logic/content.md`
- `docs/handbook/system-design.md` if media processing jobs or owner responsibilities change materially
- architecture guardrails only if package boundaries or allowed dependencies change

## Testing Strategy

Backend tests:

- controller request DTO rejects old `content` and accepts `blocks`
- create post saves ordered blocks
- create post binds uploaded draft assets
- create post rejects missing/foreign/non-uploaded/wrong-kind assets
- update post replaces blocks and releases removed media references
- video block publish sets `PENDING_TRANSCODE`
- detail response returns blocks, not content
- summary preview is derived from blocks
- media upload session creates draft asset and delegates to OSS through infrastructure adapter
- ArchUnit tests remain green for controller/listener/domain/infrastructure boundaries

Frontend tests:

- block editor creates paragraph/image/video/file/code blocks
- media block uploads immediately and stores `assetId`
- publish payload contains `blocks` and no `content`
- detail renderer displays each block type
- video pending and failed states render distinctly
- post edit uses block editor

## References

- Community OSS service design: `docs/superpowers/specs/2026-05-07-community-oss-service-design.md`
- Content business logic handbook: `docs/handbook/business-logic/content.md`
- GitHub attaching files: https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/attaching-files
- Discourse uploads/images/attachments: https://meta.discourse.org/t/understanding-uploads-images-and-attachments/275735
- Reddit post types: https://support.reddithelp.com/hc/en-us/articles/360060422572-How-do-I-post-on-Reddit
- Stack Overflow formatting/images: https://stackoverflow.com/help/formatting

## Final Shape

Posts become structured documents composed of ordered blocks. Media assets are owned by content as post media facts and stored physically in OSS. The old string-body post model is removed from the post write/read surface because the project has no historical data or deployment to preserve.
