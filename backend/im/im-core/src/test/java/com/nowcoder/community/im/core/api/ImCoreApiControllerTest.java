package com.nowcoder.community.im.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.im.contracts.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.contracts.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.core.service.PrivateMessageService;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import com.nowcoder.community.im.core.service.RoomMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImCoreApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomMembershipService roomMembershipService;

    @Autowired
    private RoomMessageService roomMessageService;

    @Autowired
    private PrivateMessageService privateMessageService;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    @Test
    void api_should_require_authentication() throws Exception {
        mockMvc.perform(get("/api/im/unread/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conversation_history_and_markRead_should_enforce_membership_and_update_watermark() throws Exception {
        int user1 = 1;
        int user2 = 2;
        int user3 = 3;
        String conversationId = "1_2";

        privateMessageService.persist(new SendPrivateTextCommandV1(
                "req-1",
                "c1",
                user1,
                user2,
                conversationId,
                "hello",
                System.currentTimeMillis()
        ));

        String res1 = mockMvc.perform(get("/api/im/conversations/{id}/messages", conversationId)
                        .param("afterSeq", "0")
                        .param("limit", "50")
                        .header("Authorization", bearer(user1)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json1 = objectMapper.readTree(res1);
        assertThat(json1.path("code").asInt(-1)).isEqualTo(0);
        JsonNode data1 = json1.path("data");
        assertThat(data1.path("conversationId").asText("")).isEqualTo(conversationId);
        assertThat(data1.path("items").isArray()).isTrue();
        assertThat(data1.path("items").size()).isEqualTo(1);
        assertThat(data1.path("items").get(0).path("content").asText("")).isEqualTo("hello");
        assertThat(data1.path("nextAfterSeq").asLong()).isEqualTo(1L);
        assertThat(data1.path("lastReadSeq").asLong()).isEqualTo(1L); // sender has read their own outgoing

        mockMvc.perform(post("/api/im/conversations/{id}/read", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadSeq\":1}")
                        .header("Authorization", bearer(user2)))
                .andExpect(status().isOk());

        String res2 = mockMvc.perform(get("/api/im/conversations/{id}/messages", conversationId)
                        .param("afterSeq", "0")
                        .param("limit", "50")
                        .header("Authorization", bearer(user2)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json2 = objectMapper.readTree(res2);
        assertThat(json2.path("code").asInt(-1)).isEqualTo(0);
        assertThat(json2.path("data").path("lastReadSeq").asLong()).isEqualTo(1L);

        mockMvc.perform(get("/api/im/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(user3)))
                .andExpect(status().isForbidden());
    }

    @Test
    void room_history_and_markRead_should_require_membership() throws Exception {
        int owner = 1;
        int member = 2;
        int outsider = 99;

        long roomId = roomMembershipService.createRoom(owner, "room");
        roomMembershipService.joinRoom(member, roomId);

        roomMessageService.persist(new SendRoomTextCommandV1(
                "req-1",
                "c1",
                owner,
                roomId,
                "hi room",
                System.currentTimeMillis()
        ));

        String res = mockMvc.perform(get("/api/im/rooms/{roomId}/messages", roomId)
                        .param("afterSeq", "0")
                        .param("limit", "50")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(res);
        assertThat(json.path("code").asInt(-1)).isEqualTo(0);
        JsonNode roomData = json.path("data");
        assertThat(roomData.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(roomData.path("items").isArray()).isTrue();
        assertThat(roomData.path("items").size()).isEqualTo(1);
        assertThat(roomData.path("items").get(0).path("content").asText("")).isEqualTo("hi room");
        assertThat(roomData.path("nextAfterSeq").asLong()).isEqualTo(1L);

        mockMvc.perform(post("/api/im/rooms/{roomId}/read", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadSeq\":1}")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk());

        String res2 = mockMvc.perform(get("/api/im/rooms/{roomId}/messages", roomId)
                        .param("afterSeq", "0")
                        .param("limit", "50")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json2 = objectMapper.readTree(res2);
        assertThat(json2.path("code").asInt(-1)).isEqualTo(0);
        assertThat(json2.path("data").path("lastReadSeq").asLong()).isEqualTo(1L);

        mockMvc.perform(get("/api/im/rooms/{roomId}/messages", roomId)
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unread_summary_should_reflect_room_and_conversation_unread() throws Exception {
        int sender = 1;
        int receiver = 2;
        String conversationId = "1_2";

        long roomId = roomMembershipService.createRoom(sender, "room");
        roomMembershipService.joinRoom(receiver, roomId);

        privateMessageService.persist(new SendPrivateTextCommandV1(
                "req-p1",
                "cp1",
                sender,
                receiver,
                conversationId,
                "hello",
                System.currentTimeMillis()
        ));
        roomMessageService.persist(new SendRoomTextCommandV1(
                "req-r1",
                "cr1",
                sender,
                roomId,
                "hi room",
                System.currentTimeMillis()
        ));

        String receiverRes = mockMvc.perform(get("/api/im/unread/summary")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode receiverJson = objectMapper.readTree(receiverRes);
        assertThat(receiverJson.path("code").asInt(-1)).isEqualTo(0);
        JsonNode receiverData = receiverJson.path("data");
        assertThat(receiverData.path("rooms").isArray()).isTrue();
        assertThat(receiverData.path("conversations").isArray()).isTrue();

        JsonNode roomItem = receiverData.path("rooms").get(0);
        assertThat(roomItem.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(roomItem.path("lastSeq").asLong()).isEqualTo(1L);
        assertThat(roomItem.path("lastReadSeq").asLong()).isEqualTo(0L);
        assertThat(roomItem.path("unreadCount").asLong()).isEqualTo(1L);

        JsonNode convItem = receiverData.path("conversations").get(0);
        assertThat(convItem.path("conversationId").asText("")).isEqualTo(conversationId);
        assertThat(convItem.path("lastSeq").asLong()).isEqualTo(1L);
        assertThat(convItem.path("lastReadSeq").asLong()).isEqualTo(0L);
        assertThat(convItem.path("unreadCount").asLong()).isEqualTo(1L);

        String senderRes = mockMvc.perform(get("/api/im/unread/summary")
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode senderJson = objectMapper.readTree(senderRes);
        assertThat(senderJson.path("code").asInt(-1)).isEqualTo(0);
        JsonNode senderData = senderJson.path("data");
        assertThat(senderData.path("rooms").get(0).path("unreadCount").asLong()).isEqualTo(0L);
        assertThat(senderData.path("conversations").get(0).path("unreadCount").asLong()).isEqualTo(0L);
    }

    @Test
    void internal_room_bootstrap_should_forbid_userId_mismatch() throws Exception {
        int user1 = 1;
        int user2 = 2;

        long roomId = roomMembershipService.createRoom(user1, "room");
        roomMembershipService.joinRoom(user2, roomId);

        String okRes = mockMvc.perform(get("/internal/im/realtime/users/{userId}/rooms", user1)
                        .header("Authorization", bearer(user1)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode okJson = objectMapper.readTree(okRes);
        assertThat(okJson.path("roomIds").isArray()).isTrue();
        boolean found = false;
        for (JsonNode n : okJson.path("roomIds")) {
            if (n != null && n.asLong() == roomId) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();

        mockMvc.perform(get("/internal/im/realtime/users/{userId}/rooms", user1)
                        .header("Authorization", bearer(user2)))
                .andExpect(status().isForbidden());
    }

    private String bearer(int userId) throws Exception {
        String token = signHs256(jwtSecret, String.valueOf(userId), Instant.now().plusSeconds(120));
        return "Bearer " + token;
    }

    private static String signHs256(String secret, String sub, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(Date.from(exp))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
