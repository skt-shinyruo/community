package com.nowcoder.community.im.gateway.security;

import com.nowcoder.community.common.security.jwt.JwtSubjects;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtVerifier {

    private final JwtDecoder jwtDecoder;

    public JwtVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public VerifiedJwt verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("missing accessToken");
        }
        Jwt jwt = jwtDecoder.decode(accessToken.trim());
        UUID userId = JwtSubjects.userUuidOrThrow(jwt);
        return new VerifiedJwt(userId, jwt);
    }

    public record VerifiedJwt(UUID userId, Jwt jwt) {
    }
}
