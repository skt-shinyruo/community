package com.nowcoder.community.im.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.service.UserModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    private UserModerationService userModerationService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

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
        insertUser(mutedUserId, "u7");
        insertUser(bannedUserId, "u8");
        userModerationService.applyModeration(mutedUserId, "mute", 300);
        userModerationService.applyModeration(bannedUserId, "ban", 300);

        mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                        .header("Authorization", internalBearer(mutedUserId))
                        .param("afterUserId", uuid(2).toString())
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
        blockService.block(uuid(1), uuid(2));
        blockService.block(uuid(1), uuid(3));
        blockService.block(uuid(2), uuid(1));

        mockMvc.perform(get("/internal/im/realtime/projections/block-relations")
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
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/internal/im/realtime/projections/block-relations")
                        .header("Authorization", internalBearer(uuid(7)))
                        .param("afterBlockerUserId", uuid(1).toString())
                        .param("afterBlockedUserId", uuid(3).toString())
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

    private void insertUser(UUID userId, String username) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setSalt("");
        user.setEmail(username + "@example.com");
        user.setType(0);
        user.setStatus(0);
        user.setHeaderUrl("/avatar.png");
        user.setCreateTime(new Date());
        userMapper.insertUser(user);
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
