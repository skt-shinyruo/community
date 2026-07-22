package com.nowcoder.community.im.realtime.client;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.realtime.session.SessionTicketCodec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LoadBalancedWebClientConfigTest {

    private static final String ACCESS_SECRET =
            "load-balanced-client-access-secret-at-least-32-bytes";
    private static final String TICKET_SECRET =
            "load-balanced-client-ticket-secret-distinct-at-least-32-bytes";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("security.jwt.issuer=community-auth");

    @Test
    void loadBalancedBuilderAppliesWebClientCustomizers() {
        validContextRunner().run(context -> {
            @SuppressWarnings("unchecked")
            AtomicReference<String> observedHeader = context.getBean(AtomicReference.class);
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient webClient = builder.clone()
                    .baseUrl("https://profiles.example")
                    .exchangeFunction(request -> {
                        observedHeader.set(request.headers().getFirst("X-Observed"));
                        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                    })
                    .build();

            webClient.get()
                    .uri("/api/users/7")
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            assertThat(observedHeader).hasValue("true");
        });
    }

    @Test
    void sessionTicketCodecContextFailsWhenTicketSecretMissing() {
        contextRunner
                .withPropertyValues("security.jwt.hmac-secret=" + ACCESS_SECRET)
                .run(context -> assertStartupFailure(context, "im.session-ticket.hmac-secret is required"));
    }

    @Test
    void sessionTicketCodecContextFailsWhenTicketSecretBlank() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.hmac-secret=" + ACCESS_SECRET,
                        "im.session-ticket.hmac-secret=   "
                )
                .run(context -> assertStartupFailure(context, "im.session-ticket.hmac-secret is required"));
    }

    @Test
    void sessionTicketCodecContextFailsWhenTicketSecretShort() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.hmac-secret=" + ACCESS_SECRET,
                        "im.session-ticket.hmac-secret=too-short"
                )
                .run(context -> assertStartupFailure(
                        context,
                        "im.session-ticket.hmac-secret must be >= 32 bytes"
                ));
    }

    @Test
    void sessionTicketCodecContextFailsWhenNormalizedTicketSecretEqualsTrimmedAccessSecret() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.hmac-secret=  " + ACCESS_SECRET + "  ",
                        "im.session-ticket.hmac-secret=\t" + ACCESS_SECRET + "\t"
                )
                .run(context -> assertStartupFailure(
                        context,
                        "im.session-ticket.hmac-secret must differ from security.jwt.hmac-secret"
                ));
    }

    @Test
    void sessionTicketCodecContextStartsWhenSecretsAreValidAndDistinct() {
        validContextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SessionTicketCodec.class);
        });
    }

    private ApplicationContextRunner validContextRunner() {
        return contextRunner.withPropertyValues(
                "security.jwt.hmac-secret=" + ACCESS_SECRET,
                "im.session-ticket.hmac-secret=" + TICKET_SECRET
        );
    }

    private static void assertStartupFailure(
            AssertableApplicationContext context,
            String message
    ) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(message);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(LoadBalancedWebClientConfig.class)
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfiguration {

        @Bean
        AtomicReference<String> observedHeader() {
            return new AtomicReference<>();
        }

        @Bean
        WebClientCustomizer markerWebClientCustomizer() {
            return builder -> builder.defaultHeader("X-Observed", "true");
        }
    }
}
