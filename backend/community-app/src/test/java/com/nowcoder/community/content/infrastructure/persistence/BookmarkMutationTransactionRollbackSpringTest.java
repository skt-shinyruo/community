package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.content.application.BookmarkApplicationService;
import com.nowcoder.community.content.application.BookmarkCounterReconciliationPort;
import com.nowcoder.community.content.application.PostCounterCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class BookmarkMutationTransactionRollbackSpringTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a5");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a6");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a7");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookmarkApplicationService service;

    @MockitoBean
    private BookmarkCounterReconciliationPort reconciliationPort;

    @MockitoBean
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
                "rollback bookmark", 0, 0,
                Timestamp.from(Instant.parse("2026-08-06T00:00:00Z")), 0, 0.0
        );
    }

    @Test
    void bookmarkFactAndDurableRevisionMustRollbackTogetherWhenMarkerWriteFails() {
        doThrow(new IllegalStateException("marker store unavailable"))
                .when(reconciliationPort).recordMutation(POST_ID);

        assertThatThrownBy(() -> service.add(USER_ID, POST_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from post_bookmark where post_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(POST_ID)
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from post_bookmark_counter_reconciliation where post_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(POST_ID)
        )).isZero();
    }
}
