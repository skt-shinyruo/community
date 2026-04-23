package com.nowcoder.community.infra.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class AuthoritiesConverterFactoryTest {

    @Test
    void jwtAuthenticationConverterShouldPreserveStringAuthoritiesAndScopes() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .claim("authorities", "ROLE_ADMIN ROLE_USER")
                .claim("scope", "im.realtime.internal")
                .build();

        Authentication authentication = AuthoritiesConverterFactory.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_USER", "SCOPE_im.realtime.internal");
    }
}
