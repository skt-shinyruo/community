package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.PasswordResetProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenDeriverTest {

    @Test
    void deriveDeliveryShouldUseOneActiveSecretSnapshotForAllMaterial() {
        String firstSecret = "first-password-reset-delivery-secret";
        String secondSecret = "second-password-reset-delivery-secret";
        AtomicInteger reads = new AtomicInteger();
        PasswordResetProperties changingProperties = new PasswordResetProperties() {
            @Override
            public String getIdentifierHmacSecret() {
                return reads.getAndIncrement() == 0 ? firstSecret : secondSecret;
            }
        };
        UUID deliveryId = UUID.randomUUID();
        PasswordResetProperties expectedProperties = new PasswordResetProperties();
        expectedProperties.setIdentifierHmacSecret(firstSecret);

        PasswordResetTokenDeriver.DeliveryMaterial actual =
                new PasswordResetTokenDeriver(changingProperties).deriveDelivery(deliveryId);
        PasswordResetTokenDeriver.DeliveryMaterial expected =
                new PasswordResetTokenDeriver(expectedProperties).deriveDelivery(deliveryId);

        assertThat(actual).isEqualTo(expected);
        assertThat(reads).hasValue(1);
    }

    @Test
    void quotaIdentifiersShouldRemainStableWhenDeliverySecretRotates() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setIdentifierHmacSecret("first-password-reset-delivery-secret");
        properties.setQuotaHmacSecret("stable-password-reset-quota-secret");
        PasswordResetTokenDeriver deriver = new PasswordResetTokenDeriver(properties);
        String before = deriver.identifierId("email-request", "alice@example.com");
        UUID deliveryId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        String tokenBefore = deriver.deriveDelivery(deliveryId).token();

        properties.setIdentifierHmacSecret("second-password-reset-delivery-secret");

        assertThat(deriver.identifierId("email-request", "alice@example.com")).isEqualTo(before);
        assertThat(deriver.deriveDelivery(deliveryId).token())
                .isNotEqualTo(tokenBefore);
    }
}
