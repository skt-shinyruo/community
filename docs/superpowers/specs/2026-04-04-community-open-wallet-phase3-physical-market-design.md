# Community Open Wallet Phase 3 Physical Market Design Spec

**Date:** 2026-04-04
**Status:** Draft for review
**Owner:** Codex

---

## 1. Background

The total economy direction is defined in [2026-04-02-community-open-wallet-economy-design.md](/home/feng/code/project/community/.worktrees/open-wallet-phase3-physical-market/docs/superpowers/specs/2026-04-02-community-open-wallet-economy-design.md). That spec fixes the four approved subsystems:

1. wallet and ledger
2. user-to-user transfer
3. C2C virtual goods marketplace
4. C2C physical goods marketplace

Phase 1 has already delivered the wallet ledger, recharge, withdraw, transfer, and admin freeze/reversal. Phase 2 has delivered a virtual-goods market on the same branch line, but it currently exists as a virtual-only implementation with `Virtual*` entities, DTOs, controllers, and routes.

Phase 3 must finish the approved economy scope by adding physical goods trading, but the user has also fixed two additional design constraints:

- the market should not be split by separate virtual-vs-physical product entry points
- virtual and physical goods should be distinguished by explicit data fields, not by separate market namespaces

Because Phase 2 and Phase 3 are still being developed on the same unmerged PR chain, this phase does not need to preserve or migrate historical `virtual_*` production data. Phase 3 may replace the Phase 2 transitional market model with the final unified market model before merge.

---

## 2. Confirmed Product Decisions

The following are treated as fixed for Phase 3:

- marketplace type: one unified C2C market that supports both virtual and physical goods
- goods type marker: `goodsType`, with values `VIRTUAL` and `PHYSICAL`
- trading mode: fixed-price, buy-now only
- seller eligibility: any logged-in user may publish
- listing review: none; publish goes live immediately
- quantity support: one order may purchase multiple units
- physical fulfillment model:
  - buyer selects a saved address when ordering
  - order stores an immutable address snapshot
  - seller records one shipment for the order
  - buyer confirms receipt, or the system auto-confirms after the physical-order timeout window
- physical fulfillment simplifications:
  - one order, one address snapshot
  - one order, one shipment record
  - no multi-package shipment
  - no logistics provider integration
  - no reverse-logistics workflow
- virtual fulfillment remains supported:
  - `PRELOADED`
  - `MANUAL`
- cancel rule: order may be cancelled only before delivery/shipment
- dispute rule: keep the Phase 2 seller-first dispute skeleton and admin fallback
- old `virtual_*` tables, DTOs, and routes are not compatibility targets in Phase 3; the branch may converge directly to the final unified market model

These decisions optimize for a minimum closed-loop commerce system, not a full marketplace platform.

---

## 3. Goal

Ship the final Phase 3 unified market model that supports:

1. one market surface for virtual and physical goods
2. virtual goods listing, inventory, delivery, confirm, and dispute
3. physical goods listing, address selection, shipment, receipt confirm, and dispute
4. one wallet-escrow transaction loop for both goods types
5. one market domain model that will replace the transitional Phase 2 virtual-only structure before merge

All money movement must continue to use the Phase 1 wallet ledger with append-only wallet transactions.

---

## 4. Scope

### 4.1 In Scope

- unified market listing and order model for both goods types
- explicit `goodsType` marker in read and write models
- physical address book
- physical order shipment record
- unified market list/detail/order pages
- unified market backend APIs
- refactor of Phase 2 virtual market implementation into unified `market_*` structures
- unified dispute handling for both goods types
- unified auto-confirm / auto-release flow with goods-type-specific timing

### 4.2 Out Of Scope

- bargaining
- auctions
- seller ratings
- listing moderation workflow
- freight pricing templates
- logistics API polling or tracking timeline
- multi-package shipment
- partial fulfillment
- partial refund
- return shipping workflow
- reverse-logistics state machine
- chat negotiation
- preserving old `virtual_*` historical data after schema convergence

---

## 5. Design Principles

### 5.1 One Product Market, One Wallet Ledger

Users should experience one C2C market, not separate virtual and physical products. The product-layer distinction is a field on the item and order, not a separate top-level market concept.

The wallet ledger remains the only money source of truth. Market code must never mutate balances directly.

### 5.2 One Main Listing Table And One Main Order Table

The final market model should converge on:

- one main listing table
- one main order table

Both tables use `goods_type` as the product discriminator. This keeps the product model unified while still allowing type-specific behavior.

### 5.3 Separate Fact Tables For Type-Specific Side Data

The main tables should not become catch-all dumping grounds for every type-specific field. Type-specific side data should be modeled separately where it represents a distinct concept:

- virtual inventory belongs in inventory records
- virtual delivery facts belong in delivery records
- user addresses belong in an address book
- shipment events belong in shipment records

### 5.4 Product State And Money State Stay Separate

Order state is not the same as wallet state. Listing, order, delivery/shipment, dispute, and wallet transactions must stay explicitly modeled.

### 5.5 Converge Before Merge

Because the open-wallet work is still on one PR chain, Phase 3 should converge the transitional Phase 2 virtual-only model into the final unified market model now, instead of carrying a long-term split between `Virtual*` and `Physical*` implementations.

---

## 6. Domain Model

### 6.1 `market_listing`

Represents one user-owned market listing for either virtual or physical goods.

Key fields:

- `listing_id`
- `seller_user_id`
- `goods_type` (`VIRTUAL`, `PHYSICAL`)
- `title`
- `description`
- `unit_price`
- `stock_total`
- `stock_available`
- `min_purchase_quantity`
- `max_purchase_quantity`
- `status` (`ACTIVE`, `PAUSED`, `SOLD_OUT`, `CLOSED`)
- `delivery_mode` (`PRELOADED`, `MANUAL`, nullable for physical goods)
- `stock_mode` (`FINITE`, `UNLIMITED`, nullable or constrained for physical goods)
- `create_time`
- `update_time`

Rules:

- virtual goods may use `delivery_mode` and `stock_mode`
- physical goods must use `goods_type=PHYSICAL`
- physical goods in Phase 3 V1 should use finite stock only

### 6.2 `market_inventory_unit`

Represents one deliverable inventory unit for virtual preloaded listings only.

Key fields:

- `inventory_unit_id`
- `listing_id`
- `seller_user_id`
- `payload_type` (`TEXT`, `CODE`, `LINK`)
- `payload_content`
- `status` (`AVAILABLE`, `RESERVED`, `DELIVERED`, `INVALID`)
- `reserved_order_id`
- `delivered_at`
- `create_time`

This table is ignored for physical goods.

### 6.3 `market_order`

Represents one buyer purchase order for either virtual or physical goods.

Key fields:

- `order_id`
- `request_id`
- `listing_id`
- `goods_type` (`VIRTUAL`, `PHYSICAL`)
- `seller_user_id`
- `buyer_user_id`
- `quantity`
- `unit_price_snapshot`
- `total_amount`
- `listing_title_snapshot`
- `delivery_mode_snapshot`
- `status`
- `escrow_txn_id`
- `release_txn_id`
- `refund_txn_id`
- `auto_confirm_at`
- physical address snapshot fields:
  - `receiver_name_snapshot`
  - `receiver_phone_snapshot`
  - `province_snapshot`
  - `city_snapshot`
  - `district_snapshot`
  - `detail_address_snapshot`
  - `postal_code_snapshot`
- `create_time`
- `update_time`

The address snapshot fields are only populated for physical goods orders.

### 6.4 `market_delivery`

Represents one virtual-goods delivery record for an order.

Key fields:

- `delivery_id`
- `order_id`
- `seller_user_id`
- `delivery_type` (`PRELOADED_BATCH`, `MANUAL_TEXT`)
- `delivery_content`
- `status`
- `delivered_at`
- `create_time`

`PRELOADED` listings may derive buyer-visible payloads from delivered inventory units, but `MANUAL` listings still need a durable delivery fact table. Phase 3 therefore keeps delivery as an explicit side table in the unified model.

### 6.5 `market_address`

Represents one buyer-owned delivery address.

Key fields:

- `address_id`
- `user_id`
- `receiver_name`
- `receiver_phone`
- `province`
- `city`
- `district`
- `detail_address`
- `postal_code`
- `is_default`
- `status` (`ACTIVE`, `DELETED`)
- `create_time`
- `update_time`

This is mutable user profile data. It is never used as the historical source of truth for placed orders.

### 6.6 `market_shipment`

Represents the physical shipment record for one physical order.

Key fields:

- `shipment_id`
- `order_id`
- `seller_user_id`
- `carrier_name`
- `tracking_no`
- `shipping_remark`
- `shipped_at`
- `create_time`
- `update_time`

Phase 3 V1 allows one shipment record per physical order.

### 6.7 `market_dispute`

Represents a buyer dispute for either goods type.

Key fields:

- `dispute_id`
- `order_id`
- `goods_type`
- `buyer_user_id`
- `seller_user_id`
- `status` (`OPEN`, `SELLER_ACCEPTED`, `SELLER_REJECTED`, `ADMIN_RESOLVED`, `CLOSED`)
- `reason`
- `buyer_note`
- `seller_note`
- `resolution_type` (`REFUND`, `RELEASE`, `CANCEL`)
- `resolved_by`
- `resolved_at`
- `create_time`
- `update_time`

Only one active dispute is allowed per order.

---

## 7. State Machines

### 7.1 Listing States

- `ACTIVE`: visible and purchasable
- `PAUSED`: visible but not purchasable
- `SOLD_OUT`: finite stock exhausted
- `CLOSED`: permanently closed by seller

Transitions:

- `ACTIVE -> PAUSED -> ACTIVE`
- `ACTIVE -> SOLD_OUT`
- `ACTIVE|PAUSED|SOLD_OUT -> CLOSED`

### 7.2 Virtual Order Flow

1. buyer creates order
2. system validates listing, quantity, wallet, and stock
3. wallet posts `ORDER_ESCROW`
4. order becomes `ESCROWED`
5. if preloaded:
   - reserve inventory
   - assemble delivery payload
   - order becomes `DELIVERED`
6. if manual:
   - seller submits one full delivery payload
   - order becomes `DELIVERED`
7. buyer confirms, or `auto_confirm_at` is reached
8. wallet posts `ORDER_RELEASE`
9. order becomes `COMPLETED`

### 7.3 Physical Order Flow

1. buyer selects an active address
2. system snapshots the address into the order
3. wallet posts `ORDER_ESCROW`
4. order becomes `ESCROWED`
5. seller records shipment data
6. order becomes `SHIPPED`
7. buyer confirms receipt, or `auto_confirm_at` is reached
8. wallet posts `ORDER_RELEASE`
9. order becomes `RECEIVED`
10. order becomes `COMPLETED`

Phase 3 V1 may collapse `RECEIVED -> COMPLETED` into a single write if the implementation does not need separate user-visible dwell time, but the outward semantics remain “buyer received, seller got released”.

### 7.4 Cancel And Refund Rules

Before virtual delivery or physical shipment:

- buyer may cancel while order is `ESCROWED`
- wallet posts `ORDER_REFUND`
- order becomes `CANCELLED`
- reserved virtual inventory returns to `AVAILABLE`

After virtual delivery or physical shipment:

- no direct refund path
- buyer may open dispute only

### 7.5 Dispute Flow

1. buyer opens dispute on delivered or shipped order
2. order becomes `DISPUTED`
3. seller either:
   - accepts refund
   - rejects refund
4. if seller accepts:
   - wallet posts `ORDER_REFUND`
   - order becomes `REFUNDED`
5. if seller rejects:
   - admin resolves
   - admin chooses `REFUND` or `RELEASE`
   - wallet posts the matching transaction
   - order becomes `REFUNDED` or `COMPLETED`

---

## 8. Wallet Integration

Phase 3 continues to use the Phase 1 wallet ledger and the Phase 2 market transaction semantics:

- `ORDER_ESCROW`
- `ORDER_RELEASE`
- `ORDER_REFUND`

Canonical postings:

- buyer escrow:
  - `USER_WALLET:buyer -> ORDER_ESCROW`
- seller release:
  - `ORDER_ESCROW -> USER_WALLET:seller`
- buyer refund:
  - `ORDER_ESCROW -> USER_WALLET:buyer`

No market-specific balance fields may be introduced.

---

## 9. API Design

### 9.1 Unified Listing APIs

- `GET /api/market/listings`
- `GET /api/market/listings/{listingId}`
- `GET /api/market/my-listings`
- `POST /api/market/listings`
- `PUT /api/market/listings/{listingId}`
- `POST /api/market/listings/{listingId}/pause`
- `POST /api/market/listings/{listingId}/resume`
- `POST /api/market/listings/{listingId}/close`

Read and write payloads include `goodsType`.

### 9.2 Virtual-Only Inventory APIs

- `GET /api/market/listings/{listingId}/inventory`
- `POST /api/market/listings/{listingId}/inventory`
- `POST /api/market/inventory/{inventoryUnitId}/invalidate`

These endpoints validate that the target listing has `goodsType=VIRTUAL`.

### 9.3 Unified Order APIs

- `POST /api/market/orders`
- `GET /api/market/orders/buying`
- `GET /api/market/orders/selling`
- `GET /api/market/orders/{orderId}`
- `POST /api/market/orders/{orderId}/confirm`
- `POST /api/market/orders/{orderId}/cancel`
- `POST /api/market/orders/{orderId}/disputes`

### 9.4 Type-Specific Fulfillment APIs

- virtual delivery:
  - `POST /api/market/orders/{orderId}/deliver`
- physical shipment:
  - `POST /api/market/orders/{orderId}/ship`

The order itself decides whether the action is valid through `goodsType`.

### 9.5 Address APIs

- `GET /api/market/addresses`
- `POST /api/market/addresses`
- `PUT /api/market/addresses/{addressId}`
- `DELETE /api/market/addresses/{addressId}`

### 9.6 Unified Dispute Admin APIs

- `GET /api/admin/market/disputes`
- `POST /api/admin/market/disputes/{disputeId}/resolve-refund`
- `POST /api/admin/market/disputes/{disputeId}/resolve-release`

---

## 10. Frontend Surface

Phase 3 replaces the virtual-only market surface with one unified market surface.

Recommended routes:

- `/market`
- `/market/listings/:listingId`
- `/market/publish`
- `/market/my-listings`
- `/market/orders/buying`
- `/market/orders/selling`
- `/market/orders/:orderId`
- `/market/addresses`
- `/admin/market/disputes`

Page responsibilities:

- market list: mixed virtual and physical listings
- listing detail: branch UI by `goodsType`
- publish page: choose `goodsType` first, then reveal type-specific form fields
- my listings: unified seller workspace with type-specific actions
- buyer orders: confirm, cancel, dispute
- seller orders:
  - virtual: deliver
  - physical: ship
- address page: maintain buyer delivery addresses
- admin disputes: unified final resolution

The official reward shop remains separate. Phase 3 unifies the C2C market, not the reward shop.

---

## 11. Phase 2 Convergence Strategy

Phase 2 virtual-market code is a transitional implementation and should not be expanded further as a parallel permanent model.

Phase 3 should converge the branch to the final shape by:

1. replacing `virtual_*` storage with `market_*` storage
2. replacing `Virtual*` DTOs, services, and controllers with unified market models
3. replacing `/market/virtual/**` frontend routes with unified `/market/**` routes
4. preserving virtual-goods behavior functionally, but no longer preserving the old virtual-specific schema or route shape

No historical `virtual_*` data migration is required in this phase because the branch has not merged to production and the user explicitly does not want to preserve old virtual-market history. This assumption is fixed for the current Phase 3 scope.

---

## 12. Operational Requirements

### 12.1 Auto-Confirm And Auto-Release Job

The market background job must scan due orders and release escrow idempotently.

Timing rules:

- virtual orders: keep the Phase 2 24-hour auto-confirm window
- physical orders: use a longer auto-confirm window, recommended default 7 days

Both goods types should use the same scheduler entry point; due time comes from the order record.

### 12.2 Idempotency

The following must be idempotent:

- create listing
- create order
- deliver virtual order
- ship physical order
- confirm order
- cancel order
- open dispute
- seller dispute response
- admin dispute resolution
- auto-confirm / auto-release job execution

### 12.3 Snapshot Rules

Orders must snapshot at least:

- title
- goods type
- unit price
- quantity
- total amount
- virtual delivery mode when relevant
- physical address when relevant

Do not render historical orders from mutable listing state or mutable address-book state.

---

## 13. Validation

Phase 3 is complete only when all of the following are true:

1. unified listing read APIs return both virtual and physical goods with `goodsType`
2. virtual listing publish, inventory, order, delivery, confirm, dispute, and auto-release still work on the unified model
3. physical listing publish, address selection, shipment, confirm receipt, dispute, and cancel-before-ship work
4. wallet escrow, release, and refund remain append-only and ledger-backed
5. admin dispute resolution can refund buyer or release seller funds for both goods types
6. the frontend unified market pages branch correctly by `goodsType`
7. the wallet/reward-shop surfaces remain unaffected
8. architecture tests remain green

---

## 14. Recommended Conclusion

Phase 3 should finish the open-wallet market roadmap by converging the virtual-only Phase 2 implementation into a final unified market model, then extending that model to physical goods. The product becomes simpler, not more fragmented: one market, one listing concept, one order concept, one wallet escrow loop, with `goodsType` explicitly deciding fulfillment behavior.

Internally, the design stays disciplined:

- one main listing table
- one main order table
- side tables for inventory, addresses, shipments, and disputes
- append-only wallet transactions
- no preservation burden for transitional `virtual_*` history

This gives the project a coherent forum economy model before PR #13 merges: Phase 1 wallet, Phase 2 virtual-trade capability, and Phase 3 final unified market with physical goods support.
