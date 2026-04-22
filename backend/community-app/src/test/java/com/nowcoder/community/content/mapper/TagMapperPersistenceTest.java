package com.nowcoder.community.content.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.entity.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
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
}
