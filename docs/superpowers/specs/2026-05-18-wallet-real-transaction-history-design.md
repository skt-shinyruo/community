# Wallet Real Transaction History Design

Date: 2026-05-18

## Status

Design approved in brainstorming. Implementation has not started.

## Context

The wallet page presents a "recent transactions" surface, but the data is not backed by a
real history query.

Current frontend behavior:

- `frontend/src/views/WalletView.vue` initializes `txns` as an empty local ref.
- `reload()` only calls `GET /api/wallet/summary`.
- Successful recharge, withdrawal, and transfer actions prepend synthetic entries into local page state.
- Refreshing the page or opening the wallet from another client loses the visible transaction list.

Current backend behavior:

- `WalletController` exposes `GET /api/wallet/summary` plus recharge, withdrawal, and transfer write endpoints.
- `WalletSummaryResponse` contains only `userId`, `balance`, and `status`.
- Wallet writes already persist ledger facts through `WalletLedgerApplicationService.post(...)`.
- Ledger facts are stored in `wallet_txn` and `wallet_entry`.
- `wallet_entry` already has an `(account_id, create_time)` index in test schema, which is the right read path for per-account recent history.

The missing piece is not ledger persistence. The missing piece is a user-facing read model
that queries the current user's wallet entries, joins the ledger transaction metadata, and
returns signed amounts from the current user's perspective.

## Problem Statement

The UI implies historical wallet transactions, but it only shows entries created in the current browser session. This causes:

- loss of visible transactions after refresh
- no cross-device consistency
- no way to inspect older recharge, withdrawal, transfer, reward, escrow, release, refund, or reversal ledger facts
- misleading "recent transaction" counts because they reflect local UI state instead of persisted wallet history

The fix must not introduce a duplicate wallet history table. The ledger tables are the source of truth.

## Goals

- Add a real authenticated wallet transaction history endpoint for the current user.
- Return recent transactions from `wallet_entry` joined with `wallet_txn`.
- Compute signed amounts from the current user's account perspective.
- Keep the read path inside the wallet domain and preserve strict DDD tactical layering.
- Replace frontend session-local transaction history with backend-provided history.
- Keep the first release focused on the wallet page's "recent transactions" need.
- Add backend and frontend tests that prove refresh-safe history behavior.

## Non-Goals

- Do not build a full searchable ledger explorer.
- Do not add admin ledger history screens.
- Do not add cursor pagination or date filtering in the first release.
- Do not change the wallet write flows, idempotency behavior, or ledger posting rules.
- Do not expose system account entries to the user-facing endpoint.
- Do not let frontend infer transaction history from write responses.
- Do not create a second ledger or denormalized transaction-history table.

## Chosen Approach

Use the existing double-entry ledger as the source of truth and add a wallet-owned recent-history query:

```text
WalletController
  -> WalletApplicationService
      -> WalletAccountApplicationService read-only account lookup
      -> WalletLedgerApplicationService recent-history query
          -> WalletLedgerRepository
              -> MyBatisWalletLedgerRepository
                  -> WalletEntryMapper / wallet_entry join wallet_txn
```

Rejected alternatives:

- Store a separate wallet history table. This duplicates ledger facts and creates consistency risk.
- Extend `/api/wallet/summary` to include transactions. Summary and ledger history have different cache, size, and evolution needs.
- Continue prepending local UI entries. This preserves the bug and makes the page non-authoritative.

## HTTP API

Add:

```http
GET /api/wallet/transactions?limit=12
```

Authentication:

- Uses the same authenticated current-user extraction as `GET /api/wallet/summary`.
- Always scopes results to the current user's wallet account.

Request parameter:

- `limit`: optional integer. Application semantics clamp it to `1..50`, default `12`.

Response body:

```json
[
  {
    "txnId": "0198f4b6-9ad4-7a22-8df4-3c680e0d0d01",
    "txnRef": "wallet:transfer:0198f4b6-9acc-7c1d-9f24-67a28fbc8d10",
    "txnType": "TRANSFER",
    "bizType": "TRANSFER",
    "bizId": "0198f4b6-9acc-7c1d-9f24-67a28fbc8d10",
    "status": "SUCCEEDED",
    "amount": -25,
    "balanceAfter": 975,
    "counterpartLabel": "User 11111111-1111-7111-8111-111111111111",
    "remark": null,
    "createTime": "2026-05-18T10:00:00Z"
  }
]
```

Field semantics:

- `txnId`: persisted wallet transaction id.
- `txnRef`: ledger request id, suitable for display/debugging and admin reversal references where allowed.
- `txnType`: persisted ledger transaction type.
- `bizType`: persisted business type.
- `bizId`: persisted business id.
- `status`: persisted ledger transaction status, not a separate recharge/withdrawal/transfer order workflow status.
- `amount`: signed amount from the current user's wallet-account perspective.
- `balanceAfter`: current user's wallet balance immediately after this ledger entry.
- `counterpartLabel`: display label for the counterparty or system context.
- `remark`: persisted transaction remark when present.
- `createTime`: ledger entry creation time.

## Backend Application Design

### Commands and Results

Add:

```java
package com.nowcoder.community.wallet.application.command;

public record ListWalletTransactionsCommand(UUID userId, Integer limit) {
}
```

Add:

```java
package com.nowcoder.community.wallet.application.result;

public record WalletTransactionResult(
        UUID txnId,
        String txnRef,
        String txnType,
        String bizType,
        String bizId,
        String status,
        long amount,
        long balanceAfter,
        String counterpartLabel,
        String remark,
        Date createTime
) {
}
```

`WalletApplicationService` adds:

```java
public List<WalletTransactionResult> recentTransactions(ListWalletTransactionsCommand command)
```

The method:

1. Validates `userId`.
2. Normalizes `limit` to default `12`, minimum `1`, maximum `50`.
3. Performs a read-only lookup of the user's wallet account.
4. Returns an empty list if the user has no wallet account.
5. Delegates to `WalletLedgerApplicationService.recentTransactions(userAccount, limit)`.

### Read-Only Account Lookup

Do not use `ensureUserWallet(...)` or `loadUserWallet(...)` in the transaction-history query. Those methods can create an account and would make a read endpoint mutate state.

Add a read-only method to `WalletAccountApplicationService`, for example:

```java
public WalletAccount findUserWallet(UUID userId)
```

It should call `WalletAccountRepository.findByOwner(...)` using:

- owner type `USER`
- account type `USER_WALLET`

It returns `null` when no account exists.

### Ledger Query Application Method

`WalletLedgerApplicationService` adds:

```java
public List<WalletTransactionResult> recentTransactions(WalletAccount userAccount, int limit)
```

Responsibilities:

- Call the ledger repository for recent ledger rows scoped to `userAccount.accountId`.
- Convert each ledger row to `WalletTransactionResult`.
- Compute signed amount with the current user's account type and entry direction.
- Build a conservative `counterpartLabel`.
- Avoid HTTP DTOs and mapper/dataobject types.

Signed amount rule:

```text
if entry direction equals normal direction of USER_WALLET:
  signed amount = entry.amount
else:
  signed amount = -entry.amount
```

For `USER_WALLET`, the normal direction is `CREDIT`, so:

- `CREDIT` means income
- `DEBIT` means expense

## Domain and Repository Design

Add a domain read model for joined ledger rows, for example:

```java
package com.nowcoder.community.wallet.domain.model;

public record WalletLedgerItem(
        UUID entryId,
        UUID txnId,
        UUID accountId,
        String direction,
        long entryAmount,
        long balanceAfter,
        Date entryCreateTime,
        String requestId,
        String txnType,
        String bizType,
        String bizId,
        String status,
        String remark,
        UUID counterpartUserId
) {
}
```

`counterpartUserId` is optional. It is populated when the same transaction has another `USER_WALLET` entry that belongs to a different user.

Extend `WalletLedgerRepository`:

```java
List<WalletLedgerItem> findRecentItemsByAccountId(UUID accountId, int limit);
```

This keeps application code away from MyBatis mappers and data objects.

## Infrastructure Persistence Design

Add an infrastructure data object if needed:

```text
wallet.infrastructure.persistence.dataobject.WalletLedgerItemDataObject
```

Extend `WalletEntryMapper` with an entry-centric recent query. The query should:

- filter by the current user's `account_id`
- join `wallet_txn` for transaction metadata
- left join a counterpart user account only when another entry in the same transaction belongs to another `USER_WALLET`
- order by newest first
- apply the normalized limit

Representative SQL:

```sql
select
  e.entry_id,
  e.txn_id,
  e.account_id,
  e.direction,
  e.amount as entry_amount,
  e.balance_after,
  e.create_time as entry_create_time,
  t.request_id,
  t.txn_type,
  t.biz_type,
  t.biz_id,
  t.status,
  t.remark,
  cp.owner_id as counterpart_user_id
from wallet_entry e
join wallet_txn t on t.txn_id = e.txn_id
left join wallet_entry ce
       on ce.txn_id = e.txn_id
      and ce.account_id <> e.account_id
left join wallet_account cp
       on cp.account_id = ce.account_id
      and cp.owner_type = 'USER'
      and cp.account_type = 'USER_WALLET'
where e.account_id = #{accountId, jdbcType=BINARY}
order by e.create_time desc, e.entry_id desc
limit #{limit}
```

Implementation should avoid duplicate rows if a future transaction has more than one possible counterpart. For the current ledger shapes, user-facing transactions have at most one other user-wallet counterpart. If implementation needs to harden this, it can aggregate counterpart selection in a subquery by transaction id.

Schema changes:

- No table changes are required.
- Production schema should have the same useful indexes as test schema:
  - `idx_wallet_entry_txn` on `wallet_entry(txn_id)`
  - `idx_wallet_entry_account_time` on `wallet_entry(account_id, create_time)`

If production migrations are managed elsewhere, the implementation should add or verify the equivalent index migration there.

## Counterpart Labels

The backend should return display-ready but conservative labels so the frontend does not reverse-engineer ledger semantics.

Rules:

- `RECHARGE`: `平台入账`
- `WITHDRAW`: `提现申请`
- `TRANSFER`: `用户 <counterpartUserId>` when a counterpart user is available; otherwise `钱包转账`
- `ORDER_ESCROW`: `订单托管`
- `ORDER_RELEASE`: `订单结算`
- `ORDER_REFUND`: `订单退款`
- `REWARD_ISSUE`: `活动奖励`
- `REVERSAL`: `交易回滚`
- fallback: persisted `remark`, then `系统记账`

The current frontend can still map `txnType` to Chinese labels in `walletState.js`. `counterpartLabel` is a metadata line, not the transaction title.

## Controller and DTO Design

`WalletController` adds:

```java
@GetMapping("/transactions")
public Result<List<WalletTransactionResponse>> transactions(
        Authentication authentication,
        @RequestParam(required = false) Integer limit
)
```

Controller responsibilities:

- Extract authenticated user id.
- Build `ListWalletTransactionsCommand`.
- Call `WalletApplicationService.recentTransactions(...)`.
- Convert application results to controller DTOs.

Add:

```text
wallet.controller.dto.WalletTransactionResponse
```

The DTO mirrors `WalletTransactionResult` and contains no domain, mapper, dataobject, or HTTP transport types.

## Frontend Design

### API Service

Add to `frontend/src/api/services/walletService.js`:

```js
export async function getWalletTransactions(limit = 12) {
  const resp = await http.get('/api/wallet/transactions', { params: { limit } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询钱包流水')
  return { data: Array.isArray(data) ? data : [], traceId }
}
```

### Wallet View

`WalletView.vue` should:

- import `getWalletTransactions`
- load summary and transactions in `reload()`
- assign `txns.value` from the backend response
- add a small `normalizeTxns(...)` helper that returns an array and preserves unknown fields
- stop using local prepend as a history source
- after a successful write action, clear the form and call `reload()`

Representative reload flow:

```js
const [summaryResp, txnsResp] = await Promise.all([
  getWalletSummary(),
  getWalletTransactions(12)
])
summary.value = normalizeSummary(summaryResp.data)
txns.value = normalizeTxns(txnsResp.data)
ready.value = true
```

`prependTxn(...)` should be removed unless it is repurposed only for temporary optimistic UI. The first release should prefer server-confirmed reloads over optimistic ledger rendering.

### Wallet State

`walletState.js` can keep the existing `buildWalletState({ summary, txns })` contract.

Required compatibility:

- Use `txnRef`, `requestId`, or `txnId` as stable item key.
- Use signed `amount` from the backend.
- Keep `counterpartLabel` and `remark` as metadata fallbacks.
- Preserve existing fallback behavior for unknown transaction types.

## Security and Privacy

- The endpoint must only return entries for the authenticated user's own wallet account.
- The endpoint must not accept arbitrary `userId` or `accountId`.
- The endpoint must not expose system account balances.
- Counterparty identity is limited to the other user id already implied by transfer activity.
- Request ids and business ids are acceptable because current admin reversal flows already use transaction references, but the UI should present them as secondary/debug metadata only if needed.

## Error Handling

- If the user has no wallet account, return an empty list.
- Invalid or missing `limit` resolves through application normalization.
- Repository query failure follows existing backend exception handling and returns the standard error envelope.
- Frontend loading failure should reuse the wallet page's existing `error` state.
- A failed transaction-history request should fail the wallet reload rather than silently displaying local or stale history.

## Testing

Backend application tests:

- A user with no wallet account gets an empty recent transaction list.
- Recharge returns one current-user item with positive signed amount.
- Transfer returns a negative item for the sender and a positive item for the recipient.
- Withdrawal returns only the user's outgoing withdrawal request entry, not the later system-to-system settlement entry.
- Reward issue, order escrow, order release, order refund, and reversal produce correctly signed user-facing entries when their ledger postings include the user's wallet account.
- `limit` defaults to `12`, clamps to `1..50`, and newest entries are returned first.

Backend persistence tests:

- `WalletEntryMapper` recent query filters by account id.
- The join returns transaction metadata from `wallet_txn`.
- Counterpart user id is populated for transfer rows.
- System-only entries are not returned for a user account query.

Backend controller tests:

- `GET /api/wallet/transactions` uses the authenticated user id.
- Response DTO includes signed `amount`, `balanceAfter`, `txnType`, `status`, and `createTime`.
- The endpoint does not accept or require a user id parameter.

Frontend tests:

- `WalletView` calls both `getWalletSummary` and `getWalletTransactions` during initial load.
- Existing backend transaction rows render in the "recent transactions" panel after mount.
- Successful recharge, withdrawal, and transfer reload history from the backend instead of depending on local prepend state.
- `walletState` maps backend transaction rows with `txnRef`, signed `amount`, and `counterpartLabel`.

Architecture tests:

- Existing wallet controller boundary rules should continue to pass.
- Application code must not depend on MyBatis mappers, data objects, Spring Web transport types, or controller DTOs.
- Infrastructure code may implement repository queries but must not leak mapper/dataobject types into application or domain APIs.

## Verification Commands

Backend targeted tests:

```bash
cd backend
mvn test -pl :community-app -Dtest='*Wallet*Test,*ArchTest'
```

Frontend targeted tests:

```bash
cd frontend
npm test -- src/views/WalletView.test.js src/views/walletState.test.js
```

## Documentation Updates During Implementation

Update `docs/handbook/business-logic/wallet.md` after implementation to include:

- `GET /api/wallet/transactions`
- the recent-history read path
- signed amount semantics
- the rule that wallet read endpoints must not create accounts as a side effect

If architecture documentation mentions wallet HTTP surfaces, update the relevant handbook sections so they stay aligned with repository DDD rules.
