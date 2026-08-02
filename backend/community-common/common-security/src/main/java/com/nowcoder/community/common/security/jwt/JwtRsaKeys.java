package com.nowcoder.community.common.security.jwt;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class JwtRsaKeys {

    private static final int MINIMUM_RSA_BITS = 2048;

    private JwtRsaKeys() {
    }

    public static RSAPublicKey accessPublicKeyOrThrow(JwtProperties properties) {
        byte[] encoded = decode(
                "security.jwt.access-public-key",
                properties == null ? null : properties.getAccessPublicKey(),
                "PUBLIC KEY"
        );
        try {
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            requireStrongKey("security.jwt.access-public-key", key.getModulus().bitLength());
            return key;
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw invalid("security.jwt.access-public-key", exception);
        }
    }

    public static RSAPrivateKey accessPrivateKeyOrThrow(JwtProperties properties) {
        byte[] encoded = decode(
                "security.jwt.access-private-key",
                properties == null ? null : properties.getAccessPrivateKey(),
                "PRIVATE KEY"
        );
        try {
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
            requireStrongKey("security.jwt.access-private-key", key.getModulus().bitLength());
            return key;
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw invalid("security.jwt.access-private-key", exception);
        }
    }

    public static void requireMatchingAccessKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (publicKey == null || privateKey == null || !publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalArgumentException(
                    "security.jwt.access-public-key and security.jwt.access-private-key must be a matching RSA key pair"
            );
        }
    }

    private static byte[] decode(String propertyName, String configuredValue, String pemLabel) {
        if (configuredValue == null || configuredValue.isBlank()) {
            throw new IllegalArgumentException(propertyName + " is required");
        }
        String normalized = configuredValue.trim()
                .replace("-----BEGIN " + pemLabel + "-----", "")
                .replace("-----END " + pemLabel + "-----", "")
                .replace("\\n", "")
                .replace("\\r", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException exception) {
            throw invalid(propertyName, exception);
        }
    }

    private static void requireStrongKey(String propertyName, int bitLength) {
        if (bitLength < MINIMUM_RSA_BITS) {
            throw new IllegalArgumentException(propertyName + " must be an RSA key of at least 2048 bits");
        }
    }

    private static IllegalArgumentException invalid(String propertyName, Exception cause) {
        return new IllegalArgumentException(propertyName + " must contain a valid RSA key", cause);
    }
}
