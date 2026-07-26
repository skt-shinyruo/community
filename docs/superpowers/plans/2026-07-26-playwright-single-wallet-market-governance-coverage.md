# Playwright Single Wallet, Market, and Governance Capability Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every user-reachable wallet, market, reporting, moderation, administration, and analytics capability a browser-only Playwright regression journey with visible state assertions.

**Architecture:** Each journey creates the funds, listings, addresses, reports, and disputes it consumes through the product UI. `ccc` is the only destructive-operation target; `aaa`, `bbb`, and `admin` remain reusable. A real transaction reference is rendered in the wallet history so rollback can be performed through the administrator's UI rather than an API, database, or response body.

**Tech Stack:** Playwright Test, Vue 3/Vitest, Spring wallet/market/moderation applications, Docker Compose single topology.

## Global Constraints

- Business specs and fixtures may use browser navigation, locators, file inputs, browser contexts, and UI-established WebSockets only. They must not import a product client or use `APIRequestContext`, `request`, `page.request`, `fetch`, axios, or `page.evaluate(fetch)`.
- A response listener may observe a UI-triggered method, URL, or status, but a passing assertion must be rendered product state. Do not parse response JSON in these specs.
- Keep Chromium-only execution serial with `workers: 1`, `fullyParallel: false`, and `retries: 0`. Do not add `test.skip`, `test.fail`, or an expected `5xx`.
- Every created title, address detail, delivery content, report reason, audit reason, and message includes `SINGLE_TEST_RUN_ID`. Numeric-only wallet requests use a deterministic positive amount derived from that run ID.
- Expected client errors require one exact `allowExpectedHttpError(page, { method, path, status }, performUiAction, assertVisible)` wrapper around the rejecting visible action and its matching visible product error. Empty-form client validation must produce no HTTP request.
- Only `accounts.candidate` may be frozen, role-mutated, or otherwise destructively governed. It is never an input to a later plan.
- Do not add test-only backend endpoints, database setup, or product API setup. Any independent backend repair preserves the DDD Tactical Layering in `AGENTS.md`.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `tests/playwright-single/tests/04-wallet.spec.ts` | Visible wallet summary, recharge, withdrawal, transfer, validation, and frozen-wallet rejection journeys. |
| `tests/playwright-single/tests/05-market.spec.ts` | Virtual and physical listing, inventory, address, order, delivery, cancellation, and confirmation journeys. |
| `tests/playwright-single/tests/07-governance.spec.ts` | Report submission, moderation action/audit, candidate role lifecycle, wallet administration, market arbitration, and analytics journeys. |
| `tests/playwright-single/fixtures/test-data.ts` | Run-scoped strings and deterministic numeric amounts used by independent journeys. |
| `frontend/src/views/walletState.js` | Carries the existing transaction reference into the wallet view model. |
| `frontend/src/views/WalletView.vue` | Renders the reference as user-visible information suitable for the rollback form. |

## Task 1: Make Wallet Transaction References Usable From the Product UI

**Files:**
- Modify: `frontend/src/views/walletState.js`
- Modify: `frontend/src/views/WalletView.vue`
- Modify: `frontend/src/views/walletState.test.js`
- Modify: `frontend/src/views/WalletView.test.js`

**Interfaces:**
- Produces `buildWalletState({ summary, txns }).feed[number].txnRef: string`.
- Produces visible text `交易参考：${txnRef}` for a rendered transaction with a nonempty reference.

- [ ] **Step 1: Write the failing wallet view-model test**

Add a `walletState.test.js` case with `{ txnType: 'RECHARGE', amount: 12, txnRef: 'recharge:pw-42' }`. Assert the feed retains `txnRef`, renders `充值到账`, and renders `+12 积分`.

- [ ] **Step 2: Run the focused red frontend tests**

Run:

```bash
npm --prefix frontend test -- src/views/walletState.test.js src/views/WalletView.test.js
```

Expected: FAIL because the view model drops the reference and no visible reference text exists.

- [ ] **Step 3: Render the existing reference**

Copy `String(txn.txnRef || '')` into each feed item. Beneath the existing `item.meta` text in `WalletView.vue`, render `交易参考：{{ item.txnRef }}` only when it is nonempty. Do not add a service call, expose a database key, or change the ledger contract.

- [ ] **Step 4: Run the focused frontend tests**

Run:

```bash
npm --prefix frontend test -- src/views/walletState.test.js src/views/WalletView.test.js
```

Expected: PASS; the reference appears alongside the existing human-readable transaction label and amount.

- [ ] **Step 5: Commit the wallet UI capability**

```bash
git add frontend/src/views/walletState.js frontend/src/views/walletState.test.js frontend/src/views/WalletView.vue frontend/src/views/WalletView.test.js
git commit -m "feat(wallet): show transaction references in history"
```

## Task 2: Cover Wallet Summary, Ledger Actions, and Validation Through UI

**Files:**
- Modify: `tests/playwright-single/tests/04-wallet.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Test: `tests/playwright-single/tests/04-wallet.spec.ts`

**Interfaces:**
- Produces tags `@cap:wallet.summary-history`, `@cap:wallet.recharge`, `@cap:wallet.withdrawal`, `@cap:wallet.transfer`, and `@cap:wallet.validation`.
- Produces positive integer `data.walletRechargeAmount`, `data.walletWithdrawalAmount`, and `data.walletTransferAmount`, derived from `runId`.

- [ ] **Step 1: Write failing visible wallet journeys**

Replace the broad wallet smoke with two self-contained tests. The first logs in as `aaa`, opens `/wallet`, enters `0` in the `充值` card and asserts `请输入有效的充值金额`, then recharges, withdraws, and transfers the three run-scoped amounts to `accounts.bbb.userId`. Assert the rendered history contains `充值到账`, `提现申请`, `转账转出`, and `交易参考：`.

The second itself funds `aaa` through `/wallet`, transfers a run-scoped amount to `bbb`, changes identity with `loginViaUi`, and asserts `bbb` renders `转账转入` plus the amount. It must not assume the first test ran.

- [ ] **Step 2: Run the new wallet capability cases to verify failure**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/04-wallet.spec.ts
```

Expected: FAIL until tags, deterministic data, UI validation, and cross-user visible assertions exist.

- [ ] **Step 3: Implement the browser-only wallet flow**

Use `.wallet-action-card` sections headed `充值`, `提现`, and `转账`; fill visible fields and click their visible buttons. Reload through the page and assert ledger rows, never request payloads or bodies. Use no expected-error wrapper for successful actions and client-side zero validation.

- [ ] **Step 4: Run wallet and presentation regressions**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/04-wallet.spec.ts
npm --prefix frontend test -- src/views/WalletView.test.js src/views/walletState.test.js
```

Expected: each wallet action produces observable history and validation sends no unallowed request.

- [ ] **Step 5: Commit wallet coverage**

```bash
git add tests/playwright-single/tests/04-wallet.spec.ts tests/playwright-single/fixtures/test-data.ts
git commit -m "test(e2e): cover wallet user journeys"
```

## Task 3: Cover Virtual and Physical Market Lifecycles

**Files:**
- Modify: `tests/playwright-single/tests/05-market.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Test: `tests/playwright-single/tests/05-market.spec.ts`

**Interfaces:**
- Produces tags `@cap:market.browse-detail`, `@cap:market.publish`, `@cap:market.inventory`, `@cap:market.address`, `@cap:market.order`, `@cap:market.delivery-confirm`, and `@cap:market.cancel`.
- Produces run-scoped `data.virtualListingTitle`, `data.manualListingTitle`, `data.physicalListingTitle`, `data.virtualListingInventory`, `data.marketDeliveryContent`, `data.trackingNo`, and address values.

- [ ] **Step 1: Write failing virtual-market browser cases**

Create a `VIRTUAL` `PRELOADED` listing as `aaa` with two run-scoped inventory values. Assert `发布成功`, the list/detail-page `自动交付` state, and the seller's `库存管理` surface. Append a third value, mark it `失效`, and assert its visible `已失效` status.

As `bbb`, fund the wallet through visible controls in the same test, open the listing from `/market`, click `安全下单`, open it from `我的购买`, assert the run-scoped delivery content, click `确认完成`, and assert `已完成` plus `已放款`. The test does not depend on the wallet spec.

- [ ] **Step 2: Run the virtual-market case to verify failure**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/05-market.spec.ts
```

Expected: FAIL until UI-created listing, funds, automatic delivery, and confirmation are asserted.

- [ ] **Step 3: Add physical order, address, and cancellation paths**

In a separate self-contained test, create an address as `bbb`, edit `详细地址` to include the run ID, and assert `地址已更新。`. Fund `bbb` through `/wallet`, publish a `PHYSICAL` listing as `aaa`, purchase it as `bbb` with the visible address selector, ship it as `aaa` using run-scoped carrier/tracking text, confirm receipt as `bbb`, and assert the rendered shipment plus address snapshot. Delete the address and assert `地址已删除。`.

Create a separate `MANUAL` virtual listing with stock two, fund `bbb` through the UI, and place two orders. Cancel the first before delivery and assert `已取消`. As `aaa`, open the second order, fill the visible `输入卡密、邀请码或其他交付内容` field with `data.marketDeliveryContent`, click `提交交付`, then as `bbb` assert the delivered text and click `确认完成`. This covers the distinct manual-delivery state transition rather than treating automatic delivery as its substitute.

- [ ] **Step 4: Add the market validation rejection**

On `/market/publish`, leave the `PRELOADED` inventory blank, click `确认发布`, and assert exactly `自动交付商品至少需要一条预存内容。`. This is client validation, so it has no audit allowlist entry and creates no listing.

- [ ] **Step 5: Run market and frontend state tests**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/05-market.spec.ts
npm --prefix frontend test -- src/views/MarketViews.test.js src/views/MarketOrderViews.test.js src/views/MarketAddressesView.test.js src/views/MarketSellerViews.test.js src/views/marketState.test.js
```

Expected: virtual delivery, physical shipping, confirmation, cancellation, inventory mutation, and address lifecycle all have rendered evidence.

- [ ] **Step 6: Commit market coverage**

```bash
git add tests/playwright-single/tests/05-market.spec.ts tests/playwright-single/fixtures/test-data.ts
git commit -m "test(e2e): cover market user lifecycles"
```

## Task 4: Cover Reporting, Moderation, Candidate Roles, Wallet Administration, Arbitration, and Analytics

**Files:**
- Modify: `tests/playwright-single/tests/07-governance.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Test: `tests/playwright-single/tests/07-governance.spec.ts`

**Interfaces:**
- Produces tags `@cap:report.create`, `@cap:governance.moderate-audit`, `@cap:governance.post-moderation`, `@cap:governance.role-management`, `@cap:governance.wallet-admin`, `@cap:governance.market-arbitration`, `@cap:governance.analytics`, and `@cap:governance.role-guard`.
- Uses `accounts.candidate` as the sole target for role update, rollback, and wallet freeze.
- Consumes `newAuditedContext()` for the candidate actor so every page and request in its separate login session remains audited.

- [ ] **Step 1: Write the failing report-to-audit journey**

As `aaa`, create two run-scoped posts. As `bbb`, open the first, click `举报`, choose `垃圾广告`, fill a run-scoped detail, and submit; assert the visible `已提交` toast. As `admin`, find the report in `/moderation`, click `处置`, choose `警告`, enter the same run-scoped reason, click `确认处置`, assert `处置成功`, switch to `处置审计`, and assert the reason is rendered.

As `admin`, open the second post, click `置顶` and complete its visible confirmation, then assert `已置顶`; click `加精` and assert `已加精`; click `删除`, complete its visible confirmation, and assert `已删除`. These are separate real moderation commands, so the test must assert each visible state before issuing the next one.

- [ ] **Step 2: Run the moderation case to verify failure**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/07-governance.spec.ts
```

Expected: FAIL until the complete reporter-to-moderator UI path has tags and visible assertions.

- [ ] **Step 3: Implement candidate role and wallet-administration journeys**

As `admin`, search `ccc` in `/admin/users`, select `MODERATOR（版主）`, enter a run-scoped reason, complete the visible confirmation modal, and assert `角色已更新`. Create the candidate actor through `newAuditedContext()`, log in as `ccc` through visible UI, and assert `治理后台` appears. Revert the candidate to `USER（普通用户）` through the same UI. Do not call `browser.newContext()` directly.

Log in as `ccc`, recharge a run-scoped amount, and read the visible `交易参考：...` text. As `admin`, enter it into `/admin/wallet` with a run-scoped reason, click `执行回滚`, and assert `已提交回滚`. Freeze the candidate, assert `已冻结钱包`, re-login as `ccc`, assert `钱包已冻结，当前仅保留查询能力。`, then wrap the visible transfer-button click in `await allowExpectedHttpError(page, { method: 'POST', path: '/api/wallet/transfers', status: 409 }, performUiAction, assertVisible)` where the callbacks click the transfer control and require the visible frozen-wallet error.

- [ ] **Step 4: Implement market arbitration and analytics cases**

Create one `MANUAL` virtual listing as `aaa` with stock two, fund `bbb`, and place two visible orders. For each order, fill `申诉原因` and `买家说明` with distinct run-scoped text, then click `发起申诉`. As `admin`, find both on `/admin/market/disputes`; click `退回买家` for the first and `放款卖家` for the second. Assert neither remains `待管理员裁定`; as `bbb`, reload the first and assert `已退款`; as `aaa`, reload the second and assert `已放款`. This exercises both administrator-visible arbitration outcomes.

Keep the ordinary-user `/admin/users` route guard case and assert `/403` plus `无权限`. As `admin`, choose today for both analytics date inputs, click `刷新`, and assert `UV（独立访客）` and `DAU（日活）` render values rather than initial `-` placeholders.

- [ ] **Step 5: Run governance and affected frontend tests**

Run:

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/07-governance.spec.ts
npm --prefix frontend test -- src/views/ModerationView.test.js src/views/UserManagementView.test.js src/views/WalletAdminView.test.js src/views/AdminMarketDisputesView.test.js src/views/AnalyticsView.test.js
```

Expected: high-risk state changes stay scoped to `ccc` or run-scoped objects, denial is exact and visible, and success is proven from page state.

- [ ] **Step 6: Commit governance coverage**

```bash
git add tests/playwright-single/tests/07-governance.spec.ts tests/playwright-single/fixtures/test-data.ts
git commit -m "test(e2e): cover governance capabilities"
```
