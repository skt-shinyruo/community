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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class BookmarkMapperPersistenceTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000602");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-7000-8000-000000000603");
    private static final UUID BOOKMARKER_ID = UUID.fromString("00000000-0000-7000-8000-000000000604");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookmarkMapper bookmarkMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_bookmark");
        jdbcTemplate.update("delete from discuss_post");
    }

    @Test
    void selectBookmarkedPostsShouldReadActivePostWithoutLegacyContentColumn() {
        Date createTime = Date.from(Instant.parse("2026-07-20T12:00:00Z"));
        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, title, type, status, create_time, comment_count, score) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(POST_ID),
                BinaryUuidCodec.toBytes(AUTHOR_ID),
                "收藏查询标题",
                0,
                0,
                Timestamp.from(createTime.toInstant()),
                3,
                8.5
        );
        jdbcTemplate.update(
                "insert into post_bookmark(user_id, post_id, create_time) values (?, ?, ?)",
                BinaryUuidCodec.toBytes(BOOKMARKER_ID),
                BinaryUuidCodec.toBytes(POST_ID),
                Timestamp.from(createTime.toInstant())
        );

        List<DiscussPost> result = bookmarkMapper.selectBookmarkedPosts(BOOKMARKER_ID, 0, 10);

        assertThat(result).singleElement().satisfies(post -> {
            assertThat(post.getId()).isEqualTo(POST_ID);
            assertThat(post.getTitle()).isEqualTo("收藏查询标题");
            assertThat(post.getStatus()).isZero();
            assertThat(post.getCommentCount()).isEqualTo(3);
            assertThat(post.getScore()).isEqualTo(8.5);
        });
    }
}
