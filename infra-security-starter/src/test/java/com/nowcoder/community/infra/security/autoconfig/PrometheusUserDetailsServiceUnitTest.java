package com.nowcoder.community.infra.security.autoconfig;

import com.nowcoder.community.infra.security.metrics.MetricsBasicAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrometheusUserDetailsServiceUnitTest {

    @Test
    void reactivePrometheusUserShouldFailClosedWhenPasswordMissing() {
        MetricsBasicAuthProperties props = new MetricsBasicAuthProperties();
        props.setUsername("prometheus");
        props.setPassword("");

        ReactiveSecurityInfraAutoConfiguration config = new ReactiveSecurityInfraAutoConfiguration();
        assertThatThrownBy(() -> config.prometheusUserDetailsService(props))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void servletPrometheusUserShouldFailClosedWhenPasswordMissing() {
        MetricsBasicAuthProperties props = new MetricsBasicAuthProperties();
        props.setUsername("prometheus");
        props.setPassword(" ");

        ServletInfraSecurityConfig config = new ServletInfraSecurityConfig();
        assertThatThrownBy(() -> config.prometheusUserDetailsService(props))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectTooShortPassword() {
        MetricsBasicAuthProperties props = new MetricsBasicAuthProperties();
        props.setUsername("prometheus");
        props.setPassword("short-pass");

        ReactiveSecurityInfraAutoConfiguration reactive = new ReactiveSecurityInfraAutoConfiguration();
        assertThatThrownBy(() -> reactive.prometheusUserDetailsService(props))
                .isInstanceOf(IllegalArgumentException.class);

        ServletInfraSecurityConfig servlet = new ServletInfraSecurityConfig();
        assertThatThrownBy(() -> servlet.prometheusUserDetailsService(props))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateUserWithRolePrometheus() {
        MetricsBasicAuthProperties props = new MetricsBasicAuthProperties();
        props.setUsername(" u ");
        props.setPassword("123456789012");

        ReactiveSecurityInfraAutoConfiguration reactive = new ReactiveSecurityInfraAutoConfiguration();
        ReactiveUserDetailsService reactiveService = reactive.prometheusUserDetailsService(props);
        UserDetails reactiveUser = Mono.from(reactiveService.findByUsername("u")).block(Duration.ofSeconds(1));
        assertThat(reactiveUser).isNotNull();
        assertThat(authoritiesOf(reactiveUser)).contains("ROLE_PROMETHEUS");

        ServletInfraSecurityConfig servlet = new ServletInfraSecurityConfig();
        UserDetailsService servletService = servlet.prometheusUserDetailsService(props);
        UserDetails servletUser = servletService.loadUserByUsername("u");
        assertThat(servletUser).isNotNull();
        assertThat(authoritiesOf(servletUser)).contains("ROLE_PROMETHEUS");
    }

    private static String authoritiesOf(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.joining(","));
    }
}

