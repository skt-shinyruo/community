package com.nowcoder.community.common.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSubjectsTest {

    @Test
    void userUuidOrThrow_shouldParseUuidSubject() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();

        assertThat(JwtSubjects.userUuidOrThrow(jwt)).isEqualTo(userId);
    }

    @Test
    void userUuidOrThrow_shouldRejectInvalidUuidSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("abc")
                .build();

        assertThatThrownBy(() -> JwtSubjects.userUuidOrThrow(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.sub");
    }

    @Test
    void tryUserUuid_shouldReturnUserIdForValidUuidSubject() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();

        assertThat(JwtSubjects.tryUserUuid(jwt)).isEqualTo(userId);
    }

    @Test
    void tryUserUuid_shouldReturnNullForInvalidSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("x-42")
                .build();

        assertThat(JwtSubjects.tryUserUuid(jwt)).isNull();
    }
}
