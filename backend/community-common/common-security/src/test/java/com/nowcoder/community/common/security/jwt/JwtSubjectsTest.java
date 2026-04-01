package com.nowcoder.community.common.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

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
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("abc")
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
}
