# Growth Reward Progress Idempotency Hardening Design

## Goal

Fix growth task reward and progress idempotency issues without expanding growth into a ledger or introducing a new task center surface.

## Scope

- Growth remains the owner of task templates, task progress, source event deduplication, automatic reward triggering, and level rule calculation.
- Wallet remains the owner of balance ledger facts and final idempotent posting.
- This change does not add a growth experience ledger, manual reward claiming endpoint, or wallet reward reversal flow.

## Design

### Wallet Reward Amount

Automatic task rewards will send only `reward_balance_delta` to `WalletRewardActionApi`. `reward_growth_delta` remains a growth-domain configuration field but is not converted into wallet balance. If `reward_balance_delta <= 0`, growth still marks an auto reward task as claimed with a stable `rewardGrantId`, but no wallet call is made.

### Like Source Event Id

All growth like event adapters will use one source event id rule: prefer trimmed `relationKey`; otherwise use `like-created:<actorUserId>:<entityType>:<entityId>`. This preserves current social relation semantics and prevents different adapters from generating different ids for the same like fact.

### Like Removal Routing

Growth's local/outbox projection path will enqueue and dispatch both `LikeCreated` and `LikeRemoved`. Removed events will be sent to the existing `growth.task.like-removed` topic and handled by `TaskProgressKafkaListener` the same way backbone events already are. Claimed tasks keep current behavior and are not rolled back in this change.

### Level Calculation

User level calculation will count only completed `DAILY_CHECK_IN` periods. A completed period means `status in ('CLAIMABLE', 'CLAIMED')` or `current_value >= target_value`. This keeps today's existing `CLAIMED` check-in behavior while avoiding accidental level inflation from partial progress rows.

### Period Key Validation

Unsupported `periodType` values will fail fast instead of silently falling back to daily period keys. Blank period types will continue to be treated as daily for compatibility with older rows.

## Tests

- Add growth application tests proving wallet reward amount excludes `reward_growth_delta`.
- Add listener/enqueuer/dispatch tests proving `LikeRemoved` flows through the growth outbox path.
- Add listener tests proving like source id fallback is identical across Kafka adapters.
- Add level tests proving incomplete check-in rows are ignored.
- Add period key tests proving unsupported period types fail fast.

## Non-Goals

- No growth experience ledger.
- No wallet reversal for already claimed like rewards.
- No new public growth controller.
- No content/social event backbone redesign.
