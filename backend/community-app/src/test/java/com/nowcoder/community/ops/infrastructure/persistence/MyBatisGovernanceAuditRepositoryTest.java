package com.nowcoder.community.ops.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.domain.model.GovernanceAction;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import com.nowcoder.community.ops.infrastructure.persistence.mapper.GovernanceAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisGovernanceAuditRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MyBatisGovernanceAuditRepository repository;

    @Autowired
    private GovernanceAuditMapper mapper;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from ops_governance_audit");
    }

    @Test
    void springShouldCreateRepositoryWithMyBatisMapper() {
        assertThat(repository).isNotNull();
        assertThat(mapper).isNotNull();
    }

    @Test
    void recordShouldPersistGovernanceAuditFields() {
        UUID actorUserId = uuid(99);

        repository.record(new RecordGovernanceAuditCommand(
                GovernanceAction.OUTBOX_REPLAY_BATCH.name(),
                actorUserId,
                "outbox_event",
                "eventbus.content",
                "topic=eventbus.content",
                "fixed handler and replaying bounded range",
                "{\"limit\":20}",
                GovernanceResult.PARTIAL.name(),
                "{\"replayed\":1,\"rejected\":1}",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        ));

        var row = jdbcTemplate.queryForMap(
                "select id, action, actor_user_id, target_type, target_id, scope, reason, request_json, " +
                        "result, summary_json, trace_id, created_at from ops_governance_audit"
        );
        assertThat(row.get("ID")).isNotNull();
        assertThat(row.get("ACTION")).isEqualTo(GovernanceAction.OUTBOX_REPLAY_BATCH.name());
        assertThat(BinaryUuidCodec.fromBytes((byte[]) row.get("ACTOR_USER_ID"))).isEqualTo(actorUserId);
        assertThat(row.get("TARGET_TYPE")).isEqualTo("outbox_event");
        assertThat(row.get("TARGET_ID")).isEqualTo("eventbus.content");
        assertThat(row.get("SCOPE")).isEqualTo("topic=eventbus.content");
        assertThat(row.get("REASON")).isEqualTo("fixed handler and replaying bounded range");
        assertThat(row.get("REQUEST_JSON")).isEqualTo("{\"limit\":20}");
        assertThat(row.get("RESULT")).isEqualTo(GovernanceResult.PARTIAL.name());
        assertThat(row.get("SUMMARY_JSON")).isEqualTo("{\"replayed\":1,\"rejected\":1}");
        assertThat(row.get("TRACE_ID")).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(row.get("CREATED_AT")).isNotNull();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
