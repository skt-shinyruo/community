# Wallet And Market DDD Tactical Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `wallet` and `market` from legacy `service`/`entity`/`mapper` business surfaces into strict DDD Tactical Layering.

**Architecture:** Controllers remain HTTP adapters and call only same-domain `application.*ApplicationService`. Wallet and market business rules move into `domain.service`, persistence contracts move into `domain.repository`, MyBatis row objects and mappers move into `infrastructure.persistence`, and foreign-domain synchronous contracts are implemented only by `service.*ApiAdapter` classes. Market jobs call market application services, and market-to-wallet collaboration goes through `wallet.api.action`.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, Redis/idempotency, ArchUnit, JUnit 5, Mockito, Maven.

---

## Scope

This plan covers these remaining strict-DDD slices:

- `wallet`
- `market`

It intentionally does not change already migrated `content`, `user`, or `social` code except where tests or architecture rules reference the new wallet/market surfaces.

---

## Target Package Shape

```text
com.nowcoder.community.wallet
  controller
    dto
  application
    command
    result
  domain
    model
    repository
    service
  infrastructure
    persistence
      mapper
      dataobject
  api
    action
    query
    model
  service              # only Wallet*ApiAdapter classes after migration

com.nowcoder.community.market
  controller
    dto
  application
    command
    result
  domain
    model
    repository
    service
  infrastructure
    persistence
      mapper
      dataobject
  job                  # local jobs call same-domain application only
  api
    action
    model
  service              # only Market*ApiAdapter classes after migration
```

---

## File Structure Map

### Wallet Controller And DTOs

- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateRechargeRequest.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/CreateRechargeRequest.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateRechargeResponse.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/CreateRechargeResponse.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateWithdrawRequest.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/CreateWithdrawRequest.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateWithdrawResponse.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/CreateWithdrawResponse.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateTransferRequest.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/CreateTransferRequest.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateTransferResponse.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/CreateTransferResponse.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/WalletSummaryResponse.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/WalletSummaryResponse.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/AdminFreezeWalletRequest.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/AdminFreezeWalletRequest.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/AdminReverseTxnRequest.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/dto/AdminReverseTxnRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/AdminWalletController.java`

### Wallet Application

- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/AdminWalletApplicationService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/AdminWalletApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletMarketApplicationService.java`
- Create command records:
  - `wallet/application/command/CreateRechargeCommand.java`
  - `wallet/application/command/CreateWithdrawCommand.java`
  - `wallet/application/command/CreateTransferCommand.java`
  - `wallet/application/command/AdminFreezeWalletCommand.java`
  - `wallet/application/command/AdminReverseTxnCommand.java`
  - `wallet/application/command/WalletRewardCommand.java`
  - `wallet/application/command/WalletMarketTxnCommand.java`
- Create result records:
  - `wallet/application/result/WalletSummaryResult.java`
  - `wallet/application/result/RechargeOrderResult.java`
  - `wallet/application/result/WithdrawOrderResult.java`
  - `wallet/application/result/TransferOrderResult.java`
  - `wallet/application/result/WalletTxnResult.java`
  - `wallet/application/result/WalletMarketTxnResult.java`

### Wallet Domain And Infrastructure

- Move model records from `wallet/model` to `wallet/domain/model`:
  - `WalletTxnType.java`
  - `WalletPosting.java`
  - `WalletLedgerCommand.java`
- Move entities to infrastructure row objects:
  - `wallet/entity/WalletAccount.java` -> `wallet/infrastructure/persistence/dataobject/WalletAccountDataObject.java`
  - `wallet/entity/WalletTxn.java` -> `wallet/infrastructure/persistence/dataobject/WalletTxnDataObject.java`
  - `wallet/entity/WalletEntry.java` -> `wallet/infrastructure/persistence/dataobject/WalletEntryDataObject.java`
  - `wallet/entity/RechargeOrder.java` -> `wallet/infrastructure/persistence/dataobject/RechargeOrderDataObject.java`
  - `wallet/entity/WithdrawOrder.java` -> `wallet/infrastructure/persistence/dataobject/WithdrawOrderDataObject.java`
  - `wallet/entity/TransferOrder.java` -> `wallet/infrastructure/persistence/dataobject/TransferOrderDataObject.java`
  - `wallet/entity/WalletAdminAction.java` -> `wallet/infrastructure/persistence/dataobject/WalletAdminActionDataObject.java`
- Move mappers to `wallet/infrastructure/persistence/mapper`:
  - `WalletAccountMapper`, `WalletTxnMapper`, `WalletEntryMapper`, `RechargeOrderMapper`, `WithdrawOrderMapper`, `TransferOrderMapper`, `WalletAdminActionMapper`
- Create repository interfaces:
  - `wallet/domain/repository/WalletAccountRepository.java`
  - `wallet/domain/repository/WalletLedgerRepository.java`
  - `wallet/domain/repository/RechargeOrderRepository.java`
  - `wallet/domain/repository/WithdrawOrderRepository.java`
  - `wallet/domain/repository/TransferOrderRepository.java`
  - `wallet/domain/repository/WalletAdminActionRepository.java`
- Create MyBatis implementations:
  - `wallet/infrastructure/persistence/MyBatisWalletAccountRepository.java`
  - `wallet/infrastructure/persistence/MyBatisWalletLedgerRepository.java`
  - `wallet/infrastructure/persistence/MyBatisRechargeOrderRepository.java`
  - `wallet/infrastructure/persistence/MyBatisWithdrawOrderRepository.java`
  - `wallet/infrastructure/persistence/MyBatisTransferOrderRepository.java`
  - `wallet/infrastructure/persistence/MyBatisWalletAdminActionRepository.java`
- Create domain services:
  - `wallet/domain/service/WalletAccountDomainService.java`
  - `wallet/domain/service/WalletLedgerDomainService.java`
  - `wallet/domain/service/WalletOrderDomainService.java`
  - `wallet/domain/service/WalletAdminDomainService.java`

### Wallet API Adapters

- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletAccountQueryApiAdapter.java`
- Rename/move: `wallet/service/WalletRewardService.java` -> `wallet/service/WalletRewardActionApiAdapter.java`
- Rename/move: `wallet/service/WalletMarketApplicationService.java` -> `wallet/service/WalletMarketActionApiAdapter.java`
- Delete after replacement:
  - `wallet/service/WalletAccountService.java`
  - `wallet/service/WalletLedgerService.java`
  - `wallet/service/WalletQueryService.java`
  - `wallet/service/RechargeService.java`
  - `wallet/service/WithdrawService.java`
  - `wallet/service/TransferService.java`
  - `wallet/service/AdminWalletOpsService.java`

### Market Controller And DTOs

- Move all files under `backend/community-app/src/main/java/com/nowcoder/community/market/dto` to `backend/community-app/src/main/java/com/nowcoder/community/market/controller/dto`.
- Modify:
  - `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java`

### Market Application

- Move:
  - `market/service/MarketApplicationService.java` -> `market/application/MarketApplicationService.java`
  - `market/service/AdminMarketApplicationService.java` -> `market/application/AdminMarketApplicationService.java`
  - `market/service/MarketWalletActionApplicationService.java` -> `market/application/MarketWalletActionApplicationService.java`
- Create:
  - `market/application/MarketOrderAutoConfirmApplicationService.java`
  - `market/application/MarketWalletActionProcessorApplicationService.java`
  - `market/application/MarketWalletActionRecoveryApplicationService.java`
- Create command records:
  - `CreateMarketListingCommand`, `UpdateMarketListingCommand`, `AddMarketInventoryBatchCommand`
  - `CreateMarketAddressCommand`, `UpdateMarketAddressCommand`
  - `CreateMarketOrderCommand`, `DeliverMarketOrderCommand`, `ShipMarketOrderCommand`
  - `CreateMarketDisputeCommand`, `SellerDisputeDecisionCommand`, `AdminResolveMarketDisputeCommand`
  - `MarketWalletActionCommand`
- Create result records matching existing market model names:
  - `MarketListingResult`, `MarketListingDetailResult`, `MarketInventoryUnitResult`
  - `MarketAddressResult`, `MarketOrderResult`, `MarketOrderDetailResult`
  - `MarketShipmentResult`, `MarketDisputeResult`, `MarketWalletActionResult`, `MarketWalletActionRecoveryResult`

### Market Domain And Infrastructure

- Move existing `market/model/*` result/view records to `market/application/result` unless they are pure value objects, then place them under `market/domain/model`.
- Move existing `market/model/MarketWalletActionType.java`, `MarketWalletActionStatus.java`, and `MarketWalletActionResultType.java` to `market/domain/model`.
- Move entities to infrastructure row objects:
  - `MarketListingDataObject`, `MarketInventoryUnitDataObject`, `MarketAddressDataObject`, `MarketOrderDataObject`
  - `MarketShipmentDataObject`, `MarketDeliveryDataObject`, `MarketDisputeDataObject`, `MarketWalletActionDataObject`
- Move mappers to `market/infrastructure/persistence/mapper`.
- Create repository interfaces:
  - `MarketListingRepository`, `MarketInventoryRepository`, `MarketAddressRepository`, `MarketOrderRepository`
  - `MarketShipmentRepository`, `MarketDeliveryRepository`, `MarketDisputeRepository`, `MarketWalletActionRepository`
- Create MyBatis repository implementations for each repository interface.
- Create domain services:
  - `MarketListingDomainService`
  - `MarketInventoryDomainService`
  - `MarketAddressDomainService`
  - `MarketOrderDomainService`
  - `MarketDisputeDomainService`
  - `MarketWalletActionDomainService`
  - `MarketOrderSagaDomainService`

### Market API Adapter And Jobs

- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderAutoConfirmActionApiAdapter.java`
- Modify jobs:
  - `market/job/MarketWalletActionProcessorHandler.java` calls `MarketWalletActionProcessorApplicationService`.
  - `market/job/MarketWalletActionRecoveryHandler.java` calls `MarketWalletActionRecoveryApplicationService`.
- Delete after replacement:
  - raw market services under `market/service` except `MarketOrderAutoConfirmActionApiAdapter`.

### Guardrails And Docs

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- Modify docs that mention wallet/market raw services:
  - `docs/ARCHITECTURE.md`
  - `docs/SYSTEM_DESIGN.md`
  - `docs/CORE_LOGIC.md`
  - `docs/business-logic`

---

## Task 1: Add Wallet And Market RED Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`

- [x] **Step 1: Add wallet and market retirement rules**

Add these `ArchRule` fields to `DddLayeringArchTest`:

```java
@ArchTest
static final ArchRule wallet_service_package_must_only_publish_foreign_api_adapters =
        classes()
                .that().resideInAnyPackage("..wallet.service..")
                .should().haveSimpleNameEndingWith("ApiAdapter")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_wallet_mapper_entity_model_packages_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..wallet.mapper..", "..wallet.entity..", "..wallet.model..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule wallet_controllers_must_not_depend_on_legacy_wallet_surfaces =
        noClasses()
                .that().resideInAnyPackage("..wallet.controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..wallet.service..",
                        "..wallet.infrastructure..",
                        "..wallet.domain..",
                        "..wallet.mapper..",
                        "..wallet.entity..",
                        "..wallet.model.."
                )
                .allowEmptyShould(true);

@ArchTest
static final ArchRule market_service_package_must_only_publish_foreign_api_adapters =
        classes()
                .that().resideInAnyPackage("..market.service..")
                .should().haveSimpleNameEndingWith("ApiAdapter")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_market_mapper_entity_model_packages_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..market.mapper..", "..market.entity..", "..market.model..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule market_controllers_and_jobs_must_not_depend_on_legacy_market_surfaces =
        noClasses()
                .that().resideInAnyPackage("..market.controller..", "..market.job..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..market.service..",
                        "..market.infrastructure..",
                        "..market.domain..",
                        "..market.mapper..",
                        "..market.entity..",
                        "..market.model.."
                )
                .allowEmptyShould(true);
```

- [x] **Step 2: Remove DTO boundary exceptions for wallet**

Remove these entries from `DtoBoundaryArchTest.LEGACY_SERVICE_RESPONSE_DTO_CALLERS`:

```java
"com.nowcoder.community.wallet.service.WalletApplicationService",
"com.nowcoder.community.wallet.service.WalletQueryService"
```

- [x] **Step 3: Run RED architecture check**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: FAIL because wallet and market still contain legacy `service`, `entity`, `mapper`, and `model` packages.

---

## Task 2: Establish Wallet Domain And Infrastructure

**Files:**
- Create/move wallet domain, infrastructure, command, and result files listed in the File Structure Map.
- Move tests from `wallet/service` and `wallet/mapper` into `wallet/application` and `wallet/infrastructure/persistence` as each production class moves.

- [x] **Step 1: Write wallet domain service tests**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/service/WalletAccountDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/service/WalletLedgerDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/service/WalletOrderDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/wallet/domain/service/WalletAdminDomainServiceTest.java
```

Minimum cases:

```java
@Test
void validatePositiveAmountShouldRejectZeroAndNegative() {
    WalletOrderDomainService service = new WalletOrderDomainService();

    assertThatThrownBy(() -> service.validatePositiveAmount(0))
            .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.validatePositiveAmount(-1))
            .isInstanceOf(BusinessException.class);
}

@Test
void transferShouldRejectSameUser() {
    WalletOrderDomainService service = new WalletOrderDomainService();
    UUID userId = uuid(1);

    assertThatThrownBy(() -> service.validateTransfer(userId, userId, 100))
            .isInstanceOf(BusinessException.class);
}

@Test
void activeWalletRequiredShouldRejectFrozenWallet() {
    WalletAccountDomainService service = new WalletAccountDomainService();

    assertThatThrownBy(() -> service.requireActive("FROZEN"))
            .isInstanceOf(BusinessException.class);
}
```

- [x] **Step 2: Run wallet domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.wallet.domain.service.WalletAccountDomainServiceTest,com.nowcoder.community.wallet.domain.service.WalletLedgerDomainServiceTest,com.nowcoder.community.wallet.domain.service.WalletOrderDomainServiceTest,com.nowcoder.community.wallet.domain.service.WalletAdminDomainServiceTest test
```

Expected: compile failure until wallet domain services exist.

- [x] **Step 3: Move wallet persistence behind repositories**

Move mappers and data objects using `git mv`. Keep mapper XML namespace updates aligned with the moved mapper packages.

Repository interface method set:

```java
public interface WalletAccountRepository {
    WalletAccount findByAccountId(UUID accountId);
    WalletAccount findByOwner(String ownerType, UUID ownerId, String accountType);
    int insert(WalletAccount account);
    int updateBalanceWithVersion(UUID accountId, long expectedVersion, long delta, String nextStatus);
}

public interface WalletLedgerRepository {
    WalletTxn findTxnByRequestId(String requestId);
    WalletTxn findTxnByBizId(String bizId);
    int insertTxn(WalletTxn txn);
    int insertEntry(WalletEntry entry);
    List<WalletEntry> findEntriesByTxnId(UUID txnId);
}
```

Create the remaining order/admin repository interfaces with one method for each currently used mapper call. Do not let application or domain classes import mapper types.

- [x] **Step 4: Implement wallet domain services**

Implement:

```java
public final class WalletAccountDomainService {
    public void requireActive(String status);
    public long deltaOf(String postingDirection, long amount);
}

public final class WalletLedgerDomainService {
    public void validateBalancedPostings(List<WalletPosting> postings);
    public WalletTxn newTxn(UUID txnId, String requestId, WalletTxnType txnType, String bizId, Date createTime);
}

public final class WalletOrderDomainService {
    public void validatePositiveAmount(long amount);
    public void validateTransfer(UUID fromUserId, UUID toUserId, long amount);
}

public final class WalletAdminDomainService {
    public void validateAdminAction(UUID actorUserId, String reason);
}
```

- [x] **Step 5: Run wallet foundation tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.wallet.domain.service.WalletAccountDomainServiceTest,com.nowcoder.community.wallet.domain.service.WalletLedgerDomainServiceTest,com.nowcoder.community.wallet.domain.service.WalletOrderDomainServiceTest,com.nowcoder.community.wallet.domain.service.WalletAdminDomainServiceTest,com.nowcoder.community.wallet.infrastructure.persistence.WalletAccountMapperPersistenceTest,com.nowcoder.community.wallet.infrastructure.persistence.WalletTxnMapperPersistenceTest,com.nowcoder.community.wallet.infrastructure.persistence.WalletEntryMapperPersistenceTest test
```

Expected: PASS after mapper XML namespaces and imports are corrected.

---

## Task 3: Move Wallet Application Entries And API Adapters

**Files:**
- Move wallet application and tests listed in the File Structure Map.
- Create wallet API adapters listed in the File Structure Map.

- [x] **Step 1: Move wallet service tests to application tests**

Move:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/wallet/service/RechargeServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceRechargeTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WithdrawServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceWithdrawTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/wallet/service/TransferServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletApplicationServiceTransferTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/wallet/service/AdminWalletOpsServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/wallet/application/AdminWalletApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletMarketApplicationServiceTest.java
```

Rewrite tests to construct command records:

```java
RechargeOrderResult result = service.recharge(
        new CreateRechargeCommand(userId, 100, "idem-1")
);

assertThat(result.amount()).isEqualTo(100);
```

- [x] **Step 2: Run wallet application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.wallet.application.WalletApplicationServiceRechargeTest,com.nowcoder.community.wallet.application.WalletApplicationServiceWithdrawTest,com.nowcoder.community.wallet.application.WalletApplicationServiceTransferTest,com.nowcoder.community.wallet.application.AdminWalletApplicationServiceTest,com.nowcoder.community.wallet.application.WalletMarketApplicationServiceTest test
```

Expected: compile failure until wallet application package and command/result records exist.

- [x] **Step 3: Implement wallet application services**

`WalletApplicationService` public methods:

```java
WalletSummaryResult summary(UUID userId);
RechargeOrderResult recharge(CreateRechargeCommand command);
WithdrawOrderResult withdraw(CreateWithdrawCommand command);
TransferOrderResult transfer(CreateTransferCommand command);
```

`AdminWalletApplicationService` public methods:

```java
void freezeWallet(AdminFreezeWalletCommand command);
void reverseTxn(AdminReverseTxnCommand command);
```

`WalletRewardApplicationService` public methods:

```java
void issue(WalletRewardCommand command);
void revoke(WalletRewardCommand command);
void applyDelta(WalletRewardCommand command);
```

`WalletMarketApplicationService` public methods:

```java
WalletMarketTxnResult escrowOrder(WalletMarketTxnCommand command);
WalletMarketTxnResult releaseOrder(WalletMarketTxnCommand command);
WalletMarketTxnResult refundOrder(WalletMarketTxnCommand command);
```

Application services own idempotency key normalization, transaction boundaries, repository calls, domain validation, and result assembly. They must not import `wallet.controller.dto`, `wallet.infrastructure`, `wallet.mapper`, or `wallet.entity`.

- [x] **Step 4: Implement wallet API adapters**

Create adapters:

```java
@Service
public class WalletAccountQueryApiAdapter implements WalletAccountQueryApi {
    private final WalletApplicationService walletApplicationService;
}

@Service
public class WalletRewardActionApiAdapter implements WalletRewardActionApi {
    private final WalletRewardApplicationService walletRewardApplicationService;
}

@Service
public class WalletMarketActionApiAdapter implements WalletMarketActionApi {
    private final WalletMarketApplicationService walletMarketApplicationService;
}
```

Adapters map application command/result objects to existing `wallet.api.model` records. Same-domain wallet code must not inject these adapters.

- [x] **Step 5: Move wallet controllers and DTO conversion**

Controllers import only:

```java
com.nowcoder.community.wallet.application.WalletApplicationService
com.nowcoder.community.wallet.application.AdminWalletApplicationService
com.nowcoder.community.wallet.application.command.*
com.nowcoder.community.wallet.application.result.*
com.nowcoder.community.wallet.controller.dto.*
```

Controllers convert request DTOs into command records and application results into response DTOs. `WalletApplicationService` must not return HTTP response DTOs.

- [x] **Step 6: Delete wallet raw service surfaces**

Delete old service classes after all references are gone:

```text
WalletAccountService
WalletLedgerService
WalletQueryService
RechargeService
WithdrawService
TransferService
AdminWalletOpsService
WalletRewardService
WalletMarketApplicationService
```

The remaining production classes in `wallet.service` must be exactly:

```text
WalletAccountQueryApiAdapter
WalletRewardActionApiAdapter
WalletMarketActionApiAdapter
```

- [x] **Step 7: Run wallet suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=WalletControllerTest,AdminWalletControllerTest,com.nowcoder.community.wallet.application.WalletApplicationServiceRechargeTest,com.nowcoder.community.wallet.application.WalletApplicationServiceWithdrawTest,com.nowcoder.community.wallet.application.WalletApplicationServiceTransferTest,com.nowcoder.community.wallet.application.AdminWalletApplicationServiceTest,com.nowcoder.community.wallet.application.WalletMarketApplicationServiceTest,DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

---

## Task 4: Establish Market Domain And Infrastructure

**Files:**
- Create/move market domain, infrastructure, command, and result files listed in the File Structure Map.
- Move tests from `market/service` and `market/mapper` into `market/application` and `market/infrastructure/persistence`.

- [x] **Step 1: Write market domain service tests**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/market/domain/service/MarketListingDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/market/domain/service/MarketOrderDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/market/domain/service/MarketDisputeDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/market/domain/service/MarketWalletActionDomainServiceTest.java
```

Minimum cases:

```java
@Test
void createOrderShouldRejectBuyingOwnListing() {
    MarketOrderDomainService service = new MarketOrderDomainService();
    UUID userId = uuid(1);

    assertThatThrownBy(() -> service.validateCreateOrder(userId, userId, 1))
            .isInstanceOf(BusinessException.class);
}

@Test
void listingShouldRejectNonPositivePriceAndStock() {
    MarketListingDomainService service = new MarketListingDomainService();

    assertThatThrownBy(() -> service.validateCreateListing(uuid(1), "name", 0, 1))
            .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.validateCreateListing(uuid(1), "name", 100, 0))
            .isInstanceOf(BusinessException.class);
}

@Test
void disputeShouldAllowOnlyOrderParticipants() {
    MarketDisputeDomainService service = new MarketDisputeDomainService();

    assertThatThrownBy(() -> service.validateBuyerCanOpen(uuid(1), uuid(2)))
            .isInstanceOf(BusinessException.class);
}
```

- [x] **Step 2: Run market domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.market.domain.service.MarketListingDomainServiceTest,com.nowcoder.community.market.domain.service.MarketOrderDomainServiceTest,com.nowcoder.community.market.domain.service.MarketDisputeDomainServiceTest,com.nowcoder.community.market.domain.service.MarketWalletActionDomainServiceTest test
```

Expected: compile failure until market domain services exist.

- [x] **Step 3: Move market persistence behind repositories**

Move all current `market/mapper/*` to `market/infrastructure/persistence/mapper` and all current `market/entity/*` to `market/infrastructure/persistence/dataobject/*DataObject.java`.

Each MyBatis repository must expose domain-oriented methods, for example:

```java
public interface MarketOrderRepository {
    MarketOrderDataObject findById(UUID orderId);
    List<MarketOrderDataObject> findByBuyer(UUID buyerUserId);
    List<MarketOrderDataObject> findBySeller(UUID sellerUserId);
    int insert(MarketOrderDataObject order);
    int updateStatus(UUID orderId, String expectedStatus, String nextStatus);
}
```

Application/domain code must use repository interfaces, never mapper interfaces.

- [x] **Step 4: Implement market domain services**

Move validation and state-transition rules from raw services into domain services:

```java
public final class MarketOrderDomainService {
    public void validateCreateOrder(UUID buyerUserId, UUID sellerUserId, int quantity);
    public void validateBuyerAction(UUID actorUserId, UUID buyerUserId);
    public void validateSellerAction(UUID actorUserId, UUID sellerUserId);
}

public final class MarketWalletActionDomainService {
    public String requestId(String actionType, UUID orderId);
    public void validateTerminalTransition(String currentStatus, String nextStatus);
}
```

- [x] **Step 5: Run market foundation tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.market.domain.service.MarketListingDomainServiceTest,com.nowcoder.community.market.domain.service.MarketOrderDomainServiceTest,com.nowcoder.community.market.domain.service.MarketDisputeDomainServiceTest,com.nowcoder.community.market.domain.service.MarketWalletActionDomainServiceTest,MarketPersistenceTest,MarketWalletActionMapperPersistenceTest test
```

Expected: PASS after mapper namespaces, repository adapters, and imports are corrected.

---

## Task 5: Move Market Application Entries, Jobs, And API Adapter

**Files:**
- Move market application and tests listed in the File Structure Map.
- Modify market jobs listed in the File Structure Map.

- [x] **Step 1: Move market service tests to application tests**

Move:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketListingServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketListingApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketAddressServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketAddressApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceUnitTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketDisputeServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketDisputeApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionApplicationServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionProcessorTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionRecoveryServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationServiceTest.java
```

Rewrite tests to call command records rather than HTTP DTOs or raw services.

- [x] **Step 2: Run market application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.market.application.MarketListingApplicationServiceTest,com.nowcoder.community.market.application.MarketAddressApplicationServiceTest,com.nowcoder.community.market.application.MarketOrderApplicationServiceTest,com.nowcoder.community.market.application.MarketDisputeApplicationServiceTest,com.nowcoder.community.market.application.MarketWalletActionApplicationServiceTest test
```

Expected: compile failure until market application package and command/result records exist.

- [x] **Step 3: Implement market application services**

`MarketApplicationService` public methods keep the current controller behavior but accept command records for writes:

```java
List<MarketListingResult> listPublicListings();
MarketListingDetailResult getListingDetail(UUID listingId);
MarketListingResult createListing(CreateMarketListingCommand command);
MarketListingResult updateListing(UpdateMarketListingCommand command);
void addInventory(AddMarketInventoryBatchCommand command);
MarketOrderResult createOrder(CreateMarketOrderCommand command);
MarketOrderResult deliverOrder(DeliverMarketOrderCommand command);
MarketOrderResult shipOrder(ShipMarketOrderCommand command);
MarketOrderResult confirmOrder(UUID orderId, UUID buyerUserId);
MarketOrderResult cancelOrder(UUID orderId, UUID buyerUserId);
MarketDisputeResult openDispute(CreateMarketDisputeCommand command);
MarketDisputeResult sellerAccept(SellerDisputeDecisionCommand command);
MarketDisputeResult sellerReject(SellerDisputeDecisionCommand command);
```

Application services own transaction boundaries, idempotency, repository calls, wallet API collaboration, and result assembly. They must not import `market.controller.dto`, `market.infrastructure`, `market.mapper`, or `market.entity`.

- [x] **Step 4: Implement market jobs through application services**

`MarketWalletActionProcessorHandler` injects only:

```java
private final MarketWalletActionProcessorApplicationService applicationService;
```

`MarketWalletActionRecoveryHandler` injects only:

```java
private final MarketWalletActionRecoveryApplicationService applicationService;
```

Jobs must not import `market.service`, `market.mapper`, `market.entity`, or `market.infrastructure`.

- [x] **Step 5: Implement market API adapter**

Create:

```java
@Service
public class MarketOrderAutoConfirmActionApiAdapter implements MarketOrderAutoConfirmActionApi {
    private final MarketOrderAutoConfirmApplicationService applicationService;

    @Override
    public MarketOrderAutoConfirmResult autoConfirmDueOrders() {
        return applicationService.autoConfirmDueOrders();
    }
}
```

- [x] **Step 6: Move market controllers and DTO conversion**

Controllers import only `market.application`, `market.application.command`, `market.application.result`, and `market.controller.dto`.

Write conversion helpers in controllers:

```java
private CreateMarketOrderCommand toCommand(UUID buyerUserId, CreateMarketOrderRequest request, String idempotencyKey) {
    return new CreateMarketOrderCommand(buyerUserId, request.getListingId(), request.getQuantity(), request.getAddressId(), idempotencyKey);
}
```

- [x] **Step 7: Delete market raw service surfaces**

Delete old raw service classes after all references are gone:

```text
MarketAddressService
MarketApplicationService
MarketDisputeService
MarketInventoryService
MarketListingService
MarketOrderAutoConfirmService
MarketOrderSagaService
MarketOrderService
MarketQueryService
MarketWalletActionApplicationService
MarketWalletActionProcessor
MarketWalletActionRecoveryService
AdminMarketApplicationService
```

The remaining production class in `market.service` must be exactly:

```text
MarketOrderAutoConfirmActionApiAdapter
```

- [x] **Step 8: Run market suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=MarketControllerTest,AdminMarketControllerTest,LegacyVirtualMarketRetirementTest,com.nowcoder.community.market.application.MarketListingApplicationServiceTest,com.nowcoder.community.market.application.MarketAddressApplicationServiceTest,com.nowcoder.community.market.application.MarketOrderApplicationServiceTest,com.nowcoder.community.market.application.MarketOrderApplicationServiceUnitTest,com.nowcoder.community.market.application.MarketDisputeApplicationServiceTest,com.nowcoder.community.market.application.MarketWalletActionApplicationServiceTest,com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationServiceTest,com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationServiceTest,DddLayeringArchTest,ControllerBoundaryArchTest test
```

Expected: PASS.

---

## Task 6: Docs, Scans, And Verification

**Files:**
- Modify docs listed in the Guardrails And Docs section.
- Verify only for final scans and Maven commands.

- [x] **Step 1: Scan for retired wallet and market surfaces**

Run:

```bash
cd /home/feng/code/project/community
rg -n "wallet\\.(service|entity|mapper|model)\\.|market\\.(service|entity|mapper|model)\\." backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: only `wallet.service.*ApiAdapter`, `market.service.*ApiAdapter`, and ArchUnit rule strings remain.

- [x] **Step 2: Update docs**

Update wallet/market sections in:

```text
docs/ARCHITECTURE.md
docs/SYSTEM_DESIGN.md
docs/CORE_LOGIC.md
docs/business-logic
```

Replace raw service wording with:

```text
Controller/Job -> ApplicationService -> DomainService/Repository -> Infrastructure
Market -> wallet.api.action.WalletMarketActionApi for synchronous wallet collaboration
Wallet service package -> foreign API adapters only
Market service package -> foreign API adapters only
```

- [x] **Step 3: Run focused wallet and market suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=WalletControllerTest,AdminWalletControllerTest,MarketControllerTest,AdminMarketControllerTest,LegacyVirtualMarketRetirementTest,com.nowcoder.community.wallet.application.WalletApplicationServiceRechargeTest,com.nowcoder.community.wallet.application.WalletApplicationServiceWithdrawTest,com.nowcoder.community.wallet.application.WalletApplicationServiceTransferTest,com.nowcoder.community.wallet.application.AdminWalletApplicationServiceTest,com.nowcoder.community.wallet.application.WalletMarketApplicationServiceTest,com.nowcoder.community.market.application.MarketListingApplicationServiceTest,com.nowcoder.community.market.application.MarketAddressApplicationServiceTest,com.nowcoder.community.market.application.MarketOrderApplicationServiceTest,com.nowcoder.community.market.application.MarketDisputeApplicationServiceTest,com.nowcoder.community.market.application.MarketWalletActionApplicationServiceTest test
```

Expected: PASS.

- [x] **Step 4: Run architecture suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest,ListenerBoundaryArchTest,DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

- [x] **Step 5: Run full backend verification**

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected: PASS with zero failures and zero errors.

---

## Self-Review

### Spec Coverage

- Wallet and market controllers call same-domain application services only: Tasks 3 and 5.
- Jobs call application services only: Task 5.
- Domain rules move to `domain.service`: Tasks 2 and 4.
- Mapper/dataobject access moves to `infrastructure.persistence`: Tasks 2 and 4.
- Foreign API collaboration remains under `api.*` and is implemented by adapters only: Tasks 3 and 5.
- Legacy raw `service`, `entity`, `mapper`, and `model` packages are retired or reduced to API adapters: Tasks 1, 3, 5, and 6.

### Placeholder Scan

No step uses placeholder markers. Every task lists exact file paths, concrete class names, exact test commands, and expected outcomes.

### Type Consistency

Command/result records are used by controllers and application services. Domain services and domain repositories use `domain.model` types and dataobject-free rule inputs. Infrastructure repositories map `infrastructure.persistence.dataobject` types to domain models and are the only classes that import MyBatis mapper types.
