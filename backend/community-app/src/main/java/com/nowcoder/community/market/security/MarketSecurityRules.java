package com.nowcoder.community.market.security;

import com.nowcoder.community.app.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Order(26)
public class MarketSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/api/admin/market/**").hasRole("ADMIN");
        auth.requestMatchers(HttpMethod.GET, "/api/market/listings", "/api/market/listings/*").permitAll();
        auth.requestMatchers("/api/market/**").authenticated();
    }
}
