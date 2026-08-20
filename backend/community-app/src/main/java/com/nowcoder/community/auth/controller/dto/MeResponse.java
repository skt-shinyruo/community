package com.nowcoder.community.auth.controller.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(UUID userId, String username, List<String> authorities) {
}
