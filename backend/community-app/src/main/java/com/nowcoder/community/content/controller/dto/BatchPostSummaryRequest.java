package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchPostSummaryRequest(
        @NotNull @Size(max = 200, message = "postIds too large (max=200)") List<UUID> postIds
) {
}
