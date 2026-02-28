package com.nowcoder.community.infra.security.jwt;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * JWT authorities 解析器工厂（跨服务 SSOT）。
 *
 * <p>约定：token 的 {@code authorities} claim 为字符串数组（如 {@code ["ROLE_ADMIN","ROLE_USER"]}）。</p>
 * <p>服务侧 SecurityConfig 只负责授权矩阵（requestMatchers），不再复制粘贴 converter 细节。</p>
 */
public final class AuthoritiesConverterFactory {

    private AuthoritiesConverterFactory() {
    }

    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter defaults = new JwtGrantedAuthoritiesConverter();
        defaults.setAuthorityPrefix("ROLE_");
        defaults.setAuthoritiesClaimName("authorities");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            Object claim = jwt == null ? null : jwt.getClaim("authorities");
            if (claim instanceof List<?> list) {
                return list.stream()
                        .map(Object::toString)
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(SimpleGrantedAuthority::new)
                        .map(a -> (GrantedAuthority) a)
                        .toList();
            }
            Collection<GrantedAuthority> fallback = defaults.convert(jwt);
            return fallback == null ? List.of() : List.copyOf(fallback);
        });
        return converter;
    }
}
