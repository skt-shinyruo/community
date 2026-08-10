package com.nowcoder.community.social.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BlockRequest(@NotNull UUID userId) {
}
