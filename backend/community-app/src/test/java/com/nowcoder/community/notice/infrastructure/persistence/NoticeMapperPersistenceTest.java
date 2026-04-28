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
        jdbcTemplate.update("delete from message");
    }

    @Test
    void insertNoticeShouldPersistApplicationAssignedUuidMessageId() {
        NoticeRecordDataObject notice = new NoticeRecordDataObject();
        notice.setId(NOTICE_ID);
        notice.setSenderUserId(NoticeRecord.SYSTEM_NOTICE_SENDER_ID);
        notice.setRecipientUserId(RECIPIENT_USER_ID);
        notice.setTopic("comment");
        notice.setContent("{\"eventId\":\"evt-1\"}");
        notice.setStatus(0);
        notice.setCreateTime(new Date());

        int inserted = noticeMapper.insertNotice(notice);

        assertThat(inserted).isEqualTo(1);
        assertThat(notice.getId()).isEqualTo(NOTICE_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from message where to_id = ? and conversation_id = ?",
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
        insertMessage(NOTICE_ID, RECIPIENT_USER_ID, "comment", 0);
        insertMessage(OTHER_NOTICE_ID, RECIPIENT_USER_ID, "comment", 0);

        int updated = noticeMapper.updateNoticesStatusForRecipient(List.of(NOTICE_ID), 1, RECIPIENT_USER_ID);

        assertThat(updated).isEqualTo(1);
        assertThat(statusOf(NOTICE_ID)).isEqualTo(1);
        assertThat(statusOf(OTHER_NOTICE_ID)).isEqualTo(0);
    }

    private void insertMessage(UUID messageId, UUID toUserId, String topic, int status) {
        jdbcTemplate.update(
                "insert into message (id, from_id, to_id, conversation_id, content, status, create_time) values (?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(messageId),
                BinaryUuidCodec.toBytes(NoticeRecord.SYSTEM_NOTICE_SENDER_ID),
                BinaryUuidCodec.toBytes(toUserId),
                topic,
                "{\"eventId\":\"" + messageId + "\"}",
                status
        );
    }

    private Integer statusOf(UUID messageId) {
        return jdbcTemplate.queryForObject(
                "select status from message where id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(messageId)
        );
    }
}
