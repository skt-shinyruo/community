package com.nowcoder.community.ops.api.security;

import com.nowcoder.community.bootstrap.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)
public class OpsSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/api/ops/**").hasRole("ADMIN");
    }
}
