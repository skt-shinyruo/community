package com.nowcoder.community.notice.domain.service;

import com.nowcoder.community.notice.domain.model.NoticeProjection;
import com.nowcoder.community.notice.domain.model.NoticeProjectionContent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeProjectionDomainServiceTest {

    private final NoticeProjectionDomainService service = new NoticeProjectionDomainService();
    private final NoticeProjectionContent content = new NoticeProjectionContent.Comment(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(),
            UUID.randomUUID(), "content", Instant.EPOCH);

    @Test
    void shouldProjectWhenRecipientTopicAndTypedContentArePresent() {
        NoticeProjection projection = new NoticeProjection(
                UUID.randomUUID(), "comment", "event-1", "CommentCreated", null, content);

        assertThat(service.shouldProject(projection)).isTrue();
    }

    @Test
    void shouldNotProjectMissingRecipientTopicOrContent() {
        assertThat(service.shouldProject(null)).isFalse();
        assertThat(service.shouldProject(new NoticeProjection(
                null, "comment", "event-1", "CommentCreated", null, content))).isFalse();
        assertThat(service.shouldProject(new NoticeProjection(
                UUID.randomUUID(), " ", "event-1", "CommentCreated", null, content))).isFalse();
        assertThat(service.shouldProject(new NoticeProjection(
                UUID.randomUUID(), "comment", "event-1", "CommentCreated", null, null))).isFalse();
    }
}
