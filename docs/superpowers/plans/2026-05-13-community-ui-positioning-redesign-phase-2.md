# Community UI Positioning Redesign Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Complete the Phase 2 follow-up from the May 13 positioning pass by finishing market lifecycle surfaces, order timelines, address management polish, admin console semantics, and modal/detail accessibility.

**Architecture:** Reuse the Phase 1 shell, route-aware copy, product tokens, `UiPageHeader`, `UiToolbar`, `UiState`, `UiCard`, and page-level state helpers. Keep API semantics unchanged; views should display available data honestly through product labels, timelines, risk copy, and accessible interaction containers. Non-trivial market display text belongs in `frontend/src/views/marketState.js`; modal accessibility should be shared through existing modal components where possible.

**Tech Stack:** Vue 3, Vue Router 4, Pinia, CSS custom properties, Vitest, Vue Test Utils, Vite, Chrome DevTools browser checks.

## Execution Status - 2026-05-13

Phase 2 implementation and verification are complete in the shared working tree.

- Market lifecycle helpers now expose trust, fulfillment, next-action, lifecycle, address, and dispute labels in `frontend/src/views/marketState.js`.
- Market detail, publish, buying/selling orders, inventory, and addresses present the Phase 2 product grammar.
- Admin analytics, moderation, user management, wallet admin, disputes, and ops console now emphasize operations, audit, and risk semantics.
- Shared and custom modals expose dialog semantics and Escape close behavior.
- Verification passed for focused Phase 2 tests, full frontend tests, production build, matrix freshness, and representative desktop/mobile browser checks.
- Browser evidence is recorded at `target/community-playwright-regression/reports/pw-20260513222643.md` and supporting screenshots in `target/community-playwright-regression/artifacts/pw-20260513222643/`.
- Commit steps in this plan were completed after verification in the shared workspace.

---

## Scope

This plan completes the explicit Phase 2 follow-up list in `2026-05-13-community-ui-positioning-redesign-phase-1.md`:

1. Full market publish/detail/order lifecycle redesign.
2. Buying/selling order list and order detail timeline.
3. Address management polish.
4. Admin analytics, moderation, user management, wallet admin, disputes, and ops console.
5. Deeper accessibility pass for detail pages and modals.

Out of scope:

- Backend changes.
- API contract changes.
- New frontend framework or design system.
- Fake metrics or fabricated lifecycle data.
- Reworking Phase 1 routes that already passed unless Phase 2 verification finds a defect.

## Files And Responsibilities

Modify:

- `frontend/src/views/marketState.js`: expose order lifecycle timeline labels, next-action copy, inventory status labels, address default labels, and dispute/fund state copy.
- `frontend/src/views/marketState.test.js`: guard market lifecycle and address/dispute state shaping.
- `frontend/src/views/MarketDetailView.vue`: make price, seller, stock, escrow/risk note, fulfillment, and purchase action the visual anchor.
- `frontend/src/views/MarketPublishView.vue`: turn publishing into a workflow form with type/fulfillment/inventory review copy and clear validation messages.
- `frontend/src/views/MarketMyListingsView.vue`: make seller listing management status-first.
- `frontend/src/views/MarketInventoryView.vue`: remove raw inventory status and show available/locked/sold/invalid product labels.
- `frontend/src/views/MarketBuyingOrdersView.vue`: show status-first order rows with escrow/funds, fulfillment state, next action, and amount.
- `frontend/src/views/MarketSellingOrdersView.vue`: same as buying orders, tuned to seller actions.
- `frontend/src/views/MarketOrderDetailView.vue`: add lifecycle timeline and audit/context sections.
- `frontend/src/views/MarketAddressesView.vue`: polish compact address management, default address copy, and safe actions.
- `frontend/src/views/MarketOrderViews.test.js`, `MarketSellerViews.test.js`, `MarketAddressesView.test.js`, `MarketViews.test.js`: assert Phase 2 market and order product semantics.
- `frontend/src/views/AnalyticsView.vue`: add explicit data freshness/scope copy and operational dashboard semantics.
- `frontend/src/views/ModerationView.vue`: add queue/audit/risk copy and improve action modal accessibility.
- `frontend/src/views/UserManagementView.vue`: make role changes audit-first.
- `frontend/src/views/WalletAdminView.vue`: separate dangerous actions, traceability, and session log.
- `frontend/src/views/AdminMarketDisputesView.vue`: show claims, fund state, and resolution actions clearly.
- `frontend/src/views/OpsConsoleView.vue`: keep high-risk operations visually distinct while using product tokens.
- `frontend/src/views/AnalyticsView.test.js`, `ModerationView.test.js`, `UserManagementView.test.js`, `WalletAdminView.test.js`, `AdminMarketDisputesView.test.js`, `OpsConsoleView.test.js`: assert admin Phase 2 copy and workflows.
- `frontend/src/components/ui/UiModalConfirm.vue`: add dialog semantics, Escape handling, and title/description associations.
- `frontend/src/components/ui/UiModalConfirm.test.js`: create coverage for dialog semantics and keyboard close.
- `frontend/src/components/modals/ReportModal.vue`: add dialog semantics and Escape close.
- `frontend/src/components/modals/EditContentModal.vue`: add dialog semantics and Escape close.
- `frontend/src/components/modals/EditContentModal.test.js`: assert edit modal accessibility.
- `frontend/src/views/SearchView.vue`: add accessible dialog semantics to the reindex confirmation modal.
- `frontend/src/views/SearchView.test.js`: assert accessible reindex dialog for admins.
- `frontend/src/styles/pages.css`, `frontend/src/styles/components.css`: add/adjust compact timeline, risk, admin, modal, and address styles if needed.
- `docs/handbook/frontend.md`: update if Phase 2 changes page-state helper or modal accessibility conventions.

Do not modify backend code or deployment files.

---

## Task 1: Market Lifecycle State Helpers

**Files:**

- Modify: `frontend/src/views/marketState.js`
- Modify: `frontend/src/views/marketState.test.js`

- [x] **Step 1: Write failing market state tests**

Add tests that call `buildMarketState` with orders, disputes, addresses, and inventory-like records where applicable:

```js
it('builds order lifecycle and next-action copy for buyer and seller rows', () => {
  const state = buildMarketState({
    orders: [
      {
        orderId: 9,
        goodsType: 'PHYSICAL',
        status: 'SHIPPED',
        escrowStatus: 'ESCROWED',
        fulfillmentStatus: 'SHIPPED',
        totalAmount: 88,
        listingTitleSnapshot: '键盘'
      }
    ]
  })

  expect(state.orders[0]).toMatchObject({
    fundsLabel: '托管中',
    fulfillmentLabel: '已发货',
    nextActionLabel: '等待买家确认收货'
  })
  expect(state.orders[0].lifecycleSteps.map((it) => it.label)).toEqual([
    '已创建',
    '资金托管',
    '已发货',
    '待确认',
    '无争议'
  ])
})

it('labels address, inventory, and dispute operational states', () => {
  const state = buildMarketState({
    disputes: [{ disputeId: 3, goodsType: 'VIRTUAL', status: 'SELLER_REJECTED', reason: '未收到', fundState: 'ESCROWED' }],
    addresses: [{ addressId: 7, receiverName: '李四', city: '北京', detailAddress: '中关村 1 号', defaultAddress: true }],
    inventory: [{ inventoryUnitId: 1, status: 'AVAILABLE' }, { inventoryUnitId: 2, status: 'INVALIDATED' }]
  })

  expect(state.disputes[0]).toMatchObject({
    fundStateLabel: '资金托管中',
    nextActionLabel: '需要管理员裁定'
  })
  expect(state.addresses[0].defaultLabel).toBe('默认地址')
  expect(state.inventory[0].statusLabel).toBe('可售')
  expect(state.inventory[1].statusLabel).toBe('已失效')
})
```

- [x] **Step 2: Run state tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/views/marketState.test.js
```

Expected: FAIL because `lifecycleSteps`, `fundsLabel`, `nextActionLabel`, and `inventory` state shaping are not implemented.

- [x] **Step 3: Implement state shaping**

In `marketState.js`:

- Add `fundsLabel(status)`, `fulfillmentStateLabel(order)`, `nextOrderActionLabel(order)`, `buildLifecycleSteps(order)`, `inventoryStatusLabel(status)`, `fundStateLabel(status)`, and `nextDisputeActionLabel(dispute)`.
- Include `fundsLabel`, `fulfillmentLabel`, `nextActionLabel`, and `lifecycleSteps` on every order.
- Include `fundStateLabel` and `nextActionLabel` on every dispute.
- Add an `inventory` array to the returned state, mapping `statusLabel` for each item.

- [x] **Step 4: Re-run state tests**

Run:

```bash
cd frontend
npm test -- src/views/marketState.test.js
```

Expected: PASS.

---

## Task 2: Market Lifecycle Views

**Files:**

- Modify: `frontend/src/views/MarketDetailView.vue`
- Modify: `frontend/src/views/MarketPublishView.vue`
- Modify: `frontend/src/views/MarketMyListingsView.vue`
- Modify: `frontend/src/views/MarketInventoryView.vue`
- Modify: `frontend/src/views/MarketBuyingOrdersView.vue`
- Modify: `frontend/src/views/MarketSellingOrdersView.vue`
- Modify: `frontend/src/views/MarketOrderDetailView.vue`
- Modify: `frontend/src/views/MarketAddressesView.vue`
- Modify: `frontend/src/views/MarketViews.test.js`
- Modify: `frontend/src/views/MarketOrderViews.test.js`
- Modify: `frontend/src/views/MarketSellerViews.test.js`
- Modify: `frontend/src/views/MarketAddressesView.test.js`
- Modify: `frontend/src/styles/pages.css`

- [x] **Step 1: Write failing market view tests**

Add assertions that:

- Listing detail contains `钱包托管`, `履约`, `库存`, `安全下单`, and a risk note near the purchase action.
- Publish view contains `发布流程`, `交易信息`, `履约信息`, and no old explanatory copy about separate market pages.
- Inventory view renders product labels such as `可售`/`已失效`, not raw `AVAILABLE`.
- Buying and selling order rows contain `托管中`, fulfillment state, next action, and amount.
- Order detail contains `.market-lifecycle`, `已创建`, `资金托管`, `履约`, `确认`, and `争议`.
- Address rows contain `默认地址`, `编辑`, `删除`, and the snapshot explanation.

- [x] **Step 2: Run focused market view tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/views/MarketViews.test.js src/views/MarketOrderViews.test.js src/views/MarketSellerViews.test.js src/views/MarketAddressesView.test.js
```

Expected: FAIL before the new Phase 2 layout and copy are implemented.

- [x] **Step 3: Update market views**

Implement the Phase 2 product grammar:

- `MarketDetailView.vue`: use a summary/action/detail layout. The purchase panel must show quantity, address when needed, `钱包托管`, risk copy, and a primary action named `安全下单`.
- `MarketPublishView.vue`: use workflow sections named `发布流程`, `交易信息`, `履约信息`, and `库存预存`; remove copy about not splitting virtual market pages.
- `MarketMyListingsView.vue`: show seller listing rows with `statusLabel`, `trustLabel`, `fulfillmentLabel`, stock, price, and next management actions.
- `MarketInventoryView.vue`: build state through `buildMarketState({ inventory })` and display `statusLabel`.
- `MarketBuyingOrdersView.vue` and `MarketSellingOrdersView.vue`: render status-first order rows with `fundsLabel`, `fulfillmentLabel`, `nextActionLabel`, and `totalAmountText`.
- `MarketOrderDetailView.vue`: render `.market-lifecycle` from `detail.lifecycleSteps` and an audit/context block with request id, funds, fulfillment, and next action.
- `MarketAddressesView.vue`: keep creation compact, show address rows with default label and explicit edit/delete actions.
- `pages.css`: add `.market-lifecycle`, `.market-lifecycle-step`, `.market-risk-note`, `.market-workflow-section`, and responsive wrapping.

- [x] **Step 4: Re-run focused market tests**

Run:

```bash
cd frontend
npm test -- src/views/MarketViews.test.js src/views/MarketOrderViews.test.js src/views/MarketSellerViews.test.js src/views/MarketAddressesView.test.js src/views/marketState.test.js
```

Expected: PASS.

---

## Task 3: Admin Console Surfaces

**Files:**

- Modify: `frontend/src/views/AnalyticsView.vue`
- Modify: `frontend/src/views/ModerationView.vue`
- Modify: `frontend/src/views/UserManagementView.vue`
- Modify: `frontend/src/views/WalletAdminView.vue`
- Modify: `frontend/src/views/AdminMarketDisputesView.vue`
- Modify: `frontend/src/views/OpsConsoleView.vue`
- Modify: `frontend/src/views/AnalyticsView.test.js`
- Modify: `frontend/src/views/ModerationView.test.js`
- Modify: `frontend/src/views/UserManagementView.test.js`
- Modify: `frontend/src/views/WalletAdminView.test.js`
- Modify: `frontend/src/views/AdminMarketDisputesView.test.js`
- Modify: `frontend/src/views/OpsConsoleView.test.js`

- [x] **Step 1: Write failing admin surface tests**

Add assertions that:

- Analytics includes `数据范围`, `数据新鲜度`, and no placeholder chart copy.
- Moderation includes `举报队列`, `处置审计`, `风险动作`, and its modal uses dialog semantics.
- User management includes `审计原因`, `目标角色`, and high-risk role warning.
- Wallet admin includes `高风险资金操作`, `审计追踪`, and separates freeze/reversal controls from session log.
- Admin disputes include `资金状态`, buyer/seller claim labels, and final resolution actions.
- Ops console includes `高风险操作`, `执行前确认`, and a danger confirmation modal.

- [x] **Step 2: Run focused admin tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/views/AnalyticsView.test.js src/views/ModerationView.test.js src/views/UserManagementView.test.js src/views/WalletAdminView.test.js src/views/AdminMarketDisputesView.test.js src/views/OpsConsoleView.test.js
```

Expected: FAIL before the new copy and modal semantics are present.

- [x] **Step 3: Update admin surfaces**

Update the admin views with operational semantics:

- Analytics: rename filter heading to `数据范围`, add `数据新鲜度` note.
- Moderation: add `风险动作` copy to action modal and `role="dialog" aria-modal="true"` on the custom modal.
- User management: rename reason label to `审计原因（必填）`, keep role warning.
- Wallet admin: label the page as `高风险资金操作`, add `审计追踪` heading for the session log.
- Admin disputes: render claim/fund state fields and resolution actions.
- Ops console: use `高风险操作` copy and rely on accessible `UiModalConfirm`.

- [x] **Step 4: Re-run focused admin tests**

Run:

```bash
cd frontend
npm test -- src/views/AnalyticsView.test.js src/views/ModerationView.test.js src/views/UserManagementView.test.js src/views/WalletAdminView.test.js src/views/AdminMarketDisputesView.test.js src/views/OpsConsoleView.test.js
```

Expected: PASS.

---

## Task 4: Modal And Detail Accessibility

**Files:**

- Modify: `frontend/src/components/ui/UiModalConfirm.vue`
- Create: `frontend/src/components/ui/UiModalConfirm.test.js`
- Modify: `frontend/src/components/modals/ReportModal.vue`
- Modify: `frontend/src/components/modals/EditContentModal.vue`
- Modify: `frontend/src/components/modals/EditContentModal.test.js`
- Modify: `frontend/src/views/SearchView.vue`
- Modify: `frontend/src/views/SearchView.test.js`
- Modify: `frontend/src/views/ModerationView.vue`
- Modify: `frontend/src/views/ModerationView.test.js`
- Modify: `frontend/src/styles/components.css`

- [x] **Step 1: Write failing accessibility tests**

Add tests for:

- `UiModalConfirm`: root/card exposes `role="dialog"`, `aria-modal="true"`, `aria-labelledby`, `aria-describedby`; Escape emits `cancel`.
- `EditContentModal`: dialog semantics and Escape close.
- `SearchView`: admin reindex modal has `role="dialog"` and an accessible name.
- `ModerationView`: action modal has `role="dialog"` and an accessible name.

- [x] **Step 2: Run accessibility tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/components/ui/UiModalConfirm.test.js src/components/modals/EditContentModal.test.js src/views/SearchView.test.js src/views/ModerationView.test.js
```

Expected: FAIL before dialog semantics and keyboard handling are implemented.

- [x] **Step 3: Implement modal accessibility**

Implement:

- Add stable ids with `useId()` or deterministic local constants.
- Put `role="dialog" aria-modal="true"` on `.modal-card`.
- Wire `aria-labelledby` to visible modal title and `aria-describedby` to visible modal message/description.
- Add `tabindex="-1"` to modal card where needed.
- Add `@keydown.esc.stop.prevent` on modal mask or card to emit close/cancel.
- Keep click-outside behavior unchanged.

- [x] **Step 4: Re-run accessibility tests**

Run:

```bash
cd frontend
npm test -- src/components/ui/UiModalConfirm.test.js src/components/modals/EditContentModal.test.js src/views/SearchView.test.js src/views/ModerationView.test.js
```

Expected: PASS.

---

## Task 5: Verification, Browser Review, And Documentation

**Files:**

- Modify if needed: `docs/handbook/frontend.md`
- No planned source edits in the happy path.

- [x] **Step 1: Run focused Phase 2 tests**

Run:

```bash
cd frontend
npm test -- src/views/marketState.test.js src/views/MarketViews.test.js src/views/MarketOrderViews.test.js src/views/MarketSellerViews.test.js src/views/MarketAddressesView.test.js src/views/AnalyticsView.test.js src/views/ModerationView.test.js src/views/UserManagementView.test.js src/views/WalletAdminView.test.js src/views/AdminMarketDisputesView.test.js src/views/OpsConsoleView.test.js src/components/ui/UiModalConfirm.test.js src/components/modals/EditContentModal.test.js src/views/SearchView.test.js
```

Expected: PASS.

- [x] **Step 2: Run full frontend tests**

Run:

```bash
cd frontend
npm test
```

Expected: PASS.

- [x] **Step 3: Run production build**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS.

- [x] **Step 4: Browser review**

Use Chrome DevTools at `http://localhost:12881` against a running single topology.

Desktop `1440x900` and mobile `390x844` routes:

- `/#/market`
- `/#/market/listings/:listingId` if data exists
- `/#/market/publish`
- `/#/market/my-listings`
- `/#/market/orders/buying`
- `/#/market/orders/selling`
- `/#/market/orders/:orderId` if data exists
- `/#/market/addresses`
- `/#/analytics`
- `/#/moderation`
- `/#/admin/users`
- `/#/admin/wallet`
- `/#/admin/market-disputes`
- `/#/admin/ops`
- Search admin reindex modal, moderation action modal, and shared confirm modals when available.

Expected:

- No horizontal overflow.
- Market order detail shows lifecycle and audit context.
- Admin pages show operations/risk/audit language.
- Dialogs expose accessible names and close with Escape.
- No raw `ACTIVE`, `AVAILABLE`, or implementation copy appears as primary product content.

- [x] **Step 5: Final copy scan**

Run:

```bash
rg -n "第一版|后续|暂未返回|待同步|钱包页为准|当前主页还未接入|前台只按|ACTIVE|AVAILABLE|成员 #" frontend/src/views frontend/src/components
```

Expected: no product-facing matches. Remaining matches in tests or state-normalization code are acceptable when they explicitly verify mapping away from raw values.

- [x] **Step 6: Commit Phase 2**

Run:

```bash
git add docs/superpowers/plans/2026-05-13-community-ui-positioning-redesign-phase-2.md frontend/src docs/handbook/frontend.md
git commit -m "feat: complete community positioning phase 2"
```

Expected: one commit with Phase 2 source, tests, and plan updates.

---

## Completion Criteria

All phases are complete when:

- Phase 1 commit remains present.
- This Phase 2 plan is committed.
- Market lifecycle views show trust, fulfillment, lifecycle, risk, and next action copy without raw technical labels.
- Buying/selling order lists and order detail include lifecycle/funds/fulfillment state.
- Address management is compact, default-aware, and action-oriented.
- Admin views consistently communicate operations, risk, audit, data freshness, and resolution context.
- Shared and custom modals expose dialog semantics and keyboard close behavior.
- Focused Phase 2 tests, full frontend tests, and production build pass.
- Browser review passes for representative desktop and mobile routes.
