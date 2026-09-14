package com.nowcoder.community.im.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.social.application.BlockApplicationService;
import com.nowcoder.community.social.application.BlockApplicationService.BlockCommand;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.nowcoder.community.support.ImInternalControllerTestSupport.bearer;
import static com.nowcoder.community.support.ImInternalControllerTestSupport.insertUser;
import static com.nowcoder.community.support.ImInternalControllerTestSupport.internalBearer;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImPrivateMessageDecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockApplicationService blockApplicationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void privateMessageDecisionShouldRequireInternalScope() throws Exception {
        UUID fromUserId = uuid(7);
        UUID toUserId = uuid(8);
        insertUser(userMapper, userRepository, fromUserId, "u7");
        insertUser(userMapper, userRepository, toUserId, "u8");

        mockMvc.perform(get("/internal/im/realtime/projections/private-message-decision")
                        .header("Authorization", bearer(jwtProperties, fromUserId))
                        .param("fromUserId", fromUserId.toString())
                        .param("toUserId", toUserId.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/im/realtime/projections/private-message-decision")
                        .header("Authorization", internalBearer(jwtProperties, fromUserId))
                        .param("fromUserId", fromUserId.toString())
                        .param("toUserId", toUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reasonCode").value("allowed"));
    }

    @Test
    void privateMessageDecisionShouldUseOwnerBlockState() throws Exception {
        UUID fromUserId = uuid(7);
        UUID toUserId = uuid(8);
        insertUser(userMapper, userRepository, fromUserId, "u7");
        insertUser(userMapper, userRepository, toUserId, "u8");
        blockApplicationService.block(new BlockCommand(toUserId, fromUserId));

        mockMvc.perform(get("/internal/im/realtime/projections/private-message-decision")
                        .header("Authorization", internalBearer(jwtProperties, fromUserId))
                        .param("fromUserId", fromUserId.toString())
                        .param("toUserId", toUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.reasonCode").value("policy_denied"))
                .andExpect(jsonPath("$.message").value("用户已拉黑"));
    }
}
