package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentTransitionTargetDataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
    void insertShouldPersistPostThreadIdentityAndInitialVersion() {
        CommentDataObject comment = new CommentDataObject();
        comment.setId(COMMENT_ID);
        comment.setPostId(POST_ID);
        comment.setUserId(USER_ID);
        comment.setRootCommentId(COMMENT_ID);
        comment.setParentCommentId(null);
        comment.setReplyToUserId(null);
        comment.setContent("hello");
        comment.setStatus(0);
        comment.setCreateTime(new Date());

        int inserted = commentMapper.insert(comment);

        assertThat(inserted).isEqualTo(1);
        assertThat(comment.getId()).isEqualTo(COMMENT_ID);

        byte[] storedPostId = jdbcTemplate.queryForObject(
                "select post_id from comment where id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(COMMENT_ID)
        );
        assertThat(BinaryUuidCodec.fromBytes(storedPostId)).isEqualTo(POST_ID);

        byte[] storedRootCommentId = jdbcTemplate.queryForObject(
                "select root_comment_id from comment where id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(COMMENT_ID)
        );
        assertThat(BinaryUuidCodec.fromBytes(storedRootCommentId)).isEqualTo(COMMENT_ID);

        byte[] storedParentCommentId = jdbcTemplate.queryForObject(
                "select parent_comment_id from comment where id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(COMMENT_ID)
        );
        assertThat(storedParentCommentId).isNull();

        byte[] storedReplyToUserId = jdbcTemplate.queryForObject(
                "select reply_to_user_id from comment where id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(COMMENT_ID)
        );
        assertThat(storedReplyToUserId).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select version from comment where id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(COMMENT_ID)
        )).isZero();
    }

    @Test
    void editCasShouldNotModifyInactiveComment() {
        CommentDataObject comment = new CommentDataObject();
        comment.setId(COMMENT_ID);
        comment.setPostId(POST_ID);
        comment.setUserId(USER_ID);
        comment.setRootCommentId(COMMENT_ID);
        comment.setParentCommentId(null);
        comment.setReplyToUserId(null);
        comment.setContent("deleted");
        comment.setStatus(2);
        comment.setCreateTime(new Date());
        commentMapper.insert(comment);

        int updated = commentMapper.applyEdit(COMMENT_ID, 0L, "updated", new Date());

        assertThat(updated).isZero();
        CommentDataObject persisted = commentMapper.selectById(COMMENT_ID);
        assertThat(persisted.getContent()).isEqualTo("deleted");
        assertThat(persisted.getStatus()).isEqualTo(2);
        assertThat(persisted.getEditCount()).isZero();
    }

    @Test
    void activeThreadDeleteShouldSelectRootAndActiveRepliesOnly() {
        UUID replyId = UUID.fromString("00000000-0000-7000-8000-000000000404");
        UUID secondReplyId = UUID.fromString("00000000-0000-7000-8000-000000000405");
        UUID inactiveReplyId = UUID.fromString("00000000-0000-7000-8000-000000000406");
        insertRootComment(COMMENT_ID, USER_ID, POST_ID, 0, "parent", Instant.parse("2026-04-29T01:02:03Z"));
        insertReply(replyId, USER_ID, POST_ID, COMMENT_ID, USER_ID, 0, "reply", Instant.parse("2026-04-29T01:02:04Z"));
        insertReply(secondReplyId, USER_ID, POST_ID, COMMENT_ID, USER_ID, 0, "second", Instant.parse("2026-04-29T01:02:05Z"));
        insertReply(inactiveReplyId, USER_ID, POST_ID, COMMENT_ID, USER_ID, 1, "inactive", Instant.parse("2026-04-29T01:02:06Z"));

        List<CommentDataObject> thread = commentMapper.selectThreadForUpdate(COMMENT_ID);
        List<CommentTransitionTargetDataObject> targets = thread.stream()
                .filter(row -> row.getStatus() == 0)
                .map(row -> new CommentTransitionTargetDataObject(row.getId(), row.getVersion()))
                .toList();
        int updated = commentMapper.applyThreadDeletion(
                COMMENT_ID,
                targets,
                USER_ID,
                "author_delete",
                new Date()
        );

        assertThat(targets).extracting(CommentTransitionTargetDataObject::commentId)
                .containsExactly(COMMENT_ID, replyId, secondReplyId);
        assertThat(updated).isEqualTo(3);
        assertThat(commentMapper.selectById(COMMENT_ID).getStatus()).isEqualTo(1);
        assertThat(commentMapper.selectById(COMMENT_ID).getVersion()).isEqualTo(1L);
        assertThat(commentMapper.selectById(replyId).getStatus()).isEqualTo(1);
        assertThat(commentMapper.selectById(secondReplyId).getStatus()).isEqualTo(1);
        assertThat(commentMapper.selectById(inactiveReplyId).getStatus()).isEqualTo(1);
    }

    @Test
    void listRootCommentsShouldReadNewestRootsByRootCursor() {
        UUID olderRootId = UUID.fromString("00000000-0000-7000-8000-000000000407");
        UUID newerRootId = UUID.fromString("00000000-0000-7000-8000-000000000408");
        Instant olderTime = Instant.parse("2026-04-29T01:02:07Z");
        Instant newerTime = Instant.parse("2026-04-29T01:02:08Z");
        insertRootComment(olderRootId, USER_ID, POST_ID, 0, "older-root", olderTime);
        insertRootComment(newerRootId, USER_ID, POST_ID, 0, "newer-root", newerTime);

        List<UUID> ids = queryIds(
                """
                        select id
                        from comment
                        where post_id = ? and parent_comment_id is null
                          and (create_time < ? or (create_time = ? and id < ?))
                        order by create_time desc, id desc
                        limit 20
                        """,
                BinaryUuidCodec.toBytes(POST_ID),
                Date.from(newerTime),
                Date.from(newerTime),
                BinaryUuidCodec.toBytes(newerRootId)
        );

        assertThat(ids).containsExactly(olderRootId);
    }

    @Test
    void listRepliesShouldReadOlderRepliesByReplyCursor() {
        UUID rootCommentId = UUID.fromString("00000000-0000-7000-8000-000000000409");
        UUID olderReplyId = UUID.fromString("00000000-0000-7000-8000-00000000040a");
        UUID newerReplyId = UUID.fromString("00000000-0000-7000-8000-00000000040b");
        Instant rootTime = Instant.parse("2026-04-29T01:02:06Z");
        Instant olderTime = Instant.parse("2026-04-29T01:02:07Z");
        Instant newerTime = Instant.parse("2026-04-29T01:02:08Z");
        insertRootComment(rootCommentId, USER_ID, POST_ID, 0, "root", rootTime);
        insertReply(olderReplyId, USER_ID, POST_ID, rootCommentId, USER_ID, 0, "older-reply", olderTime);
        insertReply(newerReplyId, USER_ID, POST_ID, rootCommentId, USER_ID, 0, "newer-reply", newerTime);

        List<UUID> ids = queryIds(
                """
                        select id
                        from comment
                        where root_comment_id = ? and parent_comment_id is not null
                          and (create_time > ? or (create_time = ? and id > ?))
                        order by create_time asc, id asc
                        limit 20
                        """,
                BinaryUuidCodec.toBytes(rootCommentId),
                Date.from(olderTime),
                Date.from(olderTime),
                BinaryUuidCodec.toBytes(olderReplyId)
        );

        assertThat(ids).containsExactly(newerReplyId);
    }

    private void insertRootComment(UUID id, UUID userId, UUID postId, int status, String content, Instant createTime) {
        CommentDataObject comment = new CommentDataObject();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setRootCommentId(id);
        comment.setParentCommentId(null);
        comment.setReplyToUserId(null);
        comment.setContent(content);
        comment.setStatus(status);
        comment.setCreateTime(Date.from(createTime));
        commentMapper.insert(comment);
    }

    private void insertReply(
            UUID id,
            UUID userId,
            UUID postId,
            UUID rootCommentId,
            UUID replyToUserId,
            int status,
            String content,
            Instant createTime
    ) {
        CommentDataObject comment = new CommentDataObject();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setRootCommentId(rootCommentId);
        comment.setParentCommentId(rootCommentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setStatus(status);
        comment.setCreateTime(Date.from(createTime));
        commentMapper.insert(comment);
    }

    private List<UUID> queryIds(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> BinaryUuidCodec.fromBytes(rs.getBytes(1)), args);
    }
}
