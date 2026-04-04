package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.dto.MarketListingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketListingServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketListingService marketListingService;

    @Autowired
    private MarketQueryService marketQueryService;

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
    void sellerListingQueryShouldOnlyReturnOwnedListings() {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(3);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);

        marketListingService.createListing(7, request, null);
        marketListingService.createListing(8, request, null);

        assertThat(marketQueryService.listSellerListings(7))
                .extracting(MarketListingResponse::sellerUserId)
                .containsExactly(7);
    }
}
