package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Sql("/social/like-target-state-schema.sql")
class MyBatisLikeTargetStateRepositoryTest {

    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-7000-8000-000000000641");

    @Autowired
    private MyBatisLikeTargetStateRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from social_like");
        jdbcTemplate.update("delete from social_like_target_state");
    }

    @Test
    void insertActiveIfAbsentShouldPreserveExistingDeletionFence() {
        assertThat(repository.insertActiveIfAbsent(POST, TARGET_ID)).isTrue();
        LikeTargetState deleted = LikeTargetState.active(POST, TARGET_ID).applyDeletion(
                "content:post-deleted:641",
                10L,
                Instant.parse("2026-07-15T08:30:00Z")
        );
        assertThat(repository.saveIfNewer(deleted)).isTrue();

        assertThat(repository.insertActiveIfAbsent(POST, TARGET_ID)).isFalse();

        assertThat(repository.findByTarget(POST, TARGET_ID))
                .hasValueSatisfying(state -> {
                    assertThat(state.isDeleted()).isTrue();
                    assertThat(state.sourceVersion()).isEqualTo(10L);
                });
    }

    @Test
    void findForUpdateShouldSerializeWritersForTheSameTargetRow() throws Exception {
        repository.insertActiveIfAbsent(POST, TARGET_ID);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            repository.findForUpdate(POST, TARGET_ID);
            firstLocked.countDown();
            await(releaseFirst);
        }));

        try {
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<LikeTargetState> second = executor.submit(() -> {
                secondStarted.countDown();
                return new TransactionTemplate(transactionManager)
                        .execute(status -> repository.findForUpdate(POST, TARGET_ID));
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS).isActive()).isTrue();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentCompareAndSetShouldKeepTheNewestSourceVersion() throws Exception {
        repository.insertActiveIfAbsent(POST, TARGET_ID);
        LikeTargetState older = LikeTargetState.active(POST, TARGET_ID).applyDeletion(
                "content:post-deleted:older",
                10L,
                Instant.parse("2026-07-15T08:30:00Z")
        );
        LikeTargetState newer = LikeTargetState.active(POST, TARGET_ID).applyDeletion(
                "content:post-deleted:newer",
                20L,
                Instant.parse("2026-07-15T08:31:00Z")
        );
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> olderWrite = executor.submit(() -> {
                await(start);
                return repository.saveIfNewer(older);
            });
            Future<Boolean> newerWrite = executor.submit(() -> {
                await(start);
                return repository.saveIfNewer(newer);
            });

            olderWrite.get(5, TimeUnit.SECONDS);
            newerWrite.get(5, TimeUnit.SECONDS);

            assertThat(repository.findByTarget(POST, TARGET_ID))
                    .hasValueSatisfying(state -> {
                        assertThat(state.isDeleted()).isTrue();
                        assertThat(state.sourceVersion()).isEqualTo(20L);
                        assertThat(state.sourceEventId()).isEqualTo("content:post-deleted:newer");
                    });
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void scanShouldReturnOnlyDeletedTargetsThatStillHaveLikesUsingKeysetOrder() {
        UUID first = uuid(642);
        UUID second = uuid(643);
        UUID deletedWithoutLikes = uuid(644);
        UUID activeWithLikes = uuid(645);
        deleteTarget(first, 10L);
        deleteTarget(second, 11L);
        deleteTarget(deletedWithoutLikes, 12L);
        repository.insertActiveIfAbsent(POST, activeWithLikes);
        insertLike(uuid(7001), uuid(1), first);
        insertLike(uuid(7002), uuid(2), second);
        insertLike(uuid(7003), uuid(3), activeWithLikes);

        List<LikeTargetState> firstPage = repository.scanDeletedTargetsWithLikesAfter(
                POST,
                new UUID(0L, 0L),
                1
        );
        List<LikeTargetState> secondPage = repository.scanDeletedTargetsWithLikesAfter(
                POST,
                first,
                10
        );

        assertThat(firstPage).extracting(LikeTargetState::entityId).containsExactly(first);
        assertThat(secondPage).extracting(LikeTargetState::entityId).containsExactly(second);
        assertThat(secondPage.get(0).sourceEventId()).isEqualTo("content:post-deleted:" + second);
        assertThat(secondPage.get(0).sourceVersion()).isEqualTo(11L);
    }

    private void deleteTarget(UUID entityId, long sourceVersion) {
        repository.insertActiveIfAbsent(POST, entityId);
        assertThat(repository.saveIfNewer(LikeTargetState.active(POST, entityId).applyDeletion(
                "content:post-deleted:" + entityId,
                sourceVersion,
                Instant.parse("2026-07-15T08:30:00Z").plusSeconds(sourceVersion)
        ))).isTrue();
    }

    private void insertLike(UUID relationInstanceId, UUID actorUserId, UUID entityId) {
        jdbcTemplate.update(
                "insert into social_like(relation_instance_id, user_id, entity_type, entity_id, entity_user_id) "
                        + "values (?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(relationInstanceId),
                BinaryUuidCodec.toBytes(actorUserId),
                POST,
                BinaryUuidCodec.toBytes(entityId),
                BinaryUuidCodec.toBytes(uuid(999))
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
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

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to synchronize concurrent writers", exception);
        }
    }
}
