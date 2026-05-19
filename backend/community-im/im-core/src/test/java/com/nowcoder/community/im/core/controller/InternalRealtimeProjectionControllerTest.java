package com.nowcoder.community.im.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.im.core.application.RoomApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InternalRealtimeProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomApplicationService roomApplicationService;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

    @Test
    void projectionEndpointShouldRequireInternalScope() throws Exception {
        UUID owner = uuid(1);
        UUID roomId = roomApplicationService.createRoom(owner, "room-a").roomId();

        mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                        .header("Authorization", bearer(owner))
                        .queryParam("limit", "10"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                        .header("Authorization", internalBearer(owner))
                        .queryParam("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.entries[0].userId").value(owner.toString()));
    }

    @Test
    void roomMembershipSnapshotShouldPageByRoomAndUserAndReturnFollowUpCursor() throws Exception {
        UUID owner = uuid(1);
        UUID member2 = uuid(2);
        UUID member3 = uuid(3);
        UUID roomId = roomApplicationService.createRoom(owner, "room-a").roomId();
        roomApplicationService.joinRoom(member2, roomId);
        roomApplicationService.joinRoom(member3, roomId);

        MvcResult firstPage = mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                        .header("Authorization", internalBearer(owner))
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.entries[0].userId").value(owner.toString()))
                .andExpect(jsonPath("$.entries[1].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.entries[1].userId").value(member2.toString()))
                .andExpect(jsonPath("$.nextRoomId").value(roomId.toString()))
                .andExpect(jsonPath("$.nextUserId").value(member2.toString()))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();

        JsonNode pageJson = objectMapper.readTree(firstPage.getResponse().getContentAsString());

        mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                        .header("Authorization", internalBearer(owner))
                        .queryParam("afterRoomId", pageJson.path("nextRoomId").asText())
                        .queryParam("afterUserId", pageJson.path("nextUserId").asText())
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.entries[0].userId").value(member3.toString()))
                .andExpect(jsonPath("$.nextRoomId").value(roomId.toString()))
                .andExpect(jsonPath("$.nextUserId").value(member3.toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void roomMembershipSnapshotShouldRejectPartialCursor() throws Exception {
        UUID owner = uuid(1);
        UUID roomId = roomApplicationService.createRoom(owner, "room-a").roomId();

        assertThatThrownBy(() -> roomApplicationService.membershipSnapshot(roomId, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("afterRoomId and afterUserId must be provided together");

        mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                        .header("Authorization", internalBearer(owner))
                        .queryParam("afterRoomId", roomId.toString())
                        .queryParam("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void roomMembershipSnapshotShouldNotReportHasMoreWhenLimitExactlyMatchesRowCount() throws Exception {
        UUID owner = uuid(1);
        UUID member2 = uuid(2);
        UUID roomId = roomApplicationService.createRoom(owner, "room-a").roomId();
        roomApplicationService.joinRoom(member2, roomId);

        mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                        .header("Authorization", internalBearer(owner))
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].userId").value(owner.toString()))
                .andExpect(jsonPath("$.entries[1].userId").value(member2.toString()))
                .andExpect(jsonPath("$.nextRoomId").value(roomId.toString()))
                .andExpect(jsonPath("$.nextUserId").value(member2.toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    private String bearer(UUID userId) throws Exception {
        String token = signHs256(jwtSecret, jwtIssuer, String.valueOf(userId), null, Instant.now().plusSeconds(120));
        return "Bearer " + token;
    }

    private String internalBearer(UUID userId) throws Exception {
        String token = signHs256(
                jwtSecret,
                jwtIssuer,
                String.valueOf(userId),
                "im.realtime.internal",
                Instant.now().plusSeconds(120)
        );
        return "Bearer " + token;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static String signHs256(String secret, String issuer, String sub, String scope, Instant exp) throws Exception {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(Date.from(exp));
        if (scope != null && !scope.isBlank()) {
            claimsBuilder.claim("scope", scope);
        }
        JWTClaimsSet claims = claimsBuilder.build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
