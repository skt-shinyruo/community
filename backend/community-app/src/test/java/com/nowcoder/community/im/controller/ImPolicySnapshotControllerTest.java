package com.nowcoder.community.im.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.social.application.BlockApplicationService;
import com.nowcoder.community.social.application.command.BlockCommand;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import com.nowcoder.community.user.api.action.UserModerationActionApi.ApplyModerationCommand;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.infrastructure.persistence.dataobject.UserDataObject;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Date;
import java.util.UUID;

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

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void projectionEndpointsShouldRequireInternalScope() throws Exception {
        insertUser(uuid(7), "u7");

        mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                        .header("Authorization", bearer(uuid(7)))
                        .param("limit", "10"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                        .header("Authorization", internalBearer(uuid(7)))
                        .param("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void userMessagingPolicySnapshotShouldExposeMuteBanAndExistence() throws Exception {
        UUID mutedUserId = uuid(7);
        UUID bannedUserId = uuid(8);
        UUID actorUserId = uuid(99);
        insertUser(mutedUserId, "u7");
        insertUser(bannedUserId, "u8");
        insertUser(actorUserId, "moderation-admin", 1, 1);
        userModerationActionApi.applyModeration(new ApplyModerationCommand(actorUserId, mutedUserId, "mute", 300));
        userModerationActionApi.applyModeration(new ApplyModerationCommand(actorUserId, bannedUserId, "ban", 300));

        mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                        .header("Authorization", internalBearer(mutedUserId))
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
                        .header("Authorization", internalBearer(uuid(7)))
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
                        .header("Authorization", internalBearer(uuid(7)))
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

    @Test
    void privateMessageDecisionShouldRequireInternalScopeAndUseOwnerState() throws Exception {
        UUID fromUserId = uuid(7);
        UUID toUserId = uuid(8);
        insertUser(fromUserId, "u7");
        insertUser(toUserId, "u8");
        blockApplicationService.block(new BlockCommand(toUserId, fromUserId));

        mockMvc.perform(get("/internal/im/realtime/projections/private-message-decision")
                        .header("Authorization", bearer(fromUserId))
                        .param("fromUserId", fromUserId.toString())
                        .param("toUserId", toUserId.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/im/realtime/projections/private-message-decision")
                        .header("Authorization", internalBearer(fromUserId))
                        .param("fromUserId", fromUserId.toString())
                        .param("toUserId", toUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.reasonCode").value("policy_denied"))
                .andExpect(jsonPath("$.message").value("用户已拉黑"));
    }

    private void insertUser(UUID userId, String username) {
        insertUser(userId, username, 0, 0);
    }

    private void insertUser(UUID userId, String username, int type, int status) {
        UserDataObject user = new UserDataObject();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setSalt("");
        user.setEmail(username + "@example.com");
        user.setType(type);
        user.setStatus(status);
        user.setHeaderUrl("/avatar.png");
        user.setCreateTime(new Date());
        user.setPolicyVersion(userRepository.nextUserPolicyVersion(userId));
        userMapper.insertUser(user);
        userMapper.insertPolicyVersionLog(user.getPolicyVersion(), userId, true, null, null);
    }

    private String bearer(UUID userId) throws Exception {
        return serviceBearer(userId, null);
    }

    private String internalBearer(UUID userId) throws Exception {
        return serviceBearer(userId, "im.realtime.internal");
    }

    private String serviceBearer(UUID userId, String scope) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(JwtCodecs.resolvedIssuer(jwtProperties))
                .audience(List.of("community-app"))
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(120));
        if (scope != null && !scope.isBlank()) {
            claimsBuilder.claim("scope", scope);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type(JwtCodecs.SERVICE_TOKEN_TYPE)
                .build();
        String token = JwtCodecs.serviceTokenEncoder(jwtProperties)
                .encode(JwtEncoderParameters.from(header, claimsBuilder.build()))
                .getTokenValue();
        return "Bearer " + token;
    }
}
