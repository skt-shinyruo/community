package com.nowcoder.community.ops.controller.dto;

import java.time.Duration;

public record ProjectionLagResponse(String projection, String status, long count, Duration oldestAge) {
}
