package com.nowcoder.community.notice.service;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.notice.dto.NoticeItemResponse;
import com.nowcoder.community.notice.entity.NoticeRecord;
import com.nowcoder.community.notice.mapper.NoticeMapper;
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
        classes = NoticeServiceTest.MapperOnlyTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class NoticeServiceTest {

    private static final UUID NOTICE_ID_1 = UUID.fromString("00000000-0000-7000-8000-000000000411");
    private static final UUID NOTICE_ID_2 = UUID.fromString("00000000-0000-7000-8000-000000000412");
    private static final UUID NOTICE_ID_3 = UUID.fromString("00000000-0000-7000-8000-000000000413");
    private static final UUID NOTICE_ID_4 = UUID.fromString("00000000-0000-7000-8000-000000000414");
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from message");
        noticeService = new NoticeService(noticeMapper);
    }

    @Test
    void createNoticeShouldPersistExplicitSystemSenderSentinelZero() {
        UUID recipientUserId = uuid(9);
        noticeService.createNotice(recipientUserId, "comment", "{\"eventId\":\"evt-1\"}");

        byte[] fromId = jdbcTemplate.queryForObject(
                "select from_id from message where to_id = ? and conversation_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(recipientUserId),
                "comment"
        );

        assertThat(BinaryUuidCodec.fromBytes(fromId)).isEqualTo(ZERO_UUID);
        byte[] noticeId = jdbcTemplate.queryForObject(
                "select id from message where to_id = ? and conversation_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(recipientUserId),
                "comment"
        );
        assertThat(noticeId).hasSize(16);
    }

    @Test
    void listNoticesShouldReturnNoticeOwnedRecords() {
        UUID recipientUserId = uuid(2);
        insertMessage(NOTICE_ID_1, ZERO_UUID, recipientUserId, "comment", "{\"eventId\":\"evt-sentinel\"}", NoticeService.STATUS_UNREAD);
        insertMessage(NOTICE_ID_2, uuid(1), recipientUserId, "comment", "{\"eventId\":\"evt-legacy\"}", NoticeService.STATUS_UNREAD);
        insertMessage(NOTICE_ID_3, uuid(1), recipientUserId, "1_2", "hello from real user one", NoticeService.STATUS_UNREAD);

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
    void listNoticeItemsShouldReturnNoticeOwnedDtos() {
        UUID recipientUserId = uuid(9);
        insertMessage(NOTICE_ID_4, ZERO_UUID, recipientUserId, "comment", "{\"eventId\":\"evt-1\"}", NoticeService.STATUS_UNREAD);

        List<NoticeItemResponse> items = noticeService.listNoticeItems(recipientUserId, "comment", 0, 10);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(NOTICE_ID_4);
            assertThat(item.getSenderUserId()).isEqualTo(ZERO_UUID);
            assertThat(item.getRecipientUserId()).isEqualTo(recipientUserId);
            assertThat(item.getTopic()).isEqualTo("comment");
            assertThat(item.getStatus()).isEqualTo(NoticeService.STATUS_UNREAD);
        });
    }

    private void insertMessage(UUID id, UUID fromId, UUID toId, String conversationId, String content, int status) {
        jdbcTemplate.update(
                "insert into message (id, from_id, to_id, conversation_id, content, status, create_time) values (?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(id),
                BinaryUuidCodec.toBytes(fromId),
                BinaryUuidCodec.toBytes(toId),
                conversationId,
                content,
                status
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(
            annotationClass = Mapper.class,
            basePackages = "com.nowcoder.community.notice.mapper"
    )
    static class MapperOnlyTestConfig {
    }
}
