package com.nowcoder.community.app.security;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JwtDecoderBeanIntegrationTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void context_shouldExposeSharedJwtPropertiesBean() {
        assertThat(jwtProperties).isNotNull();
        assertThat(jwtProperties.getIssuer()).isEqualTo("community-auth");
    }
}
