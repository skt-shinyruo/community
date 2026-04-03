package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.entity.VirtualInventoryUnit;
import com.nowcoder.community.market.entity.VirtualListing;
import com.nowcoder.community.market.mapper.VirtualInventoryUnitMapper;
import com.nowcoder.community.market.mapper.VirtualListingMapper;
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
class VirtualMarketPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VirtualListingMapper virtualListingMapper;

    @Autowired
    private VirtualInventoryUnitMapper virtualInventoryUnitMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from virtual_dispute");
        jdbcTemplate.update("delete from virtual_delivery");
        jdbcTemplate.update("delete from virtual_order");
        jdbcTemplate.update("delete from virtual_inventory_unit");
        jdbcTemplate.update("delete from virtual_listing");
    }

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
