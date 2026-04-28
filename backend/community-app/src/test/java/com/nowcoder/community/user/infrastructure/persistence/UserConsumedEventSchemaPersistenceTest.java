package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UserConsumedEventSchemaPersistenceTest {

    private static final UUID CONSUMED_EVENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000641");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_consumed_event");
    }

    @Test
    void userConsumedEventPrimaryKeyShouldStoreApplicationAssignedUuid() {
        int inserted = jdbcTemplate.update(
                "insert into user_consumed_event(id, event_id, consumed_at) values (?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(CONSUMED_EVENT_ID),
                "moderation-evt-1"
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from user_consumed_event where event_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "moderation-evt-1"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(CONSUMED_EVENT_ID);
    }
}
