package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.HotTag;
import com.nowcoder.community.content.domain.model.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class TagMapperPersistenceTest {

    private static final UUID TAG_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private PostTagMapper postTagMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_tag");
        jdbcTemplate.update("delete from tag");
        jdbcTemplate.update("delete from discuss_post");
    }

    @Test
    void insertTagShouldPersistApplicationAssignedUuidPrimaryKeyAndBinaryJoinKey() {
        Date createTime = new Date();
        UUID postId = uuid(88);
        Tag tag = new Tag();
        tag.setId(TAG_ID);
        tag.setName("spring");
        tag.setCreateTime(createTime);

        int inserted = tagMapper.insertTag(tag);

        assertThat(inserted).isEqualTo(1);
        assertThat(tag.getId()).isEqualTo(TAG_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from tag where name = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "spring"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(TAG_ID);

        Tag persisted = tagMapper.selectTagByName("spring");
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isEqualTo(TAG_ID);

        postTagMapper.insertPostTag(postId, TAG_ID, createTime);

        byte[] storedTagId = jdbcTemplate.queryForObject(
                "select tag_id from post_tag where post_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(postId)
        );
        assertThat(storedTagId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedTagId)).isEqualTo(TAG_ID);
    }

    @Test
    void tagUsageCountsShouldIgnoreDeletedPosts() {
        Date createTime = new Date();
        UUID activePostId = uuid(88);
        UUID deletedPostId = uuid(89);
        Tag tag = new Tag();
        tag.setId(TAG_ID);
        tag.setName("spring");
        tag.setCreateTime(createTime);
        tagMapper.insertTag(tag);
        insertPost(activePostId, 0, createTime);
        insertPost(deletedPostId, 2, createTime);
        postTagMapper.insertPostTag(activePostId, TAG_ID, createTime);
        postTagMapper.insertPostTag(deletedPostId, TAG_ID, createTime);

        List<HotTag> hotTags = tagMapper.selectHotTags(10);
        List<HotTag> suggestTags = tagMapper.selectSuggestTags("spr", 10);

        assertThat(hotTags).singleElement().satisfies(hotTag -> {
            assertThat(hotTag.getName()).isEqualTo("spring");
            assertThat(hotTag.getUseCount()).isEqualTo(1);
        });
        assertThat(suggestTags).singleElement().satisfies(hotTag -> {
            assertThat(hotTag.getName()).isEqualTo("spring");
            assertThat(hotTag.getUseCount()).isEqualTo(1);
        });
    }

    private void insertPost(UUID postId, int status, Date createTime) {
        jdbcTemplate.update(
                """
                insert into discuss_post(id, user_id, title, type, status, create_time, comment_count, score)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                BinaryUuidCodec.toBytes(postId),
                BinaryUuidCodec.toBytes(uuid(7)),
                "post-" + postId,
                0,
                status,
                createTime,
                0,
                0.0
        );
    }
}
