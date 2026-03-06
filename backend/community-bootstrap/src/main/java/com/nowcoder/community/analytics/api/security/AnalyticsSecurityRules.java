package com.nowcoder.community.analytics.api.security;

import com.nowcoder.community.bootstrap.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)
public class AnalyticsSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/api/analytics/**").hasAnyRole("ADMIN", "MODERATOR");
    }
}
