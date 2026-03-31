package com.nowcoder.community.growth.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRewardOpsControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @Test
    void unsupportedAdminRewardActionShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/growth/admin/rewards/orders/action")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId": 102,
                                  "action": "ARCHIVE",
                                  "note": "invalid",
                                  "confirm": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingMissingRewardItemShouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/growth/admin/rewards/items")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "itemId": 999,
                                  "itemName": "missing",
                                  "itemDesc": "missing",
                                  "costBalance": 10,
                                  "stock": 1,
                                  "perUserLimit": 1,
                                  "fulfillmentMode": "AUTO",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
