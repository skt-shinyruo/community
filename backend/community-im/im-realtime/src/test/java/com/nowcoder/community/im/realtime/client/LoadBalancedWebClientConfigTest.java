package com.nowcoder.community.im.realtime.client;

import org.junit.jupiter.api.Test;
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

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void loadBalancedBuilderAppliesWebClientCustomizers() {
        contextRunner.run(context -> {
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

    @Configuration(proxyBeanMethods = false)
    @Import(LoadBalancedWebClientConfig.class)
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
