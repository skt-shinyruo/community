package com.nowcoder.community.common.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSubjectsTest {

    @Test
    void userIdOrThrow_shouldParsePositiveSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("123")
                .build();

        assertThat(JwtSubjects.userIdOrThrow(jwt)).isEqualTo(123);
    }

    @Test
    void userIdOrThrow_shouldRejectNonNumericSubject() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();

        assertThatThrownBy(() -> JwtSubjects.userIdOrThrow(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.sub");
    }

    @Test
    void tryUserId_shouldReturnUserIdForValidSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("42")
                .build();

        assertThat(JwtSubjects.tryUserId(jwt)).isEqualTo(42);
    }

    @Test
    void tryUserId_shouldReturnNullForInvalidSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("x-42")
                .build();

        assertThat(JwtSubjects.tryUserId(jwt)).isNull();
    }

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
