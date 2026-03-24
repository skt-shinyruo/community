package com.nowcoder.community.auth.security;

import com.nowcoder.community.app.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class AuthSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/auth/register/code/resend", "/api/auth/register/code/verify").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/auth/captcha/verify").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/auth/password/reset/request", "/api/auth/password/reset/confirm").permitAll();
    }
}
