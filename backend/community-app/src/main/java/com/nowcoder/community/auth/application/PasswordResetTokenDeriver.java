package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class PasswordResetTokenDeriver {

    private final PasswordResetProperties properties;

    public PasswordResetTokenDeriver(PasswordResetProperties properties) {
        this.properties = properties;
    }

    public DeliveryMaterial deriveDelivery(UUID deliveryId) {
        requireDeliveryId(deliveryId);
        String secret = requireSecret(properties.getIdentifierHmacSecret(),
                "auth.password-reset.identifier-hmac-secret");
        return deriveWithSecret(deliveryId, secret);
    }

    public DeliveryMaterial deriveDelivery(UUID deliveryId, String keyId) {
        requireDeliveryId(deliveryId);
        if (!StringUtils.hasText(keyId)) {
            throw new IllegalStateException("password reset delivery HMAC key id is missing");
        }
        String normalizedKeyId = keyId.trim();
        for (String candidate : deliverySecrets()) {
            if (MessageDigest.isEqual(
                    keyId(candidate).getBytes(StandardCharsets.US_ASCII),
                    normalizedKeyId.getBytes(StandardCharsets.US_ASCII))) {
                return deriveWithSecret(deliveryId, candidate);
            }
        }
        throw new IllegalStateException("password reset delivery HMAC key is unavailable: " + normalizedKeyId);
    }

    public String identifierId(String scope, String identifier) {
        if (!StringUtils.hasText(scope) || identifier == null) {
            throw new IllegalArgumentException("scope/identifier must not be blank");
        }
        String configuredQuotaSecret = properties.getQuotaHmacSecret();
        String quotaSecret = StringUtils.hasText(configuredQuotaSecret)
                ? configuredQuotaSecret
                : properties.getIdentifierHmacSecret();
        return hmac(
                requireSecret(quotaSecret, "auth.password-reset.quota-hmac-secret"),
                "quota-" + scope.trim(),
                identifier
        );
    }

    private List<String> deliverySecrets() {
        List<String> secrets = new ArrayList<>();
        secrets.add(requireSecret(properties.getIdentifierHmacSecret(),
                "auth.password-reset.identifier-hmac-secret"));
        for (String previous : properties.getPreviousIdentifierHmacSecrets()) {
            if (StringUtils.hasText(previous) && secrets.stream().noneMatch(previous.trim()::equals)) {
                secrets.add(previous.trim());
            }
        }
        return secrets;
    }

    private DeliveryMaterial deriveWithSecret(UUID deliveryId, String secret) {
        String value = deliveryId.toString();
        return new DeliveryMaterial(
                hmac(secret, "delivery-token", value),
                keyId(secret),
                hmac(secret, "delivery-reference", value)
        );
    }

    private void requireDeliveryId(UUID deliveryId) {
        if (deliveryId == null) {
            throw new IllegalArgumentException("deliveryId must not be null");
        }
    }

    private String requireSecret(String secret, String propertyName) {
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "未配置 " + propertyName
            );
        }
        return secret.trim();
    }

    private String keyId(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(("password-reset-key-id:" + requireSecret(
                    secret, "auth.password-reset.identifier-hmac-secret"))
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String hmac(String secret, String scope, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requireSecret(
                    secret, "auth.password-reset.identifier-hmac-secret").getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal((scope + ":" + value).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 not available", exception);
        }
    }

    public record DeliveryMaterial(
            String token,
            String derivationKeyId,
            String deliveryReference
    ) {
    }
}
