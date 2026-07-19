package com.nowcoder.community.oss.infrastructure.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OssServiceJwtPropertiesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void issuerShouldRequireText(String issuer) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OssServiceJwtProperties(issuer, "community-oss", "oss.internal"))
                .withMessageContaining("issuer");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void audienceShouldRequireText(String audience) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OssServiceJwtProperties("community-auth", audience, "oss.internal"))
                .withMessageContaining("audience");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void scopeShouldRequireText(String scope) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OssServiceJwtProperties("community-auth", "community-oss", scope))
                .withMessageContaining("scope");
    }

    @ParameterizedTest
    @ValueSource(strings = {"oss.internal read", "oss.internal\tread", "oss.internal\nread"})
    void scopeShouldRejectMultipleValues(String scope) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OssServiceJwtProperties("community-auth", "community-oss", scope))
                .withMessageContaining("single scope value");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n"})
    void valuesShouldBeTrimmed(String whitespace) {
        OssServiceJwtProperties properties = new OssServiceJwtProperties(
                whitespace + "community-auth" + whitespace,
                whitespace + "community-oss" + whitespace,
                whitespace + "oss.internal" + whitespace
        );

        assertThat(properties.issuer()).isEqualTo("community-auth");
        assertThat(properties.audience()).isEqualTo("community-oss");
        assertThat(properties.scope()).isEqualTo("oss.internal");
    }
}
