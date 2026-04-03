package com.nowcoder.community.market.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.VirtualListingDetailResponse;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.security.MarketSecurityRules;
import com.nowcoder.community.market.service.VirtualInventoryService;
import com.nowcoder.community.market.service.VirtualListingService;
import com.nowcoder.community.market.service.VirtualMarketQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VirtualMarketController.class)
@Import({
        VirtualMarketController.class,
        MarketSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class VirtualMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VirtualListingService virtualListingService;

    @MockBean
    private VirtualInventoryService virtualInventoryService;

    @MockBean
    private VirtualMarketQueryService virtualMarketQueryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void publicListingApiShouldExposeActiveListingsWithoutAuthentication() throws Exception {
        VirtualListingResponse listing = new VirtualListingResponse(
                11L,
                7,
                "Netflix 卡密",
                "自动交付",
                1500L,
                "PRELOADED",
                "FINITE",
                2,
                2,
                1,
                2,
                "ACTIVE"
        );
        when(virtualMarketQueryService.listPublicListings()).thenReturn(List.of(listing));
        when(virtualMarketQueryService.getListingDetail(11L)).thenReturn(new VirtualListingDetailResponse(
                11L,
                7,
                "Netflix 卡密",
                "自动交付",
                1500L,
                "PRELOADED",
                "FINITE",
                2,
                2,
                1,
                2,
                "ACTIVE",
                new Date(),
                new Date()
        ));

        mockMvc.perform(get("/api/market/virtual/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].title").value("Netflix 卡密"));

        mockMvc.perform(get("/api/market/virtual/listings/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.listingId").value(11))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void createListingApiShouldRequireAuthenticationAndReturnCreatedListing() throws Exception {
        VirtualListingResponse listing = new VirtualListingResponse(
                11L,
                7,
                "Netflix 卡密",
                "自动交付",
                1500L,
                "PRELOADED",
                "FINITE",
                2,
                2,
                1,
                2,
                "ACTIVE"
        );
        when(virtualListingService.createListing(eq(7), any(CreateVirtualListingRequest.class), any(AddVirtualInventoryBatchRequest.class)))
                .thenReturn(listing);

        mockMvc.perform(post("/api/market/virtual/listings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Netflix 卡密",
                                  "description": "自动交付",
                                  "unitPrice": 1500,
                                  "deliveryMode": "PRELOADED",
                                  "stockMode": "FINITE",
                                  "stockTotal": 2,
                                  "minPurchaseQuantity": 1,
                                  "maxPurchaseQuantity": 2,
                                  "inventory": {
                                    "payloadType": "CODE",
                                    "payloads": ["NFX-001", "NFX-002"]
                                  }
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/market/virtual/listings")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("username", "seller7")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Netflix 卡密",
                                  "description": "自动交付",
                                  "unitPrice": 1500,
                                  "deliveryMode": "PRELOADED",
                                  "stockMode": "FINITE",
                                  "stockTotal": 2,
                                  "minPurchaseQuantity": 1,
                                  "maxPurchaseQuantity": 2,
                                  "inventory": {
                                    "payloadType": "CODE",
                                    "payloads": ["NFX-001", "NFX-002"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.listingId").value(11))
                .andExpect(jsonPath("$.data.stockAvailable").value(2));
    }
}
