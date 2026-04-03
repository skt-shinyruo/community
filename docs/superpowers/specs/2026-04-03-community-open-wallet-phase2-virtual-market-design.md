# Community Open Wallet Phase 2 Virtual Market Design Spec

**Date:** 2026-04-03
**Status:** Draft for review
**Owner:** Codex

---

## 1. Background

Phase 1 has already introduced the wallet ledger, recharge, withdraw, transfer, admin freeze/reversal, and the wallet-first product surface. The next approved subsystem is the C2C virtual goods marketplace.

This phase must reuse the Phase 1 wallet ledger instead of reintroducing direct balance mutation or a second order-specific wallet model. Official reward shop flows remain in place, but Phase 2 must add a separate user-to-user virtual market entry and transaction loop.

---

## 2. Confirmed Product Decisions

The following choices are treated as fixed for Phase 2:

- Marketplace type: C2C virtual goods only
- Official reward shop and C2C virtual market coexist, with separate entry points
- Trading mode: fixed-price, buy-now only
- Seller eligibility: any logged-in user may publish
- Listing review: none; publish goes live immediately
- Order quantity: one order may purchase multiple units
- Delivery modes:
  - `PRELOADED`: seller uploads inventory before sale
  - `MANUAL`: seller lists first and delivers after purchase
- Delivery granularity: one order, one delivery action
- Release timing: buyer confirms, or system auto-releases after 24 hours
- Refund policy: no default refund after delivery
- Dispute policy: buyer may dispute; seller can accept or reject first; unresolved cases go to platform admin

These decisions intentionally optimize for a minimum closed-loop market, not a full commerce platform.

---

## 3. Goal

Ship a minimum viable virtual goods market that supports:

1. user listings
2. buyer purchase with wallet escrow
3. seller delivery
4. buyer confirm or 24-hour auto-release
5. seller-accepted refund and admin-resolved dispute handling

All money movement must stay on the Phase 1 wallet ledger using append-only wallet transactions.

---

## 4. Scope

### 4.1 In Scope

- virtual goods listings
- preloaded inventory management
- manual delivery orders
- quantity-based purchases
- escrow, release, refund on wallet ledger
- buyer and seller order views
- dispute initiation and resolution
- auto-release background job
- separate admin dispute surface

### 4.2 Out Of Scope

- auctions
- bargaining
- seller ratings
- listing moderation workflow
- rich media attachment pipeline
- digital file hosting/download delivery
- IM/chat negotiation
- partial delivery
- partial refund
- per-item confirm inside a multi-quantity order
- physical goods, shipping, address handling

---

## 5. Design Principles

### 5.1 Separate Market Domain

Do not force C2C virtual goods into the existing official reward shop tables and controllers. The official shop is a platform-owned redemption flow; the virtual market is a user-to-user escrow transaction flow.

Phase 2 therefore introduces a dedicated `market` domain for virtual marketplace behavior.

### 5.2 Wallet Ledger Is The Only Money Source Of Truth

No market order may directly mutate balances. Orders only move money through wallet transactions such as:

- `ORDER_ESCROW`
- `ORDER_RELEASE`
- `ORDER_REFUND`

### 5.3 Product State And Money State Stay Separate

Listing, order, delivery, and dispute states must not be collapsed into a single field or inferred from wallet records alone.

### 5.4 Minimum Closed Loop

Phase 2 must prefer a simpler closed loop over flexibility:

- fixed-price only
- one delivery action per order
- no partial fulfillment
- no partial refund

---

## 6. Domain Model

### 6.1 `virtual_listing`

Represents a seller-owned virtual goods listing.

Key fields:

- `listing_id`
- `seller_user_id`
- `title`
- `description`
- `unit_price`
- `delivery_mode` (`PRELOADED`, `MANUAL`)
- `stock_mode` (`FINITE`, `UNLIMITED`)
- `stock_total`
- `stock_available`
- `min_purchase_quantity`
- `max_purchase_quantity`
- `status` (`ACTIVE`, `PAUSED`, `SOLD_OUT`, `CLOSED`)
- `create_time`
- `update_time`

### 6.2 `virtual_inventory_unit`

Represents one deliverable inventory unit for `PRELOADED` listings.

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

### 6.3 `virtual_order`

Represents a buyer purchase order.

Key fields:

- `order_id`
- `request_id`
- `listing_id`
- `seller_user_id`
- `buyer_user_id`
- `quantity`
- `unit_price_snapshot`
- `total_amount`
- `delivery_mode_snapshot`
- `listing_title_snapshot`
- `status` (`CREATED`, `ESCROWED`, `DELIVERED`, `COMPLETED`, `DISPUTED`, `REFUNDED`, `CANCELLED`)
- `escrow_txn_id`
- `release_txn_id`
- `refund_txn_id`
- `auto_confirm_at`
- `create_time`
- `update_time`

### 6.4 `virtual_delivery`

Represents the order delivery record. Phase 2 keeps one effective delivery record per order.

Key fields:

- `delivery_id`
- `order_id`
- `seller_user_id`
- `delivery_type` (`PRELOADED_BATCH`, `MANUAL_TEXT`)
- `delivery_content`
- `status` (`DELIVERED`, `REPLACED`)
- `delivered_at`
- `create_time`

### 6.5 `virtual_dispute`

Represents a buyer dispute on an already delivered order.

Key fields:

- `dispute_id`
- `order_id`
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
- `SOLD_OUT`: finite preloaded stock exhausted
- `CLOSED`: permanently closed by seller

Transitions:

- `ACTIVE -> PAUSED -> ACTIVE`
- `ACTIVE -> SOLD_OUT`
- `ACTIVE|PAUSED|SOLD_OUT -> CLOSED`

### 7.2 Preloaded Order Flow

1. buyer creates order
2. system validates listing, quantity, buyer wallet, and stock
3. inventory units move `AVAILABLE -> RESERVED`
4. wallet posts `ORDER_ESCROW`
5. order becomes `ESCROWED`
6. system assembles delivery payload and creates `virtual_delivery`
7. order becomes `DELIVERED`
8. buyer confirms, or `auto_confirm_at` is reached
9. wallet posts `ORDER_RELEASE`
10. order becomes `COMPLETED`
11. reserved inventory becomes `DELIVERED`

### 7.3 Manual Delivery Order Flow

1. buyer creates order
2. system validates listing and buyer wallet
3. wallet posts `ORDER_ESCROW`
4. order becomes `ESCROWED`
5. seller submits one full delivery payload
6. order becomes `DELIVERED`
7. buyer confirms, or `auto_confirm_at` is reached
8. wallet posts `ORDER_RELEASE`
9. order becomes `COMPLETED`

### 7.4 Cancel And Refund Rules

Before delivery:

- buyer may cancel while order is `ESCROWED`
- wallet posts `ORDER_REFUND`
- order becomes `CANCELLED`
- preloaded reserved inventory returns to `AVAILABLE`

After delivery:

- no direct refund path
- buyer may open dispute only

### 7.5 Dispute Flow

1. buyer opens dispute on `DELIVERED` order
2. order becomes `DISPUTED`
3. seller either:
   - accepts refund
   - rejects refund
4. if seller accepts:
   - wallet posts `ORDER_REFUND`
   - order becomes `REFUNDED`
5. if seller rejects and dispute remains unresolved:
   - admin resolves
   - admin chooses `REFUND` or `RELEASE`
   - wallet posts matching transaction
   - order becomes `REFUNDED` or `COMPLETED`

---

## 8. Wallet Integration

Phase 2 must extend wallet transaction semantics with at least:

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

Do not introduce market-specific balance fields.

---

## 9. API Design

### 9.1 Public Read APIs

- `GET /api/market/virtual/listings`
- `GET /api/market/virtual/listings/{listingId}`

### 9.2 Seller APIs

- `POST /api/market/virtual/listings`
- `PUT /api/market/virtual/listings/{listingId}`
- `POST /api/market/virtual/listings/{listingId}/pause`
- `POST /api/market/virtual/listings/{listingId}/resume`
- `POST /api/market/virtual/listings/{listingId}/close`

### 9.3 Inventory APIs

- `GET /api/market/virtual/listings/{listingId}/inventory`
- `POST /api/market/virtual/listings/{listingId}/inventory`
- `POST /api/market/virtual/inventory/{inventoryUnitId}/invalidate`

### 9.4 Order APIs

- `POST /api/market/virtual/orders`
- `GET /api/market/virtual/orders/buying`
- `GET /api/market/virtual/orders/selling`
- `GET /api/market/virtual/orders/{orderId}`
- `POST /api/market/virtual/orders/{orderId}/deliver`
- `POST /api/market/virtual/orders/{orderId}/confirm`
- `POST /api/market/virtual/orders/{orderId}/cancel`

### 9.5 Dispute APIs

- `POST /api/market/virtual/orders/{orderId}/disputes`
- `POST /api/market/virtual/disputes/{disputeId}/seller-accept`
- `POST /api/market/virtual/disputes/{disputeId}/seller-reject`

### 9.6 Admin APIs

- `GET /api/admin/market/virtual/disputes`
- `POST /api/admin/market/virtual/disputes/{disputeId}/resolve-refund`
- `POST /api/admin/market/virtual/disputes/{disputeId}/resolve-release`

---

## 10. Frontend Surface

Phase 2 adds a separate virtual market UX alongside the official reward shop.

Recommended routes:

- `/market/virtual`
- `/market/virtual/listings/:listingId`
- `/market/virtual/publish`
- `/market/virtual/my-listings`
- `/market/virtual/my-listings/:listingId/inventory`
- `/market/virtual/orders/buying`
- `/market/virtual/orders/selling`
- `/market/virtual/orders/:orderId`
- `/admin/market/virtual/disputes`

Page responsibilities:

- listing page: browse and search
- detail page: quantity select + buy
- publish page: create/edit listing
- inventory page: manage preloaded stock
- buyer orders: confirm, cancel, dispute
- seller orders: deliver, respond to disputes
- admin disputes: final resolution

Official reward shop remains separate and unchanged in this phase.

---

## 11. Operational Requirements

### 11.1 Auto-Release Job

Phase 2 requires a scheduled job that:

- scans delivered orders where `auto_confirm_at <= now`
- releases escrow idempotently
- transitions order to `COMPLETED`

### 11.2 Idempotency

The following must be idempotent:

- create order
- deliver order
- confirm order
- cancel order
- seller dispute response
- admin dispute resolution
- auto-release job execution

### 11.3 Snapshot Rules

Orders must snapshot at least:

- title
- delivery mode
- unit price
- quantity
- total amount

Do not render historical orders from mutable listing state.

---

## 12. Recommended Implementation Shape

### 12.1 Backend

Introduce a dedicated `market` domain, for example:

- `market.entity`
- `market.mapper`
- `market.service`
- `market.controller`
- `market.dto`
- `market.api.*` where cross-domain collaboration is needed

Wallet collaboration must go through wallet API interfaces, not direct foreign service dependencies.

### 12.2 Reuse Points

Phase 2 should reuse:

- wallet ledger and transaction posting
- wallet account query and action APIs
- existing auth and admin security model
- current frontend router and wallet-first navigation patterns

### 12.3 Do Not Reuse

Do not force C2C virtual market behavior into:

- `reward_item`
- `reward_order`
- `RewardShopController`
- `RewardRedemptionService`

Those remain official shop flows.

---

## 13. Validation

Phase 2 is complete only when all of the following are true:

1. buyer purchase creates escrowed wallet transaction
2. preloaded listing auto-delivers reserved inventory
3. manual listing requires seller delivery before release
4. buyer confirm releases escrow to seller
5. auto-release releases overdue delivered orders
6. pre-delivery cancel refunds buyer and unlocks reserved stock
7. seller-accepted dispute refunds buyer
8. admin resolution can refund buyer or release seller funds
9. official reward shop remains usable and unaffected
10. architecture tests stay green

---

## 14. Recommended Conclusion

Phase 2 should ship as a dedicated virtual market domain layered on top of the Phase 1 wallet ledger. The product remains simple for users: browse listings, buy with escrow, receive delivery, confirm, or dispute. Internally, the design stays strict: separate listing, inventory, order, delivery, and dispute models; append-only wallet transactions; no reuse of official shop order tables for user-to-user trade.

This gives the project a real marketplace foundation without prematurely taking on auctions, chat negotiation, or physical-order complexity.
