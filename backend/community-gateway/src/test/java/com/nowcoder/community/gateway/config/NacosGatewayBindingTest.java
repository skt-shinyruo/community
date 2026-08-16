package com.nowcoder.community.gateway.config;

import com.nowcoder.community.gateway.edge.EdgeTrustedProxyProperties;
import com.nowcoder.community.gateway.edge.RateLimitProperties;
import com.nowcoder.community.gateway.security.GatewayCorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

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
        RateLimitProperties rateLimit = binder.bind("gateway.http.rate-limit", RateLimitProperties.class)
                .orElseThrow(IllegalStateException::new);
        GatewayCorsProperties cors = binder.bind("gateway.cors", GatewayCorsProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(gateway.getRoutes())
                .extracting(RouteDefinition::getId)
                .containsExactly("im-session-edge", "im-ws-edge", "oss-api", "im-core", "bootstrap-api", "oss-files");
        assertThat(gateway.getRoutes())
                .extracting(route -> route.getUri().toString())
                .contains("lb://community-im-gateway", "lb://community-app", "lb://community-oss", "lb://im-core");
        assertThat(gateway.getDefaultFilters())
                .singleElement()
                .satisfies(filter -> assertThat(filter.getName()).isEqualTo("DedupeResponseHeader"));
        assertThat(environment.containsProperty("gateway.http.rate-limit.fail-open-on-error")).isTrue();
        assertThat(rateLimit.isEnabled()).isTrue();
        assertThat(rateLimit.isFailOpenOnError()).isFalse();
        assertThat(rateLimit.getPolicies())
                .containsKey("/api/drive/shares/{shareToken}/verify");
        assertThat(rateLimit.getPolicies().get("/api/drive/shares/{shareToken}/verify").getLimit())
                .isEqualTo(10);
        assertThat(cors.getAllowedOrigins()).containsExactly(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:12881",
                "http://127.0.0.1:12881",
                "http://localhost:12888",
                "http://127.0.0.1:12888"
        );
        assertThat(environment.getProperty("security.jwt.issuer")).isEqualTo("community-auth");
    }

    @Test
    void bindsGatewayTrustedProxyFromOwnerSpecificRuntimeInputs() throws Exception {
        StandardEnvironment environment = environmentFrom(
                "community-gateway.yaml",
                Map.of(
                        "GATEWAY_TRUSTED_PROXY_ENABLED", "true",
                        "GATEWAY_TRUSTED_PROXY_CIDRS", "172.30.0.0/24,fd00:30::/64"
                )
        );

        EdgeTrustedProxyProperties trustedProxy = Binder.get(environment)
                .bind("gateway.trusted-proxy", EdgeTrustedProxyProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(trustedProxy.isEnabled()).isTrue();
        assertThat(trustedProxy.getCidrs()).containsExactly("172.30.0.0/24", "fd00:30::/64");
        assertThat(trustedProxy.getSource()).isEqualTo("compose-environment");
        assertThat(environment.getProperty("gateway.trusted-proxy.source"))
                .isEqualTo("compose-environment");
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
