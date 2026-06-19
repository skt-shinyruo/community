package com.nowcoder.community.auth.application.port;

import java.util.List;
import java.util.UUID;

public interface AuthTokenPort {

    String createAccessToken(UUID userId, String username, List<String> authorities, long securityVersion);
}
