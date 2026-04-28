package com.nowcoder.community.notice.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.notice.domain.model.NoticeProjection;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeProjectionDomainServiceTest {

    private final NoticeProjectionDomainService noticeProjectionDomainService = new NoticeProjectionDomainService();
    private final JsonNode payload = new ObjectMapper().createObjectNode();

    @Test
    void shouldProjectWhenRecipientAndTopicArePresent() {
        NoticeProjection projection = new NoticeProjection(
                UUID.randomUUID(),
                "comment",
                "event-1",
                "COMMENT_CREATED",
                payload
        );

        assertThat(noticeProjectionDomainService.shouldProject(projection)).isTrue();
    }

    @Test
    void shouldNotProjectMissingRecipientOrTopic() {
        assertThat(noticeProjectionDomainService.shouldProject(null)).isFalse();
        assertThat(noticeProjectionDomainService.shouldProject(new NoticeProjection(
                null,
                "comment",
                "event-1",
                "COMMENT_CREATED",
                payload
        ))).isFalse();
        assertThat(noticeProjectionDomainService.shouldProject(new NoticeProjection(
                UUID.randomUUID(),
                " ",
                "event-1",
                "COMMENT_CREATED",
                payload
        ))).isFalse();
    }
}
