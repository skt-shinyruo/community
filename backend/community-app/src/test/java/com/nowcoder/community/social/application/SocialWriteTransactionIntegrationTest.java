package com.nowcoder.community.social.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.social.application.command.BlockCommand;
import com.nowcoder.community.social.application.command.FollowCommand;
import com.nowcoder.community.social.api.action.SocialLikeActionApi.SetLikeCommand;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.infrastructure.event.OutboxSocialDomainEventPublisher;
import com.nowcoder.community.social.infrastructure.persistence.MyBatisFollowRepository;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.application.UserReadApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class SocialWriteTransactionIntegrationTest {

    private static final UUID ACTOR_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000031");
    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000032");

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private FollowApplicationService followApplicationService;

    @Autowired
    private BlockApplicationService blockApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private OutboxSocialDomainEventPublisher outboxPublisher;

    @SpyBean
    private MyBatisFollowRepository followRepository;

    @MockBean
    private UserReadApplicationService userReadApplicationService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(outboxPublisher, followRepository, userReadApplicationService);
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from social_like");
        jdbcTemplate.update("delete from social_user_like_count");
        jdbcTemplate.update("delete from social_follow");
        jdbcTemplate.update("delete from social_block_version_log");
        jdbcTemplate.update("delete from social_block");
        jdbcTemplate.update("delete from social_user_pair_lock");
        jdbcTemplate.update("update social_block_version_counter set current_version = 0 where id = 1");
    }

    @Test
    void likeRelationInstanceAndOutboxShouldRollbackTogetherThenRetryWithMatchingIdentity() {
        allowTargetUserLookup();
        AtomicReference<UUID> failedInstance = new AtomicReference<>();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            LikeChangedDomainEvent event = invocation.getArgument(0);
            failedInstance.set(event.relationInstanceId());
            assertThat(likeRelationCount()).isOne();
            assertThat(storedRelationInstanceId()).hasValue(event.relationInstanceId());
            assertThat(targetLikeCountRow()).isOne();
            assertThat(outboxCount()).isOne();
            throw publicationFailure();
        }).when(outboxPublisher).publishLikeChanged(any());

        assertThatThrownBy(() -> likeApplicationService.setLike(
                new SetLikeCommand(ACTOR_USER_ID, USER, TARGET_USER_ID, true, TARGET_USER_ID, null)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(likeRelationCount()).isZero();
        assertThat(storedRelationInstanceId()).isEmpty();
        assertThat(targetLikeCountRow()).isZero();
        assertThat(outboxCount()).isZero();

        reset(outboxPublisher);
        AtomicReference<UUID> retryEventInstance = new AtomicReference<>();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            LikeChangedDomainEvent event = invocation.getArgument(0);
            retryEventInstance.set(event.relationInstanceId());
            return null;
        }).when(outboxPublisher).publishLikeChanged(any());

        likeApplicationService.setLike(
                new SetLikeCommand(ACTOR_USER_ID, USER, TARGET_USER_ID, true, TARGET_USER_ID, null)
        );

        assertThat(storedRelationInstanceId()).hasValue(retryEventInstance.get());
        assertThat(retryEventInstance.get()).isNotEqualTo(failedInstance.get());
        assertThat(targetLikeCountRow()).isOne();
        assertThat(outboxCount()).isOne();
    }

    @Test
    void followAndOutboxInsertShouldRollbackTogetherWhenPublicationFails() {
        allowTargetUserLookup();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(followRelationCount(ACTOR_USER_ID, TARGET_USER_ID)).isOne();
            assertThat(outboxCount()).isOne();
            throw publicationFailure();
        }).when(outboxPublisher).publishFollowCreated(any());

        assertThatThrownBy(() -> followApplicationService.follow(
                new FollowCommand(ACTOR_USER_ID, USER, TARGET_USER_ID)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(followRelationCount(ACTOR_USER_ID, TARGET_USER_ID)).isZero();
        assertThat(outboxCount()).isZero();
    }

    @Test
    void blockRelationshipDeletionAndOutboxInsertShouldRollbackTogetherWhenPublicationFails() {
        insertFollow(ACTOR_USER_ID, TARGET_USER_ID);
        insertFollow(TARGET_USER_ID, ACTOR_USER_ID);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(blockRelationCount()).isOne();
            assertThat(allFollowRelationCount()).isZero();
            assertThat(blockVersionLogCount()).isOne();
            assertThat(currentBlockVersion()).isOne();
            assertThat(outboxCount()).isOne();
            throw publicationFailure();
        }).when(outboxPublisher).publishBlockRelationChanged(any());

        assertThatThrownBy(() -> blockApplicationService.block(
                new BlockCommand(ACTOR_USER_ID, TARGET_USER_ID)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(blockRelationCount()).isZero();
        assertThat(followRelationCount(ACTOR_USER_ID, TARGET_USER_ID)).isOne();
        assertThat(followRelationCount(TARGET_USER_ID, ACTOR_USER_ID)).isOne();
        assertThat(blockVersionLogCount()).isZero();
        assertThat(currentBlockVersion()).isZero();
        assertThat(outboxCount()).isZero();
    }

    @Test
    void reverseFollowAndBlockShouldSerializeOnTheSameUserPairAndPreserveTheInvariant() throws Exception {
        when(userReadApplicationService.getSummaryById(ACTOR_USER_ID))
                .thenReturn(new UserSummaryView(ACTOR_USER_ID, "actor-user", null, 0));
        CountDownLatch followReachedInsert = new CountDownLatch(1);
        CountDownLatch releaseFollowInsert = new CountDownLatch(1);
        CountDownLatch blockStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            followReachedInsert.countDown();
            await(releaseFollowInsert);
            return invocation.callRealMethod();
        }).when(followRepository).follow(eq(TARGET_USER_ID), eq(USER), eq(ACTOR_USER_ID), anyLong());
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> follow = executor.submit(() -> followApplicationService.follow(
                    new FollowCommand(TARGET_USER_ID, USER, ACTOR_USER_ID)
            ));
            assertThat(followReachedInsert.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> block = executor.submit(() -> {
                blockStarted.countDown();
                blockApplicationService.block(new BlockCommand(ACTOR_USER_ID, TARGET_USER_ID));
            });
            assertThat(blockStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> block.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFollowInsert.countDown();
            follow.get(5, TimeUnit.SECONDS);
            block.get(5, TimeUnit.SECONDS);

            assertThat(blockRelationCount()).isOne();
            assertThat(followRelationCount(ACTOR_USER_ID, TARGET_USER_ID)).isZero();
            assertThat(followRelationCount(TARGET_USER_ID, ACTOR_USER_ID)).isZero();
        } finally {
            releaseFollowInsert.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void allowTargetUserLookup() {
        when(userReadApplicationService.getSummaryById(TARGET_USER_ID))
                .thenReturn(new UserSummaryView(TARGET_USER_ID, "target-user", null, 0));
    }

    private void insertFollow(UUID userId, UUID targetUserId) {
        jdbcTemplate.update(
                "insert into social_follow(user_id, entity_type, entity_id, created_at) values (?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(userId),
                USER,
                BinaryUuidCodec.toBytes(targetUserId)
        );
    }

    private long likeRelationCount() {
        return queryCount(
                "select count(*) from social_like where user_id = ? and entity_type = ? and entity_id = ?",
                BinaryUuidCodec.toBytes(ACTOR_USER_ID),
                USER,
                BinaryUuidCodec.toBytes(TARGET_USER_ID)
        );
    }

    private Optional<UUID> storedRelationInstanceId() {
        return jdbcTemplate.query(
                        "select relation_instance_id from social_like where user_id = ? and entity_type = ? and entity_id = ?",
                        (resultSet, rowNum) -> BinaryUuidCodec.fromBytes(resultSet.getBytes("relation_instance_id")),
                        BinaryUuidCodec.toBytes(ACTOR_USER_ID),
                        USER,
                        BinaryUuidCodec.toBytes(TARGET_USER_ID)
                )
                .stream()
                .findFirst();
    }

    private long targetLikeCountRow() {
        return queryCount(
                "select count(*) from social_user_like_count where user_id = ? and like_count = 1",
                BinaryUuidCodec.toBytes(TARGET_USER_ID)
        );
    }

    private long followRelationCount(UUID userId, UUID targetUserId) {
        return queryCount(
                "select count(*) from social_follow where user_id = ? and entity_type = ? and entity_id = ?",
                BinaryUuidCodec.toBytes(userId),
                USER,
                BinaryUuidCodec.toBytes(targetUserId)
        );
    }

    private long allFollowRelationCount() {
        return queryCount("select count(*) from social_follow");
    }

    private long blockRelationCount() {
        return queryCount(
                "select count(*) from social_block where user_id = ? and target_user_id = ?",
                BinaryUuidCodec.toBytes(ACTOR_USER_ID),
                BinaryUuidCodec.toBytes(TARGET_USER_ID)
        );
    }

    private long blockVersionLogCount() {
        return queryCount("select count(*) from social_block_version_log");
    }

    private long currentBlockVersion() {
        Long value = jdbcTemplate.queryForObject(
                "select current_version from social_block_version_counter where id = 1",
                Long.class
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

    private IllegalStateException publicationFailure() {
        return new IllegalStateException("publish failed");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent social write");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for concurrent social write", exception);
        }
    }
}
