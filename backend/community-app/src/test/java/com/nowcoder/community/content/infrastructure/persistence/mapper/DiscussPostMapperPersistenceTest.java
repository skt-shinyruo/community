package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.DiscussPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class DiscussPostMapperPersistenceTest {

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-7000-8000-000000000501");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000502");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000503");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DiscussPostMapper discussPostMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_content_block");
        jdbcTemplate.update("delete from post_media_asset");
        jdbcTemplate.update("delete from post_tag");
        jdbcTemplate.update("delete from post_bookmark");
        jdbcTemplate.update("delete from discuss_post");
        jdbcTemplate.update("delete from category");
    }

    @Test
    void insertDiscussPostShouldPersistApplicationAssignedUuidPrimaryKeyAndCategoryReference() {
        insertCategory();

        DiscussPost post = new DiscussPost();
        post.setId(POST_ID);
        post.setUserId(USER_ID);
        post.setCategoryId(CATEGORY_ID);
        post.setTitle("UUIDv7");
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(new Date());
        post.setCommentCount(0);
        post.setScore(0.0);
        post.setScoreVersion(1L);
        post.setAggregateVersion(1L);

        int inserted = discussPostMapper.insertDiscussPost(post);

        assertThat(inserted).isEqualTo(1);
        assertThat(post.getId()).isEqualTo(POST_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from discuss_post where title = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "UUIDv7"
        );
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(POST_ID);

        byte[] storedCategoryId = jdbcTemplate.queryForObject(
                "select category_id from discuss_post where id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(POST_ID)
        );
        assertThat(BinaryUuidCodec.fromBytes(storedCategoryId)).isEqualTo(CATEGORY_ID);

        DiscussPost persisted = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isEqualTo(POST_ID);
        assertThat(persisted.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(persisted.getScoreVersion()).isEqualTo(1L);
        assertThat(persisted.getAggregateVersion()).isEqualTo(1L);
    }

    @Test
    void aggregateVersionCasShouldRejectStaleGovernanceWrite() {
        insertCategory();
        insertPost();
        Date wonderfulAt = Date.from(Instant.parse("2026-07-20T12:10:00Z"));
        Date staleTopAt = Date.from(Instant.parse("2026-07-20T12:11:00Z"));

        assertThat(discussPostMapper.updateStatusIfVersion(POST_ID, 1, wonderfulAt, 1L)).isEqualTo(1);
        assertThat(discussPostMapper.updateTypeIfVersion(POST_ID, 1, staleTopAt, 1L)).isZero();

        DiscussPost afterConflict = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(afterConflict.getStatus()).isEqualTo(1);
        assertThat(afterConflict.getType()).isZero();
        assertThat(afterConflict.getUpdateTime()).isEqualTo(wonderfulAt);
        assertThat(afterConflict.getAggregateVersion()).isEqualTo(2L);

        assertThat(discussPostMapper.updateTypeIfVersion(POST_ID, 1, staleTopAt, 2L)).isEqualTo(1);
        DiscussPost afterRetry = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(afterRetry.getType()).isEqualTo(1);
        assertThat(afterRetry.getAggregateVersion()).isEqualTo(3L);
    }

    @Test
    void activeCommentCountUpdateShouldNotCrossCommittedDeletion() {
        insertCategory();
        insertPost();
        Date deletedAt = Date.from(Instant.parse("2026-07-20T12:20:00Z"));

        assertThat(discussPostMapper.updateModerationDeleteMetaIfVersion(
                POST_ID,
                2,
                USER_ID,
                "admin_delete",
                deletedAt,
                1L,
                null
        )).isEqualTo(1);
        assertThat(discussPostMapper.incrementActiveCommentCount(POST_ID, 1)).isZero();

        DiscussPost deleted = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(deleted.getCommentCount()).isZero();
        assertThat(deleted.getAggregateVersion()).isEqualTo(2L);
    }

    @Test
    void activeCommentMutationShouldAdvanceAggregateVersionEvenWhenCountDoesNotChange() {
        insertCategory();
        insertPost();

        assertThat(discussPostMapper.incrementActiveCommentCount(POST_ID, 1)).isEqualTo(1);
        DiscussPost afterCreate = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(afterCreate.getCommentCount()).isEqualTo(1);
        assertThat(afterCreate.getAggregateVersion()).isEqualTo(2L);

        assertThat(discussPostMapper.incrementActiveCommentCount(POST_ID, 0)).isEqualTo(1);
        DiscussPost afterEdit = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(afterEdit.getCommentCount()).isEqualTo(1);
        assertThat(afterEdit.getAggregateVersion()).isEqualTo(3L);
    }

    @Test
    void derivedScoreWriteShouldRequireTheCurrentActiveAggregateVersion() {
        insertCategory();
        insertPost();

        assertThat(discussPostMapper.incrementActiveCommentCount(POST_ID, 1)).isEqualTo(1);
        assertThat(discussPostMapper.updateScoreIfVersion(POST_ID, 42.5, 1L)).isZero();
        assertThat(discussPostMapper.updateScoreIfVersion(POST_ID, 42.5, 2L)).isEqualTo(1);

        DiscussPost updated = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(updated.getScore()).isEqualTo(42.5);
        assertThat(updated.getScoreVersion()).isEqualTo(2L);
        assertThat(updated.getAggregateVersion()).isEqualTo(2L);

        assertThat(discussPostMapper.updateModerationDeleteMetaIfVersion(
                POST_ID,
                2,
                USER_ID,
                "admin_delete",
                Date.from(Instant.parse("2026-07-20T12:30:00Z")),
                2L,
                null
        )).isEqualTo(1);
        assertThat(discussPostMapper.updateScoreIfVersion(POST_ID, 99.0, 3L)).isZero();
        assertThat(discussPostMapper.selectDiscussPostById(POST_ID).getScore()).isEqualTo(42.5);
    }

    @Test
    void terminalDeletionShouldAdvanceUpdateTimeOnceAndPreserveFirstDeletionFacts() {
        insertCategory();
        insertPost();
        Instant deletedAt = Instant.parse("2026-07-20T12:34:56Z");
        Instant repeatedAt = Instant.parse("2026-07-20T12:35:56Z");
        UUID repeatedActorId = UUID.fromString("00000000-0000-7000-8000-000000000504");

        int firstAffected = discussPostMapper.updateModerationDeleteMetaIfVersion(
                POST_ID,
                2,
                USER_ID,
                "admin_delete",
                Date.from(deletedAt),
                1L,
                null
        );
        int repeatedAffected = discussPostMapper.updateModerationDeleteMetaIfVersion(
                POST_ID,
                2,
                repeatedActorId,
                "repeated_delete",
                Date.from(repeatedAt),
                2L,
                null
        );

        assertThat(firstAffected).isEqualTo(1);
        assertThat(repeatedAffected).isZero();
        DiscussPost persisted = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(persisted.getUpdateTime()).isEqualTo(Date.from(deletedAt));
        assertThat(persisted.getDeletedTime()).isEqualTo(Date.from(deletedAt));
        assertThat(persisted.getDeletedBy()).isEqualTo(USER_ID);
        assertThat(persisted.getDeletedReason()).isEqualTo("admin_delete");
        assertThat(persisted.getAggregateVersion()).isEqualTo(2L);
    }

    private void insertCategory() {
        jdbcTemplate.update(
                "insert into category(id, name, description, position, create_time) values (?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(CATEGORY_ID),
                "后端",
                "后端开发",
                1,
                Timestamp.from(Instant.parse("2026-04-21T00:00:00Z"))
        );
    }

    private void insertPost() {
        DiscussPost post = new DiscussPost();
        post.setId(POST_ID);
        post.setUserId(USER_ID);
        post.setCategoryId(CATEGORY_ID);
        post.setTitle("terminal deletion");
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(Date.from(Instant.parse("2026-07-20T12:00:00Z")));
        post.setCommentCount(0);
        post.setScore(0.0);
        post.setScoreVersion(1L);
        post.setAggregateVersion(1L);
        discussPostMapper.insertDiscussPost(post);
    }
}
