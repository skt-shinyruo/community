package com.nowcoder.community.user.controller.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchUserSummaryRequest(
        @Size(max = 200, message = "userIds 过多（max=200）") List<UUID> userIds
) {
}
