# Market Wallet Saga Command Design

## 1. Problem

`MarketOrderService` and `MarketDisputeService` currently call `WalletMarketActionApi` inside market write transactions. The wallet implementation is also transactional, so Spring's default `REQUIRED` propagation joins wallet ledger writes into the same database transaction as market order, listing, inventory, shipment, and dispute writes.

This creates a hidden large transaction:

1. market locks listing, inventory, or order rows
2. market calls wallet escrow, release, or refund
3. wallet writes `wallet_txn`, `wallet_entry`, and `wallet_account`
4. market updates order, inventory, or dispute state
5. all writes commit or roll back together

The current behavior is easy to reason about in a single module, but it is the wrong boundary for money-related cross-domain work. It couples market inventory locks, order state, and wallet ledger state into one synchronous unit. It also makes future module extraction, retry, and compensation harder because no durable representation exists for "market asked wallet to do this action".

## 2. Goals

- Remove wallet ledger writes from market order and dispute transactions.
- Keep wallet ledger posting atomic inside the wallet domain.
- Make every market-to-wallet action durable, retryable, idempotent, and inspectable.
- Model intermediate market states explicitly instead of relying on a hidden synchronous commit.
- Preserve existing user-facing business semantics: escrow before delivery, release on confirmation, refund on cancellation or dispute resolution.
- Keep the first implementation local to `community-app`; do not introduce Kafka or a distributed transaction coordinator.

## 3. Non-Goals

- Do not replace the wallet ledger model.
- Do not turn all `community-app` cross-domain calls into a generic command bus.
- Do not introduce XA, 2PC, Seata, or distributed locks.
- Do not make wallet depend on market tables.
- Do not remove existing outbox infrastructure used by other projections.
- Do not implement partial refunds or multi-seller orders in this slice.

## 4. Decision

Use a dedicated `market_wallet_action` business command table plus a market order saga state machine.

Market writes a wallet command in the same local transaction as the market state transition. A background processor claims pending commands and calls `WalletMarketActionApi` outside the original market transaction. Wallet remains the owner of account and ledger writes. After wallet succeeds, the processor records the wallet transaction id and advances the market order or dispute with conditional updates.

This is preferred over reusing the generic `outbox_event` table as the primary state store because wallet actions are business commands, not just projection events. Operators need to query action status by order, understand terminal failures, retry or dead-letter specific money actions, and reconcile the market order state with wallet transaction ids.

The existing outbox worker concepts are still useful:

- pending/processing/succeeded/failed/dead statuses
- processing leases
- retry count and next retry time
- at-least-once execution
- idempotent handlers

The implementation may either build a small dedicated scanner for `market_wallet_action`, or wrap it behind common outbox-style support. The persisted source of truth for market wallet actions must be `market_wallet_action`.

## 5. Data Model

Add `market_wallet_action`.

Recommended columns:

- `action_id binary(16) primary key`
- `order_id binary(16) not null`
- `dispute_id binary(16) default null`
- `action_type varchar(16) not null`
  - `ESCROW`
  - `RELEASE`
  - `REFUND`
- `request_id varchar(96) not null`
- `wallet_biz_id varchar(96) not null`
- `actor_user_id binary(16) not null`
- `counterparty_user_id binary(16) default null`
- `amount bigint not null`
- `status varchar(16) not null`
  - `PENDING`
  - `PROCESSING`
  - `SUCCEEDED`
  - `CANCELLED`
  - `RETRYING`
  - `FAILED`
  - `DEAD`
- `result_type varchar(16) default null`
  - `APPLIED`
  - `NOOP`
- `wallet_txn_id binary(16) default null`
- `failure_code varchar(64) default null`
- `last_error varchar(255) default null`
- `retry_count int not null default 0`
- `next_retry_at timestamp null default null`
- `processing_lease_until timestamp null default null`
- `create_time timestamp null default current_timestamp`
- `update_time timestamp null default current_timestamp on update current_timestamp`

Recommended constraints and indexes:

- unique `request_id`
- index `(status, next_retry_at, action_id)`
- index `(order_id, action_type)`
- optional unique `(order_id, action_type)` for the initial one-escrow, one-release, one-refund model

The unique `request_id` must match the wallet `requestId`. Wallet already has `wallet_txn.request_id` uniqueness, so repeated command execution must return the same wallet transaction instead of posting money twice.

## 6. Order State Model

Add explicit pending states to `market_order.status`.

Recommended statuses:

- `ESCROW_PENDING`
- `ESCROW_CANCEL_PENDING`
- `ESCROWED`
- `DELIVERED`
- `SHIPPED`
- `RELEASE_PENDING`
- `COMPLETED`
- `REFUND_PENDING`
- `CANCELLED`
- `DISPUTED`
- `DISPUTE_REFUND_PENDING`
- `DISPUTE_RELEASE_PENDING`
- `REFUNDED`
- `ESCROW_FAILED`

The implementation may collapse `DISPUTE_REFUND_PENDING` into `REFUND_PENDING` and `DISPUTE_RELEASE_PENDING` into `RELEASE_PENDING` if the dispute table holds the resolution context clearly. The important rule is that user and admin actions no longer jump directly from a market state to a final money state before wallet has committed.

## 7. Command Lifecycle

### 7.1 Create

Within a market transaction:

1. validate the request
2. lock listing and inventory as needed
3. create order with `status = ESCROW_PENDING`
4. reserve or decrement market inventory as today
5. insert `market_wallet_action(action_type = ESCROW, status = PENDING)`
6. commit

The HTTP response may return the order in `ESCROW_PENDING`. Query APIs should expose this as a payment/escrow-in-progress state.

### 7.2 Process

The wallet action processor:

1. selects due `PENDING` or `RETRYING` actions
2. claims one action by setting `PROCESSING` with a lease
3. calls the matching wallet method
   - `ESCROW` -> `walletMarketActionApi.escrowOrder`
   - `RELEASE` -> `walletMarketActionApi.releaseOrder`
   - `REFUND` -> `walletMarketActionApi.refundOrder`
4. writes `wallet_txn_id`
5. advances market state with a conditional update
6. marks action `SUCCEEDED`

Each processor step must tolerate duplicate execution. If the wallet call succeeds but the process dies before marking the action succeeded, the next retry uses the same `request_id`, obtains the same wallet transaction, and resumes market state advancement.

### 7.3 Retry

Transient failures become retryable:

- database conflicts
- wallet optimistic update conflict
- temporary infrastructure errors

The action moves to `RETRYING`, increments `retry_count`, records `last_error`, and sets `next_retry_at` using bounded exponential backoff.

### 7.4 Terminal Failure

Business failures that cannot succeed by retrying become terminal:

- buyer wallet frozen
- buyer balance insufficient during escrow
- malformed command payload
- order no longer matches the command snapshot

For escrow terminal failure:

1. mark action `FAILED`
2. transition order `ESCROW_PENDING -> ESCROW_FAILED` or `CANCELLED`
3. restore finite stock and preloaded inventory reservations

For release or refund terminal failure, do not silently complete the order. Keep the order in the pending money state and surface it for operator repair unless the failure has a safe automatic compensation.

## 8. Saga Failure Semantics

The command processor must handle the standard saga/TCC failure patterns explicitly. These rules are part of the contract, not implementation details.

### 8.1 Idempotency

The same command may execute more than once because of timeouts, retries, scheduler overlap, lease recovery, or future MQ redelivery. Idempotency is required at three layers:

- command layer: `market_wallet_action.request_id` is unique
- wallet layer: `wallet_txn.request_id` is unique
- market state layer: every order/dispute transition is a conditional update from an expected state

If a command already has `wallet_txn_id` and the market state is already advanced, replay returns success without calling wallet again. If wallet succeeded but the process crashed before market advancement, replay uses the same wallet `requestId`, loads the same wallet transaction, and resumes the market transition.

### 8.2 Empty Compensation

A compensation request may arrive even when the forward action did not actually apply. This must be a successful no-op, not an error.

Examples:

- escrow failed before wallet posted money, but an escrow-cancel compensation path runs
- inventory reservation was already released, but retry attempts to release it again
- refund compensation is requested for an order that never reached `ESCROWED`

The rule is:

1. check the durable forward state first
2. if the forward side effect is absent, record the compensation as `SUCCEEDED` with `result_type = NOOP`
3. leave money and inventory unchanged
4. do not throw solely because there is nothing left to compensate

For wallet refund specifically, the processor must not call `refundOrder` unless the order has a successful escrow marker, either `escrow_txn_id` on `market_order` or an applied `ESCROW` action. If no escrow was applied, refund is a no-op market compensation.

### 8.3 Hanging Prevention

A compensation or cancellation may arrive before the forward command is processed. The forward command must not later apply after the system has already accepted cancellation.

For escrow:

1. if buyer cancels while order is `ESCROW_PENDING`, move the order to `ESCROW_CANCEL_PENDING`
2. try to cancel the pending `ESCROW` action if it is still `PENDING` or `RETRYING`
3. the escrow processor must re-read the order after claiming the action and before calling wallet
4. if the order is no longer `ESCROW_PENDING`, mark the escrow action `CANCELLED` or `SUCCEEDED/NOOP` and do not call wallet
5. if wallet escrow already applied concurrently, cancellation must enqueue a normal `REFUND` action instead of pretending escrow did not happen

This prevents the classic hanging case where cancel arrives first, then the old forward request wakes up and applies money movement after cancellation.

### 8.4 Persisted State Machine

All saga progress must be durable before any external or cross-domain side effect is attempted.

Required durable markers:

- order status
- dispute status and resolution decision
- wallet action status
- wallet action request id
- wallet transaction id when known
- retry count and next retry time
- terminal failure or no-op result

In-memory flags, scheduler-local state, or Java object state must never be the source of truth. After process restart, the system must recover by scanning persisted order and action states.

### 8.5 Compensation Failure

Compensation can fail. Refund may throw, inventory restoration may conflict, or the process may crash after wallet refund but before market state is updated.

The rule is:

1. compensation is represented by a durable command
2. wallet refund uses a deterministic request id and is safe to retry
3. market restoration uses conditional, idempotent updates
4. the command is not marked `SUCCEEDED` until both wallet and required market-side compensation state are complete
5. repeated compensation failure moves the action to `DEAD` or `FAILED` with enough context for operator repair

If wallet refund succeeds but inventory restoration fails, retry must not refund twice. It must reload the wallet transaction by the same request id, then retry only the remaining market-side restoration and final state transition.

## 9. Flow Changes

### 9.1 Create Order

Current flow:

1. lock listing/inventory
2. call wallet escrow synchronously
3. insert `ESCROWED` order
4. update inventory

New flow:

1. lock listing/inventory
2. insert order as `ESCROW_PENDING`
3. reserve/decrement inventory
4. insert `ESCROW` wallet action
5. processor posts wallet escrow
6. processor marks order `ESCROWED` with `escrow_txn_id`

Preloaded virtual delivery should happen only after escrow succeeds. If preloaded stock is reserved during `ESCROW_PENDING`, it must not be exposed to the buyer as delivered until the order reaches `ESCROWED`.

### 9.2 Manual Confirm And Auto Confirm

Current flow:

1. lock order
2. call wallet release synchronously
3. mark order `COMPLETED`

New flow:

1. lock order in `DELIVERED` or `SHIPPED`
2. mark `RELEASE_PENDING`
3. insert `RELEASE` wallet action
4. processor posts wallet release
5. processor marks order `COMPLETED` with `release_txn_id`

`autoConfirmDueOrders` must not execute wallet release in a large loop transaction. It should enqueue release commands for due orders using small per-order transactions or a claim-based batch.

### 9.3 Buyer Cancel

Current flow:

1. lock order in `ESCROWED`
2. call wallet refund synchronously
3. restore inventory
4. mark `CANCELLED`

New flow:

1. lock order in `ESCROWED`
2. mark `REFUND_PENDING`
3. insert `REFUND` wallet action
4. processor posts wallet refund
5. processor restores inventory and marks `CANCELLED` with `refund_txn_id`

Inventory should be restored after refund succeeds. This avoids reselling stock before escrow money has been returned. If product strategy requires faster stock reuse, that must be a separate explicit decision with reconciliation rules.

If the buyer cancels while order is still `ESCROW_PENDING`, use the hanging-prevention flow from section 8.3. Do not enqueue wallet refund unless escrow has actually applied.

### 9.4 Dispute Refund Or Release

Current flow:

1. lock dispute/order
2. call wallet refund or release synchronously
3. mark dispute resolved
4. mark order final

New flow:

1. lock dispute/order
2. record the dispute decision
3. mark order `DISPUTE_REFUND_PENDING` or `DISPUTE_RELEASE_PENDING`
4. insert matching wallet action
5. processor posts wallet action
6. processor marks dispute resolved and order `REFUNDED` or `COMPLETED`

The dispute decision and pending money action should be visible to admins so they can distinguish "decision made, money still processing" from "fully resolved".

## 10. Idempotency Rules

Canonical request ids:

- escrow: `market-order:<orderId>:escrow`
- release: `market-order:<orderId>:release`
- refund: `market-order:<orderId>:refund`

If the current create-order API only has client `requestId` before `orderId` exists, the market transaction should generate `orderId` first and use `orderId` for wallet command ids. The client `requestId` remains the idempotency key for order creation.

Rules:

- `market_order.request_id` stays unique for client create-order idempotency.
- `market_wallet_action.request_id` is unique for command idempotency.
- `wallet_txn.request_id` remains unique for wallet ledger idempotency.
- state advancement uses conditional updates, for example `ESCROW_PENDING -> ESCROWED`, not unconditional status overwrite.
- replaying an already succeeded command must not change money twice or regress order state.

## 11. Boundaries

Market owns:

- listing and inventory reservation
- order state
- dispute state
- wallet action command state
- compensation after wallet terminal failure

Wallet owns:

- account status validation
- balance validation
- ledger transaction creation
- double-entry postings
- wallet transaction idempotency

The wallet API should remain a synchronous owner-domain action API from the processor's perspective. The important change is not "make wallet async internally"; it is "do not call wallet inside the market business transaction".

## 12. Observability And Operations

Add logs and metrics for:

- action enqueued
- action claimed
- action succeeded
- action retry scheduled
- action failed terminally
- action lease recovered
- action completed as no-op compensation
- forward action cancelled because compensation arrived first

Operators need queries for:

- pending actions older than a threshold
- failed/dead actions by order id
- order pending money state without matching action
- action succeeded but order not advanced
- wallet transaction exists for request id but action not succeeded
- compensation action in `DEAD` or `FAILED`
- cancelled order with an applied escrow action but no refund action

This design should include a repair command or admin endpoint later, but the first implementation can start with database-backed operational queries and clear logs.

## 13. Migration Strategy

1. Add the command table and new statuses without changing behavior.
2. Implement command creation and processor behind code paths for escrow only.
3. Move `createOrder` to `ESCROW_PENDING -> ESCROWED`.
4. Move confirm and auto-confirm to `RELEASE_PENDING -> COMPLETED`.
5. Move cancel and dispute flows to refund/release pending states.
6. Remove direct `WalletMarketActionApi` calls from market transactional methods.
7. Update business docs to describe pending money states.

For already existing orders:

- orders with `ESCROWED`, `DELIVERED`, `SHIPPED`, `COMPLETED`, `CANCELLED`, or `REFUNDED` remain valid
- no backfill is required for completed wallet actions
- the new processor only handles rows in `market_wallet_action`

## 14. Testing Strategy

Unit tests:

- command id generation is deterministic
- duplicate command insert returns or loads the existing action
- processor maps action types to the correct wallet API method
- retryable and terminal failures update action state correctly
- conditional state advancement is idempotent
- empty compensation records `NOOP` success
- forward escrow does not apply after `ESCROW_CANCEL_PENDING`

Service tests:

- create order no longer calls wallet inside `createOrder`
- create order writes `ESCROW_PENDING` and `ESCROW` action
- escrow processor writes `escrow_txn_id` and marks order `ESCROWED`
- insufficient balance marks escrow failed and restores stock
- confirm order writes `RELEASE_PENDING` and `RELEASE` action
- release processor marks order `COMPLETED`
- cancel order writes `REFUND_PENDING` and `REFUND` action
- refund processor marks order `CANCELLED` or `REFUNDED`
- dispute resolution remains pending until wallet succeeds
- auto-confirm enqueues release commands without one large wallet transaction
- cancel during `ESCROW_PENDING` prevents hanging escrow execution
- refund retry after wallet success but market update failure does not refund twice

Concurrency tests:

- two processors cannot claim the same action at the same time
- retry after process crash does not duplicate wallet transactions
- duplicate HTTP confirm/cancel calls do not enqueue duplicate wallet commands
- cancel-before-escrow and escrow-before-cancel races converge to either no wallet action or escrow plus refund

Integration tests:

- end-to-end order lifecycle with escrow, delivery, release
- refund lifecycle with inventory restoration
- dispute refund and dispute release lifecycle

## 15. Acceptance Criteria

- No market order or dispute transaction directly calls `WalletMarketActionApi`.
- `WalletLedgerService.post` still runs in a wallet-owned transaction.
- Every escrow, release, and refund requested by market has one durable `market_wallet_action` row.
- Retrying a wallet action uses the same request id and cannot duplicate ledger postings.
- Market order states expose money-in-progress states.
- Auto-confirm no longer wraps many wallet releases in one market transaction.
- Tests cover success, retry, terminal failure, and duplicate execution paths.
- Empty compensation is a successful no-op, not an error.
- Hanging prevention stops a forward wallet action from applying after cancellation has been durably accepted.
- Compensation failure is durable, retryable, and operator-visible.
