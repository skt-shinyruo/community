package com.nowcoder.community.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.cors.CorsConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NacosGatewayBindingTest {

    @Test
    void bindsGatewaySeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("community-gateway.yaml");
        Binder binder = Binder.get(environment);

        GatewayProperties gateway = binder.bind("spring.cloud.gateway.server.webflux", GatewayProperties.class)
                .orElseThrow(IllegalStateException::new);
        GlobalCorsProperties globalCors = binder
                .bind("spring.cloud.gateway.server.webflux.globalcors", GlobalCorsProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(gateway.getRoutes())
                .extracting(RouteDefinition::getId)
                .containsExactly(
                        "drive-share-verify-rate-limit",
                        "im-session-edge",
                        "im-ws-edge",
                        "oss-api",
                        "im-core",
                        "bootstrap-api",
                        "oss-files");
        assertThat(gateway.getRoutes())
                .extracting(route -> route.getUri().toString())
                .contains("lb://community-im-gateway", "lb://community-app", "lb://community-oss", "lb://im-core");
        assertThat(gateway.getDefaultFilters())
                .singleElement()
                .satisfies(filter -> assertThat(filter.getName()).isEqualTo("DedupeResponseHeader"));

        RouteDefinition rateLimited = gateway.getRoutes().stream()
                .filter(route -> route.getId().equals("drive-share-verify-rate-limit"))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        assertThat(rateLimited.getPredicates().toString()).contains("/api/drive/shares/*/verify");
        assertThat(rateLimited.getFilters())
                .singleElement()
                .satisfies(filter -> {
                    assertThat(filter.getName()).isEqualTo("RequestRateLimiter");
                    assertThat(filter.getArgs())
                            .containsEntry("redis-rate-limiter.replenishRate", "1")
                            .containsEntry("redis-rate-limiter.burstCapacity", "10")
                            .containsEntry("redis-rate-limiter.requestedTokens", "1")
                            .containsEntry("key-resolver", "#{@gatewayRateLimitKeyResolver}");
                });
        RouteDefinition bootstrap = gateway.getRoutes().stream()
                .filter(route -> route.getId().equals("bootstrap-api"))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        assertThat(bootstrap.getFilters()).isEmpty();

        Map<String, CorsConfiguration> corsConfigurations = globalCors.getCorsConfigurations();
        assertThat(corsConfigurations).containsOnlyKeys("/api/**", "/files/**");
        for (CorsConfiguration cors : corsConfigurations.values()) {
            assertThat(cors.getAllowedOrigins()).containsExactly(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:12881",
                    "http://127.0.0.1:12881",
                    "http://localhost:12888",
                    "http://127.0.0.1:12888"
            );
            assertThat(cors.getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
            assertThat(cors.getAllowedHeaders()).containsExactly("*");
            assertThat(cors.getExposedHeaders()).containsExactly("traceparent");
            assertThat(cors.getAllowCredentials()).isTrue();
            assertThat(cors.getMaxAge()).isEqualTo(3600L);
        }
        assertThat(environment.getProperty("security.jwt.issuer")).isEqualTo("community-auth");
    }

    @Test
    void bindsGatewayTrustedProxiesFromOwnerSpecificRuntimeInputs() throws Exception {
        StandardEnvironment environment = environmentFrom(
                "community-gateway.yaml",
                Map.of("GATEWAY_TRUSTED_PROXIES", "172\\.30\\.0\\.10")
        );

        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.trusted-proxies"))
                .isEqualTo("172\\.30\\.0\\.10");
        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.httpserver.customizer-enabled"))
                .isEqualTo("true");
        // The legacy CIDR property contract is gone: no gateway.* owner block may remain.
        assertThat(environment.getProperty("gateway.trusted-proxy.enabled")).isNull();
    }

    private static StandardEnvironment environmentFrom(String fileName) throws Exception {
        return environmentFrom(fileName, Map.of(
                "BROWSER_ALLOWED_ORIGINS",
                "http://localhost:5173,http://127.0.0.1:5173,http://localhost:12881,http://127.0.0.1:12881,http://localhost:12888,http://127.0.0.1:12888"
        ));
    }

    private static StandardEnvironment environmentFrom(
            String fileName,
            Map<String, Object> runtimeInputs
    ) throws Exception {
        Path path = seedFile(fileName);
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(fileName, new FileSystemResource(path)).get(0));
        sources.addFirst(new MapPropertySource("runtime-inputs", runtimeInputs));
        return environment;
    }

    private static Path seedFile(String fileName) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("deploy/config/nacos").resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Nacos seed file not found: " + fileName);
    }
}
