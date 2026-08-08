package com.nowcoder.community.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationPropertiesTest {

    @Test
    void resendLimitShouldHaveBoundedOperationalDefaults() {
        RegistrationProperties.ResendLimit limits = new RegistrationProperties().getResendLimit();

        assertThat(limits.getWindowSeconds()).isEqualTo(3600);
        assertThat(limits.getMaxRequestsPerRegistration()).isEqualTo(5);
        assertThat(limits.getMaxRequestsPerEmail()).isEqualTo(5);
        assertThat(limits.getMaxRequestsPerIp()).isEqualTo(20);
    }

    @Test
    void nullResendLimitShouldRestoreDefaults() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.getResendLimit().setMaxRequestsPerRegistration(9);

        properties.setResendLimit(null);

        assertThat(properties.getResendLimit().getMaxRequestsPerRegistration()).isEqualTo(5);
    }
}
