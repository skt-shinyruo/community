package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState;
import com.nowcoder.community.notice.domain.repository.LikeNoticeProjectionStateRepository;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.ACTIVATED;
import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.ADVANCED;
import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.DEACTIVATED;
import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.IGNORED;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MyBatisLikeNoticeProjectionStateRepositoryTest.MapperOnlyTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "mybatis.mapper-locations=classpath:/mapper/notice_like_projection_state_mapper.xml"
)
class MyBatisLikeNoticeProjectionStateRepositoryTest {

    private static final UUID FIRST_LIFECYCLE =
            UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID SECOND_LIFECYCLE =
            UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final String RELATION_KEY = "like:" + uuid(1) + ":3:" + uuid(100);

    @Autowired
    private LikeNoticeProjectionStateRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from notice_like_projection_state");
    }

    @Test
    void outOfOrderCreateShouldNotCrossARemovalTombstone() {
        assertThat(advance(state(FIRST_LIFECYCLE, 20L, false, "removed")))
                .isEqualTo(DEACTIVATED);
        assertThat(advance(state(FIRST_LIFECYCLE, 10L, true, "stale-created")))
                .isEqualTo(IGNORED);

        assertThat(storedInstanceId()).isEqualTo(FIRST_LIFECYCLE);
        assertThat(storedVersion()).isEqualTo(20L);
        assertThat(storedActive()).isFalse();
        assertThat(storedEventId()).isEqualTo("removed");
    }

    @Test
    void delayedOldLifecycleRemovalShouldNotRevokeANewerLifecycle() {
        assertThat(advance(state(FIRST_LIFECYCLE, 100L, true, "first-created")))
                .isEqualTo(ACTIVATED);
        assertThat(advance(state(SECOND_LIFECYCLE, 200L, true, "second-created")))
                .isEqualTo(ACTIVATED);
        assertThat(advance(state(FIRST_LIFECYCLE, 150L, false, "first-delayed-removed")))
                .isEqualTo(IGNORED);
        assertThat(advance(state(SECOND_LIFECYCLE, 201L, false, "second-removed")))
                .isEqualTo(DEACTIVATED);

        assertThat(storedInstanceId()).isEqualTo(SECOND_LIFECYCLE);
        assertThat(storedVersion()).isEqualTo(201L);
        assertThat(storedActive()).isFalse();
    }

    @Test
    void concurrentDuplicateCreatesShouldActivateOnlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LikeNoticeProjectionState.Transition> first = executor.submit(() -> {
                start.await();
                return advance(state(FIRST_LIFECYCLE, 10L, true, "created-10"));
            });
            Future<LikeNoticeProjectionState.Transition> second = executor.submit(() -> {
                start.await();
                return advance(state(FIRST_LIFECYCLE, 11L, true, "created-11"));
            });
            start.countDown();

            List<LikeNoticeProjectionState.Transition> transitions = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertThat(transitions).filteredOn(ACTIVATED::equals).hasSize(1);
            assertThat(transitions).allMatch(transition ->
                    transition == ACTIVATED || transition == ADVANCED || transition == IGNORED);
        } finally {
            executor.shutdownNow();
        }
        assertThat(storedActive()).isTrue();
    }

    private LikeNoticeProjectionState.Transition advance(LikeNoticeProjectionState state) {
        return new TransactionTemplate(transactionManager).execute(status -> repository.advance(state));
    }

    private LikeNoticeProjectionState state(
            UUID relationInstanceId,
            long sourceVersion,
            boolean active,
            String eventId
    ) {
        return new LikeNoticeProjectionState(
                uuid(9), RELATION_KEY, relationInstanceId, sourceVersion, active, eventId);
    }

    private UUID storedInstanceId() {
        byte[] value = jdbcTemplate.queryForObject(
                "select relation_instance_id from notice_like_projection_state where recipient_user_id = ? and source_relation_key = ?",
                byte[].class,
                BinaryUuidCodec.toBytes(uuid(9)),
                RELATION_KEY
        );
        return BinaryUuidCodec.fromBytes(value);
    }

    private Long storedVersion() {
        return jdbcTemplate.queryForObject(
                "select source_version from notice_like_projection_state where recipient_user_id = ? and source_relation_key = ?",
                Long.class,
                BinaryUuidCodec.toBytes(uuid(9)),
                RELATION_KEY
        );
    }

    private Boolean storedActive() {
        return jdbcTemplate.queryForObject(
                "select active from notice_like_projection_state where recipient_user_id = ? and source_relation_key = ?",
                Boolean.class,
                BinaryUuidCodec.toBytes(uuid(9)),
                RELATION_KEY
        );
    }

    private String storedEventId() {
        return jdbcTemplate.queryForObject(
                "select source_event_id from notice_like_projection_state where recipient_user_id = ? and source_relation_key = ?",
                String.class,
                BinaryUuidCodec.toBytes(uuid(9)),
                RELATION_KEY
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(
            annotationClass = Mapper.class,
            basePackages = "com.nowcoder.community.notice.infrastructure.persistence.mapper"
    )
    @Import(MyBatisLikeNoticeProjectionStateRepository.class)
    static class MapperOnlyTestConfig {
    }
}
