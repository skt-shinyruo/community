package com.nowcoder.community.auth.domain.service;

import java.security.SecureRandom;
import java.util.Base64;

public class AuthSecretGenerator {

    private static final int OPAQUE_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public AuthSecretGenerator() {
        this(new SecureRandom());
    }

    AuthSecretGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom == null ? new SecureRandom() : secureRandom;
    }

    public String opaqueToken() {
        byte[] bytes = new byte[OPAQUE_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String numericCode(int digits) {
        int length = Math.max(4, digits);
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}
