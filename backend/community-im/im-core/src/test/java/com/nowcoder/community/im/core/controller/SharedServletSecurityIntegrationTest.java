package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.common.security.autoconfig.SecurityCommonAutoConfiguration;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SharedServletSecurityIntegrationTest {

    @Autowired
    private AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void context_shouldUseSharedServletSecurityEntryPoint() {
        assertThat(authenticationEntryPoint).isInstanceOf(SecurityExceptionHandler.class);
    }

    @Test
    void jwtDecoder_shouldComeFromSharedAutoConfiguration() {
        assertThat(applicationContext.getBeanFactory().getBeanDefinition("jwtDecoder").getFactoryBeanName())
                .isEqualTo(SecurityCommonAutoConfiguration.class.getName());
        assertThat(applicationContext.getBean(JwtDecoder.class)).isNotNull();
    }
}
