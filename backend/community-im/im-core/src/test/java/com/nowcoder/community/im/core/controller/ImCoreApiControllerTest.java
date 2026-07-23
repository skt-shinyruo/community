package com.nowcoder.community.im.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.im.core.application.ConversationApplicationService;
import com.nowcoder.community.im.core.application.PrivateMessageApplicationService;
import com.nowcoder.community.im.core.application.RoomApplicationService;
import com.nowcoder.community.im.core.application.RoomMessageApplicationService;
import com.nowcoder.community.im.core.application.result.ConversationResults;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    private RoomApplicationService roomApplicationService;

    @Autowired
    private RoomMessageApplicationService roomMessageApplicationService;

    @Autowired
    private PrivateMessageApplicationService privateMessageApplicationService;

    @SpyBean
    private ConversationApplicationService conversationApplicationService;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

    @MockBean
    private PrivateMessagePolicyVerifier privateMessagePolicyVerifier;

    @BeforeEach
    void setUp() {
        when(privateMessagePolicyVerifier.verify(any(UUID.class), any(UUID.class)))
                .thenReturn(PrivateMessagePolicyDecision.allow());
    }

    @Test
    void api_should_require_authentication() throws Exception {
        mockMvc.perform(get("/api/im/unread/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conversation_page_shouldDelegateCursorAndExposePageFields() throws Exception {
        UUID viewer = uuid(101);
        UUID peer = uuid(102);
        String conversationId = conversationId(viewer, peer);
        ConversationResults.Page page = new ConversationResults.Page(
                List.of(new ConversationResults.ListItem(
                        conversationId,
                        peer,
                        17L,
                        11L,
                        6L,
                        new ConversationResults.LastMessage(
                                uuid(103),
                                peer,
                                viewer,
                                "latest",
                                1_700_000_000_000L
                        )
                )),
                "next-cursor",
                true
        );
        doReturn(page).when(conversationApplicationService).listConversationPage(viewer, "cursor-value", 2);
        doReturn(page).when(conversationApplicationService).listConversationPage(viewer, "", 2);

        String response = mockMvc.perform(get("/api/im/conversations/page")
                        .param("cursor", "cursor-value")
                        .param("size", "2")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("items")).hasSize(1);
        assertThat(data.path("items").get(0).path("conversationId").asText()).isEqualTo(conversationId);
        assertThat(data.path("items").get(0).path("lastMessage").path("content").asText()).isEqualTo("latest");
        assertThat(data.path("nextCursor").asText()).isEqualTo("next-cursor");
        assertThat(data.path("hasMore").asBoolean()).isTrue();
        verify(conversationApplicationService).listConversationPage(viewer, "cursor-value", 2);

        mockMvc.perform(get("/api/im/conversations/page")
                        .param("size", "2")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk());

        verify(conversationApplicationService).listConversationPage(viewer, "", 2);
    }

    @Test
    void message_history_shouldDelegateBeforeSeqAndExposeHistoryFields() throws Exception {
        UUID viewer = uuid(111);
        UUID peer = uuid(112);
        String conversationId = conversationId(viewer, peer);
        ConversationResults.History history = new ConversationResults.History(
                conversationId,
                List.of(new ConversationResults.MessageItem(
                        conversationId,
                        17L,
                        uuid(113),
                        peer,
                        viewer,
                        "earlier",
                        "client-113",
                        1_700_000_001_000L
                )),
                17L,
                true,
                12L
        );
        doReturn(history).when(conversationApplicationService).listMessageHistory(viewer, conversationId, 18L, 2);
        doReturn(history).when(conversationApplicationService).listMessageHistory(viewer, conversationId, null, 2);

        String response = mockMvc.perform(get("/api/im/conversations/{conversationId}/messages/history", conversationId)
                        .param("beforeSeq", "18")
                        .param("limit", "2")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("conversationId").asText()).isEqualTo(conversationId);
        assertThat(data.path("items")).hasSize(1);
        assertThat(data.path("items").get(0).path("seq").asLong()).isEqualTo(17L);
        assertThat(data.path("items").get(0).path("content").asText()).isEqualTo("earlier");
        assertThat(data.path("nextBeforeSeq").asLong()).isEqualTo(17L);
        assertThat(data.path("hasMore").asBoolean()).isTrue();
        assertThat(data.path("lastReadSeq").asLong()).isEqualTo(12L);
        verify(conversationApplicationService).listMessageHistory(viewer, conversationId, 18L, 2);

        mockMvc.perform(get("/api/im/conversations/{conversationId}/messages/history", conversationId)
                        .param("limit", "2")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk());

        verify(conversationApplicationService).listMessageHistory(viewer, conversationId, null, 2);
    }

    @Test
    void conversation_history_and_markRead_should_enforce_membership_and_update_watermark() throws Exception {
        UUID user1 = uuid(1);
        UUID user2 = uuid(2);
        UUID user3 = uuid(3);
        String conversationId = conversationId(user1, user2);

        privateMessageApplicationService.persist(new SendPrivateTextCommand(
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
        UUID owner = uuid(1);
        UUID member = uuid(2);
        UUID outsider = uuid(99);

        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();
        roomApplicationService.joinRoom(member, roomId);

        roomMessageApplicationService.persist(new SendRoomTextCommand(
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
        assertThat(roomData.path("roomId").asText("")).isEqualTo(roomId.toString());
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
        UUID sender = uuid(1);
        UUID receiver = uuid(2);
        String conversationId = conversationId(sender, receiver);

        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();
        roomApplicationService.joinRoom(receiver, roomId);

        privateMessageApplicationService.persist(new SendPrivateTextCommand(
                "req-p1",
                "cp1",
                sender,
                receiver,
                conversationId,
                "hello",
                System.currentTimeMillis()
        ));
        roomMessageApplicationService.persist(new SendRoomTextCommand(
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
        assertThat(roomItem.path("roomId").asText("")).isEqualTo(roomId.toString());
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
    void conversation_list_should_readUserInboxLastMessageUnreadAndSortByLatestMessage() throws Exception {
        UUID viewer = uuid(31);
        UUID olderPeer = uuid(32);
        UUID newerPeer = uuid(33);
        String olderConversationId = conversationId(viewer, olderPeer);
        String newerConversationId = conversationId(viewer, newerPeer);

        privateMessageApplicationService.persist(new SendPrivateTextCommand(
                "req-list-old",
                "c-list-old",
                olderPeer,
                viewer,
                olderConversationId,
                "older",
                System.currentTimeMillis()
        ));
        privateMessageApplicationService.persist(new SendPrivateTextCommand(
                "req-list-new",
                "c-list-new",
                newerPeer,
                viewer,
                newerConversationId,
                "newer",
                System.currentTimeMillis()
        ));

        String res = mockMvc.perform(get("/api/im/conversations")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode items = objectMapper.readTree(res).path("data");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("conversationId").asText()).isEqualTo(newerConversationId);
        assertThat(items.get(0).path("otherUserId").asText()).isEqualTo(newerPeer.toString());
        assertThat(items.get(0).path("unreadCount").asLong()).isEqualTo(1L);
        assertThat(items.get(0).path("lastMessage").path("content").asText()).isEqualTo("newer");
        assertThat(items.get(1).path("conversationId").asText()).isEqualTo(olderConversationId);
    }

    @Test
    void markRead_shouldUpdateConversationAndRoomInboxUnreadIdempotently() throws Exception {
        UUID sender = uuid(41);
        UUID receiver = uuid(42);
        String conversationId = conversationId(sender, receiver);

        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();
        roomApplicationService.joinRoom(receiver, roomId);

        privateMessageApplicationService.persist(new SendPrivateTextCommand(
                "req-mark-p1",
                "c-mark-p1",
                sender,
                receiver,
                conversationId,
                "hello",
                System.currentTimeMillis()
        ));
        roomMessageApplicationService.persist(new SendRoomTextCommand(
                "req-mark-r1",
                "c-mark-r1",
                sender,
                roomId,
                "hi room",
                System.currentTimeMillis()
        ));

        mockMvc.perform(post("/api/im/conversations/{id}/read", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadSeq\":1}")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/im/conversations/{id}/read", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadSeq\":1}")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/im/conversations/{id}/read", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadSeq\":0}")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/im/rooms/{roomId}/read", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadSeq\":1}")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/im/rooms/{roomId}/read", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadSeq\":0}")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk());

        String receiverRes = mockMvc.perform(get("/api/im/unread/summary")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode receiverData = objectMapper.readTree(receiverRes).path("data");
        assertThat(receiverData.path("conversations").get(0).path("lastReadSeq").asLong()).isEqualTo(1L);
        assertThat(receiverData.path("conversations").get(0).path("unreadCount").asLong()).isZero();
        assertThat(receiverData.path("rooms").get(0).path("lastReadSeq").asLong()).isEqualTo(1L);
        assertThat(receiverData.path("rooms").get(0).path("unreadCount").asLong()).isZero();
    }

    private String bearer(UUID userId) throws Exception {
        String token = signHs256(jwtSecret, jwtIssuer, String.valueOf(userId), Instant.now().plusSeconds(120));
        return "Bearer " + token;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static String conversationId(UUID userId1, UUID userId2) {
        UUID first = userId1.compareTo(userId2) <= 0 ? userId1 : userId2;
        UUID second = first.equals(userId1) ? userId2 : userId1;
        return first + "_" + second;
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
