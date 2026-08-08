package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.BookmarkApplicationService;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private BookmarkApplicationService bookmarkApplicationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_bookmark");
        jdbcTemplate.update("delete from discuss_post");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void selectBookmarkedPostsShouldReadActivePostWithoutLegacyContentColumn() {
        Date createTime = Date.from(Instant.parse("2026-07-20T12:00:00Z"));
        insertPost(0, createTime);
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

    @Test
    void atomicInsertShouldReturnCreatedDuplicateAndDeletedOutcomes() {
        Date createTime = Date.from(Instant.parse("2026-07-20T12:00:00Z"));
        insertPost(0, createTime);

        assertThat(bookmarkMapper.insertBookmarkForActivePost(BOOKMARKER_ID, POST_ID, createTime)).isEqualTo(1);
        assertThat(bookmarkMapper.insertBookmarkForActivePost(BOOKMARKER_ID, POST_ID, createTime)).isZero();

        jdbcTemplate.update(
                "delete from post_bookmark where user_id = ? and post_id = ?",
                BinaryUuidCodec.toBytes(BOOKMARKER_ID),
                BinaryUuidCodec.toBytes(POST_ID)
        );
        jdbcTemplate.update(
                "update discuss_post set status = 2 where id = ?",
                BinaryUuidCodec.toBytes(POST_ID)
        );

        assertThat(bookmarkMapper.insertBookmarkForActivePost(BOOKMARKER_ID, POST_ID, createTime)).isZero();
        assertThat(bookmarkMapper.existsActivePost(POST_ID)).isZero();
        assertThat(bookmarkMapper.existsBookmark(BOOKMARKER_ID, POST_ID)).isZero();
    }

    @Test
    void bookmarkArrivingBehindUncommittedDeletionShouldWaitAndThenBeRejected() throws Exception {
        Date createTime = Date.from(Instant.parse("2026-07-20T12:00:00Z"));
        insertPost(0, createTime);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch deletionApplied = new CountDownLatch(1);
        CountDownLatch releaseDeletion = new CountDownLatch(1);
        CountDownLatch bookmarkStarted = new CountDownLatch(1);

        Future<?> deletion = executor.submit(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "update discuss_post set status = 2 where id = ?",
                    BinaryUuidCodec.toBytes(POST_ID)
            );
            deletionApplied.countDown();
            await(releaseDeletion);
        }));

        assertThat(deletionApplied.await(5, TimeUnit.SECONDS)).isTrue();
        Future<Throwable> bookmark = executor.submit(() -> {
            bookmarkStarted.countDown();
            try {
                bookmarkApplicationService.add(BOOKMARKER_ID, POST_ID);
                return null;
            } catch (Throwable error) {
                return error;
            }
        });

        try {
            assertThat(bookmarkStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> bookmark.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseDeletion.countDown();
        }

        deletion.get(5, TimeUnit.SECONDS);
        assertThat(bookmark.get(5, TimeUnit.SECONDS))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));
        assertThat(bookmarkMapper.existsBookmark(BOOKMARKER_ID, POST_ID)).isZero();
    }

    private void insertPost(int status, Date createTime) {
        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, title, type, status, create_time, comment_count, score) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(POST_ID),
                BinaryUuidCodec.toBytes(AUTHOR_ID),
                "收藏查询标题",
                0,
                status,
                Timestamp.from(createTime.toInstant()),
                3,
                8.5
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for concurrent bookmark operation");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for concurrent bookmark operation", error);
        }
    }
}
