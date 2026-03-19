package com.nowcoder.community.social.security;

import com.nowcoder.community.bootstrap.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class SocialSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(HttpMethod.GET, "/api/likes/count", "/api/likes/counts", "/api/likes/users/*/count").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/follows/*/followees", "/api/follows/*/followers").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/follows/*/followees/count", "/api/follows/*/followers/count").permitAll();
    }
}
