# Community Backend Production MyBatis Migration Design

## Summary

Migrate selected production MySQL access in the community backend from direct
`JdbcTemplate` repositories to MyBatis mapper interfaces and XML mapper files.
The goal is to make production business persistence use the same MyBatis style
already used by `community-app`, while keeping existing repository contracts and
runtime behavior unchanged.

This design intentionally does not migrate shared infrastructure stores or test
helper SQL.

## Scope

In scope:

- `backend/community-app`
  - `user.infrastructure.persistence.MyBatisRefreshTokenSessionRepository`
- `backend/community-im/im-core`
  - `JdbcConversationRepository`
  - `JdbcConversationReadStateRepository`
  - `JdbcPrivateMessageRepository`
  - `JdbcRoomRepository`
  - `JdbcRoomMemberRepository`
  - `JdbcRoomMessageRepository`
  - `JdbcRoomReadStateRepository`
  - `JdbcUserInboxRepository`

Out of scope:

- `backend/community-common/common-outbox`
- `backend/community-common/common-idempotency`
- Test-only `JdbcTemplate` usage for setup, cleanup, and assertions
- MyBatis `TypeHandler` classes that use `PreparedStatement` or `ResultSet`
- Schema changes
- Domain repository interface changes
- Business behavior changes

## Current State

`community-app` already depends on MyBatis and follows the target package shape
for most domains:

```text
domain.repository interface
  <- infrastructure.persistence.MyBatis*Repository
      -> infrastructure.persistence.mapper.*Mapper
      -> resources/mapper/*.xml
      -> infrastructure.persistence.dataobject.*DataObject when row shape differs from domain shape
```

The refresh-token session repository is an exception. It is named
`MyBatisRefreshTokenSessionRepository`, but it directly injects `JdbcTemplate`
and keeps SQL strings in Java.

`community-im/im-core` uses repository interfaces in the domain/application
boundary, but its infrastructure persistence implementations are direct JDBC
repositories named `Jdbc*Repository`.

## Target Architecture

The target shape for the migrated classes is:

```text
ApplicationService
  -> domain repository interface
      -> infrastructure.persistence.MyBatis*Repository
          -> infrastructure.persistence.mapper.*Mapper
          -> resources/mapper/*.xml
```

Repository interfaces remain stable. Application services and domain objects
must not depend on mapper or dataobject classes.

Mapper XML owns SQL text. Repository implementations own input validation,
domain conversion, duplicate-key exception translation where behavior currently
depends on it, and any multi-step orchestration that is not naturally a single
SQL statement.

## Community App Refresh Token Migration

Add:

- `user.infrastructure.persistence.mapper.RefreshTokenSessionMapper`
- `user.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject`
- `backend/community-app/src/main/resources/mapper/refresh-token-session-mapper.xml`

Update:

- `MyBatisRefreshTokenSessionRepository`

The repository keeps the existing `RefreshTokenSessionRepository` contract and
existing semantics:

- store only token hashes, not raw refresh tokens
- ignore blank input where the current implementation ignores it
- reject storing into a revoked family by preserving the current insert-select
  behavior and throwing `IllegalStateException` when no row is written
- consume active tokens atomically by updating only non-revoked, non-expired rows
- revoke one token, a whole family, or all tokens for a user
- delete expired token sessions before a cutoff

The mapper returns `RefreshTokenSessionDataObject`, and the repository converts
that row object to `RefreshTokenSession`.

## IM Core Migration

Add mapper interfaces under:

```text
backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/mapper
```

Add mapper XML files under:

```text
backend/community-im/im-core/src/main/resources/mapper
```

Rename infrastructure implementations from `Jdbc*Repository` to
`MyBatis*Repository` where practical, preserving the same implemented domain
repository interfaces.

The migrated implementations must preserve existing behavior:

- duplicate-key handling remains idempotent or translated exactly as before
- sequence allocation and read-state advancement keep the same atomic update
  semantics
- inbox and room projection updates keep existing ordering and conflict handling
- row-to-domain projection for conversation lists, message records, room
  membership entries, unread counters, and last-message views remains unchanged

If an existing JDBC repository only delegates to another repository and has no
SQL of its own, it does not need a mapper.

## MyBatis Configuration

`community-app` already has MyBatis configuration and mapper XML loading.

`community-im/im-core` currently depends on `spring-boot-starter-jdbc`. The
implementation should add `mybatis-spring-boot-starter` and configure mapper
scanning in the IM core application/configuration using the same pattern as the
other backend modules: scan `@Mapper` interfaces and load
`classpath*:mapper/*.xml`.

## Guardrails

Add or update ArchUnit coverage so production source in the migrated modules
does not reintroduce direct `JdbcTemplate` persistence, while explicitly
excluding:

- `community-common/common-outbox`
- `community-common/common-idempotency`
- test source
- MyBatis `TypeHandler` code using JDBC SPI types
- transaction management classes such as `TransactionTemplate` and
  `PlatformTransactionManager`

The guardrail should check dependency on Spring JDBC access types from
production repository implementations, not ordinary transaction coordination.

## Testing

Run focused persistence and application tests that cover the migrated behavior:

- refresh-token session repository tests
- auth refresh-token flows that use DB-backed refresh sessions
- IM private-message, room-message, room, member, read-state, and inbox tests
- IM controller boundary tests if they assert infrastructure isolation

Run architecture tests for affected modules after the guardrail changes.

The migration should not require schema changes. Existing tests that use
`JdbcTemplate` for setup or assertions can stay as they are.

## Acceptance Criteria

- No in-scope production repository directly injects `JdbcTemplate`.
- In-scope production MySQL SQL statements live in MyBatis XML mapper files.
- Application and domain code keep depending on repository interfaces, not
  MyBatis mapper interfaces or dataobjects.
- Common outbox, common idempotency, and test helper SQL remain unchanged.
- Focused tests and relevant architecture tests pass.
