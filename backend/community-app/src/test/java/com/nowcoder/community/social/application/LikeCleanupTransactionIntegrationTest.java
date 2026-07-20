package com.nowcoder.community.social.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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

    @SpyBean
    private OutboxSocialDomainEventPublisher outboxPublisher;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(outboxPublisher);
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from social_like");
        jdbcTemplate.update("delete from social_user_like_count");
        jdbcTemplate.update("delete from social_like_target_state");
    }

    @Test
    void publisherFailureInSecondPageShouldRollbackFenceLikesCountsAndOutboxThenRetryFromStart() {
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

        assertThat(targetStateCount()).isZero();
        assertThat(likeCount()).isEqualTo(205L);
        assertThat(ownerLikeCount()).isEqualTo(205L);
        assertThat(outboxCount()).isZero();
        assertThat(storedRelationInstanceIds())
                .containsExactlyInAnyOrderElementsOf(relationInstanceIds);
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
        assertThat(likeApplicationService.cleanupDeletedContentLikes(command)).isEqualTo(205L);
        assertThat(targetStatus()).isEqualTo("DELETED");
        assertThat(targetSourceVersion()).isEqualTo(42L);
        assertThat(likeCount()).isZero();
        assertThat(ownerLikeCount()).isZero();
        assertThat(outboxCount()).isEqualTo(205L);
        assertThat(retryInstances).containsExactlyElementsOf(relationInstanceIds);

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
                        BinaryUuidCodec.toBytes(OWNER_ID)
                })
                .toList();
        jdbcTemplate.batchUpdate(
                "insert into social_like(relation_instance_id, user_id, entity_type, entity_id, entity_user_id, created_at) "
                        + "values (?, ?, ?, ?, ?, current_timestamp)",
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

    private List<UUID> storedRelationInstanceIds() {
        return jdbcTemplate.query(
                "select relation_instance_id from social_like where entity_type = ? and entity_id = ? order by user_id",
                (resultSet, rowNum) -> BinaryUuidCodec.fromBytes(resultSet.getBytes("relation_instance_id")),
                POST,
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
}
