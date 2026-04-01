package com.nowcoder.community.im.realtime.security;

import com.nowcoder.community.common.security.autoconfig.SecurityCommonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImRealtimeSecurityIntegrationTest {

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private ConfigurableApplicationContext applicationContext;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(256 * 1024))
                        .build())
                .build();
    }

    @Test
    void unknownEndpoint_shouldReturnUnifiedResultBody() {
        client()
                .get()
                .uri("/__should_be_denied__")
                .exchange()
                .expectStatus().value(code -> assertThat(code).isIn(401, 403))
                .expectBody()
                .jsonPath("$.code").exists()
                .jsonPath("$.traceId").exists();
    }

    @Test
    void jwtDecoder_shouldComeFromSharedAutoConfiguration() {
        assertThat(applicationContext.getBeanFactory().getBeanDefinition("jwtDecoder").getFactoryBeanName())
                .isEqualTo(SecurityCommonAutoConfiguration.class.getName());
    }

    @Test
    void actuatorHealth_shouldBePublic() {
        client()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
