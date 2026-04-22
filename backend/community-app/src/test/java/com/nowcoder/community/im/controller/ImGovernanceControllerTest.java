package com.nowcoder.community.im.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.im.governance.action.PrivateMessageGovernanceActionApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImGovernanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrivateMessageGovernanceActionApi governanceActionApi;

    @Test
    void validateSendPrivateMessageShouldDelegateToPrivateMessageGovernanceAction() throws Exception {
        UUID fromUserId = uuid(7);
        UUID toUserId = uuid(9);
        mockMvc.perform(post("/api/im-governance/private-messages/validate")
                        .with(jwt().jwt(jwt -> jwt.subject(fromUserId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": "%s"
                                }
                                """.formatted(toUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"));

        verify(governanceActionApi).validateCanSendPrivateMessage(fromUserId, toUserId);
    }
}
