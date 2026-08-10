package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BatchPostSummaryRequest(
        @NotNull List<UUID> postIds
) {
}
