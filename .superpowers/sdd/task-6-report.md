# Task 6 Report: Analytics Async Capture, Ops Toggles, And Verification

## Status

Completed.

## Scope Implemented

- Added `AnalyticsRequestEvent` as the async request capture payload.
- Added `AnalyticsRequestCaptureApplicationService` and `AnalyticsRequestCapturePort` so request capture stays behind the same-domain application boundary.
- Added `AnalyticsRequestEventPublisher` for Kafka publishing when both `analytics.ingest.enabled=true` and `analytics.ingest.async-enabled=true`.
- Added `AnalyticsRequestKafkaListener` to translate request events back into `RecordRequestCommand`.
- Updated `AnalyticsRequestCaptureFilter` to publish asynchronously after successful downstream request completion, with synchronous fallback when async is disabled or no publisher is available.
- Added `analytics.ingest.async-enabled`, topic, group, and concurrency configuration in `application.yml`.
- Updated operations and workflow documentation for analytics async ingestion, content platform degradation toggles, and dual-region failover order.
- Updated Task 6 checklist state in the implementation plan.

## TDD Evidence

### RED

Command:

```bash
cd backend
mvn test -pl :community-app -Dtest=AnalyticsRequestCaptureFilterTest,AnalyticsRequestKafkaListenerTest,AnalyticsIngestApplicationServiceTest,AnalyticsControllerUnitTest
```

Result:

- Failed at test compile as expected.
- Missing production contract pieces:
  - `AnalyticsRequestEvent`
- `AnalyticsRequestCaptureApplicationService`
- `AnalyticsRequestCapturePort`
- `AnalyticsRequestEventPublisher`
- `AnalyticsRequestKafkaListener`
- `AnalyticsIngestProperties.setAsyncEnabled(...)`

### GREEN

Command:

```bash
cd backend
mvn test -pl :community-app -Dtest=AnalyticsRequestCaptureFilterTest,AnalyticsRequestKafkaListenerTest,AnalyticsIngestApplicationServiceTest,AnalyticsControllerUnitTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`

## Review Fixes

- Moved async capture selection out of `AnalyticsRequestCaptureFilter`; the filter now calls `AnalyticsRequestCaptureApplicationService`.
- Made `AnalyticsRequestEventPublisher` implement the application-owned `AnalyticsRequestCapturePort`.
- Conditioned both async publisher and listener beans on `analytics.ingest.enabled=true` and `analytics.ingest.async-enabled=true`.
- Added fallback coverage for `async-enabled=true` with no publisher bean and direct annotation coverage for the two-flag async bean condition.

Architecture verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Result:

- `BUILD SUCCESS`
- `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0`

## Concerns

None.
