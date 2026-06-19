package com.nowcoder.community.auth.infrastructure.jwt;

import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService implements AuthTokenPort {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public String createAccessToken(UUID userId, String username, List<String> authorities, long securityVersion) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(jwtProperties.getAccessTokenTtlSeconds());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtCodecs.resolvedIssuer(jwtProperties))
                .issuedAt(now)
                .expiresAt(exp)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("authorities", authorities)
                .claim("security_version", securityVersion)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
