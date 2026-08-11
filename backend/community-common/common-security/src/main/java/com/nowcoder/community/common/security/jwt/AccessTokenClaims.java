package com.nowcoder.community.common.security.jwt;

import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.OptionalLong;

public final class AccessTokenClaims {

    public static final String SECURITY_VERSION = "security_version";

    private AccessTokenClaims() {
    }

    public static OptionalLong securityVersion(Jwt jwt) {
        if (jwt == null) {
            return OptionalLong.empty();
        }
        Object claim = jwt.getClaim(SECURITY_VERSION);
        Long version = integralLong(claim);
        return version != null && version > 0L ? OptionalLong.of(version) : OptionalLong.empty();
    }

    private static Long integralLong(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        try {
            if (value instanceof BigInteger bigInteger) {
                return bigInteger.longValueExact();
            }
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal.longValueExact();
            }
        } catch (ArithmeticException ignored) {
            return null;
        }
        return null;
    }
}
