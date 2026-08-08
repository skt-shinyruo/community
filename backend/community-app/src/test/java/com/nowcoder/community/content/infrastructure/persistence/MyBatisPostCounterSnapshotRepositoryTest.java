package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "content.feed.hot-rank-version=hot-v3"
)
@ActiveProfiles("test")
class MyBatisPostCounterSnapshotRepositoryTest {

    private static final UUID POST_ID =
            UUID.fromString("00000000-0000-7000-8000-000000000621");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MyBatisPostCounterSnapshotRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_score_snapshot");
        jdbcTemplate.update("delete from post_counter_snapshot");
    }

    @Test
    void findShouldRestoreTheLatestPersistedCounterAndScoreBaseline() {
        repository.upsert(POST_ID, 41L, 9L, 5L, 7L, 99.5, 12L);

        PostCounterSnapshot snapshot = repository.findByPostId(POST_ID);

        assertThat(snapshot).isEqualTo(new PostCounterSnapshot(POST_ID, 41L, 9L, 5L, 7L, 99.5, 12L));
    }

    @Test
    void staleFlushShouldNotOverwriteNewerCounterOrScoreSnapshot() {
        repository.upsert(POST_ID, 51L, 10L, 6L, 8L, 101.5, 13L);

        repository.upsert(POST_ID, 41L, 9L, 5L, 7L, 99.5, 12L);

        assertThat(repository.findByPostId(POST_ID))
                .isEqualTo(new PostCounterSnapshot(POST_ID, 51L, 10L, 6L, 8L, 101.5, 13L));
    }

    @Test
    void upsertShouldPersistConfiguredHotRankVersion() {
        repository.upsert(POST_ID, 41L, 9L, 5L, 7L, 99.5, 12L);

        String rankVersion = jdbcTemplate.queryForObject(
                "select rank_version from post_score_snapshot",
                String.class
        );

        assertThat(rankVersion).isEqualTo("hot-v3");
    }

    @Test
    void sameRevisionReplayShouldRepairMissingScoreWithoutChangingCounters() {
        jdbcTemplate.update(
                "insert into post_counter_snapshot(post_id, view_count, like_count, comment_count, "
                        + "bookmark_count, flush_revision) values (?, ?, ?, ?, ?, ?)",
                POST_ID,
                51L,
                10L,
                6L,
                8L,
                13L
        );

        repository.upsert(POST_ID, 41L, 9L, 5L, 7L, 101.5, 13L);

        assertThat(repository.findByPostId(POST_ID))
                .isEqualTo(new PostCounterSnapshot(POST_ID, 51L, 10L, 6L, 8L, 101.5, 13L));
    }

    @Test
    void findShouldReturnNullWhenNoSnapshotExists() {
        assertThat(repository.findByPostId(POST_ID)).isNull();
    }
}
