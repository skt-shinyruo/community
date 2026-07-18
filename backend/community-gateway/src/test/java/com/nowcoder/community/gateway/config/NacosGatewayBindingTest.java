package com.nowcoder.community.gateway.config;

import com.nowcoder.community.gateway.canary.CanaryRouteProperties;
import com.nowcoder.community.gateway.edge.EdgeTrustedProxyProperties;
import com.nowcoder.community.gateway.edge.RateLimitProperties;
import com.nowcoder.community.gateway.edge.TrafficPolicyProperties;
import com.nowcoder.community.gateway.http.GatewayHttpRouteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
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

        GatewayHttpRouteProperties routes = binder.bind("gateway.http", GatewayHttpRouteProperties.class)
                .orElseThrow(IllegalStateException::new);
        RateLimitProperties rateLimit = binder.bind("gateway.http.rate-limit", RateLimitProperties.class)
                .orElseThrow(IllegalStateException::new);
        TrafficPolicyProperties traffic = binder.bind("gateway.http.traffic-policy", TrafficPolicyProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(routes.getRoutes())
                .extracting(GatewayHttpRouteProperties.Route::getServiceId)
                .contains("community-app", "community-oss", "im-core");
        assertThat(environment.containsProperty("gateway.http.rate-limit.fail-open-on-error")).isTrue();
        assertThat(rateLimit.isEnabled()).isTrue();
        assertThat(rateLimit.isFailOpenOnError()).isFalse();
        assertThat(traffic.getDefaultPolicyId()).isEqualTo("baseline");
        assertThat(environment.getProperty("gateway.cors.allowed-origins[2]")).isEqualTo("http://localhost:12881");
        assertThat(environment.getProperty("security.jwt.issuer")).isEqualTo("community-auth");
    }

    @Test
    void bindsCanaryRoutingPolicyDataId() throws Exception {
        CanaryRouteProperties canary = Binder.get(environmentFrom("community-canary-routing.yaml"))
                .bind("gateway.http.canary", CanaryRouteProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(canary.getRules()).isEmpty();
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
    }

    private static StandardEnvironment environmentFrom(String fileName) throws Exception {
        return environmentFrom(fileName, Map.of());
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
            Path candidate = current.resolve("deploy/nacos/config").resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Nacos seed file not found: " + fileName);
    }
}
