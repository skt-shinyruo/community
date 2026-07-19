package com.nowcoder.community.infra.oss;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.oss.client.OssServiceTokenProvider;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JwtOssServiceTokenProvider implements OssServiceTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final OssClientProperties properties;
    private final Clock clock;

    public JwtOssServiceTokenProvider(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            OssClientProperties properties,
            Clock clock
    ) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder");
        this.issuer = JwtCodecs.resolvedIssuer(jwtProperties);
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String tokenValue() {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(properties.serviceSubject())
                .audience(List.of(properties.audience()))
                .claim("scope", properties.scope())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.tokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
