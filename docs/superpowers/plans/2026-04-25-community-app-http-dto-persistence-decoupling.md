# Community App HTTP DTO Persistence Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple HTTP response DTOs from persistence entities across wallet, market, and content while keeping the existing HTTP JSON shape stable.

**Architecture:** Services return owner-domain application result/view models from `model` packages. Controllers remain the HTTP boundary and convert application models to response DTOs. DTO packages must not import persistence entities, mappers, or DAOs, and service packages must not return or depend on HTTP response DTOs.

**Tech Stack:** Spring Boot, Java records/classes, MyBatis entities/mappers, JUnit 5, Mockito, Spring MVC tests, ArchUnit, Maven.

---

## File Structure

Create application result/view models:

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/RechargeOrderResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/WithdrawOrderResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/TransferOrderResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketOrderResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketOrderDetailView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketShipmentView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketListingResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketListingDetailView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketAddressView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketInventoryUnitView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketDisputeResult.java`

Modify HTTP DTOs so they depend on application models, not entities:

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateRechargeResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateWithdrawResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateTransferResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderDetailResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingDetailResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketAddressResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketInventoryUnitResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketDisputeResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/dto/CommentResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/dto/UserRecentCommentResponse.java`

Modify services and controllers:

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/RechargeService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WithdrawService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/TransferService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketListingService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketAddressService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketInventoryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/AdminMarketApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/assembler/PostHttpResponseAssembler.java`

Modify tests:

- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/RechargeServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WithdrawServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/TransferServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/dto/UserRecentCommentResponseTest.java`

Add architecture test:

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`

Keep unrelated dirty files out of every commit. Before each commit, run `git diff --cached --name-only` and confirm it contains only the files for the current task.

---

### Task 1: Wallet Create Response Boundary

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/RechargeOrderResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/WithdrawOrderResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/TransferOrderResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateRechargeResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateWithdrawResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateTransferResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/RechargeService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WithdrawService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/TransferService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/RechargeServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WithdrawServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/TransferServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`

- [ ] **Step 1: Update wallet service tests to expect application result models**

In `RechargeServiceTest`, replace:

```java
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
```

with:

```java
import com.nowcoder.community.wallet.model.RechargeOrderResult;
```

Replace every `CreateRechargeResponse` local variable with `RechargeOrderResult`.

In `WithdrawServiceTest`, replace:

```java
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
```

with:

```java
import com.nowcoder.community.wallet.model.WithdrawOrderResult;
```

Replace every `CreateWithdrawResponse` local variable with `WithdrawOrderResult`.

In `TransferServiceTest`, replace:

```java
import com.nowcoder.community.wallet.dto.CreateTransferResponse;
```

with:

```java
import com.nowcoder.community.wallet.model.TransferOrderResult;
```

Then replace:

```java
CreateTransferResponse result
CreateTransferResponse first
CreateTransferResponse second
CreateTransferResponse response
CreateTransferResponse.class.getMethod("orderId")
private UUID readOrderId(CreateTransferResponse response)
```

with:

```java
TransferOrderResult result
TransferOrderResult first
TransferOrderResult second
TransferOrderResult response
TransferOrderResult.class.getMethod("orderId")
private UUID readOrderId(TransferOrderResult response)
```

- [ ] **Step 2: Run wallet service tests and verify the expected compile failure**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=RechargeServiceTest,WithdrawServiceTest,TransferServiceTest test
```

Expected: FAIL at compilation with missing `RechargeOrderResult`, `WithdrawOrderResult`, and `TransferOrderResult`.

- [ ] **Step 3: Add wallet result models**

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/RechargeOrderResult.java`:

```java
package com.nowcoder.community.wallet.model;

import com.nowcoder.community.wallet.entity.RechargeOrder;

import java.util.UUID;

public record RechargeOrderResult(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static RechargeOrderResult from(RechargeOrder order) {
        return new RechargeOrderResult(
                order.getOrderId(),
                order.getRequestId(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/WithdrawOrderResult.java`:

```java
package com.nowcoder.community.wallet.model;

import com.nowcoder.community.wallet.entity.WithdrawOrder;

import java.util.UUID;

public record WithdrawOrderResult(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static WithdrawOrderResult from(WithdrawOrder order) {
        return new WithdrawOrderResult(
                order.getOrderId(),
                order.getRequestId(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/TransferOrderResult.java`:

```java
package com.nowcoder.community.wallet.model;

import com.nowcoder.community.wallet.entity.TransferOrder;

import java.util.UUID;

public record TransferOrderResult(UUID orderId,
                                  String requestId,
                                  UUID fromUserId,
                                  UUID toUserId,
                                  long amount,
                                  String status) {

    public static TransferOrderResult from(TransferOrder order) {
        return new TransferOrderResult(
                order.getOrderId(),
                order.getRequestId(),
                order.getFromUserId(),
                order.getToUserId(),
                order.getAmount(),
                order.getStatus()
        );
    }
}
```

- [ ] **Step 4: Change wallet DTO factories to consume result models**

Replace `CreateRechargeResponse.java` with:

```java
package com.nowcoder.community.wallet.dto;

import com.nowcoder.community.wallet.model.RechargeOrderResult;

import java.util.UUID;

public record CreateRechargeResponse(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static CreateRechargeResponse from(RechargeOrderResult result) {
        return new CreateRechargeResponse(
                result.orderId(),
                result.requestId(),
                result.userId(),
                result.amount(),
                result.status()
        );
    }
}
```

Replace `CreateWithdrawResponse.java` with:

```java
package com.nowcoder.community.wallet.dto;

import com.nowcoder.community.wallet.model.WithdrawOrderResult;

import java.util.UUID;

public record CreateWithdrawResponse(UUID orderId, String requestId, UUID userId, long amount, String status) {

    public static CreateWithdrawResponse from(WithdrawOrderResult result) {
        return new CreateWithdrawResponse(
                result.orderId(),
                result.requestId(),
                result.userId(),
                result.amount(),
                result.status()
        );
    }
}
```

Replace `CreateTransferResponse.java` with:

```java
package com.nowcoder.community.wallet.dto;

import com.nowcoder.community.wallet.model.TransferOrderResult;

import java.util.UUID;

public record CreateTransferResponse(UUID orderId,
                                     String requestId,
                                     UUID fromUserId,
                                     UUID toUserId,
                                     long amount,
                                     String status) {

    public static CreateTransferResponse from(TransferOrderResult result) {
        return new CreateTransferResponse(
                result.orderId(),
                result.requestId(),
                result.fromUserId(),
                result.toUserId(),
                result.amount(),
                result.status()
        );
    }
}
```

- [ ] **Step 5: Change wallet services to return result models**

In `RechargeService.java`, replace:

```java
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
```

with:

```java
import com.nowcoder.community.wallet.model.RechargeOrderResult;
```

Then replace:

```java
public CreateRechargeResponse complete(String requestId, UUID userId, long amount)
CreateRechargeResponse.from(existing)
CreateRechargeResponse.from(order)
CreateRechargeResponse.from(requireOrder(requestId))
```

with:

```java
public RechargeOrderResult complete(String requestId, UUID userId, long amount)
RechargeOrderResult.from(existing)
RechargeOrderResult.from(order)
RechargeOrderResult.from(requireOrder(requestId))
```

In `WithdrawService.java`, replace:

```java
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
```

with:

```java
import com.nowcoder.community.wallet.model.WithdrawOrderResult;
```

Then replace:

```java
public CreateWithdrawResponse request(String requestId, UUID userId, long amount)
CreateWithdrawResponse.from(order)
CreateWithdrawResponse.from(requireOrder(requestId))
```

with:

```java
public WithdrawOrderResult request(String requestId, UUID userId, long amount)
WithdrawOrderResult.from(order)
WithdrawOrderResult.from(requireOrder(requestId))
```

In `TransferService.java`, replace:

```java
import com.nowcoder.community.wallet.dto.CreateTransferResponse;
```

with:

```java
import com.nowcoder.community.wallet.model.TransferOrderResult;
```

Then replace:

```java
public CreateTransferResponse create(String requestId, UUID fromUserId, UUID toUserId, long amount)
CreateTransferResponse.from(existing)
CreateTransferResponse.from(order)
```

with:

```java
public TransferOrderResult create(String requestId, UUID fromUserId, UUID toUserId, long amount)
TransferOrderResult.from(existing)
TransferOrderResult.from(order)
```

- [ ] **Step 6: Change WalletApplicationService to return result models**

Replace the wallet response imports in `WalletApplicationService.java` with:

```java
import com.nowcoder.community.wallet.model.RechargeOrderResult;
import com.nowcoder.community.wallet.model.TransferOrderResult;
import com.nowcoder.community.wallet.model.WithdrawOrderResult;
```

Then replace the three method signatures:

```java
public RechargeOrderResult recharge(UUID userId, CreateRechargeRequest request)
public WithdrawOrderResult withdraw(UUID userId, CreateWithdrawRequest request)
public TransferOrderResult transfer(UUID fromUserId, CreateTransferRequest request)
```

The method bodies stay the same, because the underlying services now return result models.

- [ ] **Step 7: Convert wallet results at the HTTP boundary**

In `WalletController.java`, change the three endpoints to:

```java
@PostMapping("/recharges")
public Result<CreateRechargeResponse> recharge(Authentication authentication, @RequestBody @Valid CreateRechargeRequest request) {
    UUID userId = CurrentUser.requireUserUuid(authentication);
    return Result.ok(CreateRechargeResponse.from(walletApplicationService.recharge(userId, request)));
}

@PostMapping("/withdrawals")
public Result<CreateWithdrawResponse> withdraw(Authentication authentication, @RequestBody @Valid CreateWithdrawRequest request) {
    UUID userId = CurrentUser.requireUserUuid(authentication);
    return Result.ok(CreateWithdrawResponse.from(walletApplicationService.withdraw(userId, request)));
}

@PostMapping("/transfers")
public Result<CreateTransferResponse> transfer(Authentication authentication, @RequestBody @Valid CreateTransferRequest request) {
    UUID fromUserId = CurrentUser.requireUserUuid(authentication);
    return Result.ok(CreateTransferResponse.from(walletApplicationService.transfer(fromUserId, request)));
}
```

- [ ] **Step 8: Update WalletControllerTest mocks to return result models**

In `WalletControllerTest.java`, add:

```java
import com.nowcoder.community.wallet.model.RechargeOrderResult;
import com.nowcoder.community.wallet.model.TransferOrderResult;
import com.nowcoder.community.wallet.model.WithdrawOrderResult;
```

Replace mock returns:

```java
new CreateRechargeResponse(orderId, "recharge:req-api-1", userId, 1200L, "PAID")
new CreateWithdrawResponse(orderId, "withdraw:req-api-1", userId, 500L, "SUCCEEDED")
new CreateTransferResponse(orderId, requestId, fromUserId, toUserId, amount, status)
```

with:

```java
new RechargeOrderResult(orderId, "recharge:req-api-1", userId, 1200L, "PAID")
new WithdrawOrderResult(orderId, "withdraw:req-api-1", userId, 500L, "SUCCEEDED")
new TransferOrderResult(orderId, requestId, fromUserId, toUserId, amount, status)
```

Change the helper signature:

```java
private TransferOrderResult transferResponse(UUID orderId,
                                             String requestId,
                                             UUID fromUserId,
                                             UUID toUserId,
                                             long amount,
                                             String status) {
    return new TransferOrderResult(orderId, requestId, fromUserId, toUserId, amount, status);
}
```

- [ ] **Step 9: Run wallet tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=WalletControllerTest,RechargeServiceTest,WithdrawServiceTest,TransferServiceTest test
```

Expected: PASS.

- [ ] **Step 10: Commit wallet boundary changes**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/model/RechargeOrderResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/model/WithdrawOrderResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/model/TransferOrderResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateRechargeResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateWithdrawResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateTransferResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/service/RechargeService.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WithdrawService.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/service/TransferService.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/service/RechargeServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WithdrawServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/service/TransferServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java
git diff --cached --name-only
git commit -m "refactor: decouple wallet responses from entities"
```

Expected staged files: only the wallet files listed above.

---

### Task 2: Market Order Response Boundary

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketOrderResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketOrderDetailView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketShipmentView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderDetailResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`

- [ ] **Step 1: Update market order service tests to expect MarketOrderResult**

In `MarketOrderServiceTest.java` and `MarketOrderServiceUnitTest.java`, replace:

```java
import com.nowcoder.community.market.dto.MarketOrderResponse;
```

with:

```java
import com.nowcoder.community.market.model.MarketOrderResult;
```

Replace local variables and futures:

```java
MarketOrderResponse response
MarketOrderResponse delivered
MarketOrderResponse confirmed
MarketOrderResponse shipped
MarketOrderResponse cancelled
MarketOrderResponse first
MarketOrderResponse second
Future<MarketOrderResponse>
```

with:

```java
MarketOrderResult response
MarketOrderResult delivered
MarketOrderResult confirmed
MarketOrderResult shipped
MarketOrderResult cancelled
MarketOrderResult first
MarketOrderResult second
Future<MarketOrderResult>
```

Replace AssertJ method references:

```java
MarketOrderResponse::goodsType
MarketOrderResponse::orderId
```

with:

```java
MarketOrderResult::goodsType
MarketOrderResult::orderId
```

- [ ] **Step 2: Run market order tests and verify the expected compile failure**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=MarketOrderServiceTest,MarketOrderServiceUnitTest test
```

Expected: FAIL at compilation with missing `MarketOrderResult`.

- [ ] **Step 3: Add market order models**

Create `MarketOrderResult.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketOrder;

import java.util.Date;
import java.util.UUID;

public record MarketOrderResult(
        UUID orderId,
        String requestId,
        UUID listingId,
        String goodsType,
        UUID sellerUserId,
        UUID buyerUserId,
        int quantity,
        long unitPriceSnapshot,
        long totalAmount,
        String deliveryModeSnapshot,
        String listingTitleSnapshot,
        String status,
        UUID escrowTxnId,
        UUID releaseTxnId,
        UUID refundTxnId,
        Date autoConfirmAt,
        Date createTime,
        Date updateTime
) {

    public static MarketOrderResult from(MarketOrder order) {
        return new MarketOrderResult(
                order.getOrderId(),
                order.getRequestId(),
                order.getListingId(),
                order.getGoodsType(),
                order.getSellerUserId(),
                order.getBuyerUserId(),
                order.getQuantity(),
                order.getUnitPriceSnapshot(),
                order.getTotalAmount(),
                order.getDeliveryModeSnapshot(),
                order.getListingTitleSnapshot(),
                order.getStatus(),
                order.getEscrowTxnId(),
                order.getReleaseTxnId(),
                order.getRefundTxnId(),
                order.getAutoConfirmAt(),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }
}
```

Create `MarketShipmentView.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketShipment;

import java.util.Date;
import java.util.UUID;

public record MarketShipmentView(
        UUID shipmentId,
        UUID orderId,
        UUID sellerUserId,
        String carrierName,
        String trackingNo,
        String shippingRemark,
        Date shippedAt,
        Date createTime,
        Date updateTime
) {

    public static MarketShipmentView from(MarketShipment shipment) {
        if (shipment == null) {
            return null;
        }
        return new MarketShipmentView(
                shipment.getShipmentId(),
                shipment.getOrderId(),
                shipment.getSellerUserId(),
                shipment.getCarrierName(),
                shipment.getTrackingNo(),
                shipment.getShippingRemark(),
                shipment.getShippedAt(),
                shipment.getCreateTime(),
                shipment.getUpdateTime()
        );
    }
}
```

Create `MarketOrderDetailView.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.entity.MarketShipment;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public record MarketOrderDetailView(
        UUID orderId,
        String requestId,
        UUID listingId,
        String goodsType,
        UUID sellerUserId,
        UUID buyerUserId,
        int quantity,
        long unitPriceSnapshot,
        long totalAmount,
        String deliveryModeSnapshot,
        String listingTitleSnapshot,
        String status,
        UUID escrowTxnId,
        UUID releaseTxnId,
        UUID refundTxnId,
        Date autoConfirmAt,
        String receiverNameSnapshot,
        String receiverPhoneSnapshot,
        String provinceSnapshot,
        String citySnapshot,
        String districtSnapshot,
        String detailAddressSnapshot,
        String postalCodeSnapshot,
        List<String> deliveryContents,
        MarketShipmentView shipment,
        Date createTime,
        Date updateTime
) {

    public static MarketOrderDetailView from(MarketOrder order,
                                             List<String> deliveryContents,
                                             MarketShipment shipment) {
        return new MarketOrderDetailView(
                order.getOrderId(),
                order.getRequestId(),
                order.getListingId(),
                order.getGoodsType(),
                order.getSellerUserId(),
                order.getBuyerUserId(),
                order.getQuantity(),
                order.getUnitPriceSnapshot(),
                order.getTotalAmount(),
                order.getDeliveryModeSnapshot(),
                order.getListingTitleSnapshot(),
                order.getStatus(),
                order.getEscrowTxnId(),
                order.getReleaseTxnId(),
                order.getRefundTxnId(),
                order.getAutoConfirmAt(),
                order.getReceiverNameSnapshot(),
                order.getReceiverPhoneSnapshot(),
                order.getProvinceSnapshot(),
                order.getCitySnapshot(),
                order.getDistrictSnapshot(),
                order.getDetailAddressSnapshot(),
                order.getPostalCodeSnapshot(),
                deliveryContents == null ? List.of() : List.copyOf(deliveryContents),
                MarketShipmentView.from(shipment),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }
}
```

- [ ] **Step 4: Change market order DTOs to consume order models**

In `MarketOrderResponse.java`, replace the entity import with:

```java
import com.nowcoder.community.market.model.MarketOrderResult;
```

Change the factory signature and body to:

```java
public static MarketOrderResponse from(MarketOrderResult order) {
    return new MarketOrderResponse(
            order.orderId(),
            order.requestId(),
            order.listingId(),
            order.goodsType(),
            order.sellerUserId(),
            order.buyerUserId(),
            order.quantity(),
            order.unitPriceSnapshot(),
            order.totalAmount(),
            order.deliveryModeSnapshot(),
            order.listingTitleSnapshot(),
            order.status(),
            order.escrowTxnId(),
            order.releaseTxnId(),
            order.refundTxnId(),
            order.autoConfirmAt(),
            order.createTime(),
            order.updateTime()
    );
}
```

In `MarketOrderDetailResponse.java`, replace entity imports with:

```java
import com.nowcoder.community.market.model.MarketOrderDetailView;
import com.nowcoder.community.market.model.MarketShipmentView;
```

Change the outer factory to:

```java
public static MarketOrderDetailResponse from(MarketOrderDetailView order) {
    return new MarketOrderDetailResponse(
            order.orderId(),
            order.requestId(),
            order.listingId(),
            order.goodsType(),
            order.sellerUserId(),
            order.buyerUserId(),
            order.quantity(),
            order.unitPriceSnapshot(),
            order.totalAmount(),
            order.deliveryModeSnapshot(),
            order.listingTitleSnapshot(),
            order.status(),
            order.escrowTxnId(),
            order.releaseTxnId(),
            order.refundTxnId(),
            order.autoConfirmAt(),
            order.receiverNameSnapshot(),
            order.receiverPhoneSnapshot(),
            order.provinceSnapshot(),
            order.citySnapshot(),
            order.districtSnapshot(),
            order.detailAddressSnapshot(),
            order.postalCodeSnapshot(),
            order.deliveryContents(),
            ShipmentView.from(order.shipment()),
            order.createTime(),
            order.updateTime()
    );
}
```

Change the nested shipment factory to:

```java
public static ShipmentView from(MarketShipmentView shipment) {
    if (shipment == null) {
        return null;
    }
    return new ShipmentView(
            shipment.shipmentId(),
            shipment.orderId(),
            shipment.sellerUserId(),
            shipment.carrierName(),
            shipment.trackingNo(),
            shipment.shippingRemark(),
            shipment.shippedAt(),
            shipment.createTime(),
            shipment.updateTime()
    );
}
```

- [ ] **Step 5: Change MarketOrderService and MarketQueryService return types**

In `MarketOrderService.java`, replace:

```java
import com.nowcoder.community.market.dto.MarketOrderResponse;
```

with:

```java
import com.nowcoder.community.market.model.MarketOrderResult;
```

Replace all public return types:

```java
public MarketOrderResponse createOrder(...)
public MarketOrderResponse deliverVirtualOrder(...)
public MarketOrderResponse confirmOrder(...)
public MarketOrderResponse shipPhysicalOrder(...)
public MarketOrderResponse cancelOrder(...)
```

with:

```java
public MarketOrderResult createOrder(...)
public MarketOrderResult deliverVirtualOrder(...)
public MarketOrderResult confirmOrder(...)
public MarketOrderResult shipPhysicalOrder(...)
public MarketOrderResult cancelOrder(...)
```

Replace all `MarketOrderResponse.from(...)` calls with `MarketOrderResult.from(...)`.

In `MarketQueryService.java`, replace order DTO imports with:

```java
import com.nowcoder.community.market.model.MarketOrderDetailView;
import com.nowcoder.community.market.model.MarketOrderResult;
```

Change order query methods to:

```java
public List<MarketOrderResult> listBuyingOrders(UUID buyerUserId) {
    return marketOrderMapper.selectByBuyerUserId(buyerUserId).stream()
            .map(MarketOrderResult::from)
            .toList();
}

public List<MarketOrderResult> listSellingOrders(UUID sellerUserId) {
    return marketOrderMapper.selectBySellerUserId(sellerUserId).stream()
            .map(MarketOrderResult::from)
            .toList();
}

public MarketOrderDetailView getOrderDetail(UUID orderId, UUID actorUserId) {
    MarketOrder order = marketOrderMapper.selectById(orderId);
    if (order == null) {
        throw new BusinessException(NOT_FOUND, "market order not found: orderId=" + orderId);
    }
    if (!Objects.equals(order.getBuyerUserId(), actorUserId) && !Objects.equals(order.getSellerUserId(), actorUserId)) {
        throw new BusinessException(FORBIDDEN, "market order does not belong to actor: orderId=" + orderId);
    }
    List<String> deliveryContents = loadDeliveryContents(orderId, order.getGoodsType());
    return MarketOrderDetailView.from(order, deliveryContents, marketShipmentMapper.selectByOrderId(orderId));
}
```

- [ ] **Step 6: Change MarketApplicationService order methods to return models**

In `MarketApplicationService.java`, replace order response imports with:

```java
import com.nowcoder.community.market.model.MarketOrderDetailView;
import com.nowcoder.community.market.model.MarketOrderResult;
```

Change these signatures:

```java
public MarketOrderResult createOrder(UUID buyerUserId, CreateMarketOrderRequest request)
public List<MarketOrderResult> listBuyingOrders(UUID buyerUserId)
public List<MarketOrderResult> listSellingOrders(UUID sellerUserId)
public MarketOrderDetailView getOrderDetail(UUID orderId, UUID actorUserId)
public MarketOrderResult deliverOrder(UUID orderId, UUID sellerUserId, DeliverMarketOrderRequest request)
public MarketOrderResult shipOrder(UUID orderId, UUID sellerUserId, ShipMarketOrderRequest request)
public MarketOrderResult confirmOrder(UUID orderId, UUID buyerUserId)
public MarketOrderResult cancelOrder(UUID orderId, UUID buyerUserId)
```

The method bodies stay the same except for return types.

- [ ] **Step 7: Convert market order models in MarketController**

Add imports:

```java
import com.nowcoder.community.market.model.MarketOrderResult;
```

Add helper:

```java
private static List<MarketOrderResponse> toOrderResponses(List<MarketOrderResult> orders) {
    return orders.stream()
            .map(MarketOrderResponse::from)
            .toList();
}
```

Change order endpoints to:

```java
return Result.ok(MarketOrderResponse.from(marketApplicationService.createOrder(buyerUserId, request)));
return Result.ok(toOrderResponses(marketApplicationService.listBuyingOrders(buyerUserId)));
return Result.ok(toOrderResponses(marketApplicationService.listSellingOrders(sellerUserId)));
return Result.ok(MarketOrderDetailResponse.from(marketApplicationService.getOrderDetail(orderId, actorUserId)));
return Result.ok(MarketOrderResponse.from(marketApplicationService.deliverOrder(orderId, sellerUserId, request)));
return Result.ok(MarketOrderResponse.from(marketApplicationService.shipOrder(orderId, sellerUserId, request)));
return Result.ok(MarketOrderResponse.from(marketApplicationService.confirmOrder(orderId, buyerUserId)));
return Result.ok(MarketOrderResponse.from(marketApplicationService.cancelOrder(orderId, buyerUserId)));
```

- [ ] **Step 8: Update MarketControllerTest order mocks**

In `MarketControllerTest.java`, add:

```java
import com.nowcoder.community.market.model.MarketOrderDetailView;
import com.nowcoder.community.market.model.MarketOrderResult;
```

Replace `MarketOrderResponse buyingOrder = new MarketOrderResponse(...)` with:

```java
MarketOrderResult buyingOrder = new MarketOrderResult(
        buyingOrderId,
        "buying:req-1",
        buyingListingId,
        "VIRTUAL",
        sellerUserId,
        buyerUserId,
        1,
        1500L,
        1500L,
        "PRELOADED",
        "Netflix 卡密",
        "DELIVERED",
        buyingEscrowTxnId,
        null,
        null,
        now,
        now,
        now
);
```

Replace `MarketOrderResponse sellingOrder = new MarketOrderResponse(...)` with:

```java
MarketOrderResult sellingOrder = new MarketOrderResult(
        sellingOrderId,
        "selling:req-1",
        sellingListingId,
        "PHYSICAL",
        sellerUserId,
        anotherBuyerUserId,
        1,
        12_900L,
        12_900L,
        null,
        "二手键盘",
        "SHIPPED",
        sellingEscrowTxnId,
        null,
        null,
        now,
        now,
        now
);
```

Replace `MarketOrderDetailResponse detail = new MarketOrderDetailResponse(...)` with:

```java
MarketOrderDetailView detail = new MarketOrderDetailView(
        buyingOrderId,
        "buying:req-1",
        buyingListingId,
        "VIRTUAL",
        sellerUserId,
        buyerUserId,
        1,
        1500L,
        1500L,
        "PRELOADED",
        "Netflix 卡密",
        "DELIVERED",
        buyingEscrowTxnId,
        null,
        null,
        now,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of("CODE-001"),
        null,
        now,
        now
);
```

- [ ] **Step 9: Run market order tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=MarketControllerTest,MarketOrderServiceTest,MarketOrderServiceUnitTest test
```

Expected: PASS.

- [ ] **Step 10: Commit market order boundary changes**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketOrderResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketOrderDetailView.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketShipmentView.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderDetailResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java
git diff --cached --name-only
git commit -m "refactor: decouple market order responses from entities"
```

Expected staged files: only the market order files listed above.

---

### Task 3: Remaining Market Response Boundaries

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketListingResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketListingDetailView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketAddressView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketInventoryUnitView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketDisputeResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingDetailResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketAddressResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketInventoryUnitResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketDisputeResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketListingService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketAddressService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketInventoryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/AdminMarketApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`

- [ ] **Step 1: Add remaining market result/view models**

Create `MarketListingResult.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketListing;

import java.util.UUID;

public record MarketListingResult(
        UUID listingId,
        UUID sellerUserId,
        String goodsType,
        String title,
        String description,
        long unitPrice,
        String deliveryMode,
        String stockMode,
        int stockTotal,
        int stockAvailable,
        int minPurchaseQuantity,
        int maxPurchaseQuantity,
        String status
) {

    public static MarketListingResult from(MarketListing listing) {
        return new MarketListingResult(
                listing.getListingId(),
                listing.getSellerUserId(),
                listing.getGoodsType(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getUnitPrice(),
                listing.getDeliveryMode(),
                listing.getStockMode(),
                listing.getStockTotal(),
                listing.getStockAvailable(),
                listing.getMinPurchaseQuantity(),
                listing.getMaxPurchaseQuantity(),
                listing.getStatus()
        );
    }
}
```

Create `MarketListingDetailView.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketListing;

import java.util.Date;
import java.util.UUID;

public record MarketListingDetailView(
        UUID listingId,
        UUID sellerUserId,
        String goodsType,
        String title,
        String description,
        long unitPrice,
        String deliveryMode,
        String stockMode,
        int stockTotal,
        int stockAvailable,
        int minPurchaseQuantity,
        int maxPurchaseQuantity,
        String status,
        Date createTime,
        Date updateTime
) {

    public static MarketListingDetailView from(MarketListing listing) {
        return new MarketListingDetailView(
                listing.getListingId(),
                listing.getSellerUserId(),
                listing.getGoodsType(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getUnitPrice(),
                listing.getDeliveryMode(),
                listing.getStockMode(),
                listing.getStockTotal(),
                listing.getStockAvailable(),
                listing.getMinPurchaseQuantity(),
                listing.getMaxPurchaseQuantity(),
                listing.getStatus(),
                listing.getCreateTime(),
                listing.getUpdateTime()
        );
    }
}
```

Create `MarketAddressView.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketAddress;

import java.util.Date;
import java.util.UUID;

public record MarketAddressView(
        UUID addressId,
        UUID userId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode,
        boolean isDefault,
        String status,
        Date createTime,
        Date updateTime
) {

    public static MarketAddressView from(MarketAddress address) {
        return new MarketAddressView(
                address.getAddressId(),
                address.getUserId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getPostalCode(),
                address.isDefault(),
                address.getStatus(),
                address.getCreateTime(),
                address.getUpdateTime()
        );
    }
}
```

Create `MarketInventoryUnitView.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketInventoryUnit;

import java.util.Date;
import java.util.UUID;

public record MarketInventoryUnitView(
        UUID inventoryUnitId,
        UUID listingId,
        UUID sellerUserId,
        String payloadType,
        String payloadContent,
        String status,
        UUID reservedOrderId,
        Date deliveredAt,
        Date createTime
) {

    public static MarketInventoryUnitView from(MarketInventoryUnit unit) {
        return new MarketInventoryUnitView(
                unit.getInventoryUnitId(),
                unit.getListingId(),
                unit.getSellerUserId(),
                unit.getPayloadType(),
                unit.getPayloadContent(),
                unit.getStatus(),
                unit.getReservedOrderId(),
                unit.getDeliveredAt(),
                unit.getCreateTime()
        );
    }
}
```

Create `MarketDisputeResult.java`:

```java
package com.nowcoder.community.market.model;

import com.nowcoder.community.market.entity.MarketDispute;

import java.util.Date;
import java.util.UUID;

public record MarketDisputeResult(
        UUID disputeId,
        UUID orderId,
        String goodsType,
        UUID buyerUserId,
        UUID sellerUserId,
        String status,
        String reason,
        String buyerNote,
        String sellerNote,
        String resolutionType,
        UUID resolvedBy,
        Date resolvedAt,
        Date createTime,
        Date updateTime
) {

    public static MarketDisputeResult from(MarketDispute dispute) {
        return new MarketDisputeResult(
                dispute.getDisputeId(),
                dispute.getOrderId(),
                dispute.getGoodsType(),
                dispute.getBuyerUserId(),
                dispute.getSellerUserId(),
                dispute.getStatus(),
                dispute.getReason(),
                dispute.getBuyerNote(),
                dispute.getSellerNote(),
                dispute.getResolutionType(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                dispute.getCreateTime(),
                dispute.getUpdateTime()
        );
    }
}
```

- [ ] **Step 2: Change remaining market DTO imports and factories**

For each DTO, replace the entity import with the matching model import and change `getX()` calls to record accessors:

```java
// MarketListingResponse.java
import com.nowcoder.community.market.model.MarketListingResult;

public static MarketListingResponse from(MarketListingResult listing) {
    return new MarketListingResponse(
            listing.listingId(),
            listing.sellerUserId(),
            listing.goodsType(),
            listing.title(),
            listing.description(),
            listing.unitPrice(),
            listing.deliveryMode(),
            listing.stockMode(),
            listing.stockTotal(),
            listing.stockAvailable(),
            listing.minPurchaseQuantity(),
            listing.maxPurchaseQuantity(),
            listing.status()
    );
}
```

```java
// MarketListingDetailResponse.java
import com.nowcoder.community.market.model.MarketListingDetailView;

public static MarketListingDetailResponse from(MarketListingDetailView listing) {
    return new MarketListingDetailResponse(
            listing.listingId(),
            listing.sellerUserId(),
            listing.goodsType(),
            listing.title(),
            listing.description(),
            listing.unitPrice(),
            listing.deliveryMode(),
            listing.stockMode(),
            listing.stockTotal(),
            listing.stockAvailable(),
            listing.minPurchaseQuantity(),
            listing.maxPurchaseQuantity(),
            listing.status(),
            listing.createTime(),
            listing.updateTime()
    );
}
```

```java
// MarketAddressResponse.java
import com.nowcoder.community.market.model.MarketAddressView;

public static MarketAddressResponse from(MarketAddressView address) {
    return new MarketAddressResponse(
            address.addressId(),
            address.userId(),
            address.receiverName(),
            address.receiverPhone(),
            address.province(),
            address.city(),
            address.district(),
            address.detailAddress(),
            address.postalCode(),
            address.isDefault(),
            address.status(),
            address.createTime(),
            address.updateTime()
    );
}
```

```java
// MarketInventoryUnitResponse.java
import com.nowcoder.community.market.model.MarketInventoryUnitView;

public static MarketInventoryUnitResponse from(MarketInventoryUnitView unit) {
    return new MarketInventoryUnitResponse(
            unit.inventoryUnitId(),
            unit.listingId(),
            unit.sellerUserId(),
            unit.payloadType(),
            unit.payloadContent(),
            unit.status(),
            unit.reservedOrderId(),
            unit.deliveredAt(),
            unit.createTime()
    );
}
```

```java
// MarketDisputeResponse.java
import com.nowcoder.community.market.model.MarketDisputeResult;

public static MarketDisputeResponse from(MarketDisputeResult dispute) {
    return new MarketDisputeResponse(
            dispute.disputeId(),
            dispute.orderId(),
            dispute.goodsType(),
            dispute.buyerUserId(),
            dispute.sellerUserId(),
            dispute.status(),
            dispute.reason(),
            dispute.buyerNote(),
            dispute.sellerNote(),
            dispute.resolutionType(),
            dispute.resolvedBy(),
            dispute.resolvedAt(),
            dispute.createTime(),
            dispute.updateTime()
    );
}
```

- [ ] **Step 3: Change market services to return market models**

Apply these replacements:

```text
MarketListingService:
  MarketListingResponse -> MarketListingResult
  MarketListingResponse.from(...) -> MarketListingResult.from(...)

MarketAddressService:
  MarketAddressResponse -> MarketAddressView
  MarketAddressResponse.from(...) -> MarketAddressView.from(...)

MarketInventoryService:
  List<MarketInventoryUnitResponse> -> List<MarketInventoryUnitView>
  MarketInventoryUnitResponse::from -> MarketInventoryUnitView::from

MarketDisputeService:
  MarketDisputeResponse -> MarketDisputeResult
  List<MarketDisputeResponse> -> List<MarketDisputeResult>
  MarketDisputeResponse.from(...) -> MarketDisputeResult.from(...)

AdminMarketApplicationService:
  MarketDisputeResponse -> MarketDisputeResult
  List<MarketDisputeResponse> -> List<MarketDisputeResult>

MarketQueryService:
  MarketListingResponse -> MarketListingResult
  MarketListingDetailResponse -> MarketListingDetailView
  MarketListingResponse::from -> MarketListingResult::from
  MarketListingDetailResponse.from(...) -> MarketListingDetailView.from(...)

MarketApplicationService:
  MarketListingResponse -> MarketListingResult
  MarketListingDetailResponse -> MarketListingDetailView
  MarketInventoryUnitResponse -> MarketInventoryUnitView
  MarketAddressResponse -> MarketAddressView
  MarketDisputeResponse -> MarketDisputeResult
```

Use imports from `com.nowcoder.community.market.model.*`, and remove now-unused imports from `com.nowcoder.community.market.dto.*` in service files.

- [ ] **Step 4: Convert remaining market models in controllers**

In `MarketController.java`, add imports for:

```java
import com.nowcoder.community.market.model.MarketAddressView;
import com.nowcoder.community.market.model.MarketDisputeResult;
import com.nowcoder.community.market.model.MarketInventoryUnitView;
import com.nowcoder.community.market.model.MarketListingResult;
```

Add helper methods:

```java
private static List<MarketListingResponse> toListingResponses(List<MarketListingResult> listings) {
    return listings.stream().map(MarketListingResponse::from).toList();
}

private static List<MarketInventoryUnitResponse> toInventoryResponses(List<MarketInventoryUnitView> units) {
    return units.stream().map(MarketInventoryUnitResponse::from).toList();
}

private static List<MarketAddressResponse> toAddressResponses(List<MarketAddressView> addresses) {
    return addresses.stream().map(MarketAddressResponse::from).toList();
}
```

Change non-order endpoints to wrap models:

```java
return Result.ok(toListingResponses(marketApplicationService.listPublicListings()));
return Result.ok(MarketListingDetailResponse.from(marketApplicationService.getListingDetail(listingId)));
return Result.ok(toListingResponses(marketApplicationService.listSellerListings(sellerUserId)));
return Result.ok(MarketListingResponse.from(marketApplicationService.createListing(sellerUserId, request)));
return Result.ok(MarketListingResponse.from(marketApplicationService.updateListing(sellerUserId, listingId, request)));
return Result.ok(MarketListingResponse.from(marketApplicationService.pauseListing(sellerUserId, listingId)));
return Result.ok(MarketListingResponse.from(marketApplicationService.resumeListing(sellerUserId, listingId)));
return Result.ok(MarketListingResponse.from(marketApplicationService.closeListing(sellerUserId, listingId)));
return Result.ok(toInventoryResponses(marketApplicationService.listInventory(listingId, sellerUserId)));
return Result.ok(toAddressResponses(marketApplicationService.listAddresses(userId)));
return Result.ok(MarketAddressResponse.from(marketApplicationService.createAddress(userId, request)));
return Result.ok(MarketAddressResponse.from(marketApplicationService.updateAddress(userId, addressId, request)));
return Result.ok(MarketDisputeResponse.from(marketApplicationService.openDispute(orderId, buyerUserId, request)));
return Result.ok(MarketDisputeResponse.from(marketApplicationService.sellerAccept(disputeId, sellerUserId, request)));
return Result.ok(MarketDisputeResponse.from(marketApplicationService.sellerReject(disputeId, sellerUserId, request)));
```

In `AdminMarketController.java`, add:

```java
import com.nowcoder.community.market.model.MarketDisputeResult;
```

Add helper:

```java
private static List<MarketDisputeResponse> toDisputeResponses(List<MarketDisputeResult> disputes) {
    return disputes.stream().map(MarketDisputeResponse::from).toList();
}
```

Change methods to:

```java
return Result.ok(toDisputeResponses(adminMarketApplicationService.listOpenDisputes()));
return Result.ok(MarketDisputeResponse.from(adminMarketApplicationService.resolveRefund(disputeId, actorUserId, request.getNote())));
return Result.ok(MarketDisputeResponse.from(adminMarketApplicationService.resolveRelease(disputeId, actorUserId, request.getNote())));
```

- [ ] **Step 5: Update market controller tests to mock models**

In `MarketControllerTest.java`, add:

```java
import com.nowcoder.community.market.model.MarketAddressView;
import com.nowcoder.community.market.model.MarketListingDetailView;
import com.nowcoder.community.market.model.MarketListingResult;
```

Replace `MarketListingResponse` test objects with `MarketListingResult`, replace `MarketListingDetailResponse` test objects with `MarketListingDetailView`, and replace `MarketAddressResponse` test objects with `MarketAddressView`. Constructor argument lists are identical to the previous DTO constructors.

In `AdminMarketControllerTest.java`, replace:

```java
import com.nowcoder.community.market.dto.MarketDisputeResponse;
```

with:

```java
import com.nowcoder.community.market.model.MarketDisputeResult;
```

Replace both `new MarketDisputeResponse(...)` calls with `new MarketDisputeResult(...)`. Constructor arguments stay identical.

- [ ] **Step 6: Run market controller tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=MarketControllerTest,AdminMarketControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit remaining market boundary changes**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketListingResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketListingDetailView.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketAddressView.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketInventoryUnitView.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketDisputeResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingDetailResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketAddressResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketInventoryUnitResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketDisputeResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketListingService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketAddressService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketInventoryService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/AdminMarketApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java
git diff --cached --name-only
git commit -m "refactor: decouple market catalog responses from entities"
```

Expected staged files: only the remaining market files listed above.

---

### Task 4: Content Comment DTO Boundary

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/dto/CommentResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/dto/UserRecentCommentResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/assembler/PostHttpResponseAssembler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/dto/UserRecentCommentResponseTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`

- [ ] **Step 1: Change UserRecentCommentResponseTest to use RecentUserCommentView**

Replace imports:

```java
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
```

with:

```java
import com.nowcoder.community.content.api.model.RecentUserCommentView;
```

Replace the setup and factory call with:

```java
RecentUserCommentView view = new RecentUserCommentView(
        commentId,
        userId,
        2,
        entityId,
        targetId,
        postId,
        "<title>",
        "<reply>",
        new Date()
);

UserRecentCommentResponse response = UserRecentCommentResponse.from(view);
```

- [ ] **Step 2: Run the content DTO test and verify the expected compile failure**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UserRecentCommentResponseTest test
```

Expected: FAIL at compilation because `UserRecentCommentResponse.from(RecentUserCommentView)` does not exist yet.

- [ ] **Step 3: Change CommentResponse to depend on CommentView**

In `CommentResponse.java`, replace:

```java
import com.nowcoder.community.content.entity.Comment;

import java.util.function.Function;
```

with:

```java
import com.nowcoder.community.content.api.model.CommentView;
```

Replace both `from(Comment...)` methods with:

```java
public static CommentResponse from(CommentView view) {
    if (view == null) {
        return null;
    }
    CommentResponse response = new CommentResponse();
    response.id = view.id();
    response.userId = view.userId();
    response.entityType = view.entityType();
    response.entityId = view.entityId();
    response.targetId = view.targetId();
    response.content = view.content();
    response.createTime = view.createTime();
    response.updateTime = view.updateTime();
    response.editCount = view.editCount();
    return response;
}
```

- [ ] **Step 4: Change UserRecentCommentResponse to depend on RecentUserCommentView**

In `UserRecentCommentResponse.java`, replace:

```java
import com.nowcoder.community.content.entity.Comment;

import java.util.function.Function;
```

with:

```java
import com.nowcoder.community.content.api.model.RecentUserCommentView;
```

Replace both `from(Comment...)` methods with:

```java
public static UserRecentCommentResponse from(RecentUserCommentView view) {
    if (view == null) {
        return null;
    }
    UserRecentCommentResponse response = new UserRecentCommentResponse();
    response.setId(view.id());
    response.setUserId(view.userId());
    response.setEntityType(view.entityType());
    response.setEntityId(view.entityId());
    response.setTargetId(view.targetId());
    response.setPostId(view.postId());
    response.setPostTitle(view.postTitle());
    response.setContent(view.content());
    response.setCreateTime(view.createTime());
    return response;
}
```

- [ ] **Step 5: Use CommentResponse.from in PostHttpResponseAssembler**

In `PostHttpResponseAssembler.java`, change `toCommentResponse` to:

```java
public CommentResponse toCommentResponse(CommentView view) {
    return CommentResponse.from(view);
}
```

- [ ] **Step 6: Run focused content tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=UserRecentCommentResponseTest,PostControllerUnitTest test
```

Expected: PASS.

- [ ] **Step 7: Commit content DTO boundary changes**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/dto/CommentResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/dto/UserRecentCommentResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/assembler/PostHttpResponseAssembler.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/dto/UserRecentCommentResponseTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java
git diff --cached --name-only
git commit -m "refactor: decouple content comment responses from entities"
```

Expected staged files: only the content files listed above.

---

### Task 5: DTO Boundary Architecture Test

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`

- [ ] **Step 1: Add DtoBoundaryArchTest**

Create `DtoBoundaryArchTest.java`:

```java
package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DtoBoundaryArchTest {

    @ArchTest
    static final ArchRule dto_must_not_depend_on_entities =
            noClasses()
                    .that().resideInAnyPackage("..dto..")
                    .should().dependOnClassesThat().resideInAnyPackage("..entity..");

    @ArchTest
    static final ArchRule dto_must_not_depend_on_mappers_or_daos =
            noClasses()
                    .that().resideInAnyPackage("..dto..")
                    .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..dao..");

    @ArchTest
    static final ArchRule services_must_not_depend_on_http_response_dtos =
            classes()
                    .that().resideInAnyPackage("..service..")
                    .should(notDependOnHttpResponseDtos());

    private static ArchCondition<JavaClass> notDependOnHttpResponseDtos() {
        return new ArchCondition<>("not depend on HTTP response DTOs") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.getPackageName().contains(".dto")) {
                        continue;
                    }
                    if (!target.getSimpleName().endsWith("Response")) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
                }
            }
        };
    }
}
```

- [ ] **Step 2: Run architecture test**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DtoBoundaryArchTest test
```

Expected: PASS.

- [ ] **Step 3: Run acceptance grep checks**

Run:

```bash
cd /home/feng/code/project/community
rg '^import com\.nowcoder\.community\..*\.entity\.' backend/community-app/src/main/java/com/nowcoder/community/*/dto
rg '^import com\.nowcoder\.community\..*\.mapper\.' backend/community-app/src/main/java/com/nowcoder/community/*/dto
rg -n 'import com\.nowcoder\.community\..*\.dto\..*Response' backend/community-app/src/main/java/com/nowcoder/community/*/service
```

Expected: all three commands return no matches and exit with code 1.

- [ ] **Step 4: Commit architecture test**

Run:

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java
git diff --cached --name-only
git commit -m "test: enforce DTO persistence boundary"
```

Expected staged files: only `DtoBoundaryArchTest.java`.

---

### Task 6: Final Verification

**Files:**
- Verify all files changed in Tasks 1-5.
- No source edits in this task unless a verification failure identifies a specific fix.

- [ ] **Step 1: Run focused regression tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=WalletControllerTest,RechargeServiceTest,WithdrawServiceTest,TransferServiceTest,MarketControllerTest,AdminMarketControllerTest,MarketOrderServiceTest,MarketOrderServiceUnitTest,UserRecentCommentResponseTest,PostControllerUnitTest,DtoBoundaryArchTest test
```

Expected: PASS.

- [ ] **Step 2: Run controller boundary architecture tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=ControllerBoundaryArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

- [ ] **Step 3: Run final acceptance grep checks**

Run:

```bash
cd /home/feng/code/project/community
rg '^import com\.nowcoder\.community\..*\.entity\.' backend/community-app/src/main/java/com/nowcoder/community/*/dto
rg '^import com\.nowcoder\.community\..*\.mapper\.' backend/community-app/src/main/java/com/nowcoder/community/*/dto
rg -n 'import com\.nowcoder\.community\..*\.dto\..*Response' backend/community-app/src/main/java/com/nowcoder/community/*/service
```

Expected: all three commands return no matches and exit with code 1.

- [ ] **Step 4: Inspect remaining git status**

Run:

```bash
cd /home/feng/code/project/community
git status --short
```

Expected: only pre-existing unrelated dirty files remain. If files from this plan are still modified, either commit the intended fix with a focused message or restore only generated build artifacts.

- [ ] **Step 5: Record verification result**

Add a short note to the final implementation summary:

```text
Verification:
- mvn -pl community-app -Dtest=WalletControllerTest,RechargeServiceTest,WithdrawServiceTest,TransferServiceTest,MarketControllerTest,AdminMarketControllerTest,MarketOrderServiceTest,MarketOrderServiceUnitTest,UserRecentCommentResponseTest,PostControllerUnitTest,DtoBoundaryArchTest test
- mvn -pl community-app -Dtest=ControllerBoundaryArchTest,DtoBoundaryArchTest test
- DTO/entity grep checks returned no matches
```
