package com.nowcoder.community.message.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyMessageApiRemovalTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void conversationsEndpointShouldReturnNotFoundForAuthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/messages/conversations")
                        .with(jwt().jwt(jwt -> jwt.subject("7"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendEndpointShouldReturnNotFoundForAuthenticatedJsonRequest() throws Exception {
        mockMvc.perform(post("/api/messages")
                        .with(jwt().jwt(jwt -> jwt.subject("7")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": 9,
                                  "content": "hello"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
