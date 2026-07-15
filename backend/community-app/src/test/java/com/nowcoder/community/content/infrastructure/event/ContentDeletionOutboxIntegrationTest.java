package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.CommentContractEventApplicationService;
import com.nowcoder.community.content.application.PostContractEventApplicationService;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class ContentDeletionOutboxIntegrationTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000061");
    private static final UUID COMMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000062");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-7000-8000-000000000063");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-7000-8000-000000000064");
    private static final Instant CREATED_AT = Instant.parse("2026-07-15T08:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-07-15T08:30:00Z");
    private static final String POST_EVENT_ID = "content:PostDeleted:" + POST_ID;
    private static final String COMMENT_EVENT_ID = "content:CommentDeleted:" + COMMENT_ID;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PostDomainEventPublisher postDomainEventPublisher;

    @Autowired
    private CommentDomainEventPublisher commentDomainEventPublisher;

    @Autowired
    private PostContractEventApplicationService postContractEventApplicationService;

    @Autowired
    private CommentContractEventApplicationService commentContractEventApplicationService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from outbox_event where event_id in (?, ?)", POST_EVENT_ID, COMMENT_EVENT_ID);
        jdbcTemplate.update("delete from comment where id = ?", bytes(COMMENT_ID));
        jdbcTemplate.update("delete from discuss_post where id = ?", bytes(POST_ID));
        jdbcTemplate.update(
                """
                        insert into discuss_post(
                            id, user_id, category_id, title, type, status,
                            create_time, update_time, comment_count, score
                        ) values (?, ?, ?, ?, 0, 0, ?, ?, 1, 0)
                        """,
                bytes(POST_ID),
                bytes(AUTHOR_ID),
                bytes(CATEGORY_ID),
                "deletion outbox fixture",
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT)
        );
        jdbcTemplate.update(
                """
                        insert into comment(
                            id, post_id, user_id, root_comment_id, content, status, create_time
                        ) values (?, ?, ?, ?, ?, 0, ?)
                        """,
                bytes(COMMENT_ID),
                bytes(POST_ID),
                bytes(AUTHOR_ID),
                bytes(COMMENT_ID),
                "comment fixture",
                Timestamp.from(CREATED_AT)
        );
    }

    @Test
    void committedDeletionShouldCommitOwnerRowsAndStableOutboxEventsTogether() {
        transactionTemplate.executeWithoutResult(status -> {
            markContentDeleted();
            postDomainEventPublisher.postDeleted(POST_ID);
            commentDomainEventPublisher.commentDeleted(commentDeletedEvent());
        });

        assertThat(postStatus()).isEqualTo(2);
        assertThat(commentStatus()).isEqualTo(2);
        assertThat(deletionEventIds()).containsExactlyInAnyOrder(POST_EVENT_ID, COMMENT_EVENT_ID);

        transactionTemplate.executeWithoutResult(status -> {
            postDomainEventPublisher.postDeleted(POST_ID);
            commentDomainEventPublisher.commentDeleted(commentDeletedEvent());
        });

        assertThat(deletionEventIds()).containsExactlyInAnyOrder(POST_EVENT_ID, COMMENT_EVENT_ID);
    }

    @Test
    void rolledBackDeletionShouldRollbackOwnerRowsAndAlreadyInsertedOutboxEventsTogether() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            markContentDeleted();
            postContractEventApplicationService.publishPostDeleted(POST_ID);
            commentContractEventApplicationService.publishCommentDeleted(commentDeletedEvent());

            assertThat(postStatus()).isEqualTo(2);
            assertThat(commentStatus()).isEqualTo(2);
            assertThat(deletionEventIds()).containsExactlyInAnyOrder(POST_EVENT_ID, COMMENT_EVENT_ID);
            throw new IllegalStateException("force rollback");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        assertThat(postStatus()).isZero();
        assertThat(commentStatus()).isZero();
        assertThat(deletionEventIds()).isEmpty();
    }

    private void markContentDeleted() {
        jdbcTemplate.update(
                "update discuss_post set status = 2, update_time = ? where id = ? and status = 0",
                Timestamp.from(DELETED_AT),
                bytes(POST_ID)
        );
        jdbcTemplate.update(
                "update comment set status = 2, deleted_time = ? where id = ? and status = 0",
                Timestamp.from(DELETED_AT),
                bytes(COMMENT_ID)
        );
    }

    private CommentDeletedDomainEvent commentDeletedEvent() {
        return new CommentDeletedDomainEvent(
                COMMENT_ID,
                POST_ID,
                AUTHOR_ID,
                POST,
                POST_ID,
                DELETED_AT
        );
    }

    private int postStatus() {
        Integer value = jdbcTemplate.queryForObject(
                "select status from discuss_post where id = ?",
                Integer.class,
                bytes(POST_ID)
        );
        return value == null ? -1 : value;
    }

    private int commentStatus() {
        Integer value = jdbcTemplate.queryForObject(
                "select status from comment where id = ?",
                Integer.class,
                bytes(COMMENT_ID)
        );
        return value == null ? -1 : value;
    }

    private List<String> deletionEventIds() {
        return jdbcTemplate.queryForList(
                "select event_id from outbox_event where event_id in (?, ?) order by event_id",
                String.class,
                POST_EVENT_ID,
                COMMENT_EVENT_ID
        );
    }

    private byte[] bytes(UUID id) {
        return BinaryUuidCodec.toBytes(id);
    }
}
