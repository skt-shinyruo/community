package com.nowcoder.community.im.core.controller;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

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
    private RoomMembershipService roomMembershipService;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

    @Test
    void roomMembershipSnapshotShouldPageByRoomAndUser() throws Exception {
        UUID owner = uuid(1);
        UUID roomId = roomMembershipService.createRoom(owner, "room-a");
        roomMembershipService.joinRoom(uuid(2), roomId);

        mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                        .header("Authorization", bearer(owner))
                        .queryParam("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.entries[0].userId").value(owner.toString()));
    }

    private String bearer(UUID userId) throws Exception {
        String token = signHs256(jwtSecret, jwtIssuer, String.valueOf(userId), Instant.now().plusSeconds(120));
        return "Bearer " + token;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static String signHs256(String secret, String issuer, String sub, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(Date.from(exp))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
