package com.nowcoder.community.user.security;

import com.nowcoder.community.app.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class UserSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/api/users/admin/**").hasRole("ADMIN");
        auth.requestMatchers(HttpMethod.GET, "/api/users/*").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/users/*/recent-posts", "/api/users/*/recent-comments").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/users/batch-summary").permitAll();
    }
}
