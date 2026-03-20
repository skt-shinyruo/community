package com.nowcoder.community.im.realtime.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

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
        int userId = CurrentUser.userIdOrThrow(jwt);
        return new VerifiedJwt(userId, jwt);
    }

    public record VerifiedJwt(int userId, Jwt jwt) {
    }
}

