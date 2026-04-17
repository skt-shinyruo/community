package com.nowcoder.community.notice.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(
        classes = NoticeServiceTest.MapperOnlyTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class NoticeServiceTest {

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
        noticeService.createNotice(9, "comment", "{\"eventId\":\"evt-1\"}");

        Integer fromId = jdbcTemplate.queryForObject(
                "select from_id from message where to_id = ? and conversation_id = ?",
                Integer.class,
                9,
                "comment"
        );

        assertThat(fromId).isEqualTo(0);
    }

    @Test
    void listNoticesShouldReturnNoticeOwnedRecords() {
        insertMessage(0, 2, "comment", "{\"eventId\":\"evt-sentinel\"}", NoticeService.STATUS_UNREAD);
        insertMessage(1, 2, "comment", "{\"eventId\":\"evt-legacy\"}", NoticeService.STATUS_UNREAD);
        insertMessage(1, 2, "1_2", "hello from real user one", NoticeService.STATUS_UNREAD);

        List<NoticeRecord> notices = noticeService.listNotices(2, "comment", 0, 10);

        assertThat(notices)
                .extracting(NoticeRecord::getSenderUserId, NoticeRecord::getRecipientUserId, NoticeRecord::getTopic)
                .containsExactlyInAnyOrder(
                        tuple(0, 2, "comment"),
                        tuple(1, 2, "comment")
                );
        assertThat(noticeService.unreadCount(2, "comment")).isEqualTo(2);
    }

    @Test
    void listNoticeItemsShouldReturnNoticeOwnedDtos() {
        insertMessage(0, 9, "comment", "{\"eventId\":\"evt-1\"}", NoticeService.STATUS_UNREAD);

        List<NoticeItemResponse> items = noticeService.listNoticeItems(9, "comment", 0, 10);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getSenderUserId()).isEqualTo(0);
            assertThat(item.getRecipientUserId()).isEqualTo(9);
            assertThat(item.getTopic()).isEqualTo("comment");
            assertThat(item.getStatus()).isEqualTo(NoticeService.STATUS_UNREAD);
        });
    }

    private void insertMessage(int fromId, int toId, String conversationId, String content, int status) {
        jdbcTemplate.update(
                "insert into message (from_id, to_id, conversation_id, content, status, create_time) values (?, ?, ?, ?, ?, current_timestamp)",
                fromId, toId, conversationId, content, status
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
