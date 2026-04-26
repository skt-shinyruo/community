package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AnalyticsPrincipalResolver {

    public UUID resolveUserUuid(Authentication authentication) {
        return CurrentUser.tryUserUuid(authentication);
    }
}
