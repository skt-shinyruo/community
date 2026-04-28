package com.nowcoder.community.auth.domain.model;

import java.util.List;
import java.util.UUID;

public record AuthCredential(UUID userId, String username, List<String> authorities) {
}
