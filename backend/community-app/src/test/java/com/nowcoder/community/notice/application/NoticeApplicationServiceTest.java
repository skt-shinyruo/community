package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.notice.application.command.CreateNoticeCommand;
import com.nowcoder.community.notice.application.result.NoticeItemResult;
import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.infrastructure.persistence.MyBatisNoticeRepository;
import com.nowcoder.community.notice.infrastructure.persistence.mapper.NoticeMapper;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.annotation.MapperScan;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(
        classes = NoticeApplicationServiceTest.MapperOnlyTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class NoticeApplicationServiceTest {

    private static final UUID NOTICE_ID_1 = UUID.fromString("00000000-0000-7000-8000-000000000411");
    private static final UUID NOTICE_ID_2 = UUID.fromString("00000000-0000-7000-8000-000000000412");
    private static final UUID NOTICE_ID_3 = UUID.fromString("00000000-0000-7000-8000-000000000413");
    private static final UUID NOTICE_ID_4 = UUID.fromString("00000000-0000-7000-8000-000000000414");
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private NoticeApplicationService noticeService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from notice_record");
        noticeService = new NoticeApplicationService(new MyBatisNoticeRepository(noticeMapper));
    }

    @Test
    void createNoticeShouldPersistExplicitSystemSenderSentinelZero() {
        UUID recipientUserId = uuid(9);
        noticeService.createNotice(new CreateNoticeCommand(recipientUserId, "comment", "{\"eventId\":\"evt-1\"}"));

        byte[] fromId = jdbcTemplate.queryForObject(
                "select sender_user_id from notice_record where recipient_user_id = ? and topic = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(recipientUserId),
                "comment"
        );

        assertThat(BinaryUuidCodec.fromBytes(fromId)).isEqualTo(ZERO_UUID);
        byte[] noticeId = jdbcTemplate.queryForObject(
                "select id from notice_record where recipient_user_id = ? and topic = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(recipientUserId),
                "comment"
        );
        assertThat(noticeId).hasSize(16);
    }

    @Test
    void listNoticesShouldReturnNoticeOwnedRecords() {
        UUID recipientUserId = uuid(2);
        insertNotice(NOTICE_ID_1, ZERO_UUID, recipientUserId, "comment", "{\"eventId\":\"evt-sentinel\"}", NoticeApplicationService.STATUS_UNREAD);
        insertNotice(NOTICE_ID_2, uuid(1), recipientUserId, "comment", "{\"eventId\":\"evt-comment\"}", NoticeApplicationService.STATUS_UNREAD);
        insertNotice(NOTICE_ID_3, uuid(1), recipientUserId, "mention", "{\"eventId\":\"evt-mention\"}", NoticeApplicationService.STATUS_UNREAD);

        List<NoticeRecord> notices = noticeService.listNotices(recipientUserId, "comment", 0, 10);

        assertThat(notices)
                .extracting(NoticeRecord::getSenderUserId, NoticeRecord::getRecipientUserId, NoticeRecord::getTopic)
                .containsExactlyInAnyOrder(
                        tuple(ZERO_UUID, recipientUserId, "comment"),
                        tuple(uuid(1), recipientUserId, "comment")
                );
        assertThat(noticeService.unreadCount(recipientUserId, "comment")).isEqualTo(2);
    }

    @Test
    void listNoticeItemsShouldReturnNoticeOwnedResults() {
        UUID recipientUserId = uuid(9);
        insertNotice(NOTICE_ID_4, ZERO_UUID, recipientUserId, "comment", "{\"eventId\":\"evt-1\"}", NoticeApplicationService.STATUS_UNREAD);

        List<NoticeItemResult> items = noticeService.listNoticeItems(recipientUserId, "comment", 0, 10);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(NOTICE_ID_4);
            assertThat(item.senderUserId()).isEqualTo(ZERO_UUID);
            assertThat(item.recipientUserId()).isEqualTo(recipientUserId);
            assertThat(item.noticeTopic()).isEqualTo("comment");
            assertThat(item.status()).isEqualTo(NoticeApplicationService.STATUS_UNREAD);
        });
    }

    @Test
    void revokeLikeNoticeShouldHideMatchingNoticeFromUnreadAndListViews() {
        UUID recipientUserId = uuid(9);
        jdbcTemplate.update(
                "insert into notice_record (id, sender_user_id, recipient_user_id, topic, content, source_event_type, source_relation_key, status, create_time) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(NOTICE_ID_4),
                BinaryUuidCodec.toBytes(ZERO_UUID),
                BinaryUuidCodec.toBytes(recipientUserId),
                "like",
                "{\"eventId\":\"evt-like-1\"}",
                "LikeCreated",
                "like:" + uuid(1) + ":3:" + uuid(100),
                NoticeApplicationService.STATUS_UNREAD
        );

        noticeService.revokeLikeNotice(recipientUserId, "like:" + uuid(1) + ":3:" + uuid(100));

        assertThat(noticeService.unreadCount(recipientUserId, "like")).isZero();
        assertThat(noticeService.listNotices(recipientUserId, "like", 0, 10)).isEmpty();
    }

    private void insertNotice(UUID id, UUID fromId, UUID toId, String topic, String content, int status) {
        jdbcTemplate.update(
                "insert into notice_record (id, sender_user_id, recipient_user_id, topic, content, source_event_type, source_relation_key, status, create_time) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(id),
                BinaryUuidCodec.toBytes(fromId),
                BinaryUuidCodec.toBytes(toId),
                topic,
                content,
                null,
                null,
                status
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(
            annotationClass = Mapper.class,
            basePackages = "com.nowcoder.community.notice.infrastructure.persistence.mapper"
    )
    static class MapperOnlyTestConfig {
    }
}
