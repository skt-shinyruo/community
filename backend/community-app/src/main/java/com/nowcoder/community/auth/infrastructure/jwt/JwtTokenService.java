package com.nowcoder.community.auth.infrastructure.jwt;

import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class JwtTokenService implements AuthTokenPort {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String createAccessToken(UUID userId, String username, List<String> authorities, long securityVersion) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(jwtProperties.getAccessTokenTtlSeconds());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtCodecs.resolvedIssuer(jwtProperties))
                .issuedAt(now)
                .expiresAt(exp)
                .subject(String.valueOf(userId))
                .audience(List.of(JwtCodecs.resolvedAccessTokenAudience(jwtProperties)))
                .claim("username", username)
                .claim("authorities", authorities)
                .claim("security_version", securityVersion)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(JwtCodecs.ACCESS_TOKEN_TYPE)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
