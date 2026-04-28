package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.application.result.MarketListingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

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
}
