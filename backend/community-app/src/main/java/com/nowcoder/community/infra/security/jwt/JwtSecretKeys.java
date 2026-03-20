package com.nowcoder.community.infra.security.jwt;

import com.nowcoder.community.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public final class JwtSecretKeys {

    private JwtSecretKeys() {
    }

    public static SecretKey hmacSha256OrThrow(JwtProperties jwtProperties) {
        String secret = jwtProperties == null ? null : jwtProperties.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(INVALID_ARGUMENT, "security.jwt.hmac-secret 未配置");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new BusinessException(INVALID_ARGUMENT, "security.jwt.hmac-secret 长度不足（建议 >= 32 字节）");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}

