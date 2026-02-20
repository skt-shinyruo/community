package com.nowcoder.community.infra.security.autoconfig;

import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.infra.security.jwt.JwtSecretKeys;
import com.nowcoder.community.infra.security.metrics.MetricsBasicAuthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, SecurityFilterChain.class})
public class ServletInfraSecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    public UserDetailsService prometheusUserDetailsService(MetricsBasicAuthProperties properties) {
        String u = properties == null ? null : properties.getUsername();
        String p = properties == null ? null : properties.getPassword();
        String username = u == null || u.isBlank() ? "prometheus" : u.trim();
        String password = p == null || p.isBlank() ? "dev-prometheus-pass" : p;
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password("{noop}" + password)
                        .roles("PROMETHEUS")
                        .build()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        return NimbusJwtDecoder.withSecretKey(JwtSecretKeys.hmacSha256OrThrow(jwtProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @Order(1)
    @ConditionalOnMissingBean(name = "actuatorSecurityFilterChain")
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").hasRole("PROMETHEUS")
                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
