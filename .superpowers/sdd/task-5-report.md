# Task 5 Report: Search And Notice Projection Guardrails

## Status

Completed.

## Scope Implemented

- Added `sourceVersion` to search and notice projection commands.
- Passed contract event versions through search and notice Kafka listeners.
- Added `projectionEnabled` policy flags to search and notice application properties.
- Gated search projection and notice projection when the corresponding flag is disabled.
- Rejected blank search projection source event ids before read/model projection work.
- Made reliable `LIKE_REMOVED` notice projection validate and record source event ids before revoking, so blank ids stay retry-visible and duplicate removals are idempotent.
- Updated the search post outbox enqueuer and handler to persist and replay source version.
- Updated Task 5 plan checkboxes and file list for the additional outbox bridge files required by the command contract.

## TDD Evidence

### RED

Command:

```bash
cd backend
mvn test -pl :community-app -Dtest=SearchPostProjectionApplicationServiceTest,SearchPostProjectionKafkaListenerTest,NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest
```

Result:

- Failed as expected at test compile.
- Missing production contract pieces:
  - `ProjectPostOutboxCommand` did not accept `sourceVersion`.
  - notice projection commands did not expose `sourceVersion`.
  - search/notice policy properties did not expose `projectionEnabled`.
  - search projection service did not have the policy-aware constructor.

### GREEN

Command:

```bash
cd backend
mvn test -pl :community-app -Dtest=SearchPostProjectionApplicationServiceTest,SearchPostProjectionKafkaListenerTest,NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`

Additional adjacent verification:

```bash
cd backend
mvn test -pl :community-app -Dtest=PostOutboxHandlerTest,PostOutboxEnqueuerTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

Review fix verification:

```bash
cd backend
mvn test -pl :community-app -Dtest=SearchPostProjectionApplicationServiceTest,SearchPostProjectionKafkaListenerTest,NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest,PostOutboxHandlerTest,PostOutboxEnqueuerTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`

## Review

- Initial review found reliable `LIKE_REMOVED` revocation bypassed source event id validation and idempotency recording.
- Fixed with explicit validation/recording before revocation and duplicate/blank-id tests.
- Removed stale `sourceEventType` from the search post outbox payload.

## Concerns

None.
