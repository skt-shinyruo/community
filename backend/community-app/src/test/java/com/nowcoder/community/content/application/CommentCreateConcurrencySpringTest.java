package com.nowcoder.community.content.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.MyBatisCommentRepository;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
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

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class CommentCreateConcurrencySpringTest {

    private static final UUID POST_ID = uuid(8701);
    private static final UUID POST_AUTHOR_ID = uuid(8702);
    private static final UUID ROOT_ID = uuid(8703);
    private static final UUID ROOT_AUTHOR_ID = uuid(8704);
    private static final UUID DIRECT_PARENT_ID = uuid(8705);
    private static final UUID DIRECT_PARENT_AUTHOR_ID = uuid(8706);
    private static final UUID CREATOR_ID = uuid(8707);

    @Autowired
    private CommentApplicationService applicationService;

    @Autowired
    private MyBatisCommentRepository commentRepository;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private IdempotencyGuard idempotencyGuard;

    @MockBean
    private ContentSanitizer contentSanitizer;

    @MockBean
    private UserModerationGuard moderationGuard;

    @MockBean
    private PostContentRepository postContentRepository;

    @MockBean
    private PostCounterCache postCounterCache;

    @MockBean
    private CommentPageCache commentPageCache;

    @MockBean
    private SocialBlockQueryApi blockQueryApi;

    @MockBean
    private CommentDomainEventPublisher domainEventPublisher;

    @MockBean
    private ClientIpResolver clientIpResolver;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        reset(
                idempotencyGuard,
                contentSanitizer,
                moderationGuard,
                postContentRepository,
                postCounterCache,
                commentPageCache,
                blockQueryApi,
                domainEventPublisher
        );
        jdbcTemplate.update("delete from comment");
        assertThat(commentMapper.insert(rootRow())).isEqualTo(1);
        assertThat(commentMapper.insert(directParentRow())).isEqualTo(1);

        when(idempotencyGuard.executeRequired(
                anyString(),
                any(UUID.class),
                anyString(),
                anyString(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(6).get());
        when(postContentRepository.getById(POST_ID)).thenReturn(post());
        when(contentSanitizer.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockQueryApi.isEitherBlocked(any(UUID.class), any(UUID.class))).thenReturn(false);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void directParentDeletionShouldMakeBlockedNestedCreateRejectStaleParentWithoutSideEffects() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch deletionApplied = new CountDownLatch(1);
        CountDownLatch releaseDeletion = new CountDownLatch(1);
        Future<CommentDeletionResult> deletion = executor.submit(() -> transaction.execute(status -> {
            CommentDeletionResult result = commentRepository.apply(new CommentDeletion(
                    DIRECT_PARENT_ID,
                    0L,
                    DIRECT_PARENT_AUTHOR_ID,
                    "author_delete",
                    new Date(2_000_000L)
            ));
            deletionApplied.countDown();
            await(releaseDeletion);
            return result;
        }));

        assertThat(deletionApplied.await(5, TimeUnit.SECONDS)).isTrue();
        CountDownLatch creationStarted = new CountDownLatch(1);
        Future<Throwable> creation = submitNestedCreate("direct-delete-race", creationStarted);
        try {
            assertThat(creationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertBlocked(creation);
        } finally {
            releaseDeletion.countDown();
        }

        assertThat(deletion.get(5, TimeUnit.SECONDS).status()).isEqualTo(CommentTransitionStatus.APPLIED);
        assertHiddenNotFound(creation.get(5, TimeUnit.SECONDS));
        assertRejectedCreateHasNoSideEffects(1);
    }

    @Test
    void rootThreadDeletionAndNestedCreateShouldFinishWithoutReverseLockDeadlockOrSideEffects() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch deletionApplied = new CountDownLatch(1);
        CountDownLatch releaseDeletion = new CountDownLatch(1);
        Future<CommentDeletionResult> deletion = executor.submit(() -> transaction.execute(status -> {
            List<CommentSnapshot> activeThread = commentRepository.getActiveThreadSnapshots(ROOT_ID);
            CommentDeletionResult result = commentRepository.apply(CommentThreadDeletion.from(
                    new CommentDeletion(
                            ROOT_ID,
                            0L,
                            ROOT_AUTHOR_ID,
                            "author_delete",
                            new Date(2_000_000L)
                    ),
                    activeThread
            ));
            deletionApplied.countDown();
            await(releaseDeletion);
            return result;
        }));

        assertThat(deletionApplied.await(5, TimeUnit.SECONDS)).isTrue();
        CountDownLatch creationStarted = new CountDownLatch(1);
        Future<Throwable> creation = submitNestedCreate("thread-delete-race", creationStarted);
        try {
            assertThat(creationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertBlocked(creation);
        } finally {
            releaseDeletion.countDown();
        }

        CommentDeletionResult result = deletion.get(5, TimeUnit.SECONDS);
        assertThat(result.status()).isEqualTo(CommentTransitionStatus.APPLIED);
        assertThat(result.deletedCommentIds()).containsExactly(ROOT_ID, DIRECT_PARENT_ID);
        assertHiddenNotFound(creation.get(5, TimeUnit.SECONDS));
        assertRejectedCreateHasNoSideEffects(0);
    }

    @Test
    void createShouldUpdateRedisCachesOnlyAfterOuterTransactionCommits() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            applicationService.create(
                    "after-commit-cache-effects",
                    new CreateCommentCommand(CREATOR_ID, POST_ID, null, "after commit")
            );

            verify(postContentRepository).incrementCommentCount(POST_ID, 1);
            verify(postCounterCache, never()).incrementCommentCount(any(UUID.class), anyLong());
            verify(commentPageCache, never()).evictPost(any(UUID.class));
        });

        verify(postCounterCache).incrementCommentCount(POST_ID, 1L);
        verify(commentPageCache).evictPost(POST_ID);
    }

    private Future<Throwable> submitNestedCreate(String idempotencyKey, CountDownLatch creationStarted) {
        return executor.submit(() -> {
            try {
                creationStarted.countDown();
                applicationService.create(
                        idempotencyKey,
                        new CreateCommentCommand(CREATOR_ID, POST_ID, DIRECT_PARENT_ID, "late reply")
                );
                return null;
            } catch (Throwable error) {
                return error;
            }
        });
    }

    private static void assertBlocked(Future<?> creation) {
        assertThatThrownBy(() -> creation.get(250, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private static void assertHiddenNotFound(Throwable failure) {
        assertThat(failure)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    private void assertRejectedCreateHasNoSideEffects(int activeCommentCount) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from comment", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from comment where status = 0",
                Integer.class
        )).isEqualTo(activeCommentCount);
        verify(contentSanitizer, never()).filter(anyString());
        verify(blockQueryApi, never()).isEitherBlocked(any(UUID.class), any(UUID.class));
        verify(postContentRepository, never()).incrementCommentCount(any(UUID.class), anyInt());
        verify(postCounterCache, never()).incrementCommentCount(any(UUID.class), anyLong());
        verify(commentPageCache, never()).evictPost(any(UUID.class));
        verify(domainEventPublisher, never()).commentCreated(any(CommentCreatedDomainEvent.class));
    }

    private static DiscussPost post() {
        DiscussPost post = new DiscussPost();
        post.setId(POST_ID);
        post.setUserId(POST_AUTHOR_ID);
        return post;
    }

    private static CommentDataObject rootRow() {
        return aComment()
                .id(ROOT_ID)
                .postId(POST_ID)
                .userId(ROOT_AUTHOR_ID)
                .rootCommentId(ROOT_ID)
                .parentCommentId(null)
                .replyToUserId(null)
                .status(0)
                .version(0L)
                .createTime(new Date(1_000_000L))
                .buildDataObject();
    }

    private static CommentDataObject directParentRow() {
        return aComment()
                .id(DIRECT_PARENT_ID)
                .postId(POST_ID)
                .userId(DIRECT_PARENT_AUTHOR_ID)
                .rootCommentId(ROOT_ID)
                .parentCommentId(ROOT_ID)
                .replyToUserId(ROOT_AUTHOR_ID)
                .status(0)
                .version(0L)
                .createTime(new Date(1_100_000L))
                .buildDataObject();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for concurrent comment operation");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for concurrent comment operation", error);
        }
    }
}
