package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class VirtualListingServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VirtualListingService virtualListingService;

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
        assertThat(response.stockTotal()).isEqualTo(2);
    }
}
