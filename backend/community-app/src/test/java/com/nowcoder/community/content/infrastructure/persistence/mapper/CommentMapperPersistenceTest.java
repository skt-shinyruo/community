package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class CommentMapperPersistenceTest {

    private static final UUID COMMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000401");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000402");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000403");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommentMapper commentMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from comment");
    }

    @Test
    void insertCommentShouldPersistApplicationAssignedUuidPrimaryKeyAndEntityReference() {
        Comment comment = new Comment();
        comment.setId(COMMENT_ID);
        comment.setUserId(USER_ID);
        comment.setEntityType(1);
        comment.setEntityId(POST_ID);
        comment.setTargetId(null);
        comment.setContent("hello");
        comment.setStatus(0);
        comment.setCreateTime(new Date());

        int inserted = commentMapper.insertComment(comment);

        assertThat(inserted).isEqualTo(1);
        assertThat(comment.getId()).isEqualTo(COMMENT_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from comment where content = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "hello"
        );
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(COMMENT_ID);

        byte[] storedEntityId = jdbcTemplate.queryForObject(
                "select entity_id from comment where id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(COMMENT_ID)
        );
        assertThat(BinaryUuidCodec.fromBytes(storedEntityId)).isEqualTo(POST_ID);

        Comment persisted = commentMapper.selectCommentById(COMMENT_ID);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isEqualTo(COMMENT_ID);
        assertThat(persisted.getEntityId()).isEqualTo(POST_ID);
        assertThat(persisted.getTargetId()).isNull();
    }
}
