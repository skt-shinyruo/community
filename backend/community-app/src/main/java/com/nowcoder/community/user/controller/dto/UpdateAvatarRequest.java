package com.nowcoder.community.user.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateAvatarRequest(@NotNull UUID objectId) {
}
