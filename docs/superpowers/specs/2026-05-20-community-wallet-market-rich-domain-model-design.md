# Community Wallet And Market Rich Domain Model Refactor Design

Date: 2026-05-20

## Status

Design draft generated from domain-model review. Implementation has not started.

## Context

`backend/community-app` is expected to follow strict DDD tactical layering:

```text
Controller / Listener / Handler / Bridge / Enqueuer / Job
  -> ApplicationService
      -> Domain model / DomainService / Repository interface / Domain event
          -> Infrastructure implementation
```

The current wallet and market code largely follows package-level layering, but
some core business rules are still expressed outside the domain model.

Wallet recharge:

- `wallet.domain.model.RechargeOrder` is a mutable field holder.
- `WalletRechargeApplicationService.complete(...)` creates recharge orders,
  checks replay compatibility, checks whether an order is paid, builds the
  recharge ledger command, and performs the `CREATED -> PAID` transition with
  raw status strings.
- `recharge_order_mapper.xml` protects the transition with `where status =
  #{fromStatus}`, so part of the state machine only appears in SQL.

Market order:

- `market.domain.model.MarketOrder` is a mutable field holder.
- `MarketOrderApplicationService` defines goods type, order status, stock mode,
  delivery mode, and delivery status constants as private application strings.
- Order creation, replay matching, stock decisions, address snapshots, delivery,
  shipping, confirmation, cancellation, and wallet-action enqueue decisions are
  concentrated in application methods.
- `MarketOrderSagaApplicationService`,
  `MarketWalletActionRecoveryApplicationService`, and
  `MarketDisputeApplicationService` repeat order-status strings and encode
  additional pieces of the same state machine.
- `market_order_mapper.xml` contains conditional updates for many transitions,
  while some updates are unconditional legacy status changes.

This is an anemic-model problem, not primarily a package-boundary problem. The
application layer is currently carrying too much of the domain state machine.

## Problem Statement

Order invariants depend on private application helper methods and SQL
conditions instead of domain behavior. This creates several risks:

- A new entry point can bypass replay checks, status checks, or participant
  checks by calling repositories directly from another application service.
- The valid state graph is hard to review because it is split across model
  fields, application constants, repository method names, and mapper XML.
- Raw status strings can drift between application services, recovery code,
  tests, documentation, and SQL.
- Repository methods such as `markDelivered(...)`, `markRefundPending(...)`,
  and `changeStatus(...)` do not always make clear whether they are enforcing a
  domain transition or just applying a persistence update.
- The current shape makes future payment callback, recovery, auto-confirm, and
  dispute changes more likely to duplicate or weaken existing rules.

## Goals

- Move wallet recharge and market order state-machine facts into domain types
  and domain behavior.
- Keep application services responsible for orchestration, transactions,
  locking, idempotency, repository calls, and cross-domain collaboration.
- Replace application-layer raw status/type strings with domain enums or value
  objects.
- Preserve existing HTTP APIs, application result shapes, database schema,
  persisted string codes, and business behavior.
- Keep SQL conditional updates as concurrency guards, but make domain behavior
  the source of transition rules.
- Make the refactor incremental so focused tests can prove no behavior changed.
- Improve test coverage around recharge replay/payment behavior and market
  order transitions before or alongside implementation.

## Non-Goals

- Do not split `community-app` into separate services.
- Do not change database tables, columns, indexes, or persisted status codes.
- Do not change wallet ledger accounting rules.
- Do not change market wallet-action saga semantics.
- Do not rewrite all wallet order types in the first pass.
- Do not rewrite all market listing, inventory, delivery, shipment, or dispute
  models into rich aggregates.
- Do not move transaction management into domain objects.
- Do not let domain models depend on Spring, MyBatis, mapper classes,
  dataobject classes, HTTP DTOs, or foreign-domain APIs.
- Do not remove repository-level conditional updates that protect concurrent
  state changes.

## Chosen Approach

Use a staged refactor:

1. Introduce domain enums and small value objects while preserving string-backed
   persistence.
2. Move creation, replay checks, actor checks, and status predicates into
   `RechargeOrder` and `MarketOrder`.
3. Make application services call those domain methods before persisting
   changes or enqueuing wallet actions.
4. Gradually tighten repository method semantics so persistence operations
   reflect domain transitions and still perform conditional updates.

Rejected alternatives:

- Full aggregate rewrite in one change. This would touch recharge, withdrawal,
  transfer, listing, inventory, delivery, dispute, wallet action, and saga
  recovery at once, creating high regression risk.
- Keep strings in application services and only add tests. This leaves the
  valid state graph distributed and does not address the core design issue.
- Move transition logic into repositories. This improves application code but
  pushes business rules into infrastructure-facing persistence contracts.
- Store enum ordinals in the database. This is incompatible with existing
  schema and makes status inspection harder.

## Target Domain Types

### Wallet Recharge

Add:

```text
wallet.domain.model.RechargeOrderStatus
```

Values:

- `CREATED`
- `PAID`

The enum exposes:

```java
String code()
static RechargeOrderStatus fromCode(String code)
```

Unknown or blank codes should fail fast with a business exception or an
`IllegalArgumentException` converted at the application boundary, matching
existing error style for invalid persisted state.

`RechargeOrder` remains the domain model returned by repositories, but gains
behavior:

```java
public static RechargeOrder create(UUID orderId, String requestId, UUID userId, long amount)
public RechargeOrderStatus status()
public boolean isPaid()
public void assertReplayMatches(UUID userId, long amount)
public RechargeOrderTransition pay()
```

`RechargeOrderTransition` is a small domain value that carries:

```text
order identity
from status
to status
```

It lets the application service pass an explicit domain decision into the
repository without retyping string codes.

The legacy `getStatus()` and `setStatus(String)` methods can stay during the
first migration so result mappers, MyBatis row mapping, and existing tests do
not need to change all at once. New domain logic must use `status()` and enum
methods instead of raw string comparison.

### Market Order

Add:

```text
market.domain.model.MarketOrderStatus
market.domain.model.MarketGoodsType
market.domain.model.MarketDeliveryMode
market.domain.model.MarketStockMode
market.domain.model.MarketAddressSnapshot
market.domain.model.MarketOrderTransition
```

`MarketOrderStatus` values:

- `ESCROW_PENDING`
- `ESCROWED`
- `DELIVERED`
- `SHIPPED`
- `RELEASE_PENDING`
- `COMPLETED`
- `REFUND_PENDING`
- `CANCELLED`
- `ESCROW_CANCEL_PENDING`
- `ESCROW_FAILED`
- `DISPUTED`
- `DISPUTE_REFUND_PENDING`
- `DISPUTE_RELEASE_PENDING`
- `REFUNDED`

`MarketGoodsType` values:

- `PHYSICAL`
- `VIRTUAL`

`MarketDeliveryMode` values:

- `MANUAL`
- `PRELOADED`

`MarketStockMode` values:

- `FINITE`
- `UNLIMITED`

Each enum exposes `code()` and `fromCode(String)` and persists the same string
codes currently stored in MySQL.

`MarketAddressSnapshot` captures the address fields copied into an order:

```text
addressId
receiverName
receiverPhone
province
city
district
detailAddress
postalCode
```

`MarketOrder` gains behavior:

```java
public static MarketOrder place(MarketOrderPlacement placement)
public MarketOrderStatus status()
public MarketGoodsType goodsType()
public MarketDeliveryMode deliveryMode()
public boolean isReplayOf(UUID buyerUserId, UUID listingId, int quantity, UUID addressId)
public void assertReplayMatches(...)
public void assertBuyer(UUID actorUserId)
public void assertSeller(UUID actorUserId)
public void assertEscrowed()
public void assertPhysical()
public void assertVirtual()
public boolean isConfirmable()
public MarketOrderTransition requestRelease()
public MarketOrderTransition requestRefund()
public MarketOrderTransition requestEscrowCancel()
public MarketOrderTransition markDelivered(Date autoConfirmAt)
public MarketOrderTransition markShipped(Date autoConfirmAt)
public String pendingWalletActionType()
```

`MarketOrderPlacement` is an application-built domain command or factory
parameter object. It contains only domain data needed to create an order:

```text
orderId
requestId
listing identity and seller
buyer identity
goods type
quantity
unit price snapshot
total amount
delivery mode snapshot
listing title snapshot
optional address snapshot
```

It must not contain HTTP DTOs, mapper/dataobject types, or Spring types.

## Application Service Design

### WalletRechargeApplicationService

The method shape remains:

```java
public RechargeOrderResult complete(String requestId, UUID userId, long amount)
```

Responsibilities after refactor:

1. Validate application command inputs that are not naturally model behavior,
   such as blank `requestId`.
2. Use `WalletOrderDomainService` or a wallet amount policy for amount
   validation.
3. Load an existing order by `userId + requestId`.
4. Call `order.assertReplayMatches(userId, amount)` when an order exists.
5. Return immediately when `order.isPaid()`.
6. Create a new order through `RechargeOrder.create(...)` when none exists.
7. Post the ledger command once.
8. Ask the order for a `pay()` transition.
9. Persist the transition through the repository.
10. Reload and return the result.

The application service may continue to build `WalletLedgerCommand` because
ledger posting involves account lookup and application orchestration. It must
not compare recharge status with raw strings.

### MarketOrderApplicationService

`createOrder(...)` keeps the same public signature and orchestration role.
After refactor it should:

1. Validate request fields.
2. Resolve existing order and call `order.assertReplayMatches(...)`.
3. Lock listing and validate listing-specific constraints.
4. Build `MarketAddressSnapshot` when required for physical goods.
5. Build `MarketOrderPlacement`.
6. Create the order with `MarketOrder.place(...)`.
7. Save the order.
8. Apply stock and preloaded-inventory persistence operations.
9. Enqueue escrow wallet action.

Delivery, shipping, confirmation, and cancellation methods should delegate
state eligibility to `MarketOrder`:

- `deliverVirtualOrder(...)` calls seller check, virtual check, escrowed check,
  manual-delivery check, then persists delivery and the order transition.
- `shipPhysicalOrder(...)` calls seller check, physical check, escrowed check,
  then persists shipment and the order transition.
- `confirmOrder(...)` calls buyer check and `requestRelease()`, then enqueues a
  release action only if the conditional transition was applied.
- `cancelOrder(...)` calls buyer check and asks the order whether cancellation
  means refund pending or escrow-cancel pending, then enqueues or cancels wallet
  actions as today.

The application service continues to own:

- transaction boundaries
- repository locking
- idempotency/replay orchestration
- address repository reads
- inventory repository writes
- delivery and shipment persistence
- wallet action enqueue
- result assembly

### MarketOrderSagaApplicationService

Replace local status strings with `MarketOrderStatus`.

Methods such as `canApplyEscrow(...)`, `markEscrowSucceeded(...)`,
`markEscrowTerminalFailed(...)`, `markReleaseSucceeded(...)`, and
`markRefundSucceeded(...)` should use domain status predicates and transition
values where practical.

Saga methods can remain application services because they coordinate wallet
action outcomes, order status, listing compensation, and inventory release.

### MarketWalletActionRecoveryApplicationService

Replace `actionTypeFor(String orderStatus)` with a domain method:

```java
order.pendingWalletActionType()
```

Recovery continues to enqueue missing wallet actions and reconcile wallet
transaction IDs. The refactor should make the order responsible for mapping
pending order statuses to the expected wallet action type. The return type can
remain `String` while `MarketWalletActionType` is still a string-constant class;
converting wallet action type to an enum is optional and outside the minimum
scope of this spec.

### MarketDisputeApplicationService

Replace local order-status constants with `MarketOrderStatus`.

Opening a dispute should call a domain method such as:

```java
order.openDispute()
```

Seller/admin resolution should use domain transition methods for dispute refund
or dispute release pending states. The dispute aggregate can remain mostly as
is; this design only requires order status behavior to move into `MarketOrder`.

## Repository And Persistence Design

Repositories remain domain interfaces. Mapper and dataobject types stay in
`infrastructure.persistence`.

### Wallet Recharge Repository

Keep existing methods during the first pass:

```java
RechargeOrder findByUserIdAndRequestId(UUID userId, String requestId)
int insert(RechargeOrder order)
int updateStatus(UUID userId, String requestId, String fromStatus, String toStatus)
```

Add or migrate toward:

```java
int applyTransition(RechargeOrderTransition transition)
```

The implementation converts enum codes to strings and calls the existing mapper
SQL. The SQL remains conditional on current status.

### Market Order Repository

Keep existing `markXxx(...)` methods initially to reduce blast radius. Migrate
callers to pass domain transitions where it makes the code clearer.

Add a generic conditional transition only if it reduces duplication without
weakening semantics:

```java
int applyTransition(MarketOrderTransition transition)
```

`MarketOrderTransition` may support:

- single expected status
- multiple expected statuses
- target status
- optional `escrowTxnId`
- optional `releaseTxnId`
- optional `refundTxnId`
- optional `autoConfirmAt`

Do not replace every specialized mapper method in the first implementation if
that makes SQL less readable. The important requirement is that application
code obtains the intended transition from the domain model.

### DataObject Inheritance Follow-Up

Current dataobject classes such as `RechargeOrderDataObject` and
`MarketOrderDataObject` extend domain models. This reinforces the idea that a
domain object is just a persistence row.

This design does not require fixing that in the first implementation, but it
should be a follow-up after the state-machine refactor:

```text
DataObject -> explicit mapper method -> Domain model
Domain model -> explicit mapper method -> DataObject
```

Do not combine this follow-up with the initial state-machine migration unless
the implementation becomes simpler and tests remain focused.

## Testing Strategy

### New Domain Unit Tests

Add focused tests for wallet:

- creating a recharge order sets `CREATED`
- paid order returns `isPaid()`
- replay with same `userId` and amount passes
- replay with different user or amount fails
- `pay()` only allows `CREATED -> PAID`
- `pay()` on `PAID` is idempotent only if the chosen model explicitly supports
  idempotence; otherwise callers must check `isPaid()` first

Add focused tests for market:

- placing physical and virtual orders captures the correct snapshots
- replay matching includes buyer, listing, quantity, and physical address id
- buyer and seller action checks reject the wrong actor
- virtual delivery requires virtual goods, manual delivery, and escrowed state
- physical shipping requires physical goods and escrowed state
- confirmation is allowed from `DELIVERED` and `SHIPPED` only
- buyer cancellation maps `ESCROW_PENDING` to escrow-cancel pending
- buyer cancellation maps `ESCROWED` to refund pending
- pending wallet action mapping returns escrow, release, or refund for the
  expected pending states
- terminal or unrelated states do not produce wallet actions

### Existing Application Tests

Keep and update existing wallet recharge tests so they still prove:

- recharge credits the user wallet once
- replay returns the same order
- replay conflict fails
- concurrent duplicate insert path posts only one ledger transaction
- persisted order id remains UUIDv7

Keep and update market application, saga, wallet-action recovery, and dispute
tests so they still prove:

- order creation enqueues escrow once
- stock and preloaded inventory behavior is unchanged
- escrow success advances the order and preloaded delivery
- cancellation before and after escrow behaves the same
- release and refund wallet action outcomes advance the same statuses
- recovery enqueues missing actions and reconciles existing wallet transaction
  IDs
- dispute refund/release paths enqueue the correct wallet actions

### Architecture Tests

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

No new architecture rule is required for this refactor unless implementation
adds package-boundary changes. If implementation moves dataobject mapping away
from inheritance, existing infrastructure/domain boundary tests should still
pass.

### Focused Test Command

Run focused module tests for affected behavior:

```bash
cd backend
mvn test -pl :community-app -Dtest='WalletApplicationServiceRechargeTest,*Market*Order*Test,*Market*Wallet*Action*Test,*Market*Dispute*Test'
```

If test class names differ, use the closest focused wallet and market suites
that cover recharge, order creation, saga, recovery, and dispute flows.

## Migration Plan

### Phase 1: Wallet Recharge Pilot

1. Add `RechargeOrderStatus` and `RechargeOrderTransition`.
2. Add factory and behavior methods to `RechargeOrder`.
3. Add domain unit tests for `RechargeOrder`.
4. Update `WalletRechargeApplicationService` to use domain behavior.
5. Add repository transition method or adapt existing `updateStatus(...)` calls
   to use transition values.
6. Run wallet recharge tests and architecture tests.

This phase proves the target pattern on a small lifecycle before market changes.

### Phase 2: Market Type Extraction

1. Add market enums for order status, goods type, delivery mode, and stock mode.
2. Add `code()` and `fromCode(...)` conversions.
3. Replace application-local raw strings in market order, saga, recovery, and
   dispute services where behavior is not otherwise changing.
4. Keep mapper XML persisted codes unchanged.
5. Add enum conversion tests.
6. Run focused market tests.

### Phase 3: Market Order Creation And Replay

1. Add `MarketAddressSnapshot`, `MarketOrderPlacement`, and
   `MarketOrder.place(...)`.
2. Move replay matching into `MarketOrder`.
3. Update `MarketOrderApplicationService.createOrder(...)` to use the factory
   and replay behavior.
4. Keep stock and inventory orchestration in application service.
5. Add domain tests for placement and replay.
6. Run focused create-order tests.

### Phase 4: Market Order Transitions

1. Add `MarketOrderTransition`.
2. Move buyer/seller checks and status predicates into `MarketOrder`.
3. Update delivery, shipping, confirmation, cancellation, dispute, saga, and
   recovery application services to request transitions from `MarketOrder`.
4. Keep wallet action enqueue and compensation orchestration in application
   services.
5. Keep SQL conditional updates and ensure expected statuses match domain
   transitions.
6. Run focused market lifecycle tests.

### Phase 5: Repository Cleanup

1. Introduce `applyTransition(...)` where it reduces duplication.
2. Remove unused application-layer status constants.
3. Remove or restrict unconditional status update methods when no longer used.
4. Consider replacing dataobject inheritance with explicit conversion as a
   separate follow-up change.
5. Update handbook documents that describe wallet recharge and market order
   flows if method/class names changed materially.

## Acceptance Criteria

- `WalletRechargeApplicationService` no longer compares or writes recharge
  status with raw string literals.
- `RechargeOrder` owns creation, replay compatibility, paid predicate, and paid
  transition semantics.
- Market application services no longer define private order-status, goods-type,
  delivery-mode, or stock-mode string constants for domain decisions.
- `MarketOrder` owns order creation, replay compatibility, participant checks,
  status predicates, and transition intent for delivery, shipping,
  confirmation, cancellation, dispute, saga, and recovery flows.
- Persisted status codes remain unchanged.
- HTTP APIs and application result DTOs remain unchanged.
- Mapper SQL still performs conditional updates for concurrent state changes.
- Existing wallet recharge and market lifecycle tests pass after updates.
- `*ArchTest` passes for `community-app`.
- No domain model imports Spring, MyBatis, mapper, dataobject, controller DTO,
  application result, HTTP transport, or foreign-domain API types.

## Risks And Mitigations

- Risk: market lifecycle behavior changes during refactor.
  Mitigation: split market work into type extraction, creation/replay, and
  transition phases with focused tests after each phase.

- Risk: enum conversion breaks persisted rows with unexpected codes.
  Mitigation: fail fast in `fromCode(...)` and cover all documented current
  codes before replacing string comparisons.

- Risk: repository generic transitions obscure SQL.
  Mitigation: keep specialized mapper methods where they are clearer; the main
  goal is domain-owned transition decisions, not generic SQL for every case.

- Risk: wallet ledger posting accidentally becomes coupled to recharge order.
  Mitigation: keep account lookup and ledger posting orchestration in
  application services unless a small domain value object clearly improves
  semantics without infrastructure dependencies.

- Risk: changing dataobject inheritance expands the blast radius.
  Mitigation: treat explicit dataobject/domain conversion as follow-up unless it
  is required by the state-machine refactor.

## Documentation Updates

After implementation, update the following docs if class names, method names, or
flow wording changes:

- `docs/handbook/business-logic/wallet.md`
- `docs/handbook/business-logic/market.md`
- `docs/handbook/business-logic/workflows/market-wallet.md`
- `docs/handbook/business-flows.md`
- `docs/handbook/reliability.md`

Architecture documentation does not need to change unless new package-boundary
rules or ArchUnit tests are added.
