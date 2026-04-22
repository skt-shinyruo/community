package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserSummaryView(UUID id, String username, String headerUrl, int type) {
}
