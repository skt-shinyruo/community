package com.nowcoder.community.drive.security;

import com.nowcoder.community.app.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Order(25)
public class DriveSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(HttpMethod.GET, "/api/drive/shares/*").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/drive/shares/*/verify").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/drive/shares/*/entries").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/drive/shares/*/download-url").permitAll();
        auth.requestMatchers("/api/drive/**").authenticated();
    }
}
