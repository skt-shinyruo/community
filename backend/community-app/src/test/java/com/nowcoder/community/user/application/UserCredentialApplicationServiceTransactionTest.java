package com.nowcoder.community.user.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.infrastructure.persistence.MyBatisRefreshTokenSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UserCredentialApplicationServiceTransactionTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000000c");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserCredentialApplicationService service;

    @MockBean
    private MyBatisRefreshTokenSessionRepository refreshTokenSessionRepository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user where id = ?", BinaryUuidCodec.toBytes(USER_ID));
        jdbcTemplate.update(
                """
                        insert into user(id, username, password, salt, email, type, status, header_url, create_time)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                BinaryUuidCodec.toBytes(USER_ID),
                "reset-tx-user",
                "old-password",
                "",
                "reset-tx-user@example.com",
                0,
                1,
                "h-tx",
                Timestamp.from(Instant.parse("2026-04-21T03:00:00Z"))
        );
    }

    @Test
    void resetPasswordAndRevokeRefreshSessionsShouldRollBackPasswordWhenRevocationFails() {
        doThrow(new IllegalStateException("revocation failed"))
                .when(refreshTokenSessionRepository).revokeByUserId(USER_ID);

        assertThatThrownBy(() -> service.resetPasswordAndRevokeRefreshSessions(USER_ID, "new-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("revocation failed");

        String storedPassword = jdbcTemplate.queryForObject(
                "select password from user where id = ?",
                String.class,
                BinaryUuidCodec.toBytes(USER_ID)
        );
        assertThat(storedPassword).isEqualTo("old-password");
    }
}
