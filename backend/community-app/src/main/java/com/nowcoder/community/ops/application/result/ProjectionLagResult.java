package com.nowcoder.community.ops.application.result;

import java.time.Duration;

public record ProjectionLagResult(String projection, String status, long count, Duration oldestAge) {
}
