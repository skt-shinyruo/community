# Community Open Wallet Phase 2 Virtual Market Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a dedicated C2C virtual goods marketplace that reuses the Phase 1 wallet ledger for escrow, release, and refund, while keeping the official reward shop unchanged.

**Architecture:** Add a new `market` backend domain for virtual listings, inventory, orders, deliveries, and disputes. Cross-domain money movement must go through wallet API interfaces rather than direct wallet service dependencies, and auto-release must run through a market action API invoked by an XXL Job handler. Frontend adds a separate virtual market surface alongside the existing official reward shop.

**Tech Stack:** Java 17, Spring Boot 3, Spring Transactions, MyBatis XML mappers, H2 test schema, XXL Job handlers, existing auth/security stack, wallet ledger APIs, Vue 3, Vue Router, Vitest, Maven

---

## File Structure Map

### Schema

- `deploy/mysql-init/010_schema.sql`
  Role: add Phase 2 virtual market tables to the production schema.
- `backend/community-app/src/test/resources/schema.sql`
  Role: mirror the Phase 2 virtual market tables in H2 tests.

### Wallet collaboration boundary

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/api/action/WalletMarketActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/api/model/WalletMarketTxnView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketActionService.java`
  Role: encapsulate escrow, release, and refund postings so `market` does not depend on foreign wallet services directly.

### Market domain persistence

- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualListing.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualInventoryUnit.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualOrder.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDelivery.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDispute.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualListingMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualInventoryUnitMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualOrderMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDeliveryMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDisputeMapper.java`
- `backend/community-app/src/main/resources/mapper/virtual_listing_mapper.xml`
- `backend/community-app/src/main/resources/mapper/virtual_inventory_unit_mapper.xml`
- `backend/community-app/src/main/resources/mapper/virtual_order_mapper.xml`
- `backend/community-app/src/main/resources/mapper/virtual_delivery_mapper.xml`
- `backend/community-app/src/main/resources/mapper/virtual_dispute_mapper.xml`
  Role: store listing, inventory, order, delivery, and dispute state separately.

### Market domain services and APIs

- `backend/community-app/src/main/java/com/nowcoder/community/market/api/action/VirtualOrderAutoReleaseActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/api/model/VirtualOrderAutoReleaseResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualMarketQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualListingService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualInventoryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualOrderService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualDisputeService.java`
  Role: implement publish, inventory reserve, order escrow, delivery, confirm, cancel, dispute, and auto-release logic.

### Market transport layer

- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualListingRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateVirtualListingRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AddVirtualInventoryBatchRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualOrderRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/DeliverVirtualOrderRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualDisputeRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/SellerDisputeDecisionRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AdminResolveVirtualDisputeRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualListingResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualListingDetailResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualInventoryUnitResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualOrderResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualOrderDetailResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualDisputeResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminVirtualMarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java`
  Role: expose public read, buyer, seller, and admin market APIs.

### Auto-release job integration

- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandler.java`
  Role: run 24-hour auto-release through a market action API, not through direct domain internals.

### Frontend

- `frontend/src/api/services/virtualMarketService.js`
- `frontend/src/views/VirtualMarketListView.vue`
- `frontend/src/views/VirtualMarketDetailView.vue`
- `frontend/src/views/VirtualMarketPublishView.vue`
- `frontend/src/views/VirtualMarketMyListingsView.vue`
- `frontend/src/views/VirtualMarketInventoryView.vue`
- `frontend/src/views/VirtualMarketBuyingOrdersView.vue`
- `frontend/src/views/VirtualMarketSellingOrdersView.vue`
- `frontend/src/views/VirtualMarketOrderDetailView.vue`
- `frontend/src/views/AdminVirtualDisputesView.vue`
- `frontend/src/views/virtualMarketState.js`
- `frontend/src/views/virtualMarketState.test.js`
- `frontend/src/router/index.js`
- `frontend/src/router/navigation.js`
- `frontend/src/router/index.test.js`
- `frontend/src/router/navigation.test.js`
- `frontend/src/styles/pages.css`
  Role: add the separate virtual market navigation and views without disturbing the official reward shop routes.

### Tests

- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketActionServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualMarketPersistenceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualListingServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualDisputeServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminVirtualMarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandlerTest.java`
- `frontend/src/views/virtualMarketState.test.js`
- `frontend/src/router/index.test.js`
- `frontend/src/router/navigation.test.js`
  Role: prove the market domain, wallet collaboration, job path, and frontend navigation all work.

---

### Task 1: Add Virtual Market Schema And Persistence Primitives

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualListing.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualInventoryUnit.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualOrder.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDelivery.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDispute.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualListingMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualInventoryUnitMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualOrderMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDeliveryMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDisputeMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/virtual_listing_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/virtual_inventory_unit_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/virtual_order_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/virtual_delivery_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/virtual_dispute_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualMarketPersistenceTest.java`

- [ ] **Step 1: Write the failing persistence test**

```java
@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class VirtualMarketPersistenceTest {

    @Autowired
    private VirtualListingMapper virtualListingMapper;

    @Autowired
    private VirtualInventoryUnitMapper virtualInventoryUnitMapper;

    @Test
    void insertPreloadedListingShouldPersistStockAndInventoryUnits() {
        VirtualListing listing = new VirtualListing();
        listing.setSellerUserId(7);
        listing.setTitle("Steam 兑换码");
        listing.setDescription("自动交付");
        listing.setUnitPrice(1999L);
        listing.setDeliveryMode("PRELOADED");
        listing.setStockMode("FINITE");
        listing.setStockTotal(2);
        listing.setStockAvailable(2);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(2);
        listing.setStatus("ACTIVE");

        virtualListingMapper.insert(listing);

        VirtualInventoryUnit first = new VirtualInventoryUnit();
        first.setListingId(listing.getListingId());
        first.setSellerUserId(7);
        first.setPayloadType("CODE");
        first.setPayloadContent("CODE-001");
        first.setStatus("AVAILABLE");
        virtualInventoryUnitMapper.insert(first);

        assertThat(listing.getListingId()).isPositive();
        assertThat(virtualInventoryUnitMapper.countAvailableByListingId(listing.getListingId())).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualMarketPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the market tables, entities, mappers, and XML files do not exist yet.

- [ ] **Step 3: Add schema, entities, and mapper primitives**

```sql
create table if not exists virtual_listing (
  listing_id bigint auto_increment primary key,
  seller_user_id int not null,
  title varchar(128) not null,
  description varchar(1000) not null,
  unit_price bigint not null,
  delivery_mode varchar(16) not null,
  stock_mode varchar(16) not null,
  stock_total int not null,
  stock_available int not null,
  min_purchase_quantity int not null,
  max_purchase_quantity int not null,
  status varchar(16) not null,
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp
);

create table if not exists virtual_inventory_unit (
  inventory_unit_id bigint auto_increment primary key,
  listing_id bigint not null,
  seller_user_id int not null,
  payload_type varchar(16) not null,
  payload_content varchar(4000) not null,
  status varchar(16) not null,
  reserved_order_id bigint default null,
  delivered_at timestamp null default null,
  create_time timestamp default current_timestamp
);
```

```java
@Repository
@Mapper
public interface VirtualInventoryUnitMapper {

    int insert(VirtualInventoryUnit unit);

    int countAvailableByListingId(@Param("listingId") long listingId);

    List<VirtualInventoryUnit> selectAvailableForUpdate(@Param("listingId") long listingId, @Param("limit") int limit);
}
```

```xml
<select id="countAvailableByListingId" resultType="int">
  select count(*)
  from virtual_inventory_unit
  where listing_id = #{listingId}
    and status = 'AVAILABLE'
</select>
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualMarketPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add deploy/mysql-init/010_schema.sql \
  backend/community-app/src/test/resources/schema.sql \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualListing.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualInventoryUnit.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualOrder.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDelivery.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDispute.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualListingMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualInventoryUnitMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualOrderMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDeliveryMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDisputeMapper.java \
  backend/community-app/src/main/resources/mapper/virtual_listing_mapper.xml \
  backend/community-app/src/main/resources/mapper/virtual_inventory_unit_mapper.xml \
  backend/community-app/src/main/resources/mapper/virtual_order_mapper.xml \
  backend/community-app/src/main/resources/mapper/virtual_delivery_mapper.xml \
  backend/community-app/src/main/resources/mapper/virtual_dispute_mapper.xml \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualMarketPersistenceTest.java
git commit -m "feat: add virtual market persistence primitives"
```

---

### Task 2: Add Wallet Market Action API And Escrow/Release/Refund Posting Service

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/api/action/WalletMarketActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/api/model/WalletMarketTxnView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketActionService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketActionServiceTest.java`

- [ ] **Step 1: Write the failing wallet market action test**

```java
@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class WalletMarketActionServiceTest {

    @Autowired
    private WalletMarketActionApi walletMarketActionApi;

    @Autowired
    private WalletAccountService walletAccountService;

    @Autowired
    private WalletRewardService walletRewardService;

    @BeforeEach
    void setUp() {
        walletRewardService.issue("reward:buyer:1", 1, 5000, "SeedBalance");
    }

    @Test
    void escrowReleaseAndRefundShouldPostOrderTransactions() {
        WalletMarketTxnView escrow = walletMarketActionApi.escrowOrder("order:1:escrow", 1, 2000, "virtual-order:1");

        assertThat(escrow.txnType()).isEqualTo("ORDER_ESCROW");
        assertThat(walletAccountService.balanceOfUser(1)).isEqualTo(3000);
    }
}
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `cd backend && mvn -pl community-app -am -Dtest=WalletMarketActionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the market wallet action API and service do not exist yet.

- [ ] **Step 3: Implement the wallet market collaboration boundary**

```java
public interface WalletMarketActionApi {

    WalletMarketTxnView escrowOrder(String requestId, int buyerUserId, long amount, String bizId);

    WalletMarketTxnView releaseOrder(String requestId, int sellerUserId, long amount, String bizId);

    WalletMarketTxnView refundOrder(String requestId, int buyerUserId, long amount, String bizId);
}
```

```java
@Service
public class WalletMarketActionService implements WalletMarketActionApi {

    private final WalletAccountService walletAccountService;
    private final WalletLedgerService walletLedgerService;

    @Transactional
    @Override
    public WalletMarketTxnView escrowOrder(String requestId, int buyerUserId, long amount, String bizId) {
        walletAccountService.requireUserWalletActive(buyerUserId);
        WalletTxnResult result = walletLedgerService.post(
                requestId,
                WalletTxnType.ORDER_ESCROW,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureUserWallet(buyerUserId), amount),
                        WalletPosting.credit(walletAccountService.ensureSystemAccount("ORDER_ESCROW"), amount)
                )
        );
        return new WalletMarketTxnView(result.txnId(), WalletTxnType.ORDER_ESCROW.name(), result.status(), amount, bizId);
    }
}
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `cd backend && mvn -pl community-app -am -Dtest=WalletMarketActionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/api/action/WalletMarketActionApi.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/api/model/WalletMarketTxnView.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketActionService.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketActionServiceTest.java
git commit -m "feat: add wallet market action api"
```

---

### Task 3: Implement Listing Publish, Edit, Pause, Resume, Close, And Inventory Management

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualListingService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualInventoryService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualMarketQueryService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualListingRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateVirtualListingRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AddVirtualInventoryBatchRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualListingResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualListingDetailResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualInventoryUnitResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualListingServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java`

- [ ] **Step 1: Write the failing listing publish and read tests**

```java
@Test
void publishPreloadedListingShouldCreateActiveListingWithInventory() {
    CreateVirtualListingRequest request = new CreateVirtualListingRequest();
    request.setTitle("Netflix 卡密");
    request.setDescription("自动交付");
    request.setUnitPrice(1500L);
    request.setDeliveryMode("PRELOADED");
    request.setStockMode("FINITE");
    request.setStockTotal(2);
    request.setMinPurchaseQuantity(1);
    request.setMaxPurchaseQuantity(2);

    AddVirtualInventoryBatchRequest inventory = new AddVirtualInventoryBatchRequest();
    inventory.setPayloadType("CODE");
    inventory.setPayloads(List.of("NFX-001", "NFX-002"));

    VirtualListingResponse response = virtualListingService.createListing(7, request, inventory);

    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.stockAvailable()).isEqualTo(2);
}
```

```java
mockMvc.perform(get("/api/market/virtual/listings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].title").value("Netflix 卡密"));
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualListingServiceTest,VirtualMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because listing services, DTOs, controller, and security rules do not exist yet.

- [ ] **Step 3: Implement listing and inventory flows**

```java
@Transactional
public VirtualListingResponse createListing(int sellerUserId, CreateVirtualListingRequest request, AddVirtualInventoryBatchRequest inventoryRequest) {
    VirtualListing listing = new VirtualListing();
    listing.setSellerUserId(sellerUserId);
    listing.setTitle(request.getTitle().trim());
    listing.setDescription(request.getDescription().trim());
    listing.setUnitPrice(request.getUnitPrice());
    listing.setDeliveryMode(request.getDeliveryMode());
    listing.setStockMode(request.getStockMode());
    listing.setStockTotal(request.getStockTotal());
    listing.setStockAvailable(request.getStockTotal());
    listing.setMinPurchaseQuantity(request.getMinPurchaseQuantity());
    listing.setMaxPurchaseQuantity(request.getMaxPurchaseQuantity());
    listing.setStatus("ACTIVE");
    virtualListingMapper.insert(listing);

    if ("PRELOADED".equals(request.getDeliveryMode())) {
        inventoryService.appendInventory(listing.getListingId(), sellerUserId, inventoryRequest);
    }
    return VirtualListingResponse.from(listing);
}
```

```java
@PostMapping("/listings")
public Result<VirtualListingResponse> createListing(Authentication authentication, @RequestBody @Valid CreateVirtualListingRequest request) {
    int sellerUserId = CurrentUser.requireUserId(authentication);
    return Result.ok(virtualListingService.createListing(sellerUserId, request, request.getInventory()));
}
```

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualListingServiceTest,VirtualMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualListingService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualInventoryService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualMarketQueryService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualListingRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateVirtualListingRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/AddVirtualInventoryBatchRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualListingResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualListingDetailResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualInventoryUnitResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualListingServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java
git commit -m "feat: add virtual market listing flows"
```

---

### Task 4: Implement Order Creation, Order Query APIs, And Preloaded Auto-Delivery

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualOrderService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualOrderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualOrderResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualOrderDetailResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualMarketQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualOrderMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDeliveryMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/virtual_order_mapper.xml`
- Modify: `backend/community-app/src/main/resources/mapper/virtual_delivery_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java`

- [ ] **Step 1: Write the failing preloaded order and order query tests**

```java
@Test
void createPreloadedOrderShouldEscrowAndAutoDeliverReservedInventory() {
    long listingId = seedPreloadedListing(7, List.of("CODE-001", "CODE-002"));

    VirtualOrderResponse response = virtualOrderService.createOrder("market-order:req-1", 9, listingId, 2);

    assertThat(response.status()).isEqualTo("DELIVERED");
    assertThat(response.totalAmount()).isEqualTo(3998L);
    assertThat(walletTxnCount("market-order:req-1:escrow")).isEqualTo(1);
    assertThat(deliveryContentOf(response.orderId())).contains("CODE-001", "CODE-002");
}
```

```java
@Test
void buyerAndSellerOrderQueriesShouldReturnSnapshotsAndDeliveryRecords() {
    long orderId = seedDeliveredPreloadedOrder(7, 9, List.of("CODE-001"));

    assertThat(virtualMarketQueryService.listBuyingOrders(9))
            .extracting(VirtualOrderResponse::orderId)
            .contains(orderId);
    assertThat(virtualMarketQueryService.listSellingOrders(7))
            .extracting(VirtualOrderResponse::orderId)
            .contains(orderId);
    assertThat(virtualMarketQueryService.getOrderDetail(orderId, 9).deliveryContents()).hasSize(1);
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualOrderServiceTest,VirtualMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the order service, order query methods, mapper query SQL, and controller routes do not exist yet.

- [ ] **Step 3: Implement order creation, order queries, and preloaded delivery**

```java
public List<VirtualOrderResponse> listBuyingOrders(int buyerUserId) {
    return virtualOrderMapper.selectByBuyerUserId(buyerUserId).stream()
            .map(VirtualOrderResponse::from)
            .toList();
}

public List<VirtualOrderResponse> listSellingOrders(int sellerUserId) {
    return virtualOrderMapper.selectBySellerUserId(sellerUserId).stream()
            .map(VirtualOrderResponse::from)
            .toList();
}

public VirtualOrderDetailResponse getOrderDetail(long orderId, int actorUserId) {
    VirtualOrder order = requireReadableOrder(orderId, actorUserId);
    return VirtualOrderDetailResponse.from(order, virtualDeliveryMapper.selectByOrderId(orderId));
}
```

```java
@Transactional
public VirtualOrderResponse createOrder(String requestId, int buyerUserId, long listingId, int quantity) {
    VirtualListing listing = requireActiveListingForUpdate(listingId);
    validateBuyerAndQuantity(buyerUserId, listing, quantity);

    List<VirtualInventoryUnit> reservedUnits = reserveInventoryIfNeeded(listing, quantity);
    long totalAmount = listing.getUnitPrice() * quantity;
    WalletMarketTxnView escrowTxn = walletMarketActionApi.escrowOrder(requestId + ":escrow", buyerUserId, totalAmount, "virtual-order:" + requestId);

    VirtualOrder order = insertEscrowedOrder(requestId, buyerUserId, listing, quantity, totalAmount, escrowTxn.txnId());

    if ("PRELOADED".equals(listing.getDeliveryMode())) {
        String deliveryContent = reservedUnits.stream().map(VirtualInventoryUnit::getPayloadContent).collect(Collectors.joining("\n"));
        insertDeliveredPayload(order.getOrderId(), listing.getSellerUserId(), "PRELOADED_BATCH", deliveryContent);
        markOrderDelivered(order.getOrderId(), deliveryContent);
    }

    return VirtualOrderResponse.from(reloadOrder(order.getOrderId()));
}
```

```java
@GetMapping("/orders/buying")
public Result<List<VirtualOrderResponse>> listBuyingOrders(Authentication authentication) {
    int buyerUserId = CurrentUser.requireUserId(authentication);
    return Result.ok(virtualMarketQueryService.listBuyingOrders(buyerUserId));
}

@GetMapping("/orders/selling")
public Result<List<VirtualOrderResponse>> listSellingOrders(Authentication authentication) {
    int sellerUserId = CurrentUser.requireUserId(authentication);
    return Result.ok(virtualMarketQueryService.listSellingOrders(sellerUserId));
}

@GetMapping("/orders/{orderId}")
public Result<VirtualOrderDetailResponse> getOrderDetail(Authentication authentication, @PathVariable long orderId) {
    int actorUserId = CurrentUser.requireUserId(authentication);
    return Result.ok(virtualMarketQueryService.getOrderDetail(orderId, actorUserId));
}
```

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualOrderServiceTest,VirtualMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualOrderService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualOrderRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualOrderResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualOrderDetailResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualMarketQueryService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualOrderMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/VirtualDeliveryMapper.java \
  backend/community-app/src/main/resources/mapper/virtual_order_mapper.xml \
  backend/community-app/src/main/resources/mapper/virtual_delivery_mapper.xml \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java
git commit -m "feat: add virtual order flow and query apis"
```

---

### Task 5: Implement Manual Delivery, Buyer Confirm, And Pre-Delivery Cancel

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/DeliverVirtualOrderRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualOrderService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java`

- [ ] **Step 1: Write the failing manual delivery, confirm, and cancel tests**

```java
@Test
void manualOrderShouldRequireSellerDeliveryBeforeBuyerConfirm() {
    long listingId = seedManualListing(7, 1200L);
    VirtualOrderResponse order = virtualOrderService.createOrder("manual:req-1", 9, listingId, 2);

    assertThat(order.status()).isEqualTo("ESCROWED");

    virtualOrderService.deliverOrder(order.orderId(), 7, "邀请码-A\n邀请码-B");
    VirtualOrderResponse confirmed = virtualOrderService.confirmOrder(order.orderId(), 9);

    assertThat(confirmed.status()).isEqualTo("COMPLETED");
    assertThat(walletTxnCount("virtual-order:" + order.orderId() + ":release")).isEqualTo(1);
}
```

```java
@Test
void cancelEscrowedOrderShouldRefundBuyerAndUnlockInventory() {
    long listingId = seedPreloadedListing(7, List.of("CODE-001"));
    VirtualOrderResponse order = virtualOrderService.createEscrowOnlyOrderForTest("cancel:req-1", 9, listingId, 1);

    VirtualOrderResponse cancelled = virtualOrderService.cancelOrder(order.orderId(), 9);

    assertThat(cancelled.status()).isEqualTo("CANCELLED");
    assertThat(walletTxnCount("virtual-order:" + order.orderId() + ":refund")).isEqualTo(1);
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualOrderServiceTest,VirtualMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because manual delivery, confirm, cancel flows, and the delivery request DTO/controller contract are incomplete.

- [ ] **Step 3: Implement manual delivery, confirm, and cancel**

```java
@Transactional
public VirtualOrderResponse deliverOrder(long orderId, int sellerUserId, String deliveryContent) {
    VirtualOrder order = requireOrderForUpdate(orderId);
    ensureManualDeliveryAllowed(order, sellerUserId);
    insertDeliveredPayload(orderId, sellerUserId, "MANUAL_TEXT", deliveryContent.trim());
    markOrderDelivered(orderId, deliveryContent);
    return VirtualOrderResponse.from(reloadOrder(orderId));
}

@Transactional
public VirtualOrderResponse confirmOrder(long orderId, int buyerUserId) {
    VirtualOrder order = requireDeliveredOrderForBuyer(orderId, buyerUserId);
    WalletMarketTxnView releaseTxn = walletMarketActionApi.releaseOrder(
            "virtual-order:" + orderId + ":release",
            order.getSellerUserId(),
            order.getTotalAmount(),
            "virtual-order:" + orderId
    );
    markOrderCompleted(orderId, releaseTxn.txnId());
    return VirtualOrderResponse.from(reloadOrder(orderId));
}
```

```java
@PostMapping("/orders/{orderId}/deliver")
public Result<VirtualOrderResponse> deliverOrder(Authentication authentication,
                                                 @PathVariable long orderId,
                                                 @RequestBody @Valid DeliverVirtualOrderRequest request) {
    int sellerUserId = CurrentUser.requireUserId(authentication);
    return Result.ok(virtualOrderService.deliverOrder(orderId, sellerUserId, request.getDeliveryContent()));
}
```

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualOrderServiceTest,VirtualMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/dto/DeliverVirtualOrderRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualOrderService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java
git commit -m "feat: add manual delivery and confirm flows"
```

---

### Task 6: Implement Disputes, Seller Decision, Admin Resolution, And Auto-Release Job

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/api/action/VirtualOrderAutoReleaseActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/api/model/VirtualOrderAutoReleaseResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualDisputeService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualDisputeRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/SellerDisputeDecisionRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AdminResolveVirtualDisputeRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualDisputeResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminVirtualMarketController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualOrderService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualDisputeServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminVirtualMarketControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandlerTest.java`

- [ ] **Step 1: Write the failing dispute and auto-release tests**

```java
@Test
void sellerAcceptedDisputeShouldRefundBuyer() {
    long orderId = seedDeliveredManualOrder(7, 9, 2400L);

    VirtualDisputeResponse dispute = virtualDisputeService.openDispute(orderId, 9, "商品无效", "兑换失败");
    VirtualDisputeResponse resolved = virtualDisputeService.sellerAcceptRefund(dispute.disputeId(), 7, "同意退款");

    assertThat(resolved.status()).isEqualTo("SELLER_ACCEPTED");
    assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
    assertThat(walletTxnCount("virtual-order:" + orderId + ":refund")).isEqualTo(1);
}
```

```java
@Test
void autoReleaseHandlerShouldCompleteOverdueDeliveredOrders() {
    long orderId = seedDeliveredOrderDueForAutoRelease();

    handler.autoRelease();

    assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
    assertThat(walletTxnCount("virtual-order:" + orderId + ":release")).isEqualTo(1);
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualDisputeServiceTest,AdminVirtualMarketControllerTest,VirtualOrderAutoReleaseHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because dispute services, admin resolution, and auto-release job do not exist yet.

- [ ] **Step 3: Implement dispute handling and auto-release**

```java
@Transactional
public VirtualDisputeResponse sellerAcceptRefund(long disputeId, int sellerUserId, String sellerNote) {
    VirtualDispute dispute = requireOpenDisputeForSeller(disputeId, sellerUserId);
    VirtualOrder order = requireDisputedOrderForUpdate(dispute.getOrderId());

    WalletMarketTxnView refundTxn = walletMarketActionApi.refundOrder(
            "virtual-order:" + order.getOrderId() + ":refund",
            order.getBuyerUserId(),
            order.getTotalAmount(),
            "virtual-order:" + order.getOrderId()
    );

    markDisputeSellerAccepted(disputeId, sellerNote);
    markOrderRefunded(order.getOrderId(), refundTxn.txnId());
    return VirtualDisputeResponse.from(reloadDispute(disputeId));
}
```

```java
@XxlJob(JOB_NAME)
public void autoRelease() {
    VirtualOrderAutoReleaseResult result = virtualOrderAutoReleaseActionApi.autoReleaseDueOrders();
    String message = "[market] auto-release completed=" + result.completedCount() + " skipped=" + result.skippedCount();
    XxlJobHelper.log(message);
    XxlJobHelper.handleSuccess(message);
}
```

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualDisputeServiceTest,AdminVirtualMarketControllerTest,VirtualOrderAutoReleaseHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/api/action/VirtualOrderAutoReleaseActionApi.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/api/model/VirtualOrderAutoReleaseResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualDisputeService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateVirtualDisputeRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/SellerDisputeDecisionRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/AdminResolveVirtualDisputeRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/VirtualDisputeResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminVirtualMarketController.java \
  backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandler.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualDisputeServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminVirtualMarketControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandlerTest.java
git commit -m "feat: add virtual market dispute and auto-release flows"
```

---

### Task 7: Add Virtual Market Frontend Routes, Services, And Views

**Files:**
- Create: `frontend/src/api/services/virtualMarketService.js`
- Create: `frontend/src/views/VirtualMarketListView.vue`
- Create: `frontend/src/views/VirtualMarketDetailView.vue`
- Create: `frontend/src/views/VirtualMarketPublishView.vue`
- Create: `frontend/src/views/VirtualMarketMyListingsView.vue`
- Create: `frontend/src/views/VirtualMarketInventoryView.vue`
- Create: `frontend/src/views/VirtualMarketBuyingOrdersView.vue`
- Create: `frontend/src/views/VirtualMarketSellingOrdersView.vue`
- Create: `frontend/src/views/VirtualMarketOrderDetailView.vue`
- Create: `frontend/src/views/AdminVirtualDisputesView.vue`
- Create: `frontend/src/views/virtualMarketState.js`
- Create: `frontend/src/views/virtualMarketState.test.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/index.test.js`
- Modify: `frontend/src/router/navigation.test.js`
- Modify: `frontend/src/styles/pages.css`

- [ ] **Step 1: Write the failing frontend state and router tests**

```js
import { buildVirtualMarketState } from './virtualMarketState'

test('buildVirtualMarketState should label delivery and order status clearly', () => {
  const state = buildVirtualMarketState({
    listings: [{ id: 1, title: 'Steam Key', unitPrice: 1999, deliveryMode: 'PRELOADED', status: 'ACTIVE', stockAvailable: 2 }],
    orders: [{ orderId: 9, status: 'DELIVERED', totalAmount: 3998, autoConfirmAt: '2026-04-04T12:00:00Z' }]
  })

  expect(state.listings[0].deliveryLabel).toBe('自动交付')
  expect(state.orders[0].statusLabel).toBe('待确认')
})
```

```js
expect(routes.some((route) => route.name === 'virtualMarket')).toBe(true)
expect(routes.some((route) => route.name === 'virtualMarketPublish')).toBe(true)
expect(routes.some((route) => route.name === 'adminVirtualDisputes')).toBe(true)
```

- [ ] **Step 2: Run the targeted frontend tests to verify they fail**

Run: `cd frontend && npm test -- virtualMarketState.test.js router/index.test.js router/navigation.test.js`

Expected: FAIL because the virtual market state helper, routes, and navigation entries do not exist yet.

- [ ] **Step 3: Implement frontend services, routes, and views**

```js
export async function listVirtualListings(params = {}) {
  const resp = await http.get('/api/market/virtual/listings', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询虚拟商品列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createVirtualOrder(payload) {
  const resp = await http.post('/api/market/virtual/orders', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建虚拟商品订单')
  return { data: data || {}, traceId }
}

export async function listBuyingVirtualOrders() {
  const resp = await http.get('/api/market/virtual/orders/buying')
  const { data, traceId } = unwrapResultBody(resp.data, '查询购买订单')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getVirtualOrderDetail(orderId) {
  const resp = await http.get(`/api/market/virtual/orders/${encodeURIComponent(orderId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '查询订单详情')
  return { data: data || {}, traceId }
}
```

```js
{
  path: '/market/virtual',
  name: 'virtualMarket',
  component: VirtualMarketListView,
  meta: { title: '虚拟市场', subtitle: '浏览用户出售的虚拟商品。', navGroup: 'explore' }
},
{
  path: '/market/virtual/orders/buying',
  name: 'virtualMarketBuyingOrders',
  component: VirtualMarketBuyingOrdersView,
  meta: { title: '我的购买', subtitle: '查看托管、交付、确认与申诉状态。', navGroup: 'me', requiresAuth: true }
}
```

```vue
<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />
    <section class="market-hero">
      <span class="market-label">虚拟市场</span>
      <strong>用户卖家 · 固定价一口价</strong>
      <p>官方商城继续保留，这里只展示用户出售的虚拟商品。</p>
    </section>
    <VirtualMarketListingGrid :items="state.listings" />
  </div>
</template>
```

- [ ] **Step 4: Run the targeted frontend tests to verify they pass**

Run: `cd frontend && npm test -- virtualMarketState.test.js router/index.test.js router/navigation.test.js`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/services/virtualMarketService.js \
  frontend/src/views/VirtualMarketListView.vue \
  frontend/src/views/VirtualMarketDetailView.vue \
  frontend/src/views/VirtualMarketPublishView.vue \
  frontend/src/views/VirtualMarketMyListingsView.vue \
  frontend/src/views/VirtualMarketInventoryView.vue \
  frontend/src/views/VirtualMarketBuyingOrdersView.vue \
  frontend/src/views/VirtualMarketSellingOrdersView.vue \
  frontend/src/views/VirtualMarketOrderDetailView.vue \
  frontend/src/views/AdminVirtualDisputesView.vue \
  frontend/src/views/virtualMarketState.js \
  frontend/src/views/virtualMarketState.test.js \
  frontend/src/router/index.js \
  frontend/src/router/navigation.js \
  frontend/src/router/index.test.js \
  frontend/src/router/navigation.test.js \
  frontend/src/styles/pages.css
git commit -m "feat: add virtual market frontend surfaces"
```

---

### Task 8: Run Full Verification And Final Checkpoint

**Files:**
- No planned file edits; verification only.

- [ ] **Step 1: Run the focused backend suite for the virtual market**

Run: `cd backend && mvn -pl community-app -am -Dtest=VirtualMarketPersistenceTest,WalletMarketActionServiceTest,VirtualListingServiceTest,VirtualOrderServiceTest,VirtualDisputeServiceTest,VirtualMarketControllerTest,AdminVirtualMarketControllerTest,VirtualOrderAutoReleaseHandlerTest,DomainBoundaryArchTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 2: Run the full backend suite**

Run: `cd backend && mvn -pl community-app -am test`

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run the full frontend test suite**

Run: `cd frontend && npm test`

Expected: all tests PASS

- [ ] **Step 4: Run the frontend production build**

Run: `cd frontend && npm run build`

Expected: build succeeds and emits `dist/` assets

- [ ] **Step 5: Verify the worktree is clean**

Run: `git status --short`

Expected: no output

---

## Self-Review

### Spec coverage

- Dedicated market domain instead of reusing official reward shop: covered by Tasks 1, 3, 4, 6, and 7.
- Wallet-only money flow through escrow, release, and refund: covered by Tasks 2, 4, 5, and 6.
- Preloaded and manual delivery modes: covered by Tasks 3, 4, and 5.
- Buyer/seller order views and immutable order snapshots: covered by Tasks 4 and 7.
- Buyer confirm or 24-hour auto-release: covered by Task 6.
- Default no-refund after delivery, with seller-first dispute handling and admin fallback: covered by Task 6.
- Separate frontend entry alongside official reward shop: covered by Task 7.
- Full verification and architecture test health: covered by Task 8.

### Placeholder scan

- No `TODO`, `TBD`, or “similar to Task N” placeholders remain.
- Every code-changing task includes concrete file paths, example test code, implementation snippets, and exact commands.

### Type consistency

- Wallet collaboration uses `WalletMarketActionApi` / `WalletMarketTxnView` consistently.
- Auto-release job uses `VirtualOrderAutoReleaseActionApi` / `VirtualOrderAutoReleaseResult` consistently.
- Market transport and persistence names consistently use the `Virtual*` prefix, except `DeliverVirtualOrderRequest`, which stays specific to the delivery action contract.
- Buyer/seller order read paths consistently flow through `VirtualMarketQueryService` plus `VirtualOrderResponse` / `VirtualOrderDetailResponse`.

## Execution Status

Execution completed inline in worktree `community/.worktrees/open-wallet-phase2-virtual-market`.

Verified commands:

- `cd backend && mvn -pl community-app -am -Dtest=VirtualMarketPersistenceTest,WalletMarketActionServiceTest,VirtualListingServiceTest,VirtualOrderServiceTest,VirtualDisputeServiceTest,VirtualMarketControllerTest,AdminVirtualMarketControllerTest,VirtualOrderAutoReleaseHandlerTest,DomainBoundaryArchTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `cd backend && mvn -pl community-app -am test`
- `cd frontend && npm test`
- `cd frontend && npm run build`

Verification outcome:

- Focused virtual-market backend suite passed.
- Full backend suite passed with `Tests run: 491, Failures: 0, Errors: 0, Skipped: 0`.
- Full frontend suite passed with `43` test files and `158` tests passing.
- Frontend production build succeeded and emitted `dist/` assets.

Current worktree note:

- The worktree is intentionally not clean yet because the implementation, tests, and this plan document remain uncommitted.
