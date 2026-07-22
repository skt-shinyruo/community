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
    void rootKeysetShouldUseDescendingIdTieBreakWithoutMutationGaps() {
        UUID oldestRootId = UUID.fromString("00000000-0000-7000-8000-000000000407");
        UUID olderRootId = UUID.fromString("00000000-0000-7000-8000-000000000408");
        UUID boundaryRootId = UUID.fromString("00000000-0000-7000-8000-000000000409");
        UUID newestRootId = UUID.fromString("00000000-0000-7000-8000-00000000040a");
        UUID insertedBeforeBoundaryId = UUID.fromString("00000000-0000-7000-8000-00000000040b");
        Instant sharedTime = Instant.parse("2026-04-29T01:02:07.123Z");
        insertRootComment(oldestRootId, USER_ID, POST_ID, 0, "oldest-root", sharedTime);
        insertRootComment(olderRootId, USER_ID, POST_ID, 0, "older-root", sharedTime);
        insertRootComment(boundaryRootId, USER_ID, POST_ID, 0, "boundary-root", sharedTime);
        insertRootComment(newestRootId, USER_ID, POST_ID, 0, "newest-root", sharedTime);

        List<CommentDataObject> firstPage = commentMapper.selectRootCommentsAfter(
                POST_ID, null, null, 2);
        jdbcTemplate.update("delete from comment where id = ?", BinaryUuidCodec.toBytes(newestRootId));
        insertRootComment(insertedBeforeBoundaryId, USER_ID, POST_ID, 0, "inserted-root", sharedTime);
        List<CommentDataObject> secondPage = commentMapper.selectRootCommentsAfter(
                POST_ID, Date.from(sharedTime), boundaryRootId, 10);

        assertThat(firstPage).extracting(CommentDataObject::getId)
                .containsExactly(newestRootId, boundaryRootId);
        assertThat(secondPage).extracting(CommentDataObject::getId)
                .containsExactly(olderRootId, oldestRootId);
    }

    @Test
    void replyKeysetShouldUseAscendingIdTieBreakWithoutMutationGaps() {
        UUID rootCommentId = UUID.fromString("00000000-0000-7000-8000-000000000420");
        UUID oldestReplyId = UUID.fromString("00000000-0000-7000-8000-000000000411");
        UUID boundaryReplyId = UUID.fromString("00000000-0000-7000-8000-000000000412");
        UUID newerReplyId = UUID.fromString("00000000-0000-7000-8000-000000000413");
        UUID newestReplyId = UUID.fromString("00000000-0000-7000-8000-000000000414");
        UUID insertedBeforeBoundaryId = UUID.fromString("00000000-0000-7000-8000-000000000410");
        Instant rootTime = Instant.parse("2026-04-29T01:02:06Z");
        Instant sharedTime = Instant.parse("2026-04-29T01:02:07.123Z");
        insertRootComment(rootCommentId, USER_ID, POST_ID, 0, "root", rootTime);
        insertReply(oldestReplyId, USER_ID, POST_ID, rootCommentId, USER_ID, 0, "oldest-reply", sharedTime);
        insertReply(boundaryReplyId, USER_ID, POST_ID, rootCommentId, USER_ID, 0, "boundary-reply", sharedTime);
        insertReply(newerReplyId, USER_ID, POST_ID, rootCommentId, USER_ID, 0, "newer-reply", sharedTime);
        insertReply(newestReplyId, USER_ID, POST_ID, rootCommentId, USER_ID, 0, "newest-reply", sharedTime);

        List<CommentDataObject> firstPage = commentMapper.selectRepliesAfter(
                rootCommentId, null, null, 2);
        jdbcTemplate.update("delete from comment where id = ?", BinaryUuidCodec.toBytes(oldestReplyId));
        insertReply(insertedBeforeBoundaryId, USER_ID, POST_ID, rootCommentId, USER_ID, 0,
                "inserted-reply", sharedTime);
        List<CommentDataObject> secondPage = commentMapper.selectRepliesAfter(
                rootCommentId, Date.from(sharedTime), boundaryReplyId, 10);

        assertThat(firstPage).extracting(CommentDataObject::getId)
                .containsExactly(oldestReplyId, boundaryReplyId);
        assertThat(secondPage).extracting(CommentDataObject::getId)
                .containsExactly(newerReplyId, newestReplyId);
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
}
