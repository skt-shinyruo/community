package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.notice.application.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.application.command.ProjectNoticeCommand;
import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.infrastructure.persistence.dataobject.NoticeRecordDataObject;
import com.nowcoder.community.notice.infrastructure.persistence.mapper.NoticeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class NoticeMapperPersistenceTest {

    private static final UUID NOTICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000401");
    private static final UUID OTHER_NOTICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000402");
    private static final UUID RECIPIENT_USER_ID = uuid(9);
    private static final UUID FIRST_LIFECYCLE =
            UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID SECOND_LIFECYCLE =
            UUID.fromString("00000000-0000-7000-8000-000000000002");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private NoticeProjectionApplicationService noticeProjectionApplicationService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from notice_projection_event_log");
        jdbcTemplate.update("delete from notice_like_projection_state");
        jdbcTemplate.update("delete from notice_record");
    }

    @Test
    void insertNoticeShouldPersistApplicationAssignedUuidNoticeId() {
        NoticeRecordDataObject notice = new NoticeRecordDataObject();
        notice.setId(NOTICE_ID);
        notice.setSenderUserId(NoticeRecord.SYSTEM_NOTICE_SENDER_ID);
        notice.setRecipientUserId(RECIPIENT_USER_ID);
        notice.setTopic("comment");
        notice.setContent("{\"eventId\":\"evt-1\"}");
        notice.setSourceEventType("CommentCreated");
        notice.setSourceRelationKey("comment:1");
        notice.setStatus(0);
        notice.setCreateTime(new Date());

        int inserted = noticeMapper.insertNotice(notice);

        assertThat(inserted).isEqualTo(1);
        assertThat(notice.getId()).isEqualTo(NOTICE_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from notice_record where recipient_user_id = ? and topic = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(RECIPIENT_USER_ID),
                "comment"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(NOTICE_ID);

        List<NoticeRecordDataObject> notices = noticeMapper.selectNotices(RECIPIENT_USER_ID, "comment", 0, 10);
        assertThat(notices).singleElement().satisfies(persisted -> {
            assertThat(persisted.getId()).isEqualTo(NOTICE_ID);
            assertThat(persisted.getRecipientUserId()).isEqualTo(RECIPIENT_USER_ID);
        });
    }

    @Test
    void insertNoticeShouldPersistEscapedCommentProjectionJson() {
        String sourceContent = "\"".repeat(2_000);
        String contentJson = jsonCodec().toJson(Map.of(
                "eventId", "evt-comment-escaped",
                "type", "CommentCreated",
                "payload", Map.of("content", sourceContent)
        ));
        NoticeRecordDataObject notice = new NoticeRecordDataObject();
        notice.setId(NOTICE_ID);
        notice.setSenderUserId(NoticeRecord.SYSTEM_NOTICE_SENDER_ID);
        notice.setRecipientUserId(RECIPIENT_USER_ID);
        notice.setTopic("comment");
        notice.setContent(contentJson);
        notice.setSourceEventType("CommentCreated");
        notice.setStatus(0);
        notice.setCreateTime(new Date());

        assertThat(contentJson.length()).isGreaterThan(4_000);
        assertThat(noticeMapper.insertNotice(notice)).isEqualTo(1);
        String persistedJson = jdbcTemplate.queryForObject(
                "select content from notice_record where id = ?",
                String.class,
                BinaryUuidCodec.toBytes(NOTICE_ID)
        );

        assertThat(persistedJson).isEqualTo(contentJson);
        assertThat(jsonCodec().readTree(persistedJson).path("payload").path("content").asText())
                .isEqualTo(sourceContent);
    }

    @Test
    void updateNoticesStatusForRecipientShouldTargetUuidIds() {
        insertNotice(NOTICE_ID, RECIPIENT_USER_ID, "comment", 0);
        insertNotice(OTHER_NOTICE_ID, RECIPIENT_USER_ID, "comment", 0);

        int updated = noticeMapper.updateNoticesStatusForRecipient(List.of(NOTICE_ID), 0, 1, RECIPIENT_USER_ID);

        assertThat(updated).isEqualTo(1);
        assertThat(statusOf(NOTICE_ID)).isEqualTo(1);
        assertThat(statusOf(OTHER_NOTICE_ID)).isEqualTo(0);
    }

    @Test
    void updateNoticesStatusShouldNotReviveRevokedNotice() {
        insertNotice(NOTICE_ID, RECIPIENT_USER_ID, "like", 2);

        int updated = noticeMapper.updateNoticesStatusForRecipient(
                List.of(NOTICE_ID), 0, 1, RECIPIENT_USER_ID);

        assertThat(updated).isZero();
        assertThat(statusOf(NOTICE_ID)).isEqualTo(2);
    }

    @Test
    void revokeLikeNoticeShouldUpdateOnlyMatchingActiveLikeNotice() {
        insertLikeNotice(NOTICE_ID, RECIPIENT_USER_ID, "like:actor:3:entity", 0);
        insertLikeNotice(OTHER_NOTICE_ID, RECIPIENT_USER_ID, "like:actor:3:other", 0);

        int updated = noticeMapper.revokeLikeNotice(
                RECIPIENT_USER_ID,
                "like",
                "LikeCreated",
                "like:actor:3:entity",
                0,
                1,
                2
        );

        assertThat(updated).isEqualTo(1);
        assertThat(statusOf(NOTICE_ID)).isEqualTo(2);
        assertThat(statusOf(OTHER_NOTICE_ID)).isEqualTo(0);
    }

    @Test
    void likeProjectionShouldConvergeAcrossReorderedLifecycles() {
        String relationKey = "like:" + uuid(1) + ":3:" + uuid(100);

        noticeProjectionApplicationService.projectReliably(likeCommand(
                true, "first-created", 10L, FIRST_LIFECYCLE, relationKey));
        noticeProjectionApplicationService.projectReliably(likeCommand(
                true, "second-created", 30L, SECOND_LIFECYCLE, relationKey));
        noticeProjectionApplicationService.projectReliably(likeCommand(
                false, "first-delayed-removed", 20L, FIRST_LIFECYCLE, relationKey));

        assertThat(visibleLikeNoticeCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select source_event_id from notice_like_projection_state where recipient_user_id = ? and source_relation_key = ?",
                String.class,
                BinaryUuidCodec.toBytes(RECIPIENT_USER_ID),
                relationKey
        )).isEqualTo("second-created");

        noticeProjectionApplicationService.projectReliably(likeCommand(
                false, "second-removed", 50L, SECOND_LIFECYCLE, relationKey));

        assertThat(visibleLikeNoticeCount()).isZero();
    }

    private void insertNotice(UUID noticeId, UUID toUserId, String topic, int status) {
        jdbcTemplate.update(
                "insert into notice_record (id, sender_user_id, recipient_user_id, topic, content, source_event_type, source_relation_key, status, create_time) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(noticeId),
                BinaryUuidCodec.toBytes(NoticeRecord.SYSTEM_NOTICE_SENDER_ID),
                BinaryUuidCodec.toBytes(toUserId),
                topic,
                "{\"eventId\":\"" + noticeId + "\"}",
                null,
                null,
                status
        );
    }

    private void insertLikeNotice(UUID noticeId, UUID toUserId, String relationKey, int status) {
        jdbcTemplate.update(
                "insert into notice_record (id, sender_user_id, recipient_user_id, topic, content, source_event_type, source_relation_key, status, create_time) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(noticeId),
                BinaryUuidCodec.toBytes(NoticeRecord.SYSTEM_NOTICE_SENDER_ID),
                BinaryUuidCodec.toBytes(toUserId),
                "like",
                "{\"eventId\":\"" + noticeId + "\"}",
                "LikeCreated",
                relationKey,
                status
        );
    }

    private Integer statusOf(UUID noticeId) {
        return jdbcTemplate.queryForObject(
                "select status from notice_record where id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(noticeId)
        );
    }

    private ProjectNoticeCommand likeCommand(
            boolean active,
            String eventId,
            long sourceVersion,
            UUID relationInstanceId,
            String relationKey
    ) {
        if (active) {
            return new ProjectNoticeCommand.LikeCreated(
                    eventId,
                    sourceVersion,
                    "LikeCreated",
                    uuid(1),
                    3,
                    uuid(100),
                    RECIPIENT_USER_ID,
                    uuid(100),
                    relationKey,
                    relationInstanceId
            );
        }
        return new ProjectNoticeCommand.LikeRemoved(
                eventId,
                sourceVersion,
                "LikeRemoved",
                uuid(1),
                3,
                uuid(100),
                RECIPIENT_USER_ID,
                uuid(100),
                relationKey,
                relationInstanceId
        );
    }

    private Integer visibleLikeNoticeCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from notice_record where recipient_user_id = ? and topic = 'like' and status != 2",
                Integer.class,
                BinaryUuidCodec.toBytes(RECIPIENT_USER_ID)
        );
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
