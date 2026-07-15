package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class CommentThreadLockingPersistenceIntegrationTest {

    private static final UUID ROOT_ID = uuid(8601);
    private static final UUID AUTHOR_ID = uuid(8602);
    private static final UUID POST_ID = uuid(8603);

    @Autowired
    private MyBatisCommentRepository repository;

    @Autowired
    private CommentMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private ClientIpResolver clientIpResolver;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from comment");
        CommentDataObject root = aComment()
                .id(ROOT_ID)
                .userId(AUTHOR_ID)
                .postId(POST_ID)
                .rootCommentId(ROOT_ID)
                .version(0L)
                .createTime(new Date(1_000_000L))
                .buildDataObject();
        assertThat(mapper.insert(root)).isEqualTo(1);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void replyCreateShouldWaitForRootDeletionAndMustNotEscapeTheFrozenThread() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch rootLocked = new CountDownLatch(1);
        CountDownLatch replyAttempted = new CountDownLatch(1);
        CountDownLatch allowDeletion = new CountDownLatch(1);

        Future<CommentDeletionResult> deletion = executor.submit(() -> transaction.execute(status -> {
            List<CommentSnapshot> activeThread = repository.getActiveThreadSnapshots(ROOT_ID);
            rootLocked.countDown();
            await(allowDeletion);
            CommentDeletion rootDeletion = new CommentDeletion(
                    ROOT_ID,
                    0L,
                    AUTHOR_ID,
                    "author_delete",
                    new Date(2_000_000L)
            );
            return repository.apply(CommentThreadDeletion.from(rootDeletion, activeThread));
        }));

        assertThat(rootLocked.await(5, TimeUnit.SECONDS)).isTrue();
        Future<UUID> reply = executor.submit(() -> transaction.execute(status -> {
            replyAttempted.countDown();
            return repository.create(new CommentDraft(
                    AUTHOR_ID,
                    POST_ID,
                    ROOT_ID,
                    ROOT_ID,
                    AUTHOR_ID,
                    "late reply",
                    new Date(1_500_000L)
            ));
        }));

        try {
            assertThat(replyAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> reply.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            allowDeletion.countDown();
        }

        CommentDeletionResult result = deletion.get(5, TimeUnit.SECONDS);
        assertThat(result.status()).isEqualTo(CommentTransitionStatus.APPLIED);
        assertThat(result.deletedCommentIds()).containsExactly(ROOT_ID);
        assertThatThrownBy(() -> reply.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(BusinessException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from comment", Integer.class)).isEqualTo(1);
        assertThat(mapper.selectById(ROOT_ID).getStatus()).isEqualTo(1);
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
