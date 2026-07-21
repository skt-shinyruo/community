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
    }

    @Test
    void terminalDeletionShouldAdvanceUpdateTimeOnceAndPreserveFirstDeletionFacts() {
        insertCategory();
        insertPost();
        Instant deletedAt = Instant.parse("2026-07-20T12:34:56Z");
        Instant repeatedAt = Instant.parse("2026-07-20T12:35:56Z");
        UUID repeatedActorId = UUID.fromString("00000000-0000-7000-8000-000000000504");

        int firstAffected = discussPostMapper.updateModerationDeleteMeta(
                POST_ID,
                2,
                USER_ID,
                "admin_delete",
                Date.from(deletedAt)
        );
        int repeatedAffected = discussPostMapper.updateModerationDeleteMeta(
                POST_ID,
                2,
                repeatedActorId,
                "repeated_delete",
                Date.from(repeatedAt)
        );

        assertThat(firstAffected).isEqualTo(1);
        assertThat(repeatedAffected).isZero();
        DiscussPost persisted = discussPostMapper.selectDiscussPostById(POST_ID);
        assertThat(persisted.getUpdateTime()).isEqualTo(Date.from(deletedAt));
        assertThat(persisted.getDeletedTime()).isEqualTo(Date.from(deletedAt));
        assertThat(persisted.getDeletedBy()).isEqualTo(USER_ID);
        assertThat(persisted.getDeletedReason()).isEqualTo("admin_delete");
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
        discussPostMapper.insertDiscussPost(post);
    }
}
