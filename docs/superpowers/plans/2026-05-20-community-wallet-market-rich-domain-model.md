# Community Wallet And Market Rich Domain Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move wallet recharge and market order lifecycle rules from application-private strings and helper methods into domain enums, value objects, and model behavior while preserving existing APIs, schemas, and persisted codes.

**Architecture:** Keep controllers, listeners, jobs, idempotency, transactions, repository calls, wallet action enqueue, and cross-domain collaboration in application/inbound layers. Add string-backed domain enums and transition value objects so application services ask domain models for eligibility and transition intent, while MyBatis SQL keeps conditional updates as concurrency protection.

**Tech Stack:** Java 17, Spring Boot 3.2.6, MyBatis XML mappers, JUnit 5, Mockito, AssertJ, Maven, ArchUnit.

---

## File Structure

Wallet files to create:

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderStatus.java`: string-backed recharge status enum.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTransition.java`: domain transition value for conditional persistence.
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTest.java`: domain tests for creation, replay, paid predicate, and paid transition.

Wallet files to modify:

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrder.java`: add factory and behavior methods while keeping current getters/setters.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/repository/RechargeOrderRepository.java`: add `applyTransition(RechargeOrderTransition transition)`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/MyBatisRechargeOrderRepository.java`: implement transition application using existing mapper update.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java`: remove raw status comparisons and use domain behavior.
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceRechargeTest.java`: verify transition object instead of raw status strings in mocked repository test.

Market files to create:

- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketCodeEnum.java`: tiny package-private helper contract for string-backed enums.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderStatus.java`: order lifecycle enum.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketGoodsType.java`: goods type enum.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketDeliveryMode.java`: delivery mode enum.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketStockMode.java`: stock mode enum.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketListingStatus.java`: listing status enum used by stock compensation.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketAddressSnapshot.java`: value object for order address snapshots.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderPlacement.java`: value object for order factory input.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderTransition.java`: value object describing requested order transition.
- `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderStatusTest.java`: enum conversion and wallet-action mapping tests.
- `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderTest.java`: domain tests for placement, replay, actor checks, status predicates, and transitions.
- `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketListingTest.java`: listing status/type helper tests.

Market files to modify:

- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketListing.java`: add type/status helper methods.
- `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrder.java`: add factory, replay checks, actor checks, lifecycle predicates, and transition methods while keeping current getters/setters.
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java`: use domain behavior for create, delivery, shipping, confirmation, and cancellation.
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderAutoConfirmSingleOrderApplicationService.java`: use domain auto-confirm and release transition behavior.
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationService.java`: replace duplicated strings with domain behavior.
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationService.java`: replace status-to-wallet-action helper with `MarketOrder.pendingWalletActionType()`.
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketDisputeApplicationService.java`: use order dispute predicates and transitions.
- Existing market tests under `backend/community-app/src/test/java/com/nowcoder/community/market/application`: update helper code where direct raw model construction should use new enum codes or domain factories.

Documentation files to update after implementation:

- `docs/handbook/business-logic/wallet.md`
- `docs/handbook/business-logic/market.md`
- `docs/handbook/business-logic/workflows/market-wallet.md`
- `docs/handbook/business-flows.md`
- `docs/handbook/reliability.md`

---

### Task 1: Wallet Recharge Domain Model

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderStatus.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTransition.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrder.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTest.java`

- [ ] **Step 1: Write the failing wallet domain test**

Create `backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTest.java`:

```java
package com.nowcoder.community.wallet.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RechargeOrderTest {

    @Test
    void createShouldBuildCreatedOrder() {
        UUID orderId = uuid(1);
        UUID userId = uuid(2);

        RechargeOrder order = RechargeOrder.create(orderId, "recharge:req-domain", userId, 1200L);

        assertThat(order.getOrderId()).isEqualTo(orderId);
        assertThat(order.getRequestId()).isEqualTo("recharge:req-domain");
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getAmount()).isEqualTo(1200L);
        assertThat(order.status()).isEqualTo(RechargeOrderStatus.CREATED);
        assertThat(order.getStatus()).isEqualTo("CREATED");
        assertThat(order.isPaid()).isFalse();
    }

    @Test
    void assertReplayMatchesShouldAllowSameUserAndAmount() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-replay", uuid(2), 1200L);

        order.assertReplayMatches(uuid(2), 1200L);
    }

    @Test
    void assertReplayMatchesShouldRejectDifferentPayload() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-replay", uuid(2), 1200L);

        assertThatThrownBy(() -> order.assertReplayMatches(uuid(2), 1300L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));
    }

    @Test
    void payShouldReturnCreatedToPaidTransition() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-pay", uuid(2), 1200L);

        RechargeOrderTransition transition = order.pay();

        assertThat(transition.orderId()).isEqualTo(uuid(1));
        assertThat(transition.userId()).isEqualTo(uuid(2));
        assertThat(transition.requestId()).isEqualTo("recharge:req-pay");
        assertThat(transition.fromStatus()).isEqualTo(RechargeOrderStatus.CREATED);
        assertThat(transition.toStatus()).isEqualTo(RechargeOrderStatus.PAID);
    }

    @Test
    void payShouldRejectAlreadyPaidOrder() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-paid", uuid(2), 1200L);
        order.setStatus("PAID");

        assertThat(order.isPaid()).isTrue();
        assertThatThrownBy(order::pay)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recharge order status mismatch");
    }

    private static UUID uuid(int value) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", value));
    }
}
```

- [ ] **Step 2: Run the wallet domain test and verify it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RechargeOrderTest
```

Expected: compilation fails because `RechargeOrderStatus`, `RechargeOrderTransition`, `RechargeOrder.create(...)`, `RechargeOrder.status()`, `RechargeOrder.isPaid()`, `RechargeOrder.assertReplayMatches(...)`, and `RechargeOrder.pay()` do not exist.

- [ ] **Step 3: Add recharge status enum**

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderStatus.java`:

```java
package com.nowcoder.community.wallet.domain.model;

import java.util.Arrays;

public enum RechargeOrderStatus {
    CREATED("CREATED"),
    PAID("PAID");

    private final String code;

    RechargeOrderStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static RechargeOrderStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown recharge order status: " + code));
    }
}
```

- [ ] **Step 4: Add recharge transition value**

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTransition.java`:

```java
package com.nowcoder.community.wallet.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RechargeOrderTransition(
        UUID orderId,
        UUID userId,
        String requestId,
        RechargeOrderStatus fromStatus,
        RechargeOrderStatus toStatus
) {
    public RechargeOrderTransition {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
    }
}
```

- [ ] **Step 5: Add behavior to RechargeOrder**

In `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrder.java`, add imports:

```java
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
```

Add this block inside `RechargeOrder`, before the first getter:

```java
    public static RechargeOrder create(UUID orderId, String requestId, UUID userId, long amount) {
        RechargeOrder order = new RechargeOrder();
        order.setOrderId(orderId);
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus(RechargeOrderStatus.CREATED.code());
        return order;
    }

    public RechargeOrderStatus status() {
        return RechargeOrderStatus.fromCode(status);
    }

    public boolean isPaid() {
        return RechargeOrderStatus.PAID.equals(status());
    }

    public void assertReplayMatches(UUID userId, long amount) {
        if (!this.userId.equals(userId) || this.amount != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + requestId
            );
        }
    }

    public RechargeOrderTransition pay() {
        if (!RechargeOrderStatus.CREATED.equals(status())) {
            throw new BusinessException(
                    WalletErrorCode.INVALID_REQUEST,
                    "recharge order status mismatch: orderId=" + orderId
            );
        }
        return new RechargeOrderTransition(
                orderId,
                userId,
                requestId,
                RechargeOrderStatus.CREATED,
                RechargeOrderStatus.PAID
        );
    }
```

- [ ] **Step 6: Run the wallet domain test and verify it passes**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RechargeOrderTest
```

Expected: PASS.

- [ ] **Step 7: Commit wallet domain model**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrder.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderStatus.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTransition.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/model/RechargeOrderTest.java
git commit -m "refactor(wallet): add recharge order domain behavior"
```

---

### Task 2: Wallet Recharge Application Migration

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/repository/RechargeOrderRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/MyBatisRechargeOrderRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceRechargeTest.java`

- [ ] **Step 1: Update repository contract**

In `RechargeOrderRepository.java`, add import:

```java
import com.nowcoder.community.wallet.domain.model.RechargeOrderTransition;
```

Add this method after `updateStatus(...)`:

```java
    int applyTransition(RechargeOrderTransition transition);
```

- [ ] **Step 2: Implement recharge transition persistence**

In `MyBatisRechargeOrderRepository.java`, add import:

```java
import com.nowcoder.community.wallet.domain.model.RechargeOrderTransition;
```

Add this method after `updateStatus(...)`:

```java
    @Override
    public int applyTransition(RechargeOrderTransition transition) {
        return updateStatus(
                transition.userId(),
                transition.requestId(),
                transition.fromStatus().code(),
                transition.toStatus().code()
        );
    }
```

- [ ] **Step 3: Refactor WalletRechargeApplicationService**

In `WalletRechargeApplicationService.complete(...)`, replace the method body with:

```java
        validate(requestId, amount);

        RechargeOrder existing = rechargeOrderRepository.findByUserIdAndRequestId(userId, requestId);
        if (existing != null) {
            existing.assertReplayMatches(userId, amount);
            if (existing.isPaid()) {
                return RechargeOrderResult.from(existing);
            }
        }

        RechargeOrder order = existing == null ? createOrLoad(requestId, userId, amount) : existing;
        order.assertReplayMatches(userId, amount);
        if (order.isPaid()) {
            return RechargeOrderResult.from(order);
        }

        ledgerService.post(new WalletLedgerCommand(
                "wallet:recharge:" + order.getOrderId(),
                WalletTxnType.RECHARGE,
                WalletTxnType.RECHARGE.name(),
                order.getOrderId().toString(),
                List.of(
                        WalletPosting.debit(accountService.ensureSystemAccount("PLATFORM_CASH"), amount),
                        WalletPosting.credit(accountService.ensureUserWallet(userId), amount)
                )
        ));
        rechargeOrderRepository.applyTransition(order.pay());
        return RechargeOrderResult.from(requireOrder(userId, requestId));
```

In `createOrLoad(...)`, replace the manual construction block:

```java
        RechargeOrder order = new RechargeOrder();
        order.setOrderId(idGenerator.next());
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus("CREATED");
```

with:

```java
        RechargeOrder order = RechargeOrder.create(idGenerator.next(), requestId, userId, amount);
```

Delete the private `ensureReplayMatches(...)` method from `WalletRechargeApplicationService`.

- [ ] **Step 4: Update mocked recharge application test**

In `WalletApplicationServiceRechargeTest.completeRechargeShouldLoadExistingOrderWhenInsertLosesDuplicateKeyRace`, replace:

```java
        verify(repository).updateStatus(userId, "recharge:req-race", "CREATED", "PAID");
```

with:

```java
        org.mockito.ArgumentCaptor<com.nowcoder.community.wallet.domain.model.RechargeOrderTransition> transitionCaptor =
                org.mockito.ArgumentCaptor.forClass(com.nowcoder.community.wallet.domain.model.RechargeOrderTransition.class);
        verify(repository).applyTransition(transitionCaptor.capture());
        assertThat(transitionCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(transitionCaptor.getValue().requestId()).isEqualTo("recharge:req-race");
        assertThat(transitionCaptor.getValue().fromStatus().code()).isEqualTo("CREATED");
        assertThat(transitionCaptor.getValue().toStatus().code()).isEqualTo("PAID");
```

- [ ] **Step 5: Run wallet recharge tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='RechargeOrderTest,WalletApplicationServiceRechargeTest,RechargeOrderMapperPersistenceTest'
```

Expected: PASS.

- [ ] **Step 6: Verify no raw recharge status comparison remains in application service**

Run:

```bash
rg -n '"CREATED"|"PAID"|ensureReplayMatches' backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java
```

Expected: no output.

- [ ] **Step 7: Commit wallet application migration**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/domain/repository/RechargeOrderRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/infrastructure/persistence/MyBatisRechargeOrderRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceRechargeTest.java
git commit -m "refactor(wallet): route recharge transitions through domain model"
```

---

### Task 3: Market String-Backed Domain Types

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketCodeEnum.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderStatus.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketGoodsType.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketDeliveryMode.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketStockMode.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketListingStatus.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketListing.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderStatusTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketListingTest.java`

- [ ] **Step 1: Write failing market enum and listing tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderStatusTest.java`:

```java
package com.nowcoder.community.market.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOrderStatusTest {

    @Test
    void fromCodeShouldResolvePersistedCodes() {
        assertThat(MarketOrderStatus.fromCode("ESCROW_PENDING")).isEqualTo(MarketOrderStatus.ESCROW_PENDING);
        assertThat(MarketOrderStatus.fromCode("ESCROWED")).isEqualTo(MarketOrderStatus.ESCROWED);
        assertThat(MarketOrderStatus.fromCode("DELIVERED")).isEqualTo(MarketOrderStatus.DELIVERED);
        assertThat(MarketOrderStatus.fromCode("SHIPPED")).isEqualTo(MarketOrderStatus.SHIPPED);
        assertThat(MarketOrderStatus.fromCode("RELEASE_PENDING")).isEqualTo(MarketOrderStatus.RELEASE_PENDING);
        assertThat(MarketOrderStatus.fromCode("COMPLETED")).isEqualTo(MarketOrderStatus.COMPLETED);
        assertThat(MarketOrderStatus.fromCode("REFUND_PENDING")).isEqualTo(MarketOrderStatus.REFUND_PENDING);
        assertThat(MarketOrderStatus.fromCode("CANCELLED")).isEqualTo(MarketOrderStatus.CANCELLED);
        assertThat(MarketOrderStatus.fromCode("ESCROW_CANCEL_PENDING")).isEqualTo(MarketOrderStatus.ESCROW_CANCEL_PENDING);
        assertThat(MarketOrderStatus.fromCode("ESCROW_FAILED")).isEqualTo(MarketOrderStatus.ESCROW_FAILED);
        assertThat(MarketOrderStatus.fromCode("DISPUTED")).isEqualTo(MarketOrderStatus.DISPUTED);
        assertThat(MarketOrderStatus.fromCode("DISPUTE_REFUND_PENDING")).isEqualTo(MarketOrderStatus.DISPUTE_REFUND_PENDING);
        assertThat(MarketOrderStatus.fromCode("DISPUTE_RELEASE_PENDING")).isEqualTo(MarketOrderStatus.DISPUTE_RELEASE_PENDING);
        assertThat(MarketOrderStatus.fromCode("REFUNDED")).isEqualTo(MarketOrderStatus.REFUNDED);
    }

    @Test
    void fromCodeShouldRejectUnknownCode() {
        assertThatThrownBy(() -> MarketOrderStatus.fromCode("BROKEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown market order status");
    }

    @Test
    void pendingWalletActionTypeShouldMapOnlyPendingStatuses() {
        assertThat(MarketOrderStatus.ESCROW_PENDING.pendingWalletActionType()).isEqualTo(MarketWalletActionType.ESCROW);
        assertThat(MarketOrderStatus.ESCROW_CANCEL_PENDING.pendingWalletActionType()).isEqualTo(MarketWalletActionType.ESCROW);
        assertThat(MarketOrderStatus.RELEASE_PENDING.pendingWalletActionType()).isEqualTo(MarketWalletActionType.RELEASE);
        assertThat(MarketOrderStatus.DISPUTE_RELEASE_PENDING.pendingWalletActionType()).isEqualTo(MarketWalletActionType.RELEASE);
        assertThat(MarketOrderStatus.REFUND_PENDING.pendingWalletActionType()).isEqualTo(MarketWalletActionType.REFUND);
        assertThat(MarketOrderStatus.DISPUTE_REFUND_PENDING.pendingWalletActionType()).isEqualTo(MarketWalletActionType.REFUND);
        assertThat(MarketOrderStatus.COMPLETED.pendingWalletActionType()).isNull();
    }
}
```

Create `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketListingTest.java`:

```java
package com.nowcoder.community.market.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketListingTest {

    @Test
    void physicalListingShouldUseFiniteStockEvenWhenStockModeIsUnlimited() {
        MarketListing listing = listing("PHYSICAL", "MANUAL", "UNLIMITED", 5, "ACTIVE");

        assertThat(listing.goodsType()).isEqualTo(MarketGoodsType.PHYSICAL);
        assertThat(listing.deliveryMode()).isEqualTo(MarketDeliveryMode.MANUAL);
        assertThat(listing.stockMode()).isEqualTo(MarketStockMode.UNLIMITED);
        assertThat(listing.isActive()).isTrue();
        assertThat(listing.isFiniteStock()).isTrue();
    }

    @Test
    void virtualFiniteListingShouldUseFiniteStock() {
        MarketListing listing = listing("VIRTUAL", "PRELOADED", "FINITE", 2, "ACTIVE");

        assertThat(listing.isFiniteStock()).isTrue();
        assertThat(listing.isPreloadedDelivery()).isTrue();
    }

    @Test
    void stockDecreaseShouldReturnSoldOutWhenNextAvailableIsZero() {
        MarketListing listing = listing("VIRTUAL", "MANUAL", "FINITE", 1, "ACTIVE");

        assertThat(listing.statusAfterStockDecreasedBy(1)).isEqualTo("SOLD_OUT");
    }

    @Test
    void stockRestoreShouldReactivateSoldOutListingWhenAvailableBecomesPositive() {
        MarketListing listing = listing("VIRTUAL", "MANUAL", "FINITE", 0, "SOLD_OUT");

        assertThat(listing.statusAfterStockRestoredBy(1)).isEqualTo("ACTIVE");
    }

    private static MarketListing listing(String goodsType, String deliveryMode, String stockMode, int available, String status) {
        MarketListing listing = new MarketListing();
        listing.setListingId(UUID.fromString("00000000-0000-7000-8000-000000000101"));
        listing.setSellerUserId(UUID.fromString("00000000-0000-7000-8000-000000000102"));
        listing.setGoodsType(goodsType);
        listing.setDeliveryMode(deliveryMode);
        listing.setStockMode(stockMode);
        listing.setStockAvailable(available);
        listing.setStatus(status);
        return listing;
    }
}
```

- [ ] **Step 2: Run market enum tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='MarketOrderStatusTest,MarketListingTest'
```

Expected: compilation fails because the enum classes and `MarketListing` helper methods do not exist.

- [ ] **Step 3: Add enum helper contract**

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketCodeEnum.java`:

```java
package com.nowcoder.community.market.domain.model;

interface MarketCodeEnum {

    String code();
}
```

- [ ] **Step 4: Add market order status enum**

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderStatus.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Arrays;
import java.util.Set;

public enum MarketOrderStatus implements MarketCodeEnum {
    ESCROW_PENDING("ESCROW_PENDING"),
    ESCROWED("ESCROWED"),
    DELIVERED("DELIVERED"),
    SHIPPED("SHIPPED"),
    RELEASE_PENDING("RELEASE_PENDING"),
    COMPLETED("COMPLETED"),
    REFUND_PENDING("REFUND_PENDING"),
    CANCELLED("CANCELLED"),
    ESCROW_CANCEL_PENDING("ESCROW_CANCEL_PENDING"),
    ESCROW_FAILED("ESCROW_FAILED"),
    DISPUTED("DISPUTED"),
    DISPUTE_REFUND_PENDING("DISPUTE_REFUND_PENDING"),
    DISPUTE_RELEASE_PENDING("DISPUTE_RELEASE_PENDING"),
    REFUNDED("REFUNDED");

    private static final Set<MarketOrderStatus> CONFIRMABLE = Set.of(DELIVERED, SHIPPED);
    private static final Set<MarketOrderStatus> DISPUTABLE = Set.of(DELIVERED, SHIPPED);

    private final String code;

    MarketOrderStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isConfirmable() {
        return CONFIRMABLE.contains(this);
    }

    public boolean isDisputable() {
        return DISPUTABLE.contains(this);
    }

    public String pendingWalletActionType() {
        return switch (this) {
            case ESCROW_PENDING, ESCROW_CANCEL_PENDING -> MarketWalletActionType.ESCROW;
            case RELEASE_PENDING, DISPUTE_RELEASE_PENDING -> MarketWalletActionType.RELEASE;
            case REFUND_PENDING, DISPUTE_REFUND_PENDING -> MarketWalletActionType.REFUND;
            default -> null;
        };
    }

    public static MarketOrderStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market order status: " + code));
    }
}
```

- [ ] **Step 5: Add goods, delivery, stock, and listing status enums**

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketGoodsType.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketGoodsType implements MarketCodeEnum {
    PHYSICAL("PHYSICAL"),
    VIRTUAL("VIRTUAL");

    private final String code;

    MarketGoodsType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isPhysical() {
        return this == PHYSICAL;
    }

    public boolean isVirtual() {
        return this == VIRTUAL;
    }

    public static MarketGoodsType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market goods type: " + code));
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketDeliveryMode.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketDeliveryMode implements MarketCodeEnum {
    MANUAL("MANUAL"),
    PRELOADED("PRELOADED");

    private final String code;

    MarketDeliveryMode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isManual() {
        return this == MANUAL;
    }

    public boolean isPreloaded() {
        return this == PRELOADED;
    }

    public static MarketDeliveryMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market delivery mode: " + code));
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketStockMode.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketStockMode implements MarketCodeEnum {
    FINITE("FINITE"),
    UNLIMITED("UNLIMITED");

    private final String code;

    MarketStockMode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isFinite() {
        return this == FINITE;
    }

    public static MarketStockMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market stock mode: " + code));
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketListingStatus.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketListingStatus implements MarketCodeEnum {
    ACTIVE("ACTIVE"),
    SOLD_OUT("SOLD_OUT");

    private final String code;

    MarketListingStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static MarketListingStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market listing status: " + code));
    }
}
```

- [ ] **Step 6: Add MarketListing helper methods**

In `MarketListing.java`, add this block before the first getter:

```java
    public MarketGoodsType goodsType() {
        return MarketGoodsType.fromCode(goodsType);
    }

    public MarketDeliveryMode deliveryMode() {
        return MarketDeliveryMode.fromCode(deliveryMode);
    }

    public MarketStockMode stockMode() {
        return MarketStockMode.fromCode(stockMode);
    }

    public boolean isActive() {
        return MarketListingStatus.ACTIVE.code().equals(status);
    }

    public boolean isSoldOut() {
        return MarketListingStatus.SOLD_OUT.code().equals(status);
    }

    public boolean isFiniteStock() {
        return goodsType().isPhysical() || stockMode().isFinite();
    }

    public boolean isPreloadedDelivery() {
        return deliveryMode().isPreloaded();
    }

    public String statusAfterStockDecreasedBy(int quantity) {
        int nextAvailable = stockAvailable - quantity;
        return nextAvailable <= 0 ? MarketListingStatus.SOLD_OUT.code() : status;
    }

    public String statusAfterStockRestoredBy(int quantity) {
        int nextAvailable = stockAvailable + quantity;
        return isSoldOut() && nextAvailable > 0 ? MarketListingStatus.ACTIVE.code() : status;
    }
```

- [ ] **Step 7: Run market enum and listing tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='MarketOrderStatusTest,MarketListingTest'
```

Expected: PASS.

- [ ] **Step 8: Commit market type extraction**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketCodeEnum.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderStatus.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketGoodsType.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketDeliveryMode.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketStockMode.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketListingStatus.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketListing.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderStatusTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketListingTest.java
git commit -m "refactor(market): add string-backed domain types"
```

---

### Task 4: Market Order Domain Behavior

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketAddressSnapshot.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderPlacement.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderTransition.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrder.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderTest.java`

- [ ] **Step 1: Write failing MarketOrder behavior tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderTest.java`:

```java
package com.nowcoder.community.market.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOrderTest {

    @Test
    void placeShouldCreateEscrowPendingOrderWithAddressSnapshot() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThat(order.getOrderId()).isEqualTo(uuid(1));
        assertThat(order.getRequestId()).isEqualTo("market:req-physical");
        assertThat(order.getListingId()).isEqualTo(uuid(2));
        assertThat(order.getSellerUserId()).isEqualTo(uuid(3));
        assertThat(order.getBuyerUserId()).isEqualTo(uuid(4));
        assertThat(order.goodsType()).isEqualTo(MarketGoodsType.PHYSICAL);
        assertThat(order.status()).isEqualTo(MarketOrderStatus.ESCROW_PENDING);
        assertThat(order.getStatus()).isEqualTo("ESCROW_PENDING");
        assertThat(order.getAddressIdSnapshot()).isEqualTo(uuid(5));
        assertThat(order.getReceiverNameSnapshot()).isEqualTo("Buyer");
    }

    @Test
    void assertReplayMatchesShouldAllowSamePayload() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        order.assertReplayMatches(uuid(4), uuid(2), 1, uuid(5), null);
    }

    @Test
    void assertReplayMatchesShouldRejectDifferentQuantity() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThatThrownBy(() -> order.assertReplayMatches(uuid(4), uuid(2), 2, uuid(5), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));
    }

    @Test
    void assertBuyerAndSellerShouldRejectWrongActor() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThatThrownBy(() -> order.assertBuyer(uuid(6)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actor is not market order buyer");
        assertThatThrownBy(() -> order.assertSeller(uuid(6)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actor is not market order seller");
    }

    @Test
    void requestReleaseShouldRequireDeliveredOrShipped() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThatThrownBy(order::requestRelease)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("order is not confirmable");

        order.setStatus("SHIPPED");

        MarketOrderTransition transition = order.requestRelease();

        assertThat(transition.orderId()).isEqualTo(uuid(1));
        assertThat(transition.expectedStatuses()).containsExactlyInAnyOrder(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED);
        assertThat(transition.nextStatus()).isEqualTo(MarketOrderStatus.RELEASE_PENDING);
    }

    @Test
    void cancelTransitionsShouldDependOnCurrentStatus() {
        MarketOrder order = MarketOrder.place(physicalPlacement());
        assertThat(order.requestEscrowCancel().nextStatus()).isEqualTo(MarketOrderStatus.ESCROW_CANCEL_PENDING);

        order.setStatus("ESCROWED");
        assertThat(order.requestRefund().nextStatus()).isEqualTo(MarketOrderStatus.REFUND_PENDING);
    }

    @Test
    void pendingWalletActionTypeShouldDelegateToStatus() {
        MarketOrder order = MarketOrder.place(physicalPlacement());
        assertThat(order.pendingWalletActionType()).isEqualTo(MarketWalletActionType.ESCROW);

        order.setStatus("DISPUTE_RELEASE_PENDING");
        assertThat(order.pendingWalletActionType()).isEqualTo(MarketWalletActionType.RELEASE);
    }

    @Test
    void autoConfirmShouldRequireDueConfirmableOrder() {
        MarketOrder order = MarketOrder.place(physicalPlacement());
        Date now = new Date(1_000L);
        order.setStatus("DELIVERED");
        order.setAutoConfirmAt(new Date(500L));

        assertThat(order.isAutoConfirmDue(now)).isTrue();

        order.setAutoConfirmAt(new Date(1_500L));
        assertThat(order.isAutoConfirmDue(now)).isFalse();
    }

    private static MarketOrderPlacement physicalPlacement() {
        return new MarketOrderPlacement(
                uuid(1),
                "market:req-physical",
                uuid(2),
                MarketGoodsType.PHYSICAL,
                uuid(3),
                uuid(4),
                1,
                12900L,
                12900L,
                MarketDeliveryMode.MANUAL,
                "二手键盘",
                new MarketAddressSnapshot(uuid(5), "Buyer", "13800000000", "浙江", "杭州", "西湖", "文三路 1 号", "310000")
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", value));
    }
}
```

- [ ] **Step 2: Run MarketOrder behavior tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=MarketOrderTest
```

Expected: compilation fails because the value objects and `MarketOrder` behavior methods do not exist.

- [ ] **Step 3: Add address snapshot and placement values**

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketAddressSnapshot.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MarketAddressSnapshot(
        UUID addressId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode
) {
    public MarketAddressSnapshot {
        Objects.requireNonNull(addressId, "addressId must not be null");
    }

    public static MarketAddressSnapshot from(MarketAddress address) {
        return new MarketAddressSnapshot(
                address.getAddressId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getPostalCode()
        );
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderPlacement.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MarketOrderPlacement(
        UUID orderId,
        String requestId,
        UUID listingId,
        MarketGoodsType goodsType,
        UUID sellerUserId,
        UUID buyerUserId,
        int quantity,
        long unitPriceSnapshot,
        long totalAmount,
        MarketDeliveryMode deliveryModeSnapshot,
        String listingTitleSnapshot,
        MarketAddressSnapshot addressSnapshot
) {
    public MarketOrderPlacement {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        Objects.requireNonNull(listingId, "listingId must not be null");
        Objects.requireNonNull(goodsType, "goodsType must not be null");
        Objects.requireNonNull(sellerUserId, "sellerUserId must not be null");
        Objects.requireNonNull(buyerUserId, "buyerUserId must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPriceSnapshot < 0 || totalAmount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        Objects.requireNonNull(deliveryModeSnapshot, "deliveryModeSnapshot must not be null");
        if (goodsType.isPhysical() && addressSnapshot == null) {
            throw new IllegalArgumentException("addressSnapshot must not be null for physical order");
        }
    }
}
```

- [ ] **Step 4: Add MarketOrderTransition**

Create `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderTransition.java`:

```java
package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MarketOrderTransition(
        UUID orderId,
        Set<MarketOrderStatus> expectedStatuses,
        MarketOrderStatus nextStatus,
        UUID escrowTxnId,
        UUID releaseTxnId,
        UUID refundTxnId,
        Date autoConfirmAt
) {
    public MarketOrderTransition {
        Objects.requireNonNull(orderId, "orderId must not be null");
        expectedStatuses = Set.copyOf(Objects.requireNonNull(expectedStatuses, "expectedStatuses must not be null"));
        if (expectedStatuses.isEmpty()) {
            throw new IllegalArgumentException("expectedStatuses must not be empty");
        }
        Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        autoConfirmAt = copy(autoConfirmAt);
    }

    @Override
    public Date autoConfirmAt() {
        return copy(autoConfirmAt);
    }

    public static MarketOrderTransition delivered(UUID orderId, Date autoConfirmAt) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.ESCROWED), MarketOrderStatus.DELIVERED, null, null, null, autoConfirmAt);
    }

    public static MarketOrderTransition shipped(UUID orderId, Date autoConfirmAt) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.ESCROWED), MarketOrderStatus.SHIPPED, null, null, null, autoConfirmAt);
    }

    public static MarketOrderTransition releasePending(UUID orderId) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED), MarketOrderStatus.RELEASE_PENDING, null, null, null, null);
    }

    public static MarketOrderTransition refundPending(UUID orderId) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.ESCROWED), MarketOrderStatus.REFUND_PENDING, null, null, null, null);
    }

    public static MarketOrderTransition escrowCancelPending(UUID orderId) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.ESCROW_PENDING), MarketOrderStatus.ESCROW_CANCEL_PENDING, null, null, null, null);
    }

    public static MarketOrderTransition disputed(UUID orderId) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED), MarketOrderStatus.DISPUTED, null, null, null, null);
    }

    public static MarketOrderTransition disputeRefundPending(UUID orderId) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.DISPUTED), MarketOrderStatus.DISPUTE_REFUND_PENDING, null, null, null, null);
    }

    public static MarketOrderTransition disputeReleasePending(UUID orderId) {
        return new MarketOrderTransition(orderId, Set.of(MarketOrderStatus.DISPUTED), MarketOrderStatus.DISPUTE_RELEASE_PENDING, null, null, null, null);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
```

- [ ] **Step 5: Add behavior to MarketOrder**

In `MarketOrder.java`, add imports:

```java
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.exception.MarketErrorCode;

import java.util.Objects;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
```

Add this block inside `MarketOrder`, before the first getter:

```java
    public static MarketOrder place(MarketOrderPlacement placement) {
        MarketOrder order = new MarketOrder();
        order.setOrderId(placement.orderId());
        order.setRequestId(placement.requestId());
        order.setListingId(placement.listingId());
        order.setGoodsType(placement.goodsType().code());
        order.setSellerUserId(placement.sellerUserId());
        order.setBuyerUserId(placement.buyerUserId());
        order.setQuantity(placement.quantity());
        order.setUnitPriceSnapshot(placement.unitPriceSnapshot());
        order.setTotalAmount(placement.totalAmount());
        order.setDeliveryModeSnapshot(placement.deliveryModeSnapshot().code());
        order.setListingTitleSnapshot(placement.listingTitleSnapshot());
        order.setStatus(MarketOrderStatus.ESCROW_PENDING.code());
        if (placement.addressSnapshot() != null) {
            order.applyAddressSnapshot(placement.addressSnapshot());
        }
        return order;
    }

    public MarketOrderStatus status() {
        return MarketOrderStatus.fromCode(status);
    }

    public MarketGoodsType goodsType() {
        return MarketGoodsType.fromCode(goodsType);
    }

    public MarketDeliveryMode deliveryMode() {
        return MarketDeliveryMode.fromCode(deliveryModeSnapshot);
    }

    public void assertReplayMatches(UUID buyerUserId, UUID listingId, int quantity, UUID addressId, MarketAddressSnapshot suppliedAddressSnapshot) {
        if (!Objects.equals(this.buyerUserId, buyerUserId)
                || !Objects.equals(this.listingId, listingId)
                || this.quantity != quantity
                || !addressMatchesReplay(addressId, suppliedAddressSnapshot)) {
            throw new BusinessException(
                    MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + requestId
            );
        }
    }

    public void assertBuyer(UUID actorUserId) {
        if (!Objects.equals(actorUserId, buyerUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market order buyer");
        }
    }

    public void assertSeller(UUID actorUserId) {
        if (!Objects.equals(actorUserId, sellerUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market order seller");
        }
    }

    public void assertEscrowed() {
        requireStatus(MarketOrderStatus.ESCROWED);
    }

    public void assertPhysical() {
        if (!goodsType().isPhysical()) {
            throw new BusinessException(INVALID_ARGUMENT, "order goodsType mismatch: orderId=" + orderId);
        }
    }

    public void assertVirtual() {
        if (!goodsType().isVirtual()) {
            throw new BusinessException(INVALID_ARGUMENT, "order goodsType mismatch: orderId=" + orderId);
        }
    }

    public void assertManualDelivery() {
        if (!deliveryMode().isManual()) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not MANUAL delivery: orderId=" + orderId);
        }
    }

    public boolean isConfirmable() {
        return status().isConfirmable();
    }

    public boolean isDisputable() {
        return status().isDisputable();
    }

    public boolean isEscrowPending() {
        return MarketOrderStatus.ESCROW_PENDING.equals(status());
    }

    public boolean isEscrowCancelPending() {
        return MarketOrderStatus.ESCROW_CANCEL_PENDING.equals(status());
    }

    public boolean isPreloadedDelivery() {
        return deliveryMode().isPreloaded();
    }

    public boolean isAutoConfirmDue(Date now) {
        return now != null && isConfirmable() && autoConfirmAt != null && !autoConfirmAt.after(now);
    }

    public MarketOrderTransition markDelivered(Date autoConfirmAt) {
        requireStatus(MarketOrderStatus.ESCROWED);
        return MarketOrderTransition.delivered(orderId, autoConfirmAt);
    }

    public MarketOrderTransition markShipped(Date autoConfirmAt) {
        requireStatus(MarketOrderStatus.ESCROWED);
        return MarketOrderTransition.shipped(orderId, autoConfirmAt);
    }

    public MarketOrderTransition requestRelease() {
        if (!isConfirmable()) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not confirmable: orderId=" + orderId);
        }
        return MarketOrderTransition.releasePending(orderId);
    }

    public MarketOrderTransition requestRefund() {
        requireStatus(MarketOrderStatus.ESCROWED);
        return MarketOrderTransition.refundPending(orderId);
    }

    public MarketOrderTransition requestEscrowCancel() {
        requireStatus(MarketOrderStatus.ESCROW_PENDING);
        return MarketOrderTransition.escrowCancelPending(orderId);
    }

    public MarketOrderTransition openDispute() {
        if (!isDisputable()) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not disputable: orderId=" + orderId);
        }
        return MarketOrderTransition.disputed(orderId);
    }

    public void assertDisputed() {
        requireStatus(MarketOrderStatus.DISPUTED);
    }

    public MarketOrderTransition requestDisputeRefund() {
        requireStatus(MarketOrderStatus.DISPUTED);
        return MarketOrderTransition.disputeRefundPending(orderId);
    }

    public MarketOrderTransition requestDisputeRelease() {
        requireStatus(MarketOrderStatus.DISPUTED);
        return MarketOrderTransition.disputeReleasePending(orderId);
    }

    public String pendingWalletActionType() {
        return status().pendingWalletActionType();
    }

    private void applyAddressSnapshot(MarketAddressSnapshot snapshot) {
        setAddressIdSnapshot(snapshot.addressId());
        setReceiverNameSnapshot(snapshot.receiverName());
        setReceiverPhoneSnapshot(snapshot.receiverPhone());
        setProvinceSnapshot(snapshot.province());
        setCitySnapshot(snapshot.city());
        setDistrictSnapshot(snapshot.district());
        setDetailAddressSnapshot(snapshot.detailAddress());
        setPostalCodeSnapshot(snapshot.postalCode());
    }

    private boolean addressMatchesReplay(UUID addressId, MarketAddressSnapshot suppliedSnapshot) {
        if (!goodsType().isPhysical()) {
            return true;
        }
        if (addressIdSnapshot != null) {
            return Objects.equals(addressIdSnapshot, addressId);
        }
        return suppliedSnapshot != null
                && Objects.equals(suppliedSnapshot.addressId(), addressId)
                && Objects.equals(suppliedSnapshot.receiverName(), receiverNameSnapshot)
                && Objects.equals(suppliedSnapshot.receiverPhone(), receiverPhoneSnapshot)
                && Objects.equals(suppliedSnapshot.province(), provinceSnapshot)
                && Objects.equals(suppliedSnapshot.city(), citySnapshot)
                && Objects.equals(suppliedSnapshot.district(), districtSnapshot)
                && Objects.equals(suppliedSnapshot.detailAddress(), detailAddressSnapshot)
                && Objects.equals(suppliedSnapshot.postalCode(), postalCodeSnapshot);
    }

    private void requireStatus(MarketOrderStatus expected) {
        if (!expected.equals(status())) {
            throw new BusinessException(INVALID_ARGUMENT, "order status mismatch: orderId=" + orderId);
        }
    }
```

- [ ] **Step 6: Run MarketOrder behavior tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='MarketOrderStatusTest,MarketListingTest,MarketOrderTest'
```

Expected: PASS.

- [ ] **Step 7: Commit MarketOrder domain behavior**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketAddressSnapshot.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderPlacement.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrderTransition.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrder.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderTest.java
git commit -m "refactor(market): add order lifecycle behavior"
```

---

### Task 5: Market Order Creation Migration

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceUnitTest.java`

- [ ] **Step 1: Add imports for new market domain types**

In `MarketOrderApplicationService.java`, add imports:

```java
import com.nowcoder.community.market.domain.model.MarketAddressSnapshot;
import com.nowcoder.community.market.domain.model.MarketDeliveryMode;
import com.nowcoder.community.market.domain.model.MarketGoodsType;
import com.nowcoder.community.market.domain.model.MarketOrderPlacement;
```

- [ ] **Step 2: Replace replay calls**

In `createOrder(...)`, replace each call to:

```java
ensureReplayMatches(existing, buyerUserId, listingId, quantity, addressId);
```

with:

```java
existing.assertReplayMatches(buyerUserId, listingId, quantity, addressId, replayAddressSnapshot(existing, buyerUserId, addressId));
```

Replace the duplicate-key replay call:

```java
ensureReplayMatches(duplicated, buyerUserId, listingId, quantity, addressId);
```

with:

```java
duplicated.assertReplayMatches(buyerUserId, listingId, quantity, addressId, replayAddressSnapshot(duplicated, buyerUserId, addressId));
```

- [ ] **Step 3: Replace manual MarketOrder construction**

In `createOrder(...)`, replace the block from `UUID orderId = idGenerator.next();` through the physical address snapshot block with:

```java
        MarketAddressSnapshot addressSnapshot = null;
        if (listing.goodsType().isPhysical()) {
            addressSnapshot = MarketAddressSnapshot.from(requireActiveAddress(addressId, buyerUserId));
        }

        MarketOrder order = MarketOrder.place(new MarketOrderPlacement(
                idGenerator.next(),
                requestId,
                listing.getListingId(),
                listing.goodsType(),
                listing.getSellerUserId(),
                buyerUserId,
                quantity,
                listing.getUnitPrice(),
                totalAmount,
                listing.deliveryMode(),
                listing.getTitle(),
                addressSnapshot
        ));
```

- [ ] **Step 4: Replace listing and inventory helper internals**

Replace `requireActiveListing(...)` with:

```java
    private void requireActiveListing(MarketListing listing) {
        if (!listing.isActive()) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing is not active: listingId=" + listing.getListingId());
        }
    }
```

Replace `validateBuyerAndQuantity(...)` with:

```java
    private void validateBuyerAndQuantity(UUID buyerUserId, MarketListing listing, int quantity) {
        orderDomainService.validateCreateOrder(buyerUserId, listing.getSellerUserId(), quantity);
        if (quantity < listing.getMinPurchaseQuantity() || quantity > listing.getMaxPurchaseQuantity()) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity is outside listing purchase limits: listingId=" + listing.getListingId());
        }
        if (listing.isFiniteStock() && listing.getStockAvailable() < quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "listing stock is insufficient: listingId=" + listing.getListingId());
        }
    }
```

Replace `isFiniteStock(...)` with:

```java
    private boolean isFiniteStock(MarketListing listing) {
        return listing.isFiniteStock();
    }
```

Replace `reserveInventoryIfNeeded(...)` with:

```java
    private List<MarketInventoryUnit> reserveInventoryIfNeeded(MarketListing listing, int quantity) {
        if (!listing.goodsType().isVirtual() || !listing.isPreloadedDelivery()) {
            return List.of();
        }
        List<MarketInventoryUnit> units = marketInventoryRepository.lockAvailable(listing.getListingId(), quantity);
        if (units.size() != quantity) {
            throw new BusinessException(INVALID_ARGUMENT, "preloaded inventory is insufficient: listingId=" + listing.getListingId());
        }
        return units;
    }
```

Replace `adjustFiniteStockAfterOrder(...)` with:

```java
    private void adjustFiniteStockAfterOrder(MarketListing listing, int quantity) {
        if (!listing.isFiniteStock()) {
            return;
        }
        marketListingRepository.adjustStock(
                listing.getListingId(),
                listing.getSellerUserId(),
                0,
                -quantity,
                listing.statusAfterStockDecreasedBy(quantity)
        );
    }
```

- [ ] **Step 5: Replace replay address helper methods**

Delete `ensureReplayMatches(...)`, `addressMatchesReplay(...)`, and `snapshotAddress(...)`.

Add this helper:

```java
    private MarketAddressSnapshot replayAddressSnapshot(MarketOrder order, UUID buyerUserId, UUID addressId) {
        if (!order.goodsType().isPhysical() || order.getAddressIdSnapshot() != null || addressId == null) {
            return null;
        }
        MarketAddress address = marketAddressRepository.findById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), buyerUserId)) {
            return null;
        }
        return MarketAddressSnapshot.from(address);
    }
```

- [ ] **Step 6: Remove unused market creation imports/constants**

Remove these private constants from `MarketOrderApplicationService` if no remaining code uses them:

```java
private static final String GOODS_TYPE_PHYSICAL = "PHYSICAL";
private static final String GOODS_TYPE_VIRTUAL = "VIRTUAL";
private static final String STATUS_ACTIVE = "ACTIVE";
private static final String STATUS_ESCROW_PENDING = "ESCROW_PENDING";
private static final String STATUS_SOLD_OUT = "SOLD_OUT";
private static final String STOCK_MODE_FINITE = "FINITE";
private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
```

Remove unused imports reported by the compiler.

- [ ] **Step 7: Run create-order focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='MarketOrderTest,MarketListingTest,MarketOrderApplicationServiceTest,MarketOrderApplicationServiceUnitTest'
```

Expected: PASS.

- [ ] **Step 8: Verify creation path has no private status/type strings for domain decisions**

Run:

```bash
rg -n 'GOODS_TYPE_|STATUS_ESCROW_PENDING|STATUS_ACTIVE|STATUS_SOLD_OUT|STOCK_MODE_|DELIVERY_MODE_PRELOADED|ensureReplayMatches|snapshotAddress|addressMatchesReplay' backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java
```

Expected: no output for the deleted constants and helper names. `DELIVERY_MODE_MANUAL`, `DELIVERY_TYPE_MANUAL_TEXT`, and `DELIVERY_STATUS_DELIVERED` may still appear until Task 6 handles delivery behavior.

- [ ] **Step 9: Commit market creation migration**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceUnitTest.java
git commit -m "refactor(market): create orders through domain factory"
```

---

### Task 6: Market Order Action Migration

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderAutoConfirmSingleOrderApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderAutoConfirmSingleOrderApplicationServiceUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/infrastructure/job/MarketOrderAutoConfirmHandlerTest.java`

- [ ] **Step 1: Replace delivery status checks in MarketOrderApplicationService**

In `deliverVirtualOrder(...)`, replace:

```java
        requireSeller(order, sellerUserId);
        requireGoodsType(order, GOODS_TYPE_VIRTUAL);
        requireStatus(order, STATUS_ESCROWED);
        if (!DELIVERY_MODE_MANUAL.equals(order.getDeliveryModeSnapshot())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not MANUAL delivery: orderId=" + orderId);
        }
```

with:

```java
        order.assertSeller(sellerUserId);
        order.assertVirtual();
        order.assertEscrowed();
        order.assertManualDelivery();
```

Replace:

```java
        marketOrderRepository.markDelivered(orderId, Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
```

with:

```java
        MarketOrderTransition transition = order.markDelivered(Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        marketOrderRepository.markDelivered(transition.orderId(), transition.autoConfirmAt());
```

- [ ] **Step 2: Replace shipping status checks**

In `shipPhysicalOrder(...)`, replace:

```java
        requireSeller(order, sellerUserId);
        requireGoodsType(order, GOODS_TYPE_PHYSICAL);
        requireStatus(order, STATUS_ESCROWED);
```

with:

```java
        order.assertSeller(sellerUserId);
        order.assertPhysical();
        order.assertEscrowed();
```

Replace:

```java
        marketOrderRepository.markShipped(orderId, Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
```

with:

```java
        MarketOrderTransition transition = order.markShipped(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        marketOrderRepository.markShipped(transition.orderId(), transition.autoConfirmAt());
```

- [ ] **Step 3: Replace confirm and cancel status decisions**

In `confirmOrder(...)`, replace the body after loading the order with:

```java
        order.assertBuyer(buyerUserId);
        MarketOrderTransition transition = order.requestRelease();

        int updated = marketOrderRepository.markReleasePending(transition.orderId());
        if (updated == 1) {
            marketWalletActionService.enqueueRelease(
                    orderId,
                    order.getSellerUserId(),
                    order.getBuyerUserId(),
                    order.getTotalAmount()
            );
        }
        return MarketOrderResult.from(reloadOrder(orderId));
```

In `cancelOrder(...)`, replace the body after loading the order with:

```java
        order.assertBuyer(buyerUserId);
        if (order.status() == MarketOrderStatus.ESCROWED) {
            MarketOrderTransition transition = order.requestRefund();
            int updated = marketOrderRepository.markRefundPending(transition.orderId());
            if (updated == 1) {
                marketWalletActionService.enqueueRefund(orderId, buyerUserId, order.getSellerUserId(), order.getTotalAmount());
            }
            return MarketOrderResult.from(reloadOrder(orderId));
        }
        if (order.status() == MarketOrderStatus.ESCROW_PENDING) {
            MarketOrderTransition transition = order.requestEscrowCancel();
            int updated = marketOrderRepository.markEscrowCancelPending(transition.orderId());
            if (updated == 1 && marketWalletActionService.cancelPendingEscrowIfPossible(orderId)) {
                marketOrderSagaService.completeEscrowNoop(orderId);
            }
            return MarketOrderResult.from(reloadOrder(orderId));
        }
        throw new BusinessException(INVALID_ARGUMENT, "order status mismatch: orderId=" + order.getOrderId());
```

Add imports:

```java
import com.nowcoder.community.market.domain.model.MarketOrderStatus;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
```

- [ ] **Step 4: Remove old local actor/type/status helpers**

Delete these private methods from `MarketOrderApplicationService`:

```java
private void requireSeller(MarketOrder order, UUID sellerUserId)
private void requireBuyer(MarketOrder order, UUID buyerUserId)
private void requireGoodsType(MarketOrder order, String goodsType)
private void requireStatus(MarketOrder order, String status)
```

Remove private constants that only supported those helpers:

```java
private static final String GOODS_TYPE_PHYSICAL = "PHYSICAL";
private static final String GOODS_TYPE_VIRTUAL = "VIRTUAL";
private static final String STATUS_ESCROWED = "ESCROWED";
private static final String STATUS_DELIVERED = "DELIVERED";
private static final String STATUS_SHIPPED = "SHIPPED";
private static final String DELIVERY_MODE_MANUAL = "MANUAL";
```

Keep these constants if they are still used for creating `MarketDelivery` rows:

```java
private static final String DELIVERY_TYPE_MANUAL_TEXT = "MANUAL_TEXT";
private static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";
```

- [ ] **Step 5: Refactor auto-confirm single-order service**

In `MarketOrderAutoConfirmSingleOrderApplicationService.java`, remove:

```java
private static final String STATUS_DELIVERED = "DELIVERED";
private static final String STATUS_SHIPPED = "SHIPPED";
```

Remove unused import:

```java
import java.util.Set;
```

Replace `confirmOneDueOrder(...)` body with:

```java
        MarketOrder locked = marketOrderRepository.lockById(orderId);
        if (locked == null || !locked.isAutoConfirmDue(now)) {
            return false;
        }
        int updated = marketOrderRepository.markReleasePending(locked.requestRelease().orderId());
        if (updated != 1) {
            return false;
        }
        marketWalletActionService.enqueueRelease(
                locked.getOrderId(),
                locked.getSellerUserId(),
                locked.getBuyerUserId(),
                locked.getTotalAmount()
        );
        return true;
```

- [ ] **Step 6: Run order action and auto-confirm tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='MarketOrderTest,MarketOrderApplicationServiceTest,MarketOrderAutoConfirmSingleOrderApplicationServiceUnitTest,MarketOrderAutoConfirmApplicationServiceUnitTest,MarketOrderAutoConfirmHandlerTest'
```

Expected: PASS.

- [ ] **Step 7: Verify MarketOrderApplicationService has no domain decision string constants**

Run:

```bash
rg -n 'GOODS_TYPE_|STATUS_ESCROW|STATUS_DELIVERED|STATUS_SHIPPED|DELIVERY_MODE_|STOCK_MODE_' backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java
```

Expected: no output except `DELIVERY_TYPE_MANUAL_TEXT` and `DELIVERY_STATUS_DELIVERED` if the search pattern is expanded manually to include delivery row constants.

- [ ] **Step 8: Commit market action migration**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderAutoConfirmSingleOrderApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderAutoConfirmSingleOrderApplicationServiceUnitTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/infrastructure/job/MarketOrderAutoConfirmHandlerTest.java
git commit -m "refactor(market): use order domain behavior for lifecycle actions"
```

---

### Task 7: Market Saga, Recovery, And Dispute Migration

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketDisputeApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketDisputeApplicationServiceTest.java`

- [ ] **Step 1: Refactor MarketOrderSagaApplicationService**

Remove private constants:

```java
private static final String GOODS_TYPE_PHYSICAL = "PHYSICAL";
private static final String STOCK_MODE_FINITE = "FINITE";
private static final String STATUS_ACTIVE = "ACTIVE";
private static final String STATUS_SOLD_OUT = "SOLD_OUT";
private static final String STATUS_ESCROW_PENDING = "ESCROW_PENDING";
private static final String STATUS_ESCROW_CANCEL_PENDING = "ESCROW_CANCEL_PENDING";
private static final String DELIVERY_MODE_PRELOADED = "PRELOADED";
```

Replace `canApplyEscrow(...)` with:

```java
    @Transactional(readOnly = true)
    public boolean canApplyEscrow(UUID orderId) {
        MarketOrder order = marketOrderRepository.findById(orderId);
        return order != null && order.isEscrowPending();
    }
```

In `markEscrowTerminalFailed(...)`, replace:

```java
        if (updated != 1 && STATUS_ESCROW_CANCEL_PENDING.equals(order.getStatus())) {
            updated = marketOrderRepository.markCancelledNoRefund(orderId);
        }
```

with:

```java
        if (updated != 1 && order.isEscrowCancelPending()) {
            updated = marketOrderRepository.markCancelledNoRefund(orderId);
        }
```

Replace `deliverPreloadedInventoryIfNeeded(...)` with:

```java
    private void deliverPreloadedInventoryIfNeeded(MarketOrder order) {
        if (order == null || !order.isPreloadedDelivery()) {
            return;
        }
        Date deliveredAt = new Date();
        marketInventoryRepository.markDeliveredByOrderIfReserved(order.getOrderId(), deliveredAt);
        marketOrderRepository.markDelivered(order.getOrderId(), Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
    }
```

Replace `restoreMarketSideCompensation(...)` with:

```java
    private void restoreMarketSideCompensation(MarketOrder order) {
        if (order == null) {
            return;
        }
        MarketListing listing = marketListingRepository.lockById(order.getListingId());
        if (listing != null && listing.isFiniteStock()) {
            marketListingRepository.adjustStock(
                    listing.getListingId(),
                    listing.getSellerUserId(),
                    0,
                    order.getQuantity(),
                    listing.statusAfterStockRestoredBy(order.getQuantity())
            );
        }
        if (order.isPreloadedDelivery()) {
            marketInventoryRepository.releaseReservedByOrderIfNeeded(order.getOrderId());
        }
    }
```

Delete `isFiniteStock(...)`.

- [ ] **Step 2: Refactor MarketWalletActionRecoveryApplicationService status mapping**

In `MarketWalletActionRecoveryApplicationService.java`, remove private order-status constants:

```java
private static final String STATUS_ESCROW_PENDING = "ESCROW_PENDING";
private static final String STATUS_ESCROW_CANCEL_PENDING = "ESCROW_CANCEL_PENDING";
private static final String STATUS_RELEASE_PENDING = "RELEASE_PENDING";
private static final String STATUS_REFUND_PENDING = "REFUND_PENDING";
private static final String STATUS_DISPUTE_RELEASE_PENDING = "DISPUTE_RELEASE_PENDING";
private static final String STATUS_DISPUTE_REFUND_PENDING = "DISPUTE_REFUND_PENDING";
```

Replace:

```java
        String actionType = actionTypeFor(order.getStatus());
```

with:

```java
        String actionType = order.pendingWalletActionType();
```

Replace:

```java
        if (STATUS_ESCROW_CANCEL_PENDING.equals(order.getStatus())
                && MarketWalletActionType.ESCROW.equals(action.getActionType())
```

with:

```java
        if (order.isEscrowCancelPending()
                && MarketWalletActionType.ESCROW.equals(action.getActionType())
```

Replace:

```java
            if (STATUS_ESCROW_CANCEL_PENDING.equals(order.getStatus())) {
```

with:

```java
            if (order.isEscrowCancelPending()) {
```

Delete the private `actionTypeFor(String orderStatus)` method.

- [ ] **Step 3: Refactor MarketDisputeApplicationService order decisions**

In `MarketDisputeApplicationService.java`, add import:

```java
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
```

Remove private order-status constants:

```java
private static final String ORDER_STATUS_DELIVERED = "DELIVERED";
private static final String ORDER_STATUS_SHIPPED = "SHIPPED";
private static final String ORDER_STATUS_DISPUTED = "DISPUTED";
```

In `openDispute(...)`, replace:

```java
        if (!Set.of(ORDER_STATUS_DELIVERED, ORDER_STATUS_SHIPPED).contains(order.getStatus())) {
            throw new BusinessException(INVALID_ARGUMENT, "order is not disputable: orderId=" + orderId);
        }
```

with:

```java
        MarketOrderTransition transition = order.openDispute();
```

Replace:

```java
        marketOrderRepository.markDisputed(orderId);
```

with:

```java
        marketOrderRepository.markDisputed(transition.orderId());
```

In `sellerAcceptRefund(...)` and `adminResolveRefund(...)`, replace:

```java
        marketOrderRepository.markDisputeRefundPending(order.getOrderId());
```

with:

```java
        marketOrderRepository.markDisputeRefundPending(order.requestDisputeRefund().orderId());
```

In `adminResolveRelease(...)`, replace:

```java
        marketOrderRepository.markDisputeReleasePending(order.getOrderId());
```

with:

```java
        marketOrderRepository.markDisputeReleasePending(order.requestDisputeRelease().orderId());
```

Replace `requireDisputedOrderForUpdate(...)` with:

```java
    private MarketOrder requireDisputedOrderForUpdate(UUID orderId) {
        MarketOrder order = requireOrderForUpdate(orderId);
        order.assertDisputed();
        return order;
    }
```

Remove unused import:

```java
import java.util.Set;
```

- [ ] **Step 4: Run saga, recovery, and dispute tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='MarketOrderSagaApplicationServiceTest,MarketWalletActionRecoveryApplicationServiceTest,MarketDisputeApplicationServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Verify duplicated status mapping is gone**

Run:

```bash
rg -n 'STATUS_ESCROW|STATUS_RELEASE|STATUS_REFUND|DISPUTE_RELEASE_PENDING|DISPUTE_REFUND_PENDING|ORDER_STATUS_' backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationService.java backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationService.java backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketDisputeApplicationService.java
```

Expected: no output.

- [ ] **Step 6: Commit saga, recovery, and dispute migration**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketDisputeApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderSagaApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketDisputeApplicationServiceTest.java
git commit -m "refactor(market): centralize saga and dispute order decisions"
```

---

### Task 8: Verification, Cleanup, And Documentation

**Files:**
- Modify: `docs/handbook/business-logic/wallet.md`
- Modify: `docs/handbook/business-logic/market.md`
- Modify: `docs/handbook/business-logic/workflows/market-wallet.md`
- Modify: `docs/handbook/business-flows.md`
- Modify: `docs/handbook/reliability.md`

- [ ] **Step 1: Run all focused wallet and market tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='RechargeOrderTest,WalletApplicationServiceRechargeTest,RechargeOrderMapperPersistenceTest,MarketOrderStatusTest,MarketListingTest,MarketOrderTest,MarketOrderApplicationServiceTest,MarketOrderApplicationServiceUnitTest,MarketOrderAutoConfirmSingleOrderApplicationServiceUnitTest,MarketOrderAutoConfirmApplicationServiceUnitTest,MarketOrderAutoConfirmHandlerTest,MarketOrderSagaApplicationServiceTest,MarketWalletActionRecoveryApplicationServiceTest,MarketDisputeApplicationServiceTest'
```

Expected: PASS.

- [ ] **Step 2: Run architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS.

- [ ] **Step 3: Scan for prohibited raw order/recharge status decisions**

Run:

```bash
rg -n '"CREATED"|"PAID"' backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java
rg -n 'GOODS_TYPE_|STATUS_ESCROW|STATUS_DELIVERED|STATUS_SHIPPED|STATUS_RELEASE|STATUS_REFUND|ORDER_STATUS_|STOCK_MODE_|DELIVERY_MODE_' backend/community-app/src/main/java/com/nowcoder/community/market/application
```

Expected:

- First command prints no output.
- Second command prints no output for order/goods/stock/delivery-mode domain decisions. `DELIVERY_TYPE_MANUAL_TEXT`, `DELIVERY_STATUS_DELIVERED`, dispute status constants, and dispute resolution constants may remain because they are not order lifecycle decisions.

- [ ] **Step 4: Scan domain imports for forbidden dependencies**

Run:

```bash
rg -n 'org\\.springframework|org\\.apache\\.ibatis|\\.controller\\.|\\.application\\.result|\\.infrastructure\\.|\\.mapper\\.|\\.dataobject\\.|\\.api\\.' backend/community-app/src/main/java/com/nowcoder/community/wallet/domain backend/community-app/src/main/java/com/nowcoder/community/market/domain
```

Expected: no output except existing domain references that predate this work and are not introduced by the refactor. If output appears from new files, remove the dependency before continuing.

- [ ] **Step 5: Update wallet documentation wording**

In `docs/handbook/business-logic/wallet.md`, update the recharge flow bullets to say:

```markdown
3. `WalletRechargeApplicationService.complete(...)` loads or creates a `RechargeOrder` through the wallet domain model.
4. `RechargeOrder` owns replay compatibility and the `CREATED -> PAID` transition intent.
5. The application service ensures accounts and writes the RECHARGE ledger.
6. The repository applies the domain transition with a conditional status update.
```

In `docs/handbook/business-flows.md`, update the recharge section to use the same wording for replay and transition ownership.

- [ ] **Step 6: Update market documentation wording**

In `docs/handbook/business-logic/market.md`, update the order flow summary to say:

```markdown
`MarketOrder` owns order placement snapshots, replay compatibility, actor checks, status predicates, and transition intent. `MarketOrderApplicationService`, saga services, recovery, and dispute services keep transaction and repository orchestration, inventory writes, and wallet-action enqueue.
```

In `docs/handbook/business-logic/workflows/market-wallet.md`, update the market wallet workflow to say:

```markdown
Market-side order transitions are requested from `MarketOrder` and persisted through conditional repository updates. Wallet actions remain durable market-owned commands and are still executed by the wallet owner.
```

In `docs/handbook/reliability.md`, update the recovery section to say:

```markdown
Recovery maps pending order statuses to wallet action types through `MarketOrder.pendingWalletActionType()` so the order lifecycle has a single domain source for saga action expectations.
```

- [ ] **Step 7: Run final community-app test suite**

Run:

```bash
cd backend
mvn test -pl :community-app
```

Expected: PASS.

- [ ] **Step 8: Commit verification and docs**

Run:

```bash
git add docs/handbook/business-logic/wallet.md \
        docs/handbook/business-logic/market.md \
        docs/handbook/business-logic/workflows/market-wallet.md \
        docs/handbook/business-flows.md \
        docs/handbook/reliability.md
git commit -m "docs: describe wallet and market domain lifecycle ownership"
```

---

## Final Acceptance Checklist

- [ ] `RechargeOrder` owns creation, replay compatibility, paid predicate, and paid transition semantics.
- [ ] `WalletRechargeApplicationService` has no raw recharge status literals.
- [ ] `RechargeOrderRepository` can apply a `RechargeOrderTransition`.
- [ ] `MarketOrderStatus`, `MarketGoodsType`, `MarketDeliveryMode`, `MarketStockMode`, and `MarketListingStatus` preserve existing string codes.
- [ ] `MarketOrder` owns placement, replay compatibility, actor checks, status predicates, pending wallet action mapping, and transition intent.
- [ ] `MarketOrderApplicationService` no longer defines private order-status, goods-type, delivery-mode, or stock-mode constants for domain decisions.
- [ ] Saga, recovery, auto-confirm, and dispute services call `MarketOrder` or `MarketListing` behavior for lifecycle decisions.
- [ ] MyBatis XML conditional updates are still present for concurrent state changes.
- [ ] HTTP APIs, application result records, and database schema remain unchanged.
- [ ] Focused wallet and market tests pass.
- [ ] `mvn test -pl :community-app -Dtest='*ArchTest'` passes.
- [ ] `mvn test -pl :community-app` passes.
