package com.nowcoder.community.im.gateway;

import com.nowcoder.community.common.security.jwt.JwtProperties;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class TestJwtKeys {

    private static final KeyPair ACCESS_KEY_PAIR = keyPair();

    private TestJwtKeys() {
    }

    public static String publicKey() {
        return Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPublic().getEncoded());
    }

    public static JwtProperties accessProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessPublicKey(publicKey());
        properties.setAccessPrivateKey(Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPrivate().getEncoded()));
        properties.setIssuer("community-auth");
        properties.setAccessTokenAudience("community-api");
        return properties;
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
