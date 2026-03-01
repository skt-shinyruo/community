# Message API Module Enablement (Design)

**Date:** 2026-02-26  
**Status:** Approved  

## Goal

Enable a first-class `message-api` Maven module so that message-domain **public contracts** are centralized and reusable, consistent with existing `user-api` / `content-api` / `social-api` patterns.

This includes:
- HTTP-facing DTOs used by `message-service` controllers
- Message-domain error codes
- A module layout that scales to future RPC contracts if needed

## Context / Current State

- The repo is a multi-module Maven project (`community` root `pom.xml` is the reactor SSOT).
- `message-service` currently contains:
  - Spring MVC controllers under `com.nowcoder.community.message.api.*`
  - HTTP DTOs under `com.nowcoder.community.message.api.dto.*`
  - `MessageErrorCode`
  - Internal entity `com.nowcoder.community.message.entity.Message`
- A top-level `message-api/` directory exists in the filesystem but is **not** a real Maven module:
  - No `pom.xml`
  - No tracked source files
  - Only leftover `target/` artifacts

## Non-Goals

- Do not introduce new message-domain functionality.
- Do not refactor unrelated packages or naming conventions.
- Do not add new Dubbo RPC providers for message at this time (contract scaffolding is allowed, but behavior changes are out of scope).

## Proposed Architecture

### Module Layout (Aggregate Parent)

Introduce an aggregate parent module `message/` (packaging `pom`), mirroring `user/` and `content/`:

```
community (root)
└── message (pom)
    ├── message-api (jar)
    └── message-service (jar)
```

Root `pom.xml` changes:
- Replace `<module>message-service</module>` with `<module>message</module>`

### What Lives in `message-api`

`message-api` is a pure contract jar:

- Error codes
  - `com.nowcoder.community.message.api.MessageErrorCode`
- HTTP request/response DTOs
  - `com.nowcoder.community.message.api.dto.*`

**Important constraint:** `message-api` must not depend on `message-service` implementation types.

#### DTO Entity Decoupling

Currently `NoticeTopicSummaryResponse.latest` is typed as `com.nowcoder.community.message.entity.Message` (an implementation entity).

Design change:
- Replace it with `LetterItemResponse latest`

Rationale:
- `LetterItemResponse` intentionally mirrors the public JSON shape of `Message` (id/fromId/toId/conversationId/content/status/createTime).
- This preserves API compatibility while removing an implementation dependency.

### What Stays in `message-service`

`message-service` remains the implementation module:
- Controllers (`MessageController`, `NoticeController`)
- Services/DAO/entity/kafka/etc.

It will depend on `message-api` for DTOs and error codes.

### HTTP API Contract Adjustment

To ensure the public HTTP interface does not expose internal entities:

- Change `GET /api/notices` return type from `Result<List<Message>>` to `Result<List<LetterItemResponse>>`
  - JSON remains compatible because `LetterItemResponse` matches the entity fields.

Other endpoints already return DTOs for private message flows and will remain unchanged.

## Maven Dependency Design

Follow `user-api` / `content-api` / `social-api` minimalism:

- `message-api` dependencies:
  - `contracts-core` (for `ErrorCode`)
  - `common` (for `ValidationLimits`)
  - `jakarta.validation-api` (DTO annotations: `@Size`, `@NotBlank`, `@Min`, ...)

- `message-service` dependencies:
  - Add dependency on `message-api` (same `${project.version}`)

## Migration Plan (High-Level)

1. Add `message/pom.xml` aggregate module.
2. Move `message-service/` → `message/message-service/` and update its parent.
3. Create `message/message-api/` module and its `pom.xml`.
4. Move contract types from `message-service` to `message-api` without changing packages:
   - `MessageErrorCode`
   - `com.nowcoder.community.message.api.dto.*`
5. Refactor notices DTO/service/controller to remove entity leakage:
   - `NoticeTopicSummaryResponse.latest` → `LetterItemResponse`
   - `NoticeController#list` returns `LetterItemResponse` list
6. Ensure the reactor builds and tests pass.

## Risks / Compatibility Notes

- **Binary compatibility:** Since packages stay the same, internal compilation impact is limited to Maven dependencies.
- **HTTP compatibility:** We rely on `LetterItemResponse` mirroring the existing `Message` JSON field names. If any consumers depended on Jackson-specific entity behaviors, they may observe differences; this is unlikely given the plain POJO structure.

## Validation

- Run `mvn test` at the repo root (reactor build).
- Ensure `message-service` unit tests compile and pass.
- Spot-check the serialized fields for:
  - `GET /api/notices`
  - `GET /api/notices/summary`

