package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.application.command.UpdateMarketListingCommand;
import com.nowcoder.community.market.controller.dto.AddMarketInventoryBatchRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketListingApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketListingApplicationService marketListingService;

    @Autowired
    private MarketQueryApplicationService marketQueryService;

    @Autowired
    private MarketInventoryApplicationService marketInventoryService;

    @Autowired
    private MarketListingRepository marketListingRepository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_address");
        jdbcTemplate.update("delete from market_listing");
    }

    @Test
    void createPhysicalListingShouldPersistGoodsTypeWithoutVirtualOnlyFields() {
        var sellerUserId = uuid(7);
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(3);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);

        MarketListingResult response = marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null));

        assertThat(response.goodsType()).isEqualTo("PHYSICAL");
        assertThat(response.deliveryMode()).isNull();
        assertThat(response.stockAvailable()).isEqualTo(3);
    }

    @Test
    void sellerListingQueryShouldOnlyReturnOwnedListings() {
        var firstSellerId = uuid(7);
        var secondSellerId = uuid(8);
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(3);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);

        marketListingService.createListing(MarketTestCommands.listingCommand(firstSellerId, request, null));
        marketListingService.createListing(MarketTestCommands.listingCommand(secondSellerId, request, null));

        assertThat(marketQueryService.listSellerListings(firstSellerId))
                .extracting(MarketListingResult::sellerUserId)
                .containsExactly(firstSellerId);
    }

    @Test
    void resumeListingShouldKeepPhysicalSoldOutListingSoldOutWhenStockIsStillZero() {
        UUID sellerUserId = uuid(7);
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(1);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);

        UUID listingId = marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null)).listingId();
        jdbcTemplate.update(
                "update market_listing set stock_available = 0, status = 'SOLD_OUT' where listing_id = ?",
                listingId
        );

        marketListingService.pauseListing(sellerUserId, listingId);
        MarketListingResult resumed = marketListingService.resumeListing(sellerUserId, listingId);

        assertThat(resumed.status()).isEqualTo("SOLD_OUT");
        assertThat(resumed.stockAvailable()).isEqualTo(0);
    }

    @Test
    void staleStatusTransitionShouldNotOverwriteCurrentStatus() {
        UUID sellerUserId = uuid(7);
        UUID listingId = marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, physicalListingRequest(), null)
        ).listingId();

        MarketListingRepository.StatusTransitionResult result = marketListingRepository.transitionStatus(
                listingId,
                sellerUserId,
                "PAUSED",
                "CLOSED"
        );

        assertThat(result).isEqualTo(MarketListingRepository.StatusTransitionResult.STALE);
        assertThat(marketQueryService.getListingDetail(listingId).status()).isEqualTo("ACTIVE");
    }

    @Test
    void staleStockAdjustmentShouldNotOverwriteCurrentStatusOrStock() {
        UUID sellerUserId = uuid(7);
        UUID listingId = marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, physicalListingRequest(), null)
        ).listingId();

        int updated = marketListingRepository.adjustStock(
                listingId,
                sellerUserId,
                -1,
                -1,
                "PAUSED",
                "SOLD_OUT"
        );

        assertThat(updated).isZero();
        assertThat(marketQueryService.getListingDetail(listingId))
                .satisfies(listing -> {
                    assertThat(listing.status()).isEqualTo("ACTIVE");
                    assertThat(listing.stockTotal()).isEqualTo(3);
                    assertThat(listing.stockAvailable()).isEqualTo(3);
                });
    }

    @Test
    void staleStatusTransitionShouldMapToMarketConflictError() {
        UUID sellerUserId = uuid(7);
        UUID listingId = uuid(71);
        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setStatus("ACTIVE");
        MarketListingRepository repository = mock(MarketListingRepository.class);
        when(repository.lockById(listingId)).thenReturn(listing);
        when(repository.transitionStatus(listingId, sellerUserId, "ACTIVE", "PAUSED"))
                .thenReturn(MarketListingRepository.StatusTransitionResult.STALE);
        MarketListingApplicationService service = new MarketListingApplicationService(
                repository,
                mock(MarketInventoryApplicationService.class),
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.pauseListing(sellerUserId, listingId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.LISTING_TRANSITION_CONFLICT));
    }

    @Test
    void closedListingShouldRejectEditableFieldChanges() {
        UUID sellerUserId = uuid(7);
        UUID listingId = marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, physicalListingRequest(), null)
        ).listingId();
        marketListingService.closeListing(sellerUserId, listingId);

        UpdateMarketListingCommand command = new UpdateMarketListingCommand(
                sellerUserId,
                listingId,
                "changed title",
                "changed description",
                25_800L,
                1,
                2
        );

        assertThatThrownBy(() -> marketListingService.updateListing(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.LISTING_TRANSITION_CONFLICT));
        assertThat(marketQueryService.getListingDetail(listingId))
                .satisfies(listing -> {
                    assertThat(listing.status()).isEqualTo("CLOSED");
                    assertThat(listing.title()).isEqualTo("二手键盘");
                    assertThat(listing.unitPrice()).isEqualTo(12_900L);
                });
    }

    @Test
    void pausedListingShouldRemainPausedWhenInventoryReachesZeroAndIsReplenished() {
        UUID sellerUserId = uuid(7);
        UUID listingId = createPreloadedListing(sellerUserId);
        marketListingService.pauseListing(sellerUserId, listingId);

        changeAllInventory(listingId, sellerUserId, "PAUSED");

        assertListingStatusAndStock(listingId, "PAUSED", 1);
    }

    @Test
    void closedListingShouldRemainClosedWhenInventoryReachesZeroAndIsReplenished() {
        UUID sellerUserId = uuid(7);
        UUID listingId = createPreloadedListing(sellerUserId);
        marketListingService.closeListing(sellerUserId, listingId);

        changeAllInventory(listingId, sellerUserId, "CLOSED");

        assertListingStatusAndStock(listingId, "CLOSED", 1);
        assertThat(marketQueryService.listPublicListings())
                .extracting(MarketListingResult::listingId)
                .doesNotContain(listingId);
    }

    @Test
    void createListingShouldRejectNullCommand() {
        assertThatThrownBy(() -> marketListingService.createListing(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    private CreateMarketListingRequest physicalListingRequest() {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(3);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);
        return request;
    }

    private UUID createPreloadedListing(UUID sellerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("VIRTUAL");
        request.setTitle("兑换码");
        request.setDescription("单个预加载兑换码");
        request.setUnitPrice(100L);
        request.setDeliveryMode("PRELOADED");
        request.setStockMode("FINITE");
        request.setStockTotal(1);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);

        AddMarketInventoryBatchRequest inventory = new AddMarketInventoryBatchRequest();
        inventory.setPayloadType("TEXT");
        inventory.setPayloads(List.of("initial-code"));
        return marketListingService.createListing(
                MarketTestCommands.listingCommand(sellerUserId, request, inventory)
        ).listingId();
    }

    private void changeAllInventory(UUID listingId, UUID sellerUserId, String expectedStatus) {
        UUID inventoryUnitId = marketInventoryService.listInventory(listingId, sellerUserId).get(0).inventoryUnitId();
        marketInventoryService.invalidateInventory(inventoryUnitId, sellerUserId);
        assertListingStatusAndStock(listingId, expectedStatus, 0);

        marketInventoryService.appendInventory(new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "TEXT",
                List.of("replacement-code")
        ));
    }

    private void assertListingStatusAndStock(UUID listingId, String status, int stockAvailable) {
        assertThat(marketQueryService.getListingDetail(listingId))
                .satisfies(listing -> {
                    assertThat(listing.status()).isEqualTo(status);
                    assertThat(listing.stockAvailable()).isEqualTo(stockAvailable);
                });
    }
}
