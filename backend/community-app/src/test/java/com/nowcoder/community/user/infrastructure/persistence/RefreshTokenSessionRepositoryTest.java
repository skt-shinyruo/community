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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class RefreshTokenSessionRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000000a");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenSessionRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from auth_refresh_token");
    }

    @Test
    void storeShouldPersistUuidUserIdAsBinary16() {
        Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");

        repository.store("hash-1", USER_ID, "family-1", expiresAt);

        byte[] storedUserId = jdbcTemplate.queryForObject(
                "select user_id from auth_refresh_token where token_hash = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "hash-1"
        );
        assertThat(storedUserId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedUserId)).isEqualTo(USER_ID);

        RefreshTokenSessionRepository.RefreshTokenRecord record = repository.find("hash-1");
        assertThat(record).isNotNull();
        assertThat(record.userId()).isEqualTo(USER_ID);
        assertThat(record.familyId()).isEqualTo("family-1");
        assertThat(record.expiresAt()).isEqualTo(expiresAt);
    }
}
