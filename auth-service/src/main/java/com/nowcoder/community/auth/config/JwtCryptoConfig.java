package com.nowcoder.community.auth.config;

import com.nowcoder.community.common.exception.BusinessException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtCryptoConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        if (!StringUtils.hasText(jwtProperties.getHmacSecret())) {
            throw new BusinessException(INVALID_ARGUMENT, "AUTH_JWT_HMAC_SECRET 未配置");
        }
        byte[] secretBytes = jwtProperties.getHmacSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new BusinessException(INVALID_ARGUMENT, "AUTH_JWT_HMAC_SECRET 长度不足（建议 >= 32 字节）");
        }

        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }
}
