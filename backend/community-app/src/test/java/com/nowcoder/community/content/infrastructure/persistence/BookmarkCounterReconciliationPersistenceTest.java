package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.content.application.BookmarkCounterReconciliationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class BookmarkCounterReconciliationPersistenceTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a1");
    private static final UUID SECOND_POST_ID = UUID.fromString("00000000-0000-7000-8000-0000000006a2");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookmarkCounterReconciliationPort port;

    @BeforeEach
    void clearMarkers() {
        jdbcTemplate.update("delete from post_bookmark_counter_reconciliation");
    }

    @Test
    void mutationRevisionMustIncreaseForIdempotentRetriesAndUseCompareAndClear() {
        port.recordMutation(POST_ID);
        port.recordMutation(POST_ID);

        List<BookmarkCounterReconciliationPort.PendingBookmarkCounter> pending = port.listPending(500);
        assertThat(pending).containsExactly(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(POST_ID, 2L)
        );
        assertThat(port.clearIfRevision(POST_ID, 1L)).isFalse();
        assertThat(port.listPending(500)).hasSize(1);
        assertThat(port.clearIfRevision(POST_ID, 2L)).isTrue();
        assertThat(port.listPending(500)).isEmpty();
    }

    @Test
    void clearedRevisionMustNotReappearAndLetAStaleWorkerClearANewerMutation() {
        port.recordMutation(POST_ID);
        BookmarkCounterReconciliationPort.PendingBookmarkCounter staleToken =
                port.listPending(500).get(0);
        assertThat(port.clearIfRevision(POST_ID, staleToken.revision())).isTrue();

        port.recordMutation(POST_ID);

        assertThat(port.clearIfRevision(POST_ID, staleToken.revision())).isFalse();
        assertThat(port.listPending(500)).containsExactly(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(POST_ID, 2L)
        );
    }

    @Test
    void failedAttemptShouldRotateToTailWithoutDeferringANewerRevision() {
        port.recordMutation(POST_ID);
        port.recordMutation(SECOND_POST_ID);
        jdbcTemplate.update(
                "update post_bookmark_counter_reconciliation set updated_at = ? where post_id = ?",
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                BinaryUuidCodec.toBytes(POST_ID)
        );
        jdbcTemplate.update(
                "update post_bookmark_counter_reconciliation set updated_at = ? where post_id = ?",
                Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")),
                BinaryUuidCodec.toBytes(SECOND_POST_ID)
        );

        assertThat(port.listPending(1)).containsExactly(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(POST_ID, 1L)
        );
        assertThat(port.deferIfRevision(POST_ID, 1L)).isTrue();
        assertThat(port.listPending(1)).containsExactly(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(SECOND_POST_ID, 1L)
        );

        port.recordMutation(POST_ID);
        assertThat(port.deferIfRevision(POST_ID, 1L)).isFalse();
    }
}
