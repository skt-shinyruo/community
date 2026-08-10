package com.nowcoder.community.ops.application;

import java.time.Duration;
import java.util.List;

public interface ProjectionLagQuery {

    List<ProjectionLag> listProjectionLag();

    record ProjectionLag(String projection, String status, long count, Duration oldestAge) {
    }
}
