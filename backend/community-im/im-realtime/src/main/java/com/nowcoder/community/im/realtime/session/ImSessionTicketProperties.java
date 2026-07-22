package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "im.session-ticket")
public class ImSessionTicketProperties {

    private String hmacSecret;
    private String issuer = "community-im-gateway";
    private String audience = "im-realtime";

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public SecretKey secretKeyOrThrow(JwtProperties accessProperties) {
        String secret = requireText("hmac-secret", hmacSecret);
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("im.session-ticket.hmac-secret must be >= 32 bytes");
        }
        String accessSecret = accessProperties == null ? null : normalize(accessProperties.getHmacSecret());
        if (secret.equals(accessSecret)) {
            throw new IllegalArgumentException(
                    "im.session-ticket.hmac-secret must differ from security.jwt.hmac-secret"
            );
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    String requiredIssuer() {
        return requireText("issuer", issuer);
    }

    String requiredAudience() {
        return requireText("audience", audience);
    }

    private static String requireText(String property, String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("im.session-ticket." + property + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
