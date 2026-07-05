# Task 3 Report

## Status

DONE

## Scope

Implemented Task 3 only for content, social, and user event dispatch ports.

- Added semantic application command records:
  - `DispatchContentEventCommand`
  - `DispatchSocialEventCommand`
  - `DispatchUserEventCommand`
- Renamed application-owned Kafka-shaped ports to semantic dispatchers:
  - `ContentIntegrationEventDispatcher`
  - `SocialIntegrationEventDispatcher`
  - `UserIntegrationEventDispatcher`
- Removed Kafka topic selection from the three application services.
- Moved Kafka topic lookup into the three sender adapters while preserving defaults:
  - `content.events`
  - `social.events`
  - `user.events`
- Updated the three outbox handlers to remain inbound adapters and pass command records into same-domain application services only.
- Updated focused dispatch, publisher, handler, and sender tests to assert command and semantic dispatcher usage.

## TDD Evidence

### Red

Ran the focused command from the brief before production changes:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,OutboxContentEventPublisherTest,OutboxSocialDomainEventPublisherTest,OutboxUserPolicyEventPublisherTest'
```

Result: failed at compile time for the expected missing command records, semantic dispatcher interfaces, and updated adapter surface.

### Green

Ran the same focused command after implementation:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,OutboxContentEventPublisherTest,OutboxSocialDomainEventPublisherTest,OutboxUserPolicyEventPublisherTest'
```

Result: `BUILD SUCCESS`, 53 tests run, 0 failures, 0 errors.

## Additional Verification

Ran adjacent touched-unit coverage for handlers and sender adapters:

```bash
cd backend
mvn test -pl :community-app -am -Dtest='ContentEventKafkaOutboxHandlerTest,SocialEventKafkaOutboxHandlerTest,UserEventKafkaOutboxHandlerTest,ContentEventKafkaSenderAdapterTest,SocialEventKafkaSenderAdapterTest,UserEventKafkaSenderAdapterTest'
```

Result: `BUILD SUCCESS`, 21 tests run, 0 failures, 0 errors.

## Behavioral Notes

- Contract event typing remains unchanged in the three application services.
- Blank payload behavior remains `IllegalStateException` for outbox retry.
- Malformed JSON behavior remains `IllegalStateException` for outbox retry.
- Kafka topic defaults were preserved exactly and moved to adapter configuration.

## Self-Review

Reviewed the final diff and `git diff --check`.

- No whitespace or patch hygiene issues.
- No scope drift into growth, IM, transaction self-invocation, API adapter naming, or docs beyond the required task report.
- No functional concerns found in the touched content/social/user dispatch path.
