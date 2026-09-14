package com.nowcoder.community.im.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.social.application.BlockApplicationService;
import com.nowcoder.community.social.application.BlockApplicationService.BlockCommand;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import com.nowcoder.community.user.api.action.UserModerationActionApi.ApplyModerationCommand;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.nowcoder.community.support.ImInternalControllerTestSupport.bearer;
import static com.nowcoder.community.support.ImInternalControllerTestSupport.insertUser;
import static com.nowcoder.community.support.ImInternalControllerTestSupport.internalBearer;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImPolicySnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserModerationActionApi userModerationActionApi;

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
    void projectionEndpointsShouldRequireInternalScope() throws Exception {
        insertUser(userMapper, userRepository, uuid(7), "u7");

        mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                        .header("Authorization", bearer(jwtProperties, uuid(7)))
                        .param("limit", "10"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                        .header("Authorization", internalBearer(jwtProperties, uuid(7)))
                        .param("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void userMessagingPolicySnapshotShouldExposeMuteBanAndExistence() throws Exception {
        UUID mutedUserId = uuid(7);
        UUID bannedUserId = uuid(8);
        UUID actorUserId = uuid(99);
        insertUser(userMapper, userRepository, mutedUserId, "u7");
        insertUser(userMapper, userRepository, bannedUserId, "u8");
        insertUser(userMapper, userRepository, actorUserId, "moderation-admin", 1, 1);
        userModerationActionApi.applyModeration(new ApplyModerationCommand(actorUserId, mutedUserId, "mute", 300));
        userModerationActionApi.applyModeration(new ApplyModerationCommand(actorUserId, bannedUserId, "ban", 300));

        mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                        .header("Authorization", internalBearer(jwtProperties, mutedUserId))
                        .param("afterUserId", uuid(2).toString())
                        .param("snapshotVersion", Long.toString(userRepository.currentUserPolicyVersion()))
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].userId").value(mutedUserId.toString()))
                .andExpect(jsonPath("$.entries[0].userExists").value(true))
                .andExpect(jsonPath("$.entries[0].suspended").value(false))
                .andExpect(jsonPath("$.entries[0].muted").value(true))
                .andExpect(jsonPath("$.entries[0].muteUntil").value(greaterThan(0L)))
                .andExpect(jsonPath("$.entries[0].canSendPrivate").value(false))
                .andExpect(jsonPath("$.entries[1].userId").value(bannedUserId.toString()))
                .andExpect(jsonPath("$.entries[1].userExists").value(true))
                .andExpect(jsonPath("$.entries[1].suspended").value(true))
                .andExpect(jsonPath("$.entries[1].muted").value(false))
                .andExpect(jsonPath("$.entries[1].banUntil").value(greaterThan(0L)))
                .andExpect(jsonPath("$.entries[1].canSendPrivate").value(false));
    }

    @Test
    void userBlockRelationSnapshotShouldPageBlockPairs() throws Exception {
        blockApplicationService.block(new BlockCommand(uuid(1), uuid(2)));
        blockApplicationService.block(new BlockCommand(uuid(1), uuid(3)));
        blockApplicationService.block(new BlockCommand(uuid(2), uuid(1)));

        MvcResult firstPage = mockMvc.perform(get("/internal/im/realtime/projections/block-relations")
                        .header("Authorization", internalBearer(jwtProperties, uuid(7)))
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].blockerUserId").value(uuid(1).toString()))
                .andExpect(jsonPath("$.entries[0].blockedUserId").value(uuid(2).toString()))
                .andExpect(jsonPath("$.entries[0].active").value(true))
                .andExpect(jsonPath("$.entries[1].blockerUserId").value(uuid(1).toString()))
                .andExpect(jsonPath("$.entries[1].blockedUserId").value(uuid(3).toString()))
                .andExpect(jsonPath("$.entries[1].active").value(true))
                .andExpect(jsonPath("$.nextBlockerUserId").value(uuid(1).toString()))
                .andExpect(jsonPath("$.nextBlockedUserId").value(uuid(3).toString()))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        long snapshotVersion = objectMapper.readTree(firstPage.getResponse().getContentAsByteArray())
                .path("snapshotHighWatermark")
                .asLong();

        mockMvc.perform(get("/internal/im/realtime/projections/block-relations")
                        .header("Authorization", internalBearer(jwtProperties, uuid(7)))
                        .param("afterBlockerUserId", uuid(1).toString())
                        .param("afterBlockedUserId", uuid(3).toString())
                        .param("snapshotVersion", Long.toString(snapshotVersion))
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].blockerUserId").value(uuid(2).toString()))
                .andExpect(jsonPath("$.entries[0].blockedUserId").value(uuid(1).toString()))
                .andExpect(jsonPath("$.entries[0].active").value(true))
                .andExpect(jsonPath("$.nextBlockerUserId").value(uuid(2).toString()))
                .andExpect(jsonPath("$.nextBlockedUserId").value(uuid(1).toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }
}
