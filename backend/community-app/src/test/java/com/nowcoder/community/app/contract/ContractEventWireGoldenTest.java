package com.nowcoder.community.app.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContractEventWireGoldenTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.standard();

    @Test
    void envelopeClassNamesAndJsonNodePayloadComponentsMustRemainStable() {
        assertThat(ContentContractEvent.class.getName())
                .isEqualTo("com.nowcoder.community.content.contracts.event.ContentContractEvent");
        assertThat(SocialContractEvent.class.getName())
                .isEqualTo("com.nowcoder.community.social.contracts.event.SocialContractEvent");
        assertThat(UserContractEvent.class.getName())
                .isEqualTo("com.nowcoder.community.user.contracts.event.UserContractEvent");

        assertPayloadComponentIsJsonNode(ContentContractEvent.class);
        assertPayloadComponentIsJsonNode(SocialContractEvent.class);
        assertPayloadComponentIsJsonNode(UserContractEvent.class);
    }

    @Test
    void existingGoldenJsonMustDeserializeIntoJsonNodePayloads() throws IOException {
        assertGoldenDeserializesWithJsonNodePayload(
                "contracts/golden/content-post-published-v1.json",
                ContentContractEvent.class
        );
        assertGoldenDeserializesWithJsonNodePayload(
                "contracts/golden/social-like-created-v1.json",
                SocialContractEvent.class
        );
        assertGoldenDeserializesWithJsonNodePayload(
                "contracts/golden/user-policy-changed-v1.json",
                UserContractEvent.class
        );
    }

    @Test
    void contentEventWireMustRemainCompatible() throws IOException {
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(101));
        payload.setUserId(uuid(102));
        payload.setCategoryId(uuid(103));
        payload.setTags(List.of("java", "ddd"));
        payload.setTitle("Architecture baseline");
        payload.setContent("Stable event wire");
        payload.setType(0);
        payload.setStatus(0);
        payload.setCreateTime(Instant.parse("2026-07-15T01:02:03Z"));
        payload.setUpdateTime(Instant.parse("2026-07-15T01:03:04Z"));
        payload.setScore(12.5);

        assertGolden(
                "contracts/golden/content-post-published-v1.json",
                new ContentContractEvent(
                        "content:post:published:101",
                        uuid(101),
                        "Post",
                        ContentEventTypes.POST_PUBLISHED,
                        Instant.parse("2026-07-15T01:02:03Z"),
                        7L,
                        OBJECT_MAPPER.valueToTree(payload)
                )
        );
    }

    @Test
    void socialEventWireMustRemainCompatible() throws IOException {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(201));
        payload.setEntityType(1);
        payload.setEntityId(uuid(202));
        payload.setEntityUserId(uuid(203));
        payload.setPostId(uuid(202));
        payload.setRelationKey("post:202:user:201");

        assertGolden(
                "contracts/golden/social-like-created-v1.json",
                new SocialContractEvent(
                        "social:like:created:201:202",
                        uuid(202),
                        "Like",
                        SocialEventTypes.LIKE_CREATED,
                        Instant.parse("2026-07-15T02:03:04Z"),
                        11L,
                        OBJECT_MAPPER.valueToTree(payload)
                )
        );
    }

    @Test
    void userEventWireMustRemainCompatible() throws IOException {
        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(uuid(301));
        payload.setUserExists(true);
        payload.setSuspended(false);
        payload.setMuted(true);
        payload.setMuteUntil(1_784_078_400_000L);
        payload.setBanUntil(null);
        payload.setCanSendPrivate(false);
        payload.setOccurredAtEpochMillis(1_773_800_000_000L);
        payload.setVersion(13L);

        assertGolden(
                "contracts/golden/user-policy-changed-v1.json",
                new UserContractEvent(
                        "user:policy:changed:301:13",
                        UserEventTypes.USER_POLICY_CHANGED,
                        OBJECT_MAPPER.valueToTree(payload)
                )
        );
    }

    private static void assertPayloadComponentIsJsonNode(Class<?> envelopeType) {
        assertThat(envelopeType.getRecordComponents())
                .filteredOn(component -> component.getName().equals("payload"))
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(JsonNode.class);
    }

    private static <T> void assertGoldenDeserializesWithJsonNodePayload(
            String resourceName,
            Class<T> envelopeType
    ) throws IOException {
        try (InputStream input = ContractEventWireGoldenTest.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertThat(input).as(resourceName).isNotNull();
            T envelope = OBJECT_MAPPER.readValue(input, envelopeType);
            Object payload = OBJECT_MAPPER.valueToTree(envelope).get("payload");
            Object recordPayload = java.util.Arrays.stream(envelopeType.getRecordComponents())
                    .filter(component -> component.getName().equals("payload"))
                    .findFirst()
                    .orElseThrow()
                    .getAccessor()
                    .invoke(envelope);
            assertThat(recordPayload).isInstanceOf(JsonNode.class);
            assertThat(recordPayload).isEqualTo(payload);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot inspect " + envelopeType.getName(), e);
        }
    }

    private static void assertGolden(String resourceName, Object event) throws IOException {
        JsonNode actual = OBJECT_MAPPER.valueToTree(event);
        try (InputStream input = ContractEventWireGoldenTest.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertThat(input).as(resourceName).isNotNull();
            JsonNode expected = OBJECT_MAPPER.readTree(input);
            assertThat(OBJECT_MAPPER.writeValueAsString(actual))
                    .as(resourceName)
                    .isEqualTo(OBJECT_MAPPER.writeValueAsString(expected));
        }
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("01965429-b34a-7000-8000-" + String.format("%012d", suffix));
    }
}
