package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.content.application.BookmarkApplicationService;
import com.nowcoder.community.content.application.PostCounterCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class BookmarkApplicationDurableMutationSpringTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a2");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a3");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a4");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookmarkApplicationService service;

    @MockBean
    private PostCounterCache postCounterCache;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_bookmark_counter_reconciliation");
        jdbcTemplate.update("delete from post_bookmark");
        jdbcTemplate.update("delete from discuss_post");
        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, title, type, status, create_time, comment_count, score) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(POST_ID),
                BinaryUuidCodec.toBytes(AUTHOR_ID),
                "durable bookmark", 0, 0,
                Timestamp.from(Instant.parse("2026-08-06T00:00:00Z")), 0, 0.0
        );
    }

    @Test
    void committedBookmarkMustLeaveDurableRevisionWhenRedisDirtyMarkFails() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(postCounterCache).markDirty(POST_ID);

        service.add(USER_ID, POST_ID);
        service.add(USER_ID, POST_ID);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from post_bookmark where post_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(POST_ID)
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select revision from post_bookmark_counter_reconciliation where post_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(POST_ID)
        )).isEqualTo(2L);
    }

    @Test
    void missingPostRemoveMustNotCreateDurableWork() {
        UUID missingPostId = UUID.fromString("00000000-0000-7000-8000-0000000006af");

        assertThatThrownBy(() -> service.remove(USER_ID, missingPostId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from post_bookmark_counter_reconciliation",
                Long.class
        )).isZero();
    }

    @Test
    void deletedExistingPostShouldStillAllowIdempotentRemoveRetries() {
        jdbcTemplate.update(
                "update discuss_post set status = 2 where id = ?",
                BinaryUuidCodec.toBytes(POST_ID)
        );

        service.remove(USER_ID, POST_ID);
        service.remove(USER_ID, POST_ID);

        assertThat(jdbcTemplate.queryForObject(
                "select revision from post_bookmark_counter_reconciliation where post_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(POST_ID)
        )).isEqualTo(2L);
    }
}
