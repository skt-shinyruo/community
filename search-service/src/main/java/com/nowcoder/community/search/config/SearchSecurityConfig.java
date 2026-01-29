package com.nowcoder.community.search.config;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SearchSecurityConfig {

    @Bean
    public UserDetailsService prometheusUserDetailsService(
            @Value("${community.metrics.basic-auth.username:prometheus}") String username,
            @Value("${community.metrics.basic-auth.password:dev-prometheus-pass}") String password
    ) {
        String u = StringUtils.hasText(username) ? username.trim() : "prometheus";
        String p = StringUtils.hasText(password) ? password : "dev-prometheus-pass";
        return new InMemoryUserDetailsManager(
                User.withUsername(u)
                        .password("{noop}" + p)
                        .roles("PROMETHEUS")
                        .build()
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        if (!StringUtils.hasText(jwtProperties.getHmacSecret())) {
            throw new BusinessException(INVALID_ARGUMENT, "JWT_HMAC_SECRET 未配置");
        }
        byte[] secretBytes = jwtProperties.getHmacSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new BusinessException(INVALID_ARGUMENT, "JWT_HMAC_SECRET 长度不足（建议 >= 32 字节）");
        }
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    @Order(1)
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

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityExceptionHandler securityExceptionHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search/posts").permitAll()
                        .requestMatchers("/internal/search/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> authorities = jwt.getClaimAsStringList("authorities");
            if (authorities == null) {
                return List.of();
            }
            return authorities.stream()
                    .filter(StringUtils::hasText)
                    .map(SimpleGrantedAuthority::new)
                    .map(a -> (GrantedAuthority) a)
                    .toList();
        });
        return converter;
    }
}
