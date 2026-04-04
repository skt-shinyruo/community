# Community Open Wallet Phase 3 Physical Market Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Converge the Phase 2 virtual-only market into a unified `market_*` model, then add physical goods trading with address book, shipment, and goods-type-driven unified market pages, while keeping all money movement on the Phase 1 wallet ledger.

**Architecture:** Replace transitional `virtual_*` persistence and transport types with one main `market_listing` table, one main `market_order` table, and side tables for virtual inventory, addresses, shipments, and disputes. Backend and frontend both converge from `/market/virtual/**` to one `goodsType`-driven market surface. Wallet escrow, release, refund, and auto-confirm stay append-only and run through market-to-wallet API boundaries rather than direct balance mutation.

**Tech Stack:** Java 17, Spring Boot 3, Spring Transactions, MyBatis XML mappers, H2 test schema, XXL Job handlers, existing auth/security stack, wallet ledger APIs, Vue 3, Vue Router, Vitest, Maven

---

## File Structure Map

### Schema

- `deploy/mysql-init/010_schema.sql`
  Role: replace Phase 2 transitional `virtual_*` market tables with final unified `market_*` tables.
- `backend/community-app/src/test/resources/schema.sql`
  Role: mirror the final unified market schema in H2 tests.

### Wallet collaboration boundary

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/api/action/WalletMarketActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/api/model/WalletMarketTxnView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketActionService.java`
  Role: continue to encapsulate escrow, release, and refund postings so the market domain never mutates balances directly.

### Market persistence

- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketListing.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketInventoryUnit.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketOrder.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketDelivery.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketDispute.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketAddress.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketShipment.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketListingMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketInventoryUnitMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDeliveryMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDisputeMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketAddressMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketShipmentMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/market_listing_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_inventory_unit_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_order_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_delivery_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_dispute_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_address_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_shipment_mapper.xml`
  Role: final unified market persistence for both virtual and physical goods, including manual virtual delivery facts.

### Auto-confirm boundary

- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/api/action/MarketOrderAutoConfirmActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/api/model/MarketOrderAutoConfirmResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandler.java`
  Role: run due confirm/release for both goods types through a unified market action API.

### Market services

- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketListingService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketInventoryService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketAddressService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java`
  Role: implement unified listing publish/edit, virtual inventory, physical address book, order escrow, virtual delivery, physical shipment, confirm, cancel, dispute, and auto-confirm logic.

### Market transport layer

- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AddMarketInventoryBatchRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketAddressRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateMarketAddressRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketListingRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateMarketListingRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketOrderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/DeliverMarketOrderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/ShipMarketOrderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketDisputeRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/SellerDisputeDecisionRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AdminResolveMarketDisputeRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketAddressResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketInventoryUnitResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingDetailResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderDetailResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketDisputeResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java`
  Role: expose unified read, seller, buyer, address, and admin APIs.

### Frontend

- Create: `frontend/src/api/services/marketService.js`
- Create: `frontend/src/api/services/marketService.test.js`
- Create: `frontend/src/views/MarketListView.vue`
- Create: `frontend/src/views/MarketDetailView.vue`
- Create: `frontend/src/views/MarketPublishView.vue`
- Create: `frontend/src/views/MarketMyListingsView.vue`
- Create: `frontend/src/views/MarketInventoryView.vue`
- Create: `frontend/src/views/MarketBuyingOrdersView.vue`
- Create: `frontend/src/views/MarketSellingOrdersView.vue`
- Create: `frontend/src/views/MarketOrderDetailView.vue`
- Create: `frontend/src/views/MarketAddressesView.vue`
- Create: `frontend/src/views/AdminMarketDisputesView.vue`
- Create: `frontend/src/views/marketState.js`
- Create: `frontend/src/views/marketState.test.js`
- Create: `frontend/src/views/MarketViews.test.js`
- Create: `frontend/src/views/MarketOrderViews.test.js`
- Create: `frontend/src/views/MarketSellerViews.test.js`
- Create: `frontend/src/views/MarketAddressesView.test.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/index.test.js`
- Modify: `frontend/src/router/navigation.test.js`
- Modify: `frontend/src/styles/pages.css`
  Role: replace virtual-only pages with one `goodsType`-driven market surface and address book.

### Transitional files to retire after convergence

- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualListing.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualInventoryUnit.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualOrder.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDelivery.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/VirtualDispute.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualMarketQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualListingService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualInventoryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualOrderService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/VirtualDisputeService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminVirtualMarketController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/api/action/VirtualOrderAutoReleaseActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/api/model/VirtualOrderAutoReleaseResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandler.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualMarketPersistenceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualListingServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualDisputeServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminVirtualMarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandlerTest.java`
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
  Role: transitional Phase 2 files to remove once unified market code is green.

### Tests

- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketPersistenceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketListingServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketAddressServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketDisputeServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandlerTest.java`
  Role: prove the final unified market model works for both goods types.

---

### Task 1: Replace Virtual Market Schema With Unified Market Persistence

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketListing.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketInventoryUnit.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketOrder.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketDispute.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketAddress.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketShipment.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketListingMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketInventoryUnitMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDisputeMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketAddressMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketShipmentMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/market_listing_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_inventory_unit_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_order_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_dispute_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_address_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/market_shipment_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketPersistenceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualMarketPersistenceTest.java`

- [ ] **Step 1: Write the failing unified persistence tests**

```java
@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MarketPersistenceTest {

    @Autowired
    private MarketListingMapper marketListingMapper;

    @Autowired
    private MarketAddressMapper marketAddressMapper;

    @Autowired
    private MarketOrderMapper marketOrderMapper;

    @Autowired
    private MarketShipmentMapper marketShipmentMapper;

    @Test
    void insertListingsAndPhysicalShipmentShouldPersistUnifiedGoodsTypeFacts() {
        MarketListing listing = new MarketListing();
        listing.setSellerUserId(7);
        listing.setGoodsType("PHYSICAL");
        listing.setTitle("二手键盘");
        listing.setDescription("九成新");
        listing.setUnitPrice(12_900L);
        listing.setStockTotal(3);
        listing.setStockAvailable(3);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("ACTIVE");
        marketListingMapper.insert(listing);

        MarketAddress address = new MarketAddress();
        address.setUserId(9);
        address.setReceiverName("张三");
        address.setReceiverPhone("13800000000");
        address.setProvince("上海市");
        address.setCity("上海市");
        address.setDistrict("浦东新区");
        address.setDetailAddress("世纪大道 100 号");
        address.setPostalCode("200120");
        address.setDefault(true);
        address.setStatus("ACTIVE");
        marketAddressMapper.insert(address);

        MarketOrder order = new MarketOrder();
        order.setRequestId("physical:req-1");
        order.setListingId(listing.getListingId());
        order.setGoodsType("PHYSICAL");
        order.setSellerUserId(7);
        order.setBuyerUserId(9);
        order.setQuantity(1);
        order.setUnitPriceSnapshot(12_900L);
        order.setTotalAmount(12_900L);
        order.setListingTitleSnapshot("二手键盘");
        order.setStatus("SHIPPED");
        order.setReceiverNameSnapshot("张三");
        order.setReceiverPhoneSnapshot("13800000000");
        order.setProvinceSnapshot("上海市");
        order.setCitySnapshot("上海市");
        order.setDistrictSnapshot("浦东新区");
        order.setDetailAddressSnapshot("世纪大道 100 号");
        order.setPostalCodeSnapshot("200120");
        marketOrderMapper.insert(order);

        MarketShipment shipment = new MarketShipment();
        shipment.setOrderId(order.getOrderId());
        shipment.setSellerUserId(7);
        shipment.setCarrierName("顺丰");
        shipment.setTrackingNo("SF1234567890");
        shipment.setShippingRemark("工作日派送");
        marketShipmentMapper.insert(shipment);

        assertThat(marketListingMapper.selectById(listing.getListingId()).getGoodsType()).isEqualTo("PHYSICAL");
        assertThat(marketOrderMapper.selectById(order.getOrderId()).getReceiverNameSnapshot()).isEqualTo("张三");
        assertThat(marketShipmentMapper.selectByOrderId(order.getOrderId()).getTrackingNo()).isEqualTo("SF1234567890");
    }
}
```

- [ ] **Step 2: Run the targeted persistence test to verify it fails**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `market_*` tables, entities, mappers, and XML files do not exist yet.

- [ ] **Step 3: Add unified market schema, entities, and mapper primitives**

```sql
create table if not exists market_listing (
  listing_id bigint auto_increment primary key,
  seller_user_id int not null,
  goods_type varchar(16) not null,
  title varchar(128) not null,
  description varchar(1000) not null,
  unit_price bigint not null,
  stock_total int not null,
  stock_available int not null,
  min_purchase_quantity int not null,
  max_purchase_quantity int not null,
  delivery_mode varchar(16) default null,
  stock_mode varchar(16) default null,
  status varchar(16) not null,
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp
);

create table if not exists market_order (
  order_id bigint auto_increment primary key,
  request_id varchar(96) not null,
  listing_id bigint not null,
  goods_type varchar(16) not null,
  seller_user_id int not null,
  buyer_user_id int not null,
  quantity int not null,
  unit_price_snapshot bigint not null,
  total_amount bigint not null,
  listing_title_snapshot varchar(128) not null,
  delivery_mode_snapshot varchar(16) default null,
  status varchar(16) not null,
  escrow_txn_id bigint default null,
  release_txn_id bigint default null,
  refund_txn_id bigint default null,
  auto_confirm_at timestamp default null,
  receiver_name_snapshot varchar(64) default null,
  receiver_phone_snapshot varchar(32) default null,
  province_snapshot varchar(64) default null,
  city_snapshot varchar(64) default null,
  district_snapshot varchar(64) default null,
  detail_address_snapshot varchar(255) default null,
  postal_code_snapshot varchar(16) default null,
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp,
  unique key uk_market_order_request (request_id)
);

create table if not exists market_inventory_unit (
  inventory_unit_id bigint auto_increment primary key,
  listing_id bigint not null,
  seller_user_id int not null,
  payload_type varchar(16) not null,
  payload_content varchar(4000) not null,
  status varchar(16) not null,
  reserved_order_id bigint default null,
  delivered_at timestamp default null,
  create_time timestamp default current_timestamp
);

create table if not exists market_dispute (
  dispute_id bigint auto_increment primary key,
  order_id bigint not null,
  goods_type varchar(16) not null,
  buyer_user_id int not null,
  seller_user_id int not null,
  status varchar(24) not null,
  reason varchar(255) not null,
  buyer_note varchar(1000) default null,
  seller_note varchar(1000) default null,
  resolution_type varchar(24) default null,
  resolved_by int default null,
  resolved_at timestamp default null,
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp
);

create table if not exists market_address (
  address_id bigint auto_increment primary key,
  user_id int not null,
  receiver_name varchar(64) not null,
  receiver_phone varchar(32) not null,
  province varchar(64) not null,
  city varchar(64) not null,
  district varchar(64) not null,
  detail_address varchar(255) not null,
  postal_code varchar(16) default null,
  is_default boolean not null default false,
  status varchar(16) not null,
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp
);

create table if not exists market_shipment (
  shipment_id bigint auto_increment primary key,
  order_id bigint not null,
  seller_user_id int not null,
  carrier_name varchar(64) not null,
  tracking_no varchar(128) not null,
  shipping_remark varchar(1000) default null,
  shipped_at timestamp default current_timestamp,
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp,
  unique key uk_market_shipment_order (order_id)
);
```

```java
public class MarketListing {
    private long listingId;
    private int sellerUserId;
    private String goodsType;
    private String title;
    private String description;
    private long unitPrice;
    private int stockTotal;
    private int stockAvailable;
    private int minPurchaseQuantity;
    private int maxPurchaseQuantity;
    private String deliveryMode;
    private String stockMode;
    private String status;
}
```

```java
public interface MarketShipmentMapper {
    void insert(MarketShipment shipment);
    MarketShipment selectByOrderId(long orderId);
}
```

- [ ] **Step 4: Run the persistence test to verify it passes**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add deploy/mysql-init/010_schema.sql \
  backend/community-app/src/test/resources/schema.sql \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketListing.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketInventoryUnit.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketOrder.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketDispute.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketAddress.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketShipment.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketListingMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketInventoryUnitMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDisputeMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketAddressMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketShipmentMapper.java \
  backend/community-app/src/main/resources/mapper/market_listing_mapper.xml \
  backend/community-app/src/main/resources/mapper/market_inventory_unit_mapper.xml \
  backend/community-app/src/main/resources/mapper/market_order_mapper.xml \
  backend/community-app/src/main/resources/mapper/market_dispute_mapper.xml \
  backend/community-app/src/main/resources/mapper/market_address_mapper.xml \
  backend/community-app/src/main/resources/mapper/market_shipment_mapper.xml \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketPersistenceTest.java
git rm backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualMarketPersistenceTest.java
git commit -m "feat: add unified market schema primitives"
```

---

### Task 2: Add Unified Listing, Inventory, And Address Services

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AddMarketInventoryBatchRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketAddressRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateMarketAddressRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketListingRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateMarketListingRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketAddressResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketInventoryUnitResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingDetailResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketListingService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketInventoryService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketAddressService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketListingServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketAddressServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualListingServiceTest.java`

- [ ] **Step 1: Write the failing listing and address service tests**

```java
@Test
void createPhysicalListingShouldPersistGoodsTypeWithoutVirtualOnlyFields() {
    CreateMarketListingRequest request = new CreateMarketListingRequest();
    request.setGoodsType("PHYSICAL");
    request.setTitle("二手键盘");
    request.setDescription("九成新");
    request.setUnitPrice(12_900L);
    request.setStockTotal(3);
    request.setMinPurchaseQuantity(1);
    request.setMaxPurchaseQuantity(1);

    MarketListingResponse response = marketListingService.createListing(7, request, null);

    assertThat(response.goodsType()).isEqualTo("PHYSICAL");
    assertThat(response.deliveryMode()).isNull();
    assertThat(response.stockAvailable()).isEqualTo(3);
}

@Test
void addressCrudShouldKeepOneDefaultAddressPerUser() {
    CreateMarketAddressRequest first = new CreateMarketAddressRequest();
    first.setReceiverName("张三");
    first.setReceiverPhone("13800000000");
    first.setProvince("上海市");
    first.setCity("上海市");
    first.setDistrict("浦东新区");
    first.setDetailAddress("世纪大道 100 号");
    first.setPostalCode("200120");
    first.setDefault(true);

    CreateMarketAddressRequest second = new CreateMarketAddressRequest();
    second.setReceiverName("李四");
    second.setReceiverPhone("13900000000");
    second.setProvince("北京市");
    second.setCity("北京市");
    second.setDistrict("海淀区");
    second.setDetailAddress("中关村大街 1 号");
    second.setPostalCode("100080");
    second.setDefault(true);

    marketAddressService.createAddress(9, first);
    marketAddressService.createAddress(9, second);

    assertThat(marketAddressService.listAddresses(9))
        .filteredOn(MarketAddressResponse::isDefault)
        .hasSize(1)
        .first()
        .extracting(MarketAddressResponse::receiverName)
        .isEqualTo("李四");
}
```

- [ ] **Step 2: Run the targeted service tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketListingServiceTest,MarketAddressServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because unified listing DTOs, address DTOs, and services do not exist yet.

- [ ] **Step 3: Implement listing, inventory, and address services with `goodsType` validation**

```java
private void validateListingRequest(CreateMarketListingRequest request, AddMarketInventoryBatchRequest inventory) {
    String goodsType = normalizeRequired(request.getGoodsType(), "goodsType");
    if ("VIRTUAL".equals(goodsType)) {
        String deliveryMode = normalizeRequired(request.getDeliveryMode(), "deliveryMode");
        String stockMode = normalizeRequired(request.getStockMode(), "stockMode");
        if (!Set.of("PRELOADED", "MANUAL").contains(deliveryMode)) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid deliveryMode: " + deliveryMode);
        }
        if (!Set.of("FINITE", "UNLIMITED").contains(stockMode)) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid stockMode: " + stockMode);
        }
        if ("PRELOADED".equals(deliveryMode) && inventory == null) {
            throw new BusinessException(INVALID_ARGUMENT, "preloaded virtual listing requires inventory");
        }
        return;
    }
    if (!"PHYSICAL".equals(goodsType)) {
        throw new BusinessException(INVALID_ARGUMENT, "invalid goodsType: " + goodsType);
    }
    if (request.getDeliveryMode() != null || request.getStockMode() != null || inventory != null) {
        throw new BusinessException(INVALID_ARGUMENT, "physical listing must not use virtual delivery fields");
    }
}
```

```java
public MarketAddressResponse createAddress(int userId, CreateMarketAddressRequest request) {
    if (request.isDefault()) {
        marketAddressMapper.clearDefaultByUserId(userId);
    }
    MarketAddress address = new MarketAddress();
    address.setUserId(userId);
    address.setReceiverName(request.getReceiverName());
    address.setReceiverPhone(request.getReceiverPhone());
    address.setProvince(request.getProvince());
    address.setCity(request.getCity());
    address.setDistrict(request.getDistrict());
    address.setDetailAddress(request.getDetailAddress());
    address.setPostalCode(request.getPostalCode());
    address.setDefault(request.isDefault());
    address.setStatus("ACTIVE");
    marketAddressMapper.insert(address);
    return MarketAddressResponse.from(address);
}
```

```java
public List<MarketListingResponse> listPublicListings() {
    return marketListingMapper.selectPublicListings().stream()
        .map(MarketListingResponse::from)
        .toList();
}
```

- [ ] **Step 4: Run the service tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketListingServiceTest,MarketAddressServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/dto/AddMarketInventoryBatchRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketAddressRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateMarketAddressRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketListingRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/UpdateMarketListingRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketAddressResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketInventoryUnitResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketListingDetailResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketListingService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketInventoryService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketAddressService.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketListingServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketAddressServiceTest.java
git rm backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualListingServiceTest.java
git commit -m "feat: add unified market listing and address services"
```

---

### Task 3: Add Unified Order Creation And Query Read Models

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketOrderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderDetailResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java`

- [ ] **Step 1: Write the failing unified order creation and query tests**

```java
@Test
void createPhysicalOrderShouldSnapshotSelectedAddressAndStayEscrowedBeforeShipment() {
    long listingId = seedPhysicalListing(7);
    long addressId = seedAddress(9, true);
    seedBuyerBalance(9, 20_000L);

    MarketOrderResponse response = marketOrderService.createOrder("physical:req-1", 9, listingId, 1, addressId);

    assertThat(response.goodsType()).isEqualTo("PHYSICAL");
    assertThat(response.status()).isEqualTo("ESCROWED");
    assertThat(response.autoConfirmAt()).isNull();

    MarketOrderDetailResponse detail = marketQueryService.getOrderDetail(response.orderId(), 9);
    assertThat(detail.receiverNameSnapshot()).isEqualTo("张三");
    assertThat(detail.shipment()).isNull();
}

@Test
void buyerAndSellerQueriesShouldReturnUnifiedMixedOrderSnapshots() {
    long virtualOrderId = seedDeliveredVirtualOrder(7, 9);
    long physicalOrderId = seedEscrowedPhysicalOrder(8, 9);

    assertThat(marketQueryService.listBuyingOrders(9))
        .extracting(MarketOrderResponse::goodsType)
        .contains("VIRTUAL", "PHYSICAL");
    assertThat(marketQueryService.listSellingOrders(8))
        .extracting(MarketOrderResponse::orderId)
        .contains(physicalOrderId);
    assertThat(marketQueryService.getOrderDetail(virtualOrderId, 9).deliveryContents()).isNotEmpty();
}
```

- [ ] **Step 2: Run the targeted order tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because unified order DTOs, address snapshots, and mixed query logic do not exist yet.

- [ ] **Step 3: Implement unified order creation with `goodsType` branching and read models**

```java
@Transactional
public MarketOrderResponse createOrder(String requestId, int buyerUserId, long listingId, int quantity, Long addressId) {
    MarketListing listing = requireActiveListingForUpdate(listingId);
    validateBuyerAndQuantity(buyerUserId, listing, quantity);

    MarketOrder existing = marketOrderMapper.selectByRequestId(requestId);
    if (existing != null) {
        return MarketOrderResponse.from(existing);
    }

    long totalAmount = listing.getUnitPrice() * quantity;
    WalletMarketTxnView escrowTxn = walletMarketActionApi.escrowOrder(
        requestId + ":escrow",
        buyerUserId,
        totalAmount,
        "market-order:" + requestId
    );

    MarketOrder order = new MarketOrder();
    order.setRequestId(requestId);
    order.setListingId(listingId);
    order.setGoodsType(listing.getGoodsType());
    order.setSellerUserId(listing.getSellerUserId());
    order.setBuyerUserId(buyerUserId);
    order.setQuantity(quantity);
    order.setUnitPriceSnapshot(listing.getUnitPrice());
    order.setTotalAmount(totalAmount);
    order.setListingTitleSnapshot(listing.getTitle());
    order.setDeliveryModeSnapshot(listing.getDeliveryMode());
    order.setStatus("ESCROWED");
    order.setEscrowTxnId(escrowTxn.txnId());

    if ("PHYSICAL".equals(listing.getGoodsType())) {
        MarketAddress address = requireActiveAddress(addressId, buyerUserId);
        snapshotAddress(order, address);
    }

    marketOrderMapper.insert(order);
    if ("VIRTUAL".equals(listing.getGoodsType())) {
        reserveVirtualInventoryIfNeeded(order, listing);
    }
    return MarketOrderResponse.from(marketOrderMapper.selectById(order.getOrderId()));
}
```

```java
public MarketOrderDetailResponse getOrderDetail(long orderId, int actorUserId) {
    MarketOrder order = requireReadableOrder(orderId, actorUserId);
    List<String> deliveryContents = "VIRTUAL".equals(order.getGoodsType())
        ? marketInventoryUnitMapper.selectDeliveredPayloadsByOrderId(orderId)
        : List.of();
    MarketShipment shipment = "PHYSICAL".equals(order.getGoodsType())
        ? marketShipmentMapper.selectByOrderId(orderId)
        : null;
    return MarketOrderDetailResponse.from(order, deliveryContents, shipment);
}
```

- [ ] **Step 4: Run the order tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketOrderRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderDetailResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java
git rm backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualOrderServiceTest.java
git commit -m "feat: add unified market order creation and queries"
```

---

### Task 4: Implement Virtual Delivery, Physical Shipment, Confirm, And Cancel

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/DeliverMarketOrderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/ShipMarketOrderRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketDelivery.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDeliveryMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/market_delivery_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/market_order_mapper.xml`
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`

- [ ] **Step 1: Write the failing fulfillment and cancel tests**

```java
@Test
void deliverVirtualOrderShouldReleaseOnlyAfterConfirmOrTimeout() {
    long orderId = seedEscrowedVirtualOrder(7, 9);

    MarketOrderResponse delivered = marketOrderService.deliverVirtualOrder(orderId, 7, "CODE-001");

    assertThat(delivered.status()).isEqualTo("DELIVERED");
    assertThat(delivered.autoConfirmAt()).isNotNull();
    assertThat(marketQueryService.getOrderDetail(orderId, 9).deliveryContents()).containsExactly("CODE-001");
}

@Test
void shipPhysicalOrderShouldPersistShipmentAndSetSevenDayAutoConfirm() {
    long orderId = seedEscrowedPhysicalOrder(7, 9);

    MarketOrderResponse shipped = marketOrderService.shipPhysicalOrder(orderId, 7, "顺丰", "SF1234567890", "工作日派送");

    assertThat(shipped.status()).isEqualTo("SHIPPED");
    assertThat(shipped.autoConfirmAt()).isNotNull();
    assertThat(marketQueryService.getOrderDetail(orderId, 9).shipment().trackingNo()).isEqualTo("SF1234567890");
}

@Test
void cancelPhysicalOrderBeforeShipmentShouldRefundBuyer() {
    long orderId = seedEscrowedPhysicalOrder(7, 9);

    MarketOrderResponse cancelled = marketOrderService.cancelOrder(orderId, 9);

    assertThat(cancelled.status()).isEqualTo("CANCELLED");
    assertThat(balanceOfUser(9)).isEqualTo(20_000L);
}
```

- [ ] **Step 2: Run the targeted fulfillment tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because unified deliver, ship, and cancel flows do not exist yet.

- [ ] **Step 3: Implement goods-type-specific fulfillment and cancel rules**

```java
@Transactional
public MarketOrderResponse deliverVirtualOrder(long orderId, int sellerUserId, String deliveryContent) {
    MarketOrder order = requireOrderForUpdate(orderId);
    requireSeller(order, sellerUserId);
    requireGoodsType(order, "VIRTUAL");
    requireStatus(order, "ESCROWED");

    persistVirtualDelivery(order, deliveryContent);
    order.setStatus("DELIVERED");
    order.setAutoConfirmAt(nowPlusHours(24));
    marketOrderMapper.updateDeliveryState(order);
    return MarketOrderResponse.from(marketOrderMapper.selectById(orderId));
}

@Transactional
public MarketOrderResponse shipPhysicalOrder(long orderId, int sellerUserId, String carrierName, String trackingNo, String remark) {
    MarketOrder order = requireOrderForUpdate(orderId);
    requireSeller(order, sellerUserId);
    requireGoodsType(order, "PHYSICAL");
    requireStatus(order, "ESCROWED");

    MarketShipment shipment = new MarketShipment();
    shipment.setOrderId(orderId);
    shipment.setSellerUserId(sellerUserId);
    shipment.setCarrierName(carrierName);
    shipment.setTrackingNo(trackingNo);
    shipment.setShippingRemark(remark);
    marketShipmentMapper.insert(shipment);

    order.setStatus("SHIPPED");
    order.setAutoConfirmAt(nowPlusDays(7));
    marketOrderMapper.updateShipmentState(order);
    return MarketOrderResponse.from(marketOrderMapper.selectById(orderId));
}

@Transactional
public MarketOrderResponse cancelOrder(long orderId, int buyerUserId) {
    MarketOrder order = requireOrderForUpdate(orderId);
    requireBuyer(order, buyerUserId);
    requireStatus(order, "ESCROWED");

    walletMarketActionApi.refundOrder(order.getRequestId() + ":refund", buyerUserId, order.getTotalAmount(), "market-order:" + order.getOrderId());
    if ("VIRTUAL".equals(order.getGoodsType())) {
        releaseReservedInventory(orderId);
    }
    order.setStatus("CANCELLED");
    marketOrderMapper.updateCancelState(order);
    return MarketOrderResponse.from(marketOrderMapper.selectById(orderId));
}
```

- [ ] **Step 4: Run the fulfillment tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/dto/DeliverMarketOrderRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/ShipMarketOrderRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java \
  backend/community-app/src/main/resources/mapper/market_order_mapper.xml \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java
git commit -m "feat: add unified market fulfillment flows"
```

---

### Task 5: Implement Unified Disputes And Auto-Confirm

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketDisputeRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/SellerDisputeDecisionRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/AdminResolveMarketDisputeRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketDisputeResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/api/action/MarketOrderAutoConfirmActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/api/model/MarketOrderAutoConfirmResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketDisputeServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandlerTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/market/api/action/VirtualOrderAutoReleaseActionApi.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/market/api/model/VirtualOrderAutoReleaseResult.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandler.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualDisputeServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandlerTest.java`

- [ ] **Step 1: Write the failing dispute and auto-confirm tests**

```java
@Test
void sellerAcceptedPhysicalDisputeShouldRefundBuyer() {
    long orderId = seedShippedPhysicalOrder(7, 9);
    MarketDisputeResponse dispute = marketDisputeService.openDispute(orderId, 9, "货不对板", "和描述不一致");

    MarketDisputeResponse resolved = marketDisputeService.sellerAcceptRefund(dispute.disputeId(), 7, "同意退款");

    assertThat(resolved.status()).isEqualTo("SELLER_ACCEPTED");
    assertThat(marketQueryService.getOrderDetail(orderId, 9).status()).isEqualTo("REFUNDED");
}

@Test
void autoConfirmShouldCompleteDeliveredAndShippedOrdersIdempotently() {
    seedDueDeliveredVirtualOrder(7, 9);
    seedDueShippedPhysicalOrder(8, 9);

    MarketOrderAutoConfirmResult result = marketOrderAutoConfirmActionApi.autoConfirmDueOrders();

    assertThat(result.completedCount()).isEqualTo(2);
    assertThat(result.skippedCount()).isEqualTo(0);
}
```

- [ ] **Step 2: Run the targeted dispute and auto-confirm tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketDisputeServiceTest,MarketOrderAutoConfirmHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because unified dispute DTOs, service, and auto-confirm job do not exist yet.

- [ ] **Step 3: Implement unified dispute resolution and due-order auto-confirm**

```java
@Transactional
public MarketDisputeResponse openDispute(long orderId, int buyerUserId, String reason, String buyerNote) {
    MarketOrder order = requireOrderForUpdate(orderId);
    requireBuyer(order, buyerUserId);
    requireDisputableStatus(order.getStatus());

    MarketDispute dispute = new MarketDispute();
    dispute.setOrderId(orderId);
    dispute.setGoodsType(order.getGoodsType());
    dispute.setBuyerUserId(buyerUserId);
    dispute.setSellerUserId(order.getSellerUserId());
    dispute.setStatus("OPEN");
    dispute.setReason(reason);
    dispute.setBuyerNote(buyerNote);
    marketDisputeMapper.insert(dispute);

    order.setStatus("DISPUTED");
    marketOrderMapper.updateStatus(orderId, "DISPUTED");
    return MarketDisputeResponse.from(dispute);
}
```

```java
@Transactional
public MarketOrderAutoConfirmResult autoConfirmDueOrders() {
    int completed = 0;
    int skipped = 0;
    for (MarketOrder order : marketOrderMapper.selectDueAutoConfirmOrders(new Date())) {
        if (!Set.of("DELIVERED", "SHIPPED").contains(order.getStatus())) {
            skipped++;
            continue;
        }
        confirmOrder(order.getOrderId(), order.getBuyerUserId());
        completed++;
    }
    return new MarketOrderAutoConfirmResult(completed, skipped);
}
```

```java
@XxlJob(JOB_NAME)
public void autoConfirm() {
    MarketOrderAutoConfirmResult result = marketOrderAutoConfirmActionApi.autoConfirmDueOrders();
    String message = "[market] auto-confirm completed=" + result.completedCount() + " skipped=" + result.skippedCount();
    XxlJobHelper.log(message);
    XxlJobHelper.handleSuccess(message);
    log.info(message);
}
```

- [ ] **Step 4: Run the dispute and auto-confirm tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketDisputeServiceTest,MarketOrderAutoConfirmHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketDisputeRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/SellerDisputeDecisionRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/AdminResolveMarketDisputeRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketDisputeResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/api/action/MarketOrderAutoConfirmActionApi.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/api/model/MarketOrderAutoConfirmResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandler.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketDisputeServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandlerTest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/market/api/action/VirtualOrderAutoReleaseActionApi.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/api/model/VirtualOrderAutoReleaseResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandler.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/service/VirtualDisputeServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/VirtualOrderAutoReleaseHandlerTest.java
git commit -m "feat: add unified market dispute and auto-confirm flows"
```

---

### Task 6: Replace Virtual Controllers, Security, And Backend Route Tests

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminVirtualMarketController.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminVirtualMarketControllerTest.java`

- [ ] **Step 1: Write the failing unified controller tests**

```java
@Test
void marketControllerShouldExposeUnifiedListingsOrdersAndAddresses() throws Exception {
    when(marketQueryService.listPublicListings()).thenReturn(List.of(
        new MarketListingResponse(11L, "VIRTUAL", "Steam 兑换码", "自动交付", 1999L, 2, 2, "ACTIVE", "PRELOADED", "FINITE"),
        new MarketListingResponse(21L, "PHYSICAL", "二手键盘", "九成新", 12900L, 3, 3, "ACTIVE", null, null)
    ));

    mockMvc.perform(get("/api/market/listings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].goodsType").value("VIRTUAL"))
        .andExpect(jsonPath("$.data[1].goodsType").value("PHYSICAL"));
}
```

- [ ] **Step 2: Run the targeted controller tests to verify they fail**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketControllerTest,AdminMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because unified controllers and routes do not exist yet.

- [ ] **Step 3: Implement unified market controllers and remove virtual-only routes**

```java
@RestController
@RequestMapping("/api/market")
public class MarketController {

    @GetMapping("/listings")
    public Result<List<MarketListingResponse>> listPublicListings() {
        return Result.ok(marketQueryService.listPublicListings());
    }

    @PostMapping("/orders/{orderId}/ship")
    public Result<MarketOrderResponse> shipOrder(Authentication authentication,
                                                 @PathVariable long orderId,
                                                 @RequestBody @Valid ShipMarketOrderRequest request) {
        int sellerUserId = CurrentUser.requireUserId(authentication);
        return Result.ok(marketOrderService.shipPhysicalOrder(orderId, sellerUserId, request.getCarrierName(), request.getTrackingNo(), request.getShippingRemark()));
    }

    @GetMapping("/addresses")
    public Result<List<MarketAddressResponse>> listAddresses(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(marketAddressService.listAddresses(userId));
    }
}
```

```java
@RestController
@RequestMapping("/api/admin/market/disputes")
public class AdminMarketController {

    @GetMapping
    public Result<List<MarketDisputeResponse>> list(Authentication authentication) {
        CurrentUser.requireAdmin(authentication);
        return Result.ok(marketDisputeService.listOpenDisputes());
    }
}
```

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketControllerTest,AdminMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminMarketController.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java
git rm backend/community-app/src/main/java/com/nowcoder/community/market/controller/VirtualMarketController.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/controller/AdminVirtualMarketController.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/VirtualMarketControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminVirtualMarketControllerTest.java
git commit -m "feat: replace virtual routes with unified market controllers"
```

---

### Task 7: Replace Virtual Frontend Surface With Unified Market Pages

**Files:**
- Create: `frontend/src/api/services/marketService.js`
- Create: `frontend/src/api/services/marketService.test.js`
- Create: `frontend/src/views/MarketListView.vue`
- Create: `frontend/src/views/MarketDetailView.vue`
- Create: `frontend/src/views/MarketPublishView.vue`
- Create: `frontend/src/views/MarketMyListingsView.vue`
- Create: `frontend/src/views/MarketInventoryView.vue`
- Create: `frontend/src/views/MarketBuyingOrdersView.vue`
- Create: `frontend/src/views/MarketSellingOrdersView.vue`
- Create: `frontend/src/views/MarketOrderDetailView.vue`
- Create: `frontend/src/views/MarketAddressesView.vue`
- Create: `frontend/src/views/AdminMarketDisputesView.vue`
- Create: `frontend/src/views/marketState.js`
- Create: `frontend/src/views/marketState.test.js`
- Create: `frontend/src/views/MarketViews.test.js`
- Create: `frontend/src/views/MarketOrderViews.test.js`
- Create: `frontend/src/views/MarketSellerViews.test.js`
- Create: `frontend/src/views/MarketAddressesView.test.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/index.test.js`
- Modify: `frontend/src/router/navigation.test.js`
- Modify: `frontend/src/styles/pages.css`
- Delete: `frontend/src/api/services/virtualMarketService.js`
- Delete: `frontend/src/api/services/virtualMarketService.test.js`
- Delete: `frontend/src/views/VirtualMarketListView.vue`
- Delete: `frontend/src/views/VirtualMarketDetailView.vue`
- Delete: `frontend/src/views/VirtualMarketPublishView.vue`
- Delete: `frontend/src/views/VirtualMarketMyListingsView.vue`
- Delete: `frontend/src/views/VirtualMarketInventoryView.vue`
- Delete: `frontend/src/views/VirtualMarketBuyingOrdersView.vue`
- Delete: `frontend/src/views/VirtualMarketSellingOrdersView.vue`
- Delete: `frontend/src/views/VirtualMarketOrderDetailView.vue`
- Delete: `frontend/src/views/AdminVirtualDisputesView.vue`
- Delete: `frontend/src/views/virtualMarketState.js`
- Delete: `frontend/src/views/virtualMarketState.test.js`
- Delete: `frontend/src/views/VirtualMarketOrderViews.test.js`
- Delete: `frontend/src/views/VirtualMarketSellerViews.test.js`

- [ ] **Step 1: Write the failing frontend state, service, and router tests**

```js
import { buildMarketState } from './marketState'

test('buildMarketState should derive labels from goodsType', () => {
  const state = buildMarketState({
    listings: [
      { listingId: 11, goodsType: 'VIRTUAL', title: 'Steam Key', unitPrice: 1999, status: 'ACTIVE', deliveryMode: 'PRELOADED', stockAvailable: 2 },
      { listingId: 21, goodsType: 'PHYSICAL', title: '二手键盘', unitPrice: 12900, status: 'ACTIVE', stockAvailable: 3 }
    ]
  })

  expect(state.listings[0].goodsTypeLabel).toBe('虚拟商品')
  expect(state.listings[1].goodsTypeLabel).toBe('实物商品')
})

expect(routes.some((route) => route.name === 'market')).toBe(true)
expect(routes.some((route) => route.name === 'marketAddresses')).toBe(true)
expect(routes.some((route) => route.name === 'adminMarketDisputes')).toBe(true)
```

- [ ] **Step 2: Run the targeted frontend tests to verify they fail**

Run: `cd frontend && npm test -- src/views/marketState.test.js src/router/index.test.js src/router/navigation.test.js src/api/services/marketService.test.js`

Expected: FAIL because unified market state, services, and routes do not exist yet.

- [ ] **Step 3: Implement unified services, routes, and goods-type-driven views**

```js
export async function listMarketListings(params = {}) {
  const resp = await http.get('/api/market/listings', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询统一市场商品列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createMarketOrder(payload) {
  const resp = await http.post('/api/market/orders', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建统一市场订单')
  return { data: data || {}, traceId }
}

export async function listMarketAddresses() {
  const resp = await http.get('/api/market/addresses')
  const { data, traceId } = unwrapResultBody(resp.data, '查询地址簿')
  return { data: Array.isArray(data) ? data : [], traceId }
}
```

```js
{
  path: '/market',
  name: 'market',
  component: MarketListView,
  meta: { title: '市场', subtitle: '一个入口浏览虚拟商品和实物商品。', navGroup: 'explore' }
},
{
  path: '/market/addresses',
  name: 'marketAddresses',
  component: MarketAddressesView,
  meta: { title: '收货地址', subtitle: '管理实物商品订单使用的收货地址。', navGroup: 'me', requiresAuth: true }
}
```

```vue
<template>
  <div class="page market-page">
    <UiBreadcrumb />
    <section class="market-hero">
      <span class="market-kicker">统一市场</span>
      <h1>同一个市场入口，同时浏览虚拟商品和实物商品</h1>
    </section>
    <article v-for="item in state.listings" :key="item.listingId" class="market-row">
      <strong>{{ item.title }}</strong>
      <span class="market-pill">{{ item.goodsTypeLabel }}</span>
      <span v-if="item.goodsType === 'VIRTUAL'">{{ item.deliveryLabel }}</span>
      <span v-else>{{ item.shipmentLabel }}</span>
    </article>
  </div>
</template>
```

- [ ] **Step 4: Run the targeted frontend tests to verify they pass**

Run: `cd frontend && npm test -- src/views/marketState.test.js src/router/index.test.js src/router/navigation.test.js src/api/services/marketService.test.js src/views/MarketViews.test.js src/views/MarketOrderViews.test.js src/views/MarketSellerViews.test.js src/views/MarketAddressesView.test.js`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/services/marketService.js \
  frontend/src/api/services/marketService.test.js \
  frontend/src/views/MarketListView.vue \
  frontend/src/views/MarketDetailView.vue \
  frontend/src/views/MarketPublishView.vue \
  frontend/src/views/MarketMyListingsView.vue \
  frontend/src/views/MarketInventoryView.vue \
  frontend/src/views/MarketBuyingOrdersView.vue \
  frontend/src/views/MarketSellingOrdersView.vue \
  frontend/src/views/MarketOrderDetailView.vue \
  frontend/src/views/MarketAddressesView.vue \
  frontend/src/views/AdminMarketDisputesView.vue \
  frontend/src/views/marketState.js \
  frontend/src/views/marketState.test.js \
  frontend/src/views/MarketViews.test.js \
  frontend/src/views/MarketOrderViews.test.js \
  frontend/src/views/MarketSellerViews.test.js \
  frontend/src/views/MarketAddressesView.test.js \
  frontend/src/router/index.js \
  frontend/src/router/navigation.js \
  frontend/src/router/index.test.js \
  frontend/src/router/navigation.test.js \
  frontend/src/styles/pages.css
git rm frontend/src/api/services/virtualMarketService.js \
  frontend/src/api/services/virtualMarketService.test.js \
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
  frontend/src/views/VirtualMarketOrderViews.test.js \
  frontend/src/views/VirtualMarketSellerViews.test.js
git commit -m "feat: replace virtual frontend with unified market surface"
```

---

### Task 8: Run Full Verification And Sync Phase 3 Back To PR #13

**Files:**
- No planned code edits; verification and branch sync only.

- [ ] **Step 1: Run the focused backend market suite**

Run: `cd backend && mvn -pl community-app -am -Dtest=MarketPersistenceTest,MarketListingServiceTest,MarketAddressServiceTest,MarketOrderServiceTest,MarketDisputeServiceTest,MarketControllerTest,AdminMarketControllerTest,MarketOrderAutoConfirmHandlerTest,WalletMarketActionServiceTest,DomainBoundaryArchTest -Dsurefire.failIfNoSpecifiedTests=false test`

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

- [ ] **Step 5: Verify the phase 3 worktree is clean**

Run: `git status --short`

Expected: no output

- [ ] **Step 6: Fast-forward the PR #13 branch to the verified phase 3 branch**

Run: `git -C /home/feng/code/project/community/.worktrees/open-wallet-phase2-virtual-market merge --ff-only open-wallet-phase3-physical-market`

Expected: fast-forward succeeds with no conflicts

- [ ] **Step 7: Push the updated PR #13 branch**

Run: `git -C /home/feng/code/project/community/.worktrees/open-wallet-phase2-virtual-market push origin open-wallet-phase2-virtual-market`

Expected: remote branch updates successfully and PR #13 reflects the phase 3 commits

---

## Self-Review

### Spec coverage

- Unified market with `goodsType` marker: covered by Tasks 1, 2, 3, 6, and 7.
- One main listing table and one main order table: covered by Task 1.
- Virtual goods behavior preserved on unified model: covered by Tasks 2, 3, 4, 5, and 7.
- Physical goods address, shipment, confirm, dispute, and cancel-before-ship: covered by Tasks 2, 3, 4, 5, and 7.
- Wallet-only escrow, release, and refund: covered by Tasks 3, 4, and 5.
- Unified auto-confirm with goods-type-specific timing: covered by Task 5.
- No preservation of historical `virtual_*` data: covered by Task 1 and Task 6 through direct convergence, not migration.
- Sync final result back into PR #13 branch: covered by Task 8.

### Placeholder scan

- No `TODO`, `TBD`, or “similar to Task N” placeholders remain.
- Every code-changing task includes exact file paths, example test code, implementation snippets, and exact commands.

### Type consistency

- Unified product discriminator consistently uses `goodsType` in DTOs and `goods_type` in persistence.
- Unified persistence consistently uses `market_listing`, `market_order`, `market_inventory_unit`, `market_dispute`, `market_address`, and `market_shipment`.
- Unified scheduler naming consistently uses `MarketOrderAutoConfirmActionApi` and `MarketOrderAutoConfirmHandler`.
- Unified transport and page naming consistently uses `Market*` rather than `Virtual*`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-04-community-open-wallet-phase3-physical-market.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
