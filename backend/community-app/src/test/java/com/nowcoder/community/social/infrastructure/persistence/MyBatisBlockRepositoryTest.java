package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisBlockRepositoryTest {

    private static final UUID BLOCKER_ID = UUID.fromString("00000000-0000-7000-8000-000000000021");
    private static final UUID BLOCKED_ID = UUID.fromString("00000000-0000-7000-8000-000000000022");
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from social_block_version_log");
        jdbcTemplate.update("delete from social_block");
        jdbcTemplate.update("delete from social_user_pair_lock");
        jdbcTemplate.update("update social_block_version_counter set current_version = 0 where id = 1");
    }

    @Test
    void lockUserPairShouldCanonicalizeBothDirectionsToOneDatabaseRow() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            blockRepository.lockUserPair(BLOCKED_ID, BLOCKER_ID);
            blockRepository.lockUserPair(BLOCKER_ID, BLOCKED_ID);
        });

        Long lockRows = jdbcTemplate.queryForObject("select count(*) from social_user_pair_lock", Long.class);
        UUID storedFirstUserId = jdbcTemplate.queryForObject(
                "select first_user_id from social_user_pair_lock",
                (resultSet, rowNum) -> BinaryUuidCodec.fromBytes(resultSet.getBytes(1))
        );
        UUID storedSecondUserId = jdbcTemplate.queryForObject(
                "select second_user_id from social_user_pair_lock",
                (resultSet, rowNum) -> BinaryUuidCodec.fromBytes(resultSet.getBytes(1))
        );

        assertThat(lockRows).isOne();
        assertThat(storedFirstUserId).isEqualTo(BLOCKER_ID);
        assertThat(storedSecondUserId).isEqualTo(BLOCKED_ID);
    }

    @Test
    void lockUserPairShouldSerializeReverseDirectionTransactions() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            blockRepository.lockUserPair(BLOCKED_ID, BLOCKER_ID);
            firstLocked.countDown();
            await(releaseFirst);
        }));

        try {
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> blockRepository.lockUserPair(BLOCKER_ID, BLOCKED_ID)
                );
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void blockProjectionVersionShouldMonotonicallyIncreaseAndPersistActiveAndDeleteFacts() {
        long blockVersion = blockRepository.nextBlockProjectionVersion();

        assertThat(blockVersion).isEqualTo(1L);
        assertThat(blockRepository.block(BLOCKER_ID, BLOCKED_ID, blockVersion)).isTrue();

        List<BlockRelation> activeRelations = blockRepository.scanBlocksAtVersionAfter(
                blockRepository.currentBlockProjectionVersion(),
                ZERO_UUID,
                ZERO_UUID,
                20
        );
        assertThat(activeRelations).singleElement().satisfies(relation -> {
            assertThat(relation.blockerUserId()).isEqualTo(BLOCKER_ID);
            assertThat(relation.blockedUserId()).isEqualTo(BLOCKED_ID);
            assertThat(relation.version()).isEqualTo(blockVersion);
        });
        assertThat(blockRepository.currentBlockProjectionVersion()).isEqualTo(blockVersion);

        long unblockVersion = blockRepository.nextBlockProjectionVersion();
        assertThat(unblockVersion).isEqualTo(2L);
        assertThat(blockRepository.unblock(BLOCKER_ID, BLOCKED_ID, unblockVersion)).isTrue();

        assertThat(blockRepository.scanBlocksAtVersionAfter(
                blockRepository.currentBlockProjectionVersion(),
                ZERO_UUID,
                ZERO_UUID,
                20
        )).isEmpty();
        assertThat(blockRepository.scanBlocksAtVersionAfter(
                blockVersion,
                ZERO_UUID,
                ZERO_UUID,
                20
        )).singleElement().satisfies(relation -> {
            assertThat(relation.blockerUserId()).isEqualTo(BLOCKER_ID);
            assertThat(relation.blockedUserId()).isEqualTo(BLOCKED_ID);
            assertThat(relation.version()).isEqualTo(blockVersion);
        });
        assertThat(blockRepository.currentBlockProjectionVersion()).isEqualTo(unblockVersion);
        Long loggedDeleteVersion = jdbcTemplate.queryForObject(
                """
                        select version
                        from social_block_version_log
                        where user_id = ? and target_user_id = ? and active = false
                        """,
                Long.class,
                BinaryUuidCodec.toBytes(BLOCKER_ID),
                BinaryUuidCodec.toBytes(BLOCKED_ID)
        );
        assertThat(loggedDeleteVersion).isEqualTo(unblockVersion);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to release user pair lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while holding user pair lock", exception);
        }
    }
}
