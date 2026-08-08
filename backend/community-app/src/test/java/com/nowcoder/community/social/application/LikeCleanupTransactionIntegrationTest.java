package com.nowcoder.community.social.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.api.action.SocialLikeActionApi.SetLikeCommand;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import com.nowcoder.community.social.infrastructure.event.OutboxSocialDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Sql("/social/like-target-state-schema.sql")
class LikeCleanupTransactionIntegrationTest {

    private static final UUID TARGET_ID = uuid(620);
    private static final UUID OWNER_ID = uuid(621);
    private static final Instant DELETED_AT = Instant.parse("2026-07-15T08:30:00Z");

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LikeTargetStateRepository targetStateRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @SpyBean
    private OutboxSocialDomainEventPublisher outboxPublisher;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(outboxPublisher);
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from social_like");
        jdbcTemplate.update("delete from social_like_relation_version");
        jdbcTemplate.update("delete from social_user_like_count");
        jdbcTemplate.update("delete from social_like_target_state");
    }

    @Test
    void publisherFailureInSecondPageShouldKeepCommittedFirstPageAndRetryOnlyRemainingLikes() {
        List<UUID> relationInstanceIds = seedLikes(205);
        List<UUID> firstAttemptInstances = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger publicationAttempt = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            LikeChangedDomainEvent event = invocation.getArgument(0);
            firstAttemptInstances.add(event.relationInstanceId());
            if (publicationAttempt.incrementAndGet() == 201) {
                throw new IllegalStateException("publish failed on second page");
            }
            return null;
        }).when(outboxPublisher).publishLikeChanged(any());
        CleanupDeletedContentLikesCommand command = deletionCommand();

        assertThatThrownBy(() -> likeApplicationService.cleanupDeletedContentLikes(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed on second page");

        assertThat(targetStateCount()).isEqualTo(1L);
        assertThat(targetStatus()).isEqualTo("DELETED");
        assertThat(likeCount()).isEqualTo(5L);
        assertThat(ownerLikeCount()).isEqualTo(5L);
        assertThat(outboxCount()).isEqualTo(200L);
        assertThat(storedRelationInstanceIds())
                .containsExactlyInAnyOrderElementsOf(relationInstanceIds.subList(200, 205));
        assertThat(firstAttemptInstances)
                .containsExactlyElementsOf(relationInstanceIds.subList(0, 201));

        reset(outboxPublisher);
        List<UUID> retryInstances = new java.util.concurrent.CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            LikeChangedDomainEvent event = invocation.getArgument(0);
            retryInstances.add(event.relationInstanceId());
            return null;
        }).when(outboxPublisher).publishLikeChanged(any());
        assertThat(likeApplicationService.cleanupDeletedContentLikes(command)).isEqualTo(5L);
        assertThat(targetStatus()).isEqualTo("DELETED");
        assertThat(targetSourceVersion()).isEqualTo(42L);
        assertThat(likeCount()).isZero();
        assertThat(ownerLikeCount()).isZero();
        assertThat(outboxCount()).isEqualTo(205L);
        assertThat(retryInstances).containsExactlyElementsOf(relationInstanceIds.subList(200, 205));

        assertThat(likeApplicationService.cleanupDeletedContentLikes(command)).isZero();
        assertThat(outboxCount()).isEqualTo(205L);
    }

    @Test
    void deletedTargetShouldRejectNewLikeWithoutCallingContentOwner() {
        assertThat(likeApplicationService.cleanupDeletedContentLikes(deletionCommand())).isZero();

        assertThatThrownBy(() -> likeApplicationService.setLike(
                new SetLikeCommand(uuid(1), POST, TARGET_ID, true, OWNER_ID, TARGET_ID)
        )).isInstanceOf(BusinessException.class);

        assertThat(targetStatus()).isEqualTo("DELETED");
        assertThat(likeCount()).isZero();
        assertThat(outboxCount()).isZero();
    }

    @Test
    void publisherFailureInSecondCommentPageShouldResumePostCascadeFromCommittedRelations() {
        UUID commentId = uuid(622);
        List<UUID> relationInstanceIds = seedCommentLikes(commentId, 205);
        AtomicInteger publicationAttempt = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (publicationAttempt.incrementAndGet() == 201) {
                throw new IllegalStateException("publish failed on second comment page");
            }
            return null;
        }).when(outboxPublisher).publishLikeChanged(any());
        CleanupDeletedContentLikesCommand command = deletionCommand();

        assertThatThrownBy(() -> likeApplicationService.cleanupDeletedContentLikes(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed on second comment page");

        assertThat(commentLikeCount()).isEqualTo(5L);
        assertThat(ownerLikeCount()).isEqualTo(5L);
        assertThat(outboxCount()).isEqualTo(200L);
        assertThat(storedCommentRelationInstanceIds())
                .containsExactlyInAnyOrderElementsOf(relationInstanceIds.subList(200, 205));

        reset(outboxPublisher);
        List<LikeChangedDomainEvent> retryEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            retryEvents.add(invocation.getArgument(0));
            return null;
        }).when(outboxPublisher).publishLikeChanged(any());

        assertThat(likeApplicationService.cleanupDeletedContentLikes(command)).isEqualTo(5L);
        assertThat(commentLikeCount()).isZero();
        assertThat(ownerLikeCount()).isZero();
        assertThat(outboxCount()).isEqualTo(205L);
        assertThat(retryEvents)
                .allSatisfy(event -> {
                    assertThat(event.entityType()).isEqualTo(COMMENT);
                    assertThat(event.entityId()).isEqualTo(commentId);
                    assertThat(event.postId()).isEqualTo(TARGET_ID);
                    assertThat(event.liked()).isFalse();
                })
                .extracting(LikeChangedDomainEvent::relationInstanceId)
                .containsExactlyElementsOf(relationInstanceIds.subList(200, 205));
    }

    @Test
    void commentLikeShouldWaitForConcurrentRootDeletionAndThenObserveTheFence() throws Exception {
        UUID commentId = uuid(623);
        targetStateRepository.insertActiveIfAbsent(POST, TARGET_ID);
        CountDownLatch deletionLockedRoot = new CountDownLatch(1);
        CountDownLatch finishDeletion = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> deletion = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        LikeTargetState current = targetStateRepository.findForUpdate(POST, TARGET_ID);
                        deletionLockedRoot.countDown();
                        await(finishDeletion);
                        assertThat(targetStateRepository.saveIfNewer(current.applyDeletion(
                                "content:post-deleted:620",
                                42L,
                                DELETED_AT
                        ))).isTrue();
                    }));
            assertThat(deletionLockedRoot.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> like = executor.submit(() -> likeApplicationService.setLike(
                    new SetLikeCommand(uuid(1), COMMENT, commentId, true, OWNER_ID, TARGET_ID)
            ));

            assertThatThrownBy(() -> like.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            finishDeletion.countDown();
            deletion.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> like.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(BusinessException.class);
            assertThat(commentLikeCount()).isZero();
            assertThat(outboxCount()).isZero();
        } finally {
            finishDeletion.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private List<UUID> seedLikes(int count) {
        List<UUID> relationInstanceIds = IntStream.range(0, count)
                .mapToObj(index -> uuid(20_000L + index))
                .toList();
        List<Object[]> rows = IntStream.range(0, count)
                .mapToObj(index -> new Object[]{
                        BinaryUuidCodec.toBytes(relationInstanceIds.get(index)),
                        BinaryUuidCodec.toBytes(uuid(10_000L + index)),
                        POST,
                        BinaryUuidCodec.toBytes(TARGET_ID),
                        BinaryUuidCodec.toBytes(OWNER_ID),
                        BinaryUuidCodec.toBytes(TARGET_ID)
                })
                .toList();
        jdbcTemplate.batchUpdate(
                "insert into social_like(relation_instance_id, user_id, entity_type, entity_id, entity_user_id, post_id, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, current_timestamp)",
                rows
        );
        jdbcTemplate.update(
                "insert into social_user_like_count(user_id, like_count, updated_at) "
                        + "values (?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(OWNER_ID),
                count
        );
        return relationInstanceIds;
    }

    private List<UUID> seedCommentLikes(UUID commentId, int count) {
        List<UUID> relationInstanceIds = IntStream.range(0, count)
                .mapToObj(index -> uuid(40_000L + index))
                .toList();
        List<Object[]> rows = IntStream.range(0, count)
                .mapToObj(index -> new Object[]{
                        BinaryUuidCodec.toBytes(relationInstanceIds.get(index)),
                        BinaryUuidCodec.toBytes(uuid(30_000L + index)),
                        COMMENT,
                        BinaryUuidCodec.toBytes(commentId),
                        BinaryUuidCodec.toBytes(OWNER_ID),
                        BinaryUuidCodec.toBytes(TARGET_ID)
                })
                .toList();
        jdbcTemplate.batchUpdate(
                "insert into social_like(relation_instance_id, user_id, entity_type, entity_id, entity_user_id, post_id, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, current_timestamp)",
                rows
        );
        jdbcTemplate.update(
                "insert into social_user_like_count(user_id, like_count, updated_at) "
                        + "values (?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(OWNER_ID),
                count
        );
        return relationInstanceIds;
    }

    private CleanupDeletedContentLikesCommand deletionCommand() {
        return new CleanupDeletedContentLikesCommand(
                POST,
                TARGET_ID,
                "content:post-deleted:620",
                42L,
                DELETED_AT
        );
    }

    private long targetStateCount() {
        return queryCount(
                "select count(*) from social_like_target_state where entity_type = ? and entity_id = ?",
                POST,
                BinaryUuidCodec.toBytes(TARGET_ID)
        );
    }

    private String targetStatus() {
        return jdbcTemplate.queryForObject(
                "select status from social_like_target_state where entity_type = ? and entity_id = ?",
                String.class,
                POST,
                BinaryUuidCodec.toBytes(TARGET_ID)
        );
    }

    private long targetSourceVersion() {
        Long value = jdbcTemplate.queryForObject(
                "select source_version from social_like_target_state where entity_type = ? and entity_id = ?",
                Long.class,
                POST,
                BinaryUuidCodec.toBytes(TARGET_ID)
        );
        return value == null ? 0L : value;
    }

    private long likeCount() {
        return queryCount(
                "select count(*) from social_like where entity_type = ? and entity_id = ?",
                POST,
                BinaryUuidCodec.toBytes(TARGET_ID)
        );
    }

    private long commentLikeCount() {
        return queryCount(
                "select count(*) from social_like where entity_type = ? and post_id = ?",
                COMMENT,
                BinaryUuidCodec.toBytes(TARGET_ID)
        );
    }

    private List<UUID> storedRelationInstanceIds() {
        return jdbcTemplate.query(
                "select relation_instance_id from social_like where entity_type = ? and entity_id = ? order by user_id",
                (resultSet, rowNum) -> BinaryUuidCodec.fromBytes(resultSet.getBytes("relation_instance_id")),
                POST,
                BinaryUuidCodec.toBytes(TARGET_ID)
        );
    }

    private List<UUID> storedCommentRelationInstanceIds() {
        return jdbcTemplate.query(
                "select relation_instance_id from social_like where entity_type = ? and post_id = ? order by entity_id, user_id",
                (resultSet, rowNum) -> BinaryUuidCodec.fromBytes(resultSet.getBytes("relation_instance_id")),
                COMMENT,
                BinaryUuidCodec.toBytes(TARGET_ID)
        );
    }

    private long ownerLikeCount() {
        Long value = jdbcTemplate.queryForObject(
                "select like_count from social_user_like_count where user_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(OWNER_ID)
        );
        return value == null ? 0L : value;
    }

    private long outboxCount() {
        return queryCount("select count(*) from outbox_event");
    }

    private long queryCount(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for test latch", exception);
        }
    }
}
