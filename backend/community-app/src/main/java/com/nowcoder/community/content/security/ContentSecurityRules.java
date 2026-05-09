package com.nowcoder.community.content.security;

import com.nowcoder.community.app.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class ContentSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/tags/hot", "/api/tags/**").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*", "/api/posts/*/comments", "/api/posts/*/comments/*/replies").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/posts/batch-summary").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/posts/media/**").authenticated();
        auth.requestMatchers(HttpMethod.POST, "/api/posts/*/top", "/api/posts/*/wonderful", "/api/posts/*/delete").hasAnyRole("ADMIN", "MODERATOR");
        auth.requestMatchers("/api/moderation/**").hasAnyRole("ADMIN", "MODERATOR");
    }
}
