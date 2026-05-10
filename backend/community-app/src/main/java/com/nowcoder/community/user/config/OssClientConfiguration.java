package com.nowcoder.community.user.config;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.HttpCommunityOssClient;
import com.nowcoder.community.user.infrastructure.oss.OssAvatarProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;

@Configuration
@EnableConfigurationProperties(OssAvatarProperties.class)
public class OssClientConfiguration {

    @Bean
    public CommunityOssClient communityOssClient(
            @Value("${oss.client.base-url:http://community-oss:18090}") String baseUrl,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties
    ) {
        return new HttpCommunityOssClient(baseUrl, () -> internalBearer(jwtEncoder, jwtProperties));
    }

    private static String internalBearer(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtCodecs.resolvedIssuer(jwtProperties))
                .subject("community-app")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("authorities", java.util.List.of("ROLE_SERVICE"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
