package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NoticeMapper noticeMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
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
    void updateNoticesStatusForRecipientShouldTargetUuidIds() {
        insertNotice(NOTICE_ID, RECIPIENT_USER_ID, "comment", 0);
        insertNotice(OTHER_NOTICE_ID, RECIPIENT_USER_ID, "comment", 0);

        int updated = noticeMapper.updateNoticesStatusForRecipient(List.of(NOTICE_ID), 1, RECIPIENT_USER_ID);

        assertThat(updated).isEqualTo(1);
        assertThat(statusOf(NOTICE_ID)).isEqualTo(1);
        assertThat(statusOf(OTHER_NOTICE_ID)).isEqualTo(0);
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
}
