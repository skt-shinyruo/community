package com.nowcoder.community.wallet.security;

import com.nowcoder.community.app.security.ApiSecurityRules;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(24)
public class WalletSecurityRules implements ApiSecurityRules {

    @Override
    public void apply(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/api/wallet/admin/**").hasRole("ADMIN");
        auth.requestMatchers("/api/wallet/**").authenticated();
    }
}
