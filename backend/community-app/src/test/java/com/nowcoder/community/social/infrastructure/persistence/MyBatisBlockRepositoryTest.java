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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from social_block_version_log");
        jdbcTemplate.update("delete from social_block");
        jdbcTemplate.update("update social_block_version_counter set current_version = 0 where id = 1");
    }

    @Test
    void blockProjectionVersionShouldMonotonicallyIncreaseAndPersistActiveAndDeleteFacts() {
        long blockVersion = blockRepository.nextBlockProjectionVersion();

        assertThat(blockRepository.block(BLOCKER_ID, BLOCKED_ID, blockVersion)).isTrue();

        List<BlockRelation> activeRelations = blockRepository.scanBlocksAfter(ZERO_UUID, ZERO_UUID, 20);
        assertThat(activeRelations).singleElement().satisfies(relation -> {
            assertThat(relation.blockerUserId()).isEqualTo(BLOCKER_ID);
            assertThat(relation.blockedUserId()).isEqualTo(BLOCKED_ID);
            assertThat(relation.version()).isEqualTo(blockVersion);
        });
        assertThat(blockRepository.currentBlockProjectionVersion()).isEqualTo(blockVersion);

        long unblockVersion = blockRepository.nextBlockProjectionVersion();
        assertThat(unblockVersion).isGreaterThan(blockVersion);
        assertThat(blockRepository.unblock(BLOCKER_ID, BLOCKED_ID, unblockVersion)).isTrue();

        assertThat(blockRepository.scanBlocksAfter(ZERO_UUID, ZERO_UUID, 20)).isEmpty();
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
}
